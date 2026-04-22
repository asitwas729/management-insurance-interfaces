package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.standard.ErrorCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorCatalogRepository extends JpaRepository<ErrorCatalog, String> {
}
