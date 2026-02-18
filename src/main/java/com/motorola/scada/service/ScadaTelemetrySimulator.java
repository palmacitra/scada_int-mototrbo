package com.motorola.scada.service;

import com.motorola.scada.model.*;
import com.motorola.scada.websocket.TelemetryWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates real-time MOTOTRBO telemetry and SCADA data flow.
 * This mimics how MOTOTRBO radios report sensor data back to a central SCADA system
 * via DMR Tier II/III data transport.
 *
 * Integration flow:
 *   [Field Sensor] -> [GPIO on XPR/DP Radio] -> [DMR Air Interface] -> [Repeater] -> [Network] -> [SCADA Server]
 *                                                                                   -> [Dispatcher Console]
 */
@Service
public class ScadaTelemetrySimulator {

    @Autowired
    private TelemetryWebSocketHandler wsHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, RadioDevice> radios = new ConcurrentHashMap<>();
    private final Map<String, ScadaNode> scadaNodes = new ConcurrentHashMap<>();
    private final Map<String, Repeater> repeaters = new ConcurrentHashMap<>();
    private final List<TelemetryEvent> eventHistory = Collections.synchronizedList(new ArrayList<>());

    private final Random rng = new Random();

    @PostConstruct
    public void initializeDemoData() {
        initRepeaters();
        initScadaNodes();
        initRadios();
    }

    private void initRepeaters() {
        String[] repeaterData = {
            "RPT-001|SLR8000|Central Hub|SLR8000|-6.3500|107.2900|30.0|50.0",
            "RPT-002|SLR5000|North Plant|SLR5000|-6.3300|107.2950|25.0|40.0",
            "RPT-003|SLR5000|South Gate|SLR5000|-6.3700|107.2850|25.0|40.0",
            "RPT-004|SLR8000|East Warehouse|SLR8000|-6.3500|107.3100|30.0|50.0"
        };

        for (String data : repeaterData) {
            String[] p = data.split("\\|");
            Repeater r = Repeater.builder()
                    .repeaterId(p[0]).model(p[1]).siteName(p[2])
                    .modelType(Repeater.RepeaterModel.valueOf(p[3]))
                    .latitude(Double.parseDouble(p[4])).longitude(Double.parseDouble(p[5]))
                    .antennaHeightMeters(Double.parseDouble(p[6]))
                    .txPowerWatts(Double.parseDouble(p[7]))
                    .txFrequencyMhz(443.0).rxFrequencyMhz(448.0)
                    .antennaGainDbi(8.0).cableLossDb(2.0)
                    .systemType(Repeater.SystemType.LINKED_CAPACITY_PLUS)
                    .status(Repeater.RepeaterStatus.ACTIVE)
                    .temperature(35.0 + rng.nextDouble() * 10)
                    .vswr(1.2 + rng.nextDouble() * 0.3)
                    .forwardPower(Double.parseDouble(p[7]))
                    .reflectedPower(1.0 + rng.nextDouble())
                    .activeChannels(rng.nextInt(3))
                    .coverageRadiusKm(p[3].equals("SLR8000") ? 12.0 : 8.0)
                    .estimatedAreaHa(p[3].equals("SLR8000") ? 452.0 : 201.0)
                    .build();
            repeaters.put(r.getRepeaterId(), r);
        }
    }

