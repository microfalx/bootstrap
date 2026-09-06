package net.microfalx.bootstrap.registry;

import net.microfalx.registry.Registry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistryConfiguration {

    @Bean
    public Registry registry(RegistryService registryService) {
        return registryService.getRegistry();
    }
}
