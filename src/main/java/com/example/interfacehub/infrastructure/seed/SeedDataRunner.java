package com.example.interfacehub.infrastructure.seed;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("seed")
public class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final SeedProperties seedProperties;

    public SeedDataRunner(JdbcTemplate jdbcTemplate, SeedProperties seedProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.seedProperties = seedProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedProperties.isEnabled()) {
            return;
        }
        if (alreadySeeded()) {
            log.info("[seed] Skip (already seeded)");
            return;
        }

        Random random = new Random(seedProperties.getRandomSeed());
        List<InterfaceSpec> interfaceSpecs = interfaceSpecs();

        insertInterfaceDefinitions(interfaceSpecs);
        insertInterfaceConfigVersions(interfaceSpecs, random);
        insertPolicyTemplates(random);
        insertInterfacePolicyBindings(interfaceSpecs, random);
        insertSchedules(interfaceSpecs, random);

        SeedResult seedResult = insertExecutionsAndRelated(interfaceSpecs, random);
        insertRetryTasks(interfaceSpecs, seedResult.failedOrTimeoutExecutions, random);
        SeedDlqResult seedDlqResult = insertDlq(interfaceSpecs, random);
        insertDlqReplayRequests(seedDlqResult.dlqMessageIds, random);
        int auditCount = insertAuditLogs(interfaceSpecs, seedResult.sampleExecutionIds, seedDlqResult.sampleDlqIds, random);

        log.info(
            "[seed] Done interfaces={}, executions={}, retries={}, dlq={}, audit={}",
            interfaceSpecs.size(),
            seedResult.executionCount,
            seedResult.retryCandidatesCount,
            seedDlqResult.dlqCount,
            auditCount
        );
    }

    private boolean alreadySeeded() {
        try {
            Integer cnt = jdbcTemplate.queryForObject("select count(*) from execution_history", Integer.class);
            return cnt != null && cnt > 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private void insertInterfaceDefinitions(List<InterfaceSpec> specs) {
        String sql = """
            insert into interface_definition
            (interface_code, name, protocol_type, owner_team, business_category, external_org, call_direction, sla_millis, status)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        List<Object[]> batch = new ArrayList<>();
        for (InterfaceSpec spec : specs) {
            batch.add(new Object[] {
                spec.interfaceCode,
                spec.name,
                spec.protocolType,
                spec.ownerTeam,
                spec.businessCategory,
                spec.externalOrg,
                spec.callDirection,
                spec.slaMillis,
                spec.status
            });
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private void insertInterfaceConfigVersions(List<InterfaceSpec> specs, Random random) {
        String sql = """
            insert into interface_config_version
            (interface_definition_id, version, endpoint, auth_type, headers_json, timeout_millis, published, environment, protocol_config_json,
             request_sample, response_sample, mapping_rule_text, field_description_text, error_code_guide_text,
             sandbox_mode, mock_http_status, mock_response_body, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        List<Object[]> batch = new ArrayList<>();
        for (InterfaceSpec spec : specs) {
            long definitionId = interfaceDefinitionId(spec.interfaceCode);
            for (int version = 1; version <= spec.configVersions; version++) {
                boolean published = version == 1;
                boolean sandboxMode = spec.supportsSandbox && version == 2;
                Integer mockStatus = sandboxMode ? (random.nextInt(10) < 8 ? 200 : 500) : null;
                String mockBody = sandboxMode ? (mockStatus == 200 ? "{\"result\":\"OK\",\"sandbox\":true}" : "{\"error\":\"MOCK_FAIL\"}") : null;
                batch.add(new Object[] {
                    definitionId,
                    version,
                    endpointFor(spec, version),
                    authTypeFor(spec),
                    headersFor(spec),
                    spec.timeoutMillis,
                    published,
                    "PROD",
                    protocolConfigFor(spec),
                    requestSampleFor(spec),
                    responseSampleFor(spec),
                    mappingRuleFor(spec),
                    fieldDescriptionFor(spec),
                    errorGuideFor(spec),
                    sandboxMode,
                    mockStatus,
                    mockBody,
                    Timestamp.valueOf(LocalDateTime.now().minusDays(Math.max(1, 7 - version)))
                });
            }
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private void insertPolicyTemplates(Random random) {
        // V13 inserts DEFAULT. Add a few more for realistic policy binding behavior.
        String existsSql = "select count(*) from policy_template where policy_name = ?";
        String insertSql = """
            insert into policy_template
            (policy_name, auth_type, timeout_millis, retry_max_attempts, retry_interval_millis, rate_limit_per_minute,
             allowed_partner_ids_json, mask_request_payload, mask_response_payload, allowed_roles_json, enabled)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        List<Object[]> templates = List.of(
            new Object[] {"FAST_REST", "API_KEY", 1500L, 1, 100L, 1800, "[\"FSS\",\"HIRA\"]", true, true, "[\"OPERATOR\"]", true},
            new Object[] {"STRICT_SOAP", "MTLS", 2500L, 0, 0L, 600, "[\"LEGACY\"]", true, true, "[\"OPERATOR\",\"APPROVER\"]", true},
            new Object[] {"PARTNER_A", "CLIENT_CREDENTIALS", 3000L, 2, 200L, 300, "[\"PARTNER_A\"]", true, true, "[\"OPERATOR\"]", true},
            new Object[] {"PARTNER_B", "JWT", 2800L, 2, 300L, 240, "[\"PARTNER_B\"]", true, true, "[\"OPERATOR\"]", true},
            new Object[] {"HIGH_RISK", "BASIC", 4000L, 3, 500L, 120, "[]", true, true, "[\"ADMIN\"]", true}
        );

        for (Object[] t : templates) {
            Integer cnt = jdbcTemplate.queryForObject(existsSql, Integer.class, t[0]);
            if (cnt != null && cnt > 0) {
                continue;
            }
            jdbcTemplate.update(insertSql, t);
        }

        // Small touch: randomly disable one template to demonstrate enabled filtering.
        if (random.nextInt(10) == 0) {
            jdbcTemplate.update("update policy_template set enabled = false where policy_name = 'HIGH_RISK'");
        }
    }

    private void insertInterfacePolicyBindings(List<InterfaceSpec> specs, Random random) {
        String insertSql = """
            insert into interface_policy_binding
            (interface_definition_id, policy_template_id, partner_id, priority, enabled, created_at)
            values (?, ?, ?, ?, ?, ?)
            """;
        List<Object[]> batch = new ArrayList<>();
        long defaultPolicyId = policyTemplateId("DEFAULT");
        long fastRestId = policyTemplateId("FAST_REST");
        long strictSoapId = policyTemplateId("STRICT_SOAP");
        long partnerAId = policyTemplateId("PARTNER_A");
        long partnerBId = policyTemplateId("PARTNER_B");

        for (InterfaceSpec spec : specs) {
            long ifId = interfaceDefinitionId(spec.interfaceCode);
            // Base binding (DEFAULT)
            batch.add(new Object[] {ifId, defaultPolicyId, null, 100, true, Timestamp.valueOf(LocalDateTime.now().minusDays(30))});

            // Partner-specific override examples
            if ("REST".equals(spec.protocolType)) {
                batch.add(new Object[] {ifId, fastRestId, spec.externalOrg, 10, true, Timestamp.valueOf(LocalDateTime.now().minusDays(10))});
            }
            if ("SOAP".equals(spec.protocolType)) {
                batch.add(new Object[] {ifId, strictSoapId, "LEGACY", 10, true, Timestamp.valueOf(LocalDateTime.now().minusDays(10))});
            }
            if (random.nextInt(10) < 3) {
                batch.add(new Object[] {ifId, partnerAId, "PARTNER_A", 20, true, Timestamp.valueOf(LocalDateTime.now().minusDays(5))});
            }
            if (random.nextInt(10) < 2) {
                batch.add(new Object[] {ifId, partnerBId, "PARTNER_B", 25, true, Timestamp.valueOf(LocalDateTime.now().minusDays(3))});
            }
        }
        jdbcTemplate.batchUpdate(insertSql, batch);
    }

    private void insertSchedules(List<InterfaceSpec> specs, Random random) {
        String insertSql = """
            insert into interface_schedule
            (interface_code, cron_expression, enabled, payload_template, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?)
            """;
        List<Object[]> batch = new ArrayList<>();
        for (InterfaceSpec spec : specs) {
            if (!spec.scheduledCandidate) {
                continue;
            }
            String cron = random.nextBoolean() ? "0 */5 * * * *" : "0 0/10 * * * *";
            String payloadTemplate = "{\"idempotencyKey\":\"SCHED-" + spec.interfaceCode + "-${yyyyMMddHHmm}\",\"payload\":{\"policyNo\":\"P${yyyyMMdd}0001\",\"eventType\":\"SCHEDULED\"}}";
            LocalDateTime base = LocalDateTime.now().minusDays(60);
            batch.add(new Object[] {
                spec.interfaceCode,
                cron,
                true,
                payloadTemplate,
                Timestamp.valueOf(base),
                Timestamp.valueOf(base.plusDays(1))
            });
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(insertSql, batch);
        }
    }

    private SeedResult insertExecutionsAndRelated(List<InterfaceSpec> specs, Random random) {
        int days = Math.max(seedProperties.getDays(), 1);
        int total = Math.max(seedProperties.getExecutionCount(), 1);

        String execSql = """
            insert into execution_history
            (execution_id, interface_code, protocol_type, trigger_type, status, started_at, ended_at, latency_millis,
             request_payload, response_payload, error_code, error_message)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        String idemSql = """
            insert into idempotency_record
            (idempotency_key, interface_code, execution_id, status, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?)
            """;

        List<Object[]> execBatch = new ArrayList<>(2000);
        List<Object[]> idemBatch = new ArrayList<>(2000);

        List<FailedExecution> failedOrTimeout = new ArrayList<>();
        List<String> sampleExecutionIds = new ArrayList<>();

        int inserted = 0;
        int chunk = 1000;
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < total; i++) {
            InterfaceSpec spec = pickWeightedByProtocol(specs, random);

            String executionId = UUID.randomUUID().toString();
            String status = pickStatus(random);
            String trigger = pickTrigger(random);

            LocalDateTime startedAt = now.minusDays(random.nextInt(days)).minusMinutes(random.nextInt(24 * 60));
            long latency = latencyMillisFor(status, spec, random);
            LocalDateTime endedAt = startedAt.plus(Duration.ofMillis(Math.max(latency, 0)));

            String requestPayload = requestPayloadFor(spec, trigger, random);
            String responsePayload = "SUCCESS".equals(status) ? responsePayloadFor(spec, random) : null;

            String errorCode = null;
            String errorMessage = null;
            if (!"SUCCESS".equals(status)) {
                errorCode = errorCodeFor(status, spec, random);
                errorMessage = errorMessageFor(errorCode, random);
            }

            execBatch.add(new Object[] {
                executionId,
                spec.interfaceCode,
                spec.protocolType,
                trigger,
                status,
                Timestamp.valueOf(startedAt),
                Timestamp.valueOf(endedAt),
                latency,
                requestPayload,
                responsePayload,
                errorCode,
                errorMessage
            });

            String idempotencyKey = idempotencyKeyFor(spec.interfaceCode, startedAt, i);
            String idemStatus = "SUCCESS".equals(status) ? "SUCCESS" : "FAILED";
            idemBatch.add(new Object[] {
                idempotencyKey,
                spec.interfaceCode,
                executionId,
                idemStatus,
                Timestamp.valueOf(startedAt),
                Timestamp.valueOf(endedAt)
            });

            if ("FAILED".equals(status) || "TIMEOUT".equals(status)) {
                failedOrTimeout.add(new FailedExecution(spec.interfaceCode, executionId));
            }
            if (sampleExecutionIds.size() < 20) {
                sampleExecutionIds.add(executionId);
            }

            if (execBatch.size() >= chunk) {
                jdbcTemplate.batchUpdate(execSql, execBatch);
                jdbcTemplate.batchUpdate(idemSql, idemBatch);
                inserted += execBatch.size();
                execBatch.clear();
                idemBatch.clear();
            }
        }
        if (!execBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(execSql, execBatch);
            jdbcTemplate.batchUpdate(idemSql, idemBatch);
            inserted += execBatch.size();
        }

        // Duplicate idempotency attempts are handled at API level; seed a tiny amount of RESERVED records to mimic in-flight.
        int reservedCount = Math.max(1, total / 5000);
        String reservedSql = """
            insert into idempotency_record
            (idempotency_key, interface_code, execution_id, status, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?)
            """;
        for (int i = 0; i < reservedCount; i++) {
            InterfaceSpec spec = specs.get(random.nextInt(specs.size()));
            LocalDateTime t = now.minusMinutes(random.nextInt(120));
            jdbcTemplate.update(
                reservedSql,
                "RESERVED-" + spec.interfaceCode + "-" + UUID.randomUUID(),
                spec.interfaceCode,
                UUID.randomUUID().toString(),
                "RESERVED",
                Timestamp.valueOf(t),
                Timestamp.valueOf(t)
            );
        }

        return new SeedResult(inserted, failedOrTimeout, sampleExecutionIds, failedOrTimeout.size());
    }

    private void insertRetryTasks(List<InterfaceSpec> specs, List<FailedExecution> candidates, Random random) {
        if (candidates.isEmpty()) {
            return;
        }
        String sql = """
            insert into retry_task
            (interface_definition_id, original_execution_id, status, requester, request_reason_code, request_reason_detail,
             approver, reject_reason, created_at, approved_at, executed_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        int toCreate = Math.min(candidates.size(), Math.max(50, candidates.size() / 3));
        List<Object[]> batch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < toCreate; i++) {
            FailedExecution fe = candidates.get(random.nextInt(candidates.size()));
            long ifId = interfaceDefinitionId(fe.interfaceCode);

            String status = pickRetryStatus(random);
            String requester = random.nextBoolean() ? "operator1" : "admin";
            String reasonCode = random.nextBoolean() ? "DOWNSTREAM" : "TIMEOUT";
            String reasonDetail = "Reprocess requested for " + fe.originalExecutionId.substring(0, 8);
            String approver = null;
            String rejectReason = null;

            LocalDateTime createdAt = now.minusDays(random.nextInt(30)).minusMinutes(random.nextInt(24 * 60));
            LocalDateTime approvedAt = null;
            LocalDateTime executedAt = null;

            if ("APPROVED".equals(status) || "EXECUTED".equals(status) || "FAILED".equals(status)) {
                approver = "admin";
                approvedAt = createdAt.plusMinutes(10 + random.nextInt(120));
            }
            if ("REJECTED".equals(status)) {
                approver = "admin";
                approvedAt = createdAt.plusMinutes(10 + random.nextInt(120));
                rejectReason = "Reject: invalid payload or cooldown not met";
            }
            if ("EXECUTED".equals(status) || "FAILED".equals(status)) {
                executedAt = (approvedAt == null ? createdAt : approvedAt).plusMinutes(5 + random.nextInt(60));
            }

            batch.add(new Object[] {
                ifId,
                fe.originalExecutionId,
                status,
                requester,
                reasonCode,
                reasonDetail,
                approver,
                rejectReason,
                Timestamp.valueOf(createdAt),
                approvedAt == null ? null : Timestamp.valueOf(approvedAt),
                executedAt == null ? null : Timestamp.valueOf(executedAt)
            });
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private SeedDlqResult insertDlq(List<InterfaceSpec> specs, Random random) {
        List<InterfaceSpec> mqSpecs = specs.stream().filter(s -> "MQ".equals(s.protocolType)).toList();
        if (mqSpecs.isEmpty()) {
            return new SeedDlqResult(0, List.of(), List.of());
        }

        int dlqCount = Math.max(30, seedProperties.getExecutionCount() / 1000);
        int beforeCount = jdbcTemplate.queryForObject("select count(*) from dlq_message", Integer.class);
        String insertSql = """
            insert into dlq_message
            (interface_code, topic, payload, reason, replay_count, last_replayed_at, created_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

        List<Object[]> batch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < dlqCount; i++) {
            InterfaceSpec spec = mqSpecs.get(random.nextInt(mqSpecs.size()));
            String topic = "interfacehub.inbound";
            String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"" + (random.nextBoolean() ? "CONTRACT" : "CLAIM") + "\",\"policyNo\":\"P" + now.toLocalDate().toString().replace("-", "") + String.format("%04d", random.nextInt(9999)) + "\"}";
            String reason = random.nextBoolean() ? "MQ_CONSUME_FAILED" : "DOWNSTREAM_UNAVAILABLE";
            int replayCount = random.nextInt(4);
            LocalDateTime createdAt = now.minusDays(random.nextInt(60)).minusMinutes(random.nextInt(24 * 60));
            LocalDateTime lastReplayedAt = replayCount == 0 ? null : createdAt.plusHours(1 + random.nextInt(72));
            batch.add(new Object[] {
                spec.interfaceCode,
                topic,
                payload,
                reason,
                replayCount,
                lastReplayedAt == null ? null : Timestamp.valueOf(lastReplayedAt),
                Timestamp.valueOf(createdAt)
            });
        }
        jdbcTemplate.batchUpdate(insertSql, batch);

        List<Long> insertedIds = jdbcTemplate.queryForList(
            "select id from dlq_message order by id asc limit ? offset ?",
            Long.class,
            dlqCount,
            beforeCount
        );
        List<Long> sampleIds = insertedIds.stream().limit(10).toList();
        return new SeedDlqResult(dlqCount, insertedIds, sampleIds);
    }

    private void insertDlqReplayRequests(List<Long> dlqMessageIds, Random random) {
        if (dlqMessageIds.isEmpty()) {
            return;
        }
        int toCreate = Math.max(10, dlqMessageIds.size() * 4 / 10);
        String sql = """
            insert into dlq_replay_request
            (dlq_message_id, status, requester, request_reason_code, request_reason_detail, approver, reject_reason, payload_override_json,
             approved_at, executed_at, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        List<Object[]> batch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < toCreate; i++) {
            Long dlqId = dlqMessageIds.get(random.nextInt(dlqMessageIds.size()));
            String status = pickRetryStatus(random); // same set as retry_task
            String requester = random.nextBoolean() ? "operator1" : "admin";
            String reasonCode = "UNSPECIFIED";
            String reasonDetail = "Replay requested for dlq_message_id=" + dlqId;
            String approver = null;
            String rejectReason = null;
            String payloadOverride = random.nextInt(10) < 2 ? "{\"override\":true}" : null;

            LocalDateTime createdAt = now.minusDays(random.nextInt(30)).minusMinutes(random.nextInt(24 * 60));
            LocalDateTime approvedAt = null;
            LocalDateTime executedAt = null;

            if ("APPROVED".equals(status) || "EXECUTED".equals(status) || "FAILED".equals(status)) {
                approver = "admin";
                approvedAt = createdAt.plusMinutes(10 + random.nextInt(120));
            }
            if ("REJECTED".equals(status)) {
                approver = "admin";
                approvedAt = createdAt.plusMinutes(10 + random.nextInt(120));
                rejectReason = "Reject: exceeds replay limit or invalid override";
            }
            if ("EXECUTED".equals(status) || "FAILED".equals(status)) {
                executedAt = (approvedAt == null ? createdAt : approvedAt).plusMinutes(5 + random.nextInt(60));
            }

            batch.add(new Object[] {
                dlqId,
                status,
                requester,
                reasonCode,
                reasonDetail,
                approver,
                rejectReason,
                payloadOverride,
                approvedAt == null ? null : Timestamp.valueOf(approvedAt),
                executedAt == null ? null : Timestamp.valueOf(executedAt),
                Timestamp.valueOf(createdAt)
            });
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private int insertAuditLogs(
        List<InterfaceSpec> specs,
        List<String> sampleExecutionIds,
        List<Long> sampleDlqIds,
        Random random
    ) {
        String sql = """
            insert into audit_log
            (actor, action, target_type, target_id, before_value, after_value, created_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """;
        List<Object[]> batch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (InterfaceSpec spec : specs) {
            batch.add(auditRow("admin", "CREATE", "INTERFACE", spec.interfaceCode, null, "{\"status\":\"ACTIVE\"}", now.minusDays(80)));
            batch.add(auditRow("admin", "PUBLISH_CONFIG", "CONFIG", spec.interfaceCode + ":v1", "{\"published\":false}", "{\"published\":true}", now.minusDays(70)));
        }
        for (String execId : sampleExecutionIds) {
            batch.add(auditRow("operator1", "REPROCESSED", "EXECUTION", execId, null, "{\"requested\":true}", now.minusDays(random.nextInt(30))));
        }
        for (Long dlqId : sampleDlqIds) {
            batch.add(auditRow("operator1", "REPLAY_DLQ", "DLQ", String.valueOf(dlqId), null, "{\"requested\":true}", now.minusDays(random.nextInt(30))));
        }

        // A bit of auth noise
        batch.add(auditRow("operator1", "LOGIN_SUCCESS", "AUTH", "operator1", null, "{\"ip\":\"127.0.0.1\"}", now.minusDays(1)));
        batch.add(auditRow("unknown", "AUTHENTICATION_FAILED", "AUTH", "operator1", null, "{\"reason\":\"bad_password\"}", now.minusHours(3)));

        jdbcTemplate.batchUpdate(sql, batch);
        return batch.size();
    }

    private Object[] auditRow(String actor, String action, String targetType, String targetId, String before, String after, LocalDateTime t) {
        return new Object[] {actor, action, targetType, targetId, before, after, Timestamp.valueOf(t)};
    }

    private long interfaceDefinitionId(String interfaceCode) {
        return jdbcTemplate.queryForObject(
            "select id from interface_definition where interface_code = ?",
            Long.class,
            interfaceCode
        );
    }

    private long policyTemplateId(String policyName) {
        return jdbcTemplate.queryForObject(
            "select id from policy_template where policy_name = ?",
            Long.class,
            policyName
        );
    }


    private static String endpointFor(InterfaceSpec spec, int version) {
        String base = switch (spec.protocolType) {
            case "REST" -> "https://api." + spec.externalOrg.toLowerCase() + ".example.com/" + spec.interfaceCode.toLowerCase();
            case "SOAP" -> "https://soap." + spec.externalOrg.toLowerCase() + ".example.com/" + spec.interfaceCode.toLowerCase();
            case "MQ" -> "kafka://interfacehub.inbound";
            case "BATCH" -> "batch://jobs/" + spec.interfaceCode.toLowerCase();
            case "SFTP" -> "sftp://files." + spec.externalOrg.toLowerCase() + ".example.com/inbox";
            default -> "https://external.example.com/" + spec.interfaceCode.toLowerCase();
        };
        return version == 1 ? base : base + "?v=" + version;
    }

    private static String authTypeFor(InterfaceSpec spec) {
        return switch (spec.protocolType) {
            case "REST" -> "API_KEY";
            case "SOAP" -> "MTLS";
            default -> "NONE";
        };
    }

    private static String headersFor(InterfaceSpec spec) {
        if (!"REST".equals(spec.protocolType)) {
            return "{}";
        }
        return "{\"X-Partner-Id\":\"" + spec.externalOrg + "\",\"X-Request-Source\":\"interfacehub\"}";
    }

    private static String protocolConfigFor(InterfaceSpec spec) {
        return switch (spec.protocolType) {
            case "REST" -> "{\"method\":\"POST\",\"contentType\":\"application/json\"}";
            case "SOAP" -> "{\"soapAction\":\"" + spec.interfaceCode + "\"}";
            case "MQ" -> "{\"topic\":\"interfacehub.inbound\",\"dlq\":\"interfacehub.dlq\"}";
            case "SFTP" -> "{\"path\":\"/inbox\",\"mode\":\"PUT\"}";
            case "BATCH" -> "{\"jobName\":\"" + spec.interfaceCode + "\"}";
            default -> "{}";
        };
    }

    private static String requestSampleFor(InterfaceSpec spec) {
        return "{\"idempotencyKey\":\"" + spec.interfaceCode + "-SAMPLE-0001\",\"payload\":{\"policyNo\":\"P202604220001\",\"eventType\":\"NEW_CONTRACT\",\"rrn\":\"******-*******\",\"cardNo\":\"************1111\"}}";
    }

    private static String responseSampleFor(InterfaceSpec spec) {
        return "{\"result\":\"OK\",\"interfaceCode\":\"" + spec.interfaceCode + "\",\"processedAt\":\"2026-04-23T10:00:00\"}";
    }

    private static String mappingRuleFor(InterfaceSpec spec) {
        return "- policyNo -> payload.policyNo\n- eventType -> payload.eventType\n- rrn -> masked\n- cardNo -> masked";
    }

    private static String fieldDescriptionFor(InterfaceSpec spec) {
        return "- policyNo: 보험증권번호\n- eventType: 이벤트 타입\n- rrn: 주민등록번호(마스킹)\n- cardNo: 카드번호(마스킹)";
    }

    private static String errorGuideFor(InterfaceSpec spec) {
        return "- TIMEOUT: 외부 호출 타임아웃\n- EXT_5XX: 외부 5xx\n- MQ_CONSUME_FAILED: MQ 처리 실패\n- SOAP_CALL_FAILED: SOAP 연동 실패";
    }

    private static InterfaceSpec pickWeightedByProtocol(List<InterfaceSpec> specs, Random random) {
        int r = random.nextInt(100);
        String desired;
        if (r < 60) {
            desired = "REST";
        } else if (r < 80) {
            desired = "MQ";
        } else if (r < 90) {
            desired = "SOAP";
        } else if (r < 97) {
            desired = "BATCH";
        } else {
            desired = "SFTP";
        }
        List<InterfaceSpec> filtered = specs.stream().filter(s -> desired.equals(s.protocolType)).toList();
        if (filtered.isEmpty()) {
            return specs.get(random.nextInt(specs.size()));
        }
        return filtered.get(random.nextInt(filtered.size()));
    }

    private static String pickStatus(Random random) {
        int r = random.nextInt(100);
        if (r < 92) {
            return "SUCCESS";
        }
        if (r < 97) {
            return "FAILED";
        }
        if (r < 99) {
            return "TIMEOUT";
        }
        return "CANCELLED";
    }

    private static String pickTrigger(Random random) {
        int r = random.nextInt(100);
        if (r < 50) {
            return "SCHEDULED";
        }
        if (r < 80) {
            return "MANUAL";
        }
        if (r < 95) {
            return "RETRY";
        }
        return "SANDBOX";
    }

    private static long latencyMillisFor(String status, InterfaceSpec spec, Random random) {
        long base = switch (spec.protocolType) {
            case "REST" -> 300;
            case "MQ" -> 120;
            case "SOAP" -> 600;
            case "BATCH" -> 1500;
            case "SFTP" -> 900;
            default -> 500;
        };
        long jitter = random.nextInt(1200);
        if ("TIMEOUT".equals(status)) {
            return Math.max(spec.timeoutMillis + random.nextInt(1500), base + jitter);
        }
        if ("FAILED".equals(status)) {
            return base + jitter;
        }
        if ("CANCELLED".equals(status)) {
            return base + random.nextInt(200);
        }
        return base + jitter;
    }

    private static String errorCodeFor(String status, InterfaceSpec spec, Random random) {
        if ("TIMEOUT".equals(status)) {
            return "TIMEOUT";
        }
        if ("CANCELLED".equals(status)) {
            return "EXECUTION_CANCELLED";
        }
        String category = pickFailureCategory(random);
        return switch (category) {
            case "NETWORK" -> switch (spec.protocolType) {
                case "REST" -> "REST_CALL_FAILED";
                case "SOAP" -> "SOAP_CALL_FAILED";
                case "SFTP" -> "SFTP_TRANSFER_FAILED";
                case "MQ" -> "MQ_CONSUME_FAILED";
                case "BATCH" -> "BATCH_FAILED";
                default -> "REST_CALL_FAILED";
            };
            case "AUTH" -> random.nextBoolean() ? "UNAUTHORIZED" : "FORBIDDEN_ROLE";
            case "DATA" -> random.nextBoolean() ? "INVALID_REQUEST" : "INVALID_CONFIG";
            case "BUSINESS" -> "DUPLICATE_REQUEST";
            case "POLICY" -> {
                int r = random.nextInt(100);
                if (r < 55) yield "RATE_LIMITED";
                if (r < 85) yield "CIRCUIT_OPEN";
                yield "BULKHEAD_FULL";
            }
            default -> switch (spec.protocolType) {
                case "REST" -> random.nextBoolean() ? "EXT_5XX" : "EXT_4XX";
                case "SOAP" -> random.nextBoolean() ? "SOAP_FAULT" : "SOAP_CALL_FAILED";
                case "MQ" -> "MQ_CONSUME_FAILED";
                case "SFTP" -> "SFTP_TRANSFER_FAILED";
                case "BATCH" -> "BATCH_FAILED";
                default -> "INTERNAL_ERROR";
            };
        };
    }

    private static String errorMessageFor(String errorCode, Random random) {
        return switch (errorCode) {
            case "TIMEOUT" -> "READ_TIMEOUT: External call timed out";
            case "EXT_5XX" -> "External server returned 5xx";
            case "EXT_4XX" -> "External agency returned 4xx";
            case "REST_CALL_FAILED" -> {
                int r = random.nextInt(100);
                if (r < 40) yield "CONNECTION reset by peer";
                if (r < 65) yield "SSL handshake failed";
                yield "DNS lookup failed";
            }
            case "SOAP_CALL_FAILED" -> "CONNECT: SOAP call failed (connection refused)";
            case "SOAP_FAULT" -> "SOAP fault: SCHEMA mismatch";
            case "MQ_CONSUME_FAILED" -> {
                int r = random.nextInt(100);
                if (r < 50) yield "SOCKET read error while consuming message";
                yield "DB deadlock detected while persisting MQ offset";
            }
            case "SFTP_TRANSFER_FAILED" -> "CONNECT: SFTP transfer failed (connection refused)";
            case "BATCH_FAILED" -> "SYSTEM: Batch job failed (unexpected null)";
            case "EXECUTION_CANCELLED" -> "Execution cancelled by operator";
            case "UNAUTHORIZED" -> "UNAUTHORIZED: TOKEN expired";
            case "FORBIDDEN_ROLE" -> "FORBIDDEN: role not permitted";
            case "INVALID_REQUEST" -> "PAYLOAD_VALIDATION_FAILED: REQUIRED field missing";
            case "INVALID_CONFIG" -> "MAPPING_VALIDATION_FAILED: invalid mapping rule";
            case "DUPLICATE_REQUEST" -> "BUSINESS: DUPLICATE request (ALREADY processed)";
            case "RATE_LIMITED" -> "RATE limit exceeded (WINDOW per minute)";
            case "CIRCUIT_OPEN" -> "CIRCUIT is open due to high failure rate";
            case "BULKHEAD_FULL" -> "POLICY: BULKHEAD full";
            default -> "Unexpected error (" + random.nextInt(1000) + ")";
        };
    }

    private static String pickFailureCategory(Random random) {
        int r = random.nextInt(100);
        if (r < 30) {
            return "NETWORK";
        }
        if (r < 40) {
            return "AUTH";
        }
        if (r < 58) {
            return "DATA";
        }
        if (r < 70) {
            return "BUSINESS";
        }
        if (r < 85) {
            return "POLICY";
        }
        return "SYSTEM";
    }

    private static String requestPayloadFor(InterfaceSpec spec, String triggerType, Random random) {
        String policyNo = "P" + LocalDateTime.now().toLocalDate().toString().replace("-", "") + String.format("%06d", random.nextInt(999999));
        String rrn = "******-*******";
        String cardNo = "************" + String.format("%04d", 1000 + random.nextInt(9000));
        return "{\"idempotencyKey\":\"" + triggerType + "-" + spec.interfaceCode + "-" + random.nextInt(999999) + "\",\"payload\":{\"policyNo\":\""
            + policyNo + "\",\"eventType\":\"" + (random.nextBoolean() ? "NEW_CONTRACT" : "CLAIM") + "\",\"rrn\":\"" + rrn + "\",\"cardNo\":\"" + cardNo + "\"}}";
    }

    private static String responsePayloadFor(InterfaceSpec spec, Random random) {
        return "{\"result\":\"OK\",\"interfaceCode\":\"" + spec.interfaceCode + "\",\"traceId\":\"" + UUID.randomUUID() + "\",\"items\":" + (1 + random.nextInt(5)) + "}";
    }

    private static String idempotencyKeyFor(String interfaceCode, LocalDateTime startedAt, int seq) {
        String day = startedAt.toLocalDate().toString().replace("-", "");
        return interfaceCode + "-" + day + "-" + String.format("%06d", seq);
    }

    private static String pickRetryStatus(Random random) {
        int r = random.nextInt(100);
        if (r < 40) {
            return "PENDING";
        }
        if (r < 70) {
            return "APPROVED";
        }
        if (r < 85) {
            return "REJECTED";
        }
        if (r < 95) {
            return "EXECUTED";
        }
        return "FAILED";
    }

    private static List<InterfaceSpec> interfaceSpecs() {
        List<InterfaceSpec> specs = new ArrayList<>();

        specs.add(new InterfaceSpec("FSS_POLICY_REPORT", "금감원 보험계약 보고", "REST", "PolicyCore", "REGULATORY", "FSS", "OUTBOUND", 3000L, 3000L, "ACTIVE", 3, true, true));
        specs.add(new InterfaceSpec("HIRA_CLAIM_CHECK", "심평원 청구 사전점검", "REST", "ClaimCore", "CLAIM", "HIRA", "OUTBOUND", 2500L, 2500L, "ACTIVE", 3, true, true));
        specs.add(new InterfaceSpec("KIDI_RATE_TABLE", "보험개발원 요율 조회", "REST", "Pricing", "PRICING", "KIDI", "OUTBOUND", 2000L, 2000L, "ACTIVE", 2, true, false));
        specs.add(new InterfaceSpec("CRM_CUSTOMER_SYNC", "CRM 고객 동기화", "REST", "CRM", "CUSTOMER", "CRM", "OUTBOUND", 1800L, 1800L, "ACTIVE", 2, true, true));
        specs.add(new InterfaceSpec("PAYMENT_REFUND", "결제 환불 요청", "REST", "Payment", "PAYMENT", "PG", "OUTBOUND", 2200L, 2200L, "ACTIVE", 2, true, false));
        specs.add(new InterfaceSpec("SMS_SEND", "문자 발송", "REST", "Notification", "NOTIFICATION", "SMS", "OUTBOUND", 1200L, 1200L, "ACTIVE", 2, true, true));

        specs.add(new InterfaceSpec("LEGACY_POLICY_QUERY", "레거시 보험계약 조회", "SOAP", "LegacyBridge", "LEGACY", "LEGACY", "OUTBOUND", 3500L, 3500L, "ACTIVE", 2, true, false));
        specs.add(new InterfaceSpec("LEGACY_CLAIM_SUBMIT", "레거시 청구 접수", "SOAP", "LegacyBridge", "LEGACY", "LEGACY", "OUTBOUND", 4000L, 4000L, "ACTIVE", 2, true, false));

        specs.add(new InterfaceSpec("KAFKA_CONTRACT_EVENT_IN", "계약 이벤트 수신", "MQ", "EventHub", "EVENT", "PARTNER_A", "INBOUND", 1500L, 1500L, "ACTIVE", 2, false, true));
        specs.add(new InterfaceSpec("KAFKA_CLAIM_EVENT_IN", "청구 이벤트 수신", "MQ", "EventHub", "EVENT", "PARTNER_B", "INBOUND", 1500L, 1500L, "ACTIVE", 2, false, true));
        specs.add(new InterfaceSpec("SETTLEMENT_EVENT_OUT", "정산 이벤트 발행", "MQ", "Settlement", "PAYMENT", "PARTNER_A", "OUTBOUND", 1500L, 1500L, "ACTIVE", 2, false, false));

        specs.add(new InterfaceSpec("BATCH_DAILY_RECONCILE", "일 정산 배치", "BATCH", "Settlement", "PAYMENT", "INTERNAL", "OUTBOUND", 5000L, 5000L, "ACTIVE", 2, false, true));
        specs.add(new InterfaceSpec("BATCH_MONTHLY_STATEMENT", "월 명세서 배치", "BATCH", "Finance", "FINANCE", "INTERNAL", "OUTBOUND", 8000L, 8000L, "ACTIVE", 2, false, false));

        specs.add(new InterfaceSpec("SFTP_PARTNER_UPLOAD", "파트너 파일 업로드", "SFTP", "PartnerOps", "FILE", "PARTNER_A", "OUTBOUND", 6000L, 6000L, "ACTIVE", 2, false, false));
        specs.add(new InterfaceSpec("SFTP_PARTNER_DOWNLOAD", "파트너 파일 다운로드", "SFTP", "PartnerOps", "FILE", "PARTNER_B", "INBOUND", 6000L, 6000L, "ACTIVE", 2, false, false));

        return specs;
    }

    private static final class InterfaceSpec {
        private final String interfaceCode;
        private final String name;
        private final String protocolType;
        private final String ownerTeam;
        private final String businessCategory;
        private final String externalOrg;
        private final String callDirection;
        private final Long slaMillis;
        private final Long timeoutMillis;
        private final String status;
        private final int configVersions;
        private final boolean supportsSandbox;
        private final boolean scheduledCandidate;

        private InterfaceSpec(
            String interfaceCode,
            String name,
            String protocolType,
            String ownerTeam,
            String businessCategory,
            String externalOrg,
            String callDirection,
            Long slaMillis,
            Long timeoutMillis,
            String status,
            int configVersions,
            boolean supportsSandbox,
            boolean scheduledCandidate
        ) {
            this.interfaceCode = interfaceCode;
            this.name = name;
            this.protocolType = protocolType;
            this.ownerTeam = ownerTeam;
            this.businessCategory = businessCategory;
            this.externalOrg = externalOrg;
            this.callDirection = callDirection;
            this.slaMillis = slaMillis;
            this.timeoutMillis = timeoutMillis;
            this.status = status;
            this.configVersions = configVersions;
            this.supportsSandbox = supportsSandbox;
            this.scheduledCandidate = scheduledCandidate;
        }
    }

    private static final class FailedExecution {
        private final String interfaceCode;
        private final String originalExecutionId;

        private FailedExecution(String interfaceCode, String originalExecutionId) {
            this.interfaceCode = interfaceCode;
            this.originalExecutionId = originalExecutionId;
        }
    }

    private static final class SeedResult {
        private final int executionCount;
        private final List<FailedExecution> failedOrTimeoutExecutions;
        private final List<String> sampleExecutionIds;
        private final int retryCandidatesCount;

        private SeedResult(
            int executionCount,
            List<FailedExecution> failedOrTimeoutExecutions,
            List<String> sampleExecutionIds,
            int retryCandidatesCount
        ) {
            this.executionCount = executionCount;
            this.failedOrTimeoutExecutions = failedOrTimeoutExecutions;
            this.sampleExecutionIds = sampleExecutionIds;
            this.retryCandidatesCount = retryCandidatesCount;
        }
    }

    private static final class SeedDlqResult {
        private final int dlqCount;
        private final List<Long> dlqMessageIds;
        private final List<Long> sampleDlqIds;

        private SeedDlqResult(int dlqCount, List<Long> dlqMessageIds, List<Long> sampleDlqIds) {
            this.dlqCount = dlqCount;
            this.dlqMessageIds = dlqMessageIds;
            this.sampleDlqIds = sampleDlqIds;
        }
    }
}
