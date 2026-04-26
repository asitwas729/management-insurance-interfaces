package com.example.interfacehub.application.standardmessage;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.standardmessage.RuleOperator;
import com.example.interfacehub.domain.standardmessage.RuleSeverity;
import com.example.interfacehub.domain.standardmessage.StandardMessageRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

@Service
public class StandardMessageValidationService {

    private final StandardMessageCatalogService catalogService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Schema> compiledSchemaCache = new ConcurrentHashMap<>();

    public StandardMessageValidationService(
        StandardMessageCatalogService catalogService,
        ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    public record StandardSchemaRef(
        String schemaCode,
        int version,
        boolean enforceXsd,
        boolean enforceRules
    ) {
        public boolean enabled() {
            return schemaCode != null && !schemaCode.isBlank() && version > 0 && (enforceXsd || enforceRules);
        }
    }

    public StandardSchemaRef resolveFromProtocolConfigJson(String protocolConfigJson) {
        if (protocolConfigJson == null || protocolConfigJson.isBlank()) {
            return new StandardSchemaRef(null, 0, false, false);
        }
        try {
            JsonNode root = objectMapper.readTree(protocolConfigJson);

            JsonNode standard = root.path("standardMessage");
            if (!standard.isMissingNode() && standard.isObject()) {
                String code = standard.path("schemaCode").asText(null);
                int version = standard.path("schemaVersion").asInt(0);
                boolean enforceXsd = standard.path("enforceXsd").asBoolean(false) || standard.path("enforce").asBoolean(false);
                boolean enforceRules = standard.path("enforceRules").asBoolean(false) || standard.path("enforce").asBoolean(false);
                return new StandardSchemaRef(code, version, enforceXsd, enforceRules);
            }

            String code = root.path("standardSchemaCode").asText(null);
            int version = root.path("standardSchemaVersion").asInt(0);
            boolean enforced = root.path("standardSchemaEnforced").asBoolean(false);
            boolean enforceXsd = root.path("standardSchemaEnforceXsd").asBoolean(enforced);
            boolean enforceRules = root.path("standardSchemaEnforceRules").asBoolean(enforced);
            return new StandardSchemaRef(code, version, enforceXsd, enforceRules);
        } catch (Exception ignored) {
            return new StandardSchemaRef(null, 0, false, false);
        }
    }

    public StandardValidationResult validate(String schemaCode, int version, String xml, boolean applyRules) {
        if (schemaCode == null || schemaCode.isBlank() || version <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "schemaCode/version is required");
        }
        if (xml == null || xml.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "xml is required");
        }

        var schema = catalogService.findEnabledSchema(schemaCode, version);
        if (schema == null) {
            throw new BusinessException(ErrorCode.IF_NOT_FOUND, "Standard schema not found: " + schemaCode + " v" + version);
        }

        List<StandardValidationResult.Violation> errors = new ArrayList<>();
        List<StandardValidationResult.Violation> warnings = new ArrayList<>();

        validateByXsd(schemaCode, version, schema.getXsdText(), xml, errors);

        if (applyRules) {
            List<StandardMessageRule> rules = catalogService.findEnabledRules(schemaCode, version);
            applyRules(xml, rules, errors, warnings);
        }

