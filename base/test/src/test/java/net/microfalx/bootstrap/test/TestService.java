package net.microfalx.bootstrap.test;

import lombok.Getter;
import net.microfalx.bootstrap.configuration.ConfigurationService;
import net.microfalx.bootstrap.resource.ResourceService;
import net.microfalx.bootstrap.store.StoreService;
import net.microfalx.registry.Registry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Getter
public class TestService {

    @Autowired private Registry registry;
    @Autowired private StoreService storeService;
    @Autowired private ResourceService resourceService;
    @Autowired private ConfigurationService configurationService;
}
