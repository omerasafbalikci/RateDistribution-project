package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.model.Rate;
import org.graalvm.polyglot.Context;

import java.math.BigDecimal;
import java.util.Map;

public class JsEvaluator implements ExpressionEvaluator {
    @Override
    public BigDecimal evaluate(String expr, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        try (Context context = Context.newBuilder("js").allowAllAccess(false).build()) {
            var bindings = context.getBindings("js");
            vars.forEach((s, r) -> {
                bindings.putMember(s + "_bid", r.bid());
                bindings.putMember(s + "_ask", r.ask());
            });
            if (helpers != null) helpers.forEach(bindings::putMember);
            return toBD(context.eval("js", expr).as(Object.class));
        }
    }

    private BigDecimal toBD(Object o) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        throw new IllegalArgumentException("not numeric");
    }
}
