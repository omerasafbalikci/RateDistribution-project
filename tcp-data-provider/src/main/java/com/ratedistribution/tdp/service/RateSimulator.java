package com.ratedistribution.tdp.service;

import com.ratedistribution.tdp.config.RegimeDefinition;
import com.ratedistribution.tdp.config.SessionVolFactor;
import com.ratedistribution.tdp.config.SimulatorProperties;
import com.ratedistribution.tdp.model.AssetState;
import com.ratedistribution.tdp.model.MultiRateDefinition;
import com.ratedistribution.tdp.model.RegimeStatus;
import com.ratedistribution.tdp.model.VolRegime;
import com.ratedistribution.tdp.utilities.CorrelatedRandomVectorGenerator;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RateSimulator {
    private final SimulatorProperties simulatorProps;
    private final HolidayCalendarService holidayCalendarService;
    private final CorrelatedRandomVectorGenerator correlatedRng;
    private final Map<String, AssetState> stateMap = new ConcurrentHashMap<>();

    public RateSimulator(SimulatorProperties props, HolidayCalendarService holidayCalendarService) {
        this.simulatorProps = props;
        this.holidayCalendarService = holidayCalendarService;

        List<List<Double>> mat = props.getCorrelationMatrix();
        int n = mat.size();
        double[][] corrArray = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                corrArray[i][j] = mat.get(i).get(j);
            }
        }
        this.correlatedRng = new CorrelatedRandomVectorGenerator(corrArray);

        props.getRates().forEach(def -> {
            long nowEpoch = System.currentTimeMillis();
            AssetState initState = new AssetState(
                    def.getInitialPrice(),
                    0.01,
                    0.0,
                    def.getInitialPrice(),
                    def.getInitialPrice(),
                    def.getInitialPrice(),
                    0L,
                    VolRegime.LOW_VOL,
                    0,
                    nowEpoch,
                    LocalDate.now(ZoneOffset.UTC)
            );
            stateMap.put(def.getRateName(), initState);
        });
    }

    public List<String> updateAllRates() {
        var resultLines = new java.util.ArrayList<String>();
        double dt = simulatorProps.getUpdateIntervalMillis() / (1000.0 * 3600.0 * 24.0);

        double[] epsVector = correlatedRng.sample();
        List<MultiRateDefinition> definitions = simulatorProps.getRates();

        for (int i = 0; i < definitions.size(); i++) {
            MultiRateDefinition def = definitions.get(i);
            String rateName = def.getRateName();
            AssetState oldState = stateMap.get(rateName);
            if (oldState == null) continue;

            long nowMillis = System.currentTimeMillis();
            LocalDateTime nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC);

            AssetState modState = handleMarketCloseScenarios(oldState, nowLdt);

            RegimeStatus newRegime = updateRegime(modState.getCurrentRegime(), modState.getStepsInRegime());

            var update = updateRate(def, modState, dt, epsVector[i], nowLdt, newRegime);

            stateMap.put(rateName, update.newState());

            resultLines.add(update.rateLine());
        }
        return resultLines;
    }

    private AssetState handleMarketCloseScenarios(AssetState oldState, LocalDateTime nowLdt) {
        long nowMillis = System.currentTimeMillis();
        if (isWeekend(nowLdt.getDayOfWeek()) || isHoliday(nowLdt)) {
            return oldState;
        }

        long lastMillis = oldState.getLastUpdateEpochMillis();
        LocalDateTime lastLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastMillis), ZoneOffset.UTC);
        if (isWeekend(lastLdt.getDayOfWeek()) || isHoliday(lastLdt)) {
            double gapMean = simulatorProps.getWeekendHandling().getWeekendGapJumpMean();
            double gapVol = simulatorProps.getWeekendHandling().getWeekendGapJumpVol();
            double jump = ThreadLocalRandom.current().nextGaussian() * gapVol + gapMean;
            double newPrice = oldState.getCurrentPrice() * Math.exp(jump);
            if (newPrice < 0.0001) newPrice = 0.0001;
            oldState.setCurrentPrice(newPrice);
        }
        oldState.setLastUpdateEpochMillis(nowMillis);
        return oldState;
    }

    private boolean isWeekend(DayOfWeek dow) {
        return (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
    }

    private boolean isHoliday(LocalDateTime ldt) {
        return (holidayCalendarService != null) && holidayCalendarService.isHoliday(ldt);
    }

    private RegimeStatus updateRegime(VolRegime current, int steps) {
        if (!simulatorProps.isEnableRegimeSwitching()) {
            return new RegimeStatus(current, steps + 1);
        }
        RegimeDefinition low = simulatorProps.getRegimeLowVol();
        RegimeDefinition high = simulatorProps.getRegimeHighVol();
        VolRegime newRegime = current;
        int newSteps = steps + 1;

        if (current == VolRegime.LOW_VOL) {
            if (newSteps >= low.getMeanDuration() && Math.random() < low.getTransitionProb()) {
                newRegime = VolRegime.HIGH_VOL;
                newSteps = 0;
            }
        } else {
            if (newSteps >= high.getMeanDuration() && Math.random() < high.getTransitionProb()) {
                newRegime = VolRegime.LOW_VOL;
                newSteps = 0;
            }
        }
        return new RegimeStatus(newRegime, newSteps);
    }

    private record RateUpdateResult(AssetState newState, String rateLine) {
    }

    private RateUpdateResult updateRate(MultiRateDefinition def,
                                        AssetState oldState,
                                        double dt,
                                        double z,
                                        LocalDateTime nowLdt,
                                        RegimeStatus newRegime) {
        double oldPrice = oldState.getCurrentPrice();
        double oldSigma = oldState.getCurrentSigma();
        double oldRet = oldState.getLastReturn();

        LocalDate oldDay = oldState.getCurrentDay();
        LocalDate nowDay = nowLdt.toLocalDate();

        double dayOpen = oldState.getDayOpen();
        double dayHigh = oldState.getDayHigh();
        double dayLow = oldState.getDayLow();
        long dayVol = oldState.getDayVolume();

        if (!nowDay.equals(oldDay)) {
            dayOpen = Math.max(oldPrice, 0.0001);
            dayHigh = dayOpen;
            dayLow = dayOpen;
            dayVol = 0L;
        }

        double newSigma = calculateGarchVolatility(def, oldRet, oldSigma);

        double volScale = (newRegime.getCurrentRegime() == VolRegime.HIGH_VOL)
                ? simulatorProps.getRegimeHighVol().getVolScale()
                : simulatorProps.getRegimeLowVol().getVolScale();
        double sessionMult = getSessionVolMultiplier(nowLdt);
        double effectiveSigma = newSigma * Math.sqrt(dt) * volScale * sessionMult;

        double normShock = effectiveSigma * z;
        double driftPart = def.getDrift() * dt;
        double jumpPart = calculateJump(def, dt);
        double mrPart = (def.isUseMeanReversion()) ? calcMeanReversion(oldPrice, def, dt) : 0.0;

        double logReturn = driftPart + normShock + jumpPart + mrPart;
        double newPrice = oldPrice * Math.exp(logReturn);
        if (newPrice < 0.0001) newPrice = 0.0001;

        double spr = def.getBaseSpread();
        double bid = Math.max(newPrice - spr / 2.0, 0.0001);
        double ask = bid + spr;
        double mid = 0.5 * (bid + ask);

        long nowMs = System.currentTimeMillis();
        long timeDiff = nowMs - oldState.getLastUpdateEpochMillis();
        double ratio = (double) timeDiff / simulatorProps.getUpdateIntervalMillis();
        if (ratio < 1.0) ratio = 1.0;
        if (ratio > 5.0) ratio = 5.0;

        long tickVol = ThreadLocalRandom.current().nextInt(10, 50);
        long scaledTickVol = (long) (tickVol * ratio);
        long newDayVol = dayVol + scaledTickVol;

        dayHigh = Math.max(dayHigh, mid);
        dayLow = Math.min(dayLow, mid);

        double dayChangeVal = mid - dayOpen;
        double dayChangePct = (dayOpen > 0.0) ? (dayChangeVal / dayOpen) * 100.0 : 0.0;

        AssetState newState = new AssetState(
                newPrice,
                newSigma,
                logReturn,
                dayOpen,
                dayHigh,
                dayLow,
                newDayVol,
                newRegime.getCurrentRegime(),
                newRegime.getStepsInRegime(),
                nowMs,
                nowDay
        );

        ZonedDateTime zdt = nowLdt.atZone(ZoneOffset.UTC);
        String timeStr = zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

        String line = String.format(
                "%s"
                        + "|bid:number:%.6f|ask:number:%.6f|timestamp:datetime:%s"
                        + "|open:number:%.6f|high:number:%.6f|low:number:%.6f"
                        + "|change:number:%.6f|changePercent:percent:%.4f"
                        + "|dayVolume:long:%d|lastTickVolume:long:%d",
                def.getRateName(),
                bid,
                ask,
                timeStr,
                dayOpen,
                dayHigh,
                dayLow,
                dayChangeVal,
                dayChangePct,
                newDayVol,
                scaledTickVol
        );

        return new RateUpdateResult(newState, line);
    }

    private double calculateGarchVolatility(MultiRateDefinition def, double oldRet, double oldSigma) {
        double omega = def.getGarchParams().getOmega();
        double alpha = def.getGarchParams().getAlpha();
        double beta = def.getGarchParams().getBeta();

        double newSigmaSq = omega + alpha * (oldRet * oldRet) + beta * (oldSigma * oldSigma);
        if (newSigmaSq < 1e-16) newSigmaSq = 1e-16;
        return Math.sqrt(newSigmaSq);
    }

    private double calculateJump(MultiRateDefinition def, double dt) {
        double lambda = def.getJumpIntensity();
        double probJump = 1 - Math.exp(-lambda * dt);
        if (Math.random() < probJump) {
            return ThreadLocalRandom.current().nextGaussian() * def.getJumpVol() + def.getJumpMean();
        }
        return 0.0;
    }

    private double calcMeanReversion(double oldPrice, MultiRateDefinition def, double dt) {
        double kappa = def.getKappa();
        double theta = def.getTheta();
        double logP = Math.log(oldPrice);
        double logT = Math.log(theta);
        return kappa * (logT - logP) * dt;
    }

    private double getSessionVolMultiplier(LocalDateTime ldt) {
        var list = simulatorProps.getSessionVolFactors();
        if (list == null || list.isEmpty()) return 1.0;
        int hour = ldt.getHour();
        for (SessionVolFactor f : list) {
            if (hour >= f.getStartHour() && hour < f.getEndHour()) {
                return f.getVolMultiplier();
            }
        }
        return 1.0;
    }
}
