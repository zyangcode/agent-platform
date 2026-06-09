package com.ls.agent.core.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationStructureTest {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([^_]+)__.+\\.sql$");

    @Test
    void versionedMigrationsUseUniqueFlywayVersions() throws Exception {
        Map<String, Long> versionCounts = Files.list(migrationDirectory())
                .map(path -> path.getFileName().toString())
                .map(VERSIONED_MIGRATION::matcher)
                .filter(matcher -> {
                    boolean matches = matcher.matches();
                    return matches;
                })
                .collect(Collectors.groupingBy(matcher -> matcher.group(1), Collectors.counting()));

        assertThat(versionCounts)
                .as("Flyway versioned migrations must not reuse the same Vxxx prefix")
                .allSatisfy((version, count) -> assertThat(count).as("V" + version).isOne());
    }

    private Path migrationDirectory() throws URISyntaxException, IOException {
        var resource = getClass().getClassLoader().getResource("db/migration");
        assertThat(resource).as("db/migration should exist").isNotNull();
        return Path.of(resource.toURI());
    }
}
