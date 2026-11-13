package com.sinx.creator.creator;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

public final class CreatorModels {
    private CreatorModels() {}
    public record Work(String title, String url) {}
    public record Input(String name, String phone, String wechat, String city, List<String> professions,
                        List<String> tags, String source, String maintainerId, List<Work> works, String notes, Long version,
                        String intro, BigDecimal quoteAmount, String quoteUnit) {}
    public record Creator(String id, String name, String phone, String wechat, String city, List<String> professions,
                          List<String> tags, String source, String maintainerId, String maintainerName,
                          List<Work> works, String notes, boolean archived, long version, Instant createdAt, Instant updatedAt,
                          String intro, BigDecimal quoteAmount, String quoteUnit) {}
    public record Summary(long all, long recent, long archived) {}
    public record FilterOptions(List<String> cities, List<String> sources) {}
    public record Page<T>(List<T> items, long total, int page, int pageSize) {}
    public record Audit(String id, String actorName, String action, String detail, Instant createdAt) {}
    public record Version(Long version) {}
}
