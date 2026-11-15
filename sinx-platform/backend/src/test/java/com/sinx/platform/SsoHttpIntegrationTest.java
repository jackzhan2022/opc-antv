package com.sinx.platform;

import java.io.File;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import com.sinx.platform.auth.IdentityProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in real HTTP test. Both databases are disposable H2; no production credentials are loaded. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "sinx.sso.enabled=true", "sinx.sso.client-id=creator-platform",
    "sinx.sso.client-secret=http-integration-fixture-secret-at-least-32-characters",
    "sinx.auth.feishu.tenant-id=http-test-company"})
@EnabledIfSystemProperty(named="creator.http.classpath",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SsoHttpIntegrationTest {
    private static final String SECRET="http-integration-fixture-secret-at-least-32-characters";
    private static final int CREATOR_PORT=freePort();
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @Autowired OAuth2AuthorizationService authorizations;
    @MockitoBean(name="feishuIdentityProvider") IdentityProvider provider;
    private final ObjectMapper json=new ObjectMapper();
    private Process creator;
    private URI platformBase;
    private URI creatorBase;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) {
        properties.add("sinx.sso.redirect-uri",()->"http://localhost:"+CREATOR_PORT+"/creator-api/login/oauth2/code/sinx");
        properties.add("sinx.sso.application-url",()->"http://localhost:"+CREATOR_PORT+"/creators/");
        properties.add("spring.datasource.url",()->"jdbc:h2:mem:platform_http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        properties.add("spring.datasource.username",()->"sa");
        properties.add("spring.datasource.password",()->"");
    }
    private static int freePort() {
        try(var socket=new java.net.ServerSocket(0)) {return socket.getLocalPort();}
        catch(java.io.IOException error) {throw new IllegalStateException("No local HTTP test port available");}
    }

    @BeforeAll void startCreator() throws Exception {
        platformBase=URI.create("http://localhost:"+environment.getRequiredProperty("local.server.port"));
        creatorBase=URI.create("http://localhost:"+CREATOR_PORT);
        Path module=Path.of("../../creator-platform/backend").toAbsolutePath().normalize();
        String dependencies=Files.readString(Path.of(System.getProperty("creator.http.classpath"))).trim();
        String classpath=module.resolve("target/classes")+File.pathSeparator+module.resolve("target/test-classes")+File.pathSeparator+dependencies;
        Path log=module.resolve("../.local/creator-http-integration.log").normalize();Files.createDirectories(log.getParent());
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Dspring.config.import=", "-cp",classpath,"com.sinx.creator.CreatorApplication");
        process.directory(module.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        Map<String,String> env=process.environment();
        env.put("SPRING_PROFILES_ACTIVE","http-test");
        env.put("SPRING_DATASOURCE_URL","jdbc:h2:mem:creator_http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        env.put("SPRING_DATASOURCE_USERNAME","sa");env.put("SPRING_DATASOURCE_PASSWORD","");
        env.put("CREATOR_PORT",Integer.toString(CREATOR_PORT));
        env.put("CREATOR_COOKIE_SECURE","false");
        env.put("SINX_BASE_URL",platformBase.toString());env.put("SINX_BACKEND_URL",platformBase.toString());
        env.put("CREATOR_PUBLIC_BASE_URL",creatorBase.toString());
        env.put("CREATOR_SSO_CLIENT_ID","creator-platform");env.put("CREATOR_SSO_CLIENT_SECRET",SECRET);
        creator=process.start();
        var health=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        boolean ready=false;
        while(creator.isAlive() && System.nanoTime()<deadline) {
            try {if(health.send(HttpRequest.newBuilder(creatorBase.resolve("/creator-api/health")).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200) {ready=true;break;}}
            catch(Exception ignored) {}
            Thread.sleep(150);
        }
        assertThat(ready).as("Creator fixture service starts on the local test port; inspect its ignored log on failure").isTrue();
    }
    @AfterAll void stopCreator() throws Exception {
        if(creator!=null) {creator.destroy();if(!creator.waitFor(10,java.util.concurrent.TimeUnit.SECONDS))creator.destroyForcibly();}
    }
    @BeforeEach void prepareMember() {
        jdbc.update("DELETE FROM oauth2_authorization");jdbc.update("DELETE FROM external_identity");
        jdbc.update("DELETE FROM pending_identity");jdbc.update("DELETE FROM member_audit");jdbc.update("DELETE FROM platform_member");
        jdbc.update("INSERT INTO platform_member(id,display_name,platform_role) VALUES ('http-member','HTTP测试成员','ADMIN')");
        jdbc.update("INSERT INTO member_application_access(application_id,member_id,granted) VALUES ('creator-platform','http-member',true)");
        jdbc.update("INSERT INTO external_identity(id,provider,tenant_id,subject_id,member_id) VALUES ('http-identity','feishu','http-test-company','http-subject','http-member')");
        when(provider.id()).thenReturn("feishu");when(provider.displayName()).thenReturn("飞书");when(provider.enabled()).thenReturn(true);
        when(provider.authorizationUrl(anyString(),nullable(String.class))).thenAnswer(i->"https://identity.example.test/authorize?state="+i.getArgument(0));
        when(provider.authenticate("http-provider-fixture-code")).thenReturn(new IdentityProvider.ExternalIdentity("feishu","http-test-company","http-subject","HTTP测试成员"));
    }
    private HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(3)).build();
    }
    private HttpResponse<String> get(HttpClient browser,URI uri) throws Exception {
        return browser.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private URI redirect(HttpResponse<String> response) {
        assertThat(response.statusCode()).as("Expected an OAuth redirect, without exposing its authorization parameters").isEqualTo(302);
        return response.uri().resolve(response.headers().firstValue("Location").orElseThrow());
    }
    private void login(HttpClient browser,boolean corporateLoginRequired) throws Exception {
        URI authorize=redirect(get(browser,creatorBase.resolve("/creator-api/oauth2/authorization/sinx")));
        assertThat(authorize.getPath()).isEqualTo("/api/sso/authorize");
        URI next=redirect(get(browser,authorize));
        if(corporateLoginRequired) {
            assertThat(next.getPath()).isEqualTo("/login");
            URI identity=redirect(get(browser,platformBase.resolve("/api/auth/feishu/start")));
            String state=Arrays.stream(identity.getRawQuery().split("&")).filter(p->p.startsWith("state="))
                    .map(p->URLDecoder.decode(p.substring(6),StandardCharsets.UTF_8)).findFirst().orElseThrow();
            URI callback=URI.create(platformBase+"/api/auth/feishu/callback?state="+URLEncoder.encode(state,StandardCharsets.UTF_8)+"&code=http-provider-fixture-code");
            URI resumed=redirect(get(browser,callback));
            assertThat(resumed.getPath()).as("Corporate callback resumes the saved OAuth request").isEqualTo("/api/sso/authorize");
            next=redirect(get(browser,resumed));
        }
        assertThat(next.getPath()).isEqualTo("/creator-api/login/oauth2/code/sinx");
        URI landing=redirect(get(browser,next));
        assertThat(landing.getPath()).as("Creator callback establishes its own browser session").isEqualTo("/creators/");
        assertThat(landing.getQuery()).isNull();
        assertThat(get(browser,creatorBase.resolve("/creator-api/me")).statusCode()).isEqualTo(200);
    }
    private JsonNode body(HttpResponse<String> response) {return json.readTree(response.body());}
    private HttpResponse<String> write(HttpClient browser,String path,String data) throws Exception {
        var csrf=get(browser,creatorBase.resolve("/creator-api/auth/csrf"));assertThat(csrf.statusCode()).isEqualTo(200);
        var token=body(csrf);
        return browser.send(HttpRequest.newBuilder(creatorBase.resolve("/creator-api"+path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json").header(token.get("headerName").asText(),token.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(data)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private String activeToken() {
        return jdbc.queryForList("SELECT id FROM oauth2_authorization WHERE principal_name='http-member'",String.class).stream()
                .map(authorizations::findById).filter(a->a.getAccessToken()!=null && a.getAccessToken().isActive())
                .map(a->a.getAccessToken().getToken().getTokenValue()).findFirst().orElseThrow();
    }
    private HttpResponse<String> platformGrant(HttpClient browser,boolean granted,long version) throws Exception {
        var csrf=body(get(browser,platformBase.resolve("/api/auth/csrf")));
        return browser.send(HttpRequest.newBuilder(platformBase.resolve("/api/admin/applications/creator-platform/members/http-member"))
                .header("Content-Type","application/json").header(csrf.get("headerName").asText(),csrf.get("token").asText())
                .method("PATCH",HttpRequest.BodyPublishers.ofString("{\"granted\":"+granted+",\"version\":"+version+"}"))
                .timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private boolean introspectsActive(String token) throws Exception {
        var basic=Base64.getEncoder().encodeToString(("creator-platform:"+SECRET).getBytes(StandardCharsets.UTF_8));
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(platformBase.resolve("/api/sso/introspect"))
                .header("Authorization","Basic "+basic).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("token="+URLEncoder.encode(token,StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);return body(response).get("active").asBoolean();
    }

    @Test void corporateLoginResumesStandardSsoAndSupportsBusinessCsrfLogoutAndMemberInvalidation() throws Exception {
        var browser=browser();login(browser,true);
        String firstToken=activeToken();assertThat(introspectsActive(firstToken)).isTrue();
        var me=get(browser,creatorBase.resolve("/creator-api/me"));assertThat(body(me).get("id").asText()).isEqualTo("http-member");
        var creation=write(browser,"/creators","{\"name\":\"HTTP集成测试档案\",\"professions\":[\"编剧\"],\"works\":[],\"tags\":[]}");
        assertThat(creation.statusCode()).as("Real creator write accepts its own CSRF token and platform-maintainer directory").isEqualTo(201);
        String id=body(creation).get("id").asText();
        assertThat(get(browser,creatorBase.resolve("/creator-api/creators/"+id)).statusCode()).isEqualTo(200);
        assertThat(write(browser,"/auth/logout","{}").statusCode()).isEqualTo(204);
        assertThat(introspectsActive(firstToken)).as("App logout revokes its issued platform token").isFalse();
        assertThat(get(browser,creatorBase.resolve("/creator-api/me")).statusCode()).isEqualTo(401);
        assertThat(get(browser,platformBase.resolve("/api/me")).statusCode()).as("App logout preserves the separate platform session").isEqualTo(200);
        login(browser,false);
        String beforeRevocation=activeToken();
        assertThat(platformGrant(browser,false,1).statusCode()).isEqualTo(200);
        assertThat(get(browser,creatorBase.resolve("/creator-api/me")).statusCode()).as("Revoking application access invalidates the real creator session on its next request").isEqualTo(401);
        assertThat(get(browser,platformBase.resolve("/api/me")).statusCode()).as("Application revocation preserves the separate platform session").isEqualTo(200);
        assertThat(platformGrant(browser,true,2).statusCode()).isEqualTo(200);
        assertThat(introspectsActive(beforeRevocation)).as("Regranting does not revive an old token").isFalse();
        login(browser,false);
        jdbc.update("UPDATE platform_member SET session_version=session_version+1 WHERE id='http-member'");
        assertThat(get(browser,creatorBase.resolve("/creator-api/me")).statusCode()).as("Membership version change invalidates creator access on its next request").isEqualTo(401);
        var fresh=browser();login(fresh,true);
        jdbc.update("UPDATE platform_member SET enabled=false WHERE id='http-member'");
        assertThat(get(fresh,creatorBase.resolve("/creator-api/me")).statusCode()).as("Disabled member immediately loses creator access").isEqualTo(401);
    }
}
