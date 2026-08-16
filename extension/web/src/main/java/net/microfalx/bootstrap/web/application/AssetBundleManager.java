package net.microfalx.bootstrap.web.application;

import net.microfalx.bootstrap.feature.Feature;
import net.microfalx.bootstrap.feature.FeatureContext;
import net.microfalx.bootstrap.feature.FeatureService;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.ConcurrencyUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.resource.ClassPathResource;
import net.microfalx.resource.MemoryResource;
import net.microfalx.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPOutputStream;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.StringUtils.*;

/**
 * Manages the asset bundles.
 */
final class AssetBundleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetBundleManager.class);

    private final ApplicationService applicationService;
    private final Map<String, AssetBundle> bundles = new ConcurrentHashMap<>();
    private final Map<String, Theme> themes = new ConcurrentHashMap<>();
    private final Map<String, Resource> bundlesContent = new ConcurrentHashMap<>();
    private final Map<String, Collection<String>> assetBundleDependencies = new ConcurrentHashMap<>();
    private final Set<String> missingAssets = new CopyOnWriteArraySet<>();
    private final Collection<AssetBundleListener> listeners = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    private ApplicationContext applicationContext;
    private AssetProperties assetProperties = new AssetProperties();

    AssetBundleManager(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    ApplicationService getApplicationService() {
        return applicationService;
    }

    Application getApplication() {
        return applicationService.getApplication();
    }

    Collection<Theme> getThemes() {
        return Collections.unmodifiableCollection(themes.values());
    }

    void initialize(ApplicationContext applicationContext, AssetProperties assetProperties) {
        this.applicationContext = applicationContext;
        this.assetProperties = assetProperties;
        logOptions();
    }

    Theme getTheme(String idOrName) {
        requireNotEmpty(idOrName);
        Theme theme = themes.get(toIdentifier(idOrName));
        if (theme == null) {
            throw new ApplicationException("A theme with identifier or name '" + idOrName + "' is not registered");
        }
        return theme;
    }

    void registerTheme(Theme theme) {
        requireNonNull(theme);
        LOGGER.debug("Register theme {}, name {}", theme.getId(), theme.getName());
        themes.put(theme.getId(), theme);
        themes.put(toIdentifier(theme.getName()), theme);
    }

    void registerAssetBundle(AssetBundle assetBundle) {
        requireNonNull(assetBundle);
        LOGGER.debug("Register asset bundle {}, name {}", assetBundle.getId(), assetBundle.getName());
        bundles.put(assetBundle.getId(), assetBundle);
        if (isNotEmpty(assetBundle.getFeature())) {
            FeatureService featureService = applicationService.getFeatureService();
            if (featureService.findFeature(assetBundle.getFeature()).isEmpty()) {
                featureService.registerFeature(Feature.create(assetBundle.getId(), assetBundle.getName()));
            }
        }
    }

    void registerListener(AssetBundleListener listener) {
        requireNonNull(listener);
        LOGGER.debug("Register asset bundle listener {}", ClassUtils.getName(listener));
        if (listener instanceof ApplicationContextAware applicationContextAware) {
            applicationContextAware.setApplicationContext(applicationContext);

        }
        listeners.add(listener);
    }

    AssetBundle getAssetBundle(String id) {
        requireNonNull(id);
        AssetBundle assetBundle = bundles.get(toIdentifier(id));
        if (assetBundle == null) {
            throw new ApplicationException("An asset bundle with identifier '" + id + "' is not registered");
        }
        return assetBundle;
    }

    Resource getAssetBundlesContent(Asset.Type type, boolean header, String... ids) throws IOException {
        requireNonNull(type);
        requireNotEmpty(ids);
        String contentId = type.name().toLowerCase() + "/" + StringUtils.join("_", ids);
        Resource resource = bundlesContent.get(contentId);
        if (resource != null && !assetProperties.isDebug()) return resource;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        OutputStream stream = new GZIPOutputStream(buffer);
        FeatureContext featureContext = FeatureContext.get();
        try (OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            int counter = 0;
            for (String id : ids) {
                AssetBundle assetBundle = getAssetBundle(id);
                for (Asset asset : assetBundle.getAssets()) {
                    if (asset.getType() != type) continue;
                    if (asset.getFeature() != null && !featureContext.isEnabled(asset.getFeature())) continue;
                    if (header) {
                        writer.append("\n\n/*\nAsset: ").append(asset.getName()).append(", path='")
                                .append(defaultIfEmpty(asset.getPath(), NA_STRING)).append("'\n*/\n\n");
                    }
                    resource = asset.getResource();
                    if (resource instanceof ClassPathResource) {
                        resource = applicationService.getResourceService().resolveResource(resource);
                    }
                    if (!resource.exists() && missingAssets.add(resource.toURI().toASCIIString())) {
                        LOGGER.error("Asset {} ({}) in bundle {} could not be located in class path", asset.getName(), asset.getPath(), assetBundle.getName());
                    }
                    writer.append(resource.loadAsString());
                    if (counter < ids.length - 1) {
                        writer.append("\n\n");
                    }
                }
                counter++;
            }
        }
        resource = MemoryResource.create(buffer.toByteArray(), contentId);
        bundlesContent.put(contentId, resource);
        return resource;
    }

    Collection<AssetBundle> getAssetBundles() {
        waitForBundlesToLoad();
        return Collections.unmodifiableCollection(bundles.values());
    }

    int getAssetBundleCount() {
        return bundles.size();
    }

    Collection<AssetBundle> expandBundles(Collection<AssetBundle> bundles) {
        List<AssetBundleOrder> expandedBundles = new ArrayList<>();
        for (AssetBundle bundle : bundles) {
            expandBundles(expandedBundles, bundle);
        }
        for (int i = 0; i < 100; i++) {
            boolean orderChanged = false;
            for (AssetBundleOrder expandedBundle : expandedBundles) {
                orderChanged |= shiftOrder(expandedBundles, expandedBundle);
            }
            // if nothing changed, exit, they are in the right order
            if (!orderChanged) break;
        }
        expandedBundles.sort(AssetBundleOrder::compareTo);
        List<AssetBundle> orderedBundles = new ArrayList<>();
        for (AssetBundleOrder expandedBundle : expandedBundles) {
            orderedBundles.add(expandedBundle.assetBundle);
        }
        return orderedBundles;
    }

    Collection<String> getDependenciesForBundle(String id) {
        return assetBundleDependencies.computeIfAbsent(id, s -> new CopyOnWriteArrayList<>());
    }

    void load() {
        AssetBundleLoader loader = new AssetBundleLoader(this)
                .setThemeMode(applicationService.getProperties().getThemeMode());
        loader.load();
    }

    void loadDynamic() {
        for (AssetBundle bundle : bundles.values()) {
            loadDynamic(bundle);
        }
        latch.countDown();
    }

    void loadDynamic(AssetBundle bundle) {
        for (AssetBundleListener listener : listeners) {
            try {
                if (!listener.supports(bundle)) continue;
                Collection<Asset> asserts = new ArrayList<>();
                listener.update(bundle, asserts);
                asserts.forEach(bundle::addAsset);
            } catch (Exception e) {
                LOGGER.atError().setCause(e).log("Error updating asset bundle " + bundle.getName()
                        + " by listener " + ClassUtils.getName(listener));
            }
        }
    }

    private void expandBundles(Collection<AssetBundleOrder> expandedBundles, AssetBundle assetBundle) {
        if (!isExpanded(expandedBundles, assetBundle)) expandedBundles.add(new AssetBundleOrder(assetBundle));
        Collection<String> dependenciesForBundle = getDependenciesForBundle(assetBundle.getId());
        for (String dependency : dependenciesForBundle) {
            AssetBundle dependencyAssetBundle = applicationService.getAssetBundle(dependency);
            if (!isExpanded(expandedBundles, dependencyAssetBundle)) {
                expandBundles(expandedBundles, dependencyAssetBundle);
            }
        }
    }

    private boolean shiftOrder(Collection<AssetBundleOrder> expandedBundles, AssetBundleOrder expandedBundle) {
        Collection<String> dependenciesForBundle = getDependenciesForBundle(expandedBundle.assetBundle.getId());
        boolean changed = false;
        for (String dependency : dependenciesForBundle) {
            AssetBundleOrder resolvedDependency = getAssetBundleOrder(expandedBundles, dependency);
            // the bundle was eliminated, skip
            if (resolvedDependency == null) continue;
            if (resolvedDependency.order >= expandedBundle.order) {
                // since the dependency order is not lower, we need to move it
                changed = true;
                resolvedDependency.order--;
            }
        }
        return changed;
    }

    private boolean isExpanded(Collection<AssetBundleOrder> expandedBundles, AssetBundle bundle) {
        for (AssetBundleOrder expandedBundle : expandedBundles) {
            if (expandedBundle.assetBundle.equals(bundle)) {
                return true;
            }
        }
        return false;
    }

    private AssetBundleOrder getAssetBundleOrder(Collection<AssetBundleOrder> expandedBundles, String id) {
        for (AssetBundleOrder expandedBundle : expandedBundles) {
            if (id.equals(expandedBundle.assetBundle.getId())) {
                return expandedBundle;
            }
        }
        return null;
    }

    private void waitForBundlesToLoad() {
        if (!ConcurrencyUtils.await(latch, Duration.ofMinutes(5))) {
            LOGGER.warn("Timed out waiting for bundles to load");
        }
    }

    private void logOptions() {
        if (assetProperties.isDebug()) {
            LOGGER.warn("Asset management is in debug mode. Do not enable debug mode in production");
        }
    }

    static class AssetBundleOrder implements Comparable<AssetBundleOrder> {

        private final AssetBundle assetBundle;
        private int order;

        AssetBundleOrder(AssetBundle assetBundle) {
            this.assetBundle = assetBundle;
            this.order = assetBundle.getOrder();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AssetBundleOrder that = (AssetBundleOrder) o;

            return assetBundle.equals(that.assetBundle);
        }

        @Override
        public int hashCode() {
            return assetBundle.hashCode();
        }

        @Override
        public int compareTo(AssetBundleOrder o) {
            return Integer.compare(order, o.order);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("AssetBundleOrder{");
            sb.append("assetBundle=").append(assetBundle);
            sb.append(", order=").append(order);
            sb.append('}');
            return sb.toString();
        }
    }
}
