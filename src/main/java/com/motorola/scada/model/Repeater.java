package com.motorola.scada.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "repeaters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repeater {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String repeaterId;

    @Column(nullable = false)
    private String model; // SLR5000, SLR8000, MTR3000

    @Enumerated(EnumType.STRING)
    private RepeaterModel modelType;

    @Column(nullable = false)
    private String siteName;

    @Column(nullable = false)
    private String zoneArea;

    private Double latitude;
    private Double longitude;
    private Double antennaHeightMeters; // AMSL
    private Double antennaTiltDegrees;

    // RF Parameters
    private Double txFrequencyMhz;
    private Double rxFrequencyMhz;
    private Double txPowerWatts; // 1-50W for SLR5000, 1-100W for SLR8000
    private Double antennaGainDbi;
    private Double cableLossDb;

    // Coverage
    private Double coverageRadiusKm;
    private Double estimatedAreaHa; // hectares

    // MOTOTRBO IP Site Connect / Capacity Plus
    @Enumerated(EnumType.STRING)
    private SystemType systemType;

    private String ipAddress;
    private Integer controlPort; // default 50000
    private Boolean linkedToController;

    // Status
    @Enumerated(EnumType.STRING)
    private RepeaterStatus status;

    private Double temperature;
    private Double vswr; // Voltage Standing Wave Ratio
    private Double forwardPower;
    private Double reflectedPower;
    private Integer activeChannels; // 0 or 1 (DMR has 2 timeslots) or 2

    private LocalDateTime lastMaintenanceDate;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum RepeaterModel {
        SLR5000("SLR 5000", 50.0, 25.0),
        SLR8000("SLR 8000", 100.0, 40.0),
        MTR3000("MTR3000", 100.0, 35.0),
        SLR1000("SLR 1000", 10.0, 15.0); // Micro repeater

        private final String displayName;
        private final double maxPowerWatts;
        private final double typicalRangeKm;

        RepeaterModel(String displayName, double maxPowerWatts, double typicalRangeKm) {
            this.displayName = displayName;
            this.maxPowerWatts = maxPowerWatts;
            this.typicalRangeKm = typicalRangeKm;
        }

        public String getDisplayName() { return displayName; }
        public double getMaxPowerWatts() { return maxPowerWatts; }
        public double getTypicalRangeKm() { return typicalRangeKm; }
    }

    public enum SystemType {
        CONVENTIONAL_ANALOG,
        MOTOTRBO_CONVENTIONAL,      // Single site DMR
        IP_SITE_CONNECT,            // Multi-site linked
        CAPACITY_PLUS,              // Single site trunking
        LINKED_CAPACITY_PLUS,       // Multi-site trunking
        CAPACITY_MAX                // MOTOTRBO Capacity Max (TETRA-like)
    }

    public enum RepeaterStatus {
        ACTIVE, INACTIVE, FAULT, MAINTENANCE, STANDBY
    }
}
