package mcp.toolkit.testing.framework.core.codec;

import mcp.toolkit.testing.framework.core.util.McpValidation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * JSON helper for MCP payload construction and parsing.
 */
public final class McpJsonCodec {

    private final ObjectMapper objectMapper;

    /**
     * Creates a codec using the provided Jackson {@link ObjectMapper}.
     *
     * @param objectMapper object mapper used for JSON conversion
     */
    public McpJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = McpValidation.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Builds a JSON object by delegating to a params writer.
     *
     * @param paramsWriter consumer that populates the object
     * @return populated JSON object node
     */
    public ObjectNode buildParams(Consumer<ObjectNode> paramsWriter) {
        McpValidation.requireNonNull(paramsWriter, "paramsWriter");
        ObjectNode params = objectMapper.createObjectNode();
        paramsWriter.accept(params);
        return params;
    }

    /**
     * Converts a value to a JSON node, or null when the input is null.
     *
     * @param value value to convert
     * @return JSON node or null
     */
    public JsonNode toJsonNode(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    /**
     * Converts a value to a JSON node for tool arguments.
     *
     * @param value value to convert
     * @return JSON node, or an empty object node when value is null
     */
    public JsonNode toArgumentsNode(Object value) {
        return value == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(value);
    }

    /**
     * Parses a JSON string into a {@link JsonNode}.
     *
     * @param data JSON payload
     * @return parsed node, or null when parsing fails
     */
    public JsonNode parseJson(String data) {
        try {
            return objectMapper.readTree(data);
        }
        catch (IOException ex) {
            return null;
        }
    }

    /**
     * Serializes a JSON node to a string.
     *
     * @param payload JSON payload
     * @return serialized JSON string
     * @throws IllegalStateException if serialization fails
     */
    public String toJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize MCP payload", ex);
        }
    }
}

