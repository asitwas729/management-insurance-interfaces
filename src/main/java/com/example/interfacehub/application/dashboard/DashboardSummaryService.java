package com.example.interfacehub.application.dashboard;

import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository.InterfaceStatProjection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardSummaryService {

    private final JdbcTemplate jdbcTemplate;
    private final ExecutionHistoryRepository executionHistoryRepository;

    public DashboardSummaryService(JdbcTemplate jdbcTemplate, ExecutionHistoryRepository executionHistoryRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.executionHistoryRepository = executionHistoryRepository;
    }

    @Cacheable(cacheNames = "dashboard", key = "'summary:' + #windowHours")
    public DashboardSummary summary(int windowHours) {
        int safeHours = Math.max(1, Math.min(windowHours, 24 * 30));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fromAt = now.minusHours(safeHours);

        long totalInterfaces = queryLong("select count(*) from interface_definition");
        long activeInterfaces = queryLong("select count(*) from interface_definition where status = 'ACTIVE'");
        long inactiveInterfaces = totalInterfaces - activeInterfaces;

        Map<String, Long> executionCounts = queryCounts(
            "select status, count(*) as cnt from execution_history where started_at >= ? group by status",
            fromAt
        );
        long execTotal = executionCounts.values().stream().mapToLong(v -> v).sum();
        long execSuccess = executionCounts.getOrDefault("SUCCESS", 0L);
        long execFailed = executionCounts.getOrDefault("FAILED", 0L);
        long execTimeout = executionCounts.getOrDefault("TIMEOUT", 0L);
        long execCancelled = executionCounts.getOrDefault("CANCELLED", 0L);

        long slaBreaches = queryLong("""
            select count(*)
            from execution_history e
            join interface_definition d on d.interface_code = e.interface_code
            where e.started_at >= ?
              and d.sla_millis is not null
              and e.latency_millis is not null
              and e.latency_millis > d.sla_millis
            """, fromAt);

        Map<String, Long> retryCounts = queryCounts("select status, count(*) as cnt from retry_task group by status");
        Map<String, Long> dlqReplayCounts = queryCounts("select status, count(*) as cnt from dlq_replay_request group by status");

        long dlqTotal = queryLong("select count(*) from dlq_message");
        long dlqRecent = queryLong("select count(*) from dlq_message where created_at >= ?", fromAt);

        List<TopFailureInterface> topFailures = queryTopFailures(fromAt);

        return new DashboardSummary(
            now,
            safeHours,
            totalInterfaces,
            activeInterfaces,
            inactiveInterfaces,
            new ExecutionWindow(execTotal, execSuccess, execFailed, execTimeout, execCancelled),
            slaBreaches,
            new StatusCounts(retryCounts),
            dlqTotal,
            dlqRecent,
            new StatusCounts(dlqReplayCounts),
            topFailures
        );
    }

    @Cacheable(cacheNames = "dashboard", key = "'interface-stats:' + #windowHours")
    public List<InterfaceStat> interfaceStats(int windowHours) {
        int safeHours = Math.max(1, Math.min(windowHours, 24 * 30));
        LocalDateTime since = LocalDateTime.now().minusHours(safeHours);

        List<InterfaceStatProjection> projections =
            executionHistoryRepository.findInterfaceStatsSince(since, ExecutionStatus.SUCCESS);
        Map<String, Long> slaBreachByCode = querySlaBreach(since);

        return projections.stream()
            .map(p -> {
                Long total = p.getTotal();
                Long successCount = p.getSuccessCount();
                double successRate = (total == null || total <= 0)
                    ? 0.0
                    : (successCount == null ? 0.0 : (double) successCount / (double) total);

                Double avgLatency = p.getAvgLatency();
                return new InterfaceStat(
                    p.getInterfaceCode(),
                    successRate,
                    avgLatency == null ? 0.0 : avgLatency,
                    slaBreachByCode.getOrDefault(p.getInterfaceCode(), 0L),
                    p.getLastExecutedAt()
                );
            })
            .toList();
    }

    @Cacheable(cacheNames = "dashboard", key = "'sla-breaches:' + #windowHours")
    public List<SlaBreachStat> slaBreaches(int windowHours) {
        int safeHours = Math.max(1, Math.min(windowHours, 24 * 30));
        LocalDateTime since = LocalDateTime.now().minusHours(safeHours);

        String sql = """
            select
              d.interface_code,
              d.sla_millis,
              count(*) as breach_count,
              avg(e.latency_millis) as avg_latency_ms
            from execution_history e
            join interface_definition d on d.interface_code = e.interface_code
            where e.started_at >= ?
              and d.sla_millis is not null
              and e.latency_millis is not null
              and e.latency_millis > d.sla_millis
            group by d.interface_code, d.sla_millis
            order by breach_count desc
            """;

        List<SlaBreachStat> list = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            list.add(new SlaBreachStat(
                rs.getString("interface_code"),
                rs.getLong("sla_millis"),
                rs.getLong("breach_count"),
                rs.getDouble("avg_latency_ms")
            ));
        }, since);
        return list;
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private Map<String, Long> queryCounts(String sql, Object... args) {
        Map<String, Long> map = new java.util.LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            map.put(rs.getString("status"), rs.getLong("cnt"));
        }, args);
        return map;
    }

    private List<TopFailureInterface> queryTopFailures(LocalDateTime fromAt) {
        String sql = """
            select
              interface_code,
              sum(case when status = 'FAILED' then 1 else 0 end) as failed,
              sum(case when status = 'TIMEOUT' then 1 else 0 end) as timeout,
              sum(case when status = 'CANCELLED' then 1 else 0 end) as cancelled,
              count(*) as total
            from execution_history
            where started_at >= ?
            group by interface_code
            order by (sum(case when status in ('FAILED','TIMEOUT','CANCELLED') then 1 else 0 end)) desc, total desc
            limit 8
            """;

        List<TopFailureInterface> list = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            String interfaceCode = rs.getString("interface_code");
            long failed = rs.getLong("failed");
            long timeout = rs.getLong("timeout");
            long cancelled = rs.getLong("cancelled");
            long total = rs.getLong("total");
            list.add(new TopFailureInterface(interfaceCode, total, failed, timeout, cancelled));
        }, fromAt);
        return list;
    }

    private Map<String, Long> querySlaBreach(LocalDateTime since) {
        Map<String, Long> map = new java.util.LinkedHashMap<>();
        jdbcTemplate.query("""
            select e.interface_code, count(*) as cnt
            from execution_history e
            join interface_definition d on d.interface_code = e.interface_code
            where e.started_at >= ?
              and d.sla_millis is not null
              and e.latency_millis is not null
              and e.latency_millis > d.sla_millis
            group by e.interface_code
            """, rs -> {
            map.put(rs.getString("interface_code"), rs.getLong("cnt"));
        }, since);
        return map;
    }

    public record DashboardSummary(
        LocalDateTime generatedAt,
        int windowHours,
        long totalInterfaces,
        long activeInterfaces,
        long inactiveInterfaces,
        ExecutionWindow executions,
        long slaBreaches,
        StatusCounts retryTasks,
        long dlqTotal,
        long dlqRecent,
        StatusCounts dlqReplayRequests,
        List<TopFailureInterface> topFailures
    ) {
    }

    public record ExecutionWindow(
        long total,
        long success,
        long failed,
        long timeout,
        long cancelled
    ) {
        public double successRate() {
            if (total <= 0) {
                return 0.0;
            }
            return (double) success / (double) total;
        }
    }

    public record StatusCounts(Map<String, Long> counts) {
        public long get(String key) {
            return counts == null ? 0L : counts.getOrDefault(key, 0L);
        }
    }

    public record TopFailureInterface(
        String interfaceCode,
        long total,
        long failed,
        long timeout,
        long cancelled
    ) {
        public long failures() {
            return failed + timeout + cancelled;
        }

        public double failureRate() {
            if (total <= 0) {
                return 0.0;
            }
            return (double) failures() / (double) total;
        }
    }

    public record InterfaceStat(
        String interfaceCode,
        double successRate,
        double avgLatencyMs,
        long slaBreachCount,
        LocalDateTime lastExecutedAt
    ) {
    }

    public record SlaBreachStat(
        String interfaceCode,
        long slaMillis,
        long slaBreachCount,
        double avgLatencyMs
    ) {
    }
}
