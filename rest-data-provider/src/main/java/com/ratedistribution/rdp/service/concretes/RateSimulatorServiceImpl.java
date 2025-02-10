package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.model.RegimeDefinition;
import com.ratedistribution.rdp.model.SessionVolFactor;
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

        // Korelasyon matrisinden L elde ediyoruz
        List<List<Double>> mat = simulatorProperties.getCorrelationMatrix();
        double[][] corrArray = to2DArray(mat);
        correlatedRng = new CorrelatedRandomVectorGenerator(corrArray);

        // Redis'te yoksa ilk AssetState yarat
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
        List<RateDataResponse> responseList = new ArrayList<>();
        List<MultiRateDefinition> definitions = simulatorProperties.getRates();

        // dt: 1 gün ~ 24 saat => updateIntervalMillis'e göre fraksiyon
        double dt = simulatorProperties.getUpdateIntervalMillis() / (1000.0 * 3600.0 * 24.0);

        // Korelasyonlu gauss random
        double[] epsVector = correlatedRng.sample();

        for (int i = 0; i < definitions.size(); i++) {
            MultiRateDefinition def = definitions.get(i);

            // Mevcut state çek
            AssetState oldState = stateOps.get("ASSET_STATES", def.getRateName());
            if (oldState == null) {
                oldState = createInitialState(def);
            }

            long nowMillis = System.currentTimeMillis();
            LocalDateTime nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC);

            // Hafta sonu / tatil senaryosu
            AssetState modState = handleMarketCloseScenarios(oldState, nowMillis, nowLdt);

            // Rejim güncelle (Markov veya basit)
            RegimeStatus newRegimeStatus;
            if (simulatorProperties.isUseMarkovSwitching()) {
                newRegimeStatus = updateRegimeMarkov(modState.getCurrentRegime(), modState.getStepsInRegime());
            } else {
                newRegimeStatus = updateRegimeSimple(modState.getCurrentRegime(), modState.getStepsInRegime());
            }

            // Kur hesaplaması
            RateUpdateResult result = updateRate(def, modState, dt, epsVector[i], nowLdt, newRegimeStatus);

            // Yeni state'i Redis'e yaz
            stateOps.put("ASSET_STATES", def.getRateName(), result.newState());

            // Response listesine ekle
            responseList.add(result.response());
        }

        return responseList;
    }

    // -------------------------------------
    // Yardımcı metotlar
    // -------------------------------------

    private double[][] to2DArray(List<List<Double>> mat) {
        int n = mat.size();
        double[][] arr = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                arr[i][j] = mat.get(i).get(j);
            }
        }
        return arr;
    }

    private AssetState createInitialState(MultiRateDefinition def) {
        long nowEpoch = System.currentTimeMillis();
        return new AssetState(
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
    }

    /**
     * Piyasa kapalı senaryolarını ele alır.
     * - Eğer yeni açılan seans bir önceki hafta sonu veya tatilden çıkış ise gap oluşur
     * - Piyasa kapalı ise state'i değiştirmeden döner
     */
    private AssetState handleMarketCloseScenarios(AssetState oldState, long nowMillis, LocalDateTime nowLdt) {
        boolean isHolidayNow = holidayCalendarService.isHoliday(nowLdt);
        boolean isWeekendNow = isWeekend(nowLdt.getDayOfWeek());

        long lastMillis = oldState.getLastUpdateEpochMillis();
        LocalDateTime lastLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastMillis), ZoneOffset.UTC);

        boolean wasWeekend = isWeekend(lastLdt.getDayOfWeek());
        boolean wasHoliday = holidayCalendarService.isHoliday(lastLdt);

        // Seans tekrar açıldıysa, gap jump
        if ((wasWeekend || wasHoliday) && (!isWeekendNow && !isHolidayNow)) {
            if (simulatorProperties.getWeekendHandling().isEnabled()) {
                double gapMean = simulatorProperties.getWeekendHandling().getWeekendGapJumpMean();
                double gapVol  = simulatorProperties.getWeekendHandling().getWeekendGapJumpVol();
                double jump = ThreadLocalRandom.current().nextGaussian() * gapVol + gapMean;
                return applyGapJump(oldState, nowMillis, jump);
            }
        }
        // Eğer şu an weekend veya holiday ise güncelleme yok
        if (isWeekendNow || isHolidayNow) {
            return oldState;
        }

        // Normal piyasa
        oldState.setLastUpdateEpochMillis(nowMillis);
        return oldState;
    }

    private boolean isWeekend(DayOfWeek dow) {
        return (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
    }

    private AssetState applyGapJump(AssetState oldState, long nowMillis, double jump) {
        double newPrice = oldState.getCurrentPrice() * Math.exp(jump);
        if (newPrice < 0.0001) {
            newPrice = 0.0001;
        }
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

    /**
     * Basit rejim geçişi: lowVol->highVol olasılığı vs.
     */
    private RegimeStatus updateRegimeSimple(VolRegime currentRegime, int stepsInRegime) {
        if (!simulatorProperties.isEnableRegimeSwitching()) {
            return new RegimeStatus(currentRegime, stepsInRegime + 1);
        }
        RegimeDefinition lowVol  = simulatorProperties.getRegimeLowVol();
        RegimeDefinition highVol = simulatorProperties.getRegimeHighVol();

        VolRegime newRegime = currentRegime;
        int newSteps = stepsInRegime + 1;

        if (currentRegime == VolRegime.LOW_VOL) {
            // Basit yaklaşım: meanDuration'a ulaştıysak ve random < transitionProb ise switch
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

    /**
     * Markov tabanlı rejim geçişi
     */
    private RegimeStatus updateRegimeMarkov(VolRegime currentRegime, int stepsInRegime) {
        if (!simulatorProperties.isEnableRegimeSwitching() ||
                simulatorProperties.getRegimeTransitionMatrix() == null) {
            // Fallback basit yaklaşım
            return updateRegimeSimple(currentRegime, stepsInRegime);
        }

        List<List<Double>> matrix = simulatorProperties.getRegimeTransitionMatrix();
        if (matrix.size() < 2 || matrix.get(0).size() < 2) {
            // Yine fallback
            return updateRegimeSimple(currentRegime, stepsInRegime);
        }

        int regimeIndex = (currentRegime == VolRegime.LOW_VOL) ? 0 : 1;
        double probStay = matrix.get(regimeIndex).get(regimeIndex);
        double randVal = Math.random();

        VolRegime newRegime = currentRegime;
        int newSteps = stepsInRegime + 1;

        if (randVal > probStay) {
            // switch
            newRegime = (currentRegime == VolRegime.LOW_VOL) ? VolRegime.HIGH_VOL : VolRegime.LOW_VOL;
            newSteps = 0;
        }

        return new RegimeStatus(newRegime, newSteps);
    }

    /**
     * Asıl çekirdek: EGARCH/GARCH, mean reversion, jump diffusion, makro faktör, event shock
     */
    private RateUpdateResult updateRate(MultiRateDefinition def,
                                        AssetState oldState,
                                        double dt,
                                        double z,
                                        LocalDateTime nowLdt,
                                        RegimeStatus regimeStatus) {

        double oldPrice = oldState.getCurrentPrice();
        double oldSigma = oldState.getCurrentSigma();
        double oldRet   = oldState.getLastReturn();

        LocalDate oldDay = oldState.getCurrentDay();
        LocalDate nowDate = nowLdt.toLocalDate();

        double dayOpen = oldState.getDayOpen();
        double dayHigh = oldState.getDayHigh();
        double dayLow  = oldState.getDayLow();
        long   dayVol  = oldState.getDayVolume();

        // Yeni güne girildiyse reset
        if (!nowDate.equals(oldDay)) {
            dayOpen = oldPrice;
            dayHigh = oldPrice;
            dayLow  = oldPrice;
            dayVol  = 0L;
        }

        // 1) Volatilite hesapla (EGARCH vs GARCH(1,1))
        double newSigma;
        switch (simulatorProperties.getModelType()) {
            case "EGARCH":
                newSigma = calculateEgarchVolatility(def, oldRet, oldSigma);
                break;
            case "GARCH11":
            default:
                newSigma = calculateGarchVolatility(def, oldRet, oldSigma);
        }

        // 2) Rejim / Seans / Hacim çarpanları
        double volScale = (regimeStatus.getCurrentRegime() == VolRegime.HIGH_VOL)
                ? simulatorProperties.getRegimeHighVol().getVolScale()
                : simulatorProperties.getRegimeLowVol().getVolScale();

        double sessionMult = getSessionVolMultiplier(nowLdt);

        // Makroekonomik faktörlerin volatilite üzerindeki etkisi (basit):
        double macroVolFactor = computeMacroVolAdjustment();

        double volumeScale = 1.0;
        if (simulatorProperties.isVolumeVolatilityScalingEnabled()) {
            // Basit yaklaşım: dayVol'a orantılı
            volumeScale += (dayVol * simulatorProperties.getVolumeVolatilityFactor());
        }

        double effectiveSigma = newSigma * Math.sqrt(dt)
                * volScale * sessionMult
                * volumeScale * (1.0 + macroVolFactor);

        double normShock = effectiveSigma * z;

        // 3) Drift + makro drift
        double baseDrift   = def.getDrift() * dt;
        double macroDrift  = computeMacroDriftAdjustment(); // Basit ek
        double driftPart   = baseDrift + macroDrift;

        // 4) Jump
        double jumpPart = calculateJump(def, dt);

        // 5) Olay (Event) shock
        double eventShockPart = checkEventShocks(nowLdt);

        // 6) Mean reversion
        double mrPart = def.isUseMeanReversion()
                ? calculateMeanReversion(oldPrice, def, dt)
                : 0.0;

        // 7) Log-getiri
        double logReturn = driftPart + normShock + jumpPart + eventShockPart + mrPart;
        double newPrice = oldPrice * Math.exp(logReturn);
        if (newPrice < 0.0001) newPrice = 0.0001;

        // 8) Spread
        double spread = def.getBaseSpread();
        double bid = Math.max(newPrice - spread / 2.0, 0.0001);
        double ask = bid + spread;
        double mid = (bid + ask) / 2.0;

        // 9) Hacim hesapları
        long nowMillis   = System.currentTimeMillis();
        long timeDiffMs  = nowMillis - oldState.getLastUpdateEpochMillis();
        double ratio     = (double) timeDiffMs / (double) simulatorProperties.getUpdateIntervalMillis();
        ratio = Math.min(Math.max(ratio, 1.0), 5.0);

        // Rastgele bir tick hacmi
        long tickVol = ThreadLocalRandom.current().nextInt(10, 50);
        long scaledTickVol = (long) (tickVol * ratio);
        long newDayVol = dayVol + scaledTickVol;

        // Gün içi max/min
        dayHigh = Math.max(dayHigh, mid);
        dayLow  = Math.min(dayLow,  mid);

        // Yüzdesel değişim
        double dayChangeVal = mid - dayOpen;
        double changePct = (dayOpen > 0.0)
                ? (dayChangeVal / dayOpen) * 100.0
                : 0.0;

        // Response
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
                nowDate
        );

        return new RateUpdateResult(newState, resp);
    }

    // ---------------------
    // MODEL BİLEŞENLERİ
    // ---------------------

    private double calculateEgarchVolatility(MultiRateDefinition def, double oldRet, double oldSigma) {
        // Basitleştirilmiş EGARCH(1,1) formu
        double omega = def.getGarchParams().getOmega();
        double alpha = def.getGarchParams().getAlpha();
        double beta  = def.getGarchParams().getBeta();

        double logSigmaSqOld = Math.log(oldSigma * oldSigma);

        // z_{t-1} ~ oldRet / oldSigma
        double zT_1 = (oldSigma > 1e-16) ? (oldRet / oldSigma) : 0.0;
        // Gauss varsayımıyla E[|z|] ~ 0.798
        double meanAbsZ = 0.798;

        double updatedLogSigmaSq = omega
                + beta  * logSigmaSqOld
                + alpha * (Math.abs(zT_1) - meanAbsZ);

        double newSigma = Math.exp(updatedLogSigmaSq / 2.0);
        if (newSigma < 1e-16) newSigma = 1e-16;
        return newSigma;
    }

    private double calculateGarchVolatility(MultiRateDefinition def, double oldRet, double oldSigma) {
        double omega = def.getGarchParams().getOmega();
        double alpha = def.getGarchParams().getAlpha();
        double beta  = def.getGarchParams().getBeta();

        double newSigmaSq = omega + alpha * (oldRet * oldRet) + beta * (oldSigma * oldSigma);
        if (newSigmaSq < 1e-16) newSigmaSq = 1e-16;
        return Math.sqrt(newSigmaSq);
    }

    private double calculateJump(MultiRateDefinition def, double dt) {
        double lambda = def.getJumpIntensity();
        double probJump = 1.0 - Math.exp(-lambda * dt);
        if (Math.random() < probJump) {
            return ThreadLocalRandom.current().nextGaussian() * def.getJumpVol() + def.getJumpMean();
        }
        return 0.0;
    }

    private double checkEventShocks(LocalDateTime nowLdt) {
        if (simulatorProperties.getEventShocks() == null) return 0.0;

        Instant now = nowLdt.toInstant(ZoneOffset.UTC);
        for (EventShockDefinition shock : simulatorProperties.getEventShocks()) {
            // Olay zamanı ±30 saniye
            Instant eventTime = shock.getDateTime();
            if (!now.isBefore(eventTime.minusSeconds(30)) && !now.isAfter(eventTime.plusSeconds(30))) {
                double jump = ThreadLocalRandom.current().nextGaussian() * shock.getJumpVol() + shock.getJumpMean();
                log.info("Event Shock Triggered: {} at {}", shock.getName(), nowLdt);
                return jump;
            }
        }
        return 0.0;
    }

    private double calculateMeanReversion(double oldPrice, MultiRateDefinition def, double dt) {
        double kappa = def.getKappa();
        double theta = def.getTheta();
        double logP  = Math.log(oldPrice);
        double logT  = Math.log(theta);
        return kappa * (logT - logP) * dt;
    }

    /**
     * Basit makroekonomik drift ayarı (örnek)
     */
    private double computeMacroDriftAdjustment() {
        if (simulatorProperties.getMacroIndicators() == null) return 0.0;

        double totalAdj = 0.0;
        // Varsayılan örnek: drift'e her bir indikatör (value * sensitivityToDrift * küçük çarpan)
        for (MacroIndicatorDefinition mid : simulatorProperties.getMacroIndicators()) {
            totalAdj += mid.getValue() * mid.getSensitivityToDrift() * 1e-4;
        }
        return totalAdj;
    }

    /**
     * Makroekonomik volatilite ayarı (örnek)
     */
    private double computeMacroVolAdjustment() {
        if (simulatorProperties.getMacroIndicators() == null) return 0.0;

        double totalAdj = 0.0;
        // Örnek: Volatiliteyi etkileme
        for (MacroIndicatorDefinition mid : simulatorProperties.getMacroIndicators()) {
            // Her bir indikatör vol'u bir miktar artırabilir
            totalAdj += mid.getValue() * mid.getSensitivityToVol() * 1e-3;
        }
        return totalAdj;
    }

    private double getSessionVolMultiplier(LocalDateTime now) {
        if (simulatorProperties.getSessionVolFactors() == null
                || simulatorProperties.getSessionVolFactors().isEmpty()) {
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
