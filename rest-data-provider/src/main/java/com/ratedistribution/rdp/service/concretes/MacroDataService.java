package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.model.MacroIndicatorDefinition;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MacroDataService {
    private final SimulatorProperties simulatorProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    @Getter
    private List<MacroIndicatorDefinition> macroIndicators;

    @PostConstruct
    public void init() {
        this.macroIndicators = simulatorProperties.getMacroIndicators();
        if (simulatorProperties.getMacroDataApiUrl() != null) {
            fetchMacroDataFromApi();
        }
    }

    public void fetchMacroDataFromApi() {
        try {
            String url = simulatorProperties.getMacroDataApiUrl();
            MacroIndicatorDefinition[] response = restTemplate.getForObject(url, MacroIndicatorDefinition[].class);
            if (response != null) {
                this.macroIndicators = Arrays.asList(response);
            }
        } catch (Exception e) {
            // log hata
        }
    }
}
