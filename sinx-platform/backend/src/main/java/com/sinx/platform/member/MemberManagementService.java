package com.sinx.platform.member;

import com.sinx.platform.auth.AuthProperties;
import com.sinx.platform.auth.IdentityProvider;
import static com.sinx.platform.member.MemberAdminModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MemberManagementService {
 private static final int PAGE_SIZE=20;
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transactions;
 private final MemberRepository members;
 private final AuthProperties properties;
 private final List<IdentityProvider> providers;

 public MemberManagementService(JdbcTemplate jdbc,TransactionTemplate transactions,MemberRepository members,AuthProperties properties,List<IdentityProvider> providers) {
  this.jdbc=jdbc;this.transactions=transactions;this.members=members;this.properties=properties;this.providers=providers;
 }

 public Page<ManagedMember> listMembers(String query,int page,boolean enabledOnly) {
  int offset=offset(page);String name=query==null?"":query.trim();
  if(name.length()>200) throw failure(400,"invalid_request");
  String pattern="%"+name.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  String where=" WHERE display_name LIKE ? ESCAPE '!'"+(enabledOnly?" AND enabled=true":"");
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM platform_member"+where,Long.class,pattern);
  var items=jdbc.query("SELECT id FROM platform_member"+where+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",(rs,n)->rs.getString(1),pattern,PAGE_SIZE,offset)
   .stream().map(this::getMember).toList();
  return new Page<>(items,total,page,PAGE_SIZE);
 }

 public ManagedMember getMember(String id) {
  return jdbc.query("SELECT id,display_name,platform_role,enabled,created_at,last_login_at FROM platform_member WHERE id=?",
   (rs,n)->new ManagedMember(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),instant(rs,"created_at"),instant(rs,"last_login_at"),identities(id)),id)
   .stream().findFirst().orElseThrow(()->failure(404,"member_not_found"));
 }

 private List<IdentityView> identities(String memberId) {
  return jdbc.query("SELECT e.id,e.provider,e.tenant_id,e.subject_id,COALESCE(e.display_name,m.display_name) AS identity_name,e.linked_at FROM external_identity e JOIN platform_member m ON m.id=e.member_id WHERE e.member_id=? ORDER BY e.provider",
   (rs,n)->new IdentityView(rs.getString("id"),rs.getString("provider"),rs.getString("tenant_id"),rs.getString("subject_id"),rs.getString("identity_name"),instant(rs,"linked_at")),memberId);
 }

 public Page<PendingIdentity> listPending(int page) {
  int offset=offset(page);
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM pending_identity",Long.class);
  var items=jdbc.query("SELECT * FROM pending_identity ORDER BY last_seen_at DESC,id LIMIT ? OFFSET ?",(rs,n)->pending(rs),PAGE_SIZE,offset);
  return new Page<>(items,total,page,PAGE_SIZE);
 }

 public Page<AuditEntry> listAudit(int page) {
  int offset=offset(page);
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM member_audit",Long.class);
  var items=jdbc.query("SELECT * FROM member_audit ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
   (rs,n)->new AuditEntry(rs.getString("id"),rs.getString("actor_name"),rs.getString("action"),rs.getString("target_name"),rs.getString("detail"),instant(rs,"created_at")),PAGE_SIZE,offset);
  return new Page<>(items,total,page,PAGE_SIZE);
 }

 public ManagedMember create(Actor actor,MemberInput input) {
  var valid=validate(input);
  return write(actor,()->{
   String id=UUID.randomUUID().toString();
   jdbc.update("INSERT INTO platform_member(id,display_name,platform_role,enabled,last_login_at) VALUES (?,?,?,?,NULL)",id,valid.displayName(),valid.role(),valid.enabled());
   audit(actor,"MEMBER_CREATED",id,valid.displayName(),"角色："+roleName(valid.role())+"；状态："+statusName(valid.enabled()));
   return getMember(id);
  });
 }

 public ManagedMember update(Actor actor,String id,MemberInput input) {
  var valid=validate(input);
  return write(actor,()->{
   var previous=getMember(id);
   boolean permissionsChanged=!previous.role().equals(valid.role()) || previous.enabled()!=valid.enabled();
   boolean changed=permissionsChanged || !previous.displayName().equals(valid.displayName());
   if(!changed) return previous;
   jdbc.update("UPDATE platform_member SET display_name=?,platform_role=?,enabled=?,session_version=session_version+? WHERE id=?",
    valid.displayName(),valid.role(),valid.enabled(),permissionsChanged?1:0,id);
   ensureUsableAdministrator();
   audit(actor,"MEMBER_UPDATED",id,valid.displayName(),"姓名："+previous.displayName()+" → "+valid.displayName()+"；角色："+roleName(previous.role())+" → "+roleName(valid.role())+"；状态："+statusName(previous.enabled())+" → "+statusName(valid.enabled()));
   return getMember(id);
  });
 }

 public void bind(Actor actor,String pendingId,String memberId) {
  if(memberId==null || memberId.isBlank() || memberId.length()>36) throw failure(400,"invalid_request");
  write(actor,()->{
   var pending=jdbc.query("SELECT * FROM pending_identity WHERE id=?",(rs,n)->pending(rs),pendingId).stream().findFirst().orElseThrow(()->failure(404,"pending_not_found"));
   validateTenant(pending.provider(),pending.tenantId());
   var target=getMember(memberId);
   if(!target.enabled()) throw failure(409,"member_disabled");
   if(jdbc.queryForObject("SELECT COUNT(*) FROM external_identity WHERE member_id=? AND provider=? AND tenant_id=?",Integer.class,memberId,pending.provider(),pending.tenantId())>0)
    throw failure(409,"provider_already_linked");
   jdbc.update("INSERT INTO external_identity(id,provider,tenant_id,subject_id,member_id,display_name) VALUES (?,?,?,?,?,?)",
    pending.id(),pending.provider(),pending.tenantId(),pending.subjectId(),memberId,pending.displayName());
   jdbc.update("DELETE FROM pending_identity WHERE id=?",pendingId);
   revokeSessions(memberId);
   audit(actor,"IDENTITY_LINKED",memberId,target.displayName(),providerName(pending.provider())+"账号："+pending.displayName()+"（"+pending.subjectId()+"）");
   return null;
  });
 }

 public void unlink(Actor actor,String memberId,String identityId) {
  write(actor,()->{
   var target=getMember(memberId);
   var identity=target.identities().stream().filter(i->i.id().equals(identityId)).findFirst().orElseThrow(()->failure(404,"identity_not_found"));
   jdbc.update("DELETE FROM external_identity WHERE id=? AND member_id=?",identityId,memberId);
   ensureUsableAdministrator();
   jdbc.update("INSERT INTO pending_identity(id,provider,tenant_id,subject_id,display_name) VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),identity.provider(),identity.tenantId(),identity.subjectId(),identity.displayName());
   revokeSessions(memberId);
   audit(actor,"IDENTITY_UNLINKED",memberId,target.displayName(),providerName(identity.provider())+"账号："+identity.displayName()+"（"+identity.subjectId()+"）");
   return null;
  });
 }

 /** Explicit one-time deployment operation, never invoked by an OAuth callback. */
 public void bootstrap(BootstrapInput input) {
  transactions.executeWithoutResult(status->{
   lock();
   if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT bootstrap_completed FROM member_admin_guard WHERE id=1",Boolean.class))) return;
   validateTenant(input.provider(),input.tenantId());
   if(input.subjectId()==null || input.subjectId().isBlank() || input.subjectId().length()>128) throw failure(400,"invalid_bootstrap_identity");
   var valid=validate(new MemberInput(input.displayName(),"ADMIN",true));
   if(jdbc.queryForObject("SELECT COUNT(DISTINCT m.id) FROM platform_member m JOIN external_identity e ON e.member_id=m.id WHERE m.platform_role='ADMIN' AND m.enabled=true",Integer.class)>0)
    throw failure(409,"administrator_already_exists");
   var existing=jdbc.query("SELECT member_id FROM external_identity WHERE provider=? AND tenant_id=? AND subject_id=?",(rs,n)->rs.getString(1),input.provider(),input.tenantId(),input.subjectId());
   String id=input.memberId()==null || input.memberId().isBlank()?(existing.isEmpty()?UUID.randomUUID().toString():existing.get(0)):input.memberId();
   if(!existing.isEmpty() && !existing.get(0).equals(id)) throw failure(409,"identity_already_linked");
   if(input.memberId()!=null && !input.memberId().isBlank()) getMember(id);
   if(members.find(id).isEmpty()) jdbc.update("INSERT INTO platform_member(id,display_name,platform_role,last_login_at) VALUES (?,?,'ADMIN',NULL)",id,valid.displayName());
   else jdbc.update("UPDATE platform_member SET display_name=?,platform_role='ADMIN',enabled=true,session_version=session_version+1 WHERE id=?",valid.displayName(),id);
   if(existing.isEmpty()) jdbc.update("INSERT INTO external_identity(id,provider,tenant_id,subject_id,member_id,display_name) VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),input.provider(),input.tenantId(),input.subjectId(),id,valid.displayName());
   jdbc.update("DELETE FROM pending_identity WHERE provider=? AND tenant_id=? AND subject_id=?",input.provider(),input.tenantId(),input.subjectId());
   jdbc.update("UPDATE member_admin_guard SET bootstrap_completed=true WHERE id=1");
   audit(null,"ADMIN_BOOTSTRAPPED",id,valid.displayName(),"部署配置指定初始管理员并关联"+providerName(input.provider())+"身份");
  });
 }

 private <T> T write(Actor actor,Supplier<T> action) {
  return transactions.execute(status->{
   lock();
   var current=members.find(actor.id()).orElseThrow(()->failure(401,"unauthenticated"));
   if(!current.enabled() || current.sessionVersion()!=actor.sessionVersion()) throw failure(401,"unauthenticated");
   if(!"ADMIN".equals(current.role())) throw failure(403,"forbidden");
   return action.get();
  });
 }

 private void lock() {jdbc.queryForObject("SELECT id FROM member_admin_guard WHERE id=1 FOR UPDATE",Integer.class);}
 private void revokeSessions(String id) {jdbc.update("UPDATE platform_member SET session_version=session_version+1 WHERE id=?",id);}
 private void ensureUsableAdministrator() {
  var admins=jdbc.query("SELECT id FROM platform_member WHERE enabled=true AND platform_role='ADMIN'",(rs,n)->rs.getString(1));
  boolean usable=admins.stream().anyMatch(id->identities(id).stream().anyMatch(identity->providers.stream().anyMatch(p->p.enabled() && p.id().equals(identity.provider()) && tenant(p.id()).equals(identity.tenantId()))));
  if(!usable) throw failure(409,"last_administrator");
 }
 private String tenant(String provider) {
  var config=switch(provider) {case "feishu"->properties.auth().feishu();case "wecom"->properties.auth().wecom();default->throw failure(400,"invalid_provider");};
  return config.tenantId()==null?"":config.tenantId();
 }
 private void validateTenant(String provider,String tenantId) {
  String expected=tenant(provider);
  if(expected.isBlank() || !expected.equals(tenantId)) throw failure(409,"tenant_not_allowed");
 }
 private MemberInput validate(MemberInput input) {
  if(input==null || input.displayName()==null || input.displayName().isBlank() || input.displayName().trim().length()>200
     || input.displayName().chars().anyMatch(Character::isISOControl) || !("ADMIN".equals(input.role()) || "MEMBER".equals(input.role())) || input.enabled()==null)
   throw failure(400,"invalid_request");
  return new MemberInput(input.displayName().trim(),input.role(),input.enabled());
 }
 private void audit(Actor actor,String action,String targetId,String targetName,String detail) {
  String actorName=actor==null?"系统初始化":members.find(actor.id()).orElseThrow().displayName();
  jdbc.update("INSERT INTO member_audit(id,actor_id,actor_name,action,target_id,target_name,detail) VALUES (?,?,?,?,?,?,?)",
   UUID.randomUUID().toString(),actor==null?null:actor.id(),actorName,action,targetId,targetName,detail);
 }
 private static PendingIdentity pending(ResultSet rs) throws SQLException {
  return new PendingIdentity(rs.getString("id"),rs.getString("provider"),rs.getString("tenant_id"),rs.getString("subject_id"),rs.getString("display_name"),instant(rs,"first_seen_at"),instant(rs,"last_seen_at"));
 }
 private static Instant instant(ResultSet rs,String column) throws SQLException {var time=rs.getTimestamp(column);return time==null?null:time.toInstant();}
 private static int offset(int page) {if(page<1 || page>1000000) throw failure(400,"invalid_request");return (page-1)*PAGE_SIZE;}
 private static String roleName(String role) {return "ADMIN".equals(role)?"管理员":"普通成员";}
 private static String statusName(boolean enabled) {return enabled?"启用":"停用";}
 private static String providerName(String provider) {return "feishu".equals(provider)?"飞书":"企业微信";}
 private static MemberAccessException failure(int status,String code) {return new MemberAccessException(status,code);}
}
