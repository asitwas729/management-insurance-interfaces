package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.auth.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP where r.username = :username and r.revoked = false")
    void revokeByUsername(@Param("username") String username);

    @Modifying
    @Query("delete from RefreshToken r where r.username = :username")
    void revokeAllByUsername(@Param("username") String username);
}
