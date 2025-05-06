package com.ratedistribution.ratehub.coord;

import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.model.RawTick;

/**
 * Listener interface for receiving real-time rate events from data sources.
 * Implementers can react to connections, tick updates, rate status changes, and errors.
 *
 * @author Ömer Asaf BALIKÇI
 */

public interface RateListener {
    /**
     * Called when a connection to the platform is established or lost.
     *
     * @param platform the name of the platform
     * @param status   true if connected, false if disconnected
     */
    void onConnect(String platform, boolean status);

    /**
     * Called when a platform disconnects unexpectedly or intentionally.
     *
     * @param platform the name of the platform
     * @param status   current status after disconnection
     */
    void onDisconnect(String platform, boolean status);

    /**
     * Called when a new rate becomes available for the first time.
     *
     * @param platform the source platform
     * @param rateName the rate symbol (e.g. "USDTRY")
     * @param rawTick  the full tick data
     */
    void onRateAvailable(String platform, String rateName, RawTick rawTick);

    /**
     * Called when a new rate update is received for an existing symbol.
     *
     * @param platform the source platform
     * @param rateName the rate symbol
     * @param delta    the changed bid/ask/timestamp fields
     */
    void onRateUpdate(String platform, String rateName, RateFields delta);

    /**
     * Called when the status of a rate changes (e.g., suspended, halted, etc.).
     *
     * @param platform the source platform
     * @param rateName the rate symbol
     * @param status   the new status
     */
    void onRateStatus(String platform, String rateName, RateStatus status);

    /**
     * Called when an error occurs while processing or receiving rate data.
     *
     * @param platformName the name of the platform
     * @param rateName     the rate symbol
     * @param error        the exception that occurred
     */
    void onRateError(String platformName, String rateName, Throwable error);
}
