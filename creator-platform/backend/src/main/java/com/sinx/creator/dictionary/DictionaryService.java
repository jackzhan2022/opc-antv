package com.sinx.creator.dictionary;

import com.sinx.creator.auth.CurrentMember;
import com.sinx.creator.creator.CreatorException;
import com.sinx.creator.dictionary.DictionaryModels.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionaryService {
    private final JdbcTemplate jdbc;
    public DictionaryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public List<Entry> list(Kind kind,boolean includeDisabled) {
        if(kind==null) throw invalid();
        return jdbc.query(select(kind)+" WHERE d.kind=?"+(includeDisabled?"":" AND d.enabled=true")+" ORDER BY d.category,d.name,d.id",
            (rs,row)->new Entry(rs.getString("id"),Kind.valueOf(rs.getString("kind")),rs.getString("name"),rs.getString("category"),rs.getString("description"),rs.getBoolean("enabled"),rs.getLong("version"),rs.getLong("usage_count")),kind.name());
    }

    /** Shared with creator writes so reference checks and renames cannot race. */
    @Transactional
    public void lockForMutation() {
        jdbc.queryForObject("SELECT id FROM creator_dictionary_guard WHERE id=1 FOR UPDATE",Integer.class);
    }

    @Transactional
    public Entry create(Input raw,Authentication authentication) {
        Input input=normalize(raw);lockForMutation();checkName(input.kind(),input.name(),null);
        String id=UUID.randomUUID().toString();Timestamp now=Timestamp.from(Instant.now());
        insert(id,input,now);
        audit(id,CurrentMember.from(authentication).id(),"CREATED","kind,name,category,description,enabled",now);
        return load(id);
    }

    @Transactional
    public Entry update(String id,Input raw,Authentication authentication) {
        Input input=normalize(raw);
        if(input.version()==null||input.version()<1) throw invalid();
        lockForMutation();Entry before=load(id);
        if(input.kind()!=before.kind()) throw invalid();
        if(input.version()!=before.version()) throw error(HttpStatus.CONFLICT,"version_conflict");
        if(!Objects.equals(before.name(),input.name())&&before.usageCount()>0) throw error(HttpStatus.CONFLICT,"dictionary_in_use");
        checkName(input.kind(),input.name(),id);
        Timestamp now=Timestamp.from(Instant.now());
        int changed=jdbc.update("UPDATE creator_dictionary SET name=?,category=?,description=?,enabled=?,version=version+1,updated_at=? WHERE id=? AND version=?",
            input.name(),input.category(),input.description(),input.enabled(),now,id,input.version());
        if(changed!=1) throw error(HttpStatus.CONFLICT,"version_conflict");
        audit(id,CurrentMember.from(authentication).id(),"UPDATED",changedFields(before,input),now);
        return load(id);
    }

    @Transactional
    public void initialize() {
        lockForMutation();
        for(String name:List.of("编剧","剪辑","导演","摄影","演员","配音","其他")) seed(Kind.PROFESSION,name);
        for(Kind kind:Kind.values()) {
            String sql=switch(kind) {
                case PROFESSION -> "SELECT DISTINCT profession FROM creator_profession";
                case TAG -> "SELECT DISTINCT tag FROM creator_tag";
                case SOURCE -> "SELECT DISTINCT source FROM creator_record";
            };
            for(String name:jdbc.query(sql,(rs,row)->rs.getString(1))) if(name!=null&&!name.isBlank()) seed(kind,name);
        }
    }

    private void seed(Kind kind,String name) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM creator_dictionary WHERE kind=? AND name=?",Long.class,kind.name(),name)>0) return;
        String id=UUID.randomUUID().toString();Timestamp now=Timestamp.from(Instant.now());
        insert(id,new Input(kind,name,"专业能力","",true,null),now);
        audit(id,"system","INITIALIZED","kind,name,category,description,enabled",now);
    }
    private void insert(String id,Input input,Timestamp now) {
        jdbc.update("INSERT INTO creator_dictionary(id,kind,name,category,description,enabled,version,created_at,updated_at) VALUES (?,?,?,?,?,?,1,?,?)",
            id,input.kind().name(),input.name(),input.category(),input.description(),input.enabled(),now,now);
    }
    private void checkName(Kind kind,String name,String ownId) {
        Long count=ownId==null
            ?jdbc.queryForObject("SELECT COUNT(*) FROM creator_dictionary WHERE kind=? AND name=?",Long.class,kind.name(),name)
            :jdbc.queryForObject("SELECT COUNT(*) FROM creator_dictionary WHERE kind=? AND name=? AND id<>?",Long.class,kind.name(),name,ownId);
        if(count>0) throw error(HttpStatus.CONFLICT,"dictionary_name_exists");
    }
    private Entry load(String id) {
        var kinds=jdbc.query("SELECT kind FROM creator_dictionary WHERE id=?",(rs,row)->Kind.valueOf(rs.getString(1)),id);
        if(kinds.isEmpty()) throw error(HttpStatus.NOT_FOUND,"dictionary_not_found");
        return jdbc.queryForObject(select(kinds.get(0))+" WHERE d.id=?",(rs,row)->new Entry(rs.getString("id"),Kind.valueOf(rs.getString("kind")),rs.getString("name"),rs.getString("category"),rs.getString("description"),rs.getBoolean("enabled"),rs.getLong("version"),rs.getLong("usage_count")),id);
    }
    private static String select(Kind kind) {
        String reference=switch(kind) {
            case PROFESSION -> "creator_profession r WHERE r.profession=d.name";
            case TAG -> "creator_tag r WHERE r.tag=d.name";
            case SOURCE -> "creator_record r WHERE r.source=d.name";
        };
        return "SELECT d.*,(SELECT COUNT(*) FROM "+reference+") AS usage_count FROM creator_dictionary d";
    }
    private void audit(String id,String actor,String action,String fields,Timestamp now) {
        jdbc.update("INSERT INTO creator_dictionary_audit(id,dictionary_id,actor_id,action,detail,created_at) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID().toString(),id,actor,action,fields,now);
    }
    private static String changedFields(Entry before,Input input) {
        List<String> fields=new ArrayList<>();
        if(!Objects.equals(before.name(),input.name())) fields.add("name");
        if(!Objects.equals(before.category(),input.category())) fields.add("category");
        if(!Objects.equals(before.description(),input.description())) fields.add("description");
        if(before.enabled()!=input.enabled()) fields.add("enabled");
        return String.join(",",fields);
    }
    private static Input normalize(Input raw) {
        if(raw==null||raw.kind()==null) throw invalid();
        String name=text(raw.name(),raw.kind()==Kind.SOURCE?200:50);
        if(name.isEmpty()) throw invalid();
        String category=text(raw.category(),50);
        return new Input(raw.kind(),name,category.isEmpty()?"专业能力":category,text(raw.description(),500),raw.enabled()==null?true:raw.enabled(),raw.version());
    }
    private static String text(String raw,int maximum) {
        String value=raw==null?"":raw.strip();
        if(value.length()>maximum||value.indexOf('\0')>=0) throw invalid();
        return value;
    }
    private static CreatorException invalid() { return error(HttpStatus.BAD_REQUEST,"invalid_request"); }
    private static CreatorException error(HttpStatus status,String code) { return new CreatorException(status,code); }
}
