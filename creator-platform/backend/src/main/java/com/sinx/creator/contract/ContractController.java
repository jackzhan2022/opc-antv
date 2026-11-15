package com.sinx.creator.contract;

import com.sinx.creator.contract.ContractModels.*;
import com.sinx.creator.creator.CreatorModels.Page;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ContractController {
    @ModelAttribute public void preventCaching(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
    private final ContractService service;
    private final ContractCallbacks callbacks;
    public ContractController(ContractService service,ContractCallbacks callbacks) { this.service=service;this.callbacks=callbacks; }
    @GetMapping("/contracts/integration") public Map<String,Object> integration() {
        return Map.of("ready",service.settings.ready(),"environment",service.settings.environment(),"companyName",service.settings.companyName());
    }
    @GetMapping("/contract-templates") public List<Template> templates() { return service.templates(); }
    @PostMapping(value="/contract-templates",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public Template upload(@RequestParam String name,@RequestParam MultipartFile file,Authentication auth) throws IOException {
        ContractService.admin(auth);if(file.getSize()>5*1024*1024) throw ContractFailure.invalid();return service.importTemplate(name,file.getOriginalFilename(),file.getBytes(),auth);
    }
    @GetMapping("/contract-templates/{id}/source") public ResponseEntity<byte[]> source(@PathVariable String id) {
        return attachment(service.source(id),ContractService.str(service.templateRow(id),"filename"));
    }
    @PostMapping("/contract-templates/{id}/editor") public Editor editor(@PathVariable String id,Authentication auth) { return service.editor(id,auth); }
    @PostMapping("/contract-templates/{id}/bind") public Template bind(@PathVariable String id,@RequestBody Bind body,Authentication auth) { return service.bind(id,body,auth); }
    @PostMapping("/contract-templates/{id}/disable") public Template disable(@PathVariable String id,@RequestBody Version body,Authentication auth) { return service.disable(id,body.version(),auth); }
    @GetMapping("/contracts") public Page<Contract> list(@RequestParam(defaultValue="") String creatorId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return service.list(creatorId,page,pageSize); }
    @PostMapping("/contracts") @ResponseStatus(HttpStatus.CREATED) public Contract draft(@RequestBody Draft body,Authentication auth) { return service.draft(body,auth); }
    @PutMapping("/contracts/{id}") public Contract save(@PathVariable String id,@RequestBody Draft body,Authentication auth) { return service.saveDraft(id,body,auth); }
    @GetMapping("/contracts/{id}") public Contract get(@PathVariable String id) { return service.get(id); }
    @GetMapping("/contracts/{id}/audit") public List<Audit> audit(@PathVariable String id) { return service.audits(id); }
    @PostMapping("/contracts/{id}/launch") public Contract launch(@PathVariable String id,@RequestBody Version body,Authentication auth) { return service.launch(id,body.version(),auth); }
    @PostMapping("/contracts/{id}/sync") public Contract sync(@PathVariable String id) { return service.sync(id); }
    @PostMapping("/contracts/{id}/signing-url") public Map<String,String> signing(@PathVariable String id) { return Map.of("url",service.signingUrl(id)); }
    @PostMapping("/contracts/{id}/preview") public Map<String,String> preview(@PathVariable String id) { return Map.of("url",service.preview(id)); }
    @PostMapping("/contracts/{id}/draft-preview") public Map<String,String> previewDraft(@PathVariable String id,Authentication auth) { return Map.of("url",service.previewDraft(id,auth)); }
    @PostMapping("/contracts/{id}/cancel") public Contract cancel(@PathVariable String id,@RequestBody Version body,Authentication auth) { return service.cancel(id,body.version(),auth); }
    @PostMapping("/contracts/{id}/archive") public Contract archive(@PathVariable String id,Authentication auth) { return service.archive(id,auth); }
    @GetMapping("/contracts/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable String id) { return attachment(service.pdf(id),service.get(id).title()+".pdf"); }
    @PostMapping("/contracts/callback") public void callback(HttpServletRequest request,@RequestHeader(value="Content-Signature",required=false) String signature) throws IOException {
        byte[] body=request.getInputStream().readNBytes(2*1024*1024+1);callbacks.accept(body,signature);
    }
    private static ResponseEntity<byte[]> attachment(byte[] bytes,String name) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes);
    }
}
