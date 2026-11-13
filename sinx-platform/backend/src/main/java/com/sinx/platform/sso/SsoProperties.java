package com.sinx.platform.sso;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sinx.sso")
public record SsoProperties(boolean enabled,String clientId,String clientSecret,String redirectUri,String applicationUrl) {
 public void validate() {
  if(clientId==null || !clientId.matches("[a-zA-Z0-9-]{1,80}") || clientSecret==null || clientSecret.length()<32)
   throw new IllegalStateException("SSO requires a client ID and a private secret of at least 32 characters");
  validateUrl(redirectUri);validateUrl(applicationUrl);
 }
 private static void validateUrl(String value) {
  URI uri=URI.create(value);
  if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getQuery()!=null ||
    !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && ListHost.local(uri.getHost())))
   throw new IllegalStateException("SSO URLs must use HTTPS or local development HTTP");
 }
 private static class ListHost {static boolean local(String host) {return "localhost".equals(host)||"127.0.0.1".equals(host);}}
}
