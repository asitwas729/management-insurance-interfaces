package com.example.interfacehub.application.auth;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.audit.AuditAction;
import com.example.interfacehub.domain.auth.AppUser;
import com.example.interfacehub.infrastructure.persistence.AppUserRepository;
import com.example.interfacehub.presentation.RegisterRequest;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final String DEFAULT_SELF_SIGNUP_ROLES = "OPERATOR";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public RegistrationService(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        AuditLogService auditLogService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AppUser registerSelf(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (appUserRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (request.password() != null && request.password().toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Password must not contain username");
        }

        AppUser user = AppUser.create(username, passwordEncoder.encode(request.password()), DEFAULT_SELF_SIGNUP_ROLES);
        AppUser saved = appUserRepository.save(user);
        auditLogService.record(
            username,
            AuditAction.CREATE,
            "APP_USER",
            username,
            null,
            "{\"roles\":\"" + DEFAULT_SELF_SIGNUP_ROLES + "\"}"
        );
        return saved;
    }

    private String normalizeUsername(String raw) {
        return raw == null ? "" : raw.trim();
    }
}

