# Modern Java17+ Wiremock Example for Swapi.dev

This project demonstrates how to use WireMock with Java 17 to mock the Star Wars API (SWAPI) for testing purposes. It includes examples of setting up WireMock, creating stubs for API endpoints, and running tests against the mocked API.

## What is included?

Just the latest and greatest Java libraries for API client development and testing:

- Java 17+ codebase
- OkHttp 5 client for making HTTP requests
- Jackson v3 for JSON processing
- JUnit 6 for testing
- WireMock 3 for API mocking
- Logback v1.5 for logging

## Development

You need Java 21+ to build and run this project. Use Maven for dependency management and build tasks.

```bash
# Build the project
mvn clean install
# Format code with Spotless
mvn spotless:apply
# Run tests
mvn test
```

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

`FieldMaskingFilter` replaces the value of any named JSON field with `***REDACTED***` (or a custom string). The replacement happens on the parsed `JsonNode` tree — before deserialization into a Java record — so sensitive data never appears in the object graph, logs, or heap dumps.

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

Multiple filters chain in order:

```java
// will replace values with "███████" instead of "***REDACTED***"
ApiClient client = new ApiClient.Builder()
    .baseUrl("https://api.example.com")
    .addFilter(new FieldMaskingFilter(Set.of("ssn", "credit_card"), "███████"))
    .addFilter(new FieldMaskingFilter(Set.of("date_of_birth")))
    .build();
```

---

## Recording WireMock cassettes against a protected service

Many production APIs require an OAuth2 bearer token. The record/playback pattern still works — you supply the real token only during the one-time recording run; the saved mapping files contain no credentials, and all subsequent test runs are fully offline.

The pattern is demonstrated in [`RecordPlaybackTest`](src/test/java/de/simonneutert/RecordPlaybackTest.java), with shared helpers in [`SwapiTest`](src/test/java/de/simonneutert/SwapiTest.java). The key ideas:

- Check whether mappings already exist; if not, call `wm.startRecording(target)` before the request and `wm.stopRecording()` after.
- Pass credentials (e.g. `Authorization: Bearer …`) as request headers at call time — read them from an environment variable, never hard-code them.
- **Do not** call `.captureHeader("Authorization")` on the `recordSpec`: that would write the token value into the saved mapping file on disk. Omitting it means WireMock forwards the header to the real service during recording but never persists it.
- As an optional safety net, assert before committing that no credentials were captured:

```bash
grep -r '"Authorization"' src/test/resources/*/mappings && echo "WARNING: credentials found!" || echo "OK: no credentials in mappings"
```

Run the recording once with the real token, then all subsequent CI runs use the saved mappings with no network access needed:

```bash
MY_API_TOKEN=eyJhbGci... mvn test -Dtest=RecordPlaybackTest
```

**4. All subsequent runs are fully offline**

Once `src/test/resources/hr/mappings/` contains the saved mappings, the `hasMappings` guard prevents any recording or real HTTP call. The `FieldMaskingFilter` still runs on the stored response body, so masked fields are never deserialized with their real values.

```bash
mvn test   # no token required, no network access
```

### Security checklist

| Concern                                     | Mitigation                                                                                                                                    |
| ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Token in environment only                   | `System.getenv("HR_API_TOKEN")` — never hard-coded or logged                                                                                  |
| Token not written to disk                   | `captureHeader("Authorization")` is **not** called — WireMock forwards the header to the real API but never persists it into the mapping file |
| Sensitive field values not in object graph  | `FieldMaskingFilter` applied before `treeToValue`                                                                                             |
| Sensitive field values not in mapping files | Strip from recorded response bodies before committing                                                                                         |
