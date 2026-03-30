package com.warmhouse.telemetry.dto;

import java.util.List;

public record HistoryResponse(
        Integer sensorId,
        List<ReadingResponse> readings,
        Double average,
        Double min,
        Double max
) {}
