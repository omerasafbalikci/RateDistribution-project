package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.RateDefinition;
import com.ratedistribution.rdp.service.abstracts.RateService;
import com.ratedistribution.rdp.utilities.exceptions.RateNotFoundException;
import jakarta.annotation.PostConstruct;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;

@Service
public class RateServiceImpl implements RateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateServiceImpl.class);

    // Redis HashOperations for RateDataResponse
    private final HashOperations<String, String, RateDataResponse> rateOps;

    // Redis HashOperations for midPrices
    private final HashOperations<String, String, Double> midOps;

    // Redis HashOperations for ShockState
    private final HashOperations<String, String, ShockState> shockOps;

    private final SimulatorProperties simulatorProperties;
    private final NormalDistribution normalDistribution = new NormalDistribution(0, 1);

    private int updateCount = 0;

    public RateServiceImpl(
            RedisTemplate<String, RateDataResponse> rateDataRedisTemplate,
            RedisTemplate<String, Double> doubleRedisTemplate,
            RedisTemplate<String, ShockState> shockStateRedisTemplate,
            SimulatorProperties simulatorProperties
    ) {
        this.rateOps = rateDataRedisTemplate.opsForHash();
        this.midOps = doubleRedisTemplate.opsForHash();
        this.shockOps = shockStateRedisTemplate.opsForHash();
        this.simulatorProperties = simulatorProperties;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("Initializing RateService with OU + dynamic shock + dynamic spread...");

        if (simulatorProperties.getRates() != null) {
            simulatorProperties.getRates().forEach(def -> {
                double initialMid = def.getBasePrice();
                double baseSpread = def.getBaseSpread();

                double bid = Math.max(0.0001, initialMid - baseSpread / 2.0);
                double ask = Math.max(0.0001, initialMid + baseSpread / 2.0);

                RateDataResponse initialData = new RateDataResponse(
                        def.getRateName(), bid, ask, LocalDateTime.now()
                );
                rateOps.put("rates", def.getRateName(), initialData);

                midOps.put("midPrices", def.getRateName(), initialMid);

                // ShockState'i Redis'e koy (shockLevel=1.0 => normal)
                ShockState state = new ShockState(false, 0, 1.0);
                shockOps.put("shockStates", def.getRateName(), state);

                LOGGER.debug("Initialized rate={} with mid={}, bid={}, ask={}",
                        def.getRateName(), initialMid, bid, ask);
            });
        }
    }

    @Override
    public void updateRates() {
        // maxUpdates kontrolü
        if (simulatorProperties.getMaxUpdates() > 0 && updateCount >= simulatorProperties.getMaxUpdates()) {
            LOGGER.debug("Max updates reached. No further updates.");
            return;
        }

        // dt: zaman adımı (yıllık oranda)
        double dt = simulatorProperties.getUpdateIntervalMillis()
                / (1000.0 * 3600.0 * 24.0 * 365.0);

        simulatorProperties.getRates().forEach(def -> {
            String rateName = def.getRateName();

            // Eski mid'i Redis'ten al
            Double oldMid = midOps.get("midPrices", rateName);
            if (oldMid == null) {
                oldMid = def.getBasePrice();
            }

            // OU parametreleri
            double kappa = def.getKappa();
            double theta = def.getTheta();
            double sigma = def.getVolatility();

            // shockLevel hesapla (shockState'i Redis'ten al, güncelle)
            double shockLevel = handleShock(def, rateName, dt);

            // Ornstein-Uhlenbeck Discretization:
            // X_{t+dt} = X_t + kappa*(theta - X_t)*dt + (sigma*shockLevel)*sqrt(dt)*Z
            double X_t = oldMid;
            double Z = normalDistribution.sample();
            double effectiveSigma = sigma * shockLevel;
            double X_t_next = X_t + kappa*(theta - X_t)*dt + effectiveSigma*Math.sqrt(dt)*Z;
            if (X_t_next < 0.0001) X_t_next = 0.0001;

            // Spread Hesabı (dinamik):
            // Örnek: dynamicSpread = baseSpread * [1 + alpha*(shockLevel-1)]
            double baseSpread = def.getBaseSpread();
            double alpha = simulatorProperties.getDynamicSpreadAlpha();
            double dynamicSpread = baseSpread * (1.0 + alpha*(shockLevel - 1.0));
            if (dynamicSpread < 0.0001) dynamicSpread = 0.0001;

            double newBid = X_t_next - (dynamicSpread/2.0);
            double newAsk = X_t_next + (dynamicSpread/2.0);
            if (newBid < 0.0001) newBid = 0.0001;
            if (newAsk < newBid) newAsk = newBid + 0.0001;

            // Redis'e yaz
            RateDataResponse updatedData = new RateDataResponse(rateName, newBid, newAsk, LocalDateTime.now());
            rateOps.put("rates", rateName, updatedData);
            midOps.put("midPrices", rateName, X_t_next);

            LOGGER.debug("rate={} OU-> midOld={} midNew={} bid={} ask={} shockLevel={}",
                    rateName, oldMid, X_t_next, newBid, newAsk, shockLevel);
        });

        updateCount++;
    }

    @Override
    public RateDataResponse getRate(String rateName) {
        RateDataResponse data = rateOps.get("rates", rateName);
        if (data == null) {
            throw new RateNotFoundException("Rate not found: " + rateName);
        }
        return data;
    }

    /**
     * handleShock: shockState'teki shockLevel'i kademeli azaltan bir yaklaşım.
     * 1) Rastgele tetiklenirse shockLevel=shockMultiplier
     * 2) Her update'te shockLevel *= e^(-decayRate*dt)
     * 3) shockLevel 1'e yakınsa (1.01 veya altı) shockActive=false
     */
    private double handleShock(RateDefinition def, String rateName, double dt) {
        ShockState state = shockOps.get("shockStates", rateName);
        if (state == null) {
            state = new ShockState(false, 0, 1.0);
        }

        double shockLevel = state.getShockLevel();

        if (state.active) {
            // Kademeli azalma
            double decay = def.getShockDecayRate(); // e.g. 0.5
            shockLevel = shockLevel * Math.exp(-decay * dt);

            state.durationLeft--;
            if (shockLevel < 1.01 || state.durationLeft <= 0) {
                // Artık normal seviyeye çok yaklaştı veya süre doldu
                state.active = false;
                shockLevel = 1.0;
            }
        } else {
            // Şok devre dışı, rastgele tetiklenebilir
            if (Math.random() < def.getShockProbability()) {
                state.active = true;
                state.durationLeft = def.getShockDuration();
                shockLevel = def.getShockMultiplier();
            }
        }

        state.setShockLevel(shockLevel);
        shockOps.put("shockStates", rateName, state);
        return shockLevel;
    }

    /**
     * ShockState: Redis'e yazılabilir olması için Serializable işaretledik.
     * shockLevel => 1.0 normal, >1.0 şok durumu
     */
    public static class ShockState implements Serializable {
        private boolean active;
        private int durationLeft;
        private double shockLevel;

        public ShockState() { }

        public ShockState(boolean active, int durationLeft, double shockLevel) {
            this.active = active;
            this.durationLeft = durationLeft;
            this.shockLevel = shockLevel;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public int getDurationLeft() {
            return durationLeft;
        }

        public void setDurationLeft(int durationLeft) {
            this.durationLeft = durationLeft;
        }

        public double getShockLevel() {
            return shockLevel;
        }

        public void setShockLevel(double shockLevel) {
            this.shockLevel = shockLevel;
        }
    }
}
