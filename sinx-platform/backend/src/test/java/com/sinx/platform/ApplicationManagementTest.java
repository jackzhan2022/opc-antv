package com.sinx.platform;

import com.sinx.platform.application.ApplicationModels.*;
import com.sinx.platform.application.ApplicationService;
import com.sinx.platform.member.MemberAccessException;
import com.sinx.platform.member.MemberAdminModels.Actor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"sinx.sso.enabled=true","sinx.sso.client-id=creator-platform","sinx.sso.client-secret=application-test-private-fixture-at-least-32-characters",
 "sinx.sso.redirect-uri=https://sinx.example.test/creator-api/login/oauth2/code/sinx","sinx.sso.application-url=https://sinx.example.test/creators/"})
@AutoConfigureMockMvc
class ApplicationManagementTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired ApplicationService applications;
 private final ObjectMapper json=new ObjectMapper();
 private final Actor admin=new Actor("app-admin",0);
 @BeforeEach void prepare() {
  jdbc.update("DELETE FROM member_application_access");jdbc.update("DELETE FROM application_audit");
  jdbc.update("DELETE FROM platform_application WHERE id<>'creator-platform'");
  jdbc.update("UPDATE platform_application SET name='创作者管理',description='创作者档案与作品资料',enabled=true,version=1,access_version=1,icon='users',sort_order=0 WHERE id='creator-platform'");
  jdbc.update("DELETE FROM external_identity");jdbc.update("DELETE FROM pending_identity");jdbc.update("DELETE FROM member_audit");jdbc.update("DELETE FROM platform_member");
  jdbc.update("INSERT INTO platform_member(id,display_name,platform_role,enabled) VALUES ('app-admin','管理员','ADMIN',true),('app-member','运营成员','MEMBER',true),('app-disabled','停用成员','MEMBER',false)");
 }
 @org.junit.jupiter.api.AfterEach void resetApplicationFixtures() {
  jdbc.update("DELETE FROM platform_application WHERE id<>'creator-platform'");
  jdbc.update("UPDATE platform_application SET name='创作者管理',description='创作者档案与作品资料',enabled=true,version=1,access_version=1,icon='users',sort_order=0 WHERE id='creator-platform'");
 }
 MockHttpSession session() {var session=new MockHttpSession();session.setAttribute("SINX_MEMBER_VERSION",0L);return session;}
 Map<String,Object> input(String code,String name,String url,boolean enabled,long version) {
  return Map.of("code",code,"name",name,"url",url,"description","应用说明","icon","grid","sortOrder",2,"enabled",enabled,"version",version);
 }
 Application create(String code) {return applications.create(admin,new Input(code,"资源中心","应用说明","https://apps.example.test/", "grid",2,true,null));}
 @Test void administratorRoleAndCsrfProtectEveryWrite() throws Exception {
  mvc.perform(get("/api/admin/applications")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/applications").session(session()).with(user("app-member").roles("MEMBER"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/applications").session(session()).with(user("app-admin").roles("ADMIN")).contentType("application/json").content(json.writeValueAsString(input("new-app","新应用","https://example.test",true,0)))).andExpect(status().isForbidden());
  mvc.perform(patch("/api/admin/applications/creator-platform/members/app-member").session(session()).with(user("app-admin").roles("ADMIN")).contentType("application/json").content("{\"granted\":true,\"version\":0}")).andExpect(status().isForbidden());
  assertThatThrownBy(()->applications.create(new Actor("app-member",0),new Input("bad-app","越权","","https://example.test","grid",0,true,null))).isInstanceOf(MemberAccessException.class).hasMessage("forbidden");
 }
 @Test void adminAlsoNeedsExplicitBusinessApplicationGrant() throws Exception {
  mvc.perform(get("/api/applications").session(session()).with(user("app-admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  applications.setAccess(admin,"creator-platform","app-admin",new GrantInput(true,0L));
  mvc.perform(get("/api/applications").session(session()).with(user("app-admin").roles("ADMIN"))).andExpect(jsonPath("$[0].id").value("creator-platform"));
 }
 @Test void createPersistsLinkApplicationAndOnlyAssignedMembersSeeIt() throws Exception {
  var result=mvc.perform(post("/api/admin/applications").session(session()).with(user("app-admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input("resource-hub","资源中心","https://apps.example.test/",true,0))))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.integrationType").value("LINK")).andExpect(jsonPath("$.grantedMembers").value(0)).andReturn();
  String id=json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(patch("/api/admin/applications/"+id+"/members/app-member").session(session()).with(user("app-admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"granted\":true,\"version\":0}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  assertThat(applications.catalog("app-member")).extracting(CatalogEntry::id).containsExactly(id);
  assertThat(applications.catalog("app-admin")).isEmpty();
  assertThat(applications.get(id).grantedMembers()).isEqualTo(1);
 }
 @Test void rejectsUnsafeUrlsDuplicateCodesAndMalformedInput() throws Exception {
  for(String url:List.of("javascript:alert(1)","http://example.test","https://name:secret@example.test/","data:text/html,hello")) {
   mvc.perform(post("/api/admin/applications").session(session()).with(user("app-admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input("unsafe-app","应用",url,true,0))))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_application_url"));
  }
  mvc.perform(post("/api/admin/applications").session(session()).with(user("app-admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("null")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/admin/applications").session(session()).with(user("app-admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input("creator-platform","重复","https://example.test",true,0)))).andExpect(status().isConflict());
 }
 @Test void updatesCheckVersionsAndProtectManagedIdentityAndAddress() {
  var app=applications.get("creator-platform");
  assertThatThrownBy(()->applications.update(admin,app.id(),new Input("other-code",app.name(),app.description(),app.url(),app.icon(),0,true,1L))).hasMessage("application_code_immutable");
  assertThatThrownBy(()->applications.update(admin,app.id(),new Input(app.code(),app.name(),app.description(),"https://other.example",app.icon(),0,true,1L))).hasMessage("application_url_managed");
  var updated=applications.update(admin,app.id(),new Input(app.code(),"创作者资源",app.description(),app.url(),app.icon(),1,true,1L));
  assertThat(updated.version()).isEqualTo(2);assertThat(updated.accessVersion()).isEqualTo(1);
  assertThatThrownBy(()->applications.update(admin,app.id(),new Input(app.code(),app.name(),app.description(),app.url(),app.icon(),0,true,1L))).hasMessage("application_conflict");
 }
 @Test void revokeAndRegrantKeepIndependentMonotonicGrantVersion() {
  var first=applications.setAccess(admin,"creator-platform","app-member",new GrantInput(true,0L));
  assertThat(first.version()).isEqualTo(1);
  assertThat(applications.access("app-member","creator-platform")).isPresent();
  applications.setAccess(admin,"creator-platform","app-member",new GrantInput(false,1L));
  assertThat(applications.access("app-member","creator-platform")).isEmpty();
  applications.setAccess(admin,"creator-platform","app-member",new GrantInput(true,2L));
  assertThat(applications.access("app-member","creator-platform").orElseThrow().grantVersion()).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT session_version FROM platform_member WHERE id='app-member'",Long.class)).isZero();
 }
 @Test void disabledMembersCannotReceiveGrantAndDisabledAppsStayHidden() {
  assertThatThrownBy(()->applications.setAccess(admin,"creator-platform","app-disabled",new GrantInput(true,0L))).hasMessage("member_disabled");
  applications.setAccess(admin,"creator-platform","app-member",new GrantInput(true,0L));
  var app=applications.get("creator-platform");
  var disabled=applications.update(admin,app.id(),new Input(app.code(),app.name(),app.description(),app.url(),app.icon(),0,false,1L));
  assertThat(disabled.accessVersion()).isEqualTo(2);assertThat(applications.catalog("app-member")).isEmpty();
  var enabled=applications.update(admin,app.id(),new Input(app.code(),app.name(),app.description(),app.url(),app.icon(),0,true,2L));
  assertThat(enabled.accessVersion()).isEqualTo(3);
 }
 @Test void filtersSearchAndPaginationReflectActualGrants() {
  applications.setAccess(admin,"creator-platform","app-member",new GrantInput(true,0L));
  assertThat(applications.memberAccess("creator-platform","","granted",1).items()).extracting(MemberAccess::id).containsExactly("app-member");
  assertThat(applications.memberAccess("creator-platform","%","all",1).total()).isZero();
  assertThat(applications.memberAccess("creator-platform","运营","all",1).total()).isEqualTo(1);
  assertThatThrownBy(()->applications.memberAccess("creator-platform","","all",0)).hasMessage("invalid_request");
  assertThat(applications.audit(1).items()).hasSize(1);
 }
 @Test void auditFailureRollsBackGrantAndConfiguration() {
  jdbc.execute("ALTER TABLE application_audit RENAME TO application_audit_unavailable");
  try {
   assertThatThrownBy(()->applications.setAccess(admin,"creator-platform","app-member",new GrantInput(true,0L))).isInstanceOf(org.springframework.dao.DataAccessException.class);
   assertThatThrownBy(()->create("rollback-app")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  } finally {jdbc.execute("ALTER TABLE application_audit_unavailable RENAME TO application_audit");}
  assertThat(applications.access("app-member","creator-platform")).isEmpty();
  assertThat(applications.list()).hasSize(1);
 }
 @Test void simultaneousChangesRejectStaleGrantAndAuditOnce() throws Exception {
  var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try {
   Callable<Integer> grant=()->{start.await();try {applications.setAccess(admin,"creator-platform","app-member",new GrantInput(true,0L));return 200;}catch(MemberAccessException error){return error.status();}};
   var first=executor.submit(grant);var second=executor.submit(grant);start.countDown();
   assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
   assertThat(applications.audit(1).items()).hasSize(1);
  } finally {executor.shutdownNow();}
 }
}
