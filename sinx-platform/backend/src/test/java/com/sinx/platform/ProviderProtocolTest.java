package com.sinx.platform;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import com.sinx.platform.auth.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.http.HttpMethod.*;

class ProviderProtocolTest {
 AuthProperties properties() {
  var feishu=new AuthProperties.Provider(true,"cli_test","test-secret","tenant",null,List.of());
  var wecom=new AuthProperties.Provider(true,"wwtest","test-secret","wwtest","1000001",List.of());
  return new AuthProperties("https://sinx.example.com",new AuthProperties.Auth(feishu,wecom));
 }
 @Test void feishuExchangesCodeOnBackendAndReadsTenantFromUserInfo() {
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token")).andExpect(method(POST))
   .andExpect(content().json("{\"grant_type\":\"authorization_code\",\"client_id\":\"cli_test\",\"client_secret\":\"test-secret\",\"code\":\"test-code\",\"redirect_uri\":\"https://sinx.example.com/api/auth/feishu/callback\"}"))
   .andRespond(withSuccess("{\"code\":0,\"access_token\":\"private-user-token\"}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info")).andExpect(header("Authorization","Bearer private-user-token"))
   .andRespond(withSuccess("{\"code\":0,\"data\":{\"open_id\":\"ou_test\",\"name\":\"林言\",\"tenant_key\":\"tenant\"}}",MediaType.APPLICATION_JSON));
  var provider=new FeishuIdentityProvider(properties(),builder.build());
  var user=provider.authenticate("test-code");
  assertThat(user).isEqualTo(new IdentityProvider.ExternalIdentity("feishu","tenant","ou_test","林言"));server.verify();
  assertThat(provider.authorizationUrl("random-state")).startsWith("https://accounts.feishu.cn/open-apis/authen/v1/authorize?").contains("state=random-state","response_type=code");
  assertThat(provider.authorizationUrl("random-state","wxwork/4.1.38")).isEqualTo(provider.authorizationUrl("random-state"));
 }
 @Test void wecomUsesCurrentWebLoginAndLowercaseUserid() {
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=wwtest&corpsecret=test-secret"))
   .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"app-token\",\"expires_in\":7200}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=app-token&code=test-code"))
   .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"zhang\"}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=app-token&userid=zhang"))
   .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"zhang\",\"name\":\"张言\",\"status\":1}",MediaType.APPLICATION_JSON));
  var provider=new WecomIdentityProvider(properties(),builder.build());
  assertThat(provider.authenticate("test-code")).isEqualTo(new IdentityProvider.ExternalIdentity("wecom","wwtest","zhang","张言"));server.verify();
  assertThat(provider.authorizationUrl("random-state")).startsWith("https://login.work.weixin.qq.com/wwlogin/sso/login?").contains("login_type=CorpApp","agentid=1000001","state=random-state");
 }
 @Test void wecomExternalContactCannotBecomeInternalMember() {
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  server.expect(anything()).andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"app-token\",\"expires_in\":7200}",MediaType.APPLICATION_JSON));
  server.expect(anything()).andRespond(withSuccess("{\"errcode\":0,\"openid\":\"external-visitor\"}",MediaType.APPLICATION_JSON));
  var provider=new WecomIdentityProvider(properties(),builder.build());
  assertThatThrownBy(()->provider.authenticate("external-code")).isInstanceOf(LoginFailure.class);server.verify();
 }
 @Test void feishuErrorPayloadIsRejected() {
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
  server.expect(anything()).andRespond(withSuccess("{\"code\":20003,\"error\":\"invalid_grant\"}",MediaType.APPLICATION_JSON));
  assertThatThrownBy(()->new FeishuIdentityProvider(properties(),builder.build()).authenticate("bad")).isInstanceOf(LoginFailure.class);server.verify();
 }
}
