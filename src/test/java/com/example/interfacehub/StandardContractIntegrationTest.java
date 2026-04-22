package com.example.interfacehub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StandardContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_manage_standard_contract_apis() throws Exception {
        mockMvc.perform(get("/api/v1/standards/error-codes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").exists());

        mockMvc.perform(put("/api/v1/standards/reprocess-policies/IF-NET-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "AUTO_RETRY",
                      "autoMaxAttempts": 5,
                      "backoffSeconds": 30,
                      "approvalLevel": "NONE",
                      "enabled": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorCode").value("IF-NET-001"))
            .andExpect(jsonPath("$.autoMaxAttempts").value(5));
    }

    @Test
    void should_suppress_execution_during_maintenance_window() throws Exception {
        DayOfWeek today = LocalDateTime.now().getDayOfWeek();
        LocalTime start = LocalTime.now().minusMinutes(1).withNano(0);
        LocalTime end = LocalTime.now().plusMinutes(1).withNano(0);

        mockMvc.perform(post("/api/v1/standards/maintenance-windows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalOrg": "FSS",
                      "dayOfWeek": "%s",
                      "startTime": "%s",
                      "endTime": "%s",
                      "suppressLevel": "WARN",
                      "reason": "scheduled maintenance"
                    }
                    """.formatted(today.name(), start, end)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.externalOrg").value("FSS"));

        mockMvc.perform(post("/api/v1/interfaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "interfaceCode": "FSS_MAINT_IF",
                      "name": "FSS maintenance test",
                      "protocolType": "REST",
                      "ownerTeam": "PolicyCore",
                      "externalOrg": "FSS",
                      "slaMillis": 3000
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/interfaces/FSS_MAINT_IF/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "endpoint": "http://localhost:65535/not-used",
                      "authType": "NONE",
                      "headers": {},
                      "timeoutMillis": 500
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/interfaces/FSS_MAINT_IF/configs/1/publish"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/interfaces/FSS_MAINT_IF/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "MAINT-0001",
                      "payload": { "policyNo": "P202604229999" }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorCode").value("EXT_MAINTENANCE"));
    }
}
