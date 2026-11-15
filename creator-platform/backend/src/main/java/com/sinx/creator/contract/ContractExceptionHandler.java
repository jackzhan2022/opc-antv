package com.sinx.creator.contract;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ContractExceptionHandler {
    @ExceptionHandler(ContractFailure.class) public ResponseEntity<Map<String,String>> failure(ContractFailure failure) { return ResponseEntity.status(failure.status).body(Map.of("code",failure.code)); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<Map<String,String>> large(MaxUploadSizeExceededException ignored) { return ResponseEntity.status(413).body(Map.of("code","contract_file_too_large")); }
}
