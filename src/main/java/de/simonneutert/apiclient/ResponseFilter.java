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
 * <p>Implementations may mutate the node in place or return a new one.
 * The caller ({@link de.simonneutert.ApiClient}) provides a
 * {@linkplain JsonNode#deepCopy() deep copy}, so filters never affect the
 * original parsed tree.
 */
@FunctionalInterface
public interface ResponseFilter {
    /**
     * Applies this filter to the given JSON node and returns the (possibly transformed) result.
     *
     * @param node the parsed response tree (safe to mutate); never {@code null}
     * @return the transformed node; must not be {@code null}
     * @throws ApiException if the filter detects an unrecoverable problem
     */
    JsonNode apply(JsonNode node) throws ApiException;

    /**
     * Returns a composed filter that first applies {@code this} filter and then
     * applies {@code after} to the result.
     *
     * @param after the filter to apply after this one; must not be {@code null}
     * @return the composed filter
     */
    default ResponseFilter andThen(ResponseFilter after) {
        return node -> after.apply(this.apply(node));
    }
}
