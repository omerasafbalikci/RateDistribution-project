package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.model.Rate;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.janino.SimpleCompiler;
import org.graalvm.polyglot.Context;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class DynamicScriptFormulaEngine implements ExpressionEvaluator {
    private final String engine;
    private final Path scriptFile;
    private long lastModified = 0;
    private String cachedScript = "";

    public DynamicScriptFormulaEngine(String engine, Path scriptFile) {
        this.engine = engine.toLowerCase();
        this.scriptFile = scriptFile;
    }

    @Override
    public BigDecimal evaluate(String unused, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        String script = loadScriptIfModified();

        return switch (engine) {
            case "groovy" -> evalGroovy(script, vars, helpers);
            case "js", "javascript" -> evalJs(script, vars, helpers);
            case "java" -> evalJava(script, vars, helpers);
            default -> throw new IllegalArgumentException("Unsupported engine: " + engine);
        };
    }

    private String loadScriptIfModified() {
        try {
            long currentModified = Files.getLastModifiedTime(scriptFile).toMillis();
            if (currentModified != lastModified) {
                cachedScript = Files.readString(scriptFile);
                lastModified = currentModified;
            }
            return cachedScript;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read script from: " + scriptFile, e);
        }
    }

    private BigDecimal evalGroovy(String script, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        Binding b = new Binding();
        injectGroovy(b::setProperty, vars, helpers);
        Object result = new GroovyShell(b, new CompilerConfiguration()).evaluate(script);
        return toBD(result);
    }

    private BigDecimal evalJs(String script, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            var bindings = context.getBindings("js");
            vars.forEach((s, r) -> {
                bindings.putMember(s + "_bid", r.bid());
                bindings.putMember(s + "_ask", r.ask());
            });
            if (helpers != null) helpers.forEach(bindings::putMember);
            return toBD(context.eval("js", script).as(Object.class));
        }
    }

    private BigDecimal evalJava(String script, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        String full = """
                    import java.math.*;
                    public class _F {
                        public static BigDecimal f(java.util.Map<String,BigDecimal> m) {
                            %s
                        }
                    }
                """.formatted(script);
        SimpleCompiler sc = new SimpleCompiler();
        try {
            sc.cook(full);
            return (BigDecimal) sc.getClassLoader().loadClass("_F")
                    .getMethod("f", Map.class).invoke(null, flatten(vars, helpers));
        } catch (Exception e) {
            throw new RuntimeException("Java script evaluation failed", e);
        }
    }

    private void injectGroovy(BiConsumer<String, Object> f, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        vars.forEach((s, r) -> {
            f.accept(s + "_bid", r.bid());
            f.accept(s + "_ask", r.ask());
        });
        if (helpers != null) helpers.forEach(f);
    }

    private Map<String, BigDecimal> flatten(Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        Map<String, BigDecimal> flat = new HashMap<>();
        vars.forEach((s, r) -> {
            flat.put(s + "_bid", r.bid());
            flat.put(s + "_ask", r.ask());
        });
        if (helpers != null) flat.putAll(helpers);
        return flat;
    }

    private BigDecimal toBD(Object o) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        throw new IllegalArgumentException("Script output not numeric: " + o);
    }
}
