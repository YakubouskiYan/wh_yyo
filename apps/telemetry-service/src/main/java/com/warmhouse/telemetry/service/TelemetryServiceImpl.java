package com.warmhouse.telemetry.service;

import com.warmhouse.telemetry.dto.HistoryResponse;
import com.warmhouse.telemetry.dto.IngestReadingRequest;
import com.warmhouse.telemetry.dto.ReadingResponse;
import com.warmhouse.telemetry.model.TelemetryReading;
import com.warmhouse.telemetry.repository.TelemetryReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Service
public class TelemetryServiceImpl implements TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryServiceImpl.class);

    private final TelemetryReadingRepository repository;

    public TelemetryServiceImpl(TelemetryReadingRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TelemetryReading ingest(IngestReadingRequest request) {
        TelemetryReading reading = new TelemetryReading();
        reading.setSensorId(request.sensorId());
        reading.setValue(request.value());
        reading.setUnit(request.unit());
        reading.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));

        TelemetryReading saved = repository.save(reading);
        log.info("Ingested reading for sensor {}: {} {}", saved.getSensorId(), saved.getValue(), saved.getUnit());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReadingResponse> getLatest(Integer sensorId) {
        return repository.findFirstBySensorIdOrderByRecordedAtDesc(sensorId)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public HistoryResponse getHistory(Integer sensorId, OffsetDateTime from, OffsetDateTime to, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<TelemetryReading> readings;
        if (from != null && to != null) {
            readings = repository.findHistoryBetween(sensorId, from, to, page);
        } else if (from != null) {
            readings = repository.findHistoryFrom(sensorId, from, page);
        } else if (to != null) {
            readings = repository.findHistoryTo(sensorId, to, page);
        } else {
            readings = repository.findBySensorIdOrderByRecordedAtDesc(sensorId, page);
        }

        List<ReadingResponse> responses = readings.stream().map(this::toResponse).toList();

        Double avg = null, min = null, max = null;
        if (!readings.isEmpty()) {
            OptionalDouble avgOpt = readings.stream().mapToDouble(TelemetryReading::getValue).average();
            avg = avgOpt.isPresent() ? avgOpt.getAsDouble() : null;
            min = readings.stream().mapToDouble(TelemetryReading::getValue).min().getAsDouble();
            max = readings.stream().mapToDouble(TelemetryReading::getValue).max().getAsDouble();
        }

        return new HistoryResponse(sensorId, responses, avg, min, max);
    }

    private ReadingResponse toResponse(TelemetryReading r) {
        return new ReadingResponse(r.getId(), r.getSensorId(), r.getValue(), r.getUnit(), r.getRecordedAt());
    }
}
