package com.sinx.creator.creator;

import com.sinx.creator.directory.MemberDirectory;
import com.sinx.creator.directory.MemberDirectory.MemberOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="server.servlet.context-path=")
@AutoConfigureMockMvc
@WithMockUser(username="member-a",roles="MEMBER")
class CreatorBusinessTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MemberDirectory directory;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void resetData() {
        for (String table : List.of("creator_audit", "creator_work", "creator_tag", "creator_profession", "creator_record"))
            jdbc.update("DELETE FROM " + table);
        when(directory.list(any())).thenReturn(List.of(new MemberOption("member-a", "维护甲"),new MemberOption("member-b", "维护乙")));
    }
    Map<String,Object> input(String name) {
        var body=new LinkedHashMap<String,Object>();
        body.put("name", name); body.put("phone", "13800138000");body.put("wechat", "wx-example");body.put("city", "杭州");
        body.put("professions",List.of("编剧","剪辑"));body.put("tags",List.of("悬疑","古装"));body.put("source","推荐");
        body.put("works",List.of(Map.of("title","短片","url","https://example.org/work")));body.put("notes","合作记录");
        return body;
    }
    JsonNode create(Map<String,Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/creators").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    @Test void createDefaultsMaintainerAndPersistsCompleteRelationalRecord() throws Exception {
        var record=create(input("陈编剧"));
        assertThat(record.get("maintainerId").asText()).isEqualTo("member-a");
        assertThat(record.get("maintainerName").asText()).isEqualTo("维护甲");
        assertThat(record.get("version").asLong()).isEqualTo(1);
        assertThat(record.get("archived").asBoolean()).isFalse();
        assertThat(record.get("createdAt").asText()).isNotBlank();
        mvc.perform(get("/creators/"+record.get("id").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.professions[1]").value("剪辑"))
            .andExpect(jsonPath("$.works[0].url").value("https://example.org/work"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_profession", Integer.class)).isEqualTo(2);
    }
    @Test void allInternalMembersShareReadWriteAndCanAssignEnabledMaintainer() throws Exception {
        var body=input("共享档案");body.put("maintainerId","member-b");
        var record=create(body);body.put("version",record.get("version").asLong());body.put("name","另一个成员修改");
        mvc.perform(put("/creators/"+record.get("id").asText()).with(user("member-b").roles("MEMBER")).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.maintainerName").value("维护乙")).andExpect(jsonPath("$.version").value(2));
    }
    @Test void listPaginatesFiltersExactlyAndSearchesNamePhoneWechat() throws Exception {
        create(input("首位编剧"));
        var other=input("第二位");other.put("city","上海");other.put("professions",List.of("摄影"));other.put("tags",List.of("都市"));other.put("phone","13912345678");other.put("wechat","unique-wechat");create(other);
        mvc.perform(get("/creators").param("page","2").param("pageSize","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        for(String search:List.of("第二","139123","unique-wechat"))
            mvc.perform(get("/creators").param("q",search)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/creators").param("profession","编剧").param("tag","悬疑").param("city","杭州"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/creators").param("profession","编")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/creators").param("q","%" )).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
    @Test void rejectsBlankNameMissingProfessionBadWorkUrlAndOversizeFields() throws Exception {
        for (var entry:List.of(Map.entry("name", (Object)"  "),Map.entry("professions",(Object)List.of()),
                Map.entry("name",(Object)"长".repeat(101)),Map.entry("works",(Object)List.of(Map.of("title","坏链接","url","javascript:alert(1)"))),
                Map.entry("works",(Object)List.of(Map.of("title","坏链接","url","https:///missing-host"))),Map.entry("professions",(Object)List.of(" ")))) {
            var body=input("未创建");body.put(entry.getKey(),entry.getValue());
            mvc.perform(post("/creators").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
        }
    }
    @Test void unavailableMaintainerFailsWithoutWritingData() throws Exception {
        var body=input("未创建");body.put("maintainerId","disabled-member");
        mvc.perform(post("/creators").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("maintainer_unavailable"));
        mvc.perform(get("/creators")).andExpect(jsonPath("$.total").value(0));
    }
    @Test void oldVersionCannotOverwriteOrArchiveAndMissingVersionIsInvalid() throws Exception {
        var record=create(input("版本控制"));String id=record.get("id").asText();
        var body=input("第一次修改");body.put("version",1);
        mvc.perform(put("/creators/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(put("/creators/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("version_conflict"));
        mvc.perform(post("/creators/"+id+"/archive").with(csrf()).contentType("application/json").content("{\"version\":1}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("version_conflict"));
        body.remove("version");
        mvc.perform(put("/creators/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
    }
    @Test void archiveBlocksEditingRestorePreservesDataAndAuditContainsOnlyFieldNames() throws Exception {
        var record=create(input("归档测试"));String id=record.get("id").asText();
        mvc.perform(post("/creators/"+id+"/archive").with(csrf()).contentType("application/json").content("{\"version\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true)).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/creators")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/creators").param("archived","true")).andExpect(jsonPath("$.total").value(1));
        var body=input("不能编辑");body.put("version",2);
        mvc.perform(put("/creators/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("creator_archived"));
        mvc.perform(post("/creators/"+id+"/restore").with(csrf()).contentType("application/json").content("{\"version\":2}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false)).andExpect(jsonPath("$.phone").value("13800138000")).andExpect(jsonPath("$.version").value(3));
        body.put("version",3);body.put("phone","13999999999");
        mvc.perform(put("/creators/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        String audit=mvc.perform(get("/creators/"+id+"/audit").param("pageSize","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.items.length()").value(2)).andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("UPDATED","RESTORED","phone").doesNotContain("13800138000","13999999999","wx-example");
    }
    @Test void membersUsesLiveDirectoryAndMissingCreatorHasContractError() throws Exception {
        mvc.perform(get("/members")).andExpect(status().isOk()).andExpect(jsonPath("$[1].id").value("member-b"));
        for(String path:List.of("/creators/missing","/creators/missing/audit"))
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("creator_not_found"));
    }
    @Test void rejectsInvalidPaginationAndMalformedPayloadWithoutPersonalData() throws Exception {
        for(String path:List.of("/creators?page=0","/creators?pageSize=101","/creators?page=wat","/creators?archived=wat"))
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
        mvc.perform(post("/creators").with(csrf()).contentType("application/json").content("{\"name\":{\"secret\":\"private\"}}"))
            .andExpect(status().isBadRequest()).andExpect(content().json("{\"code\":\"invalid_request\"}"));
    }
    @Test void authenticationAndCsrfProtectEveryWrite() throws Exception {
        mvc.perform(get("/creators").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/creators").contentType("application/json").content(json.writeValueAsString(input("CSRF")))).andExpect(status().isForbidden());
    }
    @Test void maintainerNamesSupportPlatformMaximumLength() throws Exception {
        when(directory.list(any())).thenReturn(List.of(new MemberOption("member-a","成".repeat(200))));
        assertThat(create(input("长姓名维护人")).get("maintainerName").asText()).hasSize(200);
    }
    @Test void nullOperationBodyIsAnInvalidRequest() throws Exception {
        String id=create(input("空操作" )).get("id").asText();
        for(String operation:List.of("archive","restore"))
            mvc.perform(post("/creators/"+id+"/"+operation).with(csrf()).contentType("application/json").content("null"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
    }
    @Test void auditFailureRollsBackRecordFieldsRelationsAndVersion() throws Exception {
        String id=create(input("事务原始" )).get("id").asText();
        var changed=input("不应保存");changed.put("version",1);changed.put("professions",List.of("摄影"));
        jdbc.execute("ALTER TABLE creator_audit ADD CONSTRAINT fail_update_audit CHECK (action <> 'UPDATED')");
        try {
            assertThatThrownBy(()->mvc.perform(put("/creators/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(changed))))
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
        } finally {
            boolean mysql=jdbc.execute((java.sql.Connection connection)->connection.getMetaData().getDatabaseProductName().equals("MySQL"));
            jdbc.execute("ALTER TABLE creator_audit DROP "+(mysql?"CHECK":"CONSTRAINT")+" fail_update_audit");
        }
        mvc.perform(get("/creators/"+id)).andExpect(jsonPath("$.name").value("事务原始"))
            .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.professions[0]").value("编剧"));
        mvc.perform(get("/creators/"+id+"/audit")).andExpect(jsonPath("$.total").value(1));
    }
    @Test void simultaneousEditsHaveOneWinnerAndOneAuditedUpdate() throws Exception {
        String id=create(input("并发原始")).get("id").asText();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready=new java.util.concurrent.CountDownLatch(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var attempts=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for(String name:List.of("修改甲","修改乙")) {
                attempts.add(executor.submit(()-> {
                    var body=input(name);body.put("version",1);body.put("maintainerId","member-a");
                    ready.countDown();assertThat(start.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    return mvc.perform(put("/creators/"+id).with(user(name).roles("MEMBER")).with(csrf())
                        .contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(attempts.get(0).get(10,java.util.concurrent.TimeUnit.SECONDS),attempts.get(1).get(10,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200,409);
        } finally { start.countDown();executor.shutdownNow(); }
        mvc.perform(get("/creators/"+id)).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/creators/"+id+"/audit")).andExpect(jsonPath("$.total").value(2));
    }

    @Test void profileAndQuotePersistLegacyUpdatesPreserveAndExplicitClearsRemove() throws Exception {
        var body=input("完整资料");body.put("intro","擅长悬疑短剧");body.put("quoteAmount",1234.50);body.put("quoteUnit","集");
        String id=create(body).get("id").asText();
        mvc.perform(get("/creators/"+id)).andExpect(jsonPath("$.intro").value("擅长悬疑短剧"))
            .andExpect(jsonPath("$.quoteAmount").value(1234.50)).andExpect(jsonPath("$.quoteUnit").value("集"));
        var legacy=input("旧版保存");legacy.put("version",1);
        mvc.perform(put("/creators/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(legacy)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.intro").value("擅长悬疑短剧"))
            .andExpect(jsonPath("$.quoteAmount").value(1234.50));
        legacy.put("version",2);legacy.put("intro","");legacy.put("quoteAmount",null);legacy.put("quoteUnit","");
        mvc.perform(put("/creators/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(legacy)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.intro").value(""))
            .andExpect(jsonPath("$.quoteAmount").isEmpty()).andExpect(jsonPath("$.quoteUnit").value(""));
        String audit=mvc.perform(get("/creators/"+id+"/audit")).andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("intro","quoteAmount","quoteUnit").doesNotContain("擅长悬疑短剧","1234.5");
    }

    @Test void invalidQuotesAndOversizeIntroCannotBeSaved() throws Exception {
        for(var quote:List.of(Map.of("quoteAmount",(Object)(-1),"quoteUnit","集"),Map.of("quoteAmount",(Object)1.001,"quoteUnit","条"),
                Map.of("quoteAmount",(Object)10000000000L,"quoteUnit","天"),Map.of("quoteAmount",(Object)1,"quoteUnit","小时"),
                Map.of("quoteAmount",(Object)1),Map.of("quoteUnit",(Object)"集"),Map.of("intro",(Object)"长".repeat(2001)))) {
            var body=input("无效报价");body.putAll(quote);
            mvc.perform(post("/creators").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
        }
        var zero=input("零报价");zero.put("quoteAmount",0);zero.put("quoteUnit","项目");create(zero);
    }

    @Test void summaryAndFiltersUseRealStatusRecentAndCommonFilters() throws Exception {
        String older=create(input("早期记录")).get("id").asText();
        jdbc.update("UPDATE creator_record SET updated_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(8*86400)),older);
        var recent=input("近期记录");recent.put("maintainerId","member-b");recent.put("source","活动");create(recent);
        String archived=create(input("归档记录")).get("id").asText();
        mvc.perform(post("/creators/"+archived+"/archive").with(csrf()).contentType("application/json").content("{\"version\":1}"))
            .andExpect(status().isOk());
        mvc.perform(get("/creators/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.all").value(2))
            .andExpect(jsonPath("$.recent").value(1)).andExpect(jsonPath("$.archived").value(1));
        mvc.perform(get("/creators/summary").param("source","推荐")).andExpect(jsonPath("$.all").value(1))
            .andExpect(jsonPath("$.recent").value(0)).andExpect(jsonPath("$.archived").value(1));
        mvc.perform(get("/creators").param("recent","true")).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].name").value("近期记录"));
        mvc.perform(get("/creators").param("maintainerId","member-b").param("source","活动"))
            .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/creators").param("maintainerId","member-a").param("source","活动"))
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test void filterOptionsOnlyContainDistinctActualNonblankValues() throws Exception {
        create(input("甲"));create(input("乙"));
        var other=input("丙");other.put("city","上海");other.put("source","");create(other);
        mvc.perform(get("/creators/filter-options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.cities.length()").value(2)).andExpect(jsonPath("$.sources.length()").value(1))
            .andExpect(jsonPath("$.sources[0]").value("推荐"));
    }
}

