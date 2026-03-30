package com.warmhouse.device.dto;

import lombok.Data;

@Data
public class UpdateDeviceRequest {
    private String name;
    private String homeId;
    private String roomId;
    private String firmwareVersion;
    private String status;
}
