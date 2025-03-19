package com.ratedistribution.rdp.service.abstracts;

import com.ratedistribution.rdp.model.AssetState;
import com.ratedistribution.rdp.model.MacroIndicatorDefinition;

import java.util.List;

public interface MacroDataService {
    void applyMacroData(AssetState state);
}
