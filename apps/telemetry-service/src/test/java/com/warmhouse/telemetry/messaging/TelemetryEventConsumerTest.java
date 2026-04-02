package com.warmhouse.telemetry.messaging;

import com.warmhouse.telemetry.dto.IngestReadingRequest;
import com.warmhouse.telemetry.model.TelemetryReading;
import com.warmhouse.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryEventConsumerTest {

    @Mock
    private TelemetryService telemetryService;

    @InjectMocks
    private TelemetryEventConsumer consumer;

    @Test
    void handleEvent_validEvent_callsIngest() {
        TelemetryEvent event = new TelemetryEvent("telemetry.reading", 42, 22.5, "celsius", "2024-01-01T00:00:00Z");

        TelemetryReading reading = new TelemetryReading();
        reading.setId(1L);
        reading.setSensorId(42);
        reading.setValue(22.5);
        reading.setUnit("celsius");
        reading.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(telemetryService.ingest(any(IngestReadingRequest.class))).thenReturn(reading);

        consumer.handleEvent(event);

        ArgumentCaptor<IngestReadingRequest> captor = ArgumentCaptor.forClass(IngestReadingRequest.class);
        verify(telemetryService).ingest(captor.capture());

        IngestReadingRequest captured = captor.getValue();
        assertThat(captured.sensorId()).isEqualTo(42);
        assertThat(captured.value()).isEqualTo(22.5);
        assertThat(captured.unit()).isEqualTo("celsius");
    }

    @Test
    void handleEvent_nullEvent_doesNotCallIngest() {
        consumer.handleEvent(null);

        verify(telemetryService, never()).ingest(any());
    }

    @Test
    void handleEvent_nullSensorId_doesNotCallIngest() {
        TelemetryEvent event = new TelemetryEvent("telemetry.reading", null, 22.5, "celsius", "2024-01-01T00:00:00Z");

        consumer.handleEvent(event);

        verify(telemetryService, never()).ingest(any());
    }
}
