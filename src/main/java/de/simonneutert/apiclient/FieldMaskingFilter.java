package de.simonneutert.apiclient;

import de.simonneutert.ApiException;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * A {@link ResponseFilter} that replaces the values of designated JSON fields
 * with a configurable mask string before deserialization.
 *
 * <p>The filter operates on a {@linkplain JsonNode#deepCopy() deep copy} of the
 * incoming node, so the original parsed tree is never modified.
 *
 * <p>Field names must match the <em>wire-format</em> key (e.g. {@code "hair_color"},
 * not the Java property name).
 */
public class FieldMaskingFilter implements ResponseFilter {

    /** Default replacement value written for each masked field. */
    public static final String DEFAULT_MASK = "***REDACTED***";

    private final Set<String> sensitiveFields;
    private final String maskValue;

    /**
     * Creates a filter that replaces every matching field with {@value #DEFAULT_MASK}.
     *
     * @param sensitiveFields JSON field names (wire format) to mask
     */
    public FieldMaskingFilter(Set<String> sensitiveFields) {
        this(sensitiveFields, DEFAULT_MASK);
    }

    /**
     * Creates a filter with a custom replacement string.
     *
     * @param sensitiveFields JSON field names (wire format) to mask
     * @param maskValue       replacement string written for each matching field
     */
    public FieldMaskingFilter(Set<String> sensitiveFields, String maskValue) {
        this.sensitiveFields = Set.copyOf(sensitiveFields);
        this.maskValue = maskValue;
    }

    @Override
    public JsonNode apply(JsonNode node) throws ApiException {
        JsonNode copy = node.deepCopy();
        mask(copy);
        return copy;
    }

    private void mask(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.properties()
                    .forEach(
                            entry -> {
                                if (sensitiveFields.contains(entry.getKey())) {
                                    obj.set(entry.getKey(), StringNode.valueOf(maskValue));
                                } else {
                                    mask(entry.getValue());
                                }
                            });
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(this::mask);
        }
    }
}
