package com.sinx.creator.auth;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("creator")
public record CreatorAuthProperties(String platformBaseUrl, String platformBackendUrl, String publicBaseUrl,
                                    String clientId, String clientSecret) {
    public void validate() {
        for (String value : new String[]{platformBaseUrl, platformBackendUrl, publicBaseUrl}) {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))))
                throw new IllegalStateException("Creator authentication URLs require HTTPS or local HTTP");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.length() < 32)
            throw new IllegalStateException("Creator SSO requires client credentials with a private secret of at least 32 characters");
    }
    public String platform(String path) { return platformBaseUrl.replaceAll("/$", "") + path; }
    public String backend(String path) { return platformBackendUrl.replaceAll("/$", "") + path; }
    public String frontend(String path) { return publicBaseUrl.replaceAll("/$", "") + path; }
}
