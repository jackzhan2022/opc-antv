package com.sinx.creator.contract;

import com.sinx.creator.directory.MemberDirectory;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"server.servlet.context-path=","spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:h2:mem:contract_tests;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}",
    "creator.ess.enabled=true","creator.ess.environment=test","creator.ess.secret-id=test-id","creator.ess.secret-key=test-key",
    "creator.ess.operator-id=operator","creator.ess.company-name=测试签约公司","creator.ess.signer-id=company-signer",
    "creator.ess.callback-token=callback-test-token","creator.ess.callback-key=12345678901234567890123456789012","creator.ess.poll-interval=3600000"})
@AutoConfigureMockMvc
@WithMockUser(username="admin",roles="ADMIN")
class CreatorContractsTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ContractService service;
    @MockitoBean EssGateway ess;
    @MockitoBean MemberDirectory directory;
    final ObjectMapper json=new ObjectMapper();
    final byte[] pdf="%PDF-1.4\ncontract fixture\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    @BeforeEach void data() {
        for(String table:List.of("contract_callback","contract_audit","creator_contract","contract_template","creator_record")) jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO creator_record(id,name,phone,wechat,city,source,maintainer_id,maintainer_name,notes,created_at,updated_at) VALUES ('creator','创作昵称','13800138000','','','','admin','管理员','',?,?)",ContractService.now(),ContractService.now());
        when(ess.call(anyString(),anyMap())).thenAnswer(invocation->switch((String)invocation.getArgument(0)) {
            case "DescribeFlowTemplates"->json.readTree("{\"Templates\":["+remoteTemplate("v1")+"]}");
            case "CreateFlow"->json.readTree("{\"FlowId\":\"flow-1\"}");
            case "CreateDocument","StartFlow","CancelFlow"->json.createObjectNode();
            case "DescribeFlowInfo"->json.readTree("{\"FlowDetailInfos\":[{\"FlowId\":\"flow-1\",\"FlowStatus\":0}]}");
            case "CreateSchemeUrl"->json.readTree("{\"SchemeUrl\":\"https://res.ess.tencent.cn/sign\"}");
            case "UploadFiles"->json.readTree("{\"FileIds\":[\"source\"]}");
            case "CreateFileConvertTask"->json.readTree("{\"TaskId\":\"convert-1\"}");
            case "DescribeFileConvertTask"->json.readTree("{\"TaskStatus\":4}");
            case "CreateEmbedWebUrl"->json.readTree("{\"WebUrl\":\"https://embed.test.qian.tencent.cn/editor\"}");
            case "DescribeFileUrls"->json.readTree("{\"FileUrls\":[{\"Url\":\"https://file.ess.tencent.cn/signed.pdf\"}]}");
            default->throw new AssertionError("Unexpected API action");
        });
    }
    String remoteTemplate(String version) { return """
        {"TemplateId":"remote-template","TemplateVersion":"%s","Status":1,"Available":1,"TemplateType":3,
         "Promoter":{"RecipientId":"promoter"},"Recipients":[{"RecipientType":"ENTERPRISE"},{"RecipientType":"INDIVIDUAL"}],
         "Components":[{"ComponentId":"fee","ComponentName":"合同金额","ComponentRecipientId":"promoter","ComponentType":"TEXT","ComponentRequired":true}]}
        """.formatted(version); }
    JsonNode upload(String extension,byte[] body) throws Exception {
        return json.readTree(mvc.perform(multipart("/contract-templates").file(new MockMultipartFile("file","合作模板."+extension,"application/octet-stream",body)).param("name","合作模板").with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    String readyTemplate() throws Exception {
        String id=upload("pdf",pdf).path("id").asText();
        mvc.perform(post("/contract-templates/"+id+"/bind").with(csrf()).contentType("application/json").content("{\"remoteId\":\"remote-template\",\"version\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.fields[0].name").value("合同金额"));return id;
    }
    JsonNode draft(String template) throws Exception {
        var body=Map.of("creatorId","creator","templateId",template,"title","编剧创作合同","legalName","签约实名","mobile","13800138000","deadline",Instant.now().getEpochSecond()+86400,"fields",Map.of("fee","5000"));
        return json.readTree(mvc.perform(post("/contracts").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode launch(String id) throws Exception { return json.readTree(mvc.perform(post("/contracts/"+id+"/launch").with(csrf()).contentType("application/json").content("{\"version\":1}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    @Test void importPreservesSourceAndEnforcesAdminCsrfAndContentType() throws Exception {
        var uploaded=upload("pdf",pdf);String id=uploaded.path("id").asText();assertThat(uploaded.path("enabled").asBoolean()).isFalse();
        mvc.perform(get("/contract-templates/"+id+"/source")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes(pdf));
        for(String extension:List.of("pdf","doc","docx","exe")) mvc.perform(multipart("/contract-templates").file(new MockMultipartFile("file","bad."+extension,"application/pdf","fake".getBytes())).param("name","错误文件").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/contract-templates").file(new MockMultipartFile("file","x.pdf","application/pdf",pdf)).param("name","模板").with(user("member").roles("MEMBER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/contract-templates").file(new MockMultipartFile("file","x.pdf","application/pdf",pdf)).param("name","模板")).andExpect(status().isForbidden());
        verify(ess,never()).call(anyString(),anyMap());
    }
    @Test void wordImportConvertsOnceAndResumesWithoutReupload() throws Exception {
        byte[] word;try(var output=new ByteArrayOutputStream();var zip=new ZipOutputStream(output)) {
            for(String name:List.of("[Content_Types].xml","word/document.xml")) { zip.putNextEntry(new ZipEntry(name));zip.write("<xml/>".getBytes());zip.closeEntry(); }zip.finish();word=output.toByteArray();
        }
        String id=upload("docx",word).path("id").asText();
        mvc.perform(post("/contract-templates/"+id+"/editor").with(csrf())).andExpect(jsonPath("$.state").value("CONVERTING"));
        when(ess.call(eq("DescribeFileConvertTask"),anyMap())).thenReturn(json.readTree("{\"TaskStatus\":8,\"ResourceId\":\"pdf-resource\"}"));
        mvc.perform(post("/contract-templates/"+id+"/editor").with(csrf())).andExpect(jsonPath("$.state").value("READY"));
        verify(ess,times(1)).call(eq("UploadFiles"),anyMap());verify(ess).call(eq("CreateEmbedWebUrl"),argThat(map->"pdf-resource".equals(map.get("BusinessId"))));
    }
    @Test void bindsOnlyPublishedManualCompanyAndIndividualTemplates() throws Exception {
        String id=upload("pdf",pdf).path("id").asText();
        for(String invalid:List.of(remoteTemplate("v1").replace("\"TemplateType\":3","\"TemplateType\":1"),remoteTemplate("v1").replace("INDIVIDUAL","ENTERPRISE"),remoteTemplate("v1").replace("\"Status\":1","\"Status\":0"))) {
            when(ess.call(eq("DescribeFlowTemplates"),anyMap())).thenReturn(json.readTree("{\"Templates\":["+invalid+"]}"));
            mvc.perform(post("/contract-templates/"+id+"/bind").with(csrf()).contentType("application/json").content("{\"remoteId\":\"remote-template\",\"version\":1}")).andExpect(status().isBadRequest());
        }
    }
    @Test void creatorContractsKeepSnapshotsAndMembersCannotLaunchOrCancel() throws Exception {
        var draft=draft(readyTemplate());String id=draft.path("id").asText();
        jdbc.update("UPDATE creator_record SET name='新的昵称',phone='13900000000' WHERE id='creator'");
        mvc.perform(get("/contracts/"+id)).andExpect(jsonPath("$.legalName").value("签约实名")).andExpect(jsonPath("$.mobile").value("13800138000")).andExpect(jsonPath("$.fieldDefinitions[0].name").value("合同金额"));
        assertThat(jdbc.queryForObject("SELECT creator_snapshot FROM creator_contract WHERE id=?",String.class,id)).contains("创作昵称");
        for(String action:List.of("launch","cancel")) mvc.perform(post("/contracts/"+id+"/"+action).with(user("member").roles("MEMBER")).with(csrf()).contentType("application/json").content("{\"version\":1}")).andExpect(status().isForbidden());
        mvc.perform(get("/contracts").param("creatorId","creator")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/contracts").param("creatorId","other")).andExpect(jsonPath("$.total").value(0));
    }
    @Test void launchWaitsForCompositionThenStartsAndCannotLaunchTwice() throws Exception {
        String id=draft(readyTemplate()).path("id").asText();
        assertThat(launch(id).path("status").asText()).isEqualTo("PREPARING");verify(ess,never()).call(eq("StartFlow"),anyMap());
        mvc.perform(post("/contracts/"+id+"/sync").with(csrf())).andExpect(jsonPath("$.status").value("SIGNING"));
        mvc.perform(post("/contracts/"+id+"/launch").with(csrf()).contentType("application/json").content("{\"version\":1}")).andExpect(status().isConflict());
        verify(ess,times(1)).call(eq("CreateFlow"),anyMap());verify(ess,times(1)).call(eq("CreateDocument"),anyMap());verify(ess,times(1)).call(eq("StartFlow"),anyMap());
        verify(ess).call(eq("CreateDocument"),argThat(map->map.get("FormFields").toString().contains("5000")));
    }
    @Test void partialLaunchFailureIsNotReplayedUntilRemoteQueryProvesDocumentExists() throws Exception {
        String id=draft(readyTemplate()).path("id").asText();when(ess.call(eq("CreateDocument"),anyMap())).thenThrow(ContractFailure.unavailable()).thenReturn(json.createObjectNode());
        assertThat(launch(id).path("errorCode").asText()).isEqualTo("ess_unavailable");
        service.sync(id);assertThat(service.get(id).status()).isEqualTo("PREPARING");verify(ess,times(1)).call(eq("CreateDocument"),anyMap());
        when(ess.call(eq("DescribeFlowInfo"),anyMap())).thenReturn(json.readTree("{\"FlowDetailInfos\":[{\"FlowId\":\"flow-1\",\"FlowStatus\":0,\"TemplateId\":\"remote-template\"}]}"));
        service.sync(id);assertThat(service.get(id).status()).isEqualTo("SIGNING");verify(ess,times(1)).call(eq("CreateFlow"),anyMap());
    }
    @Test void uncertainFlowCreationWaitsForSignedCorrelationInsteadOfCreatingAnotherFlow() throws Exception {
        String id=draft(readyTemplate()).path("id").asText();when(ess.call(eq("CreateFlow"),anyMap())).thenThrow(ContractFailure.unavailable());
        launch(id);service.sync(id);service.sync(id);verify(ess,times(1)).call(eq("CreateFlow"),anyMap());
        assertThat(service.get(id).errorCode()).isEqualTo("ess_creation_uncertain");
        String token=jdbc.queryForObject("SELECT launch_token FROM creator_contract WHERE id=?",String.class,id);
        String userData=Base64.getEncoder().encodeToString(json.writeValueAsBytes(Map.of("creatorContractId",id,"launchToken",token)));
        deliver(encrypt(Map.of("MsgId","recovered","MsgType","FlowStatusChange","MsgData",Map.of("FlowId","flow-1","FlowCallbackStatus",1,"UserData",userData))));
        service.sync(id);service.sync(id);assertThat(service.get(id).status()).isEqualTo("SIGNING");verify(ess,times(1)).call(eq("CreateFlow"),anyMap());
    }
    @Test void draftEditingUsesVersionAndPreviewNeverStartsSigning() throws Exception {
        String template=readyTemplate(),id=draft(template).path("id").asText();
        var changes=Map.of("creatorId","creator","templateId",template,"title","修改后的合同","legalName","签约实名","mobile","13800138000","deadline",Instant.now().getEpochSecond()+86400,"fields",Map.of("fee","6000"),"version",1);
        mvc.perform(put("/contracts/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(changes))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(put("/contracts/"+id).with(csrf()).contentType("application/json").content(json.writeValueAsString(changes))).andExpect(status().isConflict());
        when(ess.call(eq("CreateDocument"),anyMap())).thenReturn(json.readTree("{\"PreviewFileUrl\":\"https://qian.tencent.cn/preview\"}"));
        for(int i=0;i<2;i++) mvc.perform(post("/contracts/"+id+"/draft-preview").with(csrf())).andExpect(jsonPath("$.url").value("https://qian.tencent.cn/preview"));
        verify(ess,times(1)).call(eq("CreateFlow"),anyMap());verify(ess,never()).call(eq("StartFlow"),anyMap());
        assertThat(service.get(id).flowId()).isEmpty();assertThat(service.get(id).status()).isEqualTo("DRAFT");
    }
    byte[] callback(String id,int state) throws Exception {
        return encrypt(Map.of("MsgId",id,"MsgType","FlowStatusChange","MsgData",Map.of("FlowId","flow-1","FlowCallbackStatus",state)));
    }
    byte[] encrypt(Map<String,Object> message) throws Exception {
        byte[] key="12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(Arrays.copyOf(key,16)));
        byte[] plaintext=json.writeValueAsBytes(message);
        return json.writeValueAsBytes(Map.of("encrypt",Base64.getEncoder().encodeToString(cipher.doFinal(plaintext))));
    }
    void deliver(byte[] body) throws Exception {
        String signature="sha256="+HexFormat.of().formatHex(EssGateway.hmac("callback-test-token".getBytes(StandardCharsets.UTF_8),body));
        mvc.perform(post("/contracts/callback").with(anonymous()).contentType("application/json").header("Content-Signature",signature).content(body)).andExpect(status().isOk());
    }
    @Test void callbackAuthenticatesRawBytesDeduplicatesAndDoesNotRegressSignedState() throws Exception {
        String id=draft(readyTemplate()).path("id").asText();launch(id);
        byte[] signed=callback("signed",4);deliver(signed);deliver(signed);deliver(callback("older",2));
        assertThat(service.get(id).status()).isEqualTo("SIGNED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contract_callback",Integer.class)).isEqualTo(2);
        mvc.perform(post("/contracts/callback").with(anonymous()).contentType("application/json").content(signed)).andExpect(status().isForbidden());
        mvc.perform(post("/contracts/callback").with(anonymous()).contentType("application/json").header("Content-Signature","sha256="+"0".repeat(64)).content(signed)).andExpect(status().isForbidden());
    }
    @Test void archivesOnlyCompletedPdfAndKeepsPrivateDownload() throws Exception {
        String id=draft(readyTemplate()).path("id").asText();
        mvc.perform(post("/contracts/"+id+"/archive").with(csrf())).andExpect(status().isConflict());launch(id);deliver(callback("complete",4));when(ess.download(anyString())).thenReturn(pdf);
        mvc.perform(post("/contracts/"+id+"/archive").with(csrf())).andExpect(jsonPath("$.archived").value(true));
        mvc.perform(get("/contracts/"+id+"/pdf")).andExpect(content().bytes(pdf)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/contracts/"+id+"/pdf").with(anonymous())).andExpect(status().isUnauthorized());
    }
    @Test void disabledAndChangedTemplatesCannotLaunchDrafts() throws Exception {
        String template=readyTemplate(),id=draft(template).path("id").asText();
        when(ess.call(eq("DescribeFlowTemplates"),anyMap())).thenReturn(json.readTree("{\"Templates\":["+remoteTemplate("v2")+"]}"));
        mvc.perform(post("/contracts/"+id+"/launch").with(csrf()).contentType("application/json").content("{\"version\":1}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("template_changed"));
        jdbc.update("UPDATE contract_template SET enabled=false WHERE id=?",template);
        mvc.perform(post("/contracts/"+id+"/launch").with(csrf()).contentType("application/json").content("{\"version\":1}")).andExpect(status().isConflict());
        verify(ess,never()).call(eq("CreateFlow"),anyMap());
    }
}
