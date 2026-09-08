package net.microfalx.bootstrap.core.utils;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvironmentSupportTest {

    @Test
    void getSimpleProperty() {
        EnvironmentSupport support = createEnvironmentSupport(Map.of("app.name", "bootstrap"));

        assertEquals("bootstrap", support.getProperty("app.name"));
    }

    @Test
    void getPropertyWithExpression() {
        EnvironmentSupport support = createEnvironmentSupport(Map.of(
                "app.name", "bootstrap",
                "app.profile", "dev",
                "app.banner", "${app.name}-${app.profile}-${app.version:1.0}"
        ));

        assertEquals("bootstrap-dev-1.0", support.getProperty("app.banner"));
    }

    @Test
    void getPropertyWithRelaxedNamingVariants() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("naming.kebab-case", "kebab");
        properties.put("naming.snake_case", "snake");

        EnvironmentSupport support = createEnvironmentSupport(properties);

        assertSameValueForVariants(support, "kebab",
                "naming.kebab-case", "naming.kebabCase", "naming.kebab_case");
        assertSameValueForVariants(support, "snake",
                "naming.snake_case", "naming.snakeCase", "naming.snake-case");
    }

    private static EnvironmentSupport createEnvironmentSupport(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return new EnvironmentSupport(environment);
    }

    private static void assertSameValueForVariants(EnvironmentSupport support, String expected, String... keys) {
        for (String key : keys) {
            assertEquals(expected, support.getProperty(key), "Unexpected value for key '" + key + "'");
        }
    }
}

