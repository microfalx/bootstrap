package net.microfalx.bootstrap.resource;

import lombok.CustomLog;
import net.microfalx.lang.JvmUtils;
import net.microfalx.resource.*;
import net.microfalx.threadpool.ThreadPool;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.resource.ResourceUtils.CLASS_PATH_SCHEME;
import static net.microfalx.resource.ResourceUtils.toUri;

/**
 * A service which provides standard locations for resources.
 * <p>
 * The service provides three types of locations:
 * <ul>
 *     <li>transient - a location for temporary resources, which are not guaranteed to survive process restart</li>
 *     <li>persistent - a location for persisted resources, local to the server where the process runs, which will survive process restarts</li>
 *     <li>shared - a location for persisted resources outside the local server</li>
 * </ul>
 */
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
@CustomLog
public class ResourceService implements InitializingBean {

    @Autowired private ThreadPool threadPool = ThreadPool.get();
    @Autowired(required = false) private ResourceProperties properties = new ResourceProperties();

    private final ClassPathManager classPathManager = new ClassPathManager();
    private File persistedDirectory;
    private File transientDirectory;
    private Resource sharedResource;

    /**
     * Returns a resource directory for persisted data.
     * <p>
     * Loss of persisted data does result in loss of critical data.
     *
     * @param name the subdirectory name
     * @return a non-null instance
     */
    public Resource getPersisted(String name) {
        return get(ResourceLocation.PERSISTED, name);
    }

    /**
     * Returns a resource directory for transient data.
     * <p>
     * Loss of transient data does not result in loss of critical data.
     *
     * @param name the subdirectory name
     * @return a non-null instance
     */
    public Resource getTransient(String name) {
        return get(ResourceLocation.TRANSIENT, name);
    }

    /**
     * Returns a resource directory for shared data.
     * <p>
     * Shared data is stored outside the process (most of the time), and it is usually a remote file system (object store like S3, etc).
     *
     * @param name the subdirectory name
     * @return a non-null instance
     */
    public Resource getShared(String name) {
        return get(ResourceLocation.SHARED, name);
    }

    /**
     * Resolves an URI to a resource.
     * <p>
     * If a provider does not exist, it will return a "NULL" resource.
     * <p>
     * The service has special handling for "classpath" scheme, and provides resources
     * in development based on auto-detection of source directories.
     *
     * @param resource the URI
     * @return a non-null instance
     * @see ResourceFactory#resolve
     */
    public Resource resolveResource(Resource resource) {
        if (properties.isDebug() && resource instanceof ClassPathResource cpr) {
            return classPathManager.resolve(ClassPathResource.toUri(cpr.getPath()));
        } else {
            return resource;
        }
    }

    /**
     * Resolves an URI to a resource.
     * <p>
     * If a provider does not exist, it will return a "NULL" resource.
     * <p>
     * The service has special handling for "classpath" scheme, and provides resources
     * in development based on auto-detection of source directories.
     *
     * @param uri the URI
     * @return a non-null instance
     * @see ResourceFactory#resolve
     */
    public Resource resolveResource(URI uri) {
        requireNonNull(uri);
        if (properties.isDebug() && CLASS_PATH_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return classPathManager.resolve(uri);
        } else {
            return ResourceFactory.resolve(uri);
        }
    }

    /**
     * Resolves the URI by rewriting the resource, if possible.
     * <p>
     * The service has special handling for "classpath" scheme, and provides resources
     * in development based on auto-detection of source directories.
     *
     * @param uri the URI
     * @return a non-null instance
     * @see ResourceFactory#resolve
     * @see #resolveResource(URI)
     */
    public URI resolveUri(URI uri) {
        requireNonNull(uri);
        if (properties.isDebug() && CLASS_PATH_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            Resource resolved = classPathManager.resolve(uri);
            if (resolved != null) uri = resolved.toURI();
        }
        return uri;
    }

    /**
     * Returns the resource directory.
     * <p>
     * For shared resource, they can be access also as
     *
     * @param location the type of storage
     * @param name     the subdirectory name
     * @return a non-null instance
     */
    public Resource get(ResourceLocation location, String name) {
        requireNonNull(location);
        requireNonNull(name);
        return switch (location) {
            case PERSISTED -> get(persistedDirectory, name);
            case TRANSIENT -> get(transientDirectory, name);
            case SHARED -> getSharedResource(name);
        };
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDirectories();
        initializeClassPath();
        logOptions();
    }

    private void initializeDirectories() {
        validateDirectory(persistedDirectory = new File(properties.getPersistedDirectory()));
        validateDirectory(transientDirectory = new File(properties.getTransientDirectory()));
        initializeSharedResource();
        validateResource(getSharedResource(null));
        LOGGER.info("Initial JVM directories: home = {}, working = {}, var = {}, tmp = {}", JvmUtils.getHomeDirectory(),
                JvmUtils.getWorkingDirectory(false), JvmUtils.getVariableDirectory(), JvmUtils.getTemporaryDirectory());
        LOGGER.info("Persisted resources directory {}", persistedDirectory);
        ResourceFactory.setWorkspace(FileResource.directory(persistedDirectory));
        LOGGER.info("Transient resources directory {}", transientDirectory);
        LOGGER.info("Shared resources directory {}", getSharedResource(null).toURI());
        LOGGER.info("Change JVM (var & cache) directories to match the service: {}", persistedDirectory);
        JvmUtils.setVariableDirectory(persistedDirectory);
        JvmUtils.setCacheDirectory(persistedDirectory);
        ResourceFactory.setTemporary(FileResource.directory(transientDirectory));

        LOGGER.info("Final JVM directories: home = {}, working = {}, var = {}, tmp = {}", JvmUtils.getHomeDirectory(),
                JvmUtils.getWorkingDirectory(false), JvmUtils.getVariableDirectory(), JvmUtils.getTemporaryDirectory());
    }

    private void initializeClassPath() {
        threadPool.execute(classPathManager::initialize);
    }

    private void initializeSharedResource() {
        UserPasswordCredential credential = UserPasswordCredential.create(properties.getSharedUserName(), properties.getSharedPassword());
        sharedResource = ResourceFactory.resolve(toUri(properties.getSharedDirectory()), credential, Resource.Type.DIRECTORY);
        ResourceFactory.setShared(sharedResource);
    }

    private Resource getSharedResource(String name) {
        return name != null ? SharedResource.directory(name) : sharedResource;
    }

    private Resource get(File directory, String name) {
        return FileResource.directory(directory).resolve(name, Resource.Type.DIRECTORY);
    }

    private void validateDirectory(File directory) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ResourceException("Directory " + directory + " cannot be created");
        }
    }

    private void validateResource(Resource directory) {
        try {
            if (!directory.exists()) directory.create();
        } catch (IOException e) {
            throw new ResourceException("Directory " + directory + " cannot be created", e);
        }
    }

    private void logOptions() {
        if (properties.isDebug()) {
            LOGGER.warn("Classpath resources are in debug mode. Do not enable debug mode in production");
        }
    }
}
