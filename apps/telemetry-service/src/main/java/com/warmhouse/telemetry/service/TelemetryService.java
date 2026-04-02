package com.warmhouse.telemetry.service;

import com.warmhouse.telemetry.dto.HistoryResponse;
import com.warmhouse.telemetry.dto.IngestReadingRequest;
import com.warmhouse.telemetry.dto.ReadingResponse;
import com.warmhouse.telemetry.model.TelemetryReading;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface TelemetryService {

    TelemetryReading ingest(IngestReadingRequest request);

    Optional<ReadingResponse> getLatest(Integer sensorId);

    HistoryResponse getHistory(Integer sensorId, OffsetDateTime from, OffsetDateTime to, int limit);
}
