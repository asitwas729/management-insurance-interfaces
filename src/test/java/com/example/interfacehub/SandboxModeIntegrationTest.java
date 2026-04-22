package com.example.interfacehub;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.concurrent.atomic.AtomicInteger;
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
class SandboxModeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer externalServer;
    private String externalEndpoint;
    private final AtomicInteger externalCallCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws IOException {
        externalCallCount.set(0);
        externalServer = HttpServer.create(new InetSocketAddress(0), 0);
        externalServer.createContext("/real", exchange -> {
            externalCallCount.incrementAndGet();
            byte[] body = "{\"real\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        externalServer.start();
        externalEndpoint = "http://localhost:" + externalServer.getAddress().getPort() + "/real";
    }

    @AfterEach
    void tearDown() {
        if (externalServer != null) {
            externalServer.stop(0);
        }
    }

    @Test
    void sandbox_mode_returns_mock_response_without_external_call() throws Exception {
        mockMvc.perform(post("/api/v1/interfaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "interfaceCode": "SANDBOX_IF",
                      "name": "Sandbox Test Interface",
                      "protocolType": "REST",
                      "ownerTeam": "QA",
                      "slaMillis": 5000
                    }
                    """))
            .andExpect(status().isCreated());

        MvcResult configResult = mockMvc.perform(post("/api/v1/interfaces/SANDBOX_IF/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "endpoint": "%s",
                      "authType": "NONE",
                      "headers": {},
                      "timeoutMillis": 3000,
                      "sandboxMode": true,
                      "mockHttpStatus": 200,
                      "mockResponseBody": "{\\"sandboxResult\\":\\"ok\\"}"
                    }
                    """.formatted(externalEndpoint)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sandboxMode").value(true))
            .andReturn();

        Long configId = objectMapper.readTree(configResult.getResponse().getContentAsString()).get("configId").asLong();

        mockMvc.perform(post("/api/v1/interfaces/SANDBOX_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/SANDBOX_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "SANDBOX-0001",
                      "payload": {
                        "policyNo": "P202604220999"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(externalCallCount.get()).isZero();
    }

    @Test
    void sandbox_execution_is_recorded_with_sandbox_trigger_type() throws Exception {
        mockMvc.perform(post("/api/v1/interfaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "interfaceCode": "SANDBOX_HISTORY_IF",
                      "name": "Sandbox History Test",
                      "protocolType": "REST",
                      "ownerTeam": "QA",
                      "slaMillis": 5000
                    }
                    """))
            .andExpect(status().isCreated());

        MvcResult configResult = mockMvc.perform(post("/api/v1/interfaces/SANDBOX_HISTORY_IF/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "endpoint": "%s",
                      "authType": "NONE",
                      "headers": {},
                      "timeoutMillis": 3000,
                      "sandboxMode": true,
                      "mockHttpStatus": 200,
                      "mockResponseBody": "{\\"status\\":\\"mocked\\"}"
                    }
                    """.formatted(externalEndpoint)))
            .andExpect(status().isCreated())
            .andReturn();

        Long configId = objectMapper.readTree(configResult.getResponse().getContentAsString()).get("configId").asLong();
        mockMvc.perform(post("/api/v1/interfaces/SANDBOX_HISTORY_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/SANDBOX_HISTORY_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "SANDBOX-HIST-0001",
                      "payload": {
                        "policyNo": "P202604221001"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        MvcResult historiesResult = mockMvc.perform(get("/api/v1/interfaces/SANDBOX_HISTORY_IF/histories"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode histories = objectMapper.readTree(historiesResult.getResponse().getContentAsString());
        assertThat(histories.get("content")).hasSize(1);
        assertThat(histories.get("content").get(0).get("triggerType").asText()).isEqualTo("SANDBOX");
    }

    @Test
    void non_sandbox_mode_makes_real_external_call() throws Exception {
        mockMvc.perform(post("/api/v1/interfaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "interfaceCode": "REAL_IF",
                      "name": "Real Call Interface",
                      "protocolType": "REST",
                      "ownerTeam": "PolicyCore",
                      "slaMillis": 5000
                    }
                    """))
            .andExpect(status().isCreated());

        MvcResult configResult = mockMvc.perform(post("/api/v1/interfaces/REAL_IF/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "endpoint": "%s",
                      "authType": "NONE",
                      "headers": {},
                      "timeoutMillis": 3000,
                      "sandboxMode": false
                    }
                    """.formatted(externalEndpoint)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sandboxMode").value(false))
            .andReturn();

        Long configId = objectMapper.readTree(configResult.getResponse().getContentAsString()).get("configId").asLong();
        mockMvc.perform(post("/api/v1/interfaces/REAL_IF/configs/{configId}/publish", configId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/REAL_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "REAL-0001",
                      "payload": {
                        "policyNo": "P202604222001"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(externalCallCount.get()).isEqualTo(1);
    }
}
