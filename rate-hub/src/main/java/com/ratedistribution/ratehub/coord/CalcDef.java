package com.ratedistribution.ratehub.coord;

import com.ratedistribution.ratehub.utilities.ExpressionEvaluator;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Definition of a calculated rate configuration.
 * Holds metadata, dependency info, and runtime evaluator for the calculation engine.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
public class CalcDef {
    private final String rateName;
    private final String engine;
    private String scriptPath;
    private final Map<String, BigDecimal> helpers;
    private final Set<String> dependsOn;
    private transient ExpressionEvaluator customEvaluator;

    public CalcDef(String rateName,
                   String engine,
                   String scriptPath,
                   Map<String, BigDecimal> helpers,
                   Set<String> dependsOn) {
        this.rateName = rateName;
        this.engine = engine;
        this.scriptPath = scriptPath;
        this.helpers = helpers;
        this.dependsOn = dependsOn;
    }
}
