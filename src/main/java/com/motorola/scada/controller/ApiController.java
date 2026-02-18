package com.motorola.scada.controller;

import com.motorola.scada.model.*;
import com.motorola.scada.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ApiController {

    @Autowired
    private ScadaTelemetrySimulator simulator;

    @Autowired
    private SizingEngineService sizingEngine;

    // =================== LIVE DATA ===================

    @GetMapping("/radios")
    public ResponseEntity<Collection<RadioDevice>> getRadios() {
        return ResponseEntity.ok(simulator.getAllRadios());
    }

    @GetMapping("/scada/nodes")
    public ResponseEntity<Collection<ScadaNode>> getScadaNodes() {
        return ResponseEntity.ok(simulator.getAllScadaNodes());
    }

    @GetMapping("/repeaters")
    public ResponseEntity<Collection<Repeater>> getRepeaters() {
        return ResponseEntity.ok(simulator.getAllRepeaters());
    }

    @GetMapping("/telemetry/events")
    public ResponseEntity<List<TelemetryEvent>> getTelemetryEvents(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(simulator.getRecentEvents(limit));
    }

    // =================== SIZING ENGINE ===================

    @PostMapping("/sizing/calculate")
    public ResponseEntity<SizingResult> calculateSizing(@RequestBody SizingEngineService.SizingRequest req) {
        return ResponseEntity.ok(sizingEngine.calculate(req));
    }

    /**
     * Quick sizing with query params (for Postman / simple testing)
     */
    @GetMapping("/sizing/quick")
    public ResponseEntity<SizingResult> quickSizing(
            @RequestParam(defaultValue = "2000") double areaHa,
            @RequestParam(defaultValue = "4") int sites,
            @RequestParam(defaultValue = "150") int users,
            @RequestParam(defaultValue = "150") int radios,
            @RequestParam(defaultValue = "24") int scadaNodes,
            @RequestParam(defaultValue = "25") double towerHeight,
            @RequestParam(defaultValue = "XPR7550e") String radioModel,
            @RequestParam(defaultValue = "SLR8000") String repeaterModel
    ) {
        var req = new SizingEngineService.SizingRequest(
                areaHa, sites, users, radios, scadaNodes,
                towerHeight, false, radioModel, repeaterModel);
        return ResponseEntity.ok(sizingEngine.calculate(req));
    }

    // =================== RF CALCULATIONS ===================

    @GetMapping("/rf/coverage")
    public ResponseEntity<Map<String, Object>> rfCoverage(
            @RequestParam(defaultValue = "50") double txPowerW,
            @RequestParam(defaultValue = "8") double antennaGainDbi,
            @RequestParam(defaultValue = "25") double towerHeightM,
            @RequestParam(defaultValue = "443") double frequencyMhz,
            @RequestParam(defaultValue = "INDUSTRIAL_OUTDOOR") String environment
    ) {
        RfPropagationService rfService = new RfPropagationService();
        RfPropagationService.EnvironmentType env;
        try {
            env = RfPropagationService.EnvironmentType.valueOf(environment);
        } catch (Exception e) {
            env = RfPropagationService.EnvironmentType.INDUSTRIAL_OUTDOOR;
        }

        double radius = rfService.calculateCoverageRadius(txPowerW, antennaGainDbi, 2.0,
                RfPropagationService.DMR_SENSITIVITY_DBM, RfPropagationService.MINIMUM_LINK_MARGIN_DB,
                frequencyMhz, towerHeightM, env);

        double pathLoss = rfService.calculatePathLoss(frequencyMhz, towerHeightM, 1.5, radius * 0.9, env);
        var lb = rfService.calculateLinkBudget(txPowerW, antennaGainDbi, 2.0, 0, 0,
                pathLoss, RfPropagationService.DMR_SENSITIVITY_DBM);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coverageRadiusKm", String.format("%.2f", radius));
        result.put("coverageAreaHa", String.format("%.0f", Math.PI * radius * radius * 100));
        result.put("txPowerWatts", txPowerW);
        result.put("txPowerDbm", String.format("%.1f", lb.txPowerDbm()));
        result.put("eirpDbm", String.format("%.1f", lb.eirpDbm()));
        result.put("pathLossDb", String.format("%.1f", pathLoss));
        result.put("receivedSignalDbm", String.format("%.1f", lb.receivedSignalLevelDbm()));
        result.put("linkMarginDb", String.format("%.1f", lb.linkMarginDb()));
        result.put("isLinkAdequate", lb.isAdequate());
        result.put("propagationModel", "Okumura-Hata");
        result.put("environment", environment);
        return ResponseEntity.ok(result);
    }
}
