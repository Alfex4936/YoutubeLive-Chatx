package csw.youtube.chat.common.config;

import csw.youtube.chat.common.annotation.ApiV1;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    public static final String API_V1_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        PathPatternParser pathPatternParser = new PathPatternParser();
        pathPatternParser.setCaseSensitive(false);
        configurer.setPatternParser(pathPatternParser);

        // Apply the prefix only to controllers annotated with @ApiV1
        configurer.addPathPrefix(API_V1_PREFIX, c -> c.isAnnotationPresent(ApiV1.class));
    }
}