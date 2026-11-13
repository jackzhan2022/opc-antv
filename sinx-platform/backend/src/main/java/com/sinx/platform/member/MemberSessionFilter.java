package com.sinx.platform.member;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
public class MemberSessionFilter extends OncePerRequestFilter {
 private final MemberRepository members;
 public MemberSessionFilter(MemberRepository members) {this.members=members;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  var authentication=SecurityContextHolder.getContext().getAuthentication();
  if(authentication!=null && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
     && authentication.isAuthenticated() && authentication.getPrincipal() instanceof String id) {
   var member=members.find(id);
   var currentSession=req.getSession(false);
   Object version=currentSession==null ? null : currentSession.getAttribute(MemberRepository.SESSION_VERSION);
   if(member.isEmpty() || !member.get().enabled() || !(version instanceof Long) || ((Long)version).longValue()!=member.get().sessionVersion()) {
    SecurityContextHolder.clearContext();var session=req.getSession(false);if(session!=null) session.invalidate();
    res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{}");return;
   }
  }
  chain.doFilter(req,res);
 }
}
