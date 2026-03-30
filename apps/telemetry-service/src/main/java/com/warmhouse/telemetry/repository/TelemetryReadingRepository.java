package com.warmhouse.telemetry.repository;

import com.warmhouse.telemetry.model.TelemetryReading;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {

    Optional<TelemetryReading> findFirstBySensorIdOrderByRecordedAtDesc(Integer sensorId);

    List<TelemetryReading> findBySensorIdOrderByRecordedAtDesc(Integer sensorId, Pageable pageable);

    @Query("SELECT r FROM TelemetryReading r WHERE r.sensorId = :sensorId AND r.recordedAt >= :from ORDER BY r.recordedAt DESC")
    List<TelemetryReading> findHistoryFrom(@Param("sensorId") Integer sensorId, @Param("from") OffsetDateTime from, Pageable pageable);

    @Query("SELECT r FROM TelemetryReading r WHERE r.sensorId = :sensorId AND r.recordedAt <= :to ORDER BY r.recordedAt DESC")
    List<TelemetryReading> findHistoryTo(@Param("sensorId") Integer sensorId, @Param("to") OffsetDateTime to, Pageable pageable);

    @Query("SELECT r FROM TelemetryReading r WHERE r.sensorId = :sensorId AND r.recordedAt >= :from AND r.recordedAt <= :to ORDER BY r.recordedAt DESC")
    List<TelemetryReading> findHistoryBetween(@Param("sensorId") Integer sensorId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, Pageable pageable);
}
