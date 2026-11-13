package com.sinx.creator.auth;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.util.UriComponentsBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CreatorAuthenticationTest {
    @Autowired MockMvc mvc;
    @Autowired OAuth2AuthorizedClientRepository clients;
    @Autowired ClientRegistrationRepository registrations;
    static HttpServer platform;
    static volatile boolean active = true;
    static volatile int platformStatus = 200;
    static volatile String role = "MEMBER";
    static volatile String lastRevoked = "";
    static volatile String exchangeBody = "";
    static AtomicInteger inspected = new AtomicInteger();
    static AtomicInteger exchanges = new AtomicInteger();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) throws Exception {
        platform = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        platform.createContext("/api/sso/", exchange -> {
            String path=exchange.getRequestURI().getPath();
            String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            String output="{}";
            if(path.endsWith("/introspect")) {
                inspected.incrementAndGet();
                if (!exchange.getRequestHeaders().getFirst("Authorization").startsWith("Basic ")) throw new AssertionError("Client authentication missing");
                output="{\"active\":"+active+",\"sub\":\"member-a\",\"displayName\":\"成员甲\",\"role\":\""+role+"\",\"scope\":\"creator\",\"client_id\":\"creator-platform\"}";
            } else if(path.endsWith("/userinfo")) output="{\"sub\":\"member-a\",\"displayName\":\"成员甲\",\"role\":\"MEMBER\"}";
            else if(path.endsWith("/members")) output="[{\"id\":\"member-a\",\"displayName\":\"成员甲\"}]";
            else if(path.endsWith("/revoke")) lastRevoked=input;
            else if(path.endsWith("/token")) { exchanges.incrementAndGet();exchangeBody=input;output="{\"access_token\":\"server-only-test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"creator\"}"; }
            byte[] data=output.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(platformStatus,data.length);exchange.getResponseBody().write(data);exchange.close();
        });
        platform.start();
        properties.add("creator.platform-backend-url",()->"http://127.0.0.1:"+platform.getAddress().getPort());
    }
    @AfterAll static void stop() { if(platform!=null) platform.stop(0); }
    @BeforeEach void reset() { active=true;platformStatus=200;role="MEMBER";lastRevoked="";exchangeBody="";inspected.set(0);exchanges.set(0); }
    MockHttpSession authenticated(String token) {
        var roles=List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
        var user=new DefaultOAuth2User(roles,Map.of("sub","member-a","displayName","旧名称","role","MEMBER"),"sub");
        var authentication=new OAuth2AuthenticationToken(user,roles,"sinx");
        var session=new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,new SecurityContextImpl(authentication));
        var request=new MockHttpServletRequest();request.setSession(session);
        var access=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,token,Instant.now(),Instant.now().plusSeconds(3600),Set.of("creator"));
        clients.saveAuthorizedClient(new OAuth2AuthorizedClient(registrations.findByRegistrationId("sinx"),"member-a",access),authentication,request,new MockHttpServletResponse());
        return session;
    }

    @Test void healthIsPublicAndAnonymousApiIsJsonUnauthorized() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test void loginRedirectUsesFixedProviderAndPkce() throws Exception {
        mvc.perform(get("/oauth2/authorization/sinx"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",org.hamcrest.Matchers.startsWith("https://platform.example.test/api/sso/authorize?")))
            .andExpect(header().string("Location",org.hamcrest.Matchers.containsString("code_challenge_method=S256")))
            .andExpect(header().string("Location",org.hamcrest.Matchers.containsString("redirect_uri=https://creator.example.test/creator-api/login/oauth2/code/sinx")));
    }

    @Test void everyProtectedRequestIntrospectsAndUsesCurrentProfile() throws Exception {
        var session=authenticated("first-session-token");
        mvc.perform(get("/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("成员甲"));
        role="ADMIN";
        mvc.perform(get("/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(inspected.get()).isEqualTo(2);
        mvc.perform(get("/members").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("member-a"));
    }

    @Test void revokedOrChangedMemberClearsOnlyApplicationSession() throws Exception {
        var session=authenticated("old-session-token");active=false;
        mvc.perform(get("/me").session(session)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("unauthenticated"))
            .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Path=/creator-api")));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void platformFailureClosesAccessWithoutDestroyingSession() throws Exception {
        var session=authenticated("valid-session-token");platformStatus=503;
        mvc.perform(get("/me").session(session)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("platform_unavailable"));
        assertThat(session.isInvalid()).isFalse();
    }

    @Test void csrfProtectsWritesAndLogoutRevokesOnlyThisBrowserToken() throws Exception {
        var session=authenticated("first-browser-token");var other=authenticated("second-browser-token");
        mvc.perform(post("/auth/logout").session(session)).andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();assertThat(lastRevoked).isEmpty();
        mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(lastRevoked).contains("token=first-browser-token");assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/me").session(other)).andExpect(status().isOk());
    }

    @Test void callbackRequiresBoundStateAndExchangesPkceServerSide() throws Exception {
        var start=mvc.perform(get("/oauth2/authorization/sinx")).andExpect(status().isFound()).andReturn();
        var session=(MockHttpSession)start.getRequest().getSession(false);
        var state=java.net.URLDecoder.decode(UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("state"),StandardCharsets.UTF_8);
        mvc.perform(get("/login/oauth2/code/sinx").session(session).param("state",state).param("code","one-time-test-code"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("https://creator.example.test/creators/"));
        assertThat(exchangeBody).contains("code_verifier=").contains("code=one-time-test-code");
        assertThat(exchanges.get()).isEqualTo(1);
        mvc.perform(get("/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("member-a"));
        mvc.perform(get("/login/oauth2/code/sinx").param("state","unbound").param("code","wrong"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("https://creator.example.test/creators/auth-error?code=login_failed"));
        assertThat(exchanges.get()).isEqualTo(1);
    }

    @Test void unsolicitedCallbackCannotLogOutAnExistingSession() throws Exception {
        var session=authenticated("current-browser-token");
        mvc.perform(get("/login/oauth2/code/sinx").session(session).param("state","unbound").param("code","wrong"))
            .andExpect(status().isFound());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(get("/creator-api/me").contextPath("/creator-api").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("member-a"));
    }
}
