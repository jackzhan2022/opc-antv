package com.sinx.platform;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.sinx.platform.member.MemberRepository;
@RestController
public class PlatformController {
 private final MemberRepository members;
 private final com.sinx.platform.sso.SsoProperties sso;
 public PlatformController(MemberRepository members,com.sinx.platform.sso.SsoProperties sso) {this.members=members;this.sso=sso;}
 @GetMapping("/api/health") Map<String,String> health() {return Map.of("status","UP");}
 @GetMapping("/api/me") Map<String,String> me(Authentication authentication) {
  var member=members.find(authentication.getName()).orElseThrow();return Map.of("id",member.id(),"displayName",member.displayName(),"role",member.role());
 }
 @GetMapping("/api/applications") List<Object> applications() {
  return sso.enabled()?List.of(Map.of("id","creator-platform","name","创作者管理","description","创作者档案与作品资料","url",sso.applicationUrl())):List.of();
 }
}
