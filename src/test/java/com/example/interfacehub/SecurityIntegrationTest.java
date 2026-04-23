package com.example.interfacehub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "interfacehub.security.open-endpoints-for-test=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void operator_cannot_publish_config() throws Exception {
        String operatorToken = loginAndGetToken("operator1", "operator123");

        mockMvc.perform(post("/api/v1/interfaces/SECURITY_IF/configs/1/publish")
                .header("Authorization", "Bearer " + operatorToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_publish_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/interfaces/SECURITY_IF/configs/1/publish"))
            .andExpect(status().isUnauthorized());
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
