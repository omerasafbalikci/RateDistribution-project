package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.*;
import com.ratedistribution.rdp.service.abstracts.MacroDataService;
import com.ratedistribution.rdp.service.abstracts.RateSimulatorService;
import com.ratedistribution.rdp.utilities.CorrelatedRandomVectorGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RateSimulatorServiceImpl implements RateSimulatorService {
    private final SimulatorProperties simulatorProperties;
    private final RedisTemplate<String, AssetState> assetStateRedisTemplate;
    private final RedisTemplate<String, RateDataResponse> rateDataResponseRedisTemplate;
    private final HolidayCalendarService holidayCalendarService;
    private final ShockServiceImpl shockService;
    private final MacroDataService macroDataService;
    private final CorrelatedRandomVectorGenerator correlatedRng;

    @Override
    public List<RateDataResponse> updateAllRates() {
        List<RateDataResponse> responses = new ArrayList<>();
        List<MultiRateDefinition> rateDefinitions = this.simulatorProperties.getRates();

        if (rateDefinitions == null || rateDefinitions.isEmpty()) {
            return responses;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean isHoliday = this.holidayCalendarService.isHoliday(now);
        boolean isWeekend = isWeekend(now);

        // Hafta sonu veya tatilde piyasa "kapalı" kalsın
        if (isWeekend || isHoliday) {
            return buildClosedMarketResponses(rateDefinitions, now);
        }

        // Korele rastgele vektör
        double[] randomVec = this.correlatedRng.sample();

        for (int i = 0; i < rateDefinitions.size(); i++) {
            MultiRateDefinition rateDefinition = rateDefinitions.get(i);
            double randomFactor = randomVec[i];

            AssetState oldState = getOrInitAssetState(rateDefinition, now);

            // 1) Fiyat ve volatiliteyi (GARCH / EGARCH vb.) güncelle
            AssetState updatedState = updatePriceAndVolatility(rateDefinition, oldState, randomFactor, now);

            // 2) Makro dataları uygula
            macroDataService.applyMacroData(updatedState);

            // 3) Otomatik şoklara bak (küçük, orta, büyük ihtimal)
            shockService.processAutomaticShocks(updatedState, now);

            // 4) Kritik (Event) şoklara bak
            shockService.checkAndApplyCriticalShocks(updatedState, now);

            // State’i sakla
            saveAssetState(rateDefinition.getRateName(), updatedState);

            // Response objesi hazırla
            RateDataResponse response = buildRateDataResponse(rateDefinition, updatedState, now);
            saveRateDataResponse(rateDefinition.getRateName(), response);

            responses.add(response);
        }

        return responses;
    }

    private AssetState updatePriceAndVolatility(MultiRateDefinition rateDef,
                                                AssetState oldState,
                                                double randomFactor,
                                                LocalDateTime now) {

        double oldPrice = oldState.getCurrentPrice();
        double oldSigma = oldState.getCurrentSigma();
        double oldRet   = oldState.getLastReturn();

        // 1) Volatilite hesapla
        double newSigma = computeVolatility(simulatorProperties.getModelType(), rateDef, oldSigma, oldRet);

        // 2) Drift'i (yıllık) saniyeye indirgemek için
        double driftAdjustment = getDriftAdjustmentPerSec(rateDef.getDrift());

        // 3) Fiyatta rastgele hareket
        double newPrice = oldPrice * Math.exp(newSigma * randomFactor + driftAdjustment);

        // 4) Seans bazlı volMultiplier
        double volMultiplier = getSessionVolMultiplier(now);
        newPrice = applyVolMultiplier(oldPrice, newPrice, volMultiplier);

        // 5) Mean reversion
        if (rateDef.isUseMeanReversion()) {
            newPrice = applyMeanReversion(oldPrice, newPrice, rateDef.getKappa(), rateDef.getTheta());
        }

        // 6) Yeni AssetState oluştur
        AssetState updatedState = createNewStateFromOld(oldState, newPrice, newSigma, now);

        return updatedState;
    }

    // EGARCH, GARCH(1,1), GJR-EGARCH
    private double computeVolatility(String modelType,
                                     MultiRateDefinition rateDef,
                                     double oldSigma,
                                     double oldReturn) {
        switch (modelType.toUpperCase()) {
            case "GARCH11":
                return garchUpdate(rateDef.getGarchParams(), oldSigma, oldReturn);
            case "GJR-EGARCH":
                return gjrEgarchUpdate(rateDef.getGarchParams(), oldSigma, oldReturn);
            case "EGARCH":
            default:
                return eGarchUpdate(rateDef.getGarchParams(), oldSigma, oldReturn);
        }
    }

    // GARCH(1,1)
    private double garchUpdate(GarchParams params, double oldSigma, double oldReturn) {
        double variance = params.getOmega()
                + params.getAlpha() * Math.pow(oldReturn, 2)
                + params.getBeta()  * Math.pow(oldSigma, 2);
        return Math.sqrt(variance);
    }

    // EGARCH basit versiyon
    private double eGarchUpdate(GarchParams params, double oldSigma, double oldReturn) {
        double logSigmaSq = Math.log(oldSigma * oldSigma);
        double gOfZ = oldReturn; // (detaylı formüllerle geliştirilebilir)
        double nextLogSigmaSq = params.getOmega()
                + params.getAlpha() * gOfZ
                + params.getBeta()  * logSigmaSq;
        return Math.sqrt(Math.exp(nextLogSigmaSq));
    }

    // GJR-EGARCH basit versiyon
    private double gjrEgarchUpdate(GarchParams params, double oldSigma, double oldReturn) {
        double logSigmaSq = Math.log(oldSigma * oldSigma);
        double indicator  = oldReturn < 0 ? 1.0 : 0.0; // negatif getiri varsa ek etki
        double nextLogSigmaSq = params.getOmega()
                + params.getAlpha() * oldReturn * indicator
                + params.getBeta()  * logSigmaSq;
        return Math.sqrt(Math.exp(nextLogSigmaSq));
    }

    private double getDriftAdjustmentPerSec(double annualDrift) {
        // Yıllık drift -> saniyelik
        return annualDrift / (365.0 * 24.0 * 3600.0);
    }

    private double applyVolMultiplier(double oldPrice, double newPrice, double multiplier) {
        double delta = newPrice - oldPrice;
        return oldPrice + delta * multiplier;
    }

    private double applyMeanReversion(double oldPrice, double newPrice, double kappa, double theta) {
        // Basit Ornstein-Uhlenbeck benzeri
        // dP = kappa*(theta - Pold)*dt + (newPrice - oldPrice)
        return oldPrice + kappa * (theta - oldPrice) + (newPrice - oldPrice);
    }

    private AssetState createNewStateFromOld(AssetState oldState,
                                             double newPrice,
                                             double newSigma,
                                             LocalDateTime now) {
        AssetState st = new AssetState();
        st.setCurrentPrice(newPrice);
        st.setCurrentSigma(newSigma);

        double ret = Math.log(newPrice / oldState.getCurrentPrice());
        st.setLastReturn(ret);

        LocalDate oldDay = oldState.getCurrentDay();
        LocalDate nowDay = now.toLocalDate();

        if (!oldDay.isEqual(nowDay)) {
            // Yeni gün başlıyor
            st.setDayOpen(newPrice);
            st.setDayHigh(newPrice);
            st.setDayLow(newPrice);
            st.setDayVolume(0L);
            st.setCurrentDay(nowDay);
        } else {
            // Aynı gün devam
            st.setDayOpen(oldState.getDayOpen());
            st.setDayHigh(Math.max(oldState.getDayHigh(), newPrice));
            st.setDayLow(Math.min(oldState.getDayLow(), newPrice));
            st.setDayVolume(oldState.getDayVolume() + getRandomVolume());
            st.setCurrentDay(oldDay);
        }

        // Diğer alanlar
        st.setCurrentRegime(oldState.getCurrentRegime());
        st.setStepsInRegime(oldState.getStepsInRegime() + 1);
        st.setLastUpdateEpochMillis(now.toInstant(ZoneOffset.UTC).toEpochMilli());
        return st;
    }

    // Basit random volume
    private long getRandomVolume() {
        return ThreadLocalRandom.current().nextInt(1, 10);
    }

    private RateDataResponse buildRateDataResponse(MultiRateDefinition rateDef,
                                                   AssetState state,
                                                   LocalDateTime now) {
        RateDataResponse response = new RateDataResponse();
        response.setRateName(rateDef.getRateName());
        response.setTimestamp(now);

        BigDecimal mid   = BigDecimal.valueOf(state.getCurrentPrice());
        BigDecimal spread= BigDecimal.valueOf(rateDef.getBaseSpread());
        BigDecimal halfSpread = spread.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

        BigDecimal bid = mid.subtract(halfSpread);
        BigDecimal ask = mid.add(halfSpread);
        response.setBid(bid);
        response.setAsk(ask);

        response.setDayOpen(BigDecimal.valueOf(state.getDayOpen()));
        response.setDayHigh(BigDecimal.valueOf(state.getDayHigh()));
        response.setDayLow(BigDecimal.valueOf(state.getDayLow()));

        double dayChange = state.getCurrentPrice() - state.getDayOpen();
        response.setDayChange(BigDecimal.valueOf(dayChange));

        double dayChangePct = 100.0 * (dayChange / state.getDayOpen());
        response.setDayChangePercent(BigDecimal.valueOf(dayChangePct));

        response.setDayVolume(state.getDayVolume());
        response.setLastTickVolume((long) getRandomVolume());

        return response;
    }

    private boolean isWeekend(LocalDateTime dt) {
        return dt.getDayOfWeek() == DayOfWeek.SATURDAY || dt.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private List<RateDataResponse> buildClosedMarketResponses(List<MultiRateDefinition> rateDefs,
                                                              LocalDateTime now) {
        List<RateDataResponse> responses = new ArrayList<>();
        for (MultiRateDefinition rd : rateDefs) {
            AssetState st = getOrInitAssetState(rd, now);
            RateDataResponse resp = buildRateDataResponse(rd, st, now);
            saveRateDataResponse(rd.getRateName(), resp);
            responses.add(resp);
        }
        return responses;
    }

    private AssetState getOrInitAssetState(MultiRateDefinition rateDef, LocalDateTime now) {
        HashOperations<String, String, AssetState> ops = assetStateRedisTemplate.opsForHash();
        AssetState state = ops.get("ASSET_STATES", rateDef.getRateName());
        if (state == null) {
            state = initAssetState(rateDef, now);
            ops.put("ASSET_STATES", rateDef.getRateName(), state);
        }
        return state;
    }

    private AssetState initAssetState(MultiRateDefinition rateDef, LocalDateTime now) {
        AssetState state = new AssetState();
        state.setCurrentPrice(rateDef.getInitialPrice());
        double initSigma = Math.sqrt(rateDef.getGarchParams().getOmega());
        state.setCurrentSigma(initSigma);
        state.setLastReturn(0.0);
        state.setDayOpen(rateDef.getInitialPrice());
        state.setDayHigh(rateDef.getInitialPrice());
        state.setDayLow(rateDef.getInitialPrice());
        state.setDayVolume(0L);
        state.setCurrentRegime(VolRegime.LOW_VOL);
        state.setStepsInRegime(0);
        state.setLastUpdateEpochMillis(now.toInstant(ZoneOffset.UTC).toEpochMilli());
        state.setCurrentDay(now.toLocalDate());
        return state;
    }

    private void saveAssetState(String rateName, AssetState state) {
        HashOperations<String, String, AssetState> ops = assetStateRedisTemplate.opsForHash();
        ops.put("ASSET_STATES", rateName, state);
    }

    private void saveRateDataResponse(String rateName, RateDataResponse response) {
        HashOperations<String, String, RateDataResponse> ops = rateDataResponseRedisTemplate.opsForHash();
        ops.put("RATES", rateName, response);
    }

    private double getSessionVolMultiplier(LocalDateTime now) {
        if (simulatorProperties.getSessionVolFactors() == null) return 1.0;
        int hour = now.getHour();
        for (SessionVolFactor sf : simulatorProperties.getSessionVolFactors()) {
            if (hour >= sf.getStartHour() && hour < sf.getEndHour()) {
                return sf.getVolMultiplier();
            }
        }
        return 1.0;
    }
}
