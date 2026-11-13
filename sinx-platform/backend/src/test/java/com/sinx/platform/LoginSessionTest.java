package com.sinx.platform;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import com.sinx.platform.auth.IdentityProvider;
import com.sinx.platform.auth.IdentityProvider.ExternalIdentity;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"sinx.auth.feishu.tenant-id=company", "sinx.auth.feishu.admin-subjects=admin-subject"})
@AutoConfigureMockMvc
class LoginSessionTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @MockitoBean(name="feishuIdentityProvider") IdentityProvider feishu;
 @BeforeEach void setup() {
  jdbc.update("DELETE FROM external_identity"); jdbc.update("DELETE FROM pending_identity"); jdbc.update("DELETE FROM member_audit"); jdbc.update("DELETE FROM platform_member");
  when(feishu.id()).thenReturn("feishu"); when(feishu.displayName()).thenReturn("飞书"); when(feishu.enabled()).thenReturn(true);
  when(feishu.authorizationUrl(anyString(),nullable(String.class))).thenAnswer(i -> "https://identity.example/authorize?state=" + i.getArgument(0));
  when(feishu.authenticate("good-code")).thenReturn(new ExternalIdentity("feishu","company","user-1","林言"));
  jdbc.update("INSERT INTO platform_member(id,display_name,platform_role) VALUES ('member-1','平台林言','MEMBER')");
  jdbc.update("INSERT INTO external_identity(id,provider,tenant_id,subject_id,member_id) VALUES ('identity-1','feishu','company','user-1','member-1')");
 }
 record Attempt(MockHttpSession session, String state) {}
 Attempt start() throws Exception {
  var result=mvc.perform(get("/api/auth/feishu/start")).andExpect(status().isFound()).andReturn();
  String state=UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("state");
  assertThat(state).isNotBlank();
  return new Attempt((MockHttpSession)result.getRequest().getSession(false), state);
 }
 MockHttpSession login(Attempt a) throws Exception {
  return (MockHttpSession)mvc.perform(get("/api/auth/feishu/callback").session(a.session()).param("state",a.state()).param("code","good-code"))
   .andExpect(status().isFound()).andExpect(redirectedUrl("/workspace")).andReturn().getRequest().getSession(false);
 }
 @Test void successfulLoginPersistsMemberAndRotatesSession() throws Exception {
  Attempt a=start(); String oldId=a.session().getId(); var session=login(a);
  assertThat(session.getId()).isNotEqualTo(oldId);
  mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
   .andExpect(jsonPath("$.displayName").value("平台林言")).andExpect(jsonPath("$.role").value("MEMBER"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_member",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM external_identity",Integer.class)).isEqualTo(1);
 }
 @Test void repeatLoginUsesSameMember() throws Exception {
  login(start()); login(start());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_member",Integer.class)).isEqualTo(1);
 }
 @Test void invalidStateNeverCallsProvider() throws Exception {
  Attempt a=start();
  mvc.perform(get("/api/auth/feishu/callback").session(a.session()).param("state","wrong").param("code","good-code"))
   .andExpect(redirectedUrl("/login?error=invalid_state"));
  verify(feishu,never()).authenticate(anyString());
 }
 @Test void missingBrowserSessionRejectsState() throws Exception {
  Attempt a=start();
  mvc.perform(get("/api/auth/feishu/callback").param("state",a.state()).param("code","good-code"))
   .andExpect(redirectedUrl("/login?error=invalid_state"));
 }
 @Test void callbackCannotBeReplayed() throws Exception {
  Attempt a=start(); var session=login(a);
  mvc.perform(get("/api/auth/feishu/callback").session(session).param("state",a.state()).param("code","good-code"))
   .andExpect(redirectedUrl("/login?error=invalid_state"));
  verify(feishu,times(1)).authenticate("good-code");
 }
 @Test void cancelledAuthorizationConsumesState() throws Exception {
  Attempt a=start();
  mvc.perform(get("/api/auth/feishu/callback").session(a.session()).param("state",a.state()).param("error","access_denied"))
   .andExpect(redirectedUrl("/login?error=authorization_denied"));
  mvc.perform(get("/api/auth/feishu/callback").session(a.session()).param("state",a.state()).param("code","good-code"))
   .andExpect(redirectedUrl("/login?error=invalid_state"));
 }
 @Test void differentTenantIsRejectedWithoutCreatingUser() throws Exception {
  when(feishu.authenticate("good-code")).thenReturn(new ExternalIdentity("feishu","another-company","user-1","林言"));
  Attempt a=start();
  mvc.perform(get("/api/auth/feishu/callback").session(a.session()).param("state",a.state()).param("code","good-code"))
   .andExpect(redirectedUrl("/login?error=tenant_not_allowed"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_member",Integer.class)).isEqualTo(1);
 }
 @Test void disabledMemberCannotLoginOrContinueUsingSession() throws Exception {
  var session=login(start());
  jdbc.update("UPDATE platform_member SET enabled=false");
  mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
  Attempt a=start();
  mvc.perform(get("/api/auth/feishu/callback").session(a.session()).param("state",a.state()).param("code","good-code"))
   .andExpect(redirectedUrl("/login?error=member_disabled"));
 }
 @Test void logoutInvalidatesSessionWithActualCsrfToken() throws Exception {
  var session=login(start());
  mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
  var csrf=mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
  Map<?,?> token=new tools.jackson.databind.ObjectMapper().readValue(csrf.getResponse().getContentAsString(),Map.class);
  mvc.perform(post("/api/auth/logout").session(session).header(token.get("headerName").toString(),token.get("token")))
   .andExpect(status().isNoContent());
  assertThat(session.isInvalid()).isTrue();
  mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
 }
 @Test void unknownIdentityRequiresLinkingAndDoesNotCreateMember() throws Exception {
  when(feishu.authenticate("good-code")).thenReturn(new ExternalIdentity("feishu","company","unlinked-user","未关联员工"));
  for(int i=0;i<2;i++) {
   Attempt a=start();
   var result=mvc.perform(get("/api/auth/feishu/callback").session(a.session()).param("state",a.state()).param("code","good-code"))
    .andExpect(redirectedUrl("/login?error=identity_unlinked")).andReturn();
   var check=get("/api/me");
   var remaining=(MockHttpSession)result.getRequest().getSession(false);if(remaining!=null)check.session(remaining);
   mvc.perform(check).andExpect(status().isUnauthorized());
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_member",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_identity WHERE subject_id='unlinked-user'",Integer.class)).isEqualTo(1);
 }
 @Test void legacyAdminSubjectDoesNotBypassLinkRequirement() throws Exception {
  when(feishu.authenticate("good-code")).thenReturn(new ExternalIdentity("feishu","company","admin-subject","管理员"));
  Attempt a=start();
  mvc.perform(get("/api/auth/feishu/callback").session(a.session()).param("state",a.state()).param("code","good-code"))
   .andExpect(redirectedUrl("/login?error=identity_unlinked"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_member WHERE platform_role='ADMIN'",Integer.class)).isZero();
 }
 @Test void unlinkedAuthorizationClearsAnyPreviouslyAuthenticatedSession() throws Exception {
  var signedIn=login(start());
  when(feishu.authenticate("unknown-code")).thenReturn(new ExternalIdentity("feishu","company","unknown-user","未关联"));
  var start=mvc.perform(get("/api/auth/feishu/start").session(signedIn)).andReturn();
  String state=UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("state");
  mvc.perform(get("/api/auth/feishu/callback").session(signedIn).param("state",state).param("code","unknown-code"))
   .andExpect(redirectedUrl("/login?error=identity_unlinked"));
  assertThat(signedIn.isInvalid()).isTrue();
 }
}
