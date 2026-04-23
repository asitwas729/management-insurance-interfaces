package com.example.interfacehub.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.interfacehub.infrastructure.persistence.RefreshTokenRepository;
import com.example.interfacehub.infrastructure.security.JwtTokenProvider;
import com.example.interfacehub.infrastructure.security.TokenBlacklistService;
import com.example.interfacehub.presentation.LoginRequest;
import com.example.interfacehub.presentation.LoginResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AppUserDetailsService userDetailsService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Captor
    private ArgumentCaptor<UsernamePasswordAuthenticationToken> authenticationCaptor;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_success_issues_access_and_refresh_token() {
        var authentication = new UsernamePasswordAuthenticationToken(
            "admin",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(eq("admin"), anyCollection())).thenReturn("access-token");

        LoginResponse response = authService.login(new LoginRequest("admin", "servicehotkey"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any());
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        assertThat(authenticationCaptor.getValue().getName()).isEqualTo("admin");
    }

    @Test
    void login_fails_when_password_is_wrong() {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong-password")))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_fails_when_user_does_not_exist() {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing", "password")))
            .isInstanceOf(BadCredentialsException.class);
    }
}
