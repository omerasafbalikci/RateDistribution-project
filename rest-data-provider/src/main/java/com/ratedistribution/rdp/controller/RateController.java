package com.ratedistribution.rdp.controller;

import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.utilities.exceptions.RateNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
@Log4j2
public class RateController {
    private final RedisTemplate<String, RateDataResponse> rateResponseRedisTemplate;

    @GetMapping("/{rateName}")
    public ResponseEntity<RateDataResponse> getRate(@PathVariable("rateName") String rateName) {
        HashOperations<String, String, RateDataResponse> ops = this.rateResponseRedisTemplate.opsForHash();
        RateDataResponse data = ops.get("RATES", rateName);
        if (data == null) {
            throw new RateNotFoundException("Rate not found with name: " + rateName);
        }
        return ResponseEntity.ok(data);
    }
}
