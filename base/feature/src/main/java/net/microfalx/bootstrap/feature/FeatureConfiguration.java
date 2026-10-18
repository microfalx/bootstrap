package net.microfalx.bootstrap.feature;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
public class FeatureConfiguration {

    @Bean
    public FeatureWebMvcConfigurer featureWebMvcConfigurer() {
        return new FeatureWebMvcConfigurer();
    }
}