    private void initScadaNodes() {
        Object[][] nodes = {
            {"SCADA-T01", "Boiler Temperature #1", ScadaNode.NodeType.TEMPERATURE_SENSOR, "Boiler Room", 85.0, 1},
            {"SCADA-T02", "Cooling Tower Temp",   ScadaNode.NodeType.TEMPERATURE_SENSOR, "Utility Block", 28.0, 1},
            {"SCADA-P01", "Boiler Pressure",       ScadaNode.NodeType.PRESSURE_SENSOR,    "Boiler Room", 8.5, 2},
            {"SCADA-P02", "Pipeline Pressure A",   ScadaNode.NodeType.PRESSURE_SENSOR,    "Processing A", 4.2, 2},
            {"SCADA-F01", "Feed Flow Meter",       ScadaNode.NodeType.FLOW_METER,          "Mixing Plant", 250.0, 3},
            {"SCADA-L01", "Raw Material Tank A",   ScadaNode.NodeType.TANK_LEVEL,          "Storage Zone", 72.0, 4},
            {"SCADA-L02", "Finished Goods Tank B", ScadaNode.NodeType.TANK_LEVEL,          "Storage Zone", 45.0, 4},
            {"SCADA-M01", "Conveyor Motor #1",     ScadaNode.NodeType.MOTOR_CONTROLLER,    "Packaging Line", 1450.0, 5},
            {"SCADA-M02", "Mixer Motor A",         ScadaNode.NodeType.MOTOR_CONTROLLER,    "Mixing Plant", 960.0, 5},
            {"SCADA-V01", "Inlet Valve Main",      ScadaNode.NodeType.VALVE_ACTUATOR,      "Processing A", 0.0, 6},
            {"SCADA-A01", "Fire Alarm Panel",      ScadaNode.NodeType.ALARM_PANEL,         "All Zones", 0.0, 7},
            {"SCADA-E01", "Power Meter Main",      ScadaNode.NodeType.POWER_METER,         "Substation", 380.0, 8}
        };

        for (Object[] n : nodes) {
            ScadaNode node = ScadaNode.builder()
                    .nodeId((String)n[0]).nodeName((String)n[1])
                    .nodeType((ScadaNode.NodeType)n[2]).plantArea((String)n[3])
                    .modbusSlaveId((Integer)n[5]).modbusRegisterStart(100)
                    .status(ScadaNode.NodeStatus.NORMAL)
                    .telemetryEnabled(true)
                    .gpioPin1(1).gpioPin2(2).gpioPin3(3).gpioPin4(4)
                    .build();

            double baseVal = (Double)n[4];
            applyNodeValue(node, baseVal);
            scadaNodes.put(node.getNodeId(), node);
        }
    }

    private void applyNodeValue(ScadaNode node, double val) {
        switch (node.getNodeType()) {
            case TEMPERATURE_SENSOR -> node.setTemperature(val);
            case PRESSURE_SENSOR    -> node.setPressure(val);
            case FLOW_METER         -> node.setFlowRate(val);
            case TANK_LEVEL         -> node.setTankLevel(val);
            case MOTOR_CONTROLLER   -> { node.setMotorRpm(val); node.setMotorRunning(val > 0); }
            case VALVE_ACTUATOR     -> node.setValveOpen(val > 0.5);
            case ALARM_PANEL        -> node.setAlarmActive(false);
            case POWER_METER        -> node.setTemperature(val); // abuse field for display
            default -> {}
        }
    }

    private void initRadios() {
        String[][] radioData = {
            {"RADIO-001", "XPR7550e", "Boiler Operator", "Boiler Room", "RPT-001", "SCADA-T01"},
            {"RADIO-002", "XPR7550e", "Processing Supervisor", "Processing A", "RPT-001", "SCADA-P01"},
            {"RADIO-003", "DP4801e",  "Warehouse A Lead", "Warehouse A", "RPT-002", "SCADA-L01"},
            {"RADIO-004", "DP4801e",  "Warehouse B Lead", "Warehouse B", "RPT-004", "SCADA-L02"},
            {"RADIO-005", "XPR7350e", "Maintenance Eng #1", "Central Hub", "RPT-001", "SCADA-M01"},
            {"RADIO-006", "XPR7350e", "Maintenance Eng #2", "North Plant", "RPT-002", "SCADA-M02"},
            {"RADIO-007", "DP4601e",  "Security Guard #1", "Gate North", "RPT-002", null},
            {"RADIO-008", "DP4601e",  "Security Guard #2", "Gate South", "RPT-003", null},
            {"RADIO-009", "R7",       "Plant Manager", "Admin Block", "RPT-001", null},
            {"RADIO-010", "SL3500e",  "Quality Control", "Packaging Line", "RPT-004", "SCADA-M01"},
            {"RADIO-011", "XPR7550e", "Shift Supervisor A", "Processing A", "RPT-001", "SCADA-F01"},
            {"RADIO-012", "XPR7550e", "Shift Supervisor B", "Processing B", "RPT-003", "SCADA-V01"}
        };

        for (String[] d : radioData) {
            RadioDevice radio = RadioDevice.builder()
                    .radioId(d[0]).model(d[1]).radioAlias(d[2]).location(d[3])
                    .series(mapSeries(d[1]))
                    .assignedZone(d[3])
                    .scadaNodeId(d[5])
                    .talkgroupId(1001)
                    .channelNumber(1)
                    .batteryLevel(60 + rng.nextDouble() * 40)
                    .signalStrength(-70 - rng.nextDouble() * 30)
                    .temperature(35 + rng.nextDouble() * 5)
                    .emergencyActive(false)
                    .gprsConnected(true)
                    .telemetryEnabled(d[5] != null)
                    .firmwareVersion("R02.20.00")
                    .status(RadioDevice.RadioStatus.IDLE)
                    .lastSeen(LocalDateTime.now())
                    .build();
            radios.put(radio.getRadioId(), radio);
        }
    }

