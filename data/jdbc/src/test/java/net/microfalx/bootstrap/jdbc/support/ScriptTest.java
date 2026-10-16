package net.microfalx.bootstrap.jdbc.support;

import net.microfalx.resource.ClassPathResource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ScriptTest {

    @Test
    void loadScript() {
        Script script = createScript("sql/mysql/schema/01_demo1.sql");
        assertNotNull(script);
        assertEquals(2, script.getQueries().size());
    }

    private Script createScript(String path) {
        Schema schema = Mockito.mock(Schema.class);
        return new MySqlScript(schema, ClassPathResource.file(path));
    }
}