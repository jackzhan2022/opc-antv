package com.sinx.creator.dictionary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="server.servlet.context-path=")
@AutoConfigureMockMvc
@WithMockUser(username="dictionary-admin",roles="ADMIN")
class DictionaryConfigurationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DictionaryService service;
    final ObjectMapper json=new ObjectMapper();
    final String prefix="字典测试-";

    @BeforeEach @AfterEach void cleanOwnFixtures() {
        for(String table:List.of("creator_audit","creator_work","creator_tag","creator_profession"))
            jdbc.update("DELETE FROM "+table+" WHERE creator_id='dictionary-fixture'");
        jdbc.update("DELETE FROM creator_record WHERE id='dictionary-fixture'");
        jdbc.update("DELETE FROM creator_dictionary_audit WHERE dictionary_id IN (SELECT id FROM creator_dictionary WHERE name LIKE ?)",prefix+"%");
        jdbc.update("DELETE FROM creator_dictionary WHERE name LIKE ?",prefix+"%");
    }

    Map<String,Object> input(String kind,String name) {
        var body=new LinkedHashMap<String,Object>();body.put("kind",kind);body.put("name",prefix+name);
        body.put("category","专业能力");body.put("description","配置说明");body.put("enabled",true);return body;
    }
    JsonNode create(Map<String,Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/dictionaries").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    String unique(String name) { return name+UUID.randomUUID().toString().substring(0,8); }
    void fixture(String profession,String tag,String source) {
        jdbc.update("INSERT INTO creator_record(id,name,phone,wechat,city,source,maintainer_id,maintainer_name,notes,archived,version,created_at,updated_at) VALUES ('dictionary-fixture','真实引用','','','',?,'member','成员','',false,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",source);
        jdbc.update("INSERT INTO creator_profession(creator_id,position_no,profession) VALUES ('dictionary-fixture',0,?)",profession);
        jdbc.update("INSERT INTO creator_tag(creator_id,position_no,tag) VALUES ('dictionary-fixture',0,?)",tag);
    }

    @Test void seedsStandardProfessionsWithoutCreatingCreatorRecords() throws Exception {
        String body=mvc.perform(get("/dictionaries").param("kind","PROFESSION"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("编剧","剪辑","导演","摄影","演员","配音","其他");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_record WHERE id='dictionary-fixture'",Integer.class)).isZero();
    }
    @Test void createsConfigurationWithUuidVersionAndDefaultCategory() throws Exception {
        var body=input("TAG",unique("默认"));body.remove("category");
        var item=create(body);
        assertThat(UUID.fromString(item.get("id").asText())).isNotNull();
        assertThat(item.get("category").asText()).isEqualTo("专业能力");
        assertThat(item.get("version").asLong()).isEqualTo(1);
        assertThat(item.get("usageCount").asLong()).isZero();
    }
    @Test void initializationCopiesHistoricalStringsAndPreservesDisabledSettings() throws Exception {
        String profession=prefix+unique("历史职业"),tag=prefix+unique("历史标签"),source=prefix+unique("历史来源");
        fixture(profession,tag,source);service.initialize();
        for(var value:List.of(Map.entry("PROFESSION",profession),Map.entry("TAG",tag),Map.entry("SOURCE",source))) {
            String response=mvc.perform(get("/dictionaries").param("kind",value.getKey())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(response).contains(value.getValue());
        }
        var body=input("TAG",unique("保持停用"));body.put("enabled",false);var item=create(body);
        service.initialize();service.initialize();
        assertThat(jdbc.queryForObject("SELECT enabled FROM creator_dictionary WHERE id=?",Boolean.class,item.get("id").asText())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_dictionary WHERE kind='TAG' AND name=?",Integer.class,tag)).isEqualTo(1);
    }
    @Test void membersCanReadAndOnlyAdminsWithCsrfCanWrite() throws Exception {
        mvc.perform(get("/dictionaries").param("kind","TAG").with(user("member").roles("MEMBER"))).andExpect(status().isOk());
        mvc.perform(get("/dictionaries").param("kind","TAG").with(anonymous())).andExpect(status().isUnauthorized());
        String body=json.writeValueAsString(input("TAG",unique("权限")));
        mvc.perform(post("/dictionaries").with(user("member").roles("MEMBER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        mvc.perform(post("/dictionaries").contentType("application/json").content(body)).andExpect(status().isForbidden());
        var item=create(input("TAG",unique("编辑权限")));
        mvc.perform(patch("/dictionaries/"+item.get("id").asText()).with(user("member").roles("MEMBER")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isForbidden());
    }
    @Test void disabledItemsAreHiddenByDefaultAndRetainedForAdminList() throws Exception {
        var body=input("SOURCE",unique("停用"));body.put("enabled",false);String name=body.get("name").toString();create(body);
        String defaults=mvc.perform(get("/dictionaries").param("kind","SOURCE")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String all=mvc.perform(get("/dictionaries").param("kind","SOURCE").param("includeDisabled","true")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(defaults).doesNotContain(name);assertThat(all).contains(name);
    }
    @Test void duplicateNamesAreRejectedWithinKindAndAllowedAcrossKinds() throws Exception {
        String name=unique("唯一");create(input("TAG",name));
        mvc.perform(post("/dictionaries").with(csrf()).contentType("application/json").content(json.writeValueAsString(input("TAG",name))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("dictionary_name_exists"));
        create(input("PROFESSION",name));
    }
    @Test void updateChecksVersionAndKindAndReturnsMissingContract() throws Exception {
        var body=input("TAG",unique("版本"));var item=create(body);String id=item.get("id").asText();body.put("version",1);body.put("description","新说明");
        mvc.perform(patch("/dictionaries/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(patch("/dictionaries/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("version_conflict"));
        body.put("version",2);body.put("kind","SOURCE");
        mvc.perform(patch("/dictionaries/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
        mvc.perform(patch("/dictionaries/"+UUID.randomUUID()).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("dictionary_not_found"));
    }
    @Test void realUsagePreventsRenameButAllowsCategoryDescriptionAndDisable() throws Exception {
        var profession=create(input("PROFESSION",unique("已用职业")));var tag=create(input("TAG",unique("已用标签")));var source=create(input("SOURCE",unique("已用来源")));
        fixture(profession.get("name").asText(),tag.get("name").asText(),source.get("name").asText());
        for(var item:List.of(profession,tag,source)) {
            String list=mvc.perform(get("/dictionaries").param("kind",item.get("kind").asText())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode selected=null;for(var row:json.readTree(list)) if(row.get("id").asText().equals(item.get("id").asText())) selected=row;
            assertThat(selected).isNotNull();assertThat(selected.get("usageCount").asLong()).isEqualTo(1);
            var body=input(item.get("kind").asText(),unique("不能改名"));body.put("version",1);
            mvc.perform(patch("/dictionaries/"+item.get("id").asText()).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("dictionary_in_use"));
            body.put("name",item.get("name").asText());body.put("category","题材类型");body.put("enabled",false);
            mvc.perform(patch("/dictionaries/"+item.get("id").asText()).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.usageCount").value(1));
        }
    }
    @Test void validatesFieldsAndRequiresValidKindAndVersion() throws Exception {
        for(var entry:List.of(Map.entry("name",(Object)" "),Map.entry("name",(Object)"长".repeat(51)),Map.entry("kind",(Object)"INVALID"),Map.entry("category",(Object)"长".repeat(51)),Map.entry("description",(Object)"长".repeat(501)))) {
            var body=input("TAG",unique("校验"));body.put(entry.getKey(),entry.getValue());
            mvc.perform(post("/dictionaries").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
        }
        var source=input("SOURCE", "长".repeat(195));create(source);source.put("name","长".repeat(201));
        mvc.perform(post("/dictionaries").with(csrf()).contentType("application/json").content(json.writeValueAsString(source))).andExpect(status().isBadRequest());
        var item=create(input("TAG",unique("无版本")));
        mvc.perform(patch("/dictionaries/"+item.get("id").asText()).with(csrf()).contentType("application/json").content(json.writeValueAsString(input("TAG",unique("无版本")))))
            .andExpect(status().isBadRequest());
        for(String path:List.of("/dictionaries","/dictionaries?kind=OTHER","/dictionaries?kind=TAG&includeDisabled=wat"))
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
    }
    @Test void auditStoresOnlyChangedFieldNamesAndFailureRollsBackUpdate() throws Exception {
        var body=input("TAG",unique("审计"));var item=create(body);String id=item.get("id").asText();body.put("version",1);body.put("description","不应进入审计的内容");
        mvc.perform(patch("/dictionaries/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        var details=jdbc.queryForList("SELECT detail FROM creator_dictionary_audit WHERE dictionary_id=?",String.class,id);
        assertThat(details).allSatisfy(detail->assertThat(detail).doesNotContain("不应进入审计",prefix));assertThat(details).contains("description");
        jdbc.execute("ALTER TABLE creator_dictionary_audit ADD CONSTRAINT fail_dictionary_update CHECK (detail <> 'category')");
        try {
            body.put("version",2);body.put("category","事务失败");
            assertThatThrownBy(()->mvc.perform(patch("/dictionaries/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))))
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
        } finally { jdbc.execute("ALTER TABLE creator_dictionary_audit DROP CONSTRAINT fail_dictionary_update"); }
        assertThat(jdbc.queryForObject("SELECT version FROM creator_dictionary WHERE id=?",Long.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT description FROM creator_dictionary WHERE id=?",String.class,id)).isEqualTo("不应进入审计的内容");
        assertThat(jdbc.queryForObject("SELECT category FROM creator_dictionary WHERE id=?",String.class,id)).isEqualTo("专业能力");
    }
    @Test void simultaneousUpdatesHaveOneWinnerAndOneAudit() throws Exception {
        var initial=input("TAG",unique("并发"));String id=create(initial).get("id").asText();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var attempts=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for(String description:List.of("说明甲","说明乙")) attempts.add(executor.submit(()-> {
                var body=new LinkedHashMap<>(initial);body.put("version",1);body.put("description",description);ready.countDown();assertThat(start.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return mvc.perform(patch("/dictionaries/"+id).with(user("dictionary-admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
            }));
            assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(attempts.get(0).get(10,java.util.concurrent.TimeUnit.SECONDS),attempts.get(1).get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { start.countDown();executor.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_dictionary_audit WHERE dictionary_id=? AND action='UPDATED'",Integer.class,id)).isEqualTo(1);
    }
}