    private RadioDevice.RadioSeries mapSeries(String model) {
        if (model.startsWith("XPR7")) return RadioDevice.RadioSeries.MOTOTRBO_XPR;
        if (model.startsWith("DP4")) return RadioDevice.RadioSeries.MOTOTRBO_DP;
        if (model.startsWith("SL")) return RadioDevice.RadioSeries.MOTOTRBO_SL;
        if (model.equals("R7")) return RadioDevice.RadioSeries.MOTOTRBO_R7;
        return RadioDevice.RadioSeries.MOTOTRBO_XPR_P;
    }

    /**
     * Simulate live telemetry updates every 2 seconds
     * This represents MOTOTRBO DMR Tier II data packets carrying SCADA data
     */
    @Scheduled(fixedRate = 2000)
    public void simulateTelemetry() {
        // Update SCADA nodes with realistic sensor drift
        scadaNodes.values().forEach(this::updateScadaNode);

        // Update radio status
        radios.values().forEach(this::updateRadioStatus);

        // Generate telemetry events for radios with SCADA links
        radios.values().stream()
                .filter(r -> r.getScadaNodeId() != null && rng.nextDouble() < 0.4)
                .forEach(this::generateTelemetryEvent);

        // Push aggregated state to WebSocket clients
        broadcastSystemState();
    }

    private void updateScadaNode(ScadaNode node) {
        // Simulate realistic sensor behavior with random walk + trend
        switch (node.getNodeType()) {
            case TEMPERATURE_SENSOR -> {
                double temp = node.getTemperature() != null ? node.getTemperature() : 50.0;
                temp += (rng.nextDouble() - 0.48) * 0.5; // Slight upward drift
                temp = Math.max(20, Math.min(150, temp));
                node.setTemperature(temp);
                node.setAlarmActive(temp > 120.0);
                node.setStatus(temp > 120 ? ScadaNode.NodeStatus.ALARM :
                               temp > 100 ? ScadaNode.NodeStatus.WARNING :
                               ScadaNode.NodeStatus.NORMAL);
            }
            case PRESSURE_SENSOR -> {
                double p = node.getPressure() != null ? node.getPressure() : 5.0;
                p += (rng.nextDouble() - 0.5) * 0.1;
                p = Math.max(0, Math.min(20, p));
                node.setPressure(p);
                node.setStatus(p > 15 ? ScadaNode.NodeStatus.ALARM :
                               p > 12 ? ScadaNode.NodeStatus.WARNING :
                               ScadaNode.NodeStatus.NORMAL);
            }
            case TANK_LEVEL -> {
                double l = node.getTankLevel() != null ? node.getTankLevel() : 50.0;
                l += (rng.nextDouble() - 0.505) * 0.3; // Very slow drain
                l = Math.max(0, Math.min(100, l));
                node.setTankLevel(l);
                node.setStatus(l < 10 ? ScadaNode.NodeStatus.ALARM :
                               l < 20 ? ScadaNode.NodeStatus.WARNING :
                               ScadaNode.NodeStatus.NORMAL);
            }
            case FLOW_METER -> {
                double f = node.getFlowRate() != null ? node.getFlowRate() : 250.0;
                f += (rng.nextDouble() - 0.5) * 5;
                f = Math.max(0, Math.min(500, f));
                node.setFlowRate(f);
            }
            case MOTOR_CONTROLLER -> {
                if (node.getMotorRunning() != null && node.getMotorRunning()) {
                    double rpm = node.getMotorRpm() != null ? node.getMotorRpm() : 1000.0;
                    rpm += (rng.nextDouble() - 0.5) * 20;
                    rpm = Math.max(900, Math.min(1500, rpm));
                    node.setMotorRpm(rpm);
                }
            }
            default -> {}
        }
        node.setLastUpdate(LocalDateTime.now());
    }

    private void updateRadioStatus(RadioDevice radio) {
        // Battery drain simulation
        if (radio.getBatteryLevel() != null) {
            double bat = radio.getBatteryLevel() - 0.01; // Very slow drain
            if (bat < 5) bat = 95; // Simulate charging
            radio.setBatteryLevel(bat);
        }

        // Signal strength fluctuation
        if (radio.getSignalStrength() != null) {
            double sig = radio.getSignalStrength() + (rng.nextDouble() - 0.5) * 2;
            sig = Math.max(-120, Math.min(-50, sig));
            radio.setSignalStrength(sig);
        }

        // Random status changes
        if (rng.nextDouble() < 0.05) {
            RadioDevice.RadioStatus[] statuses = {
                RadioDevice.RadioStatus.IDLE, RadioDevice.RadioStatus.IDLE,
                RadioDevice.RadioStatus.IDLE, RadioDevice.RadioStatus.TRANSMITTING,
                RadioDevice.RadioStatus.ONLINE
            };
            radio.setStatus(statuses[rng.nextInt(statuses.length)]);
        }

        radio.setLastSeen(LocalDateTime.now());
    }

