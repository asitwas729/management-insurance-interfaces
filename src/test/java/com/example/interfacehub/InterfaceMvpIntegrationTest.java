package com.example.interfacehub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InterfaceMvpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer externalServer;
    private String externalEndpoint;

    @BeforeEach
    void setUp() throws IOException {
        externalServer = HttpServer.create(new InetSocketAddress(0), 0);
        externalServer.createContext("/report", exchange -> {
            byte[] body = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        externalServer.createContext("/fail", exchange -> {
            byte[] body = "{\"error\":\"temporary\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        externalServer.start();
        externalEndpoint = "http://localhost:" + externalServer.getAddress().getPort() + "/report";
    }

    @AfterEach
    void tearDown() {
        if (externalServer != null) {
            externalServer.stop(0);
        }
    }

    @Test
    void full_mvp_flow_register_config_publish_execute_and_query_history() throws Exception {
        registerInterface("FSS_POLICY_REPORT")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.interfaceCode").value("FSS_POLICY_REPORT"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        MvcResult configResult = mockMvc.perform(post("/api/v1/interfaces/FSS_POLICY_REPORT/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "endpoint": "%s",
                      "authType": "NONE",
                      "headers": {},
                      "timeoutMillis": 3000
                    }
                    """.formatted(externalEndpoint)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.published").value(false))
            .andReturn();

        Long configId = objectMapper.readTree(configResult.getResponse().getContentAsString()).get("configId").asLong();

        mockMvc.perform(post("/api/v1/interfaces/FSS_POLICY_REPORT/configs/{configId}/publish", configId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(post("/api/v1/interfaces/FSS_POLICY_REPORT/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "FSS-POLICY-0001",
                      "payload": {
                        "policyNo": "P202604220001",
                        "eventType": "NEW_CONTRACT"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        MvcResult historiesResult = mockMvc.perform(get("/api/v1/interfaces/FSS_POLICY_REPORT/histories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
            .andReturn();

        JsonNode histories = objectMapper.readTree(historiesResult.getResponse().getContentAsString());
        assertThat(histories.get("content")).hasSize(1);
    }

    @Test
    void create_interface_rejects_duplicate_interface_code() throws Exception {
        registerInterface("DUPLICATE_IF").andExpect(status().isCreated());

        registerInterface("DUPLICATE_IF")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_INTERFACE_CODE"));
    }

    @Test
    void execute_fails_when_published_config_does_not_exist() throws Exception {
        registerInterface("NO_CONFIG_IF").andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/interfaces/NO_CONFIG_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "NO-CONFIG-0001",
                      "payload": {
                        "policyNo": "P202604220002"
                      }
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CONFIG_NOT_FOUND"));
    }

    @Test
    void execute_rejects_duplicate_idempotency_key() throws Exception {
        registerInterface("IDEMPOTENCY_IF").andExpect(status().isCreated());

        MvcResult configResult = mockMvc.perform(post("/api/v1/interfaces/IDEMPOTENCY_IF/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "endpoint": "%s",
                      "authType": "NONE",
                      "headers": {},
                      "timeoutMillis": 3000
                    }
                    """.formatted(externalEndpoint)))
            .andExpect(status().isCreated())
            .andReturn();

        Long configId = objectMapper.readTree(configResult.getResponse().getContentAsString()).get("configId").asLong();
        mockMvc.perform(post("/api/v1/interfaces/IDEMPOTENCY_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        String requestBody = """
            {
              "idempotencyKey": "IDEMP-0001",
              "payload": {
                "policyNo": "P202604220010",
                "eventType": "NEW_CONTRACT"
              }
            }
            """;

        mockMvc.perform(post("/api/v1/interfaces/IDEMPOTENCY_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(post("/api/v1/interfaces/IDEMPOTENCY_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
    }

    @Test
    void retry_flow_request_approve_execute_and_mark_executed() throws Exception {
        String approverToken = loginAndGetToken("admin", "servicehotkey");
        registerInterface("RETRY_IF").andExpect(status().isCreated());
        String failEndpoint = "http://localhost:" + externalServer.getAddress().getPort() + "/fail";

        Long failConfigId = createConfigAndGetId("RETRY_IF", failEndpoint);
        mockMvc.perform(post("/api/v1/interfaces/RETRY_IF/configs/{configId}/publish", failConfigId))
            .andExpect(status().isOk());

        MvcResult failedExecution = mockMvc.perform(post("/api/v1/interfaces/RETRY_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "RETRY-ORIGINAL-0001",
                      "payload": {
                        "policyNo": "P202604220020",
                        "eventType": "FAIL_CASE"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andReturn();

        String originalExecutionId = objectMapper.readTree(failedExecution.getResponse().getContentAsString())
            .get("executionId")
            .asText();

        Long successConfigId = createConfigAndGetId("RETRY_IF", externalEndpoint);
        mockMvc.perform(post("/api/v1/interfaces/RETRY_IF/configs/{configId}/publish", successConfigId))
            .andExpect(status().isOk());

        MvcResult retryTaskResult = mockMvc.perform(post("/api/v1/interfaces/RETRY_IF/retries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "originalExecutionId": "%s",
                      "requester": "operator1",
                      "reasonCode": "IF-EXT-001",
                      "reasonDetail": "external 5xx retry"
                    }
                    """.formatted(originalExecutionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();

        Long retryTaskId = objectMapper.readTree(retryTaskResult.getResponse().getContentAsString())
            .get("id")
            .asLong();

        mockMvc.perform(post("/api/v1/retries/{retryTaskId}/approve", retryTaskId)
                .header("Authorization", "Bearer " + approverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approver": "manager1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/retries/{retryTaskId}/execute", retryTaskId)
                .header("Authorization", "Bearer " + approverToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/retries/{retryTaskId}", retryTaskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EXECUTED"));
    }

    @Test
    void retry_flow_reject_blocks_retry_execution() throws Exception {
        String approverToken = loginAndGetToken("admin", "servicehotkey");
        registerInterface("RETRY_REJECT_IF").andExpect(status().isCreated());
        String failEndpoint = "http://localhost:" + externalServer.getAddress().getPort() + "/fail";

        Long failConfigId = createConfigAndGetId("RETRY_REJECT_IF", failEndpoint);
        mockMvc.perform(post("/api/v1/interfaces/RETRY_REJECT_IF/configs/{configId}/publish", failConfigId))
            .andExpect(status().isOk());

        MvcResult failedExecution = mockMvc.perform(post("/api/v1/interfaces/RETRY_REJECT_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "RETRY-REJECT-ORIGINAL-0001",
                      "payload": {
                        "policyNo": "P202604220030",
                        "eventType": "FAIL_CASE"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andReturn();

        String originalExecutionId = objectMapper.readTree(failedExecution.getResponse().getContentAsString())
            .get("executionId")
            .asText();

        MvcResult retryTaskResult = mockMvc.perform(post("/api/v1/interfaces/RETRY_REJECT_IF/retries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "originalExecutionId": "%s",
                      "requester": "operator2",
                      "reasonCode": "IF-EXT-001",
                      "reasonDetail": "manual validation required"
                    }
                    """.formatted(originalExecutionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();

        Long retryTaskId = objectMapper.readTree(retryTaskResult.getResponse().getContentAsString())
            .get("id")
            .asLong();

        mockMvc.perform(post("/api/v1/retries/{retryTaskId}/reject", retryTaskId)
                .header("Authorization", "Bearer " + approverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approver": "manager2",
                      "reason": "Invalid business date"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectReason").value("Invalid business date"));

        mockMvc.perform(post("/api/v1/retries/{retryTaskId}/execute", retryTaskId)
                .header("Authorization", "Bearer " + approverToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RETRY_NOT_APPROVED"));
    }

    @Test
    void audit_log_search_filters_by_action() throws Exception {
        registerInterface("AUDIT_IF").andExpect(status().isCreated());
        Long configId = createConfigAndGetId("AUDIT_IF", externalEndpoint);

        mockMvc.perform(post("/api/v1/interfaces/AUDIT_IF/configs/{configId}/publish", configId)
                .param("actor", "auditor1"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-logs")
                .param("action", "PUBLISH_CONFIG"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].action").value("PUBLISH_CONFIG"));
    }

    @Test
    void mq_execute_success_with_inmemory_broker() throws Exception {
        registerInterfaceWithProtocol("MQ_SUCCESS_IF", "MQ").andExpect(status().isCreated());
        Long configId = createConfigAndGetId("MQ_SUCCESS_IF", "topic.policy.report");

        mockMvc.perform(post("/api/v1/interfaces/MQ_SUCCESS_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/MQ_SUCCESS_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "MQ-SUCCESS-0001",
                      "payload": {
                        "policyNo": "P202604220100",
                        "eventType": "NEW_CONTRACT",
                        "simulateFailure": false
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void batch_execute_success_with_joblauncher() throws Exception {
        registerInterfaceWithProtocol("BATCH_SUCCESS_IF", "BATCH").andExpect(status().isCreated());
        Long configId = createConfigAndGetId("BATCH_SUCCESS_IF", "interfaceHubPayloadJob");

        mockMvc.perform(post("/api/v1/interfaces/BATCH_SUCCESS_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/BATCH_SUCCESS_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "BATCH-SUCCESS-0001",
                      "payload": {
                        "jobName": "interfaceHubPayloadJob",
                        "itemCount": 12,
                        "requestedBy": "operator-batch",
                        "simulateFailure": false
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void batch_execute_failure_should_return_batch_failed() throws Exception {
        registerInterfaceWithProtocol("BATCH_FAIL_IF", "BATCH").andExpect(status().isCreated());
        Long configId = createConfigAndGetId("BATCH_FAIL_IF", "interfaceHubPayloadJob");

        mockMvc.perform(post("/api/v1/interfaces/BATCH_FAIL_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/BATCH_FAIL_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "BATCH-FAIL-0001",
                      "payload": {
                        "jobName": "interfaceHubPayloadJob",
                        "simulateFailure": true
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorCode").value("BATCH_FAILED"));
    }

    @Test
    void mq_failure_should_store_dlq_and_allow_replay_with_override_payload() throws Exception {
        registerInterfaceWithProtocol("MQ_FAIL_IF", "MQ").andExpect(status().isCreated());
        Long configId = createConfigAndGetId("MQ_FAIL_IF", "topic.policy.report");

        mockMvc.perform(post("/api/v1/interfaces/MQ_FAIL_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/MQ_FAIL_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "MQ-FAIL-0001",
                      "payload": {
                        "policyNo": "P202604220101",
                        "eventType": "FAIL_CASE",
                        "simulateFailure": true
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorCode").value("MQ_CONSUME_FAILED"));

        MvcResult dlqResult = mockMvc.perform(get("/api/v1/dlq")
                .param("interfaceCode", "MQ_FAIL_IF"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].interfaceCode").value("MQ_FAIL_IF"))
            .andExpect(jsonPath("$.content[0].replayCount").value(0))
            .andReturn();

        Long dlqId = objectMapper.readTree(dlqResult.getResponse().getContentAsString())
            .get("content")
            .get(0)
            .get("id")
            .asLong();

        MvcResult replayRequestResult = mockMvc.perform(post("/api/v1/dlq/{dlqId}/replay-requests", dlqId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requester": "operator3",
                      "reasonCode": "IF-MQ-001",
                      "reasonDetail": "consumer outage recovered",
                      "payloadOverride": {
                        "policyNo": "P202604220101",
                        "eventType": "RETRY_OK",
                        "simulateFailure": false
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();

        Long replayRequestId = objectMapper.readTree(replayRequestResult.getResponse().getContentAsString())
            .get("id")
            .asLong();
        String approverToken = loginAndGetToken("admin", "servicehotkey");

        mockMvc.perform(post("/api/v1/dlq/replay-requests/{replayRequestId}/approve", replayRequestId)
                .header("Authorization", "Bearer " + approverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approver": "manager3"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/dlq/replay-requests/{replayRequestId}/execute", replayRequestId)
                .header("Authorization", "Bearer " + approverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "executor": "manager3"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dlqId").value(dlqId))
            .andExpect(jsonPath("$.execution.status").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/dlq")
                .param("interfaceCode", "MQ_FAIL_IF"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].replayCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void dlq_replay_approve_should_require_approver_role() throws Exception {
        registerInterfaceWithProtocol("MQ_ROLE_IF", "MQ").andExpect(status().isCreated());
        Long configId = createConfigAndGetId("MQ_ROLE_IF", "topic.policy.report");
        mockMvc.perform(post("/api/v1/interfaces/MQ_ROLE_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/MQ_ROLE_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "MQ-ROLE-0001",
                      "payload": {
                        "policyNo": "P202604220320",
                        "eventType": "FAIL_CASE",
                        "simulateFailure": true
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"));

        MvcResult dlqResult = mockMvc.perform(get("/api/v1/dlq")
                .param("interfaceCode", "MQ_ROLE_IF"))
            .andExpect(status().isOk())
            .andReturn();
        Long dlqId = objectMapper.readTree(dlqResult.getResponse().getContentAsString())
            .get("content").get(0).get("id").asLong();

        MvcResult replayRequestResult = mockMvc.perform(post("/api/v1/dlq/{dlqId}/replay-requests", dlqId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requester": "operator4",
                      "reasonCode": "IF-MQ-001",
                      "reasonDetail": "role validation test"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        Long replayRequestId = objectMapper.readTree(replayRequestResult.getResponse().getContentAsString())
            .get("id").asLong();
        String nonApproverToken = loginAndGetToken("operator1", "operator123");

        mockMvc.perform(post("/api/v1/dlq/replay-requests/{replayRequestId}/approve", replayRequestId)
                .header("Authorization", "Bearer " + nonApproverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approver": "auditor1"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions registerInterface(String interfaceCode) throws Exception {
        return mockMvc.perform(post("/api/v1/interfaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "interfaceCode": "%s",
                  "name": "금감원 보험계약 보고",
                  "protocolType": "REST",
                  "ownerTeam": "PolicyCore",
                  "slaMillis": 3000
                }
                """.formatted(interfaceCode)));
    }

    private org.springframework.test.web.servlet.ResultActions registerInterfaceWithProtocol(
        String interfaceCode,
        String protocolType
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/interfaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "interfaceCode": "%s",
                  "name": "Policy report interface",
                  "protocolType": "%s",
                  "ownerTeam": "PolicyCore",
                  "slaMillis": 3000
                }
                """.formatted(interfaceCode, protocolType)));
    }

    private Long createConfigAndGetId(String interfaceCode, String endpoint) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/interfaces/{interfaceCode}/configs", interfaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "endpoint": "%s",
                      "authType": "NONE",
                      "headers": {},
                      "timeoutMillis": 3000
                    }
                    """.formatted(endpoint)))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("configId").asLong();
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "%s"
                    }
                    """.formatted(username, password)))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

}
