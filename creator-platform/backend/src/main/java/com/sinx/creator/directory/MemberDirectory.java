package com.sinx.creator.directory;

import java.util.List;
import org.springframework.security.core.Authentication;

public interface MemberDirectory {
    List<MemberOption> list(Authentication authentication);
    public record MemberOption(String id, String displayName) {}
}
