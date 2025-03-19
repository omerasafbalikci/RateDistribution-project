package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.*;
import com.ratedistribution.rdp.service.abstracts.MacroDataService;
import com.ratedistribution.rdp.service.abstracts.RateSimulatorService;
import com.ratedistribution.rdp.utilities.CorrelatedRandomVectorGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class RateSimulatorServiceImpl implements RateSimulatorService {
    private final SimulatorProperties simulatorProperties;
    private final RedisTemplate<String, AssetState> assetStateRedisTemplate;
    private final RedisTemplate<String, RateDataResponse> rateDataResponseRedisTemplate;
    private final HolidayCalendarService holidayCalendarService;
    private final ShockServiceImpl shockService;
    private final MacroDataService macroDataService;
    private final CorrelatedRandomVectorGenerator correlatedRng;
    private final ThreadPoolTaskExecutor rateUpdateExecutor;
    private LocalDateTime lastUpdate = null;

    @Override
    public List<RateDataResponse> updateAllRates() {
        log.trace("Entering updateAllRates method in RateSimulatorServiceImpl.");
        List<RateDataResponse> responses = new ArrayList<>();
        List<MultiRateDefinition> rateDefinitions = this.simulatorProperties.getRates();

        if (rateDefinitions == null || rateDefinitions.isEmpty()) {
            log.warn("No rate definitions found. Exiting update process.");
            log.trace("Exiting updateAllRates method in RateSimulatorServiceImpl.");
            return responses;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        log.debug("Starting rate update process at: {}", now);
        double deltaTimeSeconds = computeDeltaTimeSeconds(now);
        log.debug("Computed delta time in seconds: {}", deltaTimeSeconds);
        boolean isHoliday = this.holidayCalendarService.isHoliday(now);
        boolean isWeekend = isWeekend(now);
        log.debug("Market status - isWeekend: {}, isHoliday: {}", isWeekend, isHoliday);

        if (isWeekend || isHoliday) {
            log.info("Market is closed (weekend/holiday). Returning closed market responses.");
            this.lastUpdate = now;
            log.trace("Exiting updateAllRates method in RateSimulatorServiceImpl.");
            return buildClosedMarketResponses(rateDefinitions, now);
        }

        boolean doWeekendGap = checkIfWeekendGap(now);
        log.debug("Weekend gap adjustment required: {}", doWeekendGap);
        double[] randomVec = this.correlatedRng.sample();
        List<CompletableFuture<RateDataResponse>> futures = new ArrayList<>();

        for (int i = 0; i < rateDefinitions.size(); i++) {
            MultiRateDefinition rateDefinition = rateDefinitions.get(i);
            double randomFactor = randomVec[i];

            CompletableFuture<RateDataResponse> future = CompletableFuture.supplyAsync(() -> {
                ThreadContext.put("rate", rateDefinition.getRateName());
                try {
                    log.info("Processing rate update: {}", rateDefinition.getRateName());

                    AssetState oldState = getOrInitAssetState(rateDefinition, now);
                    AssetState updatedState = updatePriceAndVolatility(
                            rateDefinition,
                            oldState,
                            randomFactor,
                            now,
                            deltaTimeSeconds,
                            doWeekendGap
                    );
                    log.info("Updated price and volatility for rate: {}", rateDefinition.getRateName());

                    macroDataService.applyMacroData(updatedState);
                    log.debug("Applied macro data adjustments for rate: {}", rateDefinition.getRateName());
                    shockService.processAutomaticShocks(updatedState, now);
                    log.debug("Processed automatic shocks for rate: {}", rateDefinition.getRateName());
                    shockService.checkAndApplyCriticalShocks(updatedState, now);
                    log.debug("Checked and applied critical shocks for rate: {}", rateDefinition.getRateName());

                    saveAssetState(rateDefinition.getRateName(), updatedState);
                    log.debug("Saved asset state for rate: {}", rateDefinition.getRateName());

                    RateDataResponse response = buildRateDataResponse(rateDefinition, updatedState, now);
                    saveRateDataResponse(rateDefinition.getRateName(), response);
                    log.debug("Saved rate data response for rate: {}", rateDefinition.getRateName());

                    return response;
                } finally {
                    ThreadContext.clearAll();
                }
            }, rateUpdateExecutor);
            futures.add(future);
        }
        responses = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
        this.lastUpdate = now;
        log.trace("Exiting updateAllRates method in RateSimulatorServiceImpl.");
        return responses;
    }

    private AssetState updatePriceAndVolatility(MultiRateDefinition rateDef,
                                                AssetState oldState,
                                                double randomFactor,
                                                LocalDateTime now,
                                                double deltaTimeSeconds,
                                                boolean weekendGap) {
        log.trace("Entering updatePriceAndVolatility method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
        double oldPrice = oldState.getCurrentPrice();
        double oldSigma = oldState.getCurrentSigma();
        double oldRet = oldState.getLastReturn();
        log.debug("Old price: {}, Old sigma: {}, Old return: {}", oldPrice, oldSigma, oldRet);
        double newSigma = computeVolatility(
                simulatorProperties.getModelType(),
                rateDef,
                oldSigma,
                oldRet,
                deltaTimeSeconds
        );
        log.debug("New sigma computed: {}", newSigma);
        double driftAdjustment = getDriftAdjustment(rateDef.getDrift(), deltaTimeSeconds);
        log.debug("Drift adjustment: {}", driftAdjustment);
        double logReturn = driftAdjustment + newSigma * randomFactor;
        double newPrice = oldPrice * Math.exp(logReturn);
        log.debug("Price updated from {} to {} (pre-session adjustments)", oldPrice, newPrice);
        double volMultiplier = getSessionVolMultiplier(now);
        double delta = newPrice - oldPrice;
        newPrice = oldPrice + delta * volMultiplier;
        log.debug("Session vol multiplier: {}, final newPrice: {}", volMultiplier, newPrice);
        if (rateDef.isUseMeanReversion()) {
            newPrice = applyMeanReversion(oldPrice, newPrice, rateDef.getKappa(), rateDef.getTheta(), deltaTimeSeconds);
            log.debug("Applied mean reversion, New price: {}", newPrice);
        }

        if (weekendGap) {
            log.debug("Applying weekend gap adjustment for rate: {}", rateDef.getRateName());
            double gapRandom = ThreadLocalRandom.current().nextGaussian() * this.simulatorProperties.getWeekendGapVolatility();
            double gapFactor = Math.exp(gapRandom * this.simulatorProperties.getWeekendShockFactor());
            newPrice *= gapFactor;
            log.debug("Weekend gap factor: {}, Adjusted price: {}", gapFactor, newPrice);
        }

        VolRegime newRegime = checkRegimeSwitch(newSigma);
        log.debug("Determined new volatility regime: {}", newRegime);

        AssetState updatedState = createNewStateFromOld(
                oldState,
                newPrice,
                newSigma,
                now,
                newRegime
        );
        log.trace("Exiting updatePriceAndVolatility method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
        return updatedState;
    }

    private double computeVolatility(String modelType,
                                     MultiRateDefinition rateDef,
                                     double oldSigma,
                                     double oldReturn,
                                     double deltaTimeSeconds) {
        log.trace("Entering computeVolatility method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
        double newSigma = switch (modelType.toUpperCase()) {
            case "GARCH11" -> {
                log.debug("Using GARCH(1,1)");
                yield garchUpdate(rateDef.getGarchParams(), oldSigma, oldReturn, deltaTimeSeconds);
            }
            case "GJR-EGARCH" -> {
                log.debug("Using GJR-EGARCH");
                yield gjrEgarchUpdate(rateDef.getGarchParams(), oldSigma, oldReturn, deltaTimeSeconds);
            }
            default -> {
                log.debug("Using EGARCH");
                yield eGarchUpdate(rateDef.getGarchParams(), oldSigma, oldReturn, deltaTimeSeconds);
            }
        };
        log.trace("Exiting computeVolatility method in RateSimulatorServiceImpl-> newSigma: {} rate: {}", newSigma, rateDef.getRateName());
        return newSigma;
    }

    private double garchUpdate(GarchParams params,
                               double oldSigma,
                               double oldReturn,
                               double dt) {
        log.trace("Entering garchUpdate method in RateSimulatorServiceImpl.");
        double oldVar = oldSigma * oldSigma;
        double variance = params.getOmega() * dt
                + params.getAlpha() * Math.pow(oldReturn, 2)
                + params.getBeta() * oldVar;
        double result = Math.sqrt(Math.max(variance, 1e-15));
        log.debug("GARCH(1,1) variance: {}, sigma: {}", variance, result);
        log.trace("Exiting garchUpdate method in RateSimulatorServiceImpl.");
        return result;
    }


    private double eGarchUpdate(GarchParams params,
                                double oldSigma,
                                double oldReturn,
                                double dt) {
        log.trace("Entering eGarchUpdate method in RateSimulatorServiceImpl.");
        double oldVar = oldSigma * oldSigma;
        double gamma = 0.1;
        double sign = (oldReturn < 0) ? -1.0 : 1.0;
        double gOfZ = oldReturn + gamma * (Math.abs(oldReturn) - 0.0) * sign;

        double logSigmaSq = Math.log(oldVar);
        double nextLogSigmaSq = Math.log(params.getOmega() * dt + 1e-15)
                + params.getAlpha() * gOfZ
                + params.getBeta() * logSigmaSq;
        double result = Math.sqrt(Math.exp(nextLogSigmaSq));
        log.debug("EGARCH nextLogSigmaSq: {}, sigma: {}", nextLogSigmaSq, result);
        log.trace("Exiting eGarchUpdate method in RateSimulatorServiceImpl.");
        return result;
    }

    private double gjrEgarchUpdate(GarchParams params,
                                   double oldSigma,
                                   double oldReturn,
                                   double dt) {
        log.trace("Entering gjrEgarchUpdate method in RateSimulatorServiceImpl.");
        double oldVar = oldSigma * oldSigma;
        double indicator = (oldReturn < 0) ? 1.0 : 0.0;
        double gamma = 0.2;

        double logSigmaSq = Math.log(oldVar);
        double fRt = oldReturn + gamma * indicator * oldReturn;

        double nextLogSigmaSq = Math.log(params.getOmega() * dt + 1e-15) +
                params.getBeta() * logSigmaSq +
                params.getAlpha() * fRt;

        double result = Math.sqrt(Math.exp(nextLogSigmaSq));
        log.debug("GJR-EGARCH nextLogSigmaSq: {}, sigma: {}", nextLogSigmaSq, result);
        log.trace("Exiting gjrEgarchUpdate method in RateSimulatorServiceImpl.");
        return result;
    }

    private double getDriftAdjustment(double driftAnnual, double dtSeconds) {
        log.trace("Entering getDriftAdjustment method in RateSimulatorServiceImpl.");
        double yearInSeconds = 365.0 * 24.0 * 3600.0;
        double driftPerSecond = driftAnnual / yearInSeconds;
        log.trace("Exiting getDriftAdjustment method in RateSimulatorServiceImpl.");
        return driftPerSecond * dtSeconds;
    }

    private double applyMeanReversion(double oldPrice,
                                      double newPrice,
                                      double kappa,
                                      double theta,
                                      double dtSeconds) {
        log.trace("Entering applyMeanReversion method in RateSimulatorServiceImpl.");
        double adjustedPrice = oldPrice + kappa * dtSeconds * (theta - oldPrice) + (newPrice - oldPrice);
        log.trace("Exiting applyMeanReversion method in RateSimulatorServiceImpl.");
        return adjustedPrice;
    }

    private VolRegime checkRegimeSwitch(double sigma) {
        log.trace("Entering checkRegimeSwitch method in RateSimulatorServiceImpl.");
        VolRegime regime;
        if (sigma > 0.02) {
            regime = VolRegime.HIGH_VOL;
        } else if (sigma < 0.005) {
            regime = VolRegime.LOW_VOL;
        } else {
            regime = VolRegime.MID_VOL;
        }
        log.debug("Determined volatility regime: {}", regime);
        log.trace("Exiting checkRegimeSwitch method in RateSimulatorServiceImpl.");
        return regime;
    }

    private AssetState createNewStateFromOld(AssetState oldState,
                                             double newPrice,
                                             double newSigma,
                                             LocalDateTime now,
                                             VolRegime newRegime) {
        log.trace("Entering createNewStateFromOld method in RateSimulatorServiceImpl.");
        AssetState st = new AssetState();
        st.setCurrentPrice(newPrice);
        st.setCurrentSigma(newSigma);

        double ret = 0.0;
        if (oldState.getCurrentPrice() > 0) {
            ret = Math.log(newPrice / oldState.getCurrentPrice());
        }
        st.setLastReturn(ret);
        log.debug("Computed return: {}", ret);
        LocalDate oldDay = oldState.getCurrentDay();
        LocalDate nowDay = now.toLocalDate();

        if (!oldDay.isEqual(nowDay)) {
            log.info("New day detected -> resetting daily open/high/low/volume");
            st.setDayOpen(newPrice);
            st.setDayHigh(newPrice);
            st.setDayLow(newPrice);
            st.setDayVolume(0L);
            st.setCurrentDay(nowDay);
        } else {
            st.setDayOpen(oldState.getDayOpen());
            st.setDayHigh(Math.max(oldState.getDayHigh(), newPrice));
            st.setDayLow(Math.min(oldState.getDayLow(), newPrice));
            st.setDayVolume(oldState.getDayVolume() + getRandomVolume());
            st.setCurrentDay(oldDay);
        }

        if (newRegime != oldState.getCurrentRegime()) {
            st.setStepsInRegime(0);
        } else {
            st.setStepsInRegime(oldState.getStepsInRegime() + 1);
        }
        st.setCurrentRegime(newRegime);
        st.setLastUpdateEpochMillis(now.toInstant(ZoneOffset.UTC).toEpochMilli());
        log.trace("Exiting createNewStateFromOld method in RateSimulatorServiceImpl.");
        return st;
    }

    private long getRandomVolume() {
        long volume = ThreadLocalRandom.current().nextInt(1, 10);
        log.debug("Generated random volume: {}", volume);
        return volume;
    }

    private RateDataResponse buildRateDataResponse(MultiRateDefinition rateDef,
                                                   AssetState state,
                                                   LocalDateTime now) {
        log.trace("Entering buildRateDataResponse method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
        RateDataResponse response = new RateDataResponse();
        response.setRateName(rateDef.getRateName());
        response.setTimestamp(now);

        BigDecimal mid = BigDecimal.valueOf(state.getCurrentPrice());
        BigDecimal spread = BigDecimal.valueOf(rateDef.getBaseSpread());
        BigDecimal halfSpread = spread.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

        BigDecimal bid = mid.subtract(halfSpread);
        BigDecimal ask = mid.add(halfSpread);

        response.setBid(bid);
        response.setAsk(ask);
        log.debug("Computed bid: {}, ask: {} for rate: {}", bid, ask, rateDef.getRateName());
        response.setDayOpen(BigDecimal.valueOf(state.getDayOpen()));
        response.setDayHigh(BigDecimal.valueOf(state.getDayHigh()));
        response.setDayLow(BigDecimal.valueOf(state.getDayLow()));

        double dayChange = state.getCurrentPrice() - state.getDayOpen();
        response.setDayChange(BigDecimal.valueOf(dayChange));

        if (state.getDayOpen() != 0) {
            double dayChangePct = 100.0 * (dayChange / state.getDayOpen());
            response.setDayChangePercent(BigDecimal.valueOf(dayChangePct));
        } else {
            response.setDayChangePercent(BigDecimal.ZERO);
        }

        response.setDayVolume(state.getDayVolume());
        response.setLastTickVolume(getRandomVolume());
        log.trace("Exiting buildRateDataResponse method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
        return response;
    }

    private boolean isWeekend(LocalDateTime dt) {
        boolean weekend = dt.getDayOfWeek() == DayOfWeek.SATURDAY || dt.getDayOfWeek() == DayOfWeek.SUNDAY;
        log.debug("Checked if date {} is weekend: {}", dt, weekend);
        return weekend;
    }

    private List<RateDataResponse> buildClosedMarketResponses(List<MultiRateDefinition> rateDefs,
                                                              LocalDateTime now) {
        log.trace("Entering buildClosedMarketResponses method in RateSimulatorServiceImpl.");
        List<RateDataResponse> responses = new ArrayList<>();
        for (MultiRateDefinition rd : rateDefs) {
            AssetState st = getOrInitAssetState(rd, now);
            RateDataResponse resp = buildRateDataResponse(rd, st, now);
            saveRateDataResponse(rd.getRateName(), resp);
            responses.add(resp);
        }
        log.trace("Exiting buildClosedMarketResponses method in RateSimulatorServiceImpl.");
        return responses;
    }

    private AssetState getOrInitAssetState(MultiRateDefinition rateDef, LocalDateTime now) {
        log.trace("Entering getOrInitAssetState method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
        HashOperations<String, String, AssetState> ops = assetStateRedisTemplate.opsForHash();
        AssetState state = ops.get("ASSET_STATES", rateDef.getRateName());
        if (state == null) {
            log.warn("No existing asset state found for rate: {}, initializing new state.", rateDef.getRateName());
            state = initAssetState(rateDef, now);
            ops.put("ASSET_STATES", rateDef.getRateName(), state);
        }
        log.trace("Exiting getOrInitAssetState method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
        return state;
    }

    private AssetState initAssetState(MultiRateDefinition rateDef, LocalDateTime now) {
        log.trace("Entering initAssetState method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
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
        log.trace("Exiting initAssetState method in RateSimulatorServiceImpl: {}", rateDef.getRateName());
        return state;
    }

    private void saveAssetState(String rateName, AssetState state) {
        log.debug("Saving asset state for rate: {}", rateName);
        HashOperations<String, String, AssetState> ops = assetStateRedisTemplate.opsForHash();
        ops.put("ASSET_STATES", rateName, state);
    }

    private void saveRateDataResponse(String rateName, RateDataResponse response) {
        log.debug("Saving rate data response for rate: {}", rateName);
        HashOperations<String, String, RateDataResponse> ops = rateDataResponseRedisTemplate.opsForHash();
        ops.put("RATES", rateName, response);
    }

    private double computeDeltaTimeSeconds(LocalDateTime now) {
        if (lastUpdate == null) {
            return 1.0;
        }
        Duration diff = Duration.between(lastUpdate, now);
        return Math.max(diff.toSeconds(), 1);
    }

    private double getSessionVolMultiplier(LocalDateTime now) {
        if (simulatorProperties.getSessionVolFactors() == null) {
            return 1.0;
        }
        int hour = now.getHour();
        for (SessionVolFactor sf : simulatorProperties.getSessionVolFactors()) {
            if (hour >= sf.getStartHour() && hour < sf.getEndHour()) {
                log.debug("Applied session volatility multiplier for hour {}: {}", hour, sf.getVolMultiplier());
                return sf.getVolMultiplier();
            }
        }
        return 1.0;
    }

    private boolean checkIfWeekendGap(LocalDateTime now) {
        if (lastUpdate == null) return false;
        boolean isGap = isWeekend(lastUpdate) && !isWeekend(now);
        log.debug("Weekend gap detected: {}", isGap);
        return isGap;
    }
}
