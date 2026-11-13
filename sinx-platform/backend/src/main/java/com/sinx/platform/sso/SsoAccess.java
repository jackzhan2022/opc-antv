package com.sinx.platform.sso;
import java.util.Map;
import java.util.Optional;
import com.sinx.platform.member.MemberRepository;
import org.springframework.security.oauth2.server.authorization.*;
public class SsoAccess {
 private final OAuth2AuthorizationService authorizations;
 private final MemberRepository members;
 private final String clientId;
 public SsoAccess(OAuth2AuthorizationService authorizations,MemberRepository members,String clientId) {this.authorizations=authorizations;this.members=members;this.clientId=clientId;}
 public Optional<Map<String,Object>> inspect(String token) {
  if(token==null || token.isBlank() || token.length()>4096)return Optional.empty();
  var authorization=authorizations.findByToken(token,OAuth2TokenType.ACCESS_TOKEN);
  if(authorization==null || !"sinx-creator-client".equals(authorization.getRegisteredClientId()) || !authorization.getAuthorizedScopes().contains("creator"))return Optional.empty();
  var access=authorization.getAccessToken();
  if(access==null || !access.isActive())return Optional.empty();
  Object version=authorization.getAttribute(MemberAuthorizationService.MEMBER_VERSION);
  var member=members.find(authorization.getPrincipalName());
  if(member.isEmpty() || !member.get().enabled() || !Long.toString(member.get().sessionVersion()).equals(version))return Optional.empty();
  var found=member.get();
  return Optional.of(Map.of("active",true,"sub",found.id(),"displayName",found.displayName(),"role",found.role(),
   "scope","creator","client_id",clientId,"token_type","Bearer","exp",access.getToken().getExpiresAt().getEpochSecond()));
 }
 public Optional<Map<String,Object>> profile(String header) {
  if(header==null || !header.regionMatches(true,0,"Bearer ",0,7))return Optional.empty();
  return inspect(header.substring(7).trim());
 }
}
