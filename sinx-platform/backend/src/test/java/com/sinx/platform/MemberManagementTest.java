package com.sinx.platform;

import com.sinx.platform.auth.IdentityProvider;
import com.sinx.platform.auth.IdentityProvider.ExternalIdentity;
import java.util.Map;
import java.util.concurrent.Executors;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"sinx.auth.feishu.tenant-id=company","sinx.auth.wecom.tenant-id=corp"})
@AutoConfigureMockMvc
class MemberManagementTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired com.sinx.platform.member.MemberManagementService management;
 @MockitoBean(name="feishuIdentityProvider") IdentityProvider feishu;
 @MockitoBean(name="wecomIdentityProvider") IdentityProvider wecom;
 @BeforeEach void setup() {
  jdbc.update("DELETE FROM external_identity");jdbc.update("DELETE FROM pending_identity");
  jdbc.update("DELETE FROM member_audit");jdbc.update("DELETE FROM platform_member");
  for(var p:new IdentityProvider[]{feishu,wecom}) {
   String id=p==feishu?"feishu":"wecom";
   when(p.id()).thenReturn(id);when(p.displayName()).thenReturn(id);when(p.enabled()).thenReturn(true);
   when(p.authorizationUrl(anyString(),nullable(String.class))).thenAnswer(i->"https://identity.example/authorize?state="+i.getArgument(0));
  }
  seed("admin","ADMIN","feishu","company","admin");
  seed("member","MEMBER","feishu","company","member");
 }
 void seed(String id,String role,String provider,String tenant,String subject) {
  jdbc.update("INSERT INTO platform_member(id,display_name,platform_role) VALUES (?,?,?)",id,id,role);
  jdbc.update("INSERT INTO external_identity(id,provider,tenant_id,subject_id,member_id,display_name) VALUES (?,?,?,?,?,?)",
   "identity-"+id,provider,tenant,subject,id,id);
 }
 MockHttpSession authorize(String provider,String tenant,String subject,String destination) throws Exception {
  var p=provider.equals("feishu")?feishu:wecom;
  when(p.authenticate(subject)).thenReturn(new ExternalIdentity(provider,tenant,subject,"外部"+subject));
  var start=mvc.perform(get("/api/auth/"+provider+"/start")).andReturn();
  String state=UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("state");
  var callback=mvc.perform(get("/api/auth/"+provider+"/callback").session((MockHttpSession)start.getRequest().getSession(false)).param("state",state).param("code",subject))
   .andExpect(redirectedUrl(destination)).andReturn();
  return (MockHttpSession)callback.getRequest().getSession(false);
 }
 MockHttpSession login(String subject) throws Exception {return authorize("feishu","company",subject,"/workspace");}
 String pending(String provider,String tenant,String subject) throws Exception {
  authorize(provider,tenant,subject,"/login?error=identity_unlinked");
  return jdbc.queryForObject("SELECT id FROM pending_identity WHERE provider=? AND tenant_id=? AND subject_id=?",String.class,provider,tenant,subject);
 }
 String create(MockHttpSession admin,String name) throws Exception {
  var result=mvc.perform(post("/api/admin/members").session(admin).with(csrf()).contentType("application/json")
   .content("{\"displayName\":\""+name+"\",\"role\":\"MEMBER\",\"enabled\":true}"))
   .andExpect(status().isCreated()).andReturn();
  return new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText();
 }
 @Test void managementRequiresRealAdministratorAndCsrf() throws Exception {
  mvc.perform(get("/api/admin/members")).andExpect(status().isUnauthorized());
  var member=login("member");
  for(String path:new String[]{"members","pending-identities","audit"})
   mvc.perform(get("/api/admin/"+path).session(member)).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/members").session(member).with(csrf()).contentType("application/json")
   .content("{\"displayName\":\"越权\",\"role\":\"ADMIN\",\"enabled\":true}")).andExpect(status().isForbidden());
  var admin=login("admin");
  mvc.perform(post("/api/admin/members").session(admin).contentType("application/json").content("{}"))
   .andExpect(status().isForbidden());
 }
 @Test void administratorCreatesMemberAndLinksTwoChannelsToSameAccount() throws Exception {
  var admin=login("admin");String id=create(admin,"统一成员");
  for(var source:new String[][]{{"feishu","company","new-feishu"},{"wecom","corp","new-wecom"}}) {
   String pending=pending(source[0],source[1],source[2]);
   mvc.perform(post("/api/admin/pending-identities/"+pending+"/bind").session(admin).with(csrf())
    .contentType("application/json").content("{\"memberId\":\""+id+"\"}")).andExpect(status().isNoContent());
   var session=authorize(source[0],source[1],source[2],"/workspace");
   mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.displayName").value("统一成员"));
  }
  mvc.perform(get("/api/admin/members").session(admin).param("q","统一"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].identities.length()").value(2));
  mvc.perform(get("/api/admin/audit").session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
 }
 @Test void linkingConflictAndDisabledTargetDoNotConsumePendingIdentity() throws Exception {
  var admin=login("admin");String id=pending("feishu","company","another");
  mvc.perform(post("/api/admin/pending-identities/"+id+"/bind").session(admin).with(csrf()).contentType("application/json")
   .content("{\"memberId\":\"member\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("provider_already_linked"));
  jdbc.update("UPDATE platform_member SET enabled=false WHERE id='member'");
  mvc.perform(post("/api/admin/pending-identities/"+id+"/bind").session(admin).with(csrf()).contentType("application/json")
   .content("{\"memberId\":\"member\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("member_disabled"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_identity WHERE id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void lastUsableAdministratorCannotBeDisabledDemotedOrUnlinked() throws Exception {
  var admin=login("admin");
  for(String body:new String[]{"{\"displayName\":\"admin\",\"role\":\"MEMBER\",\"enabled\":true}","{\"displayName\":\"admin\",\"role\":\"ADMIN\",\"enabled\":false}"})
   mvc.perform(patch("/api/admin/members/admin").session(admin).with(csrf()).contentType("application/json").content(body))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("last_administrator"));
  // An unlinked or unavailable administrator must not defeat the last-login protection.
  jdbc.update("INSERT INTO platform_member(id,display_name,platform_role) VALUES ('unlinked-admin','unlinked','ADMIN')");
  mvc.perform(delete("/api/admin/members/admin/identities/identity-admin").session(admin).with(csrf()))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("last_administrator"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_audit",Integer.class)).isZero();
 }
 @Test void concurrentDemotionsCannotRemoveBothAdministrators() throws Exception {
  seed("admin2","ADMIN","feishu","company","admin2");
  var first=login("admin");var second=login("admin2");
  var executor=Executors.newFixedThreadPool(2);
  try {
   var a=executor.submit(()->mvc.perform(patch("/api/admin/members/admin").session(first).with(csrf()).contentType("application/json")
    .content("{\"displayName\":\"admin\",\"role\":\"MEMBER\",\"enabled\":true}")).andReturn().getResponse().getStatus());
   var b=executor.submit(()->mvc.perform(patch("/api/admin/members/admin2").session(second).with(csrf()).contentType("application/json")
    .content("{\"displayName\":\"admin2\",\"role\":\"MEMBER\",\"enabled\":true}")).andReturn().getResponse().getStatus());
   assertThat(java.util.List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_member WHERE platform_role='ADMIN' AND enabled=true",Integer.class)).isEqualTo(1);
  } finally {executor.shutdownNow();}
 }
 @Test void roleChangeInvalidatesPreviouslyAuthenticatedAdministrator() throws Exception {
  seed("admin2","ADMIN","feishu","company","admin2");
  var operator=login("admin");var old=login("admin2");
  mvc.perform(patch("/api/admin/members/admin2").session(operator).with(csrf()).contentType("application/json")
   .content("{\"displayName\":\"admin2\",\"role\":\"MEMBER\",\"enabled\":true}")).andExpect(status().isOk());
  mvc.perform(get("/api/admin/members").session(old)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/members").session(login("admin2"))).andExpect(status().isForbidden());
 }
 @Test void disableAndReenableDoNotRestoreOldSession() throws Exception {
  var admin=login("admin");var old=login("member");
  for(boolean enabled:new boolean[]{false,true})
   mvc.perform(patch("/api/admin/members/member").session(admin).with(csrf()).contentType("application/json")
    .content("{\"displayName\":\"member\",\"role\":\"MEMBER\",\"enabled\":"+enabled+"}")).andExpect(status().isOk());
  mvc.perform(get("/api/me").session(old)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/me").session(login("member"))).andExpect(status().isOk());
 }
 @Test void unlinkRevokesSessionAndRequiresManualRelinking() throws Exception {
  var admin=login("admin");var old=login("member");
  mvc.perform(delete("/api/admin/members/member/identities/identity-member").session(admin).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/me").session(old)).andExpect(status().isUnauthorized());
  authorize("feishu","company","member","/login?error=identity_unlinked");
  mvc.perform(get("/api/admin/pending-identities").session(admin)).andExpect(jsonPath("$.total").value(1));
 }
 @Test void invalidFieldsAndPaginationAreRejected() throws Exception {
  var admin=login("admin");
  for(String body:new String[]{"{}","{\"displayName\":\"   \",\"role\":\"MEMBER\",\"enabled\":true}","{\"displayName\":\"name\",\"role\":\"OWNER\",\"enabled\":true}"})
   mvc.perform(post("/api/admin/members").session(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/admin/members").session(admin).param("page","0")).andExpect(status().isBadRequest());
  mvc.perform(patch("/api/admin/members/missing").session(admin).with(csrf()).contentType("application/json")
   .content("{\"displayName\":\"name\",\"role\":\"MEMBER\",\"enabled\":true}")).andExpect(status().isNotFound());
 }
 @Test void bootstrapCanPrelinkExplicitLegacyAdministratorAfterMigrationClearsLinks() {
  jdbc.update("DELETE FROM external_identity");
  jdbc.update("UPDATE member_admin_guard SET bootstrap_completed=false WHERE id=1");
  management.bootstrap(new com.sinx.platform.member.MemberAdminModels.BootstrapInput("feishu","company","admin","管理员","admin"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM external_identity WHERE member_id='admin' AND subject_id='admin'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT bootstrap_completed FROM member_admin_guard WHERE id=1",Boolean.class)).isTrue();
 }
}
