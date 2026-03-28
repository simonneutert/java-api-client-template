package de.simonneutert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Example model for a SWAPI starship resource.
 * This class lives in test sources and is NOT part of the published library JAR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Starship(
        String name,
        String model,
        @JsonProperty("starship_class") String starshipClass,
        String manufacturer,
        @JsonProperty("cost_in_credits") String costInCredits,
        String length,
        String crew,
        String passengers,
        @JsonProperty("max_atmosphering_speed") String maxAtmospheringSpeed,
        @JsonProperty("hyperdrive_rating") String hyperdriveRating,
        @JsonProperty("MGLT") String mglt,
        @JsonProperty("cargo_capacity") String cargoCapacity,
        String consumables,
        List<String> films,
        List<String> pilots) {}
