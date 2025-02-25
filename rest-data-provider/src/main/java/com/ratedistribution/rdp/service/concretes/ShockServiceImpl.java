package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.model.AssetState;
import com.ratedistribution.rdp.model.EventShockDefinition;
import com.ratedistribution.rdp.model.ShockConfigDefinition;
import com.ratedistribution.rdp.service.abstracts.ShockService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Log4j2
public class ShockServiceImpl implements ShockService {
    private final SimulatorProperties simulatorProperties;

    public ShockServiceImpl(SimulatorProperties simulatorProperties) {
        this.simulatorProperties = simulatorProperties;
    }

    @Override
    public void processAutomaticShocks(AssetState state, LocalDateTime now) {
        double dtSeconds = 1.0;
        ShockConfigDefinition shockCfg = this.simulatorProperties.getShockConfig();
        double smallShockPerSec  = shockCfg.getSmallShockWeekly()  / (7 * 24 * 3600.0);
        double mediumShockPerSec = shockCfg.getMediumShockMonthly() / (30 * 24 * 3600.0);
        double bigShockPerSec    = shockCfg.getBigShockYearly()    / (365 * 24 * 3600.0);

        if (ThreadLocalRandom.current().nextDouble() < smallShockPerSec * dtSeconds) {
            applyRandomShock(state, ShockType.SMALL);
        } else if (ThreadLocalRandom.current().nextDouble() < mediumShockPerSec * dtSeconds) {
            applyRandomShock(state, ShockType.MEDIUM);
        } else if (ThreadLocalRandom.current().nextDouble() < bigShockPerSec * dtSeconds) {
            applyRandomShock(state, ShockType.BIG);
        }
    }

    private void applyRandomShock(AssetState state, ShockType shockType) {
        ShockConfigDefinition cfg = this.simulatorProperties.getShockConfig();
        double minPct;
        double maxPct;

        switch (shockType) {
            case SMALL -> {
                minPct = cfg.getSmallShockMinPct();
                maxPct = cfg.getSmallShockMaxPct();
            }
            case MEDIUM -> {
                minPct = cfg.getMediumShockMinPct();
                maxPct = cfg.getMediumShockMaxPct();
            }
            case BIG -> {
                minPct = cfg.getBigShockMinPct();
                maxPct = cfg.getBigShockMaxPct();
            }
            default -> {
                minPct = 0.0;
                maxPct = 0.0;
            }
        }

        double shockMagnitude = ThreadLocalRandom.current().nextDouble(minPct, maxPct);
        // +/- rastgele
        shockMagnitude *= (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);

        double newPrice = state.getCurrentPrice() * (1.0 + shockMagnitude);
        state.setCurrentPrice(newPrice);

        log.info("Applying {} shock => magnitude: {}, newPrice: {}", shockType, shockMagnitude, newPrice);
    }

    @Override
    public void checkAndApplyCriticalShocks(AssetState state, LocalDateTime now) {
        if (simulatorProperties.getEventShocks() != null) {
            for (EventShockDefinition es : simulatorProperties.getEventShocks()) {
                LocalDateTime eventTime = LocalDateTime.ofInstant(es.getDateTime(), ZoneOffset.UTC);

                // Örnek: eventTime ile şu an arasında 1 dakika (abs) fark varsa tetikle
                if (Math.abs(Duration.between(now, eventTime).toMinutes()) < 1) {
                    // jumpMean ve jumpVol'u kullan:
                    // Normal dağılımdan bir rastgele shock: N(jumpMean, jumpVol^2)
                    double randomShock = ThreadLocalRandom.current().nextGaussian() * es.getJumpVol()
                            + es.getJumpMean();

                    // Bu randomShock’u (ör. 0.02 ise %2) +1 şeklinde çarparak fiyatı etkileyelim
                    double newPrice = state.getCurrentPrice() * (1.0 + randomShock);
                    state.setCurrentPrice(newPrice);

                    log.info("Critical event shock '{}' applied => jumpMean: {}, jumpVol: {}, newPrice: {}",
                            es.getName(), es.getJumpMean(), es.getJumpVol(), newPrice);
                }
            }
        }
    }

    private enum ShockType { SMALL, MEDIUM, BIG }
}
