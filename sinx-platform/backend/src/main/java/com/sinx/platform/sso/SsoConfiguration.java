package com.sinx.platform.sso;

import java.time.Duration;
import java.util.Map;
import com.sinx.platform.auth.AuthProperties;
import com.sinx.platform.member.MemberRepository;
import com.sinx.platform.member.MemberSessionFilter;
import com.sinx.platform.application.ApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(name="sinx.sso.enabled",havingValue="true")
public class SsoConfiguration {
 @Bean @DependsOnDatabaseInitialization RegisteredClientRepository registeredClients(JdbcTemplate jdbc,SsoProperties properties) {
  properties.validate();var repository=new JdbcRegisteredClientRepository(jdbc);
  var client=RegisteredClient.withId("sinx-creator-client").clientId(properties.clientId())
   .clientSecret("{bcrypt}"+new BCryptPasswordEncoder().encode(properties.clientSecret())).clientName("创作者平台")
   .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
   .redirectUri(properties.redirectUri()).scope("creator")
   .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
   .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).accessTokenTimeToLive(Duration.ofHours(8))
    .authorizationCodeTimeToLive(Duration.ofMinutes(1)).build()).build();
  repository.save(client);return repository;
 }
 @Bean @DependsOnDatabaseInitialization OAuth2AuthorizationService authorizations(JdbcTemplate jdbc,RegisteredClientRepository clients,MemberRepository members,ApplicationService applications) {
  return new MemberAuthorizationService(new JdbcOAuth2AuthorizationService(jdbc,clients),members,applications);
 }
 @Bean OAuth2AuthorizationConsentService consents(JdbcTemplate jdbc,RegisteredClientRepository clients) {return new JdbcOAuth2AuthorizationConsentService(jdbc,clients);}
 @Bean AuthorizationServerSettings authorizationSettings(AuthProperties properties) {
  return AuthorizationServerSettings.builder().issuer(properties.publicBaseUrl())
   .authorizationEndpoint("/api/sso/authorize").tokenEndpoint("/api/sso/token")
   .tokenIntrospectionEndpoint("/api/sso/introspect").tokenRevocationEndpoint("/api/sso/revoke").build();
 }
 @Bean OAuth2TokenCustomizer<OAuth2TokenClaimsContext> memberClaims(MemberRepository members,ApplicationService applications) {
  return context->{
   var authorization=context.getAuthorization();
   var member=members.find(context.getPrincipal().getName());
   Object version=authorization==null?null:authorization.getAttribute(MemberAuthorizationService.MEMBER_VERSION);
   if(member.isEmpty() || !member.get().enabled() || !Long.toString(member.get().sessionVersion()).equals(version))
    throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
   var grant=applications.access(member.get().id(),ApplicationService.CREATOR_ID);
   if(grant.isEmpty() || !Long.toString(grant.get().applicationVersion()).equals(authorization.getAttribute(MemberAuthorizationService.APPLICATION_VERSION))
     || !Long.toString(grant.get().grantVersion()).equals(authorization.getAttribute(MemberAuthorizationService.GRANT_VERSION)))
    throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
   context.getClaims().claim("sv",version);
  };
 }
 @Bean SsoAccess ssoAccess(OAuth2AuthorizationService authorizations,MemberRepository members,SsoProperties properties,ApplicationService applications) {return new SsoAccess(authorizations,members,properties.clientId(),applications);}
 @Bean @Order(1) SecurityFilterChain authorizationSecurity(HttpSecurity http,MemberRepository members,HttpSessionRequestCache cache,SsoAccess access,ObjectMapper mapper,JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager transactions,ApplicationService applications) throws Exception {
  var authorizationServer=new OAuth2AuthorizationServerConfigurer();
  return http.securityMatcher(authorizationServer.getEndpointsMatcher())
   .with(authorizationServer,server->server
    .authorizationEndpoint(endpoint->endpoint.authenticationProviders(providers->providers.forEach(provider->{
     if(provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeProvider) {
      codeProvider.setAuthenticationValidator(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
       .andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR).andThen(context->{
        Authentication principal=(Authentication)context.getAuthentication().getPrincipal();
        if(principal.isAuthenticated() && !(principal instanceof AnonymousAuthenticationToken)
          && applications.access(principal.getName(),ApplicationService.CREATOR_ID).isEmpty())
         throw new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED),context.getAuthentication());
       }));
     }
    })))
    .tokenIntrospectionEndpoint(endpoint->endpoint.introspectionResponseHandler((req,res,authentication)->{
    var validated=(OAuth2TokenIntrospectionAuthenticationToken)authentication;
    var profile=validated.getTokenClaims().isActive()?access.inspect(validated.getToken()):java.util.Optional.<Map<String,Object>>empty();
    res.setContentType("application/json");res.setHeader("Cache-Control","no-store");
    mapper.writeValue(res.getOutputStream(),profile.orElseGet(()->Map.of("active",false)));
   })))
   .authorizeHttpRequests(a->a.anyRequest().authenticated())
   .requestCache(c->c.requestCache(cache))
   .addFilterBefore(new CodeExchangeFilter(jdbc,transactions),SecurityContextHolderFilter.class)
   .addFilterBefore(new MemberSessionFilter(members) {
    @Override protected boolean shouldNotFilter(HttpServletRequest req) {return !"/api/sso/authorize".equals(req.getServletPath());}
   },AuthorizationFilter.class)
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,error)->{
    if("/api/sso/authorize".equals(req.getRequestURI()))res.sendRedirect("/login");else res.sendError(401);
   }))
   .build();
 }
}
