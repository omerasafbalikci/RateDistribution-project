package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.RegimeDefinition;
import com.ratedistribution.rdp.config.SessionVolFactor;
import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.RateUpdateResult;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.*;
import com.ratedistribution.rdp.service.abstracts.HolidayCalendarService;
import com.ratedistribution.rdp.service.abstracts.RateSimulatorService;
import com.ratedistribution.rdp.utilities.CorrelatedRandomVectorGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Log4j2
public class RateSimulatorServiceImpl implements RateSimulatorService {
    private final SimulatorProperties simulatorProperties;
    private final RedisTemplate<String, AssetState> assetStateRedisTemplate;
    private final HolidayCalendarService holidayCalendarService;
    private HashOperations<String, String, AssetState> stateOps;
    private CorrelatedRandomVectorGenerator correlatedRng;

    @PostConstruct
    public void init() {
        this.stateOps = assetStateRedisTemplate.opsForHash();

        List<List<Double>> mat = simulatorProperties.getCorrelationMatrix();
        int n = mat.size();
        double[][] corrArray = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                corrArray[i][j] = mat.get(i).get(j);
            }
        }
        correlatedRng = new CorrelatedRandomVectorGenerator(corrArray);

        simulatorProperties.getRates().forEach(def -> {
            AssetState existing = stateOps.get("ASSET_STATES", def.getRateName());
            if (existing == null) {
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
                stateOps.put("ASSET_STATES", def.getRateName(), initState);
            }
        });
    }

    @Override
    public List<RateDataResponse> updateAllRates() {
        List<MultiRateDefinition> definitions = simulatorProperties.getRates();
        int n = definitions.size();

        double dt = simulatorProperties.getUpdateIntervalMillis() / (1000.0 * 3600.0 * 24.0);

        double[] epsVector = correlatedRng.sample();
        List<RateDataResponse> responseList = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            MultiRateDefinition def = definitions.get(i);
            String rateName = def.getRateName();
            AssetState oldState = stateOps.get("ASSET_STATES", rateName);
            if (oldState == null) {
                long now = System.currentTimeMillis();
                oldState = new AssetState(
                        def.getInitialPrice(),
                        0.01,
                        0.0,
                        def.getInitialPrice(),
                        def.getInitialPrice(),
                        def.getInitialPrice(),
                        0L,
                        VolRegime.LOW_VOL,
                        0,
                        now,
                        LocalDate.now(ZoneOffset.UTC)
                );
            }

            long nowMillis = System.currentTimeMillis();
            LocalDateTime nowLdt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC);

            AssetState modState = handleMarketCloseScenarios(oldState, nowMillis, nowLdt);

            RegimeStatus newRegimeStatus = updateRegime(modState.getCurrentRegime(),
                    modState.getStepsInRegime());

            RateUpdateResult result = updateRate(def, modState, dt, epsVector[i], nowLdt, newRegimeStatus);
            stateOps.put("ASSET_STATES", rateName, result.newState());

            responseList.add(result.response());
        }
        return responseList;
    }

    private AssetState handleMarketCloseScenarios(AssetState oldState, long nowMillis, LocalDateTime nowLdt) {
        boolean isHolidayNow = holidayCalendarService.isHoliday(nowLdt);
        boolean isWeekendNow = isWeekend(nowLdt.getDayOfWeek());

        long lastMillis = oldState.getLastUpdateEpochMillis();
        LocalDateTime lastLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastMillis), ZoneOffset.UTC);
        boolean wasWeekend = isWeekend(lastLdt.getDayOfWeek());
        boolean wasHoliday = holidayCalendarService.isHoliday(lastLdt);

        if ((wasWeekend || wasHoliday) && (!isWeekendNow && !isHolidayNow)) {
            double gapMean = simulatorProperties.getWeekendHandling().getWeekendGapJumpMean();
            double gapVol = simulatorProperties.getWeekendHandling().getWeekendGapJumpVol();
            double jump = ThreadLocalRandom.current().nextGaussian() * gapVol + gapMean;
            return applyGapJump(oldState, nowMillis, jump);
        } else if (isWeekendNow || isHolidayNow) {
            return oldState;
        } else {
            oldState.setLastUpdateEpochMillis(nowMillis);
            return oldState;
        }
    }

    private AssetState applyGapJump(AssetState oldState, long nowMillis, double jump) {
        double newPrice = oldState.getCurrentPrice() * Math.exp(jump);
        if (newPrice < 0.0001) newPrice = 0.0001;

        return new AssetState(
                newPrice,
                oldState.getCurrentSigma(),
                0.0,
                newPrice,
                newPrice,
                newPrice,
                0L,
                oldState.getCurrentRegime(),
                oldState.getStepsInRegime(),
                nowMillis,
                oldState.getCurrentDay()
        );
    }

    private boolean isWeekend(DayOfWeek dow) {
        return (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
    }

    private RegimeStatus updateRegime(VolRegime currentRegime, int stepsInRegime) {
        if (!simulatorProperties.isEnableRegimeSwitching()) {
            return new RegimeStatus(currentRegime, stepsInRegime + 1);
        }
        RegimeDefinition lowVol = simulatorProperties.getRegimeLowVol();
        RegimeDefinition highVol = simulatorProperties.getRegimeHighVol();

        VolRegime newRegime = currentRegime;
        int newSteps = stepsInRegime + 1;

        if (currentRegime == VolRegime.LOW_VOL) {
            double prob = lowVol.getTransitionProb();
            if (newSteps >= lowVol.getMeanDuration() && Math.random() < prob) {
                newRegime = VolRegime.HIGH_VOL;
                newSteps = 0;
            }
        } else {
            double prob = highVol.getTransitionProb();
            if (newSteps >= highVol.getMeanDuration() && Math.random() < prob) {
                newRegime = VolRegime.LOW_VOL;
                newSteps = 0;
            }
        }
        return new RegimeStatus(newRegime, newSteps);
    }

    private RateUpdateResult updateRate(MultiRateDefinition def, AssetState oldState, double dt, double z, LocalDateTime nowLdt, RegimeStatus regimeStatus) {
        double oldPrice = oldState.getCurrentPrice();
        double oldSigma = oldState.getCurrentSigma();
        double oldRet = oldState.getLastReturn();

        LocalDate oldDay = oldState.getCurrentDay();
        LocalDate nowDate = nowLdt.toLocalDate();

        double dayOpen = oldState.getDayOpen();
        double dayHigh = oldState.getDayHigh();
        double dayLow = oldState.getDayLow();
        long dayVol = oldState.getDayVolume();

        if (!nowDate.equals(oldDay)) {
            dayOpen = oldPrice;
            dayHigh = oldPrice;
            dayLow = oldPrice;
            dayVol = 0L;
        }

        double newSigma = calculateGarchVolatility(def, oldRet, oldSigma);

        double volScale = (regimeStatus.getCurrentRegime() == VolRegime.HIGH_VOL)
                ? simulatorProperties.getRegimeHighVol().getVolScale()
                : simulatorProperties.getRegimeLowVol().getVolScale();

        double sessionMult = getSessionVolMultiplier(nowLdt);

        double effectiveSigma = newSigma * Math.sqrt(dt) * volScale * sessionMult;

        double normShock = effectiveSigma * z;

        double driftPart = def.getDrift() * dt;

        double jumpPart = calculateJump(def, dt);

        double mrPart = (def.isUseMeanReversion()) ? calculateMeanReversion(oldPrice, def, dt) : 0.0;

        double logReturn = driftPart + normShock + jumpPart + mrPart;
        double newPrice = oldPrice * Math.exp(logReturn);
        if (newPrice < 0.0001) newPrice = 0.0001;

        double spr = def.getBaseSpread();
        double bid = Math.max(newPrice - spr / 2.0, 0.0001);
        double ask = bid + spr;
        double mid = (bid + ask) / 2.0;

        long nowMillis = System.currentTimeMillis();
        long timeDiffMs = nowMillis - oldState.getLastUpdateEpochMillis();

        double ratio = (double) timeDiffMs / simulatorProperties.getUpdateIntervalMillis();
        if (ratio < 1.0) ratio = 1.0;
        if (ratio > 5.0) ratio = 5.0;

        long tickVol = ThreadLocalRandom.current().nextInt(10, 50);
        long scaledTickVol = (long) (tickVol * ratio);

        long newDayVol = dayVol + scaledTickVol;

        dayHigh = Math.max(dayHigh, mid);
        dayLow = Math.min(dayLow, mid);

        double dayChangeVal = mid - dayOpen;
        double changePct = 0.0;
        if (dayOpen > 0.0) {
            changePct = (dayChangeVal / dayOpen) * 100.0;
        }

        RateDataResponse resp = new RateDataResponse();
        resp.setRateName(def.getRateName());
        resp.setTimestamp(nowLdt);
        resp.setBid(BigDecimal.valueOf(bid));
        resp.setAsk(BigDecimal.valueOf(ask));
        resp.setDayOpen(BigDecimal.valueOf(dayOpen));
        resp.setDayHigh(BigDecimal.valueOf(dayHigh));
        resp.setDayLow(BigDecimal.valueOf(dayLow));
        resp.setDayChange(BigDecimal.valueOf(dayChangeVal));
        resp.setDayChangePercent(BigDecimal.valueOf(changePct));
        resp.setDayVolume(newDayVol);
        resp.setLastTickVolume(scaledTickVol);

        AssetState newState = new AssetState(
                newPrice,
                newSigma,
                logReturn,
                dayOpen,
                dayHigh,
                dayLow,
                newDayVol,
                regimeStatus.getCurrentRegime(),
                regimeStatus.getStepsInRegime(),
                nowMillis,
                nowDate
        );
        return new RateUpdateResult(newState, resp);
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

    private double calculateMeanReversion(double oldPrice, MultiRateDefinition def, double dt) {
        double kappa = def.getKappa();
        double theta = def.getTheta();
        double logP = Math.log(oldPrice);
        double logT = Math.log(theta);
        return kappa * (logT - logP) * dt;
    }

    private double getSessionVolMultiplier(LocalDateTime now) {
        if (simulatorProperties.getSessionVolFactors() == null || simulatorProperties.getSessionVolFactors().isEmpty()) {
            return 1.0;
        }
        int hour = now.getHour();
        for (SessionVolFactor f : simulatorProperties.getSessionVolFactors()) {
            if (hour >= f.getStartHour() && hour < f.getEndHour()) {
                return f.getVolMultiplier();
            }
        }
        return 1.0;
    }
}
