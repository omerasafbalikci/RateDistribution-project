package com.ratedistribution.usermanagement;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages({
        "com.ratedistribution.usermanagement.controller",
        "com.ratedistribution.usermanagement.service.concretes"
})
class UserManagementServiceSuite {
}
