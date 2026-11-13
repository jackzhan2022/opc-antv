package com.sinx.platform.member;

public class MemberAccessException extends RuntimeException {
 private final int status;
 private final String code;
 public MemberAccessException(int status,String code) {super(code);this.status=status;this.code=code;}
 public int status() {return status;}
 public String code() {return code;}
}
