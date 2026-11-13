package com.sinx.creator.auth;

public final class PlatformFailure extends RuntimeException {
    private final boolean invalid;
    public PlatformFailure(boolean invalid) { super(invalid ? "unauthenticated" : "platform_unavailable"); this.invalid = invalid; }
    public boolean invalid() { return invalid; }
}
