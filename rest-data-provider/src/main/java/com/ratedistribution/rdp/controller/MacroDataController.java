package com.ratedistribution.rdp.controller;

import com.ratedistribution.rdp.model.MacroIndicatorDefinition;
import com.ratedistribution.rdp.service.abstracts.MacroDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/macro")
@RequiredArgsConstructor
@Log4j2
public class MacroDataController {
    private final MacroDataService macroDataService;

    @GetMapping
    public List<MacroIndicatorDefinition> getMacroIndicators() {
        return macroDataService.getAllMacroIndicators();
    }
}
