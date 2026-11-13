package com.sinx.creator.dictionary;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DictionaryInitializer implements ApplicationRunner {
    private final DictionaryService service;
    public DictionaryInitializer(DictionaryService service) { this.service=service; }
    @Override public void run(ApplicationArguments arguments) { service.initialize(); }
}
