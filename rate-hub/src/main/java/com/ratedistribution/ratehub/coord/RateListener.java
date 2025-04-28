package com.ratedistribution.ratehub.coord;

import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.model.RawTick;

public interface RateListener {
    void onConnect(String platform, boolean status);

    void onDisconnect(String platform, boolean status);

    void onRateAvailable(String platform, String rateName, RawTick rawTick);

    void onRateUpdate(String platform, String rateName, RateFields delta);

    void onRateStatus(String platform, String rateName, RateStatus status);

    void onRateError(String platformName, String rateName, Throwable error);
}
