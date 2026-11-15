package com.sinx.platform.application;

import java.time.Instant;

public final class ApplicationModels {
 private ApplicationModels() {}
 public record Application(String id,String code,String name,String description,String url,String integrationType,
                           String icon,int sortOrder,boolean enabled,boolean connected,long version,long accessVersion,long grantedMembers) {}
 public record Input(String code,String name,String description,String url,String icon,Integer sortOrder,Boolean enabled,Long version) {}
 public record GrantInput(Boolean granted,Long version) {}
 public record MemberAccess(String id,String displayName,String role,boolean enabled,boolean granted,long version) {}
 public record Audit(String id,String actorName,String action,String applicationName,String memberName,String detail,Instant createdAt) {}
 public record CatalogEntry(String id,String name,String description,String url,String icon) {}
 public record AccessStamp(long applicationVersion,long grantVersion) {}
}
