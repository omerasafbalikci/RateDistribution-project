package com.ratedistribution.rdp.controller;

import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.service.abstracts.RateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rates")
public class RateController {
    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping("/{rateName}")
    public ResponseEntity<RateDataResponse> getRate(@PathVariable String rateName) {
        RateDataResponse response = this.rateService.getRate(rateName);
        return ResponseEntity.ok(response);
    }
}
