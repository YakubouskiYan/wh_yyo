package com.warmhouse.device.controller;

import com.warmhouse.device.dto.CreateDeviceRequest;
import com.warmhouse.device.dto.UpdateDeviceRequest;
import com.warmhouse.device.model.Device;
import com.warmhouse.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public List<Device> list(
            @RequestParam(required = false) String homeId,
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String status) {
        return deviceService.findAll(homeId, roomId, deviceType, status);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Device> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(deviceService.findById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody CreateDeviceRequest request) {
        try {
            Device created = deviceService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateDeviceRequest request) {
        try {
            return ResponseEntity.ok(deviceService.update(id, request));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            deviceService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "device-service");
    }
}
