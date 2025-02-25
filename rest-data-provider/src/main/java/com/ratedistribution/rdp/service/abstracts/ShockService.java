package com.ratedistribution.rdp.service.abstracts;

import com.ratedistribution.rdp.model.AssetState;

import java.time.LocalDateTime;

public interface ShockService {
    /**
     * Otomatik (küçük, orta, büyük) şokların gerçekleşip gerçekleşmeyeceğini
     * kontrol eder ve varsa ilgili AssetState'i günceller.
     */
    void processAutomaticShocks(AssetState state, LocalDateTime now);

    /**
     * application.yml'de tanımlanan kritik (event) şokları kontrol edip
     * zamanı gelmişse uygular.
     */
    void checkAndApplyCriticalShocks(AssetState state, LocalDateTime now);
}
