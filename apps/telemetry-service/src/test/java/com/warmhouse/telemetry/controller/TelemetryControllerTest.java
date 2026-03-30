package com.warmhouse.telemetry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warmhouse.telemetry.dto.HistoryResponse;
import com.warmhouse.telemetry.dto.IngestReadingRequest;
import com.warmhouse.telemetry.dto.ReadingResponse;
import com.warmhouse.telemetry.model.TelemetryReading;
import com.warmhouse.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TelemetryController.class)
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TelemetryService telemetryService;

    @Test
    void ingest_validRequest_returns201() throws Exception {
        IngestReadingRequest request = new IngestReadingRequest(42, 22.5, "celsius");

        TelemetryReading reading = new TelemetryReading();
        reading.setId(1L);
        reading.setSensorId(42);
        reading.setValue(22.5);
        reading.setUnit("celsius");
        reading.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(telemetryService.ingest(any(IngestReadingRequest.class))).thenReturn(reading);

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensorId").value(42))
                .andExpect(jsonPath("$.value").value(22.5));
    }

    @Test
    void ingest_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getLatest_existingReading_returns200() throws Exception {
        ReadingResponse response = new ReadingResponse(1L, 42, 22.5, "celsius", OffsetDateTime.now(ZoneOffset.UTC));
        when(telemetryService.getLatest(42)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/telemetry/42/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value(42))
                .andExpect(jsonPath("$.value").value(22.5));
    }

    @Test
    void getLatest_noReading_returns404() throws Exception {
        when(telemetryService.getLatest(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/telemetry/99/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getHistory_returnsHistoryWithStats() throws Exception {
        ReadingResponse r1 = new ReadingResponse(1L, 42, 10.0, "celsius", OffsetDateTime.now(ZoneOffset.UTC));
        ReadingResponse r2 = new ReadingResponse(2L, 42, 30.0, "celsius", OffsetDateTime.now(ZoneOffset.UTC));
        HistoryResponse history = new HistoryResponse(42, List.of(r1, r2), 20.0, 10.0, 30.0);

        when(telemetryService.getHistory(eq(42), isNull(), isNull(), eq(100))).thenReturn(history);

        mockMvc.perform(get("/api/v1/telemetry/42/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value(42))
                .andExpect(jsonPath("$.readings").isArray())
                .andExpect(jsonPath("$.average").value(20.0))
                .andExpect(jsonPath("$.min").value(10.0))
                .andExpect(jsonPath("$.max").value(30.0));
    }

    @Test
    void getHistory_withLimitParam_passesLimit() throws Exception {
        HistoryResponse history = new HistoryResponse(42, List.of(), null, null, null);
        when(telemetryService.getHistory(eq(42), isNull(), isNull(), eq(10))).thenReturn(history);

        mockMvc.perform(get("/api/v1/telemetry/42/history").param("limit", "10"))
                .andExpect(status().isOk());
    }
}
