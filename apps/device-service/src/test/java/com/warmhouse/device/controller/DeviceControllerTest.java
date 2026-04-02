package com.warmhouse.device.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warmhouse.device.dto.CreateDeviceRequest;
import com.warmhouse.device.dto.UpdateDeviceRequest;
import com.warmhouse.device.model.Device;
import com.warmhouse.device.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceService deviceService;

    private Device device;

    @BeforeEach
    void setUp() {
        device = Device.builder()
                .id(1L)
                .name("Thermostat")
                .serialNumber("SN-001")
                .deviceType("thermostat")
                .homeId("home-1")
                .status("offline")
                .build();
    }

    @Test
    void list_returnsDevices() throws Exception {
        when(deviceService.findAll(null, null, null, null)).thenReturn(List.of(device));

        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serialNumber").value("SN-001"));
    }

    @Test
    void list_withHomeIdFilter_passesFilterToService() throws Exception {
        when(deviceService.findAll("home-1", null, null, null)).thenReturn(List.of(device));

        mockMvc.perform(get("/api/v1/devices").param("homeId", "home-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].homeId").value("home-1"));
    }

    @Test
    void getById_found_returns200() throws Exception {
        when(deviceService.findById(1L)).thenReturn(device);

        mockMvc.perform(get("/api/v1/devices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(deviceService.findById(99L)).thenThrow(new NoSuchElementException("Device not found: 99"));

        mockMvc.perform(get("/api/v1/devices/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void register_validRequest_returns201() throws Exception {
        CreateDeviceRequest request = new CreateDeviceRequest();
        request.setName("Thermostat");
        request.setSerialNumber("SN-NEW");
        request.setDeviceType("thermostat");

        when(deviceService.register(any(CreateDeviceRequest.class))).thenReturn(device);

        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serialNumber").value("SN-001"));
    }

    @Test
    void register_duplicateSerial_returns409() throws Exception {
        CreateDeviceRequest request = new CreateDeviceRequest();
        request.setName("Thermostat");
        request.setSerialNumber("SN-001");
        request.setDeviceType("thermostat");

        when(deviceService.register(any(CreateDeviceRequest.class)))
                .thenThrow(new IllegalArgumentException("Device with serial number already exists: SN-001"));

        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void register_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_found_returns200() throws Exception {
        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setStatus("online");

        when(deviceService.update(eq(1L), any(UpdateDeviceRequest.class))).thenReturn(device);

        mockMvc.perform(put("/api/v1/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void update_notFound_returns404() throws Exception {
        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setStatus("online");

        when(deviceService.update(eq(99L), any(UpdateDeviceRequest.class)))
                .thenThrow(new NoSuchElementException("Device not found: 99"));

        mockMvc.perform(put("/api/v1/devices/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_found_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/devices/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new NoSuchElementException("Device not found: 99"))
                .when(deviceService).delete(99L);

        mockMvc.perform(delete("/api/v1/devices/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/devices/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
