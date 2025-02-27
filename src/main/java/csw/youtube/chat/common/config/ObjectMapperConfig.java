package csw.youtube.chat.common.config;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

import java.util.TimeZone;

@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        // Create a new JsonMapper (the newer builder-based API) for clarity
        JsonMapper builder = JsonMapper.builder()
                // Register standard modules
                .addModule(new JavaTimeModule())      // For Java 8 (JSR-310) date/time support
                .addModule(new Jdk8Module())          // For Optionals and other JDK8 goodies
                .addModule(new ParameterNamesModule())// For better constructor parameter name introspection
                // .addModule(new AfterburnerModule())   // For performance optimizations
                .addModule(new BlackbirdModule())     // For performance optimizations (jdk 11+, https://github.com/FasterXML/jackson-modules-base/tree/2.19/blackbird)

                // BigDecimal behavior: disable STRIP_TRAILING_BIGDECIMAL_ZEROES to mimic "withExactBigDecimals(true)"
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)

                // Mapper features
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(MapperFeature.USE_STD_BEAN_NAMING)

                // Parser features
                .enable(JsonParser.Feature.ALLOW_COMMENTS)       // Allow JSON comments
                .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)   // Don’t close the underlying stream

                // Serialization features
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

                // Deserialization features
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

                // Security - prevent polymorphic deserialization unless you intentionally use it
                .deactivateDefaultTyping()

                // Visibility: only serialize fields, ignore getters/setters
                .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

                // Don’t close the output stream automatically
                .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)

                // Include only non-null and non-absent values
                .serializationInclusion(JsonInclude.Include.NON_ABSENT)

                // Provide a consistent date/time format, in UTC, with the colon in time zone offsets
                .defaultDateFormat(new StdDateFormat()
                        .withColonInTimeZone(true)
                        .withTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();

        builder.addMixIn(ProblemDetail.class, ProblemDetailMixIn.class);

        return builder;
    }

    // mix-in for ProblemDetail to ignore some properties
    @JsonIgnoreProperties(value = {"instance", "type", "parameters"}, allowGetters = true)
    interface ProblemDetailMixIn {
    }
}