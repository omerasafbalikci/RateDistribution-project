package com.ratedistribution.ratehub.subscriber;

import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;

public interface RateListener {
    void onConnect(String platform, boolean status);

    void onDisconnect(String platform, boolean status);

    void onRateAvailable(String platform, String rateName, Rate fullRate);

    void onRateUpdate(String platform, String rateName, RateFields delta);

    void onRateStatus(String platform, String rateName, RateStatus status);

    void onRateError(String platformName, String rateName, Throwable error);
}
