package com.example.interfacehub.application.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RetentionSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RetentionService retentionService;

    @BeforeEach
    void setUp() {
        RetentionProperties properties = new RetentionProperties();
        properties.setExecutionHistoryDays(1);
        properties.setAuditLogDays(1);
        properties.setArchiveEnabled(false);
        retentionService = new RetentionService(jdbcTemplate, properties);
    }

    @Test
    void archive_and_purge_deletes_data_older_than_retention_period() {
        when(jdbcTemplate.update(startsWith("DELETE FROM execution_history"), any(LocalDateTime.class))).thenReturn(2);
        when(jdbcTemplate.update(startsWith("DELETE FROM audit_log"), any(LocalDateTime.class))).thenReturn(3);

        RetentionService.RetentionResult result = retentionService.archiveAndPurge();

        assertThat(result.execDeleted()).isEqualTo(2);
        assertThat(result.auditDeleted()).isEqualTo(3);
        verify(jdbcTemplate).update(startsWith("DELETE FROM execution_history"), any(LocalDateTime.class));
        verify(jdbcTemplate).update(startsWith("DELETE FROM audit_log"), any(LocalDateTime.class));
    }
}
