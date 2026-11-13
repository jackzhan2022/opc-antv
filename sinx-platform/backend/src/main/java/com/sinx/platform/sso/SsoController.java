package com.sinx.platform.sso;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name="sinx.sso.enabled",havingValue="true")
public class SsoController {
 private final SsoAccess access;private final JdbcTemplate jdbc;
 public SsoController(SsoAccess access,JdbcTemplate jdbc) {this.access=access;this.jdbc=jdbc;}
 @GetMapping("/api/sso/userinfo") public ResponseEntity<?> userinfo(@RequestHeader(name="Authorization",required=false)String header) {
  return access.profile(header).<ResponseEntity<?>>map(p->ResponseEntity.ok(Map.of("sub",p.get("sub"),"displayName",p.get("displayName"),"role",p.get("role"))))
   .orElseGet(()->ResponseEntity.status(401).body(Map.of("code","unauthenticated")));
 }
 @GetMapping("/api/sso/members") public ResponseEntity<?> members(@RequestHeader(name="Authorization",required=false)String header) {
  if(access.profile(header).isEmpty())return ResponseEntity.status(401).body(Map.of("code","unauthenticated"));
  return ResponseEntity.ok(jdbc.query("SELECT id,display_name FROM platform_member WHERE enabled=true ORDER BY display_name,id",
   (rs,n)->Map.of("id",rs.getString(1),"displayName",rs.getString(2))));
 }
}
