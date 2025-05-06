package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.model.Rate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ExpressionEvaluator defines a contract for evaluating expressions
 * based on input market rates and optional helper variables.
 * Implementations may support scripting, formula parsing, or expression trees.
 *
 * @author Ömer Asaf BALIKÇI
 */

public interface ExpressionEvaluator {
    /**
     * Evaluates the given expression using the provided rate and helper values.
     *
     * @param expression the target expression to evaluate (e.g., "bid", "ask", or custom formula)
     * @param rates      a map of rate names to their {@link Rate} data
     * @param helpers    optional named constants or helper values
     * @return the result of the evaluated expression
     */
    BigDecimal evaluate(String expression, Map<String, Rate> rates, Map<String, BigDecimal> helpers);
}
