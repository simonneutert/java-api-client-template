package de.simonneutert.apiclient;

import de.simonneutert.ApiException;
import tools.jackson.databind.JsonNode;

/**
 * A post-processing transformation applied to the raw {@link JsonNode} returned
 * by the remote API before it is deserialized into the target type.
 *
 * <p>Filters are applied in registration order. Each filter receives the output
 * of the previous one, and its return value is passed to the next.
 *
 * <p>Implementations MUST return a non-null {@link JsonNode} (which may be the
 * same instance, a mutated copy, or an entirely new node).
 */
@FunctionalInterface
public interface ResponseFilter {
    /**
     * Applies this filter to the given JSON node and returns the (possibly transformed) result.
     *
     * @param node the raw parsed response tree; never {@code null}
     * @return the transformed node; must not be {@code null}
     * @throws ApiException if the filter detects an unrecoverable problem
     */
    JsonNode apply(JsonNode node) throws ApiException;
}
