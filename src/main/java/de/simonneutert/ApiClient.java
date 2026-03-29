package de.simonneutert;

import de.simonneutert.apiclient.ResponseFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A generic HTTP API client backed by OkHttp and Jackson.
 *
 * <p>Create instances via the fluent {@link Builder}:
 * <pre>{@code
 * try (ApiClient client = new ApiClient.Builder()
 *         .baseUrl("https://api.example.com")
 *         .addHeader("Accept", "application/json")
 *         .addFilter(new FieldMaskingFilter(Set.of("token")))
 *         .build()) {
 *     MyData data = client.get("/v1/resource/1", MyData.class);
 * }
 * }</pre>
 *
 * <p>Always {@linkplain #close() close} the client when it is no longer needed to
 * release the underlying connection pool and thread resources.
 */
public class ApiClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final HttpUrl baseUrl;
    private final List<ResponseFilter> filters;

    private ApiClient(Builder builder) {
        this.mapper = new ObjectMapper();
        this.baseUrl =
                Objects.requireNonNull(
                        HttpUrl.parse(builder.baseUrl), "Invalid base URL: " + builder.baseUrl);
        this.filters = Collections.unmodifiableList(new ArrayList<>(builder.filters));

        OkHttpClient.Builder httpBuilder =
                new OkHttpClient.Builder()
                        .connectTimeout(builder.connectTimeoutMillis, TimeUnit.MILLISECONDS)
                        .readTimeout(builder.readTimeoutMillis, TimeUnit.MILLISECONDS)
                        .writeTimeout(builder.writeTimeoutMillis, TimeUnit.MILLISECONDS);

        if (!builder.defaultHeaders.isEmpty()) {
            Map<String, String> headers =
                    Collections.unmodifiableMap(new LinkedHashMap<>(builder.defaultHeaders));
            httpBuilder.addInterceptor(
                    chain -> {
                        Request.Builder reqBuilder = chain.request().newBuilder();
                        headers.forEach(reqBuilder::header);
                        return chain.proceed(reqBuilder.build());
                    });
        }

        if (builder.authInterceptor != null) {
            httpBuilder.addInterceptor(builder.authInterceptor);
        }

        this.http = httpBuilder.build();
    }

    /**
     * Convenience factory for clients that need no filters, custom headers, or auth.
     *
     * @param baseUrl the root URL of the remote API (e.g. {@code "https://api.example.com"})
     * @return a new {@code ApiClient}
     */
    public static ApiClient of(String baseUrl) {
        return new Builder().baseUrl(baseUrl).build();
    }

    /**
     * Performs an HTTP GET request and deserializes the response body.
     *
     * @param <T>          target type
     * @param endpoint     path relative to the base URL (e.g. {@code "/v1/users/42"})
     * @param responseType target deserialization type
     * @return deserialized response
     * @throws ApiException if the request fails or the server returns a non-2xx status
     */
    public <T> T get(String endpoint, Class<T> responseType) throws ApiException {
        Request request = new Request.Builder().url(resolveUrl(endpoint)).get().build();
        return execute(request, responseType);
    }

    /**
     * Performs an HTTP POST request, serializing {@code body} to JSON.
     *
     * @param <T>          target type
     * @param endpoint     path relative to the base URL
     * @param body         object to serialize as the request body
     * @param responseType target deserialization type
     * @return deserialized response
     * @throws ApiException if the request fails or the server returns a non-2xx status
     */
    public <T> T post(String endpoint, Object body, Class<T> responseType) throws ApiException {
        Request request =
                new Request.Builder().url(resolveUrl(endpoint)).post(toRequestBody(body)).build();
        return execute(request, responseType);
    }

    /**
     * Performs an HTTP PUT request, serializing {@code body} to JSON.
     *
     * @param <T>          target type
     * @param endpoint     path relative to the base URL
     * @param body         object to serialize as the request body
     * @param responseType target deserialization type
     * @return deserialized response
     * @throws ApiException if the request fails or the server returns a non-2xx status
     */
    public <T> T put(String endpoint, Object body, Class<T> responseType) throws ApiException {
        Request request =
                new Request.Builder().url(resolveUrl(endpoint)).put(toRequestBody(body)).build();
        return execute(request, responseType);
    }

    /**
     * Performs an HTTP PATCH request, serializing {@code body} to JSON.
     *
     * @param <T>          target type
     * @param endpoint     path relative to the base URL
     * @param body         object to serialize as the request body
     * @param responseType target deserialization type
     * @return deserialized response
     * @throws ApiException if the request fails or the server returns a non-2xx status
     */
    public <T> T patch(String endpoint, Object body, Class<T> responseType) throws ApiException {
        Request request =
                new Request.Builder().url(resolveUrl(endpoint)).patch(toRequestBody(body)).build();
        return execute(request, responseType);
    }

    /**
     * Performs an HTTP DELETE request.
     *
     * @param endpoint path relative to the base URL
     * @throws ApiException if the request fails or the server returns a non-2xx status
     */
    public void delete(String endpoint) throws ApiException {
        Request request = new Request.Builder().url(resolveUrl(endpoint)).delete().build();
        executeVoid(request);
    }

    /**
     * Releases the underlying OkHttp connection pool and executor threads.
     * The client must not be used after this method returns.
     */
    @Override
    public void close() {
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private HttpUrl resolveUrl(String endpoint) {
        String e = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        HttpUrl resolved = baseUrl.resolve(e);
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve endpoint against base URL: " + endpoint);
        }
        return resolved;
    }

    private RequestBody toRequestBody(Object body) throws ApiException {
        try {
            return RequestBody.create(mapper.writeValueAsBytes(body), JSON);
        } catch (JacksonException e) {
            throw new ApiException("Failed to serialize request body", e);
        }
    }

    private <T> T execute(Request request, Class<T> responseType) throws ApiException {
        log.debug("{} {}", request.method(), request.url());
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiException(
                        response.code(),
                        "HTTP "
                                + response.code()
                                + " "
                                + response.message()
                                + " for "
                                + request.method()
                                + " "
                                + request.url());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ApiException(
                        response.code(), "Empty response body from: " + request.url());
            }
            JsonNode node = mapper.readTree(body.string());
            if (!filters.isEmpty()) {
                node = node.deepCopy();
                for (ResponseFilter filter : filters) {
                    node = filter.apply(node);
                }
            }
            return mapper.treeToValue(node, responseType);
        } catch (JacksonException e) {
            throw new ApiException("Failed to parse JSON response from: " + request.url(), e);
        } catch (IOException e) {
            throw new ApiException("I/O error communicating with: " + request.url(), e);
        }
    }

    private void executeVoid(Request request) throws ApiException {
        log.debug("{} {}", request.method(), request.url());
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiException(
                        response.code(),
                        "HTTP "
                                + response.code()
                                + " "
                                + response.message()
                                + " for "
                                + request.method()
                                + " "
                                + request.url());
            }
        } catch (IOException e) {
            throw new ApiException("I/O error communicating with: " + request.url(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Fluent builder for {@link ApiClient}.
     *
     * <p>Usage:
     * <pre>{@code
     * ApiClient client = new ApiClient.Builder()
     *         .baseUrl("https://api.example.com")
     *         .addHeader("Accept", "application/json")
     *         .build();
     * }</pre>
     */
    public static final class Builder {

        private String baseUrl;
        private final List<ResponseFilter> filters = new ArrayList<>();
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private long connectTimeoutMillis = 10_000;
        private long readTimeoutMillis = 30_000;
        private long writeTimeoutMillis = 10_000;
        private Interceptor authInterceptor;

        /**
         * Sets the root URL of the remote API.
         *
         * @param baseUrl e.g. {@code "https://api.example.com"} — must not end with {@code /}
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            return this;
        }

        /**
         * Appends a {@link ResponseFilter} applied (in registration order) to every response
         * before deserialization.
         *
         * @param filter the filter to append; must not be {@code null}
         * @return this builder
         */
        public Builder addFilter(ResponseFilter filter) {
            this.filters.add(Objects.requireNonNull(filter, "filter must not be null"));
            return this;
        }

        /**
         * Adds a fixed HTTP request header sent with every outgoing request.
         *
         * @param name  header field name; must not be {@code null}
         * @param value header field value; must not be {@code null}
         * @return this builder
         */
        public Builder addHeader(String name, String value) {
            this.defaultHeaders.put(
                    Objects.requireNonNull(name, "header name must not be null"),
                    Objects.requireNonNull(value, "header value must not be null"));
            return this;
        }

        /**
         * Overrides the default connect timeout (10 s).
         *
         * @param duration timeout value
         * @param unit     time unit of {@code duration}
         * @return this builder
         */
        public Builder connectTimeout(long duration, TimeUnit unit) {
            this.connectTimeoutMillis = unit.toMillis(duration);
            return this;
        }

        /**
         * Overrides the default read timeout (30 s).
         *
         * @param duration timeout value
         * @param unit     time unit of {@code duration}
         * @return this builder
         */
        public Builder readTimeout(long duration, TimeUnit unit) {
            this.readTimeoutMillis = unit.toMillis(duration);
            return this;
        }

        /**
         * Overrides the default write timeout (10 s).
         *
         * @param duration timeout value
         * @param unit     time unit of {@code duration}
         * @return this builder
         */
        public Builder writeTimeout(long duration, TimeUnit unit) {
            this.writeTimeoutMillis = unit.toMillis(duration);
            return this;
        }

        /**
         * Sets an {@link Interceptor} responsible for adding authentication credentials to
         * outgoing requests (e.g., injecting an {@code Authorization: Bearer …} header).
         *
         * @param interceptor the auth interceptor; must not be {@code null}
         * @return this builder
         */
        public Builder authInterceptor(Interceptor interceptor) {
            this.authInterceptor =
                    Objects.requireNonNull(interceptor, "interceptor must not be null");
            return this;
        }

        /**
         * Builds the {@link ApiClient}.
         *
         * @return a new {@link ApiClient} instance
         * @throws NullPointerException if {@link #baseUrl(String)} has not been called
         */
        public ApiClient build() {
            Objects.requireNonNull(baseUrl, "baseUrl must be set before calling build()");
            return new ApiClient(this);
        }
    }
}
