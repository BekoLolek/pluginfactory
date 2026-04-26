package com.bekololek.pluginfactory.agent.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AnthropicConfig {

    @Bean
    @Qualifier("anthropicRestTemplate")
    public RestTemplate anthropicRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(180_000);
        return new RestTemplate(factory);
    }
}