        return new StandardValidationResult(errors.isEmpty(), errors, warnings);
    }

    public void invalidateSchemaCache(String schemaCode, int version) {
        String prefix = schemaCode + ":" + version + ":";
        compiledSchemaCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void validateByXsd(
        String schemaCode,
        int version,
        String xsdText,
        String xml,
        List<StandardValidationResult.Violation> errors
    ) {
        try {
            Schema schema = compiledSchemaCache.computeIfAbsent(
                schemaCode + ":" + version + ":" + sha256(xsdText),
                key -> compileSchema(xsdText)
            );

            Validator validator = schema.newValidator();
            List<String> messages = new ArrayList<>();
            validator.setErrorHandler(new CollectingErrorHandler(messages));
            validator.validate(new StreamSource(new StringReader(xml)));
            for (String msg : messages) {
                errors.add(new StandardValidationResult.Violation("XSD", msg));
            }
        } catch (SAXException exception) {
            errors.add(new StandardValidationResult.Violation("XSD", "XSD validation failed: " + exception.getMessage()));
        } catch (Exception exception) {
            errors.add(new StandardValidationResult.Violation("XSD", "XSD validator error: " + exception.getMessage()));
        }
    }

    private Schema compileSchema(String xsdText) {
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return schemaFactory.newSchema(new StreamSource(new StringReader(xsdText)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid XSD: " + exception.getMessage());
        }
    }

    private void applyRules(
        String xml,
        List<StandardMessageRule> rules,
        List<StandardValidationResult.Violation> errors,
        List<StandardValidationResult.Violation> warnings
    ) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Document document = parseXml(xml);
        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new RootNamespaceContext(document));

        for (StandardMessageRule rule : rules) {
            RuleSeverity severity = rule.getSeverity() == null ? RuleSeverity.ERROR : rule.getSeverity();
            String ruleId = Objects.toString(rule.getRuleId(), "RULE");
            String message = rule.getMessage();
            if (message == null || message.isBlank()) {
                message = severity + " " + ruleId + " violated";
            }

            boolean ok = evaluateRule(xpath, document, rule);
            if (ok) {
                continue;
            }
            var violation = new StandardValidationResult.Violation(ruleId, message);
            if (severity == RuleSeverity.WARN) {
                warnings.add(violation);
            } else {
                errors.add(violation);
            }
        }
    }

    private boolean evaluateRule(javax.xml.xpath.XPath xpath, Document document, StandardMessageRule rule) {
        try {
            RuleOperator op = rule.getOperator();
            String expr = rule.getXpathExpr();
            String expected = rule.getExpectedValue();

            var nodes = (org.w3c.dom.NodeList) xpath.evaluate(expr, document, XPathConstants.NODESET);
            String value = null;
            if (nodes != null && nodes.getLength() > 0) {
                value = nodes.item(0).getTextContent();
            }
            String trimmed = value == null ? null : value.trim();

            if (op == RuleOperator.REQUIRED) {
                return trimmed != null && !trimmed.isBlank();
            }
            if (op == RuleOperator.FORBIDDEN) {
                return trimmed == null || trimmed.isBlank();
            }
            if (op == RuleOperator.EQUALS) {
                return Objects.equals(trimmed, expected);
            }
            if (op == RuleOperator.REGEX) {
                return trimmed != null && expected != null && trimmed.matches(expected);
            }
            if (op == RuleOperator.MAX_LEN) {
                int max = parseInt(expected, Integer.MAX_VALUE);
                return trimmed != null && trimmed.length() <= max;
            }
            if (op == RuleOperator.MIN_LEN) {
                int min = parseInt(expected, 0);
                return trimmed != null && trimmed.length() >= min;
            }
            if (op == RuleOperator.IN) {
                if (trimmed == null || expected == null) {
                    return false;
                }
                return Arrays.stream(expected.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .anyMatch(s -> s.equals(trimmed));
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid XML: " + exception.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception exception) {
            return String.valueOf(input == null ? 0 : input.hashCode());
        }
    }

    private static class CollectingErrorHandler implements ErrorHandler {
        private final List<String> messages;

        private CollectingErrorHandler(List<String> messages) {
            this.messages = messages;
        }

        @Override
        public void warning(SAXParseException exception) {
            messages.add(format(exception));
        }

        @Override
        public void error(SAXParseException exception) {
            messages.add(format(exception));
        }

        @Override
        public void fatalError(SAXParseException exception) {
            messages.add(format(exception));
        }

        private String format(SAXParseException e) {
            String loc = (e.getLineNumber() > 0 ? ("line " + e.getLineNumber() + ", col " + e.getColumnNumber() + ": ") : "");
            return loc + e.getMessage();
        }
    }

    private static class RootNamespaceContext implements NamespaceContext {
        private final String rootNamespace;

        private RootNamespaceContext(Document document) {
            String ns = document == null || document.getDocumentElement() == null
                ? null
                : document.getDocumentElement().getNamespaceURI();
            this.rootNamespace = ns == null ? "" : ns;
        }

        @Override
        public String getNamespaceURI(String prefix) {
            if (prefix == null) {
                return XMLConstants.NULL_NS_URI;
            }
            if ("xml".equals(prefix)) {
                return XMLConstants.XML_NS_URI;
            }
            if ("xmlns".equals(prefix)) {
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            }
            if ("ns".equals(prefix) || "ifh".equals(prefix)) {
                return rootNamespace;
            }
            return XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            if (namespaceURI == null) {
                return null;
            }
            if (namespaceURI.equals(rootNamespace)) {
                return "ns";
            }
            return null;
        }

        @Override
        public java.util.Iterator<String> getPrefixes(String namespaceURI) {
            String prefix = getPrefix(namespaceURI);
            if (prefix == null) {
                return java.util.List.<String>of().iterator();
            }
            return java.util.List.of(prefix).iterator();
        }
    }
}
