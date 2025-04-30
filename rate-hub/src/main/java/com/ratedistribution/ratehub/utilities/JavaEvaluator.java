package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.model.Rate;
import org.codehaus.janino.SimpleCompiler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class JavaEvaluator implements ExpressionEvaluator {
    @Override
    public BigDecimal evaluate(String expr, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        String src = """
                    import java.math.*;
                    public class _F {
                        public static BigDecimal f(java.util.Map<String,BigDecimal> m) {
                            %s
                        }
                    }
                """.formatted(expr);
        SimpleCompiler sc = new SimpleCompiler();
        try {
            sc.cook(src);
            return (BigDecimal) sc.getClassLoader()
                    .loadClass("_F")
                    .getMethod("f", Map.class)
                    .invoke(null, flatten(vars, helpers));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Map<String, BigDecimal> flatten(Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        Map<String, BigDecimal> m = new HashMap<>();
        vars.forEach((s, r) -> {
            m.put(s + "_bid", r.bid());
            m.put(s + "_ask", r.ask());
        });
        if (helpers != null) m.putAll(helpers);
        return m;
    }
}
