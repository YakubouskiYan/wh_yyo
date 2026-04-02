package com.warmhouse.telemetry.service;

import com.warmhouse.telemetry.dto.HistoryResponse;
import com.warmhouse.telemetry.dto.IngestReadingRequest;
import com.warmhouse.telemetry.dto.ReadingResponse;
import com.warmhouse.telemetry.model.TelemetryReading;
import com.warmhouse.telemetry.repository.TelemetryReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceImplTest {

    @Mock
    private TelemetryReadingRepository repository;

    @InjectMocks
    private TelemetryServiceImpl service;

    private TelemetryReading reading;

    @BeforeEach
    void setUp() {
        reading = new TelemetryReading();
        reading.setId(1L);
        reading.setSensorId(42);
        reading.setValue(22.5);
        reading.setUnit("celsius");
        reading.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void ingest_savesReading() {
        IngestReadingRequest request = new IngestReadingRequest(42, 22.5, "celsius");
        when(repository.save(any(TelemetryReading.class))).thenReturn(reading);

        TelemetryReading result = service.ingest(request);

        assertThat(result.getSensorId()).isEqualTo(42);
        assertThat(result.getValue()).isEqualTo(22.5);
        verify(repository).save(any(TelemetryReading.class));
    }

    @Test
    void getLatest_existingReading_returnsResponse() {
        when(repository.findFirstBySensorIdOrderByRecordedAtDesc(42))
                .thenReturn(Optional.of(reading));

        Optional<ReadingResponse> result = service.getLatest(42);

        assertThat(result).isPresent();
        assertThat(result.get().sensorId()).isEqualTo(42);
        assertThat(result.get().value()).isEqualTo(22.5);
    }

    @Test
    void getLatest_noReading_returnsEmpty() {
        when(repository.findFirstBySensorIdOrderByRecordedAtDesc(99))
                .thenReturn(Optional.empty());

        Optional<ReadingResponse> result = service.getLatest(99);

        assertThat(result).isEmpty();
    }

    @Test
    void getHistory_multipleReadings_computesStats() {
        TelemetryReading r1 = buildReading(1L, 42, 10.0);
        TelemetryReading r2 = buildReading(2L, 42, 20.0);
        TelemetryReading r3 = buildReading(3L, 42, 30.0);

        when(repository.findHistory(eq(42), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(r1, r2, r3));

        HistoryResponse result = service.getHistory(42, null, null, 100);

        assertThat(result.readings()).hasSize(3);
        assertThat(result.average()).isEqualTo(20.0);
        assertThat(result.min()).isEqualTo(10.0);
        assertThat(result.max()).isEqualTo(30.0);
    }

    @Test
    void getHistory_emptyReadings_nullStats() {
        when(repository.findHistory(eq(99), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        HistoryResponse result = service.getHistory(99, null, null, 100);

        assertThat(result.readings()).isEmpty();
        assertThat(result.average()).isNull();
        assertThat(result.min()).isNull();
        assertThat(result.max()).isNull();
    }

    @Test
    void getHistory_withDateRange_passesParamsToRepository() {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);

        when(repository.findHistory(eq(42), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(List.of(reading));

        HistoryResponse result = service.getHistory(42, from, to, 50);

        assertThat(result.readings()).hasSize(1);
        verify(repository).findHistory(eq(42), eq(from), eq(to), any(Pageable.class));
    }

    private TelemetryReading buildReading(Long id, Integer sensorId, double value) {
        TelemetryReading r = new TelemetryReading();
        r.setId(id);
        r.setSensorId(sensorId);
        r.setValue(value);
        r.setUnit("celsius");
        r.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return r;
    }
}
