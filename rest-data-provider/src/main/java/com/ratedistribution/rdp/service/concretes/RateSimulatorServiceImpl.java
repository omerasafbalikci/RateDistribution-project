package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.RateUpdateResult;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.*;
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
public class RateSimulatorServiceImpl implements RateSimulatorService {

    private final SimulatorProperties simulatorProperties;
    private final RedisTemplate<String, AssetState> assetStateRedisTemplate;
    private final HolidayCalendarService holidayCalendarService;
    private final HistoricalDataCalibrationService historicalDataCalibrationService;
    private final MacroDataService macroDataService;
    private final NewsApiService newsApiService;
    private final AdvancedNlpService advancedNlpService;

    private HashOperations<String, String, AssetState> stateOps;
    private CorrelatedRandomVectorGenerator correlatedRng;

    @PostConstruct
    public void init() {
        this.stateOps = assetStateRedisTemplate.opsForHash();

        // 1) MLE tabanlı GARCH parametre kalibrasyonu
        for (MultiRateDefinition def : simulatorProperties.getRates()) {
            GarchParams calibrated = historicalDataCalibrationService.calibrateGarchParams(def.getRateName());
            def.setGarchParams(calibrated);
        }

        // 2) NxN Markov matris kalibrasyonu
        if (simulatorProperties.isUseMarkovSwitching()) {
            List<List<Double>> matrix = historicalDataCalibrationService.calibrateMarkovMatrix();
            if (matrix != null && !matrix.isEmpty()) {
                simulatorProperties.setRegimeTransitionMatrix(matrix);
            }
        }

        // 3) Korelasyon matrisinden correlated RNG
        double[][] corrArray = to2DArray(simulatorProperties.getCorrelationMatrix());
        correlatedRng = new CorrelatedRandomVectorGenerator(corrArray);

        // 4) Redis'te yoksa initial state oluştur
        simulatorProperties.getRates().forEach(def -> {
            AssetState existing = stateOps.get("ASSET_STATES", def.getRateName());
            if (existing == null) {
                AssetState initState = createInitialState(def);
                stateOps.put("ASSET_STATES", def.getRateName(), initState);
            }
        });
    }

    @Override
    public List<RateDataResponse> updateAllRates() {
        List<RateDataResponse> responseList = new ArrayList<>();
        List<MultiRateDefinition> definitions = simulatorProperties.getRates();

        // dt: updateIntervalMillis / 1 gün
        double dt = simulatorProperties.getUpdateIntervalMillis() / (1000.0 * 3600.0 * 24.0);

        // Korelasyonlu normal rastgele sayılar
        double[] epsVector = correlatedRng.sample();

        for (int i = 0; i < definitions.size(); i++) {
            MultiRateDefinition def = definitions.get(i);

            AssetState oldState = stateOps.get("ASSET_STATES", def.getRateName());
            if (oldState == null) {
                oldState = createInitialState(def);
            }

            long nowMillis = System.currentTimeMillis();
            LocalDateTime nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC);

            // Hafta sonu / tatil durumu
            AssetState modState = handleMarketCloseScenarios(oldState, nowMillis, nowLdt);

            // Rejim güncellemesi (NxN Markov veya basit)
            RegimeStatus newRegimeStatus;
            if (simulatorProperties.isUseMarkovSwitching()) {
                newRegimeStatus = updateRegimeMarkovN(modState.getCurrentRegime(), modState.getStepsInRegime());
            } else {
                newRegimeStatus = updateRegimeSimple(modState.getCurrentRegime(), modState.getStepsInRegime());
            }

            // Asıl kur hesaplaması
            RateUpdateResult result = updateRate(def, modState, dt, epsVector[i], nowLdt, newRegimeStatus);

            // Redis'e kaydet & listeye ekle
            stateOps.put("ASSET_STATES", def.getRateName(), result.newState());
            responseList.add(result.response());
        }

