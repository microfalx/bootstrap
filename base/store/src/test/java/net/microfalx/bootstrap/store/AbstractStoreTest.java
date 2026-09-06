package net.microfalx.bootstrap.store;

import net.microfalx.bootstrap.resource.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractStoreTest {

    @Spy private ResourceService resourceService = new ResourceService();

    @Spy
    private ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    @InjectMocks
    protected StoreService storeService;

    @BeforeEach
    void before() throws Exception {
        taskScheduler.afterPropertiesSet();
        resourceService.afterPropertiesSet();
        storeService.afterPropertiesSet();
    }
}
