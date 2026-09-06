package net.microfalx.bootstrap.registry;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.registry.Registry;
import net.microfalx.registry.Storage;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class RegistryService implements InitializingBean {

    private Registry registry;

    /**
     * Returns the current registry.
     *
     * @return a non-null instance
     */
    public Registry getRegistry() {
        return registry;
    }

    /**
     * Returns the registered storages.
     *
     * @return a non-null instance
     */
    public List<Storage> getStorages() {
        return getRegistryService().getStorages();
    }

    /**
     * Returns the current storage.
     *
     * @return a non-null instance
     */
    public Storage getStorage() {
        return getRegistryService().getStorage();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        registry = getRegistryService().getRegistry();
    }

    private net.microfalx.registry.RegistryService getRegistryService() {
        return net.microfalx.registry.RegistryService.getInstance();
    }

}