        return responseList;
    }

    // ------------------- Yardımcı Metotlar ----------------------------

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
                0.01, // sigma
                0.0,  // lastRet
                def.getInitialPrice(),
                def.getInitialPrice(),
                def.getInitialPrice(),
                0L, // dayVolume
                VolRegime.LOW_VOL,
                0, // stepsInRegime
                nowEpoch,
                LocalDate.now(ZoneOffset.UTC)
        );
    }

    private AssetState handleMarketCloseScenarios(AssetState oldState, long nowMillis, LocalDateTime nowLdt) {
        boolean isHolidayNow = holidayCalendarService.isHoliday(nowLdt);
        boolean isWeekendNow = isWeekend(nowLdt.getDayOfWeek());

        long lastMillis = oldState.getLastUpdateEpochMillis();
        LocalDateTime lastLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastMillis), ZoneOffset.UTC);

        boolean wasWeekend = isWeekend(lastLdt.getDayOfWeek());
        boolean wasHoliday = holidayCalendarService.isHoliday(lastLdt);

        // Gap jump
        if ((wasWeekend || wasHoliday) && (!isWeekendNow && !isHolidayNow)) {
            if (simulatorProperties.getWeekendHandling().isEnabled()) {
                double gapMean = simulatorProperties.getWeekendHandling().getWeekendGapJumpMean();
                double gapVol  = simulatorProperties.getWeekendHandling().getWeekendGapJumpVol();
                double jump = ThreadLocalRandom.current().nextGaussian() * gapVol + gapMean;
                return applyGapJump(oldState, nowMillis, jump);
            }
        }
        // Piyasa kapalıysa güncelleme yok
        if (isWeekendNow || isHolidayNow) {
            return oldState;
        }

        // Normal piyasa açık
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

    // Basit low/high rejim geçişi
    private RegimeStatus updateRegimeSimple(VolRegime currentRegime, int stepsInRegime) {
        if (!simulatorProperties.isEnableRegimeSwitching()) {
            return new RegimeStatus(currentRegime, stepsInRegime + 1);
        }
        RegimeDefinition lowVol  = simulatorProperties.getRegimeLowVol();
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

    // NxN Markov
    private RegimeStatus updateRegimeMarkovN(VolRegime currentRegime, int stepsInRegime) {
        if (!simulatorProperties.isEnableRegimeSwitching()
                || simulatorProperties.getRegimeTransitionMatrix() == null) {
            return updateRegimeSimple(currentRegime, stepsInRegime);
        }

        List<List<Double>> matrix = simulatorProperties.getRegimeTransitionMatrix();
        int n = matrix.size();

        int currentIndex = currentRegime.getIndex();
        if (currentIndex < 0 || currentIndex >= n) {
            return updateRegimeSimple(currentRegime, stepsInRegime);
        }

        double randVal = Math.random();
        double cumulative = 0.0;
        int newIndex = currentIndex;
        for (int j = 0; j < n; j++) {
            cumulative += matrix.get(currentIndex).get(j);
            if (randVal <= cumulative) {
                newIndex = j;
                break;
            }
        }

        VolRegime newRegime = VolRegime.fromIndex(newIndex);
        int newSteps = (newRegime == currentRegime) ? (stepsInRegime + 1) : 0;
        return new RegimeStatus(newRegime, newSteps);
    }

    // ------------------- Asıl Kur Hesaplaması -------------------------
    private RateUpdateResult updateRate(MultiRateDefinition def,
                                        AssetState oldState,
                                        double dt,
                                        double z,
                                        LocalDateTime nowLdt,
                                        RegimeStatus regimeStatus) {

        // 0) Haber Analizi => NLP
        // Query: "forex OR interest OR inflation" (isteğe göre)
        List<String> headlines = newsApiService.fetchNewsHeadlines("forex OR interest OR inflation");
        AdvancedNlpService.NlpAnalysisResult nlpResult = advancedNlpService.analyzeNewsBatch(headlines);

        // Sentiment (0..4) => 2=neutral
        double sentimentScore = nlpResult.getSentimentScore();
        // Anlam (ör. FED_RATE_CUT, FED_RATE_HIKE)
        AdvancedNlpService.NewsTrigger trigger = nlpResult.getTrigger();

        // 1) Eski state verileri
        double oldPrice = oldState.getCurrentPrice();
        double oldSigma = oldState.getCurrentSigma();
        double oldRet   = oldState.getLastReturn();

        LocalDate oldDay = oldState.getCurrentDay();
        LocalDate nowDate = nowLdt.toLocalDate();

        double dayOpen = oldState.getDayOpen();
        double dayHigh = oldState.getDayHigh();
        double dayLow  = oldState.getDayLow();
        long   dayVol  = oldState.getDayVolume();

        // Yeni gün?
        if (!nowDate.equals(oldDay)) {
            dayOpen = oldPrice;
            dayHigh = oldPrice;
            dayLow  = oldPrice;
            dayVol  = 0L;
        }

        // 2) Volatilite modeli (GARCH/EGARCH/GJR-EGARCH)
        double newSigma;
        switch (simulatorProperties.getModelType()) {
            case "EGARCH":
                newSigma = calculateEgarchVolatility(def, oldRet, oldSigma);
                break;
            case "GJR-EGARCH":
                newSigma = calculateGjrEgarchVolatility(def, oldRet, oldSigma);
                break;
            case "GARCH11":
            default:
                newSigma = calculateGarchVolatility(def, oldRet, oldSigma);
        }

        // 3) Rejim, seans, hacim, makroVol
        double volScale = getVolScaleFromRegime(regimeStatus.getCurrentRegime());
        double sessionMult = getSessionVolMultiplier(nowLdt);
        double macroVolFactor = computeMacroVolAdjustment();

        double volumeScale = 1.0;
        if (simulatorProperties.isVolumeVolatilityScalingEnabled()) {
            volumeScale += (dayVol * simulatorProperties.getVolumeVolatilityFactor());
        }

        // Haber bazlı volatilite ek kural:
        // Örnek => negatif sentiment => vol artar
        double newsVolFactor = 1.0;
        if (sentimentScore < 1.9) {
            // “kötü haber” sayalım
            newsVolFactor = 1.05;
        }

        double effectiveSigma = newSigma * Math.sqrt(dt)
                * volScale
                * sessionMult
                * volumeScale
                * (1.0 + macroVolFactor)
                * newsVolFactor;

        double normShock = effectiveSigma * z;

        // 4) Drift:
        double baseDrift = def.getDrift() * dt;
        double macroDrift = computeMacroDriftAdjustment();
        double newsDrift = sentimentToDriftDelta(sentimentScore);
        double driftPart = baseDrift + macroDrift + newsDrift;

        // 5) Jump
        // => trigger'a göre factor
        double jumpPart = calculateJumpWithTrigger(def, dt, trigger);

        // 6) Event shock
        double eventShockPart = checkEventShocks(nowLdt);

        // 7) Mean reversion
        double mrPart = def.isUseMeanReversion() ? calculateMeanReversion(oldPrice, def, dt) : 0.0;

        // 8) Log-return
        double logReturn = driftPart + normShock + jumpPart + eventShockPart + mrPart;
        double newPrice = oldPrice * Math.exp(logReturn);
        if (newPrice < 0.0001) newPrice = 0.0001;

        // 9) Spread, mid
        double spr = def.getBaseSpread();
        double bid = Math.max(newPrice - spr / 2.0, 0.0001);
        double ask = bid + spr;
        double mid = (bid + ask) / 2.0;

        // 10) Hacim
        long nowMillis = System.currentTimeMillis();
        long timeDiffMs = nowMillis - oldState.getLastUpdateEpochMillis();
        double ratio = (double) timeDiffMs / (double) simulatorProperties.getUpdateIntervalMillis();
        ratio = Math.min(Math.max(ratio, 1.0), 5.0);

        long tickVol = ThreadLocalRandom.current().nextInt(10, 50);
        long scaledTickVol = (long) (tickVol * ratio);
        long newDayVol = dayVol + scaledTickVol;

        dayHigh = Math.max(dayHigh, mid);
        dayLow  = Math.min(dayLow, mid);

        double dayChangeVal = mid - dayOpen;
        double changePct = (dayOpen > 0.0) ? (dayChangeVal / dayOpen) * 100.0 : 0.0;

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

    // ---------------- NLP Tetikleyicisi Ek Fonksiyonlar ----------------

    /**
     * Örnek sentiment => drift eklemesi.
     * 2.0 => nötr => 0.0
     * >2.1 => hafif pozitif => +0.0001
     * <1.9 => negatif => -0.0002 vs.
     */
    private double sentimentToDriftDelta(double sentimentScore) {
        if (sentimentScore > 2.1) {
            return 0.00005;
        } else if (sentimentScore < 1.9) {
            return -0.0001;
        }
        return 0.0;
    }

    /**
     * Trigger'a göre jumpIntensity factor.
     * Örn: FED_RATE_HIKE => 2x jump
     *      FED_RATE_CUT => yarısı
     */
    private double calculateJumpWithTrigger(MultiRateDefinition def, double dt, AdvancedNlpService.NewsTrigger trig) {
        double factor = 1.0;
        if (trig == AdvancedNlpService.NewsTrigger.FED_RATE_HIKE) {
            factor = 1.5;
        } else if (trig == AdvancedNlpService.NewsTrigger.FED_RATE_CUT) {
            factor = 0.8;
        }
        return applyJumpFactor(def, dt, factor);
    }

    private double applyJumpFactor(MultiRateDefinition def, double dt, double factor) {
        double lambda = def.getJumpIntensity() * factor;
        double probJump = 1.0 - Math.exp(-lambda * dt);
        if (Math.random() < probJump) {
            double jumpMean = def.getJumpMean();
            double jumpVol  = def.getJumpVol() * factor;
            return ThreadLocalRandom.current().nextGaussian() * jumpVol + jumpMean;
        }
        return 0.0;
    }

    // ---------------- GARCH / EGARCH / GJR-EGARCH Metotları ----------------

    private double calculateGjrEgarchVolatility(MultiRateDefinition def, double oldRet, double oldSigma) {
        double omega = def.getGarchParams().getOmega();
        double alpha = def.getGarchParams().getAlpha();
        double beta  = def.getGarchParams().getBeta();
        double gamma = 0.1; // sabit

        double logSigmaSqOld = Math.log(oldSigma * oldSigma);
        double zT_1 = (oldSigma > 1e-16) ? (oldRet / oldSigma) : 0.0;
        double meanAbsZ = 0.798;

        double negativePart = Math.min(zT_1, 0.0);

        double updatedLogSigmaSq = omega
                + beta  * logSigmaSqOld
                + alpha * (Math.abs(zT_1) - meanAbsZ)
                + gamma * negativePart;

        double newSigma = Math.exp(updatedLogSigmaSq / 2.0);
        if (newSigma < 1e-16) newSigma = 1e-16;
        return newSigma;
    }

    private double calculateEgarchVolatility(MultiRateDefinition def, double oldRet, double oldSigma) {
        double omega = def.getGarchParams().getOmega();
        double alpha = def.getGarchParams().getAlpha();
        double beta  = def.getGarchParams().getBeta();

        double logSigmaSqOld = Math.log(oldSigma * oldSigma);
        double zT_1 = (oldSigma > 1e-16) ? (oldRet / oldSigma) : 0.0;
        double meanAbsZ = 0.798;

        double updatedLogSigmaSq = omega
                + beta  * logSigmaSqOld
                + alpha * (Math.abs(zT_1) - meanAbsZ);

        double newSigma = Math.exp(updatedLogSigmaSq / 2.0);
        if (newSigma < 1e-16) {
            newSigma = 1e-16;
        }
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

    // ---------------- Jump / Event / MeanReversion ----------------

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
            Instant eventTime = shock.getDateTime();
            // 30 saniye aralığında
            if (!now.isBefore(eventTime.minusSeconds(30)) && !now.isAfter(eventTime.plusSeconds(30))) {
                double jump = ThreadLocalRandom.current().nextGaussian() * shock.getJumpVol() + shock.getJumpMean();
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

    // ---------------- Macro Param Adjust ----------------

    private double computeMacroDriftAdjustment() {
        if (macroDataService.getMacroIndicators() == null) return 0.0;
        double totalAdj = 0.0;
        for (MacroIndicatorDefinition mid : macroDataService.getMacroIndicators()) {
            totalAdj += mid.getValue() * mid.getSensitivityToDrift() * 1e-4;
        }
        return totalAdj;
    }

    private double computeMacroVolAdjustment() {
        if (macroDataService.getMacroIndicators() == null) return 0.0;
        double totalAdj = 0.0;
        for (MacroIndicatorDefinition mid : macroDataService.getMacroIndicators()) {
            totalAdj += mid.getValue() * mid.getSensitivityToVol() * 1e-3;
        }
        return totalAdj;
    }

    private double getVolScaleFromRegime(VolRegime regime) {
        switch (regime) {
            case LOW_VOL:
                return simulatorProperties.getRegimeLowVol().getVolScale();
            case HIGH_VOL:
                return simulatorProperties.getRegimeHighVol().getVolScale();
            case MID_VOL:
                // 3 rejim varsa.
                return 2.0;
            default:
                return 1.0;
        }
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
