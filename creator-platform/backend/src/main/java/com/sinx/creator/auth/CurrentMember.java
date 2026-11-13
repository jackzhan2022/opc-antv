package com.sinx.creator.auth;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
public record CurrentMember(String id,String displayName,String role) {
 public static CurrentMember from(Authentication authentication) {
  String name=authentication.getName();
  if(authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
   Object label=principal.getAttribute("displayName");
   if(label instanceof String value && !value.isBlank())name=value;
  }
  String role=authentication.getAuthorities().stream().anyMatch(a->"ROLE_ADMIN".equals(a.getAuthority()))?"ADMIN":"MEMBER";
  return new CurrentMember(authentication.getName(),name,role);
 }
}
