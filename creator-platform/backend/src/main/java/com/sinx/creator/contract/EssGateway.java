package com.sinx.creator.contract;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Server-only TC3 client; endpoints cannot be provided by a browser request. */
@Component
public class EssGateway {
    private final EssConfiguration.Settings settings;
    private final ObjectMapper json=new ObjectMapper();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    public EssGateway(EssConfiguration.Settings settings) { this.settings=settings; }

    public JsonNode call(String action,Map<String,Object> input) {
        settings.requireReady();
        String host=action.equals("UploadFiles")?(settings.environment().equals("test")?"file.test.ess.tencent.cn":"file.ess.tencent.cn"):
            (settings.environment().equals("test")?"ess.test.ess.tencent.cn":"ess.tencentcloudapi.com");
        try {
            var body=new LinkedHashMap<>(input);
            if(!action.equals("UploadFiles")) body.put("Operator",Map.of("UserId",settings.operatorId()));
            else body.put("Caller",Map.of("OperatorId",settings.operatorId()));
            byte[] payload=json.writeValueAsBytes(body);
            Instant now=Instant.now();String timestamp=Long.toString(now.getEpochSecond());
            String date=DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(now);
            String headers="content-type:application/json; charset=utf-8\nhost:"+host+"\n";
            String canonical="POST\n/\n\n"+headers+"\ncontent-type;host\n"+sha(payload);
            String scope=date+"/ess/tc3_request";
            String toSign="TC3-HMAC-SHA256\n"+timestamp+"\n"+scope+"\n"+sha(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] key=hmac(("TC3"+settings.secretKey()).getBytes(StandardCharsets.UTF_8),date.getBytes(StandardCharsets.UTF_8));
            key=hmac(key,"ess".getBytes(StandardCharsets.UTF_8));key=hmac(key,"tc3_request".getBytes(StandardCharsets.UTF_8));
            String authorization="TC3-HMAC-SHA256 Credential="+settings.secretId()+"/"+scope+", SignedHeaders=content-type;host, Signature="+HexFormat.of().formatHex(hmac(key,toSign.getBytes(StandardCharsets.UTF_8)));
            var request=HttpRequest.newBuilder(URI.create("https://"+host+"/")).timeout(Duration.ofSeconds(25))
                .header("Content-Type","application/json; charset=utf-8").header("Authorization",authorization)
                .header("X-TC-Action",action).header("X-TC-Version","2020-11-11").header("X-TC-Timestamp",timestamp)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode result=json.readTree(response.body()).path("Response");
            if(response.statusCode()!=200||result.isMissingNode()||result.has("Error")) throw ContractFailure.unavailable();
            return result;
        } catch(ContractFailure failure) { throw failure; }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw ContractFailure.unavailable(); }
        catch(Exception ignored) { throw ContractFailure.unavailable(); }
    }
    public byte[] download(String url) {
        try {
            URI uri=URI.create(url);String host=Objects.requireNonNullElse(uri.getHost(),"");
            if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||!(host.endsWith(".ess.tencent.cn")||host.endsWith(".myqcloud.com"))) throw ContractFailure.unavailable();
            var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                byte[] bytes=stream.readNBytes(20*1024*1024+1);
                if(response.statusCode()!=200||bytes.length>20*1024*1024||!new String(bytes,0,Math.min(5,bytes.length),StandardCharsets.US_ASCII).equals("%PDF-")) throw ContractFailure.unavailable();
                return bytes;
            }
        } catch(ContractFailure failure) { throw failure; }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw ContractFailure.unavailable(); }
        catch(Exception ignored) { throw ContractFailure.unavailable(); }
    }
    static byte[] hmac(byte[] key,byte[] data) throws Exception { var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(data); }
    static String sha(byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
}
