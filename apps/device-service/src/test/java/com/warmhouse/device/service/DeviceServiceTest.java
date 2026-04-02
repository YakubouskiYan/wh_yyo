package com.warmhouse.device.service;

import com.warmhouse.device.dto.CreateDeviceRequest;
import com.warmhouse.device.dto.UpdateDeviceRequest;
import com.warmhouse.device.messaging.DeviceEventPublisher;
import com.warmhouse.device.model.Device;
import com.warmhouse.device.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceEventPublisher eventPublisher;

    @InjectMocks
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
                .roomId("room-1")
                .status("offline")
                .build();
    }

    @Test
    void findAll_noFilters_returnsAll() {
        when(deviceRepository.findAll()).thenReturn(List.of(device));

        List<Device> result = deviceService.findAll(null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSerialNumber()).isEqualTo("SN-001");
    }

    @Test
    void findAll_byHomeId_delegatesToRepository() {
        when(deviceRepository.findByHomeId("home-1")).thenReturn(List.of(device));

        List<Device> result = deviceService.findAll("home-1", null, null, null);

        assertThat(result).hasSize(1);
        verify(deviceRepository).findByHomeId("home-1");
        verify(deviceRepository, never()).findAll();
    }

    @Test
    void findById_existingId_returnsDevice() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        Device result = deviceService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void findById_missingId_throwsNoSuchElement() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.findById(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void register_newDevice_savesAndPublishesEvent() {
        CreateDeviceRequest request = new CreateDeviceRequest();
        request.setName("Thermostat");
        request.setSerialNumber("SN-NEW");
        request.setDeviceType("thermostat");
        request.setHomeId("home-1");

        when(deviceRepository.existsBySerialNumber("SN-NEW")).thenReturn(false);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d = Device.builder()
                    .id(2L).name(d.getName()).serialNumber(d.getSerialNumber())
                    .deviceType(d.getDeviceType()).homeId(d.getHomeId()).status("offline")
                    .build();
            return d;
        });

        Device result = deviceService.register(request);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getStatus()).isEqualTo("offline");
        verify(eventPublisher).publishDeviceRegistered(any(Device.class));
    }

    @Test
    void register_duplicateSerial_throwsIllegalArgument() {
        CreateDeviceRequest request = new CreateDeviceRequest();
        request.setName("Sensor");
        request.setSerialNumber("SN-001");
        request.setDeviceType("sensor");

        when(deviceRepository.existsBySerialNumber("SN-001")).thenReturn(true);

        assertThatThrownBy(() -> deviceService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SN-001");

        verify(deviceRepository, never()).save(any());
    }

    @Test
    void update_statusChange_publishesStatusChangedEvent() {
        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setStatus("online");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenReturn(device);

        deviceService.update(1L, request);

        verify(eventPublisher).publishDeviceStatusChanged(any(Device.class), eq("offline"));
    }

    @Test
    void delete_existingId_deletesDevice() {
        when(deviceRepository.existsById(1L)).thenReturn(true);

        deviceService.delete(1L);

        verify(deviceRepository).deleteById(1L);
    }

    @Test
    void delete_missingId_throwsNoSuchElement() {
        when(deviceRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> deviceService.delete(99L))
                .isInstanceOf(NoSuchElementException.class);

        verify(deviceRepository, never()).deleteById(any());
    }
}
