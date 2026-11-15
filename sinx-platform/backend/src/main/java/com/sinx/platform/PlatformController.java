package com.sinx.platform;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.sinx.platform.member.MemberRepository;
@RestController
public class PlatformController {
 private final MemberRepository members;
 private final com.sinx.platform.application.ApplicationService applications;
 public PlatformController(MemberRepository members,com.sinx.platform.application.ApplicationService applications) {this.members=members;this.applications=applications;}
 @GetMapping("/api/health") Map<String,String> health() {return Map.of("status","UP");}
 @GetMapping("/api/me") Map<String,String> me(Authentication authentication) {
  var member=members.find(authentication.getName()).orElseThrow();return Map.of("id",member.id(),"displayName",member.displayName(),"role",member.role());
 }
 @GetMapping("/api/applications") List<com.sinx.platform.application.ApplicationModels.CatalogEntry> applications(Authentication authentication) {
  return applications.catalog(authentication.getName());
 }
}
