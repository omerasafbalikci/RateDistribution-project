package com.ratedistribution.rdp.utilities.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Custom serializer for formatting BigDecimal as percentage strings.
 * Example: 0.0123 → "0.0123%"
 *
 * @author Ömer Asaf BALIKÇI
 */

public class PercentSerializer extends StdSerializer<BigDecimal> {
    private static final ThreadLocal<DecimalFormat> DF =
            ThreadLocal.withInitial(() -> new DecimalFormat("#.####'%'"));

    public PercentSerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value,
                          JsonGenerator gen,
                          SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(DF.get().format(value));
        }
    }
}
