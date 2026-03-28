package de.simonneutert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Example model for a SWAPI person resource.
 * This class lives in test sources and is NOT part of the published library JAR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Person(
        String name,
        String height,
        String mass,
        @JsonProperty("hair_color") String hairColor,
        @JsonProperty("skin_color") String skinColor,
        @JsonProperty("eye_color") String eyeColor,
        @JsonProperty("birth_year") String birthYear,
        String gender) {}
