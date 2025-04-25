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
    private final CompilerConfiguration configuration;

    public FormulaEngine() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass("groovy.lang.Script");
        this.configuration = configuration;
    }

    public BigDecimal eval(String engine, String formula, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        return switch (engine.toLowerCase()) {
            case "groovy" -> evalGroovy(formula, vars, helpers);
            case "js" -> evalJS(formula, vars, helpers);
            default -> throw new IllegalArgumentException("Unknown engine " + engine);
        };
    }

    private BigDecimal evalGroovy(String f, Map<String, Rate> v, Map<String, BigDecimal> h) {
        Binding b = new Binding();
        v.forEach((k, r) -> {
            b.setProperty(k + "_bid", r.bid());
            b.setProperty(k + "_ask", r.ask());
        });
        h.forEach(b::setProperty);

        GroovyShell shell = new GroovyShell(b, configuration);
        Object res = shell.evaluate(f);
        return toBigDecimal(res);
    }

    private BigDecimal evalJS(String f, Map<String, Rate> v, Map<String, BigDecimal> h) {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value bindings = ctx.getBindings("js");
            v.forEach((k, r) -> {
                bindings.putMember(k + "_bid", r.bid());
                bindings.putMember(k + "_ask", r.ask());
            });
            h.forEach(bindings::putMember);
            Object res = ctx.eval("js", f).as(Object.class);
            return toBigDecimal(res);
        }
    }

    private BigDecimal toBigDecimal(Object o) {
        return switch (o) {
            case BigDecimal bd -> bd;
            case Double d -> BigDecimal.valueOf(d);
            case Integer i -> BigDecimal.valueOf(i);
            default -> throw new IllegalArgumentException("Unsupported result type " + o);
        };
    }
}
