package com.ratedistribution.ratehub.calculator;

import com.ratedistribution.ratehub.model.Rate;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.math.BigDecimal;
import java.util.Map;

@RequiredArgsConstructor
public class FormulaEngine {
    private final GroovyShell gShell;

    public FormulaEngine() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass("groovy.lang.Script");
        gShell = new GroovyShell(configuration);
    }

    public BigDecimal eval(String engine, String formula, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        return switch (engine.toLowerCase()) {
            case "groovy" -> evalGroovy(formula, vars, helpers);
            case "js" -> evalJS(formula, vars, helpers);
            default -> throw new IllegalArgumentException("Unsupported engine: " + engine);
        };
    }

    private BigDecimal evalGroovy(String formula, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        Binding binding = new Binding();
        vars.forEach((k, r) -> {
            binding.setProperty(k + "_bid", r.bid());
            binding.setProperty(k + "_ask", r.ask());
        });
        helpers.forEach(binding::setProperty);
        GroovyShell shell = new GroovyShell(binding);
        Object result = shell.evaluate(formula);
        return toBigDecimal(result);
    }

    private BigDecimal evalJS(String formula, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value bindings = ctx.getBindings("js");
            vars.forEach((k, r) -> {
                bindings.putMember(k + "_bid", r.bid());
                bindings.putMember(k + "_ask", r.ask());
            });
            helpers.forEach(bindings::putMember);
            return toBigDecimal(ctx.eval("js", formula).as(Object.class));
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        return switch (value) {
            case BigDecimal bd -> bd;
            case Double d -> BigDecimal.valueOf(d);
            case Integer i -> BigDecimal.valueOf(i);
            default -> throw new IllegalArgumentException("Unsupported result type: " + value);
        };
    }
}
