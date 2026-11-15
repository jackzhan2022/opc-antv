package com.sinx.creator.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class ContractCallbacks {
    final ContractService service;
    final EssConfiguration.Settings settings;
    public ContractCallbacks(ContractService service,EssConfiguration.Settings settings) { this.service=service;this.settings=settings; }
    public void accept(byte[] body,String signature) {
        settings.requireReady();
        if(body.length>2*1024*1024||signature==null||!signature.matches("sha256=[0-9a-f]{64}")) throw denied();
        JsonNode message;
        try {
            String expected="sha256="+HexFormat.of().formatHex(EssGateway.hmac(settings.callbackToken().getBytes(StandardCharsets.UTF_8),body));
            if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),signature.getBytes(StandardCharsets.UTF_8))) throw denied();
            JsonNode envelope=service.json.readTree(body);
            // This application requires encrypted callbacks; plain callbacks are not accepted.
            byte[] encrypted=Base64.getDecoder().decode(envelope.path("encrypt").asText());
            byte[] key=settings.callbackKey().getBytes(StandardCharsets.UTF_8);
            Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(Arrays.copyOf(key,16)));
            message=service.json.readTree(cipher.doFinal(encrypted));
        } catch(ContractFailure failure) { throw failure; }
        catch(Exception ignored) { throw denied(); }
        String id=ContractService.required(message.path("MsgId").asText(),100),type=ContractService.required(message.path("MsgType").asText(),100);
        try {
            service.tx.executeWithoutResult(ignored->{
                service.jdbc.update("INSERT INTO contract_callback(msg_id,msg_type,received_at) VALUES (?,?,?)",id,type,ContractService.now());
                JsonNode data=message.path("MsgData");String flowId=data.path("FlowId").asText();
                if(type.equals("DocumentFill")&&!data.path("ResourceUrl").asText().isBlank()) {
                    service.jdbc.update("UPDATE creator_contract SET preview_url=? WHERE preview_flow_id=? AND status='DRAFT' AND preview_deadline>?",data.path("ResourceUrl").asText(),flowId,java.time.Instant.now().getEpochSecond());
                }
                if(type.equals("DocumentFill")) { service.jdbc.update("UPDATE creator_contract SET launch_stage='START' WHERE flow_id=? AND status='PREPARING' AND launch_stage='DOCUMENT_IN_FLIGHT'",flowId);return; }
                if(!type.equals("FlowStatusChange")) return;
                // Correlation is supplied by our server before creating the remote flow.
                if(!data.path("UserData").asText().isBlank()&&!flowId.isBlank()) {
                    JsonNode correlation=null;
                    try {
                        correlation=service.json.readTree(Base64.getDecoder().decode(data.path("UserData").asText()));
                    } catch(RuntimeException unrelatedUserData) { /* Other applications may supply unrelated UserData. */ }
                    if(correlation!=null) {
                        service.jdbc.update("UPDATE creator_contract SET flow_id=?,launch_stage='DOCUMENT',error_code=NULL WHERE id=? AND launch_token=? AND status='PREPARING' AND launch_stage='CREATE_IN_FLIGHT' AND flow_id IS NULL",
                            flowId,correlation.path("creatorContractId").asText(),correlation.path("launchToken").asText());
                    }
                }
                var ids=service.jdbc.query("SELECT id FROM creator_contract WHERE flow_id=?",(rs,n)->rs.getString(1),flowId);
                if(ids.isEmpty()) return;
                int code=data.path("FlowCallbackStatus").asInt(-1);
                String next=switch(code) { case 1->null;case 2->"SIGNING";case 3->"REJECTED";case 4->"SIGNED";case 5->"EXPIRED";case 6->"CANCELLED";case 8,9->"FILLING";case 16->"INVALID";case 21->"RELEASED";default->null; };
                if(next!=null) service.applyStatus(ids.get(0),next);
            });
        } catch(DuplicateKeyException duplicate) { /* Already durably accepted. */ }
    }
    private static ContractFailure denied() { return new ContractFailure(HttpStatus.FORBIDDEN,"callback_invalid"); }
}
