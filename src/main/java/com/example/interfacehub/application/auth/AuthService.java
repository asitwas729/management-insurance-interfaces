package com.example.interfacehub.application.auth;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.audit.AuditAction;
import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.domain.auth.RefreshToken;
import com.example.interfacehub.infrastructure.persistence.RefreshTokenRepository;
import com.example.interfacehub.infrastructure.security.JwtTokenProvider;
import com.example.interfacehub.infrastructure.security.TokenBlacklistService;
import com.example.interfacehub.presentation.LoginRequest;
import com.example.interfacehub.presentation.LoginResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Service
public class AuthService {

    private static final long REFRESH_TOKEN_VALID_DAYS = 30;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserDetailsService userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final AuditLogService auditLogService;

    public AuthService(
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        AppUserDetailsService userDetailsService,
        RefreshTokenRepository refreshTokenRepository,
        TokenBlacklistService tokenBlacklistService,
        RateLimiterRegistry rateLimiterRegistry,
        AuditLogService auditLogService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String clientIp = getClientIp();
        String limitKey = "login:" + request.username() + ":" + clientIp;
        
        // Use custom config if "loginRateLimiter" not found (fallback to safe defaults)
        RateLimiter limiter;
        try {
            limiter = rateLimiterRegistry.rateLimiter(limitKey, "loginRateLimiter");
        } catch (Exception e) {
            RateLimiterConfig config = rateLimiterRegistry.getConfiguration("loginRateLimiter")
                .orElse(RateLimiterConfig.custom()
                    .limitForPeriod(5)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ZERO)
                    .build());
            limiter = rateLimiterRegistry.rateLimiter(limitKey, config);
        }

        return RateLimiter.decorateSupplier(limiter, () -> {
            try {
                Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
                );
                
                refreshTokenRepository.revokeByUsername(authentication.getName());
                String accessToken = jwtTokenProvider.generateToken(authentication.getName(), authentication.getAuthorities());
                RefreshToken refreshToken = RefreshToken.issue(authentication.getName(), REFRESH_TOKEN_VALID_DAYS);
                refreshTokenRepository.save(refreshToken);

                return LoginResponse.bearer(accessToken, refreshToken.getToken());
            } catch (Exception e) {
                auditLogService.record(
                    request.username(),
                    AuditAction.AUTHENTICATION_FAILED,
                    "LOGIN",
                    "WEB",
                    e.getMessage(),
                    clientIp
                );
                throw e;
            }
        }).get();
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(rawRefreshToken)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isRevoked() || stored.isExpired()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token has expired or been revoked");
        }

        stored.revoke();

        UserDetails userDetails = userDetailsService.loadUserByUsername(stored.getUsername());
        String newAccessToken = jwtTokenProvider.generateToken(stored.getUsername(), userDetails.getAuthorities());

        RefreshToken newRefreshToken = RefreshToken.issue(stored.getUsername(), REFRESH_TOKEN_VALID_DAYS);
        refreshTokenRepository.save(newRefreshToken);

        return LoginResponse.bearer(newAccessToken, newRefreshToken.getToken());
    }

    @Transactional
    public void logout(String accessToken) {
        try {
            tokenBlacklistService.blacklist(accessToken, jwtTokenProvider.extractExpiry(accessToken));
        } catch (Exception ignored) {
        }
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "UNKNOWN";
        HttpServletRequest request = attrs.getRequest();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
