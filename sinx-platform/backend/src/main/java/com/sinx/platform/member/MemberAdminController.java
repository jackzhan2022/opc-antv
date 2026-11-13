package com.sinx.platform.member;

import static com.sinx.platform.member.MemberAdminModels.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class MemberAdminController {
 private final MemberManagementService management;
 public MemberAdminController(MemberManagementService management) {this.management=management;}
 @GetMapping("/members") public Page<ManagedMember> members(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="false") boolean enabledOnly) {return management.listMembers(q,page,enabledOnly);}
 @PostMapping("/members") @ResponseStatus(HttpStatus.CREATED)
 public ManagedMember create(Authentication auth,HttpSession session,@RequestBody MemberInput input) {return management.create(actor(auth,session),input);}
 @PatchMapping("/members/{id}") public ManagedMember update(Authentication auth,HttpSession session,@PathVariable String id,@RequestBody MemberInput input) {return management.update(actor(auth,session),id,input);}
 @GetMapping("/pending-identities") public Page<PendingIdentity> pending(@RequestParam(defaultValue="1") int page) {return management.listPending(page);}
 @PostMapping("/pending-identities/{id}/bind") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void bind(Authentication auth,HttpSession session,@PathVariable String id,@RequestBody BindInput input) {management.bind(actor(auth,session),id,input.memberId());}
 @DeleteMapping("/members/{memberId}/identities/{identityId}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void unlink(Authentication auth,HttpSession session,@PathVariable String memberId,@PathVariable String identityId) {management.unlink(actor(auth,session),memberId,identityId);}
 @GetMapping("/audit") public Page<AuditEntry> audit(@RequestParam(defaultValue="1") int page) {return management.listAudit(page);}
 private Actor actor(Authentication auth,HttpSession session) {
  Object version=session.getAttribute(MemberRepository.SESSION_VERSION);
  if(!(version instanceof Long)) throw new MemberAccessException(401,"unauthenticated");
  return new Actor(auth.getName(),(Long)version);
 }
}
