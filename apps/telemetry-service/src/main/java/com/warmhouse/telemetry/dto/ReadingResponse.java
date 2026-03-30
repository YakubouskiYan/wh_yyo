package com.warmhouse.telemetry.dto;

import java.time.OffsetDateTime;

public record ReadingResponse(
        Long id,
        Integer sensorId,
        Double value,
        String unit,
        OffsetDateTime recordedAt
) {}
