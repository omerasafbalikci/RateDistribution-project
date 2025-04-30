package com.ratedistribution.ratehub.coord;

import com.ratedistribution.ratehub.utilities.ExpressionEvaluator;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public class CalcDef {
    private final String rateName;
    private final String engine;
    private final String bidFormula;
    private final String askFormula;
    private final Map<String, BigDecimal> helpers;
    private final Set<String> dependsOn;
    private ExpressionEvaluator customEvaluator;

    public CalcDef(String rateName, String engine, String bidFormula, String askFormula,
                   Map<String, BigDecimal> helpers, Set<String> dependsOn) {
        this.rateName = rateName;
        this.engine = engine;
        this.bidFormula = bidFormula;
        this.askFormula = askFormula;
        this.helpers = helpers;
        this.dependsOn = dependsOn;
    }

    public String rateName() {
        return rateName;
    }

    public String engine() {
        return engine;
    }

    public String bidFormula() {
        return bidFormula;
    }

    public String askFormula() {
        return askFormula;
    }

    public Map<String, BigDecimal> helpers() {
        return helpers;
    }

    public Set<String> dependsOn() {
        return dependsOn;
    }

    public ExpressionEvaluator customEvaluator() {
        return customEvaluator;
    }
}
