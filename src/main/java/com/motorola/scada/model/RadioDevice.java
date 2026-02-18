package com.motorola.scada.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "radio_devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RadioDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String radioId; // e.g. "RADIO-001"

    @Column(nullable = false)
    private String model; // e.g. "SL300", "XPR7550e", "DP4800e"

    @Enumerated(EnumType.STRING)
    private RadioSeries series; // MOTOTRBO_XPR, MOTOTRBO_SL, MOTOTRBO_DP, MOTOTRBO_XPR_P

    @Column(nullable = false)
    private String location; // e.g. "Warehouse A - Zone 1"

    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private RadioStatus status;

    // Telemetry fields (MOTOTRBO Telemetry feature)
    private Double batteryLevel; // 0.0 - 100.0 %
    private Double signalStrength; // dBm
    private Double temperature; // Celsius
    private Boolean emergencyActive;
    private Boolean gprsConnected;

    // SCADA integration
    private String scadaNodeId; // Linked SCADA node
    private String assignedZone;
    private Integer talkgroupId;
    private Integer channelNumber;

    // MOTOTRBO specific
    private String radioAlias;
    private Boolean telemetryEnabled;
    private String firmwareVersion;

    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastSeen = LocalDateTime.now();
    }

    public enum RadioStatus {
        ONLINE, OFFLINE, IDLE, TRANSMITTING, EMERGENCY, MAINTENANCE
    }

    public enum RadioSeries {
        MOTOTRBO_XPR,   // XPR7000/7000e series - flagship
        MOTOTRBO_DP,    // DP4000/4000e series - professional
        MOTOTRBO_SL,    // SL300/3500 - slim/covert
        MOTOTRBO_XPR_P, // XPR3000/5000 - mid-range
        MOTOTRBO_R7,    // R7 series - latest generation
        SLR_REPEATER    // SLR5000/8000 repeater
    }
}
