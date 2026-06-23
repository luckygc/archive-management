package github.luckygc.am.infrastructure.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

@Configuration
public class JacksonJsonConfig {

    @Bean
    JsonMapperBuilderCustomizer longAsStringJsonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("am-long-as-string");
            LongAsStringSerializer serializer = new LongAsStringSerializer();
            module.addSerializer(Long.class, serializer);
            module.addSerializer(Long.TYPE, serializer);
            builder.addModule(module);
        };
    }

    private static final class LongAsStringSerializer extends StdSerializer<Long> {

        private LongAsStringSerializer() {
            super(Long.class);
        }

        @Override
        public void serialize(Long value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(value.toString());
        }
    }
}
