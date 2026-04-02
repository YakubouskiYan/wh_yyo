package com.warmhouse.telemetry.messaging;

import com.warmhouse.telemetry.dto.IngestReadingRequest;
import com.warmhouse.telemetry.service.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TelemetryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventConsumer.class);

    private final TelemetryService telemetryService;

    public TelemetryEventConsumer(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @RabbitListener(queues = "${rabbitmq.telemetry-queue}")
    public void handleEvent(TelemetryEvent event) {
        if (event == null || event.sensorId() == null) {
            log.warn("Received null or invalid telemetry event");
            return;
        }
        log.info("Received telemetry event for sensor {}", event.sensorId());
        telemetryService.ingest(new IngestReadingRequest(event.sensorId(), event.value(), event.unit()));
    }
}
