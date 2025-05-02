package com.ratedistribution.rdp.utilities.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Custom serializer for formatting BigDecimal values with high precision.
 * Example: 1.23456789 → "1.23456789"
 * Used for bid/ask and other price fields.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class PriceSerializer extends StdSerializer<BigDecimal> {
    private static final DecimalFormat df = new DecimalFormat("#.#############");

    public PriceSerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(df.format(value));
        }
    }
}
