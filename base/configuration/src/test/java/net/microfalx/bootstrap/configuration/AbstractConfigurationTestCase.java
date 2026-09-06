package net.microfalx.bootstrap.configuration;

import net.microfalx.bootstrap.registry.RegistryService;
import net.microfalx.registry.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractConfigurationTestCase {

    @Mock protected RegistryService registryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Spy protected Registry registry = Registry.get();
    @Spy protected MockEnvironment environment = new MockEnvironment();
    @Spy protected EnvironmentConfigurationSource configurationSource = new EnvironmentConfigurationSource(environment);

    @InjectMocks
    protected ConfigurationService configurationService;

    @BeforeEach
    void setup() throws Exception {
        configurationService.afterPropertiesSet();
        postSetup();
    }

    protected void postSetup() {

    }

    protected void mockRegistry() {
        when(registryService.getRegistry()).thenReturn(registry);
    }

}
