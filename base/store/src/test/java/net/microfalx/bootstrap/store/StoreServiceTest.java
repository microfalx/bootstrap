package net.microfalx.bootstrap.store;

import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoreServiceTest extends AbstractStoreTest {

    @Test
    void getStore() {
        assertThrows(StoreException.class, () -> storeService.getStore("test"));
    }

    @Test
    void registerStore() {
        storeService.registerStore(Store.Options.create("Test"));
        assertNotNull(storeService.getStore("test"));
    }

}