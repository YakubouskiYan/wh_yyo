package com.warmhouse.device.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "devices", schema = "device_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    @Column(name = "serial_number", nullable = false, unique = true, length = 100)
    private String serialNumber;

    @NotBlank
    @Column(name = "device_type", nullable = false, length = 50)
    private String deviceType;

    @Column(name = "home_id", length = 50)
    private String homeId;

    @Column(name = "room_id", length = 50)
    private String roomId;

    @Column(name = "firmware_version", length = 20)
    private String firmwareVersion;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "offline";

    @Builder.Default
    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt = OffsetDateTime.now();
}
