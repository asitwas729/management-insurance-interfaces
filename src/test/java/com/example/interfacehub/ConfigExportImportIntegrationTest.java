package com.example.interfacehub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interfacehub.domain.interfaceconfig.CallDirection;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.domain.interfaceconfig.RuntimeEnvironment;
import com.example.interfacehub.domain.policy.PolicyAuthType;
import com.example.interfacehub.presentation.admin.ExportPayload;
import com.example.interfacehub.presentation.admin.ExportPayload.ErrorCatalogExport;
import com.example.interfacehub.presentation.admin.ExportPayload.InterfaceConfigVersionExport;
import com.example.interfacehub.presentation.admin.ExportPayload.InterfaceDefinitionExport;
import com.example.interfacehub.presentation.admin.ExportPayload.InterfaceExportEntry;
import com.example.interfacehub.presentation.admin.ExportPayload.PolicyTemplateExport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
class ConfigExportImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void import_then_export_then_reimport_appends_versions_and_counts_created_updated() throws Exception {
        ExportPayload payload = new ExportPayload(
            java.time.LocalDateTime.now(),
            List.of(new InterfaceExportEntry(
                new InterfaceDefinitionExport(
                    "IF_EXPORT_1",
                    "Export Import Test",
                    ProtocolType.REST,
                    "TEAM_A",
                    "GENERAL",
                    "ORG_A",
                    CallDirection.OUTBOUND,
                    1000L
                ),
                List.of(
                    new InterfaceConfigVersionExport(
                        1,
                        "http://example.com/v1",
                        "NONE",
                        "{\"x\":\"y\"}",
                        1000L,
                        RuntimeEnvironment.PROD,
                        "{}",
                        "{\"req\":1}",
                        "{\"res\":1}",
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        true
                    ),
                    new InterfaceConfigVersionExport(
                        2,
                        "http://example.com/v2",
                        "NONE",
                        "{\"x\":\"y\"}",
                        1500L,
                        RuntimeEnvironment.PROD,
                        "{}",
                        "{\"req\":2}",
                        "{\"res\":2}",
                        null,
                        null,
                        null,
                        true,
                        200,
                        "{\"ok\":true}",
                        false
                    )
                )
            )),
            List.of(new ErrorCatalogExport(
                "E_EXPORT_1",
                "TEST",
                "WARN",
                400,
                true,
                "RETRY",
                "Export/Import test error"
            )),
            List.of(new PolicyTemplateExport(
                "POLICY_EXPORT_1",
                PolicyAuthType.NONE,
                2000L,
                1,
                100L,
                60,
                "[]",
                false,
                false,
                "[]",
                true
            ))
        );

        mockMvc.perform(post("/api/v1/admin/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(1))
            .andExpect(jsonPath("$.updated").value(0));

        MvcResult exportResult = mockMvc.perform(get("/api/v1/admin/export"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode exported = objectMapper.readTree(exportResult.getResponse().getContentAsString());
        assertThat(exported.get("interfaces")).isNotNull();
        assertThat(exported.get("interfaces").size()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(post("/api/v1/admin/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(0))
            .andExpect(jsonPath("$.updated").value(1));

        MvcResult exportAfterReimport = mockMvc.perform(get("/api/v1/admin/export"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode exported2 = objectMapper.readTree(exportAfterReimport.getResponse().getContentAsString());
        JsonNode targetInterface = null;
        for (JsonNode entry : exported2.get("interfaces")) {
            if ("IF_EXPORT_1".equals(entry.get("definition").get("interfaceCode").asText())) {
                targetInterface = entry;
                break;
            }
        }
        assertThat(targetInterface).isNotNull();
        assertThat(targetInterface.get("configVersions").size()).isEqualTo(4);
        assertThat(targetInterface.get("configVersions").get(0).get("version").asInt()).isEqualTo(1);
        assertThat(targetInterface.get("configVersions").get(3).get("version").asInt()).isEqualTo(4);
    }
}

