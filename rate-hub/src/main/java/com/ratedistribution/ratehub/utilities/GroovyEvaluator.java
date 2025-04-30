package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.model.Rate;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.BiConsumer;

public class GroovyEvaluator implements ExpressionEvaluator {
    private static final CompilerConfiguration GC = new CompilerConfiguration();

    @Override
    public BigDecimal evaluate(String expr, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        Binding b = new Binding();
        inject(b::setProperty, vars, helpers);
        Object result = new GroovyShell(b, GC).evaluate(expr);
        return toBD(result);
    }

    private void inject(BiConsumer<String, Object> f, Map<String, Rate> v, Map<String, BigDecimal> h) {
        v.forEach((s, r) -> {
            f.accept(s + "_bid", r.bid());
            f.accept(s + "_ask", r.ask());
        });
        if (h != null) h.forEach(f);
    }

    private BigDecimal toBD(Object o) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        throw new IllegalArgumentException("not numeric");
    }
}
