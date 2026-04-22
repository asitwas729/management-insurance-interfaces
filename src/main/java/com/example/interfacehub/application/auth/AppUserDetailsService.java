package com.example.interfacehub.application.auth;

import com.example.interfacehub.domain.auth.AppUser;
import com.example.interfacehub.infrastructure.persistence.AppUserRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(toAuthorities(user.getRoles()))
            .build();
    }

    private List<GrantedAuthority> toAuthorities(String roles) {
        return Arrays.stream(roles.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .map(SimpleGrantedAuthority::new)
            .map(authority -> (GrantedAuthority) authority)
            .toList();
    }
}
