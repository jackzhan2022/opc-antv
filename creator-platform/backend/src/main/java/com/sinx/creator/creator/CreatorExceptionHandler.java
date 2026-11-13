package com.sinx.creator.creator;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class CreatorExceptionHandler {
    @ExceptionHandler(CreatorException.class)
    ResponseEntity<Map<String,String>> business(CreatorException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code",error.code()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String,String>> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(Map.of("code","invalid_request"));
    }
}
