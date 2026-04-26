package com.example.interfacehub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interfacehub.application.standardmessage.StandardMessageAdminService;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
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
class SoapStandardValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StandardMessageAdminService adminService;

    @Test
    void soap_execute_should_fail_fast_when_standard_schema_enforced_and_xsd_invalid() throws Exception {
        String xsd = """
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                targetNamespace="http://interfacehub.example.com/soap"
                xmlns="http://interfacehub.example.com/soap"
                elementFormDefault="qualified">
              <xsd:element name="ExecuteRequest">
                <xsd:complexType>
                  <xsd:sequence>
                    <xsd:element name="customerId" type="xsd:string"/>
                  </xsd:sequence>
                </xsd:complexType>
              </xsd:element>
            </xsd:schema>
            """;
        adminService.upsertSchema("SOAP_EXEC", 1, xsd, true);

        // 1) register SOAP interface
        mockMvc.perform(post("/api/v1/interfaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "interfaceCode": "IF_SOAP_STD_1",
                      "name": "SOAP STD",
                      "protocolType": "%s",
                      "ownerTeam": "TEAM_A",
                      "businessCategory": "GENERAL",
                      "externalOrg": "ORG_A",
                      "callDirection": "OUTBOUND",
                      "slaMillis": 1000
                    }
                    """.formatted(ProtocolType.SOAP.name())))
            .andExpect(status().isCreated());

        // 2) create config with standardMessage enforcement and publish
        Map<String, Object> createConfigBody = new LinkedHashMap<>();
        createConfigBody.put("endpoint", "http://localhost:1/soap");
        createConfigBody.put("authType", "NONE");
        createConfigBody.put("headers", Map.of());
        createConfigBody.put("timeoutMillis", 1000);
        createConfigBody.put("environment", "PROD");
        createConfigBody.put("protocolConfig", Map.of(
            "standardMessage", Map.of(
                "schemaCode", "SOAP_EXEC",
                "schemaVersion", 1,
                "enforce", true
            )
        ));
        createConfigBody.put("requestSample", null);
        createConfigBody.put("responseSample", null);
        createConfigBody.put("mappingRuleText", null);
        createConfigBody.put("fieldDescriptionText", null);
        createConfigBody.put("errorCodeGuideText", null);
        createConfigBody.put("sandboxMode", false);
        createConfigBody.put("mockHttpStatus", null);
        createConfigBody.put("mockResponseBody", null);

        MvcResult configResult = mockMvc.perform(post("/api/v1/interfaces/IF_SOAP_STD_1/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createConfigBody)))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode configJson = objectMapper.readTree(configResult.getResponse().getContentAsString());
        long configId = configJson.get("configId").asLong();

        mockMvc.perform(post("/api/v1/interfaces/IF_SOAP_STD_1/configs/%d/publish".formatted(configId)))
            .andExpect(status().isOk());

        // 3) execute with missing required field => should fail before network call
        Map<String, Object> executeBody = new LinkedHashMap<>();
        executeBody.put("idempotencyKey", "k1");
        executeBody.put("payload", Map.of(
            "operation", "ExecuteRequest",
            "body", Map.of()
        ));

        mockMvc.perform(post("/api/v1/interfaces/IF_SOAP_STD_1/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(executeBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorCode").value("STANDARD_VALIDATION_FAILED"));
    }
}
