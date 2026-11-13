package com.sinx.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {
 @Autowired MockMvc mvc;
 @Test void anonymousApiReturns401InsteadOfLoginHtml() throws Exception {
  mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
 }
 @Test void providersArePublicAndUnconfiguredByDefault() throws Exception {
  mvc.perform(get("/api/auth/providers")).andExpect(status().isOk())
   .andExpect(jsonPath("$[0].id").value("feishu"))
   .andExpect(jsonPath("$[0].enabled").value(false))
   .andExpect(jsonPath("$[1].id").value("wecom"));
 }
 @Test void disabledProviderDoesNotPretendToLogin() throws Exception {
  mvc.perform(get("/api/auth/feishu/start")).andExpect(status().isFound())
   .andExpect(redirectedUrl("/login?error=provider_unavailable"));
 }
 @Test void logoutRequiresCsrf() throws Exception {
  mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
 }
 @Test void csrfTokenIsAvailableWithoutLogin() throws Exception {
  mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
   .andExpect(jsonPath("$.token").isNotEmpty())
   .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
 }
}
