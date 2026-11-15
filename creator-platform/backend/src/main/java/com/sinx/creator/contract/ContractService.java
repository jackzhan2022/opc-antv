package com.sinx.creator.contract;

import com.sinx.creator.auth.CurrentMember;
import com.sinx.creator.creator.CreatorService;
import com.sinx.creator.creator.CreatorModels.Page;
import com.sinx.creator.contract.ContractModels.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ContractService {
    final JdbcTemplate jdbc;
    final EssGateway ess;
    final EssConfiguration.Settings settings;
    final ObjectMapper json=new ObjectMapper();
    final CreatorService creators;
    final TransactionTemplate tx;
    public ContractService(JdbcTemplate jdbc,EssGateway ess,EssConfiguration.Settings settings,CreatorService creators,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.ess=ess;this.settings=settings;this.creators=creators;this.tx=new TransactionTemplate(manager);
    }
    static Timestamp now() { return Timestamp.from(Instant.now()); }
    static String text(String s,int max) { String v=s==null?"":s.strip();if(v.length()>max||v.indexOf('\0')>=0) throw ContractFailure.invalid();return v; }
    static String required(String s,int max) { String v=text(s,max);if(v.isEmpty()) throw ContractFailure.invalid();return v; }
    static void admin(Authentication auth) { if(!CurrentMember.from(auth).role().equals("ADMIN")) throw new ContractFailure(HttpStatus.FORBIDDEN,"forbidden"); }
    Map<String,Object> templateRow(String id) { var rows=jdbc.queryForList("SELECT * FROM contract_template WHERE id=?",id);if(rows.isEmpty()) throw ContractFailure.missing();return rows.get(0); }
    Map<String,Object> contractRow(String id) { var rows=jdbc.queryForList("SELECT * FROM creator_contract WHERE id=?",id);if(rows.isEmpty()) throw ContractFailure.missing();return rows.get(0); }
    static String str(Map<String,Object> row,String key) { return Objects.toString(row.get(key),""); }
    JsonNode tree(String source) { return source.isBlank()?json.createObjectNode():json.readTree(source); }
    void audit(String contractId,String templateId,CurrentMember actor,String action) {
        jdbc.update("INSERT INTO contract_audit(id,contract_id,template_id,actor_id,actor_name,action,created_at) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID().toString(),contractId,templateId,actor.id(),actor.displayName(),action,now());
    }
    public List<Template> templates() { return jdbc.queryForList("SELECT id,name,filename,file_type,remote_id,remote_json,enabled,version,created_at FROM contract_template ORDER BY created_at DESC,id DESC").stream().map(this::template).toList(); }
    Template template(Map<String,Object> r) {
        return new Template(str(r,"id"),str(r,"name"),str(r,"filename"),str(r,"file_type"),str(r,"remote_id"),Boolean.TRUE.equals(r.get("enabled")),((Number)r.get("version")).longValue(),fields(tree(str(r,"remote_json"))),((Timestamp)r.get("created_at")).toInstant());
    }
    static List<Field> fields(JsonNode snapshot) {
        List<Field> result=new ArrayList<>();String promoter=snapshot.path("Promoter").path("RecipientId").asText();
        for(JsonNode component:snapshot.path("Components")) {
            if(!promoter.equals(component.path("ComponentRecipientId").asText())||promoter.isBlank()) continue;
            String type=component.path("ComponentType").asText();
            if(List.of("TEXT","MULTI_LINE_TEXT","DATE","CHECK_BOX","SELECTOR").contains(type))
                result.add(new Field(component.path("ComponentId").asText(),component.path("ComponentName").asText(),type,component.path("ComponentRequired").asBoolean()));
        }
        return List.copyOf(result);
    }
    public Template importTemplate(String name,String filename,byte[] bytes,Authentication auth) {
        admin(auth);name=required(name,100);filename=required(filename,200);
        if(filename.contains("/")||filename.contains("\\")||filename.contains("\r")||filename.contains("\n")||bytes.length==0||bytes.length>5*1024*1024) throw ContractFailure.invalid();
        String type=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        validateFile(type,bytes);
        final String label=name,file=filename,id=UUID.randomUUID().toString();
        return tx.execute(ignored->{
            String hash;try { hash=EssGateway.sha(bytes); }catch(Exception e) { throw ContractFailure.invalid(); }
            jdbc.update("INSERT INTO contract_template(id,name,filename,file_type,source_body,source_sha256,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",id,label,file,type,bytes,hash,now(),now());
            audit(null,id,CurrentMember.from(auth),"TEMPLATE_IMPORTED");return template(templateRow(id));
        });
    }
    static void validateFile(String type,byte[] bytes) {
        if(type.equals("pdf")&&new String(bytes,0,Math.min(bytes.length,5),StandardCharsets.US_ASCII).equals("%PDF-")) return;
        if(type.equals("doc")&&bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),HexFormat.of().parseHex("d0cf11e0a1b11ae1"))) return;
        if(type.equals("docx")) {
            try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
                boolean content=false,document=false;int count=0,total=0;byte[] buffer=new byte[8192];
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    if(++count>2000) throw ContractFailure.invalid();
                    if(entry.getName().equals("[Content_Types].xml")) content=true;
                    if(entry.getName().equals("word/document.xml")) document=true;
                    if(entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject")) throw ContractFailure.invalid();
                    for(int n;(n=zip.read(buffer))!=-1;) { total+=n;if(total>30*1024*1024) throw ContractFailure.invalid(); }
                }
                if(content&&document) return;
            } catch(IOException invalid) { throw ContractFailure.invalid(); }
        }
        throw ContractFailure.invalid();
    }
    public byte[] source(String id) { return (byte[])templateRow(id).get("source_body"); }
    public synchronized Editor editor(String id,Authentication auth) {
        admin(auth);settings.requireReady();var r=templateRow(id);
        if(!str(r,"remote_id").isBlank()) {
            if(!str(r,"remote_environment").equals(settings.environment())) throw new ContractFailure(HttpStatus.CONFLICT,"ess_environment_changed");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM creator_contract WHERE template_id=?",Long.class,id)>0) throw new ContractFailure(HttpStatus.CONFLICT,"template_in_use");
            return new Editor("READY",ess.call("CreateEmbedWebUrl",Map.of("EmbedType","MODIFY_TEMPLATE","BusinessId",str(r,"remote_id"))).path("WebUrl").asText());
        }
        String resource=str(r,"resource_id"),task=str(r,"conversion_task"),type=str(r,"file_type");
        if(r.get("resource_deadline")==null||((Number)r.get("resource_deadline")).longValue()<Instant.now().getEpochSecond()+300) { resource="";task=""; }
        if(resource.isBlank()&&task.isBlank()) {
            JsonNode upload=ess.call("UploadFiles",Map.of("BusinessType","TEMPLATE","FileInfos",List.of(Map.of("FileBody",Base64.getEncoder().encodeToString((byte[])r.get("source_body")),"FileName",str(r,"filename")))));
            resource=required(upload.path("FileIds").path(0).asText(),100);
            long expires=upload.path("Deadline").asLong(Instant.now().getEpochSecond()+3600);
            if(!type.equals("pdf")) {
                task=required(ess.call("CreateFileConvertTask",Map.of("ResourceType",type,"ResourceName",str(r,"filename"),"ResourceId",resource)).path("TaskId").asText(),100);resource="";
            }
            jdbc.update("UPDATE contract_template SET resource_id=?,conversion_task=?,resource_deadline=? WHERE id=?",resource,task,expires,id);
        }
        if(resource.isBlank()) {
            JsonNode converted=ess.call("DescribeFileConvertTask",Map.of("TaskId",task));int status=converted.path("TaskStatus").asInt(-99);
            if(status<0) { jdbc.update("UPDATE contract_template SET conversion_task=NULL,resource_id=NULL WHERE id=?",id);throw new ContractFailure(HttpStatus.BAD_GATEWAY,"template_conversion_failed"); }
            if(status!=8) return new Editor("CONVERTING",null);
            resource=required(converted.path("ResourceId").asText(),100);
            jdbc.update("UPDATE contract_template SET resource_id=? WHERE id=?",resource,id);
        }
        String userData=Base64.getEncoder().encodeToString(json.writeValueAsBytes(Map.of("creatorTemplateId",id)));
        return new Editor("READY",ess.call("CreateEmbedWebUrl",Map.of("EmbedType","CREATE_TEMPLATE","BusinessId",resource,"UserData",userData,"Option",Map.of("SkipUploadFile",true))).path("WebUrl").asText());
    }
    JsonNode remoteTemplate(String remoteId) {
        JsonNode list=ess.call("DescribeFlowTemplates",Map.of("Filters",List.of(Map.of("Key","template-id","Values",List.of(remoteId))))).path("Templates");
        for(JsonNode item:list) if(item.path("TemplateId").asText().equals(remoteId)) return item;
        throw new ContractFailure(HttpStatus.BAD_REQUEST,"template_not_available");
    }
    static void validateTemplate(JsonNode remote) {
        if(remote.path("Status").asInt()!=1||remote.path("Available").asInt(1)!=1||remote.path("TemplateType").asInt()!=3||remote.path("TemplateVersion").asText().isBlank()) throw new ContractFailure(HttpStatus.BAD_REQUEST,"template_not_available");
        var recipients=remote.path("Recipients");
        if(recipients.size()!=2||!recipients.path(0).path("RecipientType").asText().equals("ENTERPRISE")||!recipients.path(1).path("RecipientType").asText().equals("INDIVIDUAL")) throw new ContractFailure(HttpStatus.BAD_REQUEST,"template_roles_invalid");
        for(JsonNode component:remote.path("Components")) {
            if(component.path("ComponentRecipientId").asText().equals(remote.path("Promoter").path("RecipientId").asText())&&
                !List.of("TEXT","MULTI_LINE_TEXT","DATE","CHECK_BOX","SELECTOR","WATERMARK").contains(component.path("ComponentType").asText())) throw new ContractFailure(HttpStatus.BAD_REQUEST,"template_fields_unsupported");
        }
    }
    public Template bind(String id,Bind input,Authentication auth) {
        admin(auth);String remoteId=required(input.remoteId(),100);JsonNode remote=remoteTemplate(remoteId);validateTemplate(remote);
        return tx.execute(ignored->{
            if(jdbc.update("UPDATE contract_template SET remote_id=?,remote_environment=?,remote_json=?,enabled=true,version=version+1,updated_at=? WHERE id=? AND version=?",remoteId,settings.environment(),json.writeValueAsString(remote),now(),id,input.version())!=1) throw ContractFailure.conflict();
            audit(null,id,CurrentMember.from(auth),"TEMPLATE_BOUND");return template(templateRow(id));
        });
    }
    public Template disable(String id,Long version,Authentication auth) {
        admin(auth);return tx.execute(ignored->{
            if(jdbc.update("UPDATE contract_template SET enabled=false,version=version+1,updated_at=? WHERE id=? AND version=?",now(),id,version)!=1) throw ContractFailure.conflict();
            audit(null,id,CurrentMember.from(auth),"TEMPLATE_DISABLED");return template(templateRow(id));
        });
    }
    public Contract draft(Draft input,Authentication auth) {
        return saveDraft(null,input,auth);
    }
    public Contract saveDraft(String existingId,Draft input,Authentication auth) {
        if(existingId!=null) { var before=get(existingId);if(!before.status().equals("DRAFT")||!before.creatorId().equals(input.creatorId())) throw ContractFailure.conflict(); }
        var creator=creators.get(required(input.creatorId(),36));if(creator.archived()) throw ContractFailure.conflict();
        var template=templateRow(required(input.templateId(),36));if(!Boolean.TRUE.equals(template.get("enabled"))) throw new ContractFailure(HttpStatus.CONFLICT,"template_not_available");
        if(!str(template,"remote_environment").equals(settings.environment())) throw new ContractFailure(HttpStatus.CONFLICT,"ess_environment_changed");
        String title=required(input.title(),200),legal=required(input.legalName(),100),mobile=required(input.mobile(),20);
        if(!mobile.matches("1[3-9][0-9]{9}")||input.deadline()==null||input.deadline()<Instant.now().getEpochSecond()+300||input.deadline()>Instant.now().getEpochSecond()+366L*86400) throw ContractFailure.invalid();
        var definitions=fields(tree(str(template,"remote_json")));Map<String,String> values=new LinkedHashMap<>();
        if(input.fields()==null||input.fields().size()>100) throw ContractFailure.invalid();
        for(Field field:definitions) { String value=text(input.fields().get(field.id()),10000);if(field.required()&&value.isEmpty()) throw ContractFailure.invalid();if(field.type().equals("CHECK_BOX")&&!value.isEmpty()&&!List.of("true","false").contains(value)) throw ContractFailure.invalid();values.put(field.id(),value); }
        if(!values.keySet().containsAll(input.fields().keySet())) throw ContractFailure.invalid();
        return tx.execute(ignored->{
            if(existingId!=null) {
                if(jdbc.update("UPDATE creator_contract SET template_id=?,template_name=?,template_remote_id=?,template_snapshot=?,ess_environment=?,title=?,legal_name=?,mobile=?,creator_snapshot=?,field_values=?,deadline=?,preview_flow_id=NULL,preview_url=NULL,preview_deadline=NULL,version=version+1,updated_at=? WHERE id=? AND version=? AND status='DRAFT'",
                    input.templateId(),str(template,"name"),str(template,"remote_id"),str(template,"remote_json"),settings.environment(),title,legal,mobile,json.writeValueAsString(creator),json.writeValueAsString(values),input.deadline(),now(),existingId,input.version())!=1) throw ContractFailure.conflict();
                audit(existingId,null,CurrentMember.from(auth),"DRAFT_UPDATED");return get(existingId);
            }
            String id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO creator_contract(id,creator_id,template_id,template_name,template_remote_id,template_snapshot,ess_environment,title,legal_name,mobile,creator_snapshot,field_values,status,launch_token,deadline,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,creator.id(),input.templateId(),str(template,"name"),str(template,"remote_id"),str(template,"remote_json"),settings.environment(),title,legal,mobile,json.writeValueAsString(creator),json.writeValueAsString(values),"DRAFT",UUID.randomUUID().toString(),input.deadline(),CurrentMember.from(auth).id(),now(),now());
            audit(id,input.templateId(),CurrentMember.from(auth),"DRAFT_CREATED");return get(id);
        });
    }
    Contract contract(Map<String,Object> r) {
        Map<String,String> values=new LinkedHashMap<>();tree(str(r,"field_values")).properties().forEach(e->values.put(e.getKey(),e.getValue().asText()));
        boolean archived=r.containsKey("has_pdf")?Boolean.TRUE.equals(r.get("has_pdf")):r.get("archived_pdf")!=null;
        return new Contract(str(r,"id"),str(r,"creator_id"),str(r,"template_id"),str(r,"template_name"),str(r,"title"),str(r,"legal_name"),str(r,"mobile"),values,fields(tree(str(r,"template_snapshot"))),str(r,"status"),str(r,"flow_id"),((Number)r.get("deadline")).longValue(),((Number)r.get("version")).longValue(),archived,str(r,"error_code"),((Timestamp)r.get("created_at")).toInstant(),((Timestamp)r.get("updated_at")).toInstant());
    }
    public Contract get(String id) { return contract(contractRow(id)); }
    public Page<Contract> list(String creatorId,int page,int size) {
        if(page<1||size<1||size>100) throw ContractFailure.invalid();
        String where=creatorId.isBlank()?"":" WHERE creator_id=?";List<Object> args=new ArrayList<>();if(!creatorId.isBlank()) args.add(creatorId);
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM creator_contract"+where,Long.class,args.toArray());args.add(size);args.add(((long)page-1)*size);
        return new Page<>(jdbc.queryForList("SELECT id,creator_id,template_id,template_name,title,legal_name,mobile,field_values,template_snapshot,status,flow_id,deadline,version,(archived_pdf IS NOT NULL) AS has_pdf,error_code,created_at,updated_at FROM creator_contract"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",args.toArray()).stream().map(this::contract).toList(),count,page,size);
    }
    public List<Audit> audits(String id) { get(id);return jdbc.query("SELECT * FROM contract_audit WHERE contract_id=? ORDER BY created_at DESC,id DESC",(r,n)->new Audit(r.getString("id"),r.getString("actor_name"),r.getString("action"),r.getTimestamp("created_at").toInstant()),id); }
    public Contract launch(String id,Long version,Authentication auth) {
        admin(auth);settings.requireReady();var r=contractRow(id);
        if(!str(r,"status").equals("DRAFT")) throw ContractFailure.conflict();
        if(!str(r,"ess_environment").equals(settings.environment())) throw new ContractFailure(HttpStatus.CONFLICT,"ess_environment_changed");
        if(((Number)r.get("deadline")).longValue()<=Instant.now().getEpochSecond()+60) throw new ContractFailure(HttpStatus.CONFLICT,"contract_deadline_expired");
        var creator=creators.get(str(r,"creator_id"));if(creator.archived()) throw ContractFailure.conflict();
        var template=templateRow(str(r,"template_id"));if(!Boolean.TRUE.equals(template.get("enabled"))) throw new ContractFailure(HttpStatus.CONFLICT,"template_not_available");
        JsonNode remote=remoteTemplate(str(r,"template_remote_id"));validateTemplate(remote);
        if(!Objects.equals(tree(str(r,"template_snapshot")).path("TemplateVersion").asText(),remote.path("TemplateVersion").asText())) throw new ContractFailure(HttpStatus.CONFLICT,"template_changed");
        tx.executeWithoutResult(ignored->{
            var lockedTemplate=jdbc.queryForMap("SELECT enabled FROM contract_template WHERE id=? FOR UPDATE",str(r,"template_id"));
            if(!Boolean.TRUE.equals(lockedTemplate.get("enabled"))) throw new ContractFailure(HttpStatus.CONFLICT,"template_not_available");
            String company=json.writeValueAsString(Map.of("name",settings.companyName(),"signerId",settings.signerId(),"environment",settings.environment()));
            if(jdbc.update("UPDATE creator_contract SET status='PREPARING',launch_stage='CREATE',company_snapshot=?,version=version+1,updated_at=? WHERE id=? AND version=? AND status='DRAFT'",company,now(),id,version)!=1) throw ContractFailure.conflict();
            audit(id,null,CurrentMember.from(auth),"LAUNCH_REQUESTED");
        });
        return sync(id);
    }
    /** CreateFlow/CreateDocument have no supported provider idempotency key. Uncertain calls are not replayed. */
    public synchronized Contract sync(String id) {
        var r=contractRow(id);if(str(r,"status").equals("DRAFT")) return get(id);
        JsonNode company=tree(str(r,"company_snapshot"));
        if(!company.path("environment").asText().equals(settings.environment())) throw new ContractFailure(HttpStatus.CONFLICT,"ess_environment_changed");
        try {
            if(str(r,"status").equals("PREPARING")) {
                String flowId=str(r,"flow_id");String stage=str(r,"launch_stage");
                if(stage.equals("CREATE_IN_FLIGHT")) throw new ContractFailure(HttpStatus.SERVICE_UNAVAILABLE,"ess_creation_uncertain");
                if(stage.equals("DOCUMENT_IN_FLIGHT")) {
                    JsonNode detail=remoteFlow(flowId);
                    if(detail.path("TemplateId").asText().equals(str(r,"template_remote_id"))) {
                        jdbc.update("UPDATE creator_contract SET launch_stage='START' WHERE id=?",id);stage="START";
                    } else throw new ContractFailure(HttpStatus.SERVICE_UNAVAILABLE,"ess_document_uncertain");
                }
                if(stage.equals("CREATE")) {
                    if(((Number)r.get("deadline")).longValue()<=Instant.now().getEpochSecond()) throw new ContractFailure(HttpStatus.CONFLICT,"contract_deadline_expired");
                    List<Map<String,Object>> approvers=List.of(Map.of("ApproverType",0,"OrganizationName",company.path("name").asText(),"UserId",company.path("signerId").asText(),"NotifyType","NONE"),
                        Map.of("ApproverType",1,"ApproverName",str(r,"legal_name"),"ApproverMobile",str(r,"mobile"),"NotifyType","NONE"));
                    jdbc.update("UPDATE creator_contract SET launch_stage='CREATE_IN_FLIGHT' WHERE id=? AND launch_stage='CREATE'",id);
                    String userData=Base64.getEncoder().encodeToString(json.writeValueAsBytes(Map.of("creatorContractId",id,"launchToken",str(r,"launch_token"))));
                    JsonNode order=tree(str(r,"template_snapshot")).path("SignOrder");
                    boolean unordered=order.size()==1&&order.path(0).asInt()==-1;
                    JsonNode created=ess.call("CreateFlow",Map.of("FlowName",str(r,"title"),"Approvers",approvers,"Unordered",unordered,"DeadLine",r.get("deadline"),"UserData",userData));
                    flowId=required(created.path("FlowId").asText(),100);
                    jdbc.update("UPDATE creator_contract SET flow_id=?,launch_stage='DOCUMENT',updated_at=? WHERE id=?",flowId,now(),id);stage="DOCUMENT";
                }
                if(stage.equals("DOCUMENT")) {
                    var values=tree(str(r,"field_values"));var fields=new ArrayList<Map<String,String>>();
                    values.properties().forEach(e->{ if(!e.getValue().asText().isBlank()) fields.add(Map.of("ComponentId",e.getKey(),"ComponentValue",e.getValue().asText())); });
                    jdbc.update("UPDATE creator_contract SET launch_stage='DOCUMENT_IN_FLIGHT' WHERE id=?",id);
                    ess.call("CreateDocument",Map.of("FlowId",flowId,"TemplateId",str(r,"template_remote_id"),"FormFields",fields));
                    jdbc.update("UPDATE creator_contract SET launch_stage='START',error_code=NULL,updated_at=? WHERE id=?",now(),id);
                    // PDF composition is asynchronous; the callback or a subsequent scheduled pass starts it.
                    return get(id);
                }
                if(stage.equals("START")) {
                    JsonNode detail=remoteFlow(flowId);int remoteStatus=detail.path("FlowStatus").asInt(-1);
                    if(remoteStatus==0||remoteStatus==7) ess.call("StartFlow",Map.of("FlowId",flowId));
                    else if(remoteStatus<0) throw ContractFailure.unavailable();
                    applyStatus(id,remoteStatus==0||remoteStatus==7?"SIGNING":queryStatus(remoteStatus));
                }
            } else if(!str(r,"flow_id").isBlank()) applyStatus(id,queryStatus(remoteFlow(str(r,"flow_id")).path("FlowStatus").asInt(-1)));
            jdbc.update("UPDATE creator_contract SET error_code=NULL,updated_at=? WHERE id=?",now(),id);
        } catch(ContractFailure failure) {
            jdbc.update("UPDATE creator_contract SET error_code=?,updated_at=? WHERE id=?",failure.code,now(),id);
            // A signed callback or a query proving completion may recover an uncertain operation.
        }
        return get(id);
    }
    JsonNode remoteFlow(String flowId) {
        for(JsonNode item:ess.call("DescribeFlowInfo",Map.of("FlowIds",List.of(flowId))).path("FlowDetailInfos")) if(item.path("FlowId").asText().equals(flowId)) return item;
        throw ContractFailure.unavailable();
    }
    static String queryStatus(int value) { return switch(value) { case 0,7->"PREPARING";case 1,2->"SIGNING";case 8,9->"FILLING";case 3,10->"REJECTED";case 4->"SIGNED";case 5->"EXPIRED";case 6->"CANCELLED";case 16->"INVALID";case 21->"RELEASED";default->throw ContractFailure.unavailable(); }; }
    void applyStatus(String id,String next) {
        tx.executeWithoutResult(ignored->{
            var row=jdbc.queryForMap("SELECT status FROM creator_contract WHERE id=? FOR UPDATE",id);String previous=str(row,"status");
            if(previous.equals(next)||List.of("REJECTED","EXPIRED","CANCELLED","INVALID","RELEASED").contains(previous)||(previous.equals("SIGNED")&&!next.equals("RELEASED"))||next.equals("PREPARING")) return;
            jdbc.update("UPDATE creator_contract SET status=?,version=version+1,updated_at=? WHERE id=?",next,now(),id);
            audit(id,null,new CurrentMember("ess","腾讯电子签","SYSTEM"),"STATUS_"+next);
        });
    }
    public String signingUrl(String id) {
        var c=get(id);if(!List.of("SIGNING","FILLING").contains(c.status())) throw ContractFailure.conflict();
        return ess.call("CreateSchemeUrl",Map.of("FlowId",c.flowId(),"PathType",1,"Name",c.legalName(),"Mobile",c.mobile(),"EndPoint","HTTP")).path("SchemeUrl").asText();
    }
    public String preview(String id) { var c=get(id);if(c.flowId().isBlank()) throw ContractFailure.conflict();return ess.call("CreateEmbedWebUrl",Map.of("EmbedType","PREVIEW_FLOW_DETAIL","BusinessId",c.flowId())).path("WebUrl").asText(); }
    public synchronized String previewDraft(String id,Authentication auth) {
        var r=contractRow(id);if(!str(r,"status").equals("DRAFT")) throw ContractFailure.conflict();settings.requireReady();
        if(!str(r,"ess_environment").equals(settings.environment())) throw new ContractFailure(HttpStatus.CONFLICT,"ess_environment_changed");
        if(r.get("preview_deadline")!=null&&((Number)r.get("preview_deadline")).longValue()>Instant.now().getEpochSecond()) {
            if(!str(r,"preview_url").isBlank()) return str(r,"preview_url");
            throw new ContractFailure(HttpStatus.SERVICE_UNAVAILABLE,"contract_preview_pending");
        }
        JsonNode remote=remoteTemplate(str(r,"template_remote_id"));validateTemplate(remote);
        if(!remote.path("TemplateVersion").asText().equals(tree(str(r,"template_snapshot")).path("TemplateVersion").asText())) throw new ContractFailure(HttpStatus.CONFLICT,"template_changed");
        String flow=required(ess.call("CreateFlow",Map.of("FlowName",str(r,"title"),"Approvers",List.of(
            Map.of("ApproverType",0,"OrganizationName",settings.companyName(),"UserId",settings.signerId()),
            Map.of("ApproverType",1,"ApproverName",str(r,"legal_name"),"ApproverMobile",str(r,"mobile"))))).path("FlowId").asText(),100);
        if(jdbc.update("UPDATE creator_contract SET preview_flow_id=?,preview_url=NULL,preview_deadline=? WHERE id=? AND version=? AND status='DRAFT'",flow,Instant.now().getEpochSecond()+300,id,r.get("version"))!=1) throw ContractFailure.conflict();
        List<Map<String,String>> fields=new ArrayList<>();tree(str(r,"field_values")).properties().forEach(e->{ if(!e.getValue().asText().isBlank()) fields.add(Map.of("ComponentId",e.getKey(),"ComponentValue",e.getValue().asText())); });
        JsonNode document=ess.call("CreateDocument",Map.of("FlowId",flow,"TemplateId",str(r,"template_remote_id"),"FormFields",fields,"NeedPreview",true,"PreviewType",1));
        String url=document.path("PreviewFileUrl").asText();
        if(url.isBlank()) throw new ContractFailure(HttpStatus.SERVICE_UNAVAILABLE,"contract_preview_pending");
        jdbc.update("UPDATE creator_contract SET preview_url=? WHERE id=? AND preview_flow_id=?",url,id,flow);
        audit(id,null,CurrentMember.from(auth),"DRAFT_PREVIEWED");return url;
    }
    public Contract cancel(String id,Long version,Authentication auth) {
        admin(auth);var c=get(id);if(c.version()!=Objects.requireNonNullElse(version,-1L)||!List.of("SIGNING","FILLING").contains(c.status())) throw ContractFailure.conflict();
        ess.call("CancelFlow",Map.of("FlowId",c.flowId(),"CancelMessage","内部管理员撤销"));
        tx.executeWithoutResult(ignored->{applyStatus(id,"CANCELLED");audit(id,null,CurrentMember.from(auth),"CANCEL_REQUESTED");});return get(id);
    }
    public Contract archive(String id,Authentication auth) {
        var c=get(id);if(!c.status().equals("SIGNED")) throw ContractFailure.conflict();if(c.archived()) return c;
        JsonNode urls=ess.call("DescribeFileUrls",Map.of("BusinessType","FLOW","BusinessIds",List.of(c.flowId()),"FileType","PDF")).path("FileUrls");
        String url=required(urls.path(0).path("Url").asText(),5000);byte[] pdf=ess.download(url);
        tx.executeWithoutResult(ignored->{jdbc.update("UPDATE creator_contract SET archived_pdf=?,updated_at=? WHERE id=? AND archived_pdf IS NULL",pdf,now(),id);audit(id,null,CurrentMember.from(auth),"PDF_ARCHIVED");});return get(id);
    }
    public byte[] pdf(String id) { var r=contractRow(id);if(r.get("archived_pdf")==null) throw ContractFailure.missing();return (byte[])r.get("archived_pdf"); }
}
