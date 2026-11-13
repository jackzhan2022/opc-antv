package com.sinx.creator.auth;

import java.util.List;
import com.sinx.creator.directory.MemberDirectory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestClient;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.HttpMethod;

@Configuration
@EnableConfigurationProperties(CreatorAuthProperties.class)
public class CreatorSecurityConfiguration {
    @Bean ClientRegistrationRepository registrations(CreatorAuthProperties properties) {
        properties.validate();
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("sinx")
                .clientId(properties.clientId()).clientSecret(properties.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.frontend("/creator-api/login/oauth2/code/sinx"))
                .scope("creator").authorizationUri(properties.platform("/api/sso/authorize"))
                .tokenUri(properties.backend("/api/sso/token")).userInfoUri(properties.backend("/api/sso/userinfo"))
                .userNameAttributeName("sub").clientName("信计 X 平台").build());
    }
    @Bean OAuth2AuthorizedClientRepository authorizedClients() { return new HttpSessionOAuth2AuthorizedClientRepository(); }
    @Bean PlatformClient platformClient(CreatorAuthProperties properties) { return new PlatformClient(properties); }
    @Bean RestClientAuthorizationCodeTokenResponseClient tokenExchange() {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(RestClient.builder().requestFactory(PlatformClient.requestFactory())
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                }).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build());
        return client;
    }
    @Bean OAuth2UserService<OAuth2UserRequest,OAuth2User> platformUsers(PlatformClient platform) {
        return request -> {
            try {
                var profile = platform.profile(request.getAccessToken().getTokenValue());
                return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_"+profile.get("role"))),profile,"sub");
            } catch (PlatformFailure failure) { throw new OAuth2AuthenticationException(new OAuth2Error(failure.getMessage())); }
        };
    }
    @Bean MemberDirectory memberDirectory(PlatformClient platform,OAuth2AuthorizedClientRepository clients) {
        return authentication -> {
            var context = RequestContextHolder.getRequestAttributes();
            if (!(context instanceof ServletRequestAttributes request)) throw new PlatformFailure(true);
            OAuth2AuthorizedClient client = clients.loadAuthorizedClient("sinx",authentication,request.getRequest());
            if (client == null) throw new PlatformFailure(true);
            return platform.members(client.getAccessToken().getTokenValue());
        };
    }
    @Bean SecurityFilterChain creatorSecurity(HttpSecurity http,ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository clients,PlatformClient platform,
            OAuth2UserService<OAuth2UserRequest,OAuth2User> users,CreatorAuthProperties properties,
            RestClientAuthorizationCodeTokenResponseClient tokenExchange) throws Exception {
        var requests = new DefaultOAuth2AuthorizationRequestResolver(registrations,"/oauth2/authorization");
        requests.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return http.authorizeHttpRequests(a -> a.requestMatchers("/health","/auth/csrf","/error").permitAll()
                .requestMatchers(HttpMethod.POST,"/dictionaries").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,"/dictionaries/**").hasRole("ADMIN")
                .anyRequest().authenticated())
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .exceptionHandling(e -> e.authenticationEntryPoint((request,response,error) -> CreatorSessionFilter.json(response,401,"unauthenticated"))
                        .accessDeniedHandler((request,response,error) -> CreatorSessionFilter.json(response,403,"forbidden")))
                .oauth2Login(login -> login.authorizedClientRepository(clients)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(requests))
                        .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenExchange))
                        .userInfoEndpoint(endpoint -> endpoint.userService(users))
                        .successHandler((request,response,authentication) -> response.sendRedirect(properties.frontend("/creators/")))
                        .failureHandler((request,response,error) -> {
                            response.sendRedirect(properties.frontend("/creators/auth-error?code=login_failed"));
                        }))
                .logout(logout -> logout.logoutUrl("/auth/logout").addLogoutHandler((request,response,authentication) -> {
                    OAuth2AuthorizedClient client = clients.loadAuthorizedClient("sinx",authentication,request);
                    if (client != null) try { platform.revoke(client.getAccessToken().getTokenValue()); }
                    catch (PlatformFailure failure) { request.setAttribute("creator.logout.failed",true); }
                    clients.removeAuthorizedClient("sinx",authentication,request,response);
                }).deleteCookies("CREATOR_SESSION").logoutSuccessHandler((request,response,authentication) -> {
                    if (Boolean.TRUE.equals(request.getAttribute("creator.logout.failed"))) CreatorSessionFilter.json(response,503,"platform_unavailable");
                    else response.setStatus(204);
                }))
                .addFilterBefore(new CreatorSessionFilter(platform,clients),AuthorizationFilter.class)
                .build();
    }
}
