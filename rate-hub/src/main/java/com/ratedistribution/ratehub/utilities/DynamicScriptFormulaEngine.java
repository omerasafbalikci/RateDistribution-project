package com.ratedistribution.ratehub.utilities;

import com.ratedistribution.ratehub.advice.GlobalExceptionHandler;
import com.ratedistribution.ratehub.model.Rate;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

/**
 * DynamicScriptFormulaEngine is an implementation of {@link ExpressionEvaluator}
 * that dynamically evaluates expressions using scripting engines like:
 * - Groovy
 * - JavaScript (GraalVM)
 * - Inline Java code (Janino)
 * It watches a script file for changes and caches the script content for reuse.
 * The script can access variables using naming conventions like "USD_bid", "EUR_ask", etc.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class DynamicScriptFormulaEngine implements ExpressionEvaluator {
    private static final Logger log = LogManager.getLogger(DynamicScriptFormulaEngine.class);
    private final String engine;
    private final Path scriptFile;
    private long lastModified = 0;
    private String cachedScript = "";

    /**
     * Constructs a new dynamic evaluator with the given scripting engine and script file.
     *
     * @param engine     the name of the engine ("groovy", "js", or "java")
     * @param scriptFile the path to the script file
     */
    public DynamicScriptFormulaEngine(String engine, Path scriptFile) {
        this.engine = engine.toLowerCase();
        this.scriptFile = scriptFile;
        log.info("[DynamicEngine] Initialized with engine='{}' file='{}'", engine, scriptFile);
    }

    /**
     * Evaluates the configured script file using the specified engine.
     *
     * @param unused  unused expression name
     * @param vars    market rates to inject into the script
     * @param helpers additional constants
     * @return evaluated result as BigDecimal
     */
    @Override
    public BigDecimal evaluate(String unused, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        try {
            String script = loadScriptIfModified();
            return switch (engine) {
                case "groovy" -> evalGroovy(script, vars, helpers);
                case "js", "javascript" -> evalJs(script, vars, helpers);
                case "java" -> evalJava(script, vars, helpers);
                default -> throw new IllegalArgumentException("Unsupported engine:" + engine);
            };
        } catch (Exception e) {
            GlobalExceptionHandler.handle("DynamicScriptFormulaEngine.evaluate", e);
            return BigDecimal.ZERO;
        }
    }

    private String loadScriptIfModified() {
        try {
            long m = Files.getLastModifiedTime(scriptFile).toMillis();
            if (m != lastModified) {
                cachedScript = Files.readString(scriptFile);
                lastModified = m;
            }
            return cachedScript;
        } catch (IOException e) {
            GlobalExceptionHandler.fatal("DynamicScriptFormulaEngine.loadScript", e);
            return "";
        }
    }

    private BigDecimal evalGroovy(String script, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        try {
            Binding binding = new Binding();
            vars.forEach((k, r) -> {
                binding.setVariable(k + "_bid", r.bid());
                binding.setVariable(k + "_ask", r.ask());
            });

            if (helpers != null) {
                helpers.forEach(binding::setVariable);

            }

            CompilerConfiguration config = new CompilerConfiguration();
            GroovyShell shell = new GroovyShell(binding, config);
            Object result = shell.evaluate(script);
            return toBD(result);
        } catch (Exception e) {
            GlobalExceptionHandler.handle("DynamicScriptFormulaEngine.evalGroovy", e);
            return null;
        }
    }

    private BigDecimal evalJs(String script, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        try (Context c = Context.newBuilder("js").allowAllAccess(true).build()) {
            var b = c.getBindings("js");
            vars.forEach((k, r) -> {
                b.putMember(k + "_bid", r.bid());
                b.putMember(k + "_ask", r.ask());
            });
            if (helpers != null) helpers.forEach(b::putMember);
            return toBD(c.eval("js", script).as(Object.class));
        }
    }

    private BigDecimal evalJava(String script, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        Map<String, BigDecimal> flat = flatten(vars, helpers);

        String full = """
                import java.math.BigDecimal;
                import java.util.Map;
                
                public class _F {
                    public static BigDecimal f(Map<String, BigDecimal> m) {
                        %s
                    }
                }
                """.formatted(script);
        SimpleCompiler sc = new SimpleCompiler();
        try {
            sc.cook(full);
            Class<?> cls = sc.getClassLoader().loadClass("_F");
            Object raw = cls.getMethod("f", Map.class).invoke(null, flat);
            return toBD(raw);
        } catch (Exception e) {
            GlobalExceptionHandler.fatal("DynamicScriptFormulaEngine.evalJava", e);
            return null;
        }
    }

    private void inject(BiConsumer<String, Object> f, Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        vars.forEach((k, r) -> {
            f.accept(k + "_bid", r.bid());
            f.accept(k + "_ask", r.ask());
        });
        if (helpers != null) helpers.forEach(f);
    }

    private Map<String, BigDecimal> flatten(Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        Map<String, BigDecimal> m = new HashMap<>();
        vars.forEach((k, r) -> {
            m.put(k + "_bid", r.bid());
            m.put(k + "_ask", r.ask());
        });
        if (helpers != null) m.putAll(helpers);
        return m;
    }

    private BigDecimal toBD(Object o) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        throw new IllegalArgumentException("Script output not numeric:" + o);
    }
}
