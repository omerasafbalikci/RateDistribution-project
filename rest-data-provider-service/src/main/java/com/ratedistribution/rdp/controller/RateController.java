package com.ratedistribution.rdp.controller;

import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.service.abstracts.RateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RateService rateService;

    @GetMapping("/{rateName}")
    public ResponseEntity<RateDataResponse> getRate(@PathVariable("rateName") String rateName) {
        RateDataResponse response = this.rateService.getRate(rateName);
        return ResponseEntity.ok(response);
    }
}
