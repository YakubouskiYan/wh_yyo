package com.warmhouse.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDeviceRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String serialNumber;

    @NotBlank
    private String deviceType;

    private String homeId;
    private String roomId;
    private String firmwareVersion;
}
