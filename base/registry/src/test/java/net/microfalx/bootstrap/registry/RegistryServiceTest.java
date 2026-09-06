package net.microfalx.bootstrap.registry;

import net.microfalx.registry.core.MemoryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RegistryServiceTest {

    private RegistryService registryService;

    @BeforeEach
    void setup() throws Exception {
        registryService = new RegistryService();
        registryService.afterPropertiesSet();
    }

    @Test
    void getStorages() {
        assertEquals(2, registryService.getStorages().size());
    }

    @Test
    void getStorage() {
        assertSame(registryService.getStorage().getClass(), MemoryStorage.class);
    }

    @Test
    void getRegistry() {
        assertNotNull(registryService.getRegistry());
    }

}