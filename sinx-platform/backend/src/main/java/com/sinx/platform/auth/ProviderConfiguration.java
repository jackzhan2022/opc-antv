package com.sinx.platform.auth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
@Configuration
public class ProviderConfiguration {
 private RestClient client() {var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(10000);return RestClient.builder().requestFactory(factory).build();}
 @Bean IdentityProvider feishuIdentityProvider(AuthProperties properties) {return new FeishuIdentityProvider(properties,client());}
 @Bean IdentityProvider wecomIdentityProvider(AuthProperties properties) {return new WecomIdentityProvider(properties,client());}
}
