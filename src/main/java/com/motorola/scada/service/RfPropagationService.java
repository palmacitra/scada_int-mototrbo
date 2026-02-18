package com.motorola.scada.service;

import org.springframework.stereotype.Service;

/**
 * RF propagation calculations using Okumura-Hata model
 * Industry standard for VHF/UHF land mobile radio coverage estimation.
 * Used by RF engineers for MOTOTRBO/DMR system planning.
 */
@Service
public class RfPropagationService {

    // MOTOTRBO frequency bands
    public static final double UHF_LOW_MHZ = 403.0;
    public static final double UHF_HIGH_MHZ = 470.0;
    public static final double VHF_HIGH_MHZ = 136.0;

    // Minimum acceptable signal levels for MOTOTRBO DMR
    public static final double DMR_SENSITIVITY_DBM = -116.0;   // XPR7000e typical
    public static final double MINIMUM_LINK_MARGIN_DB = 8.0;   // Conservative indoor/outdoor
    public static final double INDOOR_PENETRATION_LOSS_DB = 15.0; // Industrial building
    public static final double BODY_LOSS_DB = 3.0;

    /**
     * Okumura-Hata path loss model for suburban/open areas (Indonesian industrial terrain)
     * Valid for: 150-1500 MHz, ht = 30-200m, hr = 1-10m, d = 1-20km
     *
     * @param frequencyMhz Carrier frequency in MHz
     * @param txHeightM    Transmitter (base station / repeater) antenna height in meters
     * @param rxHeightM    Receiver (radio) antenna height in meters (typical: 1.5m handheld)
     * @param distanceKm   Distance in kilometers
     * @param environment  Terrain type
     * @return Path loss in dB
     */
    public double calculatePathLoss(double frequencyMhz, double txHeightM,
                                    double rxHeightM, double distanceKm,
                                    EnvironmentType environment) {
        // Mobile antenna height correction factor (a(hr))
        double aHr;
        if (frequencyMhz >= 300) { // UHF
            aHr = 3.2 * Math.pow(Math.log10(11.75 * rxHeightM), 2) - 4.97;
        } else { // VHF
            aHr = (1.1 * Math.log10(frequencyMhz) - 0.7) * rxHeightM
                    - (1.56 * Math.log10(frequencyMhz) - 0.8);
        }

        // Okumura-Hata base formula (urban)
        double luUrban = 69.55
                + 26.16 * Math.log10(frequencyMhz)
                - 13.82 * Math.log10(txHeightM)
                - aHr
                + (44.9 - 6.55 * Math.log10(txHeightM)) * Math.log10(distanceKm);

        // Environment correction
        double correction = switch (environment) {
            case URBAN -> 0.0;
            case SUBURBAN -> -2 * Math.pow(Math.log10(frequencyMhz / 28), 2) - 5.4;
            case OPEN_RURAL -> -4.78 * Math.pow(Math.log10(frequencyMhz), 2)
                    + 18.33 * Math.log10(frequencyMhz) - 40.94;
            case INDUSTRIAL_INDOOR -> +20.0; // Heavy industrial: extra penetration loss
            case INDUSTRIAL_OUTDOOR -> +5.0;  // Open industrial/warehouse
        };

        return luUrban + correction;
    }

    /**
     * Calculate effective coverage radius given transmitter EIRP and required signal level
     *
     * @param txPowerWatts       Transmitter power in Watts
     * @param txAntennaGainDbi   TX antenna gain in dBi
     * @param txCableLossDb      Cable/connector losses in dB
     * @param rxSensitivityDbm   Receiver sensitivity in dBm
     * @param linkMarginDb       Required link margin in dB
     * @param frequencyMhz       Frequency in MHz
     * @param txHeightM          Repeater tower height in meters
     * @return Coverage radius in kilometers
     */
    public double calculateCoverageRadius(double txPowerWatts, double txAntennaGainDbi,
                                          double txCableLossDb, double rxSensitivityDbm,
                                          double linkMarginDb, double frequencyMhz,
                                          double txHeightM, EnvironmentType environment) {
        // TX EIRP
        double txPowerDbm = 10 * Math.log10(txPowerWatts * 1000);
        double eirpDbm = txPowerDbm + txAntennaGainDbi - txCableLossDb;

        // Maximum allowable path loss
        double maplDb = eirpDbm - rxSensitivityDbm - linkMarginDb
                - BODY_LOSS_DB - INDOOR_PENETRATION_LOSS_DB;

        // Binary search for distance that gives this path loss
        double low = 0.1, high = 80.0;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) / 2;
            double pl = calculatePathLoss(frequencyMhz, txHeightM, 1.5, mid, environment);
            if (pl < maplDb) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    /**
     * Calculate link budget for a specific path
     */
    public LinkBudget calculateLinkBudget(double txPowerWatts, double txAntennaGainDbi,
                                           double txCableLossDb, double rxAntennaGainDbi,
                                           double rxCableLossDb, double pathLossDb,
                                           double rxSensitivityDbm) {
        double txPowerDbm = 10 * Math.log10(txPowerWatts * 1000);
        double eirpDbm = txPowerDbm + txAntennaGainDbi - txCableLossDb;
        double rslDbm = eirpDbm - pathLossDb + rxAntennaGainDbi - rxCableLossDb;
        double marginDb = rslDbm - rxSensitivityDbm;

        return new LinkBudget(txPowerDbm, eirpDbm, pathLossDb, rslDbm, rxSensitivityDbm, marginDb);
    }

    /**
     * Estimate number of repeaters needed for given area
     * Assumes hexagonal cell coverage pattern
     */
    public int estimateRepeatersNeeded(double totalAreaHa, double coverageRadiusKm) {
        // Hexagonal cell area formula
        double cellAreaHa = 2.598 * Math.pow(coverageRadiusKm * 1000, 2) / 10000.0;
        // Add 20% overlap factor for seamless coverage
        double effectiveCellArea = cellAreaHa * 0.80;
        return (int) Math.ceil(totalAreaHa / effectiveCellArea);
    }

    /**
     * Calculate channel capacity for MOTOTRBO DMR trunking (Capacity Plus / Linked Capacity Plus)
     * Based on Erlang-B traffic model
     */
    public ChannelCapacity calculateCapacity(int numTimeslots, int numUsers,
                                              double callsPerHourPerUser, double avgCallDurationSec) {
        double trafficErlang = numUsers * callsPerHourPerUser * (avgCallDurationSec / 3600.0);
        double utilizationPercent = (trafficErlang / numTimeslots) * 100;

        // Grade of Service (GoS) approximation - simplified Erlang-B
        // For accurate result, full Erlang-B table lookup would be needed
        double gos = Math.exp(trafficErlang - numTimeslots * Math.log(numTimeslots));

        return new ChannelCapacity(numTimeslots, numUsers, trafficErlang, utilizationPercent, gos);
    }

    public record LinkBudget(
            double txPowerDbm,
            double eirpDbm,
            double pathLossDb,
            double receivedSignalLevelDbm,
            double sensitivityDbm,
            double linkMarginDb
    ) {
        public boolean isAdequate() { return linkMarginDb >= MINIMUM_LINK_MARGIN_DB; }
    }

    public record ChannelCapacity(
            int timeslots,
            int users,
            double trafficErlang,
            double utilizationPercent,
            double gradeOfService
    ) {}

    public enum EnvironmentType {
        URBAN,
        SUBURBAN,
        OPEN_RURAL,
        INDUSTRIAL_INDOOR,
        INDUSTRIAL_OUTDOOR
    }
}
