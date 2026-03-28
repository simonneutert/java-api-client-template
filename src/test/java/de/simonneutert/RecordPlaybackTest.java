package de.simonneutert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RecordPlaybackTest extends SwapiTest {

    static final Path MAPPINGS_DIR = Paths.get("src/test/resources/people/mappings");

    @RegisterExtension static WireMockExtension wm = buildWireMock("src/test/resources/people");

    /**
     * Runs against the recorded WireMock fixture. The test is skipped when no fixture
     * files are present so CI never falls back to the live network.
     *
     * <p>To record new fixtures: delete the files under {@code src/test/resources/people/mappings},
     * temporarily point {@code wm.startRecording("https://swapi.dev")} at the real API, and
     * re-run this test once.
     */
    @Test
    void fetchPerson() throws Exception {
        assumeTrue(
                hasMappings(MAPPINGS_DIR),
                "No WireMock mappings found – record fixtures first (see class Javadoc).");

        try (ApiClient client = ApiClient.of(wm.getRuntimeInfo().getHttpBaseUrl())) {
            Person person = client.get("/api/people/1/", Person.class);
            assertThat(person.name()).isNotEmpty();
        }
    }
}
