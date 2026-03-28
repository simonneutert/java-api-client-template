package de.simonneutert;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import de.simonneutert.apiclient.FieldMaskingFilter;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ApiClientTest extends SwapiTest {

    @RegisterExtension static WireMockExtension wm = buildWireMock("src/test/resources/people");

    @Test
    void fetchPerson_noFilter() throws Exception {
        try (ApiClient client = ApiClient.of(wm.getRuntimeInfo().getHttpBaseUrl())) {
            Person person = client.get("/api/people/1/", Person.class);
            assertThat(person.name()).isEqualTo("Luke Skywalker");
        }
    }

    @Test
    void fetchPerson_nameRedacted() throws Exception {
        try (ApiClient client =
                new ApiClient.Builder()
                        .baseUrl(wm.getRuntimeInfo().getHttpBaseUrl())
                        .addFilter(new FieldMaskingFilter(Set.of("name")))
                        .build()) {
            Person person = client.get("/api/people/1/", Person.class);
            assertThat(person.name()).isEqualTo(FieldMaskingFilter.DEFAULT_MASK);
            assertThat(person.hairColor()).isEqualTo("blond");
        }
    }

    @Test
    void fetchPerson_hairColorRedacted() throws Exception {
        try (ApiClient client =
                new ApiClient.Builder()
                        .baseUrl(wm.getRuntimeInfo().getHttpBaseUrl())
                        .addFilter(new FieldMaskingFilter(Set.of("hair_color")))
                        .build()) {
            Person person = client.get("/api/people/1/", Person.class);
            assertThat(person.name()).isEqualTo("Luke Skywalker");
            assertThat(person.hairColor()).isEqualTo(FieldMaskingFilter.DEFAULT_MASK);
        }
    }

    @Test
    void fetchPerson_multipleFieldsRedacted() throws Exception {
        try (ApiClient client =
                new ApiClient.Builder()
                        .baseUrl(wm.getRuntimeInfo().getHttpBaseUrl())
                        .addFilter(new FieldMaskingFilter(Set.of("name", "birth_year", "gender")))
                        .build()) {
            Person person = client.get("/api/people/1/", Person.class);
            assertThat(person.name()).isEqualTo(FieldMaskingFilter.DEFAULT_MASK);
            assertThat(person.birthYear()).isEqualTo(FieldMaskingFilter.DEFAULT_MASK);
            assertThat(person.gender()).isEqualTo(FieldMaskingFilter.DEFAULT_MASK);
            assertThat(person.height()).isEqualTo("172");
        }
    }
}
