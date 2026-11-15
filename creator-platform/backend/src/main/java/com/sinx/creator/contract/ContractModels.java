package com.sinx.creator.contract;

import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;

public final class ContractModels {
    public record Field(String id,String name,String type,boolean required) {}
    public record Template(String id,String name,String filename,String fileType,String remoteId,boolean enabled,long version,List<Field> fields,Instant createdAt) {}
    public record Contract(String id,String creatorId,String templateId,String templateName,String title,String legalName,String mobile,
        Map<String,String> fields,List<Field> fieldDefinitions,String status,String flowId,long deadline,long version,boolean archived,String errorCode,Instant createdAt,Instant updatedAt) {}
    public record Draft(String creatorId,String templateId,String title,String legalName,String mobile,Map<String,String> fields,Long deadline,Long version) {}
    public record Bind(String remoteId,Long version) {}
    public record Version(Long version) {}
    public record Audit(String id,String actorName,String action,Instant createdAt) {}
    public record Editor(String state,String url) {}
}
