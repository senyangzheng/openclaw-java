package com.openclaw.bootstrap.web;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Replaces the default Jackson-based {@link HttpMessageConverter} with a Fastjson2-backed one,
 * enforcing our convention: business code uses Fastjson2 only.
 */
@Configuration
public class FastJson2WebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(final List<HttpMessageConverter<?>> converters) {
        final FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();

        final FastJsonConfig config = new FastJsonConfig();
        config.setCharset(StandardCharsets.UTF_8);
        config.setWriterFeatures(
            JSONWriter.Feature.WriteNonStringKeyAsString,
            JSONWriter.Feature.IgnoreErrorGetter
        );
        config.setReaderFeatures(
            JSONReader.Feature.FieldBased,
            JSONReader.Feature.SupportSmartMatch
        );
        converter.setFastJsonConfig(config);
        converter.setSupportedMediaTypes(List.of(
            MediaType.APPLICATION_JSON,
            new MediaType("application", "*+json"),
            MediaType.APPLICATION_NDJSON
        ));

        converters.add(0, converter);
    }
}
