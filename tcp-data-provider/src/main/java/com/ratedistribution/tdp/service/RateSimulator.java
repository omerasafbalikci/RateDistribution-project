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
    private final HolidayCalendarService holidayCalendarService; // Opsiyonel
    private final CorrelatedRandomVectorGenerator correlatedRng;

    // Tüm rate'lerin anlık state'lerini burada tutuyoruz.
    private final Map<String, AssetState> stateMap = new ConcurrentHashMap<>();

    public RateSimulator(SimulatorProperties props, HolidayCalendarService holidayCalendarService) {
        this.simulatorProps = props;
        this.holidayCalendarService = holidayCalendarService;

        // Korelasyon matrisi
        List<List<Double>> mat = props.getCorrelationMatrix();
        int n = mat.size();
        double[][] corrArray = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                corrArray[i][j] = mat.get(i).get(j);
            }
        }
        this.correlatedRng = new CorrelatedRandomVectorGenerator(corrArray);

        // Başlangıç state'leri
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

    /**
     * Her update döngüsünde çağrılır.
     * Tüm rate'ler için correlated random vektör üreterek
     * GARCH + JumpDiff + Rejim + tatil/haftasonu vs. logiğini çalıştırır.
     *
     * @return Her rate için zengin formatta satır listesi, ör:
     *         PF1_USDTRY|bid:number:34.XXXX|ask:number:35.XXXX|timestamp:datetime:2025-02-03T02:07:49.652Z|open:number:...|high:number:...|...
     */
    public List<String> updateAllRates() {
        var resultLines = new java.util.ArrayList<String>();
        double dt = simulatorProps.getUpdateIntervalMillis() / (1000.0 * 3600.0 * 24.0);

        // Korelasyonlu random vektör
        double[] epsVector = correlatedRng.sample();
        List<MultiRateDefinition> defs = simulatorProps.getRates();

        for (int i = 0; i < defs.size(); i++) {
            MultiRateDefinition def = defs.get(i);
            String rateName = def.getRateName();
            AssetState oldState = stateMap.get(rateName);
            if (oldState == null) continue;

            long nowMillis = System.currentTimeMillis();
            LocalDateTime nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC);

            // Hafta sonu / tatil kontrolü + gap
            AssetState modState = handleMarketCloseScenarios(oldState, nowLdt);

            // Rejim güncellemesi
            RegimeStatus newRegime = updateRegime(modState.getCurrentRegime(), modState.getStepsInRegime());

            // GARCH, Jump vb. ile fiyat üret
            var update = updateRate(def, modState, dt, epsVector[i], nowLdt, newRegime);

            // Yeni state'i kaydet
            stateMap.put(rateName, update.newState());

            // TCP output formatını ekle
            resultLines.add(update.rateLine());
        }
        return resultLines;
    }

    // -------------------------------------------------------
    //                Özel Hesaplamalar
    // -------------------------------------------------------

    private AssetState handleMarketCloseScenarios(AssetState oldState, LocalDateTime nowLdt) {
        long nowMillis = System.currentTimeMillis();
        // Eğer market kapalıysa (haftasonu veya tatil) - update yok, oldState döner
        if (isWeekend(nowLdt.getDayOfWeek()) || isHoliday(nowLdt)) {
            return oldState;
        }

        // Daha önce haftasonu/tatildeydi, şimdi açılıyor -> gap jump
        long lastMillis = oldState.getLastUpdateEpochMillis();
        LocalDateTime lastLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastMillis), ZoneOffset.UTC);
        if (isWeekend(lastLdt.getDayOfWeek()) || isHoliday(lastLdt)) {
            double gapMean = simulatorProps.getWeekendHandling().getWeekendGapJumpMean();
            double gapVol  = simulatorProps.getWeekendHandling().getWeekendGapJumpVol();
            double jump    = ThreadLocalRandom.current().nextGaussian() * gapVol + gapMean;
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
            // Rejim geçişi kapalıysa
            return new RegimeStatus(current, steps+1);
        }
        RegimeDefinition low  = simulatorProps.getRegimeLowVol();
        RegimeDefinition high = simulatorProps.getRegimeHighVol();
        VolRegime newRegime   = current;
        int newSteps          = steps + 1;

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

    // -------------------------------------------------------
    //          GARCH + Jump + Rejim + Volume Hesapları
    // -------------------------------------------------------

    private record RateUpdateResult(AssetState newState, String rateLine) {}

    private RateUpdateResult updateRate(MultiRateDefinition def,
                                        AssetState oldState,
                                        double dt,
                                        double z,
                                        LocalDateTime nowLdt,
                                        RegimeStatus newRegime) {

        // Eski fiyat & sigma
        double oldPrice = oldState.getCurrentPrice();
        double oldSigma = oldState.getCurrentSigma();
        double oldRet   = oldState.getLastReturn();

        // Gün değişimi kontrol
        LocalDate oldDay = oldState.getCurrentDay();
        LocalDate nowDay = nowLdt.toLocalDate();

        double dayOpen = oldState.getDayOpen();
        double dayHigh = oldState.getDayHigh();
        double dayLow  = oldState.getDayLow();
        long   dayVol  = oldState.getDayVolume();

        // Yeni güne geçtiysek reset
        if (!nowDay.equals(oldDay)) {
            dayOpen = Math.max(oldPrice, 0.0001);
            dayHigh = dayOpen;
            dayLow  = dayOpen;
            dayVol  = 0L;
        }

        // GARCH sigma hesapla
        double newSigma = calculateGarchVolatility(def, oldRet, oldSigma);

        // Rejim & seans çarpanı
        double volScale = (newRegime.getCurrentRegime() == VolRegime.HIGH_VOL)
                ? simulatorProps.getRegimeHighVol().getVolScale()
                : simulatorProps.getRegimeLowVol().getVolScale();
        double sessionMult = getSessionVolMultiplier(nowLdt);
        double effectiveSigma = newSigma * Math.sqrt(dt) * volScale * sessionMult;

        // Rastgele + drift + jump + mean reversion
        double normShock = effectiveSigma * z;
        double driftPart = def.getDrift() * dt;
        double jumpPart  = calculateJump(def, dt);
        double mrPart    = (def.isUseMeanReversion()) ? calcMeanReversion(oldPrice, def, dt) : 0.0;

        double logReturn = driftPart + normShock + jumpPart + mrPart;
        double newPrice  = oldPrice * Math.exp(logReturn);
        if (newPrice < 0.0001) newPrice = 0.0001;

        // Spread, bid, ask
        double spr = def.getBaseSpread();
        double bid = Math.max(newPrice - spr/2.0, 0.0001);
        double ask = bid + spr;
        double mid = 0.5 * (bid + ask);

        // Volume
        long nowMs    = System.currentTimeMillis();
        long timeDiff = nowMs - oldState.getLastUpdateEpochMillis();
        double ratio  = (double) timeDiff / simulatorProps.getUpdateIntervalMillis();
        if (ratio < 1.0) ratio = 1.0;
        if (ratio > 5.0) ratio = 5.0;

        long tickVol       = ThreadLocalRandom.current().nextInt(10,50);
        long scaledTickVol = (long)(tickVol * ratio);
        long newDayVol     = dayVol + scaledTickVol;

        // High/Low güncelle
        dayHigh = Math.max(dayHigh, mid);
        dayLow  = Math.min(dayLow, mid);

        // Günlük değişim
        double dayChangeVal = mid - dayOpen;
        double dayChangePct = (dayOpen > 0.0) ? (dayChangeVal / dayOpen) * 100.0 : 0.0;

        // Yeni state
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

        // Timestamp string (ISO-8601 + Z)
        ZonedDateTime zdt = nowLdt.atZone(ZoneOffset.UTC);
        String timeStr = zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

        // TCP için zengin format (REST'teki tüm alanları ekliyoruz).
        String line = String.format(
                // rateName
                "%s"
                        // bid/ask/timestamp
                        + "|bid:number:%.6f|ask:number:%.6f|timestamp:datetime:%s"
                        // dayOpen/dayHigh/dayLow
                        + "|open:number:%.6f|high:number:%.6f|low:number:%.6f"
                        // dayChange/dayChangePercent
                        + "|change:number:%.6f|changePercent:percent:%.4f"
                        // dayVolume/lastTickVolume
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

        // RateUpdateResult => (yeni state, TCP satırı)
        return new RateUpdateResult(newState, line);
    }

    private double calculateGarchVolatility(MultiRateDefinition def, double oldRet, double oldSigma) {
        double omega = def.getGarchParams().getOmega();
        double alpha = def.getGarchParams().getAlpha();
        double beta  = def.getGarchParams().getBeta();

        double newSigmaSq = omega + alpha * (oldRet*oldRet) + beta * (oldSigma*oldSigma);
        if (newSigmaSq < 1e-16) newSigmaSq = 1e-16;
        return Math.sqrt(newSigmaSq);
    }

    private double calculateJump(MultiRateDefinition def, double dt) {
        double lambda   = def.getJumpIntensity();
        double probJump = 1 - Math.exp(-lambda*dt);
        if (Math.random() < probJump) {
            return ThreadLocalRandom.current().nextGaussian() * def.getJumpVol() + def.getJumpMean();
        }
        return 0.0;
    }

    private double calcMeanReversion(double oldPrice, MultiRateDefinition def, double dt) {
        double kappa = def.getKappa();
        double theta = def.getTheta();
        double logP  = Math.log(oldPrice);
        double logT  = Math.log(theta);
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
