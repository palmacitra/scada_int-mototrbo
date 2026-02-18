package com.motorola.scada.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SizingResult {

    // Input parameters
    private Double totalAreaHectares;
    private Integer numberOfSites;
    private Integer numberOfUsers;
    private Integer numberOfScadaNodes;
    private String systemType; // IP_SITE_CONNECT, CAPACITY_PLUS, etc.
    private String radioModel;

    // Recommended hardware
    private Integer recommendedRepeaters;
    private Integer recommendedRadios;
    private String recommendedRepeaterModel;
    private String recommendedControllerModel;
    private String recommendedSoftware; // MOTOTRBO CPS, ASTRO25, etc.

    // Coverage analysis
    private List<SiteRecommendation> siteRecommendations;
    private Double totalCoverageAreaHa;
    private Double coverageEfficiencyPercent;

    // Capacity analysis
    private Integer maxSimultaneousCalls;
    private Integer maxScadaPollingRate; // polls/second
    private Double channelUtilizationPercent;
    private Integer timeslotsAvailable;

    // Telemetry specifics
    private Integer gpioPointsAvailable;
    private Integer analogInputsAvailable;
    private Integer maxTelemetryUpdateRateMs;
    private String telemetryProtocol; // "MOTOTRBO Native Telemetry" or "via IOBOARD"

    // SCADA integration path
    private List<String> integrationOptions;
    private String recommendedIntegrationPath;
    private List<String> requiredGateways;

    // Cost estimate (relative units, not real prices)
    private Map<String, Integer> equipmentCount;
    private String estimatedBudgetTier; // "ENTRY", "MID", "ENTERPRISE"

    // RF calculations
    private Double linkBudgetDb;
    private Double receivedSignalLevelDbm;
    private Double noiseFloorDbm;
    private Double linkMarginDb;
    private String propagationModel; // "Okumura-Hata", "COST231"

    // Warnings & recommendations
    private List<String> warnings;
    private List<String> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiteRecommendation {
        private String siteName;
        private Double lat;
        private Double lng;
        private String repeaterModel;
        private Double antennaTowerHeightM;
        private Double txPowerWatts;
        private Double coverageRadiusKm;
        private Double coverageAreaHa;
        private List<String> coveredZones;
        private Double signalMarginDb;
        private String rationale;
    }
}
