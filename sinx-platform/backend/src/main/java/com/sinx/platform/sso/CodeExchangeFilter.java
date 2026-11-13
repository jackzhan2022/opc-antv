package com.sinx.platform.sso;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Keep authorization-code read, consumption and token persistence in one database transaction. */
final class CodeExchangeFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RequestMatcher tokenEndpoint=PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,"/api/sso/token");
    CodeExchangeFilter(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !tokenEndpoint.matches(request) || !"authorization_code".equals(request.getParameter("grant_type"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        try {
            transaction.executeWithoutResult(status->{
                // A database guard works across service instances; code exchanges are infrequent.
                jdbc.queryForObject("SELECT id FROM sso_exchange_guard WHERE id=1 FOR UPDATE",Integer.class);
                try {chain.doFilter(request,response);}
                catch(IOException|ServletException error) {throw new ExchangeFailure(error);}
            });
        } catch(ExchangeFailure failure) {
            if(failure.getCause() instanceof IOException error)throw error;
            throw (ServletException)failure.getCause();
        }
    }
    private static final class ExchangeFailure extends RuntimeException {
        ExchangeFailure(Exception cause) {super(cause);}
    }
}
