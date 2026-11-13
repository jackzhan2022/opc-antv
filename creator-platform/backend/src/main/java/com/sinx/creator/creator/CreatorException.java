package com.sinx.creator.creator;

import org.springframework.http.HttpStatus;

public final class CreatorException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public CreatorException(HttpStatus status, String code) { super(code); this.status=status; this.code=code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    static CreatorException invalid() { return new CreatorException(HttpStatus.BAD_REQUEST,"invalid_request"); }
    static CreatorException missing() { return new CreatorException(HttpStatus.NOT_FOUND,"creator_not_found"); }
    static CreatorException conflict() { return new CreatorException(HttpStatus.CONFLICT,"version_conflict"); }
}
