package com.warmhouse.telemetry.dto;

import jakarta.validation.constraints.NotNull;

public record IngestReadingRequest(
        @NotNull Integer sensorId,
        @NotNull Double value,
        String unit
) {}
