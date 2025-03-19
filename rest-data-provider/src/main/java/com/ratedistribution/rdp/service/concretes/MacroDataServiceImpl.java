package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.model.AssetState;
import com.ratedistribution.rdp.model.MacroIndicatorDefinition;
import com.ratedistribution.rdp.service.abstracts.MacroDataService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Log4j2
public class MacroDataServiceImpl implements MacroDataService {
    private final SimulatorProperties simulatorProperties;

    public MacroDataServiceImpl(SimulatorProperties simulatorProperties) {
        this.simulatorProperties = simulatorProperties;
    }

    @Override
    public void applyMacroData(AssetState state) {
        log.trace("Entering applyMacroData method in MacroDataServiceImpl.");
        List<MacroIndicatorDefinition> macroIndicators = simulatorProperties.getMacroIndicators();
        if (macroIndicators == null || macroIndicators.isEmpty()) {
            log.debug("No macro indicators to apply.");
            return;
        }

        double price = state.getCurrentPrice();
        double sigma = state.getCurrentSigma();

        for (MacroIndicatorDefinition indicator : macroIndicators) {
            double driftFactor = Math.exp(-indicator.getSensitivityToDrift() * (indicator.getValue() / 100.0));
            double volFactor = 1.0 + indicator.getSensitivityToVol() * (indicator.getValue() / 1000.0);

            price = price * driftFactor;
            sigma = sigma * volFactor;
        }

        state.setCurrentPrice(price);
        state.setCurrentSigma(sigma);

        log.debug("MacroData applied -> newPrice: {}, newSigma: {}", price, sigma);
        log.trace("Exiting applyMacroData method in MacroDataServiceImpl.");
    }
}
