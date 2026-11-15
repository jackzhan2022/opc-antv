package com.sinx.platform.application;

import static com.sinx.platform.application.ApplicationModels.*;
import com.sinx.platform.member.MemberAccessException;
import com.sinx.platform.member.MemberAdminModels.Actor;
import com.sinx.platform.member.MemberAdminModels.Page;
import com.sinx.platform.member.MemberRepository;
import com.sinx.platform.sso.SsoProperties;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ApplicationService {
 public static final String CREATOR_ID="creator-platform";
 private static final Set<String> ICONS=Set.of("users","grid","document","video","chart");
 private static final String SELECT="SELECT a.*,(SELECT COUNT(*) FROM member_application_access x JOIN platform_member p ON p.id=x.member_id WHERE x.application_id=a.id AND x.granted=true AND p.enabled=true) AS granted_members FROM platform_application a";
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transactions;
 private final MemberRepository members;
 private final SsoProperties sso;
 public ApplicationService(JdbcTemplate jdbc,TransactionTemplate transactions,MemberRepository members,SsoProperties sso) {
  this.jdbc=jdbc;this.transactions=transactions;this.members=members;this.sso=sso;
 }
 public List<CatalogEntry> catalog(String memberId) {
  return jdbc.query(SELECT+" JOIN member_application_access g ON g.application_id=a.id JOIN platform_member m ON m.id=g.member_id WHERE g.member_id=? AND g.granted=true AND m.enabled=true AND a.enabled=true ORDER BY a.sort_order,a.created_at,a.id",
   this::application,memberId).stream().filter(Application::connected)
   .map(a->new CatalogEntry(a.id(),a.name(),a.description(),a.url(),a.icon())).toList();
 }
 /** A single joined read keeps enablement and the grant generation consistent. */
 public Optional<AccessStamp> access(String memberId,String applicationId) {
  if(CREATOR_ID.equals(applicationId) && !sso.enabled())return Optional.empty();
  return jdbc.query("SELECT a.access_version,g.version FROM platform_application a JOIN member_application_access g ON g.application_id=a.id JOIN platform_member m ON m.id=g.member_id WHERE a.id=? AND g.member_id=? AND a.enabled=true AND g.granted=true AND m.enabled=true",
   (rs,n)->new AccessStamp(rs.getLong(1),rs.getLong(2)),applicationId,memberId).stream().findFirst();
 }
 public List<Application> list() {
  return jdbc.query(SELECT+" ORDER BY a.sort_order,a.created_at,a.id",this::application);
 }
 public Application get(String id) {
  return jdbc.query(SELECT+" WHERE a.id=?",this::application,id).stream().findFirst()
   .orElseThrow(()->error(404,"application_not_found"));
 }
 private Application application(ResultSet rs,int index) throws SQLException {
  String id=rs.getString("id"),type=rs.getString("integration_type");
  boolean connected="LINK".equals(type) || (CREATOR_ID.equals(id) && sso.enabled());
  String url="SSO".equals(type)?sso.applicationUrl():rs.getString("entry_url");
  long count=rs.getLong("granted_members");
  return new Application(id,rs.getString("code"),rs.getString("name"),rs.getString("description"),url,type,rs.getString("icon"),
   rs.getInt("sort_order"),rs.getBoolean("enabled"),connected,rs.getLong("version"),rs.getLong("access_version"),count);
 }
 public Application create(Actor actor,Input input) {
  var valid=validate(input,false);
  return write(actor,()->{
   String id=UUID.randomUUID().toString();
   jdbc.update("INSERT INTO platform_application(id,code,name,description,entry_url,integration_type,icon,sort_order,enabled) VALUES (?,?,?,?,?,'LINK',?,?,?)",
    id,valid.code(),valid.name(),valid.description(),valid.url(),valid.icon(),valid.sortOrder(),valid.enabled());
   audit(actor,"APPLICATION_CREATED",id,valid.name(),null,"登记链接应用，尚未分配成员");
   return get(id);
  });
 }
 public Application update(Actor actor,String id,Input input) {
  if(input==null)throw error(400,"invalid_request");
  return write(actor,()->{
   var previous=get(id);
   var valid=validate(input,"SSO".equals(previous.integrationType()));
   if(valid.version()==null || valid.version()!=previous.version())throw error(409,"application_conflict");
   if(!previous.code().equals(valid.code()))throw error(409,"application_code_immutable");
   if("SSO".equals(previous.integrationType()) && !previous.url().equals(valid.url()))throw error(409,"application_url_managed");
   boolean accessChanged=previous.enabled()!=valid.enabled();
   boolean changed=accessChanged || !previous.name().equals(valid.name()) || !previous.description().equals(valid.description())
    || !previous.url().equals(valid.url()) || !previous.icon().equals(valid.icon()) || previous.sortOrder()!=valid.sortOrder();
   if(!changed)return previous;
   jdbc.update("UPDATE platform_application SET name=?,description=?,entry_url=?,icon=?,sort_order=?,enabled=?,version=version+1,access_version=access_version+?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
    valid.name(),valid.description(),"SSO".equals(previous.integrationType())?"":valid.url(),valid.icon(),valid.sortOrder(),valid.enabled(),accessChanged?1:0,id);
   audit(actor,"APPLICATION_UPDATED",id,valid.name(),null,accessChanged?(valid.enabled()?"启用应用":"停用应用，既有业务令牌失效"):"更新应用展示信息");
   return get(id);
  });
 }
 public Page<MemberAccess> memberAccess(String id,String query,String filter,int page) {
  get(id);
  if(page<1 || page>100000 || query==null || query.length()>200 || !Set.of("all","granted","ungranted").contains(filter))throw error(400,"invalid_request");
  String pattern="%"+query.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  String from=" FROM platform_member m LEFT JOIN member_application_access g ON g.member_id=m.id AND g.application_id=? WHERE m.display_name LIKE ? ESCAPE '!'";
  if("granted".equals(filter))from+=" AND g.granted=true";
  if("ungranted".equals(filter))from+=" AND (g.granted IS NULL OR g.granted=false)";
  long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,id,pattern);
  var items=jdbc.query("SELECT m.id,m.display_name,m.platform_role,m.enabled,COALESCE(g.granted,false),COALESCE(g.version,0)"+from+" ORDER BY m.display_name,m.id LIMIT 20 OFFSET ?",
   (rs,n)->new MemberAccess(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getBoolean(5),rs.getLong(6)),id,pattern,(page-1)*20);
  return new Page<>(items,total,page,20);
 }
 public MemberAccess setAccess(Actor actor,String applicationId,String memberId,GrantInput input) {
  if(input==null || input.granted()==null || input.version()==null || input.version()<0)throw error(400,"invalid_request");
  return write(actor,()->{
   var app=get(applicationId);
   var target=members.find(memberId).orElseThrow(()->error(404,"member_not_found"));
   if(input.granted() && !target.enabled())throw error(409,"member_disabled");
   var current=jdbc.query("SELECT granted,version FROM member_application_access WHERE application_id=? AND member_id=?",
    (rs,n)->new GrantInput(rs.getBoolean(1),rs.getLong(2)),applicationId,memberId).stream().findFirst();
   long version=current.map(GrantInput::version).orElse(0L);
   if(version!=input.version())throw error(409,"application_conflict");
   boolean previous=current.map(GrantInput::granted).orElse(false);
   if(previous!=input.granted()) {
    if(current.isEmpty())jdbc.update("INSERT INTO member_application_access(application_id,member_id,granted) VALUES (?,?,?)",applicationId,memberId,input.granted());
    else jdbc.update("UPDATE member_application_access SET granted=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE application_id=? AND member_id=?",input.granted(),applicationId,memberId);
    version++;
    audit(actor,input.granted()?"ACCESS_GRANTED":"ACCESS_REVOKED",applicationId,app.name(),target.displayName(),input.granted()?"分配应用访问权限":"撤销应用访问权限，旧授权码及业务令牌失效");
   }
   return new MemberAccess(target.id(),target.displayName(),target.role(),target.enabled(),input.granted(),version);
  });
 }
 public Page<Audit> audit(int page) {
  if(page<1 || page>100000)throw error(400,"invalid_request");
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM application_audit",Long.class);
  var items=jdbc.query("SELECT * FROM application_audit ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",
   (rs,n)->new Audit(rs.getString("id"),rs.getString("actor_name"),rs.getString("action"),rs.getString("application_name"),rs.getString("member_name"),rs.getString("detail"),rs.getTimestamp("created_at").toInstant()),(page-1)*20);
  return new Page<>(items,total,page,20);
 }
 private Input validate(Input input,boolean managedUrl) {
  if(input==null || input.enabled()==null || input.sortOrder()==null || input.sortOrder()<0 || input.sortOrder()>9999)throw error(400,"invalid_request");
  String code=clean(input.code(),64,true),name=clean(input.name(),100,true),description=clean(input.description(),500,false,true),url=clean(input.url(),2048,true);
  if(!code.matches("[a-z][a-z0-9-]{1,63}") || !ICONS.contains(input.icon()==null?"":input.icon()))throw error(400,"invalid_request");
  if(!managedUrl) {
   try {
    URI uri=URI.create(url);
    boolean local="http".equals(uri.getScheme()) && Set.of("localhost","127.0.0.1").contains(uri.getHost()==null?"":uri.getHost());
    if(uri.getHost()==null || uri.getUserInfo()!=null || !("https".equals(uri.getScheme()) || local))throw error(400,"invalid_application_url");
   } catch(IllegalArgumentException invalid) {throw error(400,"invalid_application_url");}
  }
  return new Input(code,name,description,url,input.icon(),input.sortOrder(),input.enabled(),input.version());
 }
 private String clean(String value,int max,boolean required) {
  return clean(value,max,required,false);
 }
 private String clean(String value,int max,boolean required,boolean multiline) {
  if(value==null || value.length()>max || value.chars().anyMatch(c->(c<32 && !(multiline && (c=='\n' || c=='\r'))) || c==127))throw error(400,"invalid_request");
  value=value.trim();if(required && value.isEmpty())throw error(400,"invalid_request");return value;
 }
 private <T> T write(Actor actor,Supplier<T> operation) {
  return transactions.execute(status->{
   jdbc.queryForObject("SELECT id FROM member_admin_guard WHERE id=1 FOR UPDATE",Integer.class);
   var current=members.find(actor.id()).orElseThrow(()->error(401,"unauthenticated"));
   if(!current.enabled() || current.sessionVersion()!=actor.sessionVersion())throw error(401,"unauthenticated");
   if(!"ADMIN".equals(current.role()))throw error(403,"forbidden");
   return operation.get();
  });
 }
 private void audit(Actor actor,String action,String applicationId,String applicationName,String memberName,String detail) {
  var current=members.find(actor.id()).orElseThrow(()->error(401,"unauthenticated"));
  jdbc.update("INSERT INTO application_audit(id,actor_id,actor_name,action,application_id,application_name,member_name,detail) VALUES (?,?,?,?,?,?,?,?)",
   UUID.randomUUID().toString(),actor.id(),current.displayName(),action,applicationId,applicationName,memberName,detail);
 }
 private MemberAccessException error(int status,String code) {return new MemberAccessException(status,code);}
}
