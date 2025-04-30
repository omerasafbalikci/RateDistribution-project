package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.model.Rate;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormulaValidator {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("[A-Z]{3,6}_bid|[A-Z]{3,6}_ask");

    /**
     * Formülde kullanılan değişkenleri çıkarır.
     */
    public static Set<String> extractVariables(String formula) {
        Set<String> variables = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(formula);
        while (matcher.find()) {
            variables.add(matcher.group());
        }
        return variables;
    }

    /**
     * Formül içerisindeki tüm sembollerin mevcut Rate haritasında olup olmadığını kontrol eder.
     */
    public static boolean validate(String formula, Map<String, Rate> availableVars) {
        return getMissingVariables(formula, availableVars).isEmpty();
    }

    /**
     * Eksik değişkenleri döner (örneğin: EURUSD_bid, GBPUSD_ask)
     */
    public static Set<String> getMissingVariables(String formula, Map<String, Rate> availableVars) {
        Set<String> required = extractVariables(formula);
        Set<String> missing = new HashSet<>();
        for (String var : required) {
            String base = var.split("_")[0];
            if (!availableVars.containsKey(base)) {
                missing.add(var);
            }
        }
        return missing;
    }

    /**
     * Yardımcı sabit değişkenlerin eksik olup olmadığını kontrol eder.
     */
    public static boolean validateHelpers(Set<String> requiredKeys, Map<String, BigDecimal> suppliedHelpers) {
        if (requiredKeys == null || requiredKeys.isEmpty()) return true;
        return suppliedHelpers != null && suppliedHelpers.keySet().containsAll(requiredKeys);
    }

    /**
     * Yardımcı değişkenlerden eksik olanları döner.
     */
    public static Set<String> getMissingHelpers(Set<String> requiredKeys, Map<String, BigDecimal> suppliedHelpers) {
        Set<String> missing = new HashSet<>();
        if (requiredKeys == null) return missing;
        for (String key : requiredKeys) {
            if (suppliedHelpers == null || !suppliedHelpers.containsKey(key)) {
                missing.add(key);
            }
        }
        return missing;
    }
}
