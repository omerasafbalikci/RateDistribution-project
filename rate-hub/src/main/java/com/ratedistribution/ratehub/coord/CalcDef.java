package com.ratedistribution.ratehub.coord;

import com.ratedistribution.ratehub.utilities.ExpressionEvaluator;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Data
public class CalcDef {
    private final String rateName;
    private final String engine;
    private String scriptPath;     // …/eurtry.groovy vb.
    private final Map<String, BigDecimal> helpers;
    private final Set<String> dependsOn;

    @Setter
    private transient ExpressionEvaluator customEvaluator;   // Coordinator-da set edilir

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
