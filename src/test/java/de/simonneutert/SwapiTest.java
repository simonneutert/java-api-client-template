package de.simonneutert;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class SwapiTest {

    static WireMockExtension buildWireMock(String filesDir) {
        return WireMockExtension.newInstance()
                .options(wireMockConfig().usingFilesUnderDirectory(filesDir).dynamicPort())
                .build();
    }

    static boolean hasMappings(Path mappingsDir) throws IOException {
        if (!Files.exists(mappingsDir)) return false;
        try (var stream = Files.list(mappingsDir)) {
            return stream.findAny().isPresent();
        }
    }
}
