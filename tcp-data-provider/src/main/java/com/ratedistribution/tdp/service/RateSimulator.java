package com.ratedistribution.tdp.service;

import com.ratedistribution.tdp.config.SimulatorConfigLoader;
import com.ratedistribution.tdp.config.SimulatorProperties;
import com.ratedistribution.tdp.dto.responses.RateDataResponse;
import com.ratedistribution.tdp.model.*;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public class RateSimulator {
    private static final Logger log = LogManager.getLogger(RateSimulator.class);
    private final SimulatorConfigLoader configLoader;
    private final HolidayCalendarService holidayCalendarService;
    private final ShockService shockService;
    private final ExecutorService executorService;
    private Instant lastUpdate = null;
    private final Map<String, AssetState> assetStateMap = new HashMap<>();
    private final Map<String, RateDataResponse> rateDataResponseMap = new HashMap<>();

    public List<RateDataResponse> updateAllRates() {
        SimulatorProperties simulatorProperties = this.configLoader.currentSimulator();
        log.trace("Entering updateAllRates method in RateSimulator.");
        List<RateDataResponse> responses = new ArrayList<>();
        List<MultiRateDefinition> rateDefinitions = simulatorProperties.getRates();

        if (rateDefinitions == null || rateDefinitions.isEmpty()) {
            log.warn("No rate definitions found. Exiting update process.");
            log.trace("Exiting updateAllRates method in RateSimulator.");
            return responses;
        }

        Instant now = Instant.now();
        log.debug("Starting rate update process at: {}", now);
        double deltaTimeSeconds = computeDeltaTimeSeconds(now);
        log.debug("Computed delta time in seconds: {}", deltaTimeSeconds);
        boolean isHoliday = this.holidayCalendarService.isHoliday(now);
        boolean isWeekend = isWeekend(now);
        log.debug("Market status - isWeekend: {}, isHoliday: {}", isWeekend, isHoliday);

        if (isWeekend || isHoliday) {
            log.info("Market is closed (weekend/holiday). Returning closed market responses.");
            this.lastUpdate = now;
            log.trace("Exiting updateAllRates method in RateSimulator.");
            return buildClosedMarketResponses(rateDefinitions, now);
        }

        boolean doWeekendGap = checkIfWeekendGap(now);
        log.debug("Weekend gap adjustment required: {}", doWeekendGap);
        List<Future<RateDataResponse>> futures = new ArrayList<>();

        for (MultiRateDefinition rateDefinition : rateDefinitions) {
            Future<RateDataResponse> future = this.executorService.submit(() -> {
                log.info("Processing rate update: {}", rateDefinition.getRateName());
                AssetState oldState = getOrInitAssetState(rateDefinition, now);
                double randomFactor = ThreadLocalRandom.current().nextGaussian();

                log.debug("Current config for {}: drift={}, omega={}, alpha={}, beta={}",
                        rateDefinition.getRateName(),
                        rateDefinition.getDrift(),
                        rateDefinition.getGarchParams().getOmega(),
                        rateDefinition.getGarchParams().getAlpha(),
                        rateDefinition.getGarchParams().getBeta());

                AssetState updatedState = updatePriceAndVolatility(rateDefinition,
                        oldState,
                        randomFactor,
                        now,
                        deltaTimeSeconds,
                        doWeekendGap,
                        simulatorProperties
                );
                log.info("Updated price and volatility for rate: {}", rateDefinition.getRateName());

                shockService.processAutomaticShocks(updatedState);
                log.debug("Processed automatic shocks for rate: {}", rateDefinition.getRateName());
                shockService.checkAndApplyCriticalShocks(updatedState, now);
                log.debug("Checked and applied critical shocks for rate: {}", rateDefinition.getRateName());

                assetStateMap.put(rateDefinition.getRateName(), updatedState);
                log.debug("Saved asset state for rate: {}", rateDefinition.getRateName());

                RateDataResponse response = buildRateDataResponse(rateDefinition, updatedState, now);
                rateDataResponseMap.put(rateDefinition.getRateName(), response);
                log.debug("Saved rate data response for rate: {}", rateDefinition.getRateName());
                return response;
            });
            futures.add(future);
        }

        for (Future<RateDataResponse> future : futures) {
            try {
                responses.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        this.lastUpdate = now;
        log.trace("Exiting updateAllRates method in RateSimulator.");
        return responses;
    }

    private boolean isWeekend(Instant instant) {
        DayOfWeek dow = instant.atZone(ZoneId.systemDefault()).getDayOfWeek();
        log.debug("Checked if date {} is dow: {}", instant, dow);
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private boolean checkIfWeekendGap(Instant now) {
        if (lastUpdate == null) return false;
        boolean isGap = isWeekend(lastUpdate) && !isWeekend(now);
        log.debug("Weekend gap detected: {}", isGap);
        return isGap;
    }

    private double computeDeltaTimeSeconds(Instant now) {
        if (lastUpdate == null) {
            return 1.0;
        }
        Duration diff = Duration.between(lastUpdate, now);
        return Math.max(diff.toSeconds(), 1);
    }

    private AssetState getOrInitAssetState(MultiRateDefinition rateDef, Instant now) {
        log.trace("Entering getOrInitAssetState method in RateSimulator: {}", rateDef.getRateName());
        AssetState state = assetStateMap.get(rateDef.getRateName());
        String newConfigSignature = buildConfigSignature(rateDef);

        if (state == null) {
            log.warn("No existing asset state found for rate: {}, initializing new state.", rateDef.getRateName());
            state = initAssetState(rateDef, now);
            state.setConfigSignature(newConfigSignature);
            assetStateMap.put(rateDef.getRateName(), state);
        } else if (!newConfigSignature.equals(state.getConfigSignature())) {
            log.info("Detected config change for {}. Applying partial re-init...", rateDef.getRateName());
            double oldPrice = state.getCurrentPrice();
            double initSigma = Math.sqrt(rateDef.getGarchParams().getOmega());
            state.setCurrentSigma(initSigma);
            state.setLastReturn(0.0);
            state.setStepsInRegime(0);
            state.setCurrentPrice(oldPrice);
            state.setConfigSignature(newConfigSignature);
            assetStateMap.put(rateDef.getRateName(), state);
        }

        log.trace("Exiting getOrInitAssetState method in RateSimulator: {}", rateDef.getRateName());
        return state;
    }

    private AssetState initAssetState(MultiRateDefinition rateDef, Instant now) {
        log.trace("Entering initAssetState method in RateSimulator: {}", rateDef.getRateName());
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
        state.setLastUpdateEpochMillis(now.toEpochMilli());
        state.setCurrentDay(LocalDate.ofInstant(now, ZoneId.systemDefault()));
        log.trace("Exiting initAssetState method in RateSimulator: {}", rateDef.getRateName());
        return state;
    }

    private String buildConfigSignature(MultiRateDefinition rateDefinition) {
        return "drift=" + rateDefinition.getDrift()
                + "|omega=" + rateDefinition.getGarchParams().getOmega()
                + "|alpha=" + rateDefinition.getGarchParams().getAlpha()
                + "|beta=" + rateDefinition.getGarchParams().getBeta()
                + "|spread=" + rateDefinition.getBaseSpread()
                + "|meanRev=" + rateDefinition.isUseMeanReversion()
                + "|kappa=" + rateDefinition.getKappa()
                + "|theta=" + rateDefinition.getTheta();
    }

    private AssetState updatePriceAndVolatility(MultiRateDefinition rateDefinition,
                                                AssetState oldState,
                                                double randomFactor,
                                                Instant now,
                                                double deltaTimeSeconds,
                                                boolean weekendGap,
                                                SimulatorProperties simulatorProperties) {
        log.trace("Entering updatePriceAndVolatility method in RateSimulator: {}", rateDefinition.getRateName());
        double oldPrice = oldState.getCurrentPrice();
        double oldSigma = oldState.getCurrentSigma();
        double oldRet = oldState.getLastReturn();
        log.debug("Old price: {}, Old sigma: {}, Old return: {}", oldPrice, oldSigma, oldRet);

        double newSigma = garchUpdate(rateDefinition.getGarchParams(), oldState);
        log.debug("New sigma computed: {}", newSigma);

        double driftAdjustment = getDriftAdjustment(rateDefinition.getDrift(), deltaTimeSeconds);
        log.debug("Drift adjustment: {}", driftAdjustment);

        double logReturn = driftAdjustment + newSigma * randomFactor;
        double newPrice = oldPrice * Math.exp(logReturn);
        log.debug("Price updated from {} to {} (pre-session adjustments)", oldPrice, newPrice);

        double volMultiplier = getSessionVolMultiplier(now, simulatorProperties);
        double delta = newPrice - oldPrice;
        newPrice = oldPrice + delta * volMultiplier;
        log.debug("Session vol multiplier: {}, final newPrice: {}", volMultiplier, newPrice);

        if (rateDefinition.isUseMeanReversion()) {
            newPrice = applyMeanReversion(oldPrice, newPrice, rateDefinition.getKappa(), rateDefinition.getTheta(), deltaTimeSeconds);
            log.debug("Applied mean reversion, New price: {}", newPrice);
        }

        if (weekendGap) {
            log.debug("Applying weekend gap adjustment for rate: {}", rateDefinition.getRateName());
            double gapRandom = ThreadLocalRandom.current().nextGaussian() * simulatorProperties.getWeekendGapVolatility();
            double gapFactor = Math.exp(gapRandom * simulatorProperties.getWeekendShockFactor());
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
        log.trace("Exiting updatePriceAndVolatility method in RateSimulator: {}", rateDefinition.getRateName());
        return updatedState;
    }

    private double garchUpdate(GarchParams params, AssetState oldState) {
        log.trace("Entering garchUpdate method in RateSimulator.");
        double oldSigma = oldState.getCurrentSigma();
        double oldRet = oldState.getLastReturn();

        double omega = params.getOmega();
        double alpha = params.getAlpha();
        double beta = params.getBeta();

        double oldVar = oldSigma * oldSigma;
        double newVar = omega + alpha * (oldRet * oldRet) + beta * oldVar;
        double result = Math.sqrt(Math.max(newVar, 1e-15));
        log.debug("GARCH(1,1) variance: {}, sigma: {}", newVar, result);
        log.trace("Exiting garchUpdate method in RateSimulator.");
        return result;
    }

    private double getDriftAdjustment(double driftAnnual, double dtSeconds) {
        log.trace("Entering getDriftAdjustment method in RateSimulator.");
        double yearInSeconds = 365.0 * 24.0 * 3600.0;
        double driftPerSecond = driftAnnual / yearInSeconds;
        log.trace("Exiting getDriftAdjustment method in RateSimulator.");
        return driftPerSecond * dtSeconds;
    }

    private double applyMeanReversion(double oldPrice,
                                      double newPrice,
                                      double kappa,
                                      double theta,
                                      double dtSeconds) {
        log.trace("Entering applyMeanReversion method in RateSimulator.");
        double adjustedPrice = oldPrice + kappa * dtSeconds * (theta - oldPrice) + (newPrice - oldPrice);
        log.trace("Exiting applyMeanReversion method in RateSimulator.");
        return adjustedPrice;
    }

    private VolRegime checkRegimeSwitch(double sigma) {
        log.trace("Entering checkRegimeSwitch method in RateSimulator.");
        VolRegime regime;
        if (sigma > 0.02) {
            regime = VolRegime.HIGH_VOL;
        } else if (sigma < 0.005) {
            regime = VolRegime.LOW_VOL;
        } else {
            regime = VolRegime.MID_VOL;
        }
        log.debug("Determined volatility regime: {}", regime);
        log.trace("Exiting checkRegimeSwitch method in RateSimulator.");
        return regime;
    }

    private AssetState createNewStateFromOld(AssetState oldState, double newPrice, double newSigma, Instant now, VolRegime newRegime) {
        log.trace("Entering createNewStateFromOld method in RateSimulatorServiceImpl.");
        AssetState state = new AssetState();
        state.setCurrentPrice(newPrice);
        state.setCurrentSigma(newSigma);

        double ret = 0.0;
        if (oldState.getCurrentPrice() > 0) {
            ret = Math.log(newPrice / oldState.getCurrentPrice());
        }
        state.setLastReturn(ret);
        log.debug("Computed return: {}", ret);

        LocalDate oldDay = oldState.getCurrentDay();
        LocalDate nowDay = LocalDate.ofInstant(now, ZoneId.systemDefault());

        if (!oldDay.isEqual(nowDay)) {
            log.info("New day detected -> resetting daily open/high/low/volume");
            state.setDayOpen(newPrice);
            state.setDayHigh(newPrice);
            state.setDayLow(newPrice);
            state.setDayVolume(0L);
            state.setCurrentDay(nowDay);
        } else {
            state.setDayOpen(oldState.getDayOpen());
            state.setDayHigh(Math.max(oldState.getDayHigh(), newPrice));
            state.setDayLow(Math.min(oldState.getDayLow(), newPrice));
            state.setDayVolume(oldState.getDayVolume() + getRandomVolume());
            state.setCurrentDay(oldDay);
        }

        if (newRegime != oldState.getCurrentRegime()) {
            state.setCurrentRegime(newRegime);
            state.setStepsInRegime(0);
        } else {
            state.setCurrentRegime(oldState.getCurrentRegime());
            state.setStepsInRegime(oldState.getStepsInRegime() + 1);
        }
        state.setConfigSignature(oldState.getConfigSignature());
        state.setLastUpdateEpochMillis(now.toEpochMilli());

        log.trace("Exiting createNewStateFromOld method in RateSimulatorServiceImpl.");
        return state;
    }

    private long getRandomVolume() {
        long volume = ThreadLocalRandom.current().nextInt(1, 10);
        log.debug("Generated random volume: {}", volume);
        return volume;
    }

    private double getSessionVolMultiplier(Instant now, SimulatorProperties simulatorProperties) {
        if (simulatorProperties.getSessionVolFactors() == null) {
            return 1.0;
        }
        int hour = now.atZone(ZoneId.systemDefault()).getHour();
        for (SessionVolFactor sf : simulatorProperties.getSessionVolFactors()) {
            if (hour >= sf.getStartHour() && hour < sf.getEndHour()) {
                log.debug("Applied session volatility multiplier for hour {}: {}", hour, sf.getVolMultiplier());
                return sf.getVolMultiplier();
            }
        }
        return 1.0;
    }

    private RateDataResponse buildRateDataResponse(MultiRateDefinition rateDef,
                                                   AssetState state,
                                                   Instant now) {
        log.trace("Entering buildRateDataResponse method in RateSimulator: {}", rateDef.getRateName());
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
        log.trace("Exiting buildRateDataResponse method in RateSimulator: {}", rateDef.getRateName());
        return response;
    }

    private List<RateDataResponse> buildClosedMarketResponses(List<MultiRateDefinition> rateDefinitions,
                                                              Instant now) {
        log.trace("Entering buildClosedMarketResponses method in RateSimulator.");
        List<RateDataResponse> responses = new ArrayList<>();
        for (MultiRateDefinition rd : rateDefinitions) {
            AssetState state = this.assetStateMap.get(rd.getRateName());
            if (state == null) {
                state = initAssetState(rd, now);
                this.assetStateMap.put(rd.getRateName(), state);
            }
            RateDataResponse response = buildRateDataResponse(rd, state, now);
            rateDataResponseMap.put(rd.getRateName(), response);
            responses.add(response);
        }
        log.trace("Exiting buildClosedMarketResponses method in RateSimulator.");
        return responses;
    }
}
