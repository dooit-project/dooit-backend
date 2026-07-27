package com.todolab.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ApiPayloadSanitizer {

    static final String MASK = "[MASKED]";
    static final String TRUNCATED_SUFFIX = "...[TRUNCATED]";

    private final ObjectMapper objectMapper;

    public ApiPayloadSanitizer() {
        this(new ObjectMapper());
    }

    public ApiPayloadSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String sanitizeJsonPayload(String payload, Set<String> sensitiveFields, int maxLength) {
        if (payload == null || payload.isBlank()) {
            return "";
        }

        String sanitized = sanitizeJson(payload, sensitiveFields);
        return truncate(sanitized, maxLength);
    }

    public String sanitizeValue(String key, String value, Set<String> sensitiveFields, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = isSensitive(key, sensitiveFields) ? MASK : normalizeForLog(value);
        return truncate(sanitized, maxLength);
    }

    public String sanitizeExactValue(String key, String value, Set<String> sensitiveFields, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = isSensitiveExact(key, sensitiveFields) ? MASK : normalizeForLog(value);
        return truncate(sanitized, maxLength);
    }

    public boolean isSensitive(String key, Set<String> sensitiveFields) {
        if (key == null) {
            return false;
        }
        String normalizedKey = normalize(key);
        return sensitiveFields.stream()
                .map(this::normalize)
                .anyMatch(normalizedKey::contains);
    }

    public boolean isSensitiveExact(String key, Set<String> sensitiveFields) {
        if (key == null) {
            return false;
        }
        String normalizedKey = normalize(key);
        return sensitiveFields.stream()
                .map(this::normalize)
                .anyMatch(normalizedKey::equals);
    }

    private String sanitizeJson(String payload, Set<String> sensitiveFields) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            mask(root, sensitiveFields);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return payload;
        }
    }

    private void mask(JsonNode node, Set<String> sensitiveFields) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey(), sensitiveFields)) {
                    objectNode.put(field.getKey(), MASK);
                } else {
                    mask(field.getValue(), sensitiveFields);
                }
            }
            return;
        }

        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(child -> mask(child, sensitiveFields));
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + TRUNCATED_SUFFIX;
    }

    private String normalizeForLog(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }
}