    private void generateTelemetryEvent(RadioDevice radio) {
        ScadaNode node = scadaNodes.get(radio.getScadaNodeId());
        if (node == null) return;

        TelemetryEvent.EventSeverity severity = switch (node.getStatus()) {
            case ALARM -> TelemetryEvent.EventSeverity.ALARM;
            case WARNING -> TelemetryEvent.EventSeverity.WARNING;
            default -> TelemetryEvent.EventSeverity.INFO;
        };

        String desc = buildEventDescription(radio, node);
        TelemetryEvent event = TelemetryEvent.builder()
                .radioId(radio.getRadioId())
                .scadaNodeId(node.getNodeId())
                .eventType(TelemetryEvent.EventType.TELEMETRY_GPIO)
                .eventCode("SCADA_DATA")
                .description(desc)
                .sourceRadioAlias(radio.getRadioAlias())
                .talkgroupId(radio.getTalkgroupId())
                .timeslot(2) // TS2 for data in MOTOTRBO
                .severity(severity)
                .timestamp(LocalDateTime.now())
                .build();

        eventHistory.add(event);
        // Keep last 500 events
        if (eventHistory.size() > 500) {
            eventHistory.remove(0);
        }
    }

    private String buildEventDescription(RadioDevice radio, ScadaNode node) {
        return switch (node.getNodeType()) {
            case TEMPERATURE_SENSOR ->
                String.format("[%s] Temp: %.1f°C %s", node.getNodeId(), node.getTemperature(),
                    node.getAlarmActive() != null && node.getAlarmActive() ? "⚠ HIGH TEMP ALARM" : "OK");
            case PRESSURE_SENSOR ->
                String.format("[%s] Pressure: %.2f Bar", node.getNodeId(), node.getPressure());
            case TANK_LEVEL ->
                String.format("[%s] Level: %.1f%%", node.getNodeId(), node.getTankLevel());
            case MOTOR_CONTROLLER ->
                String.format("[%s] Motor: %.0f RPM %s", node.getNodeId(), node.getMotorRpm(),
                    node.getMotorRunning() ? "RUNNING" : "STOPPED");
            case FLOW_METER ->
                String.format("[%s] Flow: %.1f L/min", node.getNodeId(), node.getFlowRate());
            default ->
                String.format("[%s] Status: %s", node.getNodeId(), node.getStatus());
        };
    }

    private void broadcastSystemState() {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("timestamp", LocalDateTime.now().toString());
            state.put("radios", radios.values());
            state.put("scadaNodes", scadaNodes.values());
            state.put("repeaters", repeaters.values());
            state.put("recentEvents", eventHistory.size() > 20 ?
                    eventHistory.subList(eventHistory.size() - 20, eventHistory.size()) :
                    eventHistory);
            state.put("systemStats", buildSystemStats());

            String json = objectMapper.writeValueAsString(state);
            wsHandler.broadcast(json);
        } catch (Exception e) {
            // Log silently in simulator
        }
    }

    private Map<String, Object> buildSystemStats() {
        long onlineRadios = radios.values().stream()
                .filter(r -> r.getStatus() != RadioDevice.RadioStatus.OFFLINE).count();
        long alarmNodes = scadaNodes.values().stream()
                .filter(n -> n.getStatus() == ScadaNode.NodeStatus.ALARM).count();
        long activeRepeaters = repeaters.values().stream()
                .filter(r -> r.getStatus() == Repeater.RepeaterStatus.ACTIVE).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRadios", radios.size());
        stats.put("onlineRadios", onlineRadios);
        stats.put("totalScadaNodes", scadaNodes.size());
        stats.put("alarmNodes", alarmNodes);
        stats.put("activeRepeaters", activeRepeaters);
        stats.put("totalRepeaters", repeaters.size());
        stats.put("eventCount", eventHistory.size());
        return stats;
    }

    // Getters for controllers
    public Collection<RadioDevice> getAllRadios() { return radios.values(); }
    public Collection<ScadaNode> getAllScadaNodes() { return scadaNodes.values(); }
    public Collection<Repeater> getAllRepeaters() { return repeaters.values(); }
    public List<TelemetryEvent> getRecentEvents(int limit) {
        int size = eventHistory.size();
        return size > limit ? eventHistory.subList(size - limit, size) : new ArrayList<>(eventHistory);
    }
}
