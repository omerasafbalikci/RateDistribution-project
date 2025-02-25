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

    /**
     * Bu metot, her güncelleme döngüsünde çağrılacak.
     * Mevcut makro indikatörleri okuyarak varlık fiyatına veya volatilitesine etki uygular.
     */
    @Override
    public void applyMacroData(AssetState state) {
        List<MacroIndicatorDefinition> macroIndicators = simulatorProperties.getMacroIndicators();
        if (macroIndicators == null || macroIndicators.isEmpty()) {
            return;
        }

        double price = state.getCurrentPrice();
        double sigma = state.getCurrentSigma();

        // Tüm indikatörler üzerinden birikimli etki uygula (örnek basit bir model)
        for (MacroIndicatorDefinition indicator : macroIndicators) {
            // Örneğin drift etkisini, currentPrice'a minik bir exponential faktör olarak ekle
            // (Birebir real bir formül değil, sadece basit bir yaklaşım örneği)
            double driftFactor = Math.exp(-indicator.getSensitivityToDrift() * (indicator.getValue() / 100.0));
            double volFactor = 1.0 + indicator.getSensitivityToVol() * (indicator.getValue() / 1000.0);

            // Price ve sigma üzerinde küçük düzeltme
            price = price * driftFactor;
            sigma = sigma * volFactor;
        }

        // Yeni değerleri geri yaz
        state.setCurrentPrice(price);
        state.setCurrentSigma(sigma);

        log.debug("MacroData applied -> newPrice: {}, newSigma: {}", price, sigma);
    }

    @Override
    public List<MacroIndicatorDefinition> getAllMacroIndicators() {
        return simulatorProperties.getMacroIndicators();
    }
}
