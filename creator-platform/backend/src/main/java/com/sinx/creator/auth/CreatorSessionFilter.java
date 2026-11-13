package com.sinx.creator.auth;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.filter.OncePerRequestFilter;

final class CreatorSessionFilter extends OncePerRequestFilter {
    private final PlatformClient platform;
    private final OAuth2AuthorizedClientRepository clients;
    CreatorSessionFilter(PlatformClient platform, OAuth2AuthorizedClientRepository clients) { this.platform = platform; this.clients = clients; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/health") || path.equals("/auth/csrf") || path.equals("/auth/logout")
                || path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/") || path.equals("/error");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2AuthenticationToken oauth) {
            try {
                var client = clients.loadAuthorizedClient("sinx", oauth, request);
                if (client == null) throw new PlatformFailure(true);
                var profile = platform.inspect(client.getAccessToken().getTokenValue());
                if (!oauth.getName().equals(profile.get("sub"))) throw new PlatformFailure(true);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + profile.get("role")));
                var updated = new OAuth2AuthenticationToken(new DefaultOAuth2User(authorities,profile,"sub"),authorities,"sinx");
                SecurityContextHolder.getContext().setAuthentication(updated);
            } catch (PlatformFailure error) {
                if (error.invalid()) clear(request,response);
                json(response,error.invalid() ? 401 : 503,error.getMessage()); return;
            }
        }
        chain.doFilter(request,response);
    }
    static void clear(HttpServletRequest request,HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false); if (session != null) session.invalidate();
        response.addHeader("Set-Cookie",ResponseCookie.from("CREATOR_SESSION", "").path("/creator-api")
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").maxAge(0).build().toString());
    }
    static void json(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
