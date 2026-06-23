package github.luckygc.am.common.api;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public final class LongStringSerializer extends StdSerializer<Long> {

    public LongStringSerializer() {
        super(Long.class);
    }

    @Override
    public void serialize(Long value, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
        generator.writeString(value.toString());
    }
}
