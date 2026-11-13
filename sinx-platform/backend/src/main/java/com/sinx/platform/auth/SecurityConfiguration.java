package com.sinx.platform.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@EnableConfigurationProperties({AuthProperties.class,com.sinx.platform.sso.SsoProperties.class})
public class SecurityConfiguration {
 @Bean java.time.Clock clock() { return java.time.Clock.systemUTC(); }
 @Bean org.springframework.security.core.userdetails.UserDetailsService noPasswordLogin() {
  return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Password login is disabled"); };
 }
 @Bean HttpSessionSecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
 @Bean org.springframework.security.web.savedrequest.HttpSessionRequestCache ssoRequestCache() {
  var cache=new org.springframework.security.web.savedrequest.HttpSessionRequestCache();
  cache.setRequestMatcher(req->"GET".equals(req.getMethod()) && "/api/sso/authorize".equals(req.getServletPath()));
  return cache;
 }
 @Bean @org.springframework.core.annotation.Order(10) SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository repository, com.sinx.platform.member.MemberRepository members,org.springframework.security.web.savedrequest.HttpSessionRequestCache cache) throws Exception {
  return http
   .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/providers", "/api/auth/csrf", "/api/auth/*/start", "/api/auth/*/callback", "/api/health", "/error").permitAll()
      .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/sso/userinfo","/api/sso/members").permitAll()
      .requestMatchers("/api/admin/**").hasRole("ADMIN")
      .anyRequest().authenticated())
   .securityContext(c -> c.securityContextRepository(repository))
   .addFilterBefore(new com.sinx.platform.member.MemberSessionFilter(members), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
   .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
   .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> {
     res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"code\":\"unauthenticated\"}");
   }).accessDeniedHandler((req,res,ex) -> {
     res.setStatus(403); res.setContentType("application/json"); res.getWriter().write("{\"code\":\"forbidden\"}");
   }))
   .requestCache(c -> c.requestCache(cache))
   .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
   .headers(h -> h.referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
   .build();
 }
}
