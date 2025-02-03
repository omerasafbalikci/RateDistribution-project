package com.ratedistribution.rdp.utilities.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class PercentSerializer extends StdSerializer<BigDecimal> {
    private static final DecimalFormat df = new DecimalFormat("#.####'%'"); // 4 ondalık + %

    public PercentSerializer() {
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
