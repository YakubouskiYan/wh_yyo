package com.warmhouse.device.repository;

import com.warmhouse.device.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findByHomeId(String homeId);

    List<Device> findByRoomId(String roomId);

    List<Device> findByDeviceType(String deviceType);

    List<Device> findByStatus(String status);

    boolean existsBySerialNumber(String serialNumber);
}
