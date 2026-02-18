package com.motorola.scada.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "scada_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScadaNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nodeId;

    @Column(nullable = false)
    private String nodeName;

    @Enumerated(EnumType.STRING)
    private NodeType nodeType;

    @Column(nullable = false)
    private String plantArea; // e.g. "Mixing Plant A", "Warehouse B", "Boiler Room"

    // Sensor values (simulated)
    private Double temperature;      // Celsius
    private Double pressure;         // Bar
    private Double flowRate;         // L/min
    private Double tankLevel;        // %
    private Double motorRpm;         // RPM
    private Boolean alarmActive;
    private Boolean motorRunning;
    private Boolean valveOpen;

    // Telemetry mapping to radio
    @Column(name = "linked_radio_id")
    private String linkedRadioId;

    // Modbus addressing (for simulation)
    private Integer modbusSlaveId;
    private Integer modbusRegisterStart;

    // MOTOTRBO GPIO telemetry mapping
    // The XPR/DP radios have GPIO pins for telemetry
    private Integer gpioPin1; // Digital in/out
    private Integer gpioPin2;
    private Integer gpioPin3;
    private Integer gpioPin4;
    private String gpioMapping; // JSON string of pin-to-SCADA mapping

    @Enumerated(EnumType.STRING)
    private NodeStatus status;

    private LocalDateTime lastUpdate;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdate = LocalDateTime.now();
    }

    public enum NodeType {
        TEMPERATURE_SENSOR,
        PRESSURE_SENSOR,
        FLOW_METER,
        TANK_LEVEL,
        MOTOR_CONTROLLER,
        VALVE_ACTUATOR,
        ALARM_PANEL,
        POWER_METER,
        CONVEYOR_CONTROLLER,
        PACKAGING_LINE,
        BOILER_CONTROLLER,
        HVAC_UNIT
    }

    public enum NodeStatus {
        NORMAL, WARNING, ALARM, OFFLINE, MAINTENANCE
    }
}
