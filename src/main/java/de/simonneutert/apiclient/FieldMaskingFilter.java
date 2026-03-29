package de.simonneutert.apiclient;

import de.simonneutert.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * A {@link ResponseFilter} that replaces the values of designated JSON fields
 * with a configurable mask string before deserialization.
 *
 * <p>This filter <strong>mutates</strong> the given node in place. The caller
 * ({@link de.simonneutert.ApiClient}) is responsible for passing a safe copy
 * when the original tree must be preserved.
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
        mask(node);
        return node;
    }

    private void mask(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            // Two-pass: collect keys to mask first, then mutate —
            // avoids modifying the underlying map during iteration.
            List<String> toMask = new ArrayList<>();
            List<String> toRecurse = new ArrayList<>();
            obj.properties()
                    .forEach(
                            entry -> {
                                if (sensitiveFields.contains(entry.getKey())) {
                                    toMask.add(entry.getKey());
                                } else {
                                    toRecurse.add(entry.getKey());
                                }
                            });
            for (String key : toMask) {
                obj.set(key, StringNode.valueOf(maskValue));
            }
            for (String key : toRecurse) {
                mask(obj.get(key));
            }
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(this::mask);
        }
    }
}
