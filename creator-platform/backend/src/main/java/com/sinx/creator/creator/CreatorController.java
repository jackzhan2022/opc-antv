package com.sinx.creator.creator;

import com.sinx.creator.directory.MemberDirectory;
import com.sinx.creator.directory.MemberDirectory.MemberOption;
import com.sinx.creator.creator.CreatorModels.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class CreatorController {
    private final CreatorService service;
    private final MemberDirectory directory;
    public CreatorController(CreatorService service, MemberDirectory directory) { this.service=service; this.directory=directory; }
    @GetMapping("/members") public List<MemberOption> members(Authentication authentication) { return directory.list(authentication); }
    @GetMapping("/creators") public Page<Creator> list(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String profession, @RequestParam(defaultValue="") String tag,
            @RequestParam(defaultValue="") String city, @RequestParam(defaultValue="false") boolean archived,
            @RequestParam(defaultValue="") String maintainerId, @RequestParam(defaultValue="") String source,
            @RequestParam(defaultValue="false") boolean recent,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        return service.list(q,profession,tag,city,maintainerId,source,archived,recent,page,pageSize);
    }
    @GetMapping("/creators/summary") public Summary summary(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String profession, @RequestParam(defaultValue="") String tag,
            @RequestParam(defaultValue="") String city, @RequestParam(defaultValue="") String maintainerId,
            @RequestParam(defaultValue="") String source) { return service.summary(q,profession,tag,city,maintainerId,source); }
    @GetMapping("/creators/filter-options") public FilterOptions filterOptions() { return service.filterOptions(); }
    @PostMapping("/creators") @ResponseStatus(HttpStatus.CREATED)
    public Creator create(@RequestBody Input input, Authentication authentication) { return service.create(input,authentication); }
    @GetMapping("/creators/{id}") public Creator get(@PathVariable String id) { return service.get(id); }
    @PutMapping("/creators/{id}") public Creator update(@PathVariable String id, @RequestBody Input input, Authentication authentication) {
        return service.update(id,input,authentication);
    }
    @PostMapping("/creators/{id}/archive") public Creator archive(@PathVariable String id,@RequestBody Version input,Authentication authentication) {
        return service.setArchived(id,input.version(),true,authentication);
    }
    @PostMapping("/creators/{id}/restore") public Creator restore(@PathVariable String id,@RequestBody Version input,Authentication authentication) {
        return service.setArchived(id,input.version(),false,authentication);
    }
    @GetMapping("/creators/{id}/audit") public Page<Audit> audit(@PathVariable String id,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return service.audit(id,page,pageSize);
    }
}
