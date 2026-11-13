package com.sinx.platform.member;

import java.time.Instant;
import java.util.List;

public final class MemberAdminModels {
 private MemberAdminModels() {}
 public record Page<T>(List<T> items,long total,int page,int pageSize) {}
 public record MemberInput(String displayName,String role,Boolean enabled) {}
 public record BindInput(String memberId) {}
 public record Actor(String id,long sessionVersion) {}
 public record IdentityView(String id,String provider,String tenantId,String subjectId,String displayName,Instant linkedAt) {}
 public record ManagedMember(String id,String displayName,String role,boolean enabled,Instant createdAt,Instant lastLoginAt,List<IdentityView> identities) {}
 public record PendingIdentity(String id,String provider,String tenantId,String subjectId,String displayName,Instant firstSeenAt,Instant lastSeenAt) {}
 public record AuditEntry(String id,String actorName,String action,String targetName,String detail,Instant createdAt) {}
 public record BootstrapInput(String provider,String tenantId,String subjectId,String displayName,String memberId) {}
}
