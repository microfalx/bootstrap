package net.microfalx.bootstrap.web.application;

import net.microfalx.bootstrap.configuration.ConfigurationService;
import net.microfalx.bootstrap.registry.RegistryService;
import net.microfalx.registry.Registry;
import net.microfalx.resource.Resource;
import net.microfalx.threadpool.ThreadPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationAssetBundleListenerTest {

    @Spy
    private ApplicationContext applicationContext = new GenericApplicationContext();

    @Mock
    private RegistryService registryService;

    @Mock
    private ThreadPool threadPool;

    @Spy
    private Environment environment = new MockEnvironment();

    @InjectMocks
    private ConfigurationService configurationService;

    private AssetBundle assetBundle;
    private ConfigurationAssetBundleListener listener;

    @BeforeEach
    void setup() {
        setupMetadata();
        registerBeans();
        Registry registry = Mockito.mock(Registry.class);
        when(registryService.getRegistry()).thenReturn(registry);
        assetBundle = AssetBundle.builder("test").build();
        listener = new ConfigurationAssetBundleListener();
        listener.setApplicationContext(applicationContext);
    }

    @Test
    void registerAssert() throws IOException {
        List<Asset> asserts = new ArrayList<>();
        listener.update(assetBundle, asserts);
        assertEquals(1, asserts.size());

        Resource resource = asserts.getFirst().getResource();
        org.assertj.core.api.Assertions.assertThat(resource.loadAsString()).contains("APP_CONFIGURATION")
                .contains("test.key1");
    }


    private void setupMetadata() {
    }

    private void registerBeans() {
        ((GenericApplicationContext) applicationContext).registerBean(ConfigurationService.class, () -> configurationService);
        ((GenericApplicationContext) applicationContext).refresh();
    }

}