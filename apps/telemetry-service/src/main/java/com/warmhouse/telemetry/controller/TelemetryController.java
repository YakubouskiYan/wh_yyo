package com.warmhouse.telemetry.controller;

import com.warmhouse.telemetry.dto.IngestReadingRequest;
import com.warmhouse.telemetry.dto.ReadingResponse;
import com.warmhouse.telemetry.model.TelemetryReading;
import com.warmhouse.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryService service;

    public TelemetryController(TelemetryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReadingResponse> ingest(@Valid @RequestBody IngestReadingRequest request) {
        TelemetryReading reading = service.ingest(request);
        ReadingResponse response = new ReadingResponse(
                reading.getId(), reading.getSensorId(), reading.getValue(),
                reading.getUnit(), reading.getRecordedAt()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sensorId}/latest")
                .buildAndExpand(reading.getSensorId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{sensorId}/latest")
    public ResponseEntity<?> getLatest(@PathVariable Integer sensorId) {
        return service.getLatest(sensorId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "No readings for sensor " + sensorId)));
    }

    @GetMapping("/{sensorId}/history")
    public ResponseEntity<?> getHistory(
            @PathVariable Integer sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        if (limit < 1 || limit > 1000) limit = 100;
        return ResponseEntity.ok(service.getHistory(sensorId, from, to, limit));
    }

}
