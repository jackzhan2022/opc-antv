package com.sinx.creator.dictionary;

import com.sinx.creator.dictionary.DictionaryModels.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dictionaries")
public class DictionaryController {
    private final DictionaryService service;
    public DictionaryController(DictionaryService service) { this.service=service; }
    @GetMapping public List<Entry> list(@RequestParam Kind kind,@RequestParam(defaultValue="false") boolean includeDisabled) {
        return service.list(kind,includeDisabled);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Entry create(@RequestBody Input input,Authentication authentication) { return service.create(input,authentication); }
    @PatchMapping("/{id}") public Entry update(@PathVariable String id,@RequestBody Input input,Authentication authentication) {
        return service.update(id,input,authentication);
    }
}
