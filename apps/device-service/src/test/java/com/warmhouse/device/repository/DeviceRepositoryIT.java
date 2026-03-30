package com.warmhouse.device.repository;

import com.warmhouse.device.model.Device;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeviceRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("smarthome")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("init-test.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                postgres.getJdbcUrl() + "&currentSchema=device_service");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "device_service");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.rabbitmq.host", () -> "localhost");
    }

    @Autowired
    private DeviceRepository deviceRepository;

    private Device buildDevice(String serial, String homeId, String status) {
        return Device.builder()
                .name("Device " + serial)
                .serialNumber(serial)
                .deviceType("thermostat")
                .homeId(homeId)
                .status(status)
                .build();
    }

    @Test
    void saveAndFindById_roundtrip() {
        Device saved = deviceRepository.save(buildDevice("SN-IT-001", "home-1", "offline"));

        Optional<Device> found = deviceRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getSerialNumber()).isEqualTo("SN-IT-001");
    }

    @Test
    void findByHomeId_returnsMatchingDevices() {
        deviceRepository.save(buildDevice("SN-IT-002", "home-A", "offline"));
        deviceRepository.save(buildDevice("SN-IT-003", "home-B", "offline"));

        List<Device> result = deviceRepository.findByHomeId("home-A");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHomeId()).isEqualTo("home-A");
    }

    @Test
    void existsBySerialNumber_existingSerial_returnsTrue() {
        deviceRepository.save(buildDevice("SN-IT-004", "home-1", "offline"));

        assertThat(deviceRepository.existsBySerialNumber("SN-IT-004")).isTrue();
        assertThat(deviceRepository.existsBySerialNumber("SN-MISSING")).isFalse();
    }

    @Test
    void findByStatus_returnsOnlyMatchingStatus() {
        deviceRepository.save(buildDevice("SN-IT-005", "home-1", "online"));
        deviceRepository.save(buildDevice("SN-IT-006", "home-1", "offline"));

        List<Device> online = deviceRepository.findByStatus("online");
        assertThat(online).allMatch(d -> "online".equals(d.getStatus()));
    }
}
