package com.sinx.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.sinx.platform.auth.IdentityProvider;
import org.springframework.web.util.UriComponentsBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"sinx.sso.enabled=true","sinx.sso.client-id=creator-platform","sinx.sso.client-secret=creator-test-secret-with-at-least-32-characters",
 "sinx.sso.redirect-uri=https://sinx.example.test/creator-api/login/oauth2/code/sinx",
 "sinx.sso.application-url=https://sinx.example.test/creators/","sinx.auth.feishu.tenant-id=company"})
@AutoConfigureMockMvc
class SsoFlowTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
 org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations;
 @MockitoBean(name="feishuIdentityProvider") IdentityProvider feishu;
 static final String SECRET="creator-test-secret-with-at-least-32-characters";
 static final String CALLBACK="https://sinx.example.test/creator-api/login/oauth2/code/sinx";
 static final String VERIFIER="a-compliant-pkce-verifier-which-has-more-than-forty-three-characters";
 @BeforeEach void prepare() {
  if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='oauth2_authorization'",Integer.class)>0)jdbc.update("DELETE FROM oauth2_authorization");
  jdbc.update("DELETE FROM external_identity");
  jdbc.update("DELETE FROM pending_identity");jdbc.update("DELETE FROM member_audit");jdbc.update("DELETE FROM platform_member");
  jdbc.update("INSERT INTO platform_member(id,display_name,platform_role) VALUES ('sso-member','平台成员','MEMBER')");
  jdbc.update("INSERT INTO external_identity(id,provider,tenant_id,subject_id,member_id) VALUES ('sso-identity','feishu','company','subject','sso-member')");
  when(feishu.id()).thenReturn("feishu");when(feishu.displayName()).thenReturn("飞书");when(feishu.enabled()).thenReturn(true);
  when(feishu.authorizationUrl(anyString(),nullable(String.class))).thenAnswer(i->"https://identity.example/authorize?state="+i.getArgument(0));
  when(feishu.authenticate("good-code")).thenReturn(new IdentityProvider.ExternalIdentity("feishu","company","subject","平台成员"));
 }
 String challenge() throws Exception {return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize() throws Exception {
  return get("/api/sso/authorize").servletPath("/api/sso/authorize").queryParam("client_id","creator-platform").queryParam("response_type","code")
   .queryParam("redirect_uri",CALLBACK).queryParam("scope","creator").queryParam("state","client-state")
   .queryParam("code_challenge",challenge()).queryParam("code_challenge_method","S256");
 }
 String code() throws Exception {
  var session=new MockHttpSession();session.setAttribute("SINX_MEMBER_VERSION",0L);
  var authentication=UsernamePasswordAuthenticationToken.authenticated("sso-member",null,List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
  var result=mvc.perform(authorize().session(session).with(authentication(authentication))).andExpect(status().isFound()).andReturn();
  String target=result.getResponse().getRedirectedUrl();assertThat(target).startsWith(CALLBACK+"?");
  var query=UriComponentsBuilder.fromUriString(target).build().getQueryParams();
  assertThat(query.getFirst("state")).isEqualTo("client-state");
  assertThat(query.getFirst("access_token")).isNull();return query.getFirst("code");
 }
 String token() throws Exception {
  var result=mvc.perform(post("/api/sso/token").with(httpBasic("creator-platform",SECRET))
    .param("grant_type","authorization_code").param("code",code()).param("redirect_uri",CALLBACK).param("code_verifier",VERIFIER))
   .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("creator")).andReturn();
  return JsonPath.read(result.getResponse().getContentAsString(),"$.access_token");
 }
 @Test void anonymousAuthorizationReturnsToLogin() throws Exception {
  mvc.perform(authorize()).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
 }
 @Test void authorizationCodeProducesServerTokenAndCurrentProfile() throws Exception {
  String token=token();
  mvc.perform(get("/api/sso/userinfo").header("Authorization","Bearer "+token)).andExpect(status().isOk())
   .andExpect(jsonPath("$.sub").value("sso-member")).andExpect(jsonPath("$.displayName").value("平台成员"));
  mvc.perform(post("/api/sso/introspect").with(httpBasic("creator-platform",SECRET)).param("token",token))
   .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.role").value("MEMBER"));
  mvc.perform(get("/api/sso/members").header("Authorization","Bearer "+token))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("sso-member"));
 }
 @Test void codeCannotBeReplayedAndWrongVerifierFails() throws Exception {
  String code=code();
  mvc.perform(post("/api/sso/token").with(httpBasic("creator-platform",SECRET)).param("grant_type","authorization_code")
    .param("code",code).param("redirect_uri",CALLBACK).param("code_verifier","wrong-verifier"))
   .andExpect(status().isBadRequest());
  String once=code();
  var request=post("/api/sso/token").with(httpBasic("creator-platform",SECRET)).param("grant_type","authorization_code")
    .param("code",once).param("redirect_uri",CALLBACK).param("code_verifier",VERIFIER);
  mvc.perform(request).andExpect(status().isOk());
  mvc.perform(request).andExpect(status().isBadRequest());
 }
 @Test void memberVersionChangeInvalidatesIssuedTokenAndPendingCode() throws Exception {
  String token=token();String code=code();
  jdbc.update("UPDATE platform_member SET session_version=session_version+1,platform_role='ADMIN' WHERE id='sso-member'");
  mvc.perform(post("/api/sso/introspect").with(httpBasic("creator-platform",SECRET)).param("token",token))
   .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
  mvc.perform(get("/api/sso/userinfo").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/sso/token").with(httpBasic("creator-platform",SECRET)).param("grant_type","authorization_code")
    .param("code",code).param("redirect_uri",CALLBACK).param("code_verifier",VERIFIER))
   .andExpect(status().isBadRequest());
 }
 @Test void disabledMemberAndRevokedTokenCannotReadProfile() throws Exception {
  String token=token();jdbc.update("UPDATE platform_member SET enabled=false WHERE id='sso-member'");
  mvc.perform(get("/api/sso/userinfo").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  jdbc.update("UPDATE platform_member SET enabled=true WHERE id='sso-member'");
  mvc.perform(post("/api/sso/revoke").with(httpBasic("creator-platform",SECRET)).param("token",token)).andExpect(status().isOk());
  mvc.perform(get("/api/sso/userinfo").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void tokenEndpointsRequireClientAuthenticationAndUserInfoRequiresBearer() throws Exception {
  mvc.perform(post("/api/sso/introspect").param("token","unknown")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/sso/userinfo")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/sso/userinfo").header("Authorization","Bearer unknown")).andExpect(status().isUnauthorized());
 }
 @Test void workspaceExposesRealConfiguredCreatorApplication() throws Exception {
  var session=new MockHttpSession();session.setAttribute("SINX_MEMBER_VERSION",0L);
  mvc.perform(get("/api/applications").session(session).with(user("sso-member").roles("MEMBER")))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].url").value("https://sinx.example.test/creators/"));
 }
 @Test void enterpriseLoginResumesSavedAuthorizationAfterSessionRotation() throws Exception {
  var unauthorized=mvc.perform(authorize()).andExpect(status().isFound()).andReturn();
  var original=(MockHttpSession)unauthorized.getRequest().getSession(false);
  var begin=mvc.perform(get("/api/auth/feishu/start").session(original)).andReturn();
  String state=UriComponentsBuilder.fromUriString(begin.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("state");
  var callback=mvc.perform(get("/api/auth/feishu/callback").session(original).param("state",state).param("code","good-code"))
   .andExpect(status().isFound()).andReturn();
  assertThat(original.isInvalid()).isTrue();
  String target=callback.getResponse().getRedirectedUrl();
  assertThat(target).startsWith("/api/sso/authorize?");
  var authenticated=(MockHttpSession)callback.getRequest().getSession(false);
  mvc.perform(get(java.net.URI.create(target)).servletPath("/api/sso/authorize").session(authenticated))
   .andExpect(status().isFound()).andExpect(redirectedUrlPattern(CALLBACK+"?code=*&state=client-state"));
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"/api/sso/token","/api/sso/%74oken"})
 void simultaneousCodeRedemptionHasExactlyOneWinner(String endpoint) throws Exception {
  String once=code();
  var firstRead=new java.util.concurrent.CountDownLatch(2);
  doAnswer(invocation->{
   Object result=invocation.callRealMethod();
   if(once.equals(invocation.getArgument(0))) {
    firstRead.countDown();firstRead.await(350,java.util.concurrent.TimeUnit.MILLISECONDS);
   }
   return result;
  }).when(authorizations).findByToken(eq(once),any());
  var start=new java.util.concurrent.CountDownLatch(1);
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   java.util.concurrent.Callable<Integer> redeem=()->{
    start.await();
    return mvc.perform(post(java.net.URI.create(endpoint)).with(httpBasic("creator-platform",SECRET))
     .param("grant_type","authorization_code").param("code",once).param("redirect_uri",CALLBACK).param("code_verifier",VERIFIER))
     .andReturn().getResponse().getStatus();
   };
   var a=executor.submit(redeem);var b=executor.submit(redeem);start.countDown();
   assertThat(List.of(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS)))
    .containsExactlyInAnyOrder(200,400);
  } finally {executor.shutdownNow();}
 }
}
