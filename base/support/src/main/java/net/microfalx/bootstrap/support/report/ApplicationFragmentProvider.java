package net.microfalx.bootstrap.support.report;

import net.microfalx.argus.report.AbstractFragmentProvider;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Base class for fragment providers which are aware of the application context.
 */
public abstract class ApplicationFragmentProvider extends AbstractFragmentProvider
        implements ApplicationContextAware {

    private volatile ApplicationContext applicationContext;

    protected final <T> T getBean(Class<T> type) {
        return applicationContext.getBean(type);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
