package com.ratedistribution.rdp;

import com.ratedistribution.rdp.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(SimulatorProperties.class)
@EnableScheduling
@EnableCaching
public class RestDataProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestDataProviderApplication.class, args);
    }

}
