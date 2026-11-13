package com.sinx.platform.auth;
public interface IdentityProvider {
 String id();
 String displayName();
 boolean enabled();
 String authorizationUrl(String state);
 default String authorizationUrl(String state,String userAgent) {return authorizationUrl(state);}
 ExternalIdentity authenticate(String code);
 record ExternalIdentity(String provider, String tenant, String subject, String displayName) {}
}
