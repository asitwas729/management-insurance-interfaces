package com.example.interfacehub.infrastructure.security;

import com.example.interfacehub.domain.auth.AppUser;
import com.example.interfacehub.infrastructure.persistence.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DefaultAdminUserInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminUserInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!appUserRepository.existsByUsername("admin")) {
            AppUser admin = AppUser.create(
                "admin",
                passwordEncoder.encode("servicehotkey"),
                "ADMIN,APPROVER,OPERATOR"
            );
            appUserRepository.save(admin);
        }
        if (!appUserRepository.existsByUsername("operator1")) {
            AppUser operator = AppUser.create(
                "operator1",
                passwordEncoder.encode("operator123"),
                "OPERATOR"
            );
            appUserRepository.save(operator);
        }
    }
}
