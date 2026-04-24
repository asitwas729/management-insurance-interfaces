package com.example.interfacehub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "interfacehub.security.open-endpoints-for-test=false",
    "management.endpoints.web.exposure.include=health,prometheus,metrics"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ManualValidationScenariosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @SpyBean
    private ExecutionHistoryRepository executionHistoryRepository;

    @Test
    void run_manual_validation_scenarios() throws Exception {
        JsonNode adminLogin = login("admin", "servicehotkey", 200);
        String adminAccessToken = adminLogin.get("accessToken").asText();
        String oldRefreshToken = adminLogin.get("refreshToken").asText();

        // 1) Rate limiting: 6 rapid calls, ensure 6th is 429
        List<Integer> rateStatuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "operator1",
                          "password": "operator123"
                        }
                        """))
                .andReturn();
            rateStatuses.add(result.getResponse().getStatus());
        }
        assertThat(rateStatuses.get(5)).isEqualTo(429);

        // 2) Refresh token rotation: first refresh OK, old token retry -> 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "refreshToken": "%s"
                    }
                    """.formatted(oldRefreshToken)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "refreshToken": "%s"
                    }
                    """.formatted(oldRefreshToken)))
            .andExpect(status().isUnauthorized());

        // 3) Invalid cron -> 400
        mockMvc.perform(post("/api/v1/schedules")
                .header("Authorization", "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "interfaceCode": "IF-CRON-INVALID",
                      "cronExpression": "INVALID",
                      "payloadTemplate": "{}"
                    }
                    """))
            .andExpect(status().isBadRequest());

        // 4) Multiple missing fields -> fieldErrors has multiple entries
        MvcResult validationResult = mockMvc.perform(post("/api/v1/schedules")
                .header("Authorization", "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andReturn();
        JsonNode validationBody = objectMapper.readTree(validationResult.getResponse().getContentAsString());
        JsonNode fieldErrors = validationBody.get("fieldErrors");
        assertThat(fieldErrors).isNotNull();
        assertThat(fieldErrors.isArray()).isTrue();
        assertThat(fieldErrors.size()).isGreaterThanOrEqualTo(2);

        // 5) Swagger UI reachable
        MvcResult swaggerResult = mockMvc.perform(get("/swagger-ui.html"))
            .andReturn();
        int swaggerStatus = swaggerResult.getResponse().getStatus();
        assertThat(swaggerStatus == 200 || swaggerStatus == 302 || swaggerStatus == 301).isTrue();

        // 6) Incident cache: same params twice, repository should be hit once
        clearInvocations(executionHistoryRepository);
        mockMvc.perform(get("/api/v1/incidents/summary")
                .header("Authorization", "Bearer " + adminAccessToken)
                .param("hoursBack", "24")
                .param("limit", "10"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/incidents/summary")
                .header("Authorization", "Bearer " + adminAccessToken)
                .param("hoursBack", "24")
                .param("limit", "10"))
            .andExpect(status().isOk());
        verify(executionHistoryRepository, times(1))
            .findRecentByStatus(any(ExecutionStatus.class), any(LocalDateTime.class), any());

        // 7) Prometheus: endpoint result + cache get metric existence
        MvcResult prometheus = mockMvc.perform(get("/actuator/prometheus")).andReturn();
        int prometheusStatus = prometheus.getResponse().getStatus();
        assertThat(prometheusStatus == 200 || prometheusStatus == 404).isTrue();
        assertThat(meterRegistry.getMeters().stream().anyMatch(m -> "cache.gets".equals(m.getId().getName()))).isTrue();
    }

    private JsonNode login(String username, String password, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "%s"
                    }
                    """.formatted(username, password)))
            .andExpect(status().is(expectedStatus))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
