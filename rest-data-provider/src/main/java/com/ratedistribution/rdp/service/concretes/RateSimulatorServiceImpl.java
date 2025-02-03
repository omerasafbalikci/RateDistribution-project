package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.*;
import com.ratedistribution.rdp.utilities.CorrelatedRandomVectorGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
public class RateSimulatorServiceImpl {

    private final SimulatorProperties simulatorProperties;
    private final RedisTemplate<String, AssetState> assetStateRedisTemplate;

    private HashOperations<String, String, AssetState> stateOps;
    private CorrelatedRandomVectorGenerator correlatedRng;

    @PostConstruct
    public void init() {
        this.stateOps = assetStateRedisTemplate.opsForHash();

        // correlationMatrix -> double[][]
        List<List<Double>> mat = simulatorProperties.getCorrelationMatrix();
        int n = mat.size();
        double[][] corrArray = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                corrArray[i][j] = mat.get(i).get(j);
            }
        }
        correlatedRng = new CorrelatedRandomVectorGenerator(corrArray);

        // initial states
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
                        VolRegime.LOW_VOL, // default
                        0,
                        nowEpoch,
                        LocalDate.now(ZoneOffset.UTC)
                );
                stateOps.put("ASSET_STATES", def.getRateName(), initState);
            }
        });
    }

    public List<RateDataResponse> updateAllRates() {
        List<MultiRateDefinition> definitions = simulatorProperties.getRates();
        int n = definitions.size();

        double dt = simulatorProperties.getUpdateIntervalMillis()
                / (1000.0 * 3600.0 * 24.0);

        double[] epsVector = correlatedRng.sample(); // dimension = n
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

            // Güncel saati bul
            long nowMillis = System.currentTimeMillis();
            LocalDateTime nowLdt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC);

            // Weekend kontrol
            AssetState modState = handleWeekendGap(def, oldState, nowMillis, nowLdt);

            // Rejim güncelle
            RegimeStatus newRegimeStatus = updateRegime(modState.getCurrentRegime(),
                    modState.getStepsInRegime());

            // asıl update
            RateUpdateResult result = updateRate(def, modState, dt, epsVector[i], nowLdt, newRegimeStatus);
            stateOps.put("ASSET_STATES", rateName, result.getNewState());

            responseList.add(result.getResponse());
        }
        return responseList;
    }

    /**
     * Weekend gap mantığı:
     * - Eğer "weekendHandling.enabled" = true,
     * - Eski update ile şimdi arasında cumartesi-pazar farkı varsa
     * "weekend gap jump" ekle, price'a yansıt
     * - Örnek basit yaklaşım: dayOfWeek in [SAT,SUN] => skip updates,
     * pazartesi sabah  => jump
     */
    private AssetState handleWeekendGap(MultiRateDefinition def, AssetState oldState,
                                        long nowMillis, LocalDateTime nowLdt) {
        if (!simulatorProperties.getWeekendHandling().isEnabled()) {
            return oldState;
        }
        // Eski update
        long lastMillis = oldState.getLastUpdateEpochMillis();
        LocalDateTime lastLdt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(lastMillis), ZoneOffset.UTC);

        // eğer "lastLdt < Saturday" ve "nowLdt >= Monday" ... gibi
        // basit bir approach
        DayOfWeek lastDow = lastLdt.getDayOfWeek();
        DayOfWeek nowDow = nowLdt.getDayOfWeek();

        if (isWeekendRange(lastDow) && !isWeekendRange(nowDow)) {
            // Pazartesi açılış
            double gapMean = simulatorProperties.getWeekendHandling().getWeekendGapJumpMean();
            double gapVol = simulatorProperties.getWeekendHandling().getWeekendGapJumpVol();
            double jump = ThreadLocalRandom.current().nextGaussian() * gapVol + gapMean;
            double newPrice = oldState.getCurrentPrice() * Math.exp(jump);
            if (newPrice < 0.0001) newPrice = 0.0001;

            // dayOpen = newPrice (hafta yeni bir seans gibi)
            // dayHigh=dayLow=newPrice, dayVolume=0
            AssetState gapState = new AssetState(
                    newPrice, oldState.getCurrentSigma(), 0.0,
                    newPrice, newPrice, newPrice, 0L,
                    oldState.getCurrentRegime(), oldState.getStepsInRegime(),
                    nowMillis,
                    oldState.getCurrentDay()
            );
            return gapState;
        }
        // aksi halde bir değişiklik yapma, lastUpdateEpochMillis'i yenile
        oldState.setLastUpdateEpochMillis(nowMillis);
        return oldState;
    }

    private boolean isWeekendRange(DayOfWeek dow) {
        // basit assume: SAT=6, SUN=7
        return (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
    }

    /**
     * Basit 2 rejimli approach:
     * - eğer currentRegime=LOW_VOL, stepsInRegime++ > meanDuration(LOW_VOL) ve random<transitionProb => HIGH_VOL
     * - benzeri HIGH_VOL => LOW_VOL
     */
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
            // high vol
            double prob = highVol.getTransitionProb();
            if (newSteps >= highVol.getMeanDuration() && Math.random() < prob) {
                newRegime = VolRegime.LOW_VOL;
                newSteps = 0;
            }
        }
        return new RegimeStatus(newRegime, newSteps);
    }

    /**
     * Asıl fiyat güncellemesi: GARCH + jump + mean reversion +
     * regimeVolScale + sessionVolFactor
     */
    private RateUpdateResult updateRate(MultiRateDefinition def, AssetState oldState,
                                        double dt, double z,
                                        LocalDateTime nowLdt,
                                        RegimeStatus regimeStatus) {
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
            dayLow  = oldPrice;
            dayVol  = 0L;
        }

        // GARCH
        double newSigma = calculateGarchVolatility(def, oldRet, oldSigma);

        // Rejim vol scale
        double volScale = (regimeStatus.getCurrentRegime() == VolRegime.HIGH_VOL)
                ? simulatorProperties.getRegimeHighVol().getVolScale()
                : simulatorProperties.getRegimeLowVol().getVolScale();

        // Session factor
        double sessionMult = getSessionVolMultiplier(nowLdt);

        double effectiveSigma = newSigma * Math.sqrt(dt) * volScale * sessionMult;

        // Normal shock
        double normShock = effectiveSigma * z;

        // drift
        double driftPart = def.getDrift() * dt;

        // jump
        double jumpPart = calculateJump(def, dt);

        // mean reversion
        double mrPart    = (def.isUseMeanReversion()) ? calculateMeanReversion(oldPrice, def, dt) : 0.0;

        double logReturn = driftPart + normShock + jumpPart + mrPart;
        double newPrice = oldPrice * Math.exp(logReturn);
        if (newPrice < 0.0001) newPrice = 0.0001;

        // spread
        double spr = def.getBaseSpread();
        double bid = Math.max(newPrice - spr / 2.0, 0.0001);
        double ask = bid + spr;
        double mid = (bid + ask) / 2.0;

        long nowMillis = System.currentTimeMillis();
        long timeDiffMs = nowMillis - oldState.getLastUpdateEpochMillis();

        double ratio = (double) timeDiffMs / simulatorProperties.getUpdateIntervalMillis();
        if (ratio < 1.0) ratio = 1.0; // minimum 1
        if (ratio > 5.0) ratio = 5.0;

        long tickVol = ThreadLocalRandom.current().nextInt(10, 50);
        long scaledTickVol = (long) (tickVol * ratio);

        long newDayVol = dayVol + scaledTickVol;

        // dayHigh / dayLow / volume
        dayHigh = Math.max(dayHigh, mid);
        dayLow = Math.min(dayLow, mid);

        double dayChangeVal = mid - dayOpen;
        double changePct    = 0.0;
        if (dayOpen > 0.0) {
            changePct = (dayChangeVal / dayOpen) * 100.0;
        }
        // RateDataResponse doldur:
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

        // Yeni state
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
                nowDate  // gün bilgisi
        );

        return new RateUpdateResult(newState, resp);
    }

    // ========== Yardımcı Metotlar ============

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
            double j = ThreadLocalRandom.current().nextGaussian() * def.getJumpVol() + def.getJumpMean();
            return j;
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
        if (simulatorProperties.getSessionVolFactors() == null ||
                simulatorProperties.getSessionVolFactors().isEmpty()) {
            return 1.0;
        }
        int hour = now.getHour(); // 0..23
        for (SessionVolFactor f : simulatorProperties.getSessionVolFactors()) {
            if (hour >= f.getStartHour() && hour < f.getEndHour()) {
                return f.getVolMultiplier();
            }
        }
        return 1.0;
    }

    // Basit result container
    private static class RateUpdateResult {
        private final AssetState newState;
        private final RateDataResponse response;

        public RateUpdateResult(AssetState newState, RateDataResponse response) {
            this.newState = newState;
            this.response = response;
        }

        public AssetState getNewState() {
            return newState;
        }

        public RateDataResponse getResponse() {
            return response;
        }
    }
}
