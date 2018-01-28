package com.globo.ateam.taurusapi.configurations;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.util.List;

@Configuration
@EnableWebMvc
public class WebConfig extends WebMvcConfigurerAdapter {
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new YamlJackson2HttpMessageConverter());
    }

    private static class YamlJackson2HttpMessageConverter extends AbstractJackson2HttpMessageConverter {
        private YamlJackson2HttpMessageConverter() {
            super(new YAMLMapper(), MediaType.parseMediaType("application/x-yaml"));
        }
    }
}
