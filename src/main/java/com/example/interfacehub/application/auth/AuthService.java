package com.example.interfacehub.application.auth;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.auth.RefreshToken;
import com.example.interfacehub.infrastructure.persistence.RefreshTokenRepository;
import com.example.interfacehub.infrastructure.security.JwtTokenProvider;
import com.example.interfacehub.infrastructure.security.TokenBlacklistService;
import com.example.interfacehub.presentation.LoginRequest;
import com.example.interfacehub.presentation.LoginResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final long REFRESH_TOKEN_VALID_DAYS = 30;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserDetailsService userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        AppUserDetailsService userDetailsService,
        RefreshTokenRepository refreshTokenRepository,
        TokenBlacklistService tokenBlacklistService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        String accessToken = jwtTokenProvider.generateToken(authentication.getName(), authentication.getAuthorities());

        RefreshToken refreshToken = RefreshToken.issue(authentication.getName(), REFRESH_TOKEN_VALID_DAYS);
        refreshTokenRepository.save(refreshToken);

        return LoginResponse.bearer(accessToken, refreshToken.getToken());
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
            // token already invalid — blacklisting is best-effort
        }
    }
}
