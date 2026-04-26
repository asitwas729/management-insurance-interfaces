package com.example.interfacehub;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.interfacehub.application.standardmessage.StandardMessageAdminService;
import com.example.interfacehub.application.standardmessage.StandardMessageValidationService;
import com.example.interfacehub.domain.standardmessage.RuleOperator;
import com.example.interfacehub.domain.standardmessage.RuleSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StandardMessageValidationServiceTest {

    @Autowired
    private StandardMessageAdminService adminService;

    @Autowired
    private StandardMessageValidationService validationService;

    @Test
    void rule_engine_can_enforce_required_field_even_when_xsd_allows_missing() {
        String xsd = """
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                targetNamespace="http://interfacehub.example.com/soap"
                xmlns="http://interfacehub.example.com/soap"
                elementFormDefault="qualified">
              <xsd:element name="ExecuteRequest">
                <xsd:complexType>
                  <xsd:sequence>
                    <xsd:element name="customerId" type="xsd:string" minOccurs="0"/>
                  </xsd:sequence>
                </xsd:complexType>
              </xsd:element>
            </xsd:schema>
            """;
        adminService.upsertSchema("SOAP_EXEC_RULE", 1, xsd, true);
        adminService.upsertRule(
            "SOAP_EXEC_RULE",
            1,
            "CUSTOMER_ID_REQUIRED",
            RuleSeverity.ERROR,
            "//ns:customerId",
            RuleOperator.REQUIRED,
            null,
            "customerId is required",
            true
        );

        String xml = """
            <ifh:ExecuteRequest xmlns:ifh="http://interfacehub.example.com/soap"></ifh:ExecuteRequest>
            """;
        var result = validationService.validate("SOAP_EXEC_RULE", 1, xml, true);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().stream().anyMatch(v -> "CUSTOMER_ID_REQUIRED".equals(v.ruleId()))).isTrue();
    }
}

