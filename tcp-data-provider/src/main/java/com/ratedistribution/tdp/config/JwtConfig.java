package com.ratedistribution.tdp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class JwtConfig {
    @JsonProperty("secret-key")
    private String secretKey;
    @JsonProperty("authorities-key")
    private String authoritiesKey;
}
