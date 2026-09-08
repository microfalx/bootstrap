package net.microfalx.bootstrap.store;

import net.microfalx.bootstrap.resource.ResourceService;
import net.microfalx.lang.Identifiable;
import net.microfalx.store.api.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A service responsible for managing a collection of {@link net.microfalx.store.api.Store}.
 */
@Service
public class StoreService implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreService.class);

    @Autowired(required = false) private StoreProperties properties = new StoreProperties();
    @Autowired private ResourceService resourceService;

    /**
     * Registers a new store.
     *
     * @param options the options
     */
    public <T extends Identifiable<ID>, ID> Store<T, ID> register(Store.Options options) {
        requireNonNull(options);
        return getStoreService().register(options);
    }

    /**
     * Returns a store with a given identifier.
     *
     * @param id   the store identifier
     * @param <ID> the identifier type
     * @param <T>  the item type
     * @return a non-null instance
     */
    public <T extends Identifiable<ID>, ID> Store<T, ID> getStore(String id) {
        return getStoreService().getStore(id);
    }

    /**
     * Flushes all stores to disk.
     */
    public void flush() {
        getStoreService().flush();
    }

    /**
     * Returns registered stores.
     *
     * @return a non-null instance
     */
    public Collection<Store<?, ?>> getStores() {
        return getStoreService().getStores();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // nothing specific yet
    }

    @Override
    public void destroy() throws Exception {
        flush();
    }

    private net.microfalx.store.api.StoreService getStoreService() {
        return net.microfalx.store.api.StoreService.getInstance();
    }

}
