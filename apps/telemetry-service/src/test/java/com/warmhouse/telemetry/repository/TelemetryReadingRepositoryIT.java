package com.warmhouse.telemetry.repository;

import com.warmhouse.telemetry.model.TelemetryReading;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TelemetryReadingRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("smarthome")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("init-test.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                postgres.getJdbcUrl() + "&currentSchema=telemetry_service");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "telemetry_service");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.rabbitmq.host", () -> "localhost");
    }

    @Autowired
    private TelemetryReadingRepository repository;

    private TelemetryReading buildReading(Integer sensorId, double value, OffsetDateTime recordedAt) {
        TelemetryReading r = new TelemetryReading();
        r.setSensorId(sensorId);
        r.setValue(value);
        r.setUnit("celsius");
        r.setRecordedAt(recordedAt);
        return r;
    }

    @Test
    void saveAndFindLatest_returnsNewest() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        repository.save(buildReading(1, 20.0, now.minusMinutes(5)));
        repository.save(buildReading(1, 25.0, now.minusMinutes(1)));
        repository.save(buildReading(1, 18.0, now.minusMinutes(10)));

        Optional<TelemetryReading> latest = repository.findFirstBySensorIdOrderByRecordedAtDesc(1);

        assertThat(latest).isPresent();
        assertThat(latest.get().getValue()).isEqualTo(25.0);
    }

    @Test
    void findLatest_noReadings_returnsEmpty() {
        Optional<TelemetryReading> latest = repository.findFirstBySensorIdOrderByRecordedAtDesc(999);

        assertThat(latest).isEmpty();
    }

    @Test
    void findHistory_withDateRange_returnsFiltered() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        repository.save(buildReading(2, 10.0, now.minusHours(3)));
        repository.save(buildReading(2, 20.0, now.minusHours(1)));
        repository.save(buildReading(2, 30.0, now.minusMinutes(10)));

        OffsetDateTime from = now.minusHours(2);

        List<TelemetryReading> result = repository.findHistoryFrom(2, from,
                org.springframework.data.domain.PageRequest.of(0, 100));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> !r.getRecordedAt().isBefore(from));
    }

    @Test
    void findHistory_sensorIsolation_returnsOnlyRequestedSensor() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        repository.save(buildReading(10, 22.0, now));
        repository.save(buildReading(11, 33.0, now));

        List<TelemetryReading> result = repository.findBySensorIdOrderByRecordedAtDesc(10,
                org.springframework.data.domain.PageRequest.of(0, 100));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSensorId()).isEqualTo(10);
    }
}
