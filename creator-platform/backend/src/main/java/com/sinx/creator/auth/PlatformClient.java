package com.sinx.creator.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.sinx.creator.directory.MemberDirectory.MemberOption;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** All credentials stay in server-side calls; upstream error bodies are never relayed. */
public class PlatformClient {
    private static final ParameterizedTypeReference<Map<String,Object>> PROFILE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<MemberOption>> MEMBERS = new ParameterizedTypeReference<>() {};
    private final CreatorAuthProperties properties;
    private final RestClient http;
    public PlatformClient(CreatorAuthProperties properties) {
        this.properties = properties;
        this.http = RestClient.builder().requestFactory(requestFactory()).build();
    }
    static JdkClientHttpRequestFactory requestFactory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
    public Map<String,Object> inspect(String token) {
        try {
            var form = new LinkedMultiValueMap<String,String>(); form.add("token", token);
            var result = http.post().uri(properties.backend("/api/sso/introspect"))
                    .headers(h -> h.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(PROFILE);
            if (result == null || !Boolean.TRUE.equals(result.get("active"))) throw new PlatformFailure(true);
            validateProfile(result);
            if (!properties.clientId().equals(result.get("client_id")) || !hasCreatorScope(result.get("scope")))
                throw new PlatformFailure(true);
            return result;
        } catch (RestClientException error) { throw new PlatformFailure(false); }
    }
    public Map<String,Object> profile(String token) {
        try {
            var result = http.get().uri(properties.backend("/api/sso/userinfo"))
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(PROFILE);
            validateProfile(result); return result;
        } catch (RestClientResponseException error) { throw new PlatformFailure(error.getStatusCode().value() == 401); }
          catch (RestClientException error) { throw new PlatformFailure(false); }
    }
    public List<MemberOption> members(String token) {
        try {
            var result = http.get().uri(properties.backend("/api/sso/members"))
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(MEMBERS);
            if (result == null || result.stream().anyMatch(m -> m.id() == null || m.id().isBlank() || m.displayName() == null))
                throw new PlatformFailure(false);
            return result;
        } catch (RestClientResponseException error) { throw new PlatformFailure(error.getStatusCode().value() == 401); }
          catch (RestClientException error) { throw new PlatformFailure(false); }
    }
    public void revoke(String token) {
        try {
            var form = new LinkedMultiValueMap<String,String>(); form.add("token",token); form.add("token_type_hint","access_token");
            http.post().uri(properties.backend("/api/sso/revoke"))
                    .headers(h -> h.setBasicAuth(properties.clientId(),properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        } catch (RestClientException error) { throw new PlatformFailure(false); }
    }
    private static boolean hasCreatorScope(Object scope) {
        return scope instanceof String value && java.util.Arrays.asList(value.split(" ")).contains("creator");
    }
    private static void validateProfile(Map<String,Object> profile) {
        if (profile == null || !(profile.get("sub") instanceof String id) || id.isBlank()
                || !(profile.get("displayName") instanceof String label) || label.isBlank()
                || !("ADMIN".equals(profile.get("role")) || "MEMBER".equals(profile.get("role"))))
            throw new PlatformFailure(true);
    }
}
