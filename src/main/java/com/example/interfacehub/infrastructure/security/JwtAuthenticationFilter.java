package com.example.interfacehub.infrastructure.security;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.application.auth.AppUserDetailsService;
import com.example.interfacehub.domain.audit.AuditAction;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserDetailsService appUserDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditLogService auditLogService;

    public JwtAuthenticationFilter(
        JwtTokenProvider jwtTokenProvider,
        AppUserDetailsService appUserDetailsService,
        TokenBlacklistService tokenBlacklistService,
        AuditLogService auditLogService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.appUserDetailsService = appUserDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.auditLogService = auditLogService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            boolean isValid = jwtTokenProvider.validate(token);
            boolean isBlacklisted = tokenBlacklistService.isBlacklisted(token);

            if (isValid && !isBlacklisted) {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    String username = jwtTokenProvider.extractUsername(token);
                    UserDetails userDetails = appUserDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } else {
                String reason = isBlacklisted ? "Token is blacklisted" : "Invalid token signature or expired";
                auditLogService.record(
                    "SYSTEM",
                    AuditAction.AUTHENTICATION_FAILED,
                    "JWT_TOKEN",
                    "UNKNOWN",
                    reason,
                    request.getRemoteAddr()
                );
            }
        }
        filterChain.doFilter(request, response);
    }
}
