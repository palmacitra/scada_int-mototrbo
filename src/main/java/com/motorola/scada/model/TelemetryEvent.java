package com.motorola.scada.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String radioId;

    @Column(nullable = false)
    private String scadaNodeId;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    private String eventCode;     // e.g. "GPIO_1_HIGH", "TEMP_ALARM"
    private String description;
    private String rawData;       // JSON raw telemetry payload

    // Values
    private Double numericValue;
    private Boolean booleanValue;
    private String stringValue;

    // MOTOTRBO specific
    private Integer timeslot;          // DMR timeslot 1 or 2
    private Integer talkgroupId;
    private String sourceRadioAlias;

    @Enumerated(EnumType.STRING)
    private EventSeverity severity;

    private Boolean acknowledged;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;

    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        acknowledged = false;
    }

    public enum EventType {
        TELEMETRY_GPIO,         // GPIO pin state change (key MOTOTRBO feature)
        TELEMETRY_ANALOG,       // Analog value (via IOBOARD or gateway)
        SCADA_ALARM,            // SCADA alarm forwarded via radio
        RADIO_EMERGENCY,        // Emergency button pressed
        RADIO_STATUS,           // Radio check-in
        LOCATION_UPDATE,        // GPS update
        CALL_LOG,               // Voice call metadata
        SYSTEM_EVENT,           // System level event
        HEARTBEAT               // Periodic keep-alive
    }

    public enum EventSeverity {
        INFO, WARNING, ALARM, CRITICAL, EMERGENCY
    }
}
