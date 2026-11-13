package com.sinx.platform.auth;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import com.sinx.platform.member.MemberRepository;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
 private final List<IdentityProvider> providers;private final OAuthState states;
 private final MemberRepository members;private final HttpSessionSecurityContextRepository contexts;
 public AuthController(List<IdentityProvider> providers,OAuthState states,MemberRepository members,HttpSessionSecurityContextRepository contexts) {this.providers=providers;this.states=states;this.members=members;this.contexts=contexts;}
 @GetMapping("/providers") public List<Map<String,Object>> providers() {
  return providers.stream().sorted(java.util.Comparator.comparing(IdentityProvider::id)).map(p->Map.<String,Object>of("id",p.id(),"name",p.displayName(),"enabled",p.enabled())).toList();
 }
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken csrf) {return Map.of("token",csrf.getToken(),"headerName",csrf.getHeaderName());}
 private IdentityProvider provider(String id) {return providers.stream().filter(p->id.equals(p.id())&&p.enabled()).findFirst().orElseThrow(()->new LoginFailure("provider_unavailable"));}
 @GetMapping("/{id}/start") public void start(@PathVariable String id,HttpServletRequest req,HttpServletResponse res) throws IOException {
  try {IdentityProvider p=provider(id);res.sendRedirect(p.authorizationUrl(states.issue(req.getSession(true),id),req.getHeader("User-Agent")));}
  catch(LoginFailure ex) {res.sendRedirect("/login?error="+ex.code());}
 }
 @GetMapping("/{id}/callback") public void callback(@PathVariable String id,@RequestParam(required=false) String state,@RequestParam(required=false) String code,@RequestParam(required=false) String error,HttpServletRequest req,HttpServletResponse res) throws IOException {
  try {
   states.consume(req.getSession(false),id,state);
   if(error!=null || code==null || code.isBlank()) throw new LoginFailure("authorization_denied");
   if(code.length()>4096) throw new LoginFailure("identity_invalid");
   var identity=provider(id).authenticate(code);
   if(!id.equals(identity.provider())) throw new LoginFailure("identity_invalid");
   var member=members.login(identity);
   var saved=new org.springframework.security.web.savedrequest.HttpSessionRequestCache().getRequest(req,res);
   String target="/workspace";
   if(saved!=null) {
    var uri=java.net.URI.create(saved.getRedirectUrl());
    if("/api/sso/authorize".equals(uri.getRawPath()))target=uri.getRawPath()+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
   }
   req.getSession().invalidate();req.getSession(true);
   req.getSession().setAttribute(MemberRepository.SESSION_VERSION,member.sessionVersion());
   var context=SecurityContextHolder.createEmptyContext();
   context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(member.id(),null,List.of(new SimpleGrantedAuthority("ROLE_"+member.role()))));
   SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);res.sendRedirect(target);
  } catch(LoginFailure ex) {rejectLogin(req,res,ex.code());}
    catch(RuntimeException ex) {
     // Provider exceptions may contain credentials in URLs. Do not log their message or stack.
     org.slf4j.LoggerFactory.getLogger(getClass()).warn("Login failed for provider {} ({})",id,ex.getClass().getSimpleName());
     rejectLogin(req,res,"login_failed");
    }
 }
 private void rejectLogin(HttpServletRequest req,HttpServletResponse res,String code) throws IOException {
  SecurityContextHolder.clearContext();
  var session=req.getSession(false);if(session!=null)session.invalidate();
  res.sendRedirect("/login?error="+code);
 }
 @PostMapping("/logout") public void logout(HttpServletRequest req,HttpServletResponse res) {
  var session=req.getSession(false);if(session!=null) session.invalidate();SecurityContextHolder.clearContext();
  var cookie=new jakarta.servlet.http.Cookie("SINX_SESSION","");cookie.setPath("/");cookie.setHttpOnly(true);cookie.setSecure(req.isSecure());cookie.setMaxAge(0);cookie.setAttribute("SameSite","Lax");res.addCookie(cookie);res.setStatus(204);
 }
}
