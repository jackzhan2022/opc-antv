package com.sinx.platform.sso;
import com.sinx.platform.member.MemberRepository;
import com.sinx.platform.application.ApplicationService;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Bind each grant to the member version at authorization, not the later code exchange. */
final class MemberAuthorizationService implements OAuth2AuthorizationService {
 static final String MEMBER_VERSION="sinx.member_version";
 static final String APPLICATION_VERSION="sinx.application_version";
 static final String GRANT_VERSION="sinx.grant_version";
 private final OAuth2AuthorizationService delegate;
 private final MemberRepository members;
 private final ApplicationService applications;
 MemberAuthorizationService(OAuth2AuthorizationService delegate,MemberRepository members,ApplicationService applications) {this.delegate=delegate;this.members=members;this.applications=applications;}
 public void save(OAuth2Authorization authorization) {
  if(authorization.getAttribute(MEMBER_VERSION)==null) {
   var request=RequestContextHolder.getRequestAttributes();
   var session=request instanceof ServletRequestAttributes attributes?attributes.getRequest().getSession(false):null;
   Object version=session==null?null:session.getAttribute(MemberRepository.SESSION_VERSION);
   var member=members.find(authorization.getPrincipalName());
   if(!(version instanceof Long) || member.isEmpty() || !member.get().enabled() || member.get().sessionVersion()!=((Long)version).longValue())
    throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
   var access=applications.access(member.get().id(),ApplicationService.CREATOR_ID)
    .orElseThrow(()->new OAuth2AuthenticationException(OAuth2ErrorCodes.ACCESS_DENIED));
   authorization=OAuth2Authorization.from(authorization).attribute(MEMBER_VERSION,version.toString())
    .attribute(APPLICATION_VERSION,Long.toString(access.applicationVersion())).attribute(GRANT_VERSION,Long.toString(access.grantVersion())).build();
  }
  delegate.save(authorization);
 }
 public void remove(OAuth2Authorization authorization) {delegate.remove(authorization);}
 public OAuth2Authorization findById(String id) {return delegate.findById(id);}
 public OAuth2Authorization findByToken(String token,OAuth2TokenType type) {return delegate.findByToken(token,type);}
}
