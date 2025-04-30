package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.model.AssetState;
import com.ratedistribution.rdp.model.EventShockDefinition;
import com.ratedistribution.rdp.model.ShockConfigDefinition;
import com.ratedistribution.rdp.service.abstracts.ShockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Log4j2
public class ShockServiceImpl implements ShockService {
    private final SimulatorProperties simulatorProperties;

    @Override
    public void processAutomaticShocks(AssetState state, Instant now) {
        log.trace("Entering processAutomaticShocks method in ShockServiceImpl.");
        double dtSeconds = 1.0;
        ShockConfigDefinition shockConfig = this.simulatorProperties.getShockConfig();
        if (shockConfig == null) {
            log.debug("Shock configuration is null.");
            return;
        }
        double smallShockPerSec = shockConfig.getSmallShockWeekly() / (7 * 24 * 3600.0);
        double mediumShockPerSec = shockConfig.getMediumShockMonthly() / (30 * 24 * 3600.0);
        double bigShockPerSec = shockConfig.getBigShockYearly() / (365 * 24 * 3600.0);

        log.debug("Shock probabilities (per sec) => Small: {}, Medium: {}, Big: {}",
                smallShockPerSec, mediumShockPerSec, bigShockPerSec);

        if (ThreadLocalRandom.current().nextDouble() < smallShockPerSec * dtSeconds) {
            applyRandomShock(state, ShockType.SMALL);
        } else if (ThreadLocalRandom.current().nextDouble() < mediumShockPerSec * dtSeconds) {
            applyRandomShock(state, ShockType.MEDIUM);
        } else if (ThreadLocalRandom.current().nextDouble() < bigShockPerSec * dtSeconds) {
            applyRandomShock(state, ShockType.BIG);
        }
        log.trace("Exiting processAutomaticShocks method in ShockServiceImpl.");
    }

    private void applyRandomShock(AssetState state, ShockType shockType) {
        log.trace("Entering applyRandomShock method in ShockServiceImpl.");
        ShockConfigDefinition config = this.simulatorProperties.getShockConfig();
        double minPct;
        double maxPct;

        switch (shockType) {
            case SMALL -> {
                minPct = config.getSmallShockMinPct();
                maxPct = config.getSmallShockMaxPct();
            }
            case MEDIUM -> {
                minPct = config.getMediumShockMinPct();
                maxPct = config.getMediumShockMaxPct();
            }
            case BIG -> {
                minPct = config.getBigShockMinPct();
                maxPct = config.getBigShockMaxPct();
            }
            default -> {
                log.error("Unknown ShockType encountered: {}", shockType);
                return;
            }
        }

        double shockMagnitude = ThreadLocalRandom.current().nextDouble(minPct, maxPct);
        shockMagnitude *= (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        double newPrice = state.getCurrentPrice() * (1.0 + shockMagnitude);
        state.setCurrentPrice(newPrice);
        log.warn("[!!! SHOCK !!!] {} shock applied | Magnitude: {} | New Price: {}",
                shockType.name(), shockMagnitude, newPrice);
        log.debug("Applying {} shock => magnitude: {}, newPrice: {}", shockType, shockMagnitude, newPrice);
        log.trace("Exiting applyRandomShock method in ShockServiceImpl.");
    }

    @Override
    public void checkAndApplyCriticalShocks(AssetState state, Instant now) {
        log.trace("Entering checkAndApplyCriticalShocks method in ShockServiceImpl.");
        if (simulatorProperties.getEventShocks() != null) {
            for (EventShockDefinition es : simulatorProperties.getEventShocks()) {
                Instant eventTime = es.getDateTime();

                if (Math.abs(Duration.between(now, eventTime).toMinutes()) < 1) {
                    double randomShock = ThreadLocalRandom.current().nextGaussian() * es.getJumpVol()
                            + es.getJumpMean();
                    double newPrice = state.getCurrentPrice() * (1.0 + randomShock);
                    state.setCurrentPrice(newPrice);

                    log.warn("[!!! CRITICAL SHOCK !!!] Event: '{}' | Mean: {} | Volatility: {} | New Price: {}",
                            es.getName(), es.getJumpMean(), es.getJumpVol(), newPrice);
                    log.debug("Critical event shock '{}' applied => jumpMean: {}, jumpVol: {}, newPrice: {}",
                            es.getName(), es.getJumpMean(), es.getJumpVol(), newPrice);
                }
            }
        }
        log.trace("Exiting checkAndApplyCriticalShocks method in ShockServiceImpl.");
    }

    private enum ShockType {SMALL, MEDIUM, BIG}
}
