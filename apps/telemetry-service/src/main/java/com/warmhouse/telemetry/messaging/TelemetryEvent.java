package com.warmhouse.telemetry.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryEvent(
        String eventType,
        Integer sensorId,
        Double value,
        String unit,
        String timestamp
) {}
