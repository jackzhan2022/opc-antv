package com.sinx.platform.auth;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.MediaType;
public class FeishuIdentityProvider implements IdentityProvider {
 private final AuthProperties properties;private final RestClient client;
 public FeishuIdentityProvider(AuthProperties properties,RestClient client) {this.properties=properties;this.client=client;}
 public String id() {return "feishu";}
 public String displayName() {return "飞书";}
 public boolean enabled() {return properties.auth().feishu().ready(false);}
 public String authorizationUrl(String state) {
  return UriComponentsBuilder.fromUriString("https://accounts.feishu.cn/open-apis/authen/v1/authorize")
   .queryParam("client_id",properties.auth().feishu().clientId()).queryParam("redirect_uri",properties.callback(id()))
   .queryParam("response_type","code").queryParam("state",state).build().encode().toUriString();
 }
 public ExternalIdentity authenticate(String code) {
  var config=properties.auth().feishu();
  Map<?,?> token=client.post().uri("https://open.feishu.cn/open-apis/authen/v2/oauth/token").contentType(MediaType.APPLICATION_JSON)
   .body(Map.of("grant_type","authorization_code","client_id",config.clientId(),"client_secret",config.clientSecret(),"code",code,"redirect_uri",properties.callback(id())))
   .retrieve().body(Map.class);
  ProviderJson.success(token,"code");String accessToken=ProviderJson.text(token,"access_token");
  Map<?,?> response=client.get().uri("https://open.feishu.cn/open-apis/authen/v1/user_info").headers(h->h.setBearerAuth(accessToken)).retrieve().body(Map.class);
  ProviderJson.success(response,"code");Map<?,?> user=ProviderJson.object(response,"data");
  return new ExternalIdentity(id(),ProviderJson.text(user,"tenant_key"),ProviderJson.text(user,"open_id"),ProviderJson.text(user,"name"));
 }
}
