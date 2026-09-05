# Java API Client Template

This repository is a starting point for Java HTTP API clients. It combines an OkHttp-based client, Jackson JSON mapping, response filtering, and offline integration tests backed by recorded SWAPI fixtures.

## What is included?

The template includes:

- Java 17+ codebase
- OkHttp 5 client for making HTTP requests
- Jackson v3 for JSON processing
- JUnit 6 for testing
- WireMock 3 for API mocking
- Logback 1.6 for test logging

## Development

The generated library targets Java 17 and can be used on Java 17 or newer. The standard development environment uses JDK 25, as configured in `mise.toml`. CI also runs the test suite on JDK 17 and 21 to protect runtime compatibility.

### For the mise nerds

`mise.toml` pins the development JDK and provides shortcuts for the usual Maven commands. Install the configured tools, inspect the available tasks, and run one without activating mise in your shell:

```bash
mise install
mise tasks ls
mise run test
mise run verify
mise run format
mise run clean
```

The tasks use the included Maven Wrapper so every environment uses the same Maven version. You can also invoke it directly:

```bash
# Run the complete development build
./mvnw verify

# Run tests only
./mvnw test

# Format code with Spotless
./mvnw spotless:apply
```

`verify` compiles with `--release 17`, runs the tests, checks formatting, and builds the source and Javadoc artifacts.

### Forking checklist

Before using this template for a new client:

- Replace the Maven `groupId`, `artifactId`, project name, and description in `pom.xml`.
- Rename the `de.simonneutert` Java packages to your own namespace.
- Replace the project URL and all SCM URLs in `pom.xml`.
- Replace the SWAPI models, fixtures, and example tests with those for your target API.
- Review whether the public OkHttp `Interceptor` and Jackson `JsonNode` extension points are intentional parts of your API.

## Rationale

This project serves as a minimal, modern template for building API clients in Java. It focuses on clean design and best practices, such as:

- Using Java records for immutable data models
- Leveraging functional interfaces for flexible response filtering
- Demonstrating the record/playback pattern with WireMock for offline testing

## Using this project as a base for production implementations

This codebase is intentionally minimal and can serve as a starting point for real-world API clients. The key extension points are:

- **`ApiClient`** — built via the fluent `ApiClient.Builder`; add as many filters (`.addFilter()`), static headers (`.addHeader()`), or a custom auth interceptor (`.authInterceptor()`) as needed without changing the client's core logic. Use `ApiClient.of(baseUrl)` for the zero-config case.
- **`ResponseFilter`** — a `@FunctionalInterface` operating on a `JsonNode`. Implement it as a class or inline as a lambda.
- **`FieldMaskingFilter`** — the built-in implementation; masks named fields recursively across the entire JSON tree before deserialization.

### Adding authorization headers (e.g. OAuth2 / Strava)

```java
ApiClient client = new ApiClient.Builder()
    .baseUrl("https://www.strava.com/api/v3")
    .addHeader("Authorization", "Bearer " + System.getenv("STRAVA_TOKEN"))
    .build();
```

Headers are injected via an OkHttp interceptor and applied to every request automatically.

The WireMock record/playback pattern (see `RecordPlaybackTest`) lets you capture real API traffic once, then run all subsequent tests offline against the saved mappings.

---

## Filtering sensitive data

### Where filtering happens — two complementary layers

| Layer | Runs inside | Purpose | Mechanism |
|-------|------------|---------|-----------|
| **Client-side** (`ResponseFilter`) | `ApiClient.execute()` | Sanitize data *before* it enters the Java object graph | Transforms the parsed `JsonNode` tree before `treeToValue()` |
| **WireMock-side** (`StubRequestFilterV2`) | WireMock server | Sanitize what gets *recorded to disk* during record/playback | Intercepts requests/responses inside WireMock ([docs](https://wiremock.org/docs/extensibility/filtering-requests/)) |

This project implements the **client-side** approach. It protects the application layer regardless of where the HTTP response comes from (live API, WireMock playback, or any other test double). WireMock-side filtering is a complementary measure you can add if you also need to scrub the mapping files themselves.

### `FieldMaskingFilter` — the built-in implementation

`FieldMaskingFilter` replaces the value of any named JSON field with `***REDACTED***` (or a custom string). The replacement happens on a deep copy of the parsed `JsonNode` tree — before deserialization into a Java record — so sensitive data never appears in the object graph, logs, or heap dumps.

```java
import de.simonneutert.ApiClient;
import de.simonneutert.apiclient.FieldMaskingFilter;

import java.util.Set;

// Mask "email" and "phone" everywhere in the response (including nested objects)
ApiClient client = new ApiClient.Builder()
    .baseUrl("https://api.example.com")
    .addFilter(new FieldMaskingFilter(Set.of("email", "phone")))
    .build();

UserProfile profile = client.get("/api/v1/me", UserProfile.class);
// profile.email() → "***REDACTED***"
// profile.phone() → "***REDACTED***"
```

Multiple filters chain in registration order via `.addFilter()`:

```java
ApiClient client = new ApiClient.Builder()
    .baseUrl("https://api.example.com")
    .addFilter(new FieldMaskingFilter(Set.of("ssn", "credit_card"), "███████"))
    .addFilter(new FieldMaskingFilter(Set.of("date_of_birth")))
    .build();
```

### Lambda filters and `andThen()` composition

`ResponseFilter` is a `@FunctionalInterface` — use a lambda for ad-hoc transformations:

```java
// Log every response tree before deserialization
ResponseFilter logger = node -> { System.out.println(node); return node; };

ApiClient client = new ApiClient.Builder()
    .baseUrl("https://api.example.com")
    .addFilter(logger)
    .build();
```

Compose filters with `andThen()` when you want a single reusable unit:

```java
ResponseFilter piiFilter = new FieldMaskingFilter(Set.of("email", "phone"));
ResponseFilter secretsFilter = new FieldMaskingFilter(Set.of("api_key"), "█████");

// Combine into one filter — piiFilter runs first, then secretsFilter
ResponseFilter combined = piiFilter.andThen(secretsFilter);

ApiClient client = new ApiClient.Builder()
    .baseUrl("https://api.example.com")
    .addFilter(combined)
    .build();
```

---

## WireMock fixtures

The repository includes WireMock mappings under `src/test/resources`. Tests replay these fixtures locally and do not contact SWAPI.

The playback pattern is demonstrated in [`RecordPlaybackTest`](src/test/java/de/simonneutert/RecordPlaybackTest.java), with shared helpers in [`SwapiTest`](src/test/java/de/simonneutert/SwapiTest.java). The test checks that a mapping exists before making a request, which prevents a missing fixture from causing an accidental live network call.

To refresh fixtures, deliberately enable WireMock recording in a local test, record against the target service once, inspect the generated mappings, and restore playback-only behavior before committing. When the target uses credentials, load them from environment variables and make sure neither request headers nor sensitive response fields are captured in fixture files.

As a safety check before committing recorded mappings:

```bash
grep -r '"Authorization"' src/test/resources/*/mappings && echo "WARNING: credentials found!" || echo "OK: no credentials in mappings"
```

Run the playback test with:

```bash
./mvnw test -Dtest=RecordPlaybackTest
```
