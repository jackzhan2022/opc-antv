package com.sinx.platform.auth;
import java.util.Map;
import java.time.Instant;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
public class WecomIdentityProvider implements IdentityProvider {
 private static final java.util.regex.Pattern WECOM_CLIENT=java.util.regex.Pattern.compile("\\bwxwork\\b",java.util.regex.Pattern.CASE_INSENSITIVE);
 private final AuthProperties properties;private final RestClient client;
 private String cachedToken;private Instant tokenExpiry=Instant.EPOCH;
 public WecomIdentityProvider(AuthProperties properties,RestClient client) {this.properties=properties;this.client=client;}
 public String id() {return "wecom";}
 public String displayName() {return "企业微信";}
 public boolean enabled() {return properties.auth().wecom().ready(true);}
 @Override public String authorizationUrl(String state,String userAgent) {
  if(userAgent==null || !WECOM_CLIENT.matcher(userAgent).find()) return authorizationUrl(state);
  var config=properties.auth().wecom();
  // Client detection only selects the entry point; the callback still verifies the code and member access.
  return UriComponentsBuilder.fromUriString("https://open.weixin.qq.com/connect/oauth2/authorize")
   .queryParam("appid",config.clientId()).queryParam("redirect_uri",properties.callback(id()))
   .queryParam("response_type","code").queryParam("scope","snsapi_base")
   .queryParam("state",state).queryParam("agentid",config.agentId()).fragment("wechat_redirect")
   .build().encode().toUriString();
 }
 public String authorizationUrl(String state) {
  var config=properties.auth().wecom();
  return UriComponentsBuilder.fromUriString("https://login.work.weixin.qq.com/wwlogin/sso/login")
   .queryParam("login_type","CorpApp").queryParam("appid",config.clientId()).queryParam("agentid",config.agentId())
   .queryParam("redirect_uri",properties.callback(id())).queryParam("state",state).queryParam("lang","zh").build().encode().toUriString();
 }
 private synchronized String token() {
  if(cachedToken!=null && Instant.now().isBefore(tokenExpiry)) return cachedToken;
  var config=properties.auth().wecom();
  Map<?,?> response=client.get().uri(UriComponentsBuilder.fromUriString("https://qyapi.weixin.qq.com/cgi-bin/gettoken")
    .queryParam("corpid",config.clientId()).queryParam("corpsecret",config.clientSecret()).build().encode().toUri()).retrieve().body(Map.class);
  ProviderJson.success(response,"errcode");cachedToken=ProviderJson.text(response,"access_token");
  tokenExpiry=Instant.now().plusSeconds(Math.max(0,ProviderJson.number(response,"expires_in",0)-120));return cachedToken;
 }
 private synchronized void invalidate(String token) {if(token.equals(cachedToken)) {cachedToken=null;tokenExpiry=Instant.EPOCH;}}
 private Map<?,?> get(String path,String key,String value) {
  for(int attempt=0;attempt<2;attempt++) {
   String token=token();
   Map<?,?> response=client.get().uri(UriComponentsBuilder.fromUriString("https://qyapi.weixin.qq.com/cgi-bin/"+path)
    .queryParam("access_token",token).queryParam(key,value).build().encode().toUri()).retrieve().body(Map.class);
   int code=ProviderJson.number(response,"errcode",-1);
   if(attempt==0 && (code==40014 || code==42001)) {invalidate(token);continue;}
   ProviderJson.success(response,"errcode");return response;
  }
  throw new LoginFailure("provider_error");
 }
 public ExternalIdentity authenticate(String code) {
  // Current Web-login API returns lowercase userid; openid represents an external visitor.
  var identity=get("auth/getuserinfo","code",code);String subject=ProviderJson.text(identity,"userid");
  var user=get("user/get","userid",subject);
  if(!subject.equals(ProviderJson.text(user,"userid")) || ProviderJson.number(user,"status",-1)!=1) throw new LoginFailure("identity_invalid");
  return new ExternalIdentity(id(),properties.auth().wecom().clientId(),subject,ProviderJson.text(user,"name"));
 }
}
