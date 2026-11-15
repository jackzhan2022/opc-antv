package com.sinx.platform.application;

import com.sinx.platform.member.MemberAccessException;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=ApplicationAdminController.class)
public class ApplicationErrors {
 @ExceptionHandler(MemberAccessException.class) public ResponseEntity<?> access(MemberAccessException error) {return ResponseEntity.status(error.status()).body(Map.of("code",error.code()));}
 @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<?> duplicate() {return ResponseEntity.status(409).body(Map.of("code","application_code_exists"));}
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<?> invalid() {return ResponseEntity.badRequest().body(Map.of("code","invalid_request"));}
}
