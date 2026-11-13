package com.sinx.creator.creator;

import com.sinx.creator.auth.CurrentMember;
import com.sinx.creator.directory.MemberDirectory;
import com.sinx.creator.directory.MemberDirectory.MemberOption;
import com.sinx.creator.creator.CreatorModels.*;
import com.sinx.creator.dictionary.DictionaryService;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorService {
    private final JdbcTemplate jdbc;
    private final MemberDirectory directory;
    private final DictionaryService dictionaries;
    public CreatorService(JdbcTemplate jdbc,MemberDirectory directory,DictionaryService dictionaries) { this.jdbc=jdbc;this.directory=directory;this.dictionaries=dictionaries; }

    @Transactional(readOnly=true)
    public Page<Creator> list(String q,String profession,String tag,String city,String maintainerId,String source,boolean archived,boolean recent,int page,int pageSize) {
        checkPage(page,pageSize);
        var filter=filter(q,profession,tag,city,maintainerId,source);
        StringBuilder where=new StringBuilder(filter.where()).append(" AND c.archived=?");
        List<Object> args=new ArrayList<>(filter.args());args.add(archived);
        if(recent) { where.append(" AND c.updated_at>=?");args.add(recentCutoff()); }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM creator_record c"+where,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args);paged.add(pageSize);paged.add(((long)page-1)*pageSize);
        List<String> ids=jdbc.query("SELECT c.id FROM creator_record c"+where+" ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?",(rs,row)->rs.getString(1),paged.toArray());
        return new Page<>(ids.stream().map(this::load).toList(),total,page,pageSize);
    }

    @Transactional(readOnly=true)
    public Summary summary(String q,String profession,String tag,String city,String maintainerId,String source) {
        var filter=filter(q,profession,tag,city,maintainerId,source);
        var args=new ArrayList<Object>();args.add(recentCutoff());args.addAll(filter.args());
        return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN c.archived=false THEN 1 ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN c.archived=false AND c.updated_at>=? THEN 1 ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN c.archived=true THEN 1 ELSE 0 END),0) FROM creator_record c"+filter.where(),
            (rs,row)->new Summary(rs.getLong(1),rs.getLong(2),rs.getLong(3)),args.toArray());
    }

    @Transactional(readOnly=true)
    public FilterOptions filterOptions() {
        return new FilterOptions(jdbc.query("SELECT DISTINCT city FROM creator_record WHERE city<>'' ORDER BY city",(rs,row)->rs.getString(1)),
            jdbc.query("SELECT DISTINCT source FROM creator_record WHERE source<>'' ORDER BY source",(rs,row)->rs.getString(1)));
    }

    private record Filter(String where,List<Object> args) {}
    private static Timestamp recentCutoff() { return Timestamp.from(Instant.now().minusSeconds(7*86400)); }
    private Filter filter(String q,String profession,String tag,String city,String maintainerId,String source) {
        q=text(q,200);profession=text(profession,50);tag=text(tag,50);city=text(city,100);
        maintainerId=text(maintainerId,100);source=text(source,200);
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(!q.isEmpty()) {
            where.append(" AND (LOWER(c.name) LIKE ? ESCAPE '!' OR LOWER(c.phone) LIKE ? ESCAPE '!' OR LOWER(c.wechat) LIKE ? ESCAPE '!')");
            String escaped="%"+q.toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            args.add(escaped);args.add(escaped);args.add(escaped);
        }
        if(!profession.isEmpty()) { where.append(" AND EXISTS (SELECT 1 FROM creator_profession p WHERE p.creator_id=c.id AND p.profession=?)");args.add(profession); }
        if(!tag.isEmpty()) { where.append(" AND EXISTS (SELECT 1 FROM creator_tag t WHERE t.creator_id=c.id AND t.tag=?)");args.add(tag); }
        if(!city.isEmpty()) { where.append(" AND c.city=?");args.add(city); }
        if(!maintainerId.isEmpty()) { where.append(" AND c.maintainer_id=?");args.add(maintainerId); }
        if(!source.isEmpty()) { where.append(" AND c.source=?");args.add(source); }
        return new Filter(where.toString(),args);
    }

    @Transactional(readOnly=true)
    public Creator get(String id) { return load(id); }

    @Transactional
    public Creator create(Input raw,Authentication authentication) {
        Input input=normalize(raw);
        dictionaries.lockForMutation();
        CurrentMember actor=CurrentMember.from(authentication);
        MemberOption maintainer=maintainer(input.maintainerId(),actor.id(),authentication);
        String id=UUID.randomUUID().toString();Timestamp now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO creator_record(id,name,phone,wechat,city,source,maintainer_id,maintainer_name,notes,intro,quote_amount,quote_unit,archived,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,false,1,?,?)",
            id,input.name(),input.phone(),input.wechat(),input.city(),input.source(),maintainer.id(),maintainer.displayName(),input.notes(),input.intro(),input.quoteAmount(),input.quoteUnit(),now,now);
        replaceChildren(id,input);
        appendAudit(id,actor,"CREATED","新建档案",now);
        return load(id);
    }

    @Transactional
    public Creator update(String id,Input raw,Authentication authentication) {
        if(raw==null) throw CreatorException.invalid();
        dictionaries.lockForMutation();
        Creator before=load(id);
        boolean legacyQuote=raw.quoteAmount()==null&&raw.quoteUnit()==null;
        Input input=normalize(new Input(raw.name(),raw.phone(),raw.wechat(),raw.city(),raw.professions(),raw.tags(),raw.source(),raw.maintainerId(),raw.works(),raw.notes(),raw.version(),
            raw.intro()==null?before.intro():raw.intro(),legacyQuote?before.quoteAmount():raw.quoteAmount(),legacyQuote?before.quoteUnit():raw.quoteUnit()));
        checkVersion(input.version());
        if(before.archived()) throw new CreatorException(HttpStatus.CONFLICT,"creator_archived");
        if(before.version()!=input.version()) throw CreatorException.conflict();
        CurrentMember actor=CurrentMember.from(authentication);
        MemberOption maintainer=maintainer(input.maintainerId(),actor.id(),authentication);
        Timestamp now=Timestamp.from(Instant.now());
        int changed=jdbc.update("UPDATE creator_record SET name=?,phone=?,wechat=?,city=?,source=?,maintainer_id=?,maintainer_name=?,notes=?,intro=?,quote_amount=?,quote_unit=?,updated_at=?,version=version+1 WHERE id=? AND version=? AND archived=false",
            input.name(),input.phone(),input.wechat(),input.city(),input.source(),maintainer.id(),maintainer.displayName(),input.notes(),input.intro(),input.quoteAmount(),input.quoteUnit(),now,id,input.version());
        if(changed!=1) throw CreatorException.conflict();
        replaceChildren(id,input);
        appendAudit(id,actor,"UPDATED",changedFields(before,input,maintainer),now);
        return load(id);
    }

    @Transactional
    public Creator setArchived(String id,Long version,boolean archived,Authentication authentication) {
        checkVersion(version);Creator before=load(id);
        if(before.version()!=version) throw CreatorException.conflict();
        if(before.archived()==archived) throw CreatorException.conflict();
        Timestamp now=Timestamp.from(Instant.now());
        if(jdbc.update("UPDATE creator_record SET archived=?,version=version+1,updated_at=? WHERE id=? AND version=?",archived,now,id,version)!=1)
            throw CreatorException.conflict();
        appendAudit(id,CurrentMember.from(authentication),archived?"ARCHIVED":"RESTORED",archived?"归档档案":"恢复档案",now);
        return load(id);
    }

    @Transactional(readOnly=true)
    public Page<Audit> audit(String id,int page,int pageSize) {
        checkPage(page,pageSize);load(id);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM creator_audit WHERE creator_id=?",Long.class,id);
        var items=jdbc.query("SELECT id,actor_name,action,detail,created_at FROM creator_audit WHERE creator_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            (rs,row)->new Audit(rs.getString("id"),rs.getString("actor_name"),rs.getString("action"),rs.getString("detail"),rs.getTimestamp("created_at").toInstant()),id,pageSize,((long)page-1)*pageSize);
        return new Page<>(items,total,page,pageSize);
    }

    private Creator load(String id) {
        var rows=jdbc.query("SELECT * FROM creator_record WHERE id=?",(rs,row)->new Creator(
            rs.getString("id"),rs.getString("name"),rs.getString("phone"),rs.getString("wechat"),rs.getString("city"),
            List.of(),List.of(),rs.getString("source"),rs.getString("maintainer_id"),rs.getString("maintainer_name"),List.of(),rs.getString("notes"),
            rs.getBoolean("archived"),rs.getLong("version"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),
            Objects.requireNonNullElse(rs.getString("intro"),""),rs.getBigDecimal("quote_amount"),rs.getString("quote_unit")),id);
        if(rows.isEmpty()) throw CreatorException.missing();
        Creator c=rows.get(0);
        var professions=jdbc.query("SELECT profession FROM creator_profession WHERE creator_id=? ORDER BY position_no",(rs,row)->rs.getString(1),id);
        var tags=jdbc.query("SELECT tag FROM creator_tag WHERE creator_id=? ORDER BY position_no",(rs,row)->rs.getString(1),id);
        var works=jdbc.query("SELECT title,url FROM creator_work WHERE creator_id=? ORDER BY position_no",(rs,row)->new Work(rs.getString(1),rs.getString(2)),id);
        return new Creator(c.id(),c.name(),c.phone(),c.wechat(),c.city(),professions,tags,c.source(),c.maintainerId(),c.maintainerName(),works,c.notes(),c.archived(),c.version(),c.createdAt(),c.updatedAt(),c.intro(),c.quoteAmount(),c.quoteUnit());
    }

    private MemberOption maintainer(String requested,String defaultId,Authentication authentication) {
        String id=requested.isEmpty()?defaultId:requested;
        return directory.list(authentication).stream().filter(member->member.id().equals(id)).findFirst()
            .orElseThrow(()->new CreatorException(HttpStatus.BAD_REQUEST,"maintainer_unavailable"));
    }

    private void replaceChildren(String id,Input input) {
        jdbc.update("DELETE FROM creator_profession WHERE creator_id=?",id);
        jdbc.update("DELETE FROM creator_tag WHERE creator_id=?",id);
        jdbc.update("DELETE FROM creator_work WHERE creator_id=?",id);
        for(int i=0;i<input.professions().size();i++) jdbc.update("INSERT INTO creator_profession(creator_id,position_no,profession) VALUES (?,?,?)",id,i,input.professions().get(i));
        for(int i=0;i<input.tags().size();i++) jdbc.update("INSERT INTO creator_tag(creator_id,position_no,tag) VALUES (?,?,?)",id,i,input.tags().get(i));
        for(int i=0;i<input.works().size();i++) { var work=input.works().get(i);jdbc.update("INSERT INTO creator_work(creator_id,position_no,title,url) VALUES (?,?,?,?)",id,i,work.title(),work.url()); }
    }

    private void appendAudit(String id,CurrentMember actor,String action,String detail,Timestamp now) {
        jdbc.update("INSERT INTO creator_audit(id,creator_id,actor_id,actor_name,action,detail,created_at) VALUES (?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),id,actor.id(),actor.displayName(),action,detail,now);
    }

    private static String changedFields(Creator old,Input next,MemberOption maintainer) {
        List<String> fields=new ArrayList<>();
        if(!Objects.equals(old.name(),next.name())) fields.add("name");
        if(!Objects.equals(old.phone(),next.phone())) fields.add("phone");
        if(!Objects.equals(old.wechat(),next.wechat())) fields.add("wechat");
        if(!Objects.equals(old.city(),next.city())) fields.add("city");
        if(!Objects.equals(old.professions(),next.professions())) fields.add("professions");
        if(!Objects.equals(old.tags(),next.tags())) fields.add("tags");
        if(!Objects.equals(old.source(),next.source())) fields.add("source");
        if(!Objects.equals(old.maintainerId(),maintainer.id())) fields.add("maintainerId");
        if(!Objects.equals(old.works(),next.works())) fields.add("works");
        if(!Objects.equals(old.notes(),next.notes())) fields.add("notes");
        if(!Objects.equals(old.intro(),next.intro())) fields.add("intro");
        if(old.quoteAmount()==null?next.quoteAmount()!=null:next.quoteAmount()==null||old.quoteAmount().compareTo(next.quoteAmount())!=0) fields.add("quoteAmount");
        if(!Objects.equals(old.quoteUnit(),next.quoteUnit())) fields.add("quoteUnit");
        return fields.isEmpty()?"保存档案":"变更字段："+String.join(",",fields);
    }

    private static Input normalize(Input raw) {
        if(raw==null) throw CreatorException.invalid();
        String name=text(raw.name(),100);if(name.isEmpty()) throw CreatorException.invalid();
        List<String> professions=strings(raw.professions(),20,true),tags=strings(raw.tags(),50,false);
        List<Work> works=new ArrayList<>();
        if(raw.works()!=null) {
            if(raw.works().size()>50) throw CreatorException.invalid();
            for(Work work:raw.works()) {
                if(work==null) throw CreatorException.invalid();
                String title=text(work.title(),200),url=text(work.url(),2048);
                try {
                    URI parsed=URI.create(url);
                    if(!("http".equalsIgnoreCase(parsed.getScheme())||"https".equalsIgnoreCase(parsed.getScheme()))||parsed.getHost()==null||parsed.getUserInfo()!=null)
                        throw CreatorException.invalid();
                } catch(IllegalArgumentException invalid) { throw CreatorException.invalid(); }
                works.add(new Work(title,url));
            }
        }
        String unit=text(raw.quoteUnit(),10);BigDecimal amount=raw.quoteAmount();
        if((amount==null)!=unit.isEmpty()||(!unit.isEmpty()&&!List.of("条","集","分钟","天","项目").contains(unit))) throw CreatorException.invalid();
        if(amount!=null&&(amount.signum()<0||amount.compareTo(new BigDecimal("9999999999.99"))>0||amount.stripTrailingZeros().scale()>2)) throw CreatorException.invalid();
        return new Input(name,text(raw.phone(),64),text(raw.wechat(),100),text(raw.city(),100),professions,tags,text(raw.source(),200),text(raw.maintainerId(),100),List.copyOf(works),text(raw.notes(),10000),raw.version(),text(raw.intro(),2000),amount,unit);
    }

    private static List<String> strings(List<String> values,int maximum,boolean required) {
        if(values==null) { if(required) throw CreatorException.invalid();return List.of(); }
        if(values.size()>maximum) throw CreatorException.invalid();
        var result=new LinkedHashSet<String>();
        for(String value:values) { String cleaned=text(value,50);if(cleaned.isEmpty()) throw CreatorException.invalid();result.add(cleaned); }
        if(required&&result.isEmpty()) throw CreatorException.invalid();
        return List.copyOf(result);
    }

    private static String text(String value,int maximum) {
        if(value==null) return "";
        String cleaned=value.strip();
        if(cleaned.length()>maximum||cleaned.indexOf('\0')>=0) throw CreatorException.invalid();
        return cleaned;
    }
    private static void checkVersion(Long version) { if(version==null||version<1) throw CreatorException.invalid(); }
    private static void checkPage(int page,int pageSize) { if(page<1||pageSize<1||pageSize>100) throw CreatorException.invalid(); }
}
