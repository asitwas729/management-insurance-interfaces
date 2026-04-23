package com.example.interfacehub.application.auth;

import com.example.interfacehub.infrastructure.persistence.RefreshTokenRepository;
import com.example.interfacehub.infrastructure.security.JwtTokenProvider;
import com.example.interfacehub.infrastructure.security.TokenBlacklistService;
import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.presentation.LoginRequest;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private AuthService authService;

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AppUserDetailsService userDetailsService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest request;
    @Mock private Authentication authentication;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(java.time.Duration.ofMinutes(1))
            .build();
            
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();
        registry.addConfiguration("loginRateLimiter", config);

        authService = new AuthService(
            authenticationManager,
            jwtTokenProvider,
            userDetailsService,
            refreshTokenRepository,
            tokenBlacklistService,
            registry,
            auditLogService
        );

        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);
    }

    @Test
    void login_rate_limit_throws_RequestNotPermitted() {
        LoginRequest loginRequest = new LoginRequest("user", "pass");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getName()).thenReturn("user");

        // First call should pass
        authService.login(loginRequest);

        // Second call should throw RequestNotPermitted
        assertThrows(RequestNotPermitted.class, () -> authService.login(loginRequest));
        
        verify(authenticationManager, times(1)).authenticate(any());
    }
}
