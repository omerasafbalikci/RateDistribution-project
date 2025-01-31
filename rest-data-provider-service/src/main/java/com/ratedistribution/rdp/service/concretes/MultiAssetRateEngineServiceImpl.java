package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.AssetState;
import com.ratedistribution.rdp.model.MultiRateDefinition;
import com.ratedistribution.rdp.utilities.CorrelatedRandomVectorGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultiAssetRateEngineServiceImpl {

    private final SimulatorProperties simulatorProperties;
    private final RedisTemplate<String, AssetState> assetStateRedisTemplate;

    // assetState'leri "ASSET_STATES" isimli bir Hash'te tutacağız
    private HashOperations<String, String, AssetState> stateOps;

    // Korele rastgele sayı üretici
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

        // Her parite için initial state Redis'e koy
        simulatorProperties.getRates().forEach(def -> {
            AssetState existing = stateOps.get("ASSET_STATES", def.getRateName());
            if (existing == null) {
                AssetState initState = new AssetState(def.getInitialPrice(), 0.01, 0.0);
                stateOps.put("ASSET_STATES", def.getRateName(), initState);
            }
        });
    }

    /**
     * Tüm pariteleri EŞZAMANLI olarak günceller.
     * Dönüş: her parite için RateDataResponse listesi.
     */
    public List<RateDataResponse> updateAllRates() {
        List<MultiRateDefinition> definitions = simulatorProperties.getRates();
        int n = definitions.size();

        // korele random vector
        double[] epsVector = correlatedRng.sample(); // boyutu n

        double dt = 1.0; // basitlik adına her scheduled adım için 1 time unit

        List<RateDataResponse> result = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            MultiRateDefinition def = definitions.get(i);
            String rateName = def.getRateName();

            AssetState oldState = stateOps.get("ASSET_STATES", rateName);
            if (oldState == null) {
                // Eğer Redis'te yoksa, default oluştur
                oldState = new AssetState(def.getInitialPrice(), 0.01, 0.0);
            }

            double oldPrice = oldState.getCurrentPrice();
            double oldSigma = oldState.getCurrentSigma();
            double oldRet = oldState.getLastReturn();

            // -- 1) GARCH(1,1) Volatilite Hesabı
            double newSigmaSq = def.getGarchParams().getOmega()
                    + def.getGarchParams().getAlpha() * (oldRet * oldRet)
                    + def.getGarchParams().getBeta() * (oldSigma * oldSigma);
            if (newSigmaSq < 1e-12) newSigmaSq = 1e-12;
            double newSigma = Math.sqrt(newSigmaSq);

            // -- 2) Normal Parça (korelasyonlu epsVector[i])
            double normPart = newSigma * Math.sqrt(dt) * epsVector[i];

            // -- 3) Drift Parçası
            double driftPart = def.getDrift() * dt;

            // -- 4) Jump Diffusion (Merton)
            double jumpPart = 0.0;
            double lambda = def.getJumpIntensity();
            // Poisson ortalama (lambda * dt)
            double p = 1 - Math.exp(-lambda * dt);
            if (Math.random() < p) {
                // Tek jump varsayımı
                double j = randomNormal() * def.getJumpVol() + def.getJumpMean();
                jumpPart = j;
            }

            // -- 5) Mean Reversion (Opsiyonel)
            double mrPart = 0.0;
            if (def.isUseMeanReversion()) {
                double kappa = def.getKappa();
                double theta = def.getTheta();
                // log-fiyat bazında OU yapmak
                double logPrice = Math.log(oldPrice);
                double logTheta = Math.log(theta);
                mrPart = kappa * (logTheta - logPrice) * dt;
            }

            // -- 6) Toplam log-return
            double r_t = driftPart + normPart + jumpPart + mrPart;

            // -- 7) Yeni fiyat
            double newPrice = oldPrice * Math.exp(r_t);
            if (newPrice < 0.0001) newPrice = 0.0001;

            // -- 8) Bid-Ask spread
            double spr = def.getBaseSpread();
            double bid = newPrice - spr / 2.0;
            double ask = newPrice + spr / 2.0;
            if (bid < 0.0001) bid = 0.0001;
            if (ask < bid) ask = bid + 0.0001;

            // -- 9) Redis'e yeni state yaz
            AssetState newState = new AssetState(newPrice, newSigma, r_t);
            stateOps.put("ASSET_STATES", rateName, newState);

            // -- 10) RateDataResponse
            RateDataResponse resp = new RateDataResponse(rateName, bid, ask, LocalDateTime.now());
            result.add(resp);
        }

        return result;
    }

    // Basit normal random
    private double randomNormal() {


        return ThreadLocalRandom.current().nextGaussian();
    }

}
