package com.motorola.scada.service;

import com.motorola.scada.model.Repeater;
import com.motorola.scada.model.SizingResult;
import com.motorola.scada.service.RfPropagationService.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SizingEngineService {

    @Autowired
    private RfPropagationService rfService;

    // MOTOTRBO product specifications (verified from Motorola Solutions datasheets)
    private static final Map<String, RadioSpec> RADIO_SPECS = Map.of(
        "XPR7550e", new RadioSpec("XPR7550e", 4.0, -116.0, true, true, "UHF", 403.0),
        "XPR7350e", new RadioSpec("XPR7350e", 4.0, -116.0, true, false, "UHF", 403.0),
        "DP4801e",  new RadioSpec("DP4801e",  5.0, -116.0, true, true,  "UHF", 403.0),
        "DP4601e",  new RadioSpec("DP4601e",  5.0, -116.0, false, true, "UHF", 403.0),
        "SL3500e",  new RadioSpec("SL3500e",  3.0, -114.0, false, false,"UHF", 403.0),
        "R7",       new RadioSpec("R7",       5.0, -117.0, true,  true, "UHF", 403.0)
    );

    private static final Map<String, RepeaterSpec> REPEATER_SPECS = Map.of(
        "SLR5000",  new RepeaterSpec("SLR5000",  50.0,  2, true),
        "SLR8000",  new RepeaterSpec("SLR8000",  100.0, 2, true),
        "MTR3000",  new RepeaterSpec("MTR3000",  100.0, 2, false),
        "SLR1000",  new RepeaterSpec("SLR1000",  10.0,  2, true)
    );

    /**
     * Main sizing calculation for presales
     */
    public SizingResult calculate(SizingRequest req) {
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // 1. Select appropriate system type
        String systemType = determineSystemType(req);

        // 2. RF Coverage calculation
        RadioSpec radio = RADIO_SPECS.getOrDefault(req.radioModel(), RADIO_SPECS.get("XPR7550e"));
        RepeaterSpec repeaterSpec = REPEATER_SPECS.get(req.repeaterModel() != null ? req.repeaterModel() : "SLR5000");

        EnvironmentType envType = req.isIndoor() ? EnvironmentType.INDUSTRIAL_INDOOR : EnvironmentType.INDUSTRIAL_OUTDOOR;

        double coverageRadius = rfService.calculateCoverageRadius(
                repeaterSpec.maxPowerW(),
                8.0,   // Typical 8 dBi collinear antenna for industrial
                2.0,   // 2 dB cable loss
                radio.sensitivityDbm(),
                RfPropagationService.MINIMUM_LINK_MARGIN_DB,
                radio.frequencyMhz(),
                req.antennaTowerHeightM(),
                envType
        );

        int numRepeaters = rfService.estimateRepeatersNeeded(req.totalAreaHa(), coverageRadius);

        // 3. Generate site recommendations (simplified grid layout)
        List<SizingResult.SiteRecommendation> sites = generateSiteLayout(
                req, numRepeaters, coverageRadius, repeaterSpec, warnings);

        // 4. Capacity analysis
        // MOTOTRBO DMR: Each repeater = 2 timeslots
        // One timeslot reserved for SCADA telemetry (best practice)
        int totalTimeslots = numRepeaters * 2;
        int voiceTimeslots = totalTimeslots - (systemType.equals("LINKED_CAPACITY_PLUS") ? numRepeaters : 0);

        ChannelCapacity capacity = rfService.calculateCapacity(
                totalTimeslots,
                req.numUsers(),
                2.0, // 2 calls/hour/user - typical industrial
                30.0  // 30 sec avg call duration - industrial
        );

        // 5. Telemetry capacity
        // MOTOTRBO Telemetry: Uses TS2 (Timeslot 2) for data/telemetry
        // GPIO: XPR/DP radios have 4 GPIO pins
        // Each radio can report telemetry every ~200ms minimum (Tier II data)
        int gpioPerRadio = 4;
        int totalGpioPoints = req.numRadios() * gpioPerRadio;

        // SCADA poll rate: via MotoTRBO System (using embedded data in voice channel)
        // Telemetry packet ~100ms, with multiple radios polled round-robin
        int maxPollingRateMs = 200 * req.numScadaNodes(); // round-robin

        // 6. Integration options
        List<String> integrationOptions = buildIntegrationOptions(req, warnings, recommendations);

        // 7. Link budget for representative path
        double pathLoss = rfService.calculatePathLoss(
                radio.frequencyMhz(), req.antennaTowerHeightM(), 1.5,
                coverageRadius * 0.8, envType); // 80% edge
        LinkBudget lb = rfService.calculateLinkBudget(
                repeaterSpec.maxPowerW(), 8.0, 2.0, 0.0, 0.0,
                pathLoss, radio.sensitivityDbm());

        if (lb.linkMarginDb() < 6.0) {
            warnings.add("Link margin at coverage edge is marginal (" +
                    String.format("%.1f", lb.linkMarginDb()) + " dB). Consider increasing tower height or adding repeaters.");
        }

        if (capacity.utilizationPercent() > 70) {
            warnings.add("Channel utilization exceeds 70% (" +
                    String.format("%.1f", capacity.utilizationPercent()) + "%). Consider additional repeater capacity.");
        }

        // 8. Build recommendations
        addSystemRecommendations(req, numRepeaters, recommendations, systemType);

        Map<String, Integer> equipmentCount = new LinkedHashMap<>();
        equipmentCount.put(repeaterSpec.model() + " Repeater", numRepeaters);
        equipmentCount.put(radio.model() + " Portable Radio", req.numRadios());
        equipmentCount.put("Antenna (Collinear 8dBi)", numRepeaters);
        equipmentCount.put("MOTOTRBO Network Application Server", numRepeaters > 4 ? 1 : 0);
        equipmentCount.put("IP Gateway (for SCADA)", req.numScadaNodes() > 0 ? (int)Math.ceil(req.numScadaNodes() / 16.0) : 0);

        String budgetTier = numRepeaters <= 2 ? "ENTRY" : numRepeaters <= 6 ? "MID" : "ENTERPRISE";

        return SizingResult.builder()
                .totalAreaHectares(req.totalAreaHa())
                .numberOfSites(req.numSites())
                .numberOfUsers(req.numUsers())
                .numberOfScadaNodes(req.numScadaNodes())
                .systemType(systemType)
                .radioModel(radio.model())
                .recommendedRepeaters(numRepeaters)
                .recommendedRadios(req.numRadios())
                .recommendedRepeaterModel(repeaterSpec.model())
                .recommendedControllerModel(numRepeaters > 1 ? "MOTOTRBO Network Application Server (NAS)" : "Standalone")
                .recommendedSoftware(buildSoftwareList(systemType))
                .siteRecommendations(sites)
                .totalCoverageAreaHa(sites.stream().mapToDouble(s -> s.getCoverageAreaHa()).sum())
                .coverageEfficiencyPercent(Math.min(100, (sites.stream().mapToDouble(s -> s.getCoverageAreaHa()).sum() / req.totalAreaHa()) * 100))
                .maxSimultaneousCalls(totalTimeslots)
                .maxScadaPollingRate(1000 / maxPollingRateMs * req.numScadaNodes())
                .channelUtilizationPercent(capacity.utilizationPercent())
                .timeslotsAvailable(totalTimeslots)
                .gpioPointsAvailable(totalGpioPoints)
                .analogInputsAvailable(0) // Requires IOBOARD accessory
                .maxTelemetryUpdateRateMs(200)
                .telemetryProtocol("MOTOTRBO Native Telemetry (GPIO) + MOTOTRBO Data Gateway")
                .integrationOptions(integrationOptions)
                .recommendedIntegrationPath(integrationOptions.isEmpty() ? "MOTOTRBO Native Telemetry" : integrationOptions.get(0))
                .requiredGateways(buildGatewayList(req))
                .equipmentCount(equipmentCount)
                .estimatedBudgetTier(budgetTier)
                .linkBudgetDb(lb.eirpDbm())
                .receivedSignalLevelDbm(lb.receivedSignalLevelDbm())
                .noiseFloorDbm(-120.0)
                .linkMarginDb(lb.linkMarginDb())
                .propagationModel("Okumura-Hata (Industrial)")
                .warnings(warnings)
                .recommendations(recommendations)
                .build();
    }

    private String determineSystemType(SizingRequest req) {
        if (req.numSites() == 1 && req.numUsers() <= 100) {
            return req.numRadios() > 30 ? "CAPACITY_PLUS" : "MOTOTRBO_CONVENTIONAL";
        } else if (req.numSites() > 1 && req.totalAreaHa() <= 5000) {
            return req.numUsers() > 100 ? "LINKED_CAPACITY_PLUS" : "IP_SITE_CONNECT";
        } else {
            return "LINKED_CAPACITY_PLUS"; // Large multi-site always LCP
        }
    }

    private List<SizingResult.SiteRecommendation> generateSiteLayout(
            SizingRequest req, int numRepeaters, double coverageRadiusKm,
            RepeaterSpec spec, List<String> warnings) {

        List<SizingResult.SiteRecommendation> sites = new ArrayList<>();

        // Base coordinates - using a representative Indonesian FMCG location (Karawang industrial area)
        double baseLat = -6.3500;
        double baseLon = 107.2900;

        // Grid layout calculation
        double cellSizeKm = coverageRadiusKm * 1.5; // Hexagonal spacing
        int cols = (int) Math.ceil(Math.sqrt(numRepeaters));
        int rows = (int) Math.ceil((double) numRepeaters / cols);

        String[] siteNames = {"Central Hub", "North Wing", "South Gate", "East Plant",
                "West Warehouse", "Processing A", "Processing B", "Logistics Hub",
                "Utility Zone", "Admin Block", "Boiler Area", "Packaging Line"};

        int idx = 0;
        outer:
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (idx >= numRepeaters) break outer;

                double lat = baseLat + (r - rows/2.0) * (cellSizeKm / 111.0);
                double lon = baseLon + (c - cols/2.0) * (cellSizeKm / (111.0 * Math.cos(Math.toRadians(baseLat))));

                double cellAreaHa = Math.PI * Math.pow(coverageRadiusKm * 1000, 2) / 10000.0;

                sites.add(SizingResult.SiteRecommendation.builder()
                        .siteName(idx < siteNames.length ? siteNames[idx] : "Site-" + (idx + 1))
                        .lat(lat)
                        .lng(lon)
                        .repeaterModel(spec.model())
                        .antennaTowerHeightM(req.antennaTowerHeightM())
                        .txPowerWatts(spec.maxPowerW() * 0.8)
                        .coverageRadiusKm(coverageRadiusKm)
                        .coverageAreaHa(Math.round(cellAreaHa * 100.0) / 100.0)
                        .coveredZones(List.of("Zone " + (idx + 1)))
                        .signalMarginDb(RfPropagationService.MINIMUM_LINK_MARGIN_DB + 3.0)
                        .rationale("Optimal placement for " + String.format("%.1f", coverageRadiusKm) + " km radius coverage")
                        .build());
                idx++;
            }
        }
        return sites;
    }

    private List<String> buildIntegrationOptions(SizingRequest req, List<String> warnings, List<String> recommendations) {
        List<String> options = new ArrayList<>();

        if (req.numScadaNodes() > 0) {
            options.add("MOTOTRBO Native Telemetry (GPIO/Serial) - Best for simple I/O monitoring");
            options.add("MOTOTRBO Data Gateway + Modbus TCP - Best for existing SCADA/PLC systems");
            options.add("MOTOTRBO Application Server API (REST/JSON) - Best for custom SCADA software integration");
            options.add("Third-party SCADA middleware (e.g., Motorola PremierOne) - Best for large SCADA deployments");

            if (req.numScadaNodes() > 20) {
                recommendations.add("For " + req.numScadaNodes() + " SCADA nodes, recommend MOTOTRBO Data Gateway with Modbus RTU/TCP bridging for efficient bulk polling.");
            }
            recommendations.add("Use MOTOTRBO Telemetry feature on DP4000e/XPR7000e series for GPIO-based alarm integration (up to 4 I/O per radio).");
            recommendations.add("Configure TS2 (Timeslot 2) exclusively for SCADA data to prevent voice/data contention.");
        }
        return options;
    }

    private List<String> buildGatewayList(SizingRequest req) {
        List<String> gateways = new ArrayList<>();
        if (req.numSites() > 1) gateways.add("MOTOTRBO Network Application Server (NAS)");
        if (req.numScadaNodes() > 0) gateways.add("MOTOTRBO Data Gateway (Modbus/DNP3 to DMR bridge)");
        if (req.numSites() > 3) gateways.add("MOTOTRBO Revert Repeater (for coverage gaps)");
        return gateways;
    }

    private String buildSoftwareList(String systemType) {
        return switch (systemType) {
            case "CAPACITY_PLUS" -> "MOTOTRBO CPS, Capacity Plus Trunking Controller, MOTOTRBO Network Manager";
            case "LINKED_CAPACITY_PLUS" -> "MOTOTRBO CPS, Linked Capacity Plus, MOTOTRBO Network Manager, IP Site Controller";
            case "IP_SITE_CONNECT" -> "MOTOTRBO CPS, IP Site Connect, MOTOTRBO Network Manager";
            default -> "MOTOTRBO CPS, MOTOTRBO Network Manager";
        };
    }

    private void addSystemRecommendations(SizingRequest req, int numRepeaters,
                                           List<String> recommendations, String systemType) {
        recommendations.add("System topology: " + systemType + " - " + describeSystemType(systemType));
        recommendations.add("Use SLR8000 repeaters for sites covering >500 Ha to ensure sufficient TX power reserve.");
        recommendations.add("Install lightning protection (polyphaser) on all antenna feedlines - critical for Indonesian climate.");
        recommendations.add("Consider N+1 redundancy for critical manufacturing zones with spare repeater or standby channel.");
        if (req.totalAreaHa() > 1000) {
            recommendations.add("For " + req.totalAreaHa() + " Ha operation: implement MOTOTRBO Linked Capacity Plus for seamless wide-area trunking with SCADA priority calls.");
        }
    }

    private String describeSystemType(String type) {
        return switch (type) {
            case "MOTOTRBO_CONVENTIONAL" -> "Single site, single channel, up to ~30 users";
            case "CAPACITY_PLUS" -> "Single site trunking, 2-12 channels, 500+ users, SCADA on TS2";
            case "IP_SITE_CONNECT" -> "Multi-site linked conventional, up to 12 sites, seamless roaming";
            case "LINKED_CAPACITY_PLUS" -> "Multi-site trunking, 2-12 sites, 1000+ users, SCADA priority groups";
            case "CAPACITY_MAX" -> "MOTOTRBO Capacity Max (enterprise TETRA-equivalent), unlimited sites";
            default -> "Custom configuration";
        };
    }

    public record SizingRequest(
            double totalAreaHa,
            int numSites,
            int numUsers,
            int numRadios,
            int numScadaNodes,
            double antennaTowerHeightM,
            boolean isIndoor,
            String radioModel,
            String repeaterModel
    ) {}

    record RadioSpec(String model, double txPowerW, double sensitivityDbm,
                     boolean hasTelemetry, boolean hasGps, String band, double frequencyMhz) {}

    record RepeaterSpec(String model, double maxPowerW, int timeslots, boolean hasIpConnect) {}
}
