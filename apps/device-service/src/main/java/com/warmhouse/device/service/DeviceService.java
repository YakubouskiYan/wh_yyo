package com.warmhouse.device.service;

import com.warmhouse.device.dto.CreateDeviceRequest;
import com.warmhouse.device.dto.UpdateDeviceRequest;
import com.warmhouse.device.messaging.DeviceEventPublisher;
import com.warmhouse.device.model.Device;
import com.warmhouse.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceEventPublisher eventPublisher;

    public List<Device> findAll(String homeId, String roomId, String deviceType, String status) {
        if (homeId != null)     return deviceRepository.findByHomeId(homeId);
        if (roomId != null)     return deviceRepository.findByRoomId(roomId);
        if (deviceType != null) return deviceRepository.findByDeviceType(deviceType);
        if (status != null)     return deviceRepository.findByStatus(status);
        return deviceRepository.findAll();
    }

    public Device findById(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Device not found: " + id));
    }

    @Transactional
    public Device register(CreateDeviceRequest request) {
        if (deviceRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new IllegalArgumentException("Device with serial number already exists: " + request.getSerialNumber());
        }

        Device device = Device.builder()
                .name(request.getName())
                .serialNumber(request.getSerialNumber())
                .deviceType(request.getDeviceType())
                .homeId(request.getHomeId())
                .roomId(request.getRoomId())
                .firmwareVersion(request.getFirmwareVersion())
                .status("offline")
                .build();

        device = deviceRepository.save(device);
        log.info("Registered device id={} serial={}", device.getId(), device.getSerialNumber());

        eventPublisher.publishDeviceRegistered(device);
        return device;
    }

    @Transactional
    public Device update(Long id, UpdateDeviceRequest request) {
        Device device = findById(id);
        String previousStatus = device.getStatus();

        if (request.getName() != null)            device.setName(request.getName());
        if (request.getHomeId() != null)           device.setHomeId(request.getHomeId());
        if (request.getRoomId() != null)           device.setRoomId(request.getRoomId());
        if (request.getFirmwareVersion() != null)  device.setFirmwareVersion(request.getFirmwareVersion());
        if (request.getStatus() != null) {
            device.setStatus(request.getStatus());
        }

        device = deviceRepository.save(device);

        if (request.getStatus() != null && !request.getStatus().equals(previousStatus)) {
            eventPublisher.publishDeviceStatusChanged(device, previousStatus);
        }

        return device;
    }

    @Transactional
    public void delete(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new NoSuchElementException("Device not found: " + id);
        }
        deviceRepository.deleteById(id);
        log.info("Deleted device id={}", id);
    }
}
