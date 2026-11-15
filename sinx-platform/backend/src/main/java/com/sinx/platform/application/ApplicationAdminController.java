package com.sinx.platform.application;

import static com.sinx.platform.application.ApplicationModels.*;
import com.sinx.platform.member.MemberAccessException;
import com.sinx.platform.member.MemberAdminModels.Actor;
import com.sinx.platform.member.MemberAdminModels.Page;
import com.sinx.platform.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/applications")
public class ApplicationAdminController {
 private final ApplicationService service;
 public ApplicationAdminController(ApplicationService service) {this.service=service;}
 @GetMapping public List<Application> list() {return service.list();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public Application create(Authentication auth,HttpSession session,@RequestBody Input input) {return service.create(actor(auth,session),input);}
 @PatchMapping("/{id}") public Application update(Authentication auth,HttpSession session,@PathVariable String id,@RequestBody Input input) {return service.update(actor(auth,session),id,input);}
 @GetMapping("/{id}/members") public Page<MemberAccess> members(@PathVariable String id,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="all")String filter,@RequestParam(defaultValue="1")int page) {return service.memberAccess(id,q,filter,page);}
 @PatchMapping("/{id}/members/{memberId}") public MemberAccess grant(Authentication auth,HttpSession session,@PathVariable String id,@PathVariable String memberId,@RequestBody GrantInput input) {return service.setAccess(actor(auth,session),id,memberId,input);}
 @GetMapping("/audit") public Page<Audit> audit(@RequestParam(defaultValue="1")int page) {return service.audit(page);}
 private Actor actor(Authentication auth,HttpSession session) {
  Object version=session.getAttribute(MemberRepository.SESSION_VERSION);
  if(!(version instanceof Long value))throw new MemberAccessException(401,"unauthenticated");
  return new Actor(auth.getName(),value);
 }
}
