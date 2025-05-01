package com.ratedistribution.auth;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages({
        "com.ratedistribution.auth.controller",
        "com.ratedistribution.auth.service.concretes"
})
class AuthServiceSuite {
}
