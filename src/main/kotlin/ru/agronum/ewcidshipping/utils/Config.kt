package ru.agronum.ewcidshipping.utils

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.charset.StandardCharsets


@Configuration
class WebConfig: WebMvcConfigurer {
    override fun configureMessageConverters(converters: List<HttpMessageConverter<*>>) {
        converters.stream()
            .filter { converter: HttpMessageConverter<*>? -> converter is MappingJackson2HttpMessageConverter }
            .findFirst()
            .ifPresent { converter: HttpMessageConverter<*> ->
                (converter as MappingJackson2HttpMessageConverter).defaultCharset = StandardCharsets.UTF_8
            }
    }
}