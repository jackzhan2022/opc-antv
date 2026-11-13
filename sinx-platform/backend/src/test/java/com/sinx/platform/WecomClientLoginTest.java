package com.sinx.platform;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"sinx.public-base-url=https://sinx.example.com",
 "sinx.auth.wecom.enabled=true", "sinx.auth.wecom.client-id=wwtest",
 "sinx.auth.wecom.client-secret=test-secret", "sinx.auth.wecom.tenant-id=wwtest",
 "sinx.auth.wecom.agent-id=1000001"})
@AutoConfigureMockMvc
class WecomClientLoginTest {
 @Autowired MockMvc mvc;

 @Test void mobileWecomUsesSilentAuthorizationWithSessionBoundState() throws Exception {
  var result=mvc.perform(get("/api/auth/wecom/start").header("User-Agent","Mozilla/5.0 (iPhone) MicroMessenger/8.0 wxwork/4.1.38"))
   .andExpect(status().isFound()).andReturn();
  var target=URI.create(result.getResponse().getRedirectedUrl());
  assertThat(target.getHost()).isEqualTo("open.weixin.qq.com");
  assertThat(target.getPath()).isEqualTo("/connect/oauth2/authorize");
  assertThat(target.getFragment()).isEqualTo("wechat_redirect");
  var query=UriComponentsBuilder.fromUri(target).build().getQueryParams();
  assertThat(query.getFirst("appid")).isEqualTo("wwtest");
  assertThat(query.getFirst("agentid")).isEqualTo("1000001");
  assertThat(query.getFirst("response_type")).isEqualTo("code");
  assertThat(query.getFirst("scope")).isEqualTo("snsapi_base");
  assertThat(java.net.URLDecoder.decode(query.getFirst("redirect_uri"),java.nio.charset.StandardCharsets.UTF_8))
   .isEqualTo("https://sinx.example.com/api/auth/wecom/callback");
  assertThat(query.getFirst("state")).matches("[0-9a-f]{64}");
  assertThat(result.getRequest().getSession(false)).isNotNull();
 }
 @Test void desktopWecomUsesSilentAuthorization() throws Exception {
  var result=mvc.perform(get("/api/auth/wecom/start").header("User-Agent","Mozilla/5.0 (Windows NT 10.0) WXWork/4.1.38"))
   .andExpect(status().isFound()).andReturn();
  assertThat(URI.create(result.getResponse().getRedirectedUrl()).getHost()).isEqualTo("open.weixin.qq.com");
 }
 @Test void ordinaryBrowserAndConsumerWechatKeepWebLogin() throws Exception {
  for(String ua:new String[]{"Mozilla/5.0 Chrome/131.0", "Mozilla/5.0 MicroMessenger/8.0", "Mozilla/5.0 fakewxwork/1.0"}) {
   var result=mvc.perform(get("/api/auth/wecom/start").header("User-Agent",ua)).andExpect(status().isFound()).andReturn();
   assertThat(URI.create(result.getResponse().getRedirectedUrl()).getHost()).isEqualTo("login.work.weixin.qq.com");
  }
  var result=mvc.perform(get("/api/auth/wecom/start")).andExpect(status().isFound()).andReturn();
  assertThat(URI.create(result.getResponse().getRedirectedUrl()).getHost()).isEqualTo("login.work.weixin.qq.com");
 }
 @Test void failedSilentAuthorizationConsumesStateAndDoesNotSignIn() throws Exception {
  var result=mvc.perform(get("/api/auth/wecom/start").header("User-Agent","wxwork/4.1.38")).andReturn();
  var state=UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("state");
  var session=(MockHttpSession)result.getRequest().getSession(false);
  mvc.perform(get("/api/auth/wecom/callback").session(session).param("state",state).param("error","access_denied"))
   .andExpect(redirectedUrl("/login?error=authorization_denied"));
  assertThat(session.isInvalid()).isTrue();
  mvc.perform(get("/api/auth/wecom/callback").param("state",state).param("code","replayed-code"))
   .andExpect(redirectedUrl("/login?error=invalid_state"));
  mvc.perform(get("/api/me").header("User-Agent","wxwork/4.1.38")).andExpect(status().isUnauthorized());
 }
}
