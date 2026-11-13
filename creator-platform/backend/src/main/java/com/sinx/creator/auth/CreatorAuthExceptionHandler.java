package com.sinx.creator.auth;

import java.util.Map;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class CreatorAuthExceptionHandler {
    @ExceptionHandler(PlatformFailure.class)
    ResponseEntity<Map<String,String>> platformFailure(PlatformFailure failure,HttpServletRequest request,HttpServletResponse response) {
        if (failure.invalid()) CreatorSessionFilter.clear(request,response);
        return ResponseEntity.status(failure.invalid() ? 401 : 503).header("Cache-Control","no-store").body(Map.of("code",failure.getMessage()));
    }
}
