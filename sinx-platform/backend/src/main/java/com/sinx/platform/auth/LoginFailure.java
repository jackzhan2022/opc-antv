package com.sinx.platform.auth;
public final class LoginFailure extends RuntimeException {
 private final String code;
 public LoginFailure(String code) { super(code); this.code = code; }
 public String code() { return code; }
}
