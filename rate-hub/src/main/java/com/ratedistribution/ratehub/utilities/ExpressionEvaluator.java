package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.model.Rate;

import java.math.BigDecimal;
import java.util.Map;

public interface ExpressionEvaluator {
    BigDecimal evaluate(String expression, Map<String, Rate> rates, Map<String, BigDecimal> helpers);
}
