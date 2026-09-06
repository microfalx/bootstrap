package net.microfalx.bootstrap.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigurationServiceTest extends AbstractConfigurationTestCase {

    @Test
    void getMetadata() {
        assertEquals(2, configurationService.getRootMetadata().getChildren().size());
    }

    @Test
    void getRegistry() {
        mockRegistry();
        assertNotNull(configurationService.getRegistry());
    }

    @Test
    void getProperty() {
        assertEquals("", configurationService.getProperty("a"));
        environment.setProperty("a", 1);
        assertEquals("1", configurationService.getProperty("a"));
    }

    @Test
    void getPropertyNormalized() {
        environment.setProperty("camelCase", 1);
        assertEquals("1", configurationService.getProperty("camelCase"));
        assertEquals("1", configurationService.getProperty("camel-case"));
        assertEquals("1", configurationService.getProperty("camel_case"));
    }

    @Test
    void getPropertyAsEnvironment() {
        environment.setProperty("A", 1);
        assertEquals("1", configurationService.getProperty("a"));
    }

    @Test
    void getConfiguration() {
        mockRegistry();
        ReflectionTestUtils.invokeMethod(configurationService, "registerMetadata");
        assertEquals("20", configurationService.getConfiguration().get("group1.group12.item1"));
        assertEquals("20", configurationService.getConfiguration().get("group1.group12.item1", "10"));
        assertEquals(20, configurationService.getConfiguration().get("group1.group12.item1", 10));
        assertEquals(false, configurationService.getConfiguration().get("group1.group12.item5", boolean.class, null));
    }


}