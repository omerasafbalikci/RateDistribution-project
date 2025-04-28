package com.ratedistribution.ratehub.coord;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public record CalcDef(String rateName, String engine, String bidFormula, String askFormula,
                      Map<String, BigDecimal> helpers, Set<String> dependsOn) {
}