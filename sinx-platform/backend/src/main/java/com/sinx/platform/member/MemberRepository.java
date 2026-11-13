package com.sinx.platform.member;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.sinx.platform.auth.AuthProperties;
import com.sinx.platform.auth.IdentityProvider.ExternalIdentity;
import com.sinx.platform.auth.LoginFailure;
@Repository
public class MemberRepository {
 public record Member(String id, String displayName, String role, boolean enabled, long sessionVersion) {}
 public static final String SESSION_VERSION="SINX_MEMBER_VERSION";
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transactions;
 private final AuthProperties properties;
 public MemberRepository(JdbcTemplate jdbc, TransactionTemplate transactions, AuthProperties properties) { this.jdbc=jdbc;this.transactions=transactions;this.properties=properties; }
 public Optional<Member> find(String id) {
  return jdbc.query("SELECT id,display_name,platform_role,enabled,session_version FROM platform_member WHERE id=?",(rs,n)->new Member(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getLong(5)),id).stream().findFirst();
 }
 private Optional<Member> findIdentity(ExternalIdentity i) {
  return jdbc.query("SELECT m.id,m.display_name,m.platform_role,m.enabled,m.session_version FROM platform_member m JOIN external_identity e ON e.member_id=m.id WHERE e.provider=? AND e.tenant_id=? AND e.subject_id=?",(rs,n)->new Member(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getLong(5)),i.provider(),i.tenant(),i.subject()).stream().findFirst();
 }
 public Member login(ExternalIdentity i) {
  var config=switch(i.provider()) {case "feishu" -> properties.auth().feishu();case "wecom" -> properties.auth().wecom();default -> throw new LoginFailure("provider_unavailable");};
  if(config.tenantId()==null || config.tenantId().isBlank() || !config.tenantId().equals(i.tenant())) throw new LoginFailure("tenant_not_allowed");
  if(i.subject()==null || i.subject().isBlank() || i.subject().length()>128 || i.displayName()==null || i.displayName().isBlank() || i.displayName().length()>200) throw new LoginFailure("identity_invalid");
  // Commit pending registrations before rejecting login; serialize with administrator identity changes.
  Member member=transactions.execute(status -> {
   jdbc.queryForObject("SELECT id FROM member_admin_guard WHERE id=1 FOR UPDATE",Integer.class);
   var existing=findIdentity(i);
   if(existing.isEmpty()) {
    int updated=jdbc.update("UPDATE pending_identity SET display_name=?,last_seen_at=CURRENT_TIMESTAMP WHERE provider=? AND tenant_id=? AND subject_id=?",i.displayName(),i.provider(),i.tenant(),i.subject());
    if(updated==0) jdbc.update("INSERT INTO pending_identity(id,provider,tenant_id,subject_id,display_name) VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),i.provider(),i.tenant(),i.subject(),i.displayName());
    return null;
   }
   Member found=existing.get();
   if(!found.enabled()) throw new LoginFailure("member_disabled");
   jdbc.update("UPDATE external_identity SET display_name=? WHERE provider=? AND tenant_id=? AND subject_id=?",i.displayName(),i.provider(),i.tenant(),i.subject());
   jdbc.update("UPDATE platform_member SET last_login_at=CURRENT_TIMESTAMP WHERE id=?",found.id());
   return found;
  });
  if(member==null) throw new LoginFailure("identity_unlinked");
  return member;
 }
}
