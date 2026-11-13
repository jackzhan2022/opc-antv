package com.sinx.creator.dictionary;

public final class DictionaryModels {
    private DictionaryModels() {}
    public enum Kind { PROFESSION, TAG, SOURCE }
    public record Input(Kind kind,String name,String category,String description,Boolean enabled,Long version) {}
    public record Entry(String id,Kind kind,String name,String category,String description,boolean enabled,long version,long usageCount) {}
}
