package net.microfalx.bootstrap.feature;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class FeatureWebMvcConfigurer implements WebMvcConfigurer {

    @Autowired private FeatureService featureService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new FeatureHandlerInterceptor(featureService));
    }
}
