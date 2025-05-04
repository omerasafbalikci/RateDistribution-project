package com.ratedistribution.gateway;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages({
        "com.ratedistribution.gateway.config",
        "com.ratedistribution.gateway.controller"
})
class ApiGatewaySuite {
}
