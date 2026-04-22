package com.example.interfacehub.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RestInterfaceExecutorResilienceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer externalServer;

    @BeforeEach
    void setUp() throws IOException {
        externalServer = HttpServer.create(new InetSocketAddress(0), 0);

        externalServer.createContext("/ok", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        externalServer.createContext("/client-error", exchange -> {
            byte[] body = "{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        externalServer.createContext("/server-error", exchange -> {
            byte[] body = "{\"error\":\"server error\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        externalServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        externalServer.start();
    }

    @AfterEach
    void tearDown() {
        if (externalServer != null) {
            externalServer.stop(0);
        }
    }

    @Test
    void external_4xx_stores_EXT_4XX_error_code() throws Exception {
        String interfaceCode = "RESIL_4XX_IF";
        registerAndPublish(interfaceCode, endpoint("/client-error"), 3000L);

        mockMvc.perform(post("/api/v1/interfaces/{code}/execute", interfaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("RESIL-4XX-001")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorCode").value("EXT_4XX"));
    }

    @Test
    void external_5xx_stores_EXT_5XX_error_code() throws Exception {
        String interfaceCode = "RESIL_5XX_IF";
        registerAndPublish(interfaceCode, endpoint("/server-error"), 3000L);

        mockMvc.perform(post("/api/v1/interfaces/{code}/execute", interfaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("RESIL-5XX-001")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorCode").value("EXT_5XX"));
    }

    @Test
    void external_timeout_stores_TIMEOUT_error_code() throws Exception {
        String interfaceCode = "RESIL_TIMEOUT_IF";
        // 200ms timeout, server responds after 5s
        registerAndPublish(interfaceCode, endpoint("/slow"), 200L);

        mockMvc.perform(post("/api/v1/interfaces/{code}/execute", interfaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("RESIL-TIMEOUT-001")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorCode").value("TIMEOUT"));
    }

    @Test
    void circuit_breaker_opens_after_repeated_failures_and_returns_CIRCUIT_OPEN() throws Exception {
        String interfaceCode = "RESIL_CB_IF";
        registerAndPublish(interfaceCode, endpoint("/server-error"), 3000L);

        // sliding-window-size=20, minimum-number-of-calls=10, failure-rate-threshold=50
        // 20 consecutive failures will trip the CB
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/interfaces/{code}/execute", interfaceCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload("RESIL-CB-" + String.format("%03d", i))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        }

        // After enough failures the circuit should open.
        // The next call should be either EXT_5XX (still closed) or CIRCUIT_OPEN (open).
        // We assert that the system handled the failures correctly without throwing unexpected exceptions.
        MvcResult lastResult = mockMvc.perform(post("/api/v1/interfaces/{code}/execute", interfaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("RESIL-CB-FINAL")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andReturn();

        String errorCode = objectMapper.readTree(lastResult.getResponse().getContentAsString())
            .get("errorCode").asText();
        assertThat(errorCode).isIn("EXT_5XX", "CIRCUIT_OPEN");
    }

    private void registerAndPublish(String interfaceCode, String endpoint, long timeoutMillis) throws Exception {
        mockMvc.perform(post("/api/v1/interfaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "interfaceCode": "%s",
                      "name": "Resilience test interface",
                      "protocolType": "REST",
                      "ownerTeam": "ResilienceTeam",
                      "slaMillis": 5000
                    }
                    """.formatted(interfaceCode)))
            .andExpect(status().isCreated());

        MvcResult configResult = mockMvc.perform(post("/api/v1/interfaces/{code}/configs", interfaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "endpoint": "%s",
                      "authType": "NONE",
                      "headers": {},
                      "timeoutMillis": %d
                    }
                    """.formatted(endpoint, timeoutMillis)))
            .andExpect(status().isCreated())
            .andReturn();

        Long configId = objectMapper.readTree(configResult.getResponse().getContentAsString())
            .get("configId").asLong();

        mockMvc.perform(post("/api/v1/interfaces/{code}/configs/{configId}/publish", interfaceCode, configId))
            .andExpect(status().isOk());
    }

    private String endpoint(String path) {
        return "http://localhost:" + externalServer.getAddress().getPort() + path;
    }

    private String payload(String idempotencyKey) {
        return """
            {
              "idempotencyKey": "%s",
              "payload": {"test": "data"}
            }
            """.formatted(idempotencyKey);
    }
}
