package com.sinx.platform.member;

import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=MemberAdminController.class)
public class MemberAdminErrors {
 @ExceptionHandler(MemberAccessException.class) public ResponseEntity<Map<String,String>> access(MemberAccessException ex) {return ResponseEntity.status(ex.status()).body(Map.of("code",ex.code()));}
 @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<Map<String,String>> conflict() {return ResponseEntity.status(409).body(Map.of("code","identity_already_linked"));}
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<Map<String,String>> invalid() {return ResponseEntity.badRequest().body(Map.of("code","invalid_request"));}
}
