package com.sinx.platform.auth;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "sinx")
public record AuthProperties(String publicBaseUrl, Auth auth) {
 public record Auth(Provider feishu, Provider wecom) {}
 public record Provider(boolean enabled, String clientId, String clientSecret, String tenantId,
                        String agentId, List<String> adminSubjects) {
  public boolean ready(boolean requiresAgent) {
   return enabled && has(clientId) && has(clientSecret) && has(tenantId) && (!requiresAgent || has(agentId));
  }
  private static boolean has(String value) { return value != null && !value.isBlank(); }
 }
 public String callback(String provider) {
  URI uri = URI.create(publicBaseUrl);
  if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
      || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) &&
           ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))))) {
   throw new IllegalStateException("Public base URL must use HTTPS (HTTP is allowed only for local development)");
  }
  return publicBaseUrl.replaceAll("/+$", "") + "/api/auth/" + provider + "/callback";
 }
}
