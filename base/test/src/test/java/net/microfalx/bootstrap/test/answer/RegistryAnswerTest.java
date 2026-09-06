package net.microfalx.bootstrap.test.answer;

import net.microfalx.bootstrap.test.ServiceUnitTestCase;
import net.microfalx.registry.Data;
import net.microfalx.registry.Registry;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.junit.jupiter.api.Assertions.*;

class RegistryAnswerTest extends ServiceUnitTestCase {

    @Mock
    private Registry registry;

    @Test
    void initialize() {
        assertNotNull(registry);
    }

    @Test
    void validate() {
        assertFalse(registry.exists("/a"));
        Data data = registry.getOrCreate("/a");
        assertNotNull(data);
        data.set("a");
        registry.set(data);
        assertTrue(registry.exists("/a"));
    }

    @Service
    public static class TestService implements InitializingBean {

        @Autowired
        private Registry registry;

        @Override
        public void afterPropertiesSet() throws Exception {
            assertNotNull(registry);
        }
    }

}