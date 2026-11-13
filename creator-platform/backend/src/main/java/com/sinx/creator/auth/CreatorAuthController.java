package com.sinx.creator.auth;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class CreatorAuthController {
    @GetMapping("/health") Map<String,String> health() { return Map.of("status","UP"); }
    @GetMapping("/me") CurrentMember me(Authentication authentication) { return CurrentMember.from(authentication); }
    @GetMapping("/auth/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
}
