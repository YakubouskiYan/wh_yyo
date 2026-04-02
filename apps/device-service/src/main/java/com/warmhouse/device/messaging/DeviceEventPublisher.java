package com.warmhouse.device.messaging;

import com.warmhouse.device.model.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    public void publishDeviceRegistered(Device device) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "device.registered");
        event.put("deviceId", device.getId());
        event.put("name", device.getName());
        event.put("serialNumber", device.getSerialNumber());
        event.put("deviceType", device.getDeviceType());
        event.put("homeId", device.getHomeId());
        event.put("roomId", device.getRoomId());
        event.put("status", device.getStatus());
        event.put("timestamp", OffsetDateTime.now().toString());

        try {
            rabbitTemplate.convertAndSend(exchange, "device.registered", event);
            log.info("Published device.registered event for device id={}", device.getId());
        } catch (Exception e) {
            log.warn("Failed to publish device.registered event: {}", e.getMessage());
        }
    }

    public void publishDeviceStatusChanged(Device device, String previousStatus) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "device.status_changed");
        event.put("deviceId", device.getId());
        event.put("previousStatus", previousStatus);
        event.put("newStatus", device.getStatus());
        event.put("timestamp", OffsetDateTime.now().toString());

        try {
            rabbitTemplate.convertAndSend(exchange, "device.status_changed", event);
            log.info("Published device.status_changed event for device id={}", device.getId());
        } catch (Exception e) {
            log.warn("Failed to publish device.status_changed event: {}", e.getMessage());
        }
    }
}
