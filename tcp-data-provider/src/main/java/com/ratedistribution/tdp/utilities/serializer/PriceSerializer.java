package com.ratedistribution.tdp.utilities.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Custom serializer for BigDecimal price values.
 * Formats with up to 13 decimal places.
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
