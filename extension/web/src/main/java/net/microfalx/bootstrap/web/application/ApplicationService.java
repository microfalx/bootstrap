package net.microfalx.bootstrap.web.application;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.bootstrap.feature.FeatureContext;
import net.microfalx.bootstrap.feature.FeatureService;
import net.microfalx.bootstrap.resource.ResourceService;
import net.microfalx.bootstrap.web.component.Menu;
import net.microfalx.bootstrap.web.container.WebContainerService;
import net.microfalx.bootstrap.web.util.SecurityUtils;
import net.microfalx.lang.*;
import net.microfalx.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.StringUtils.defaultIfNull;
import static net.microfalx.lang.StringUtils.isNotEmpty;
import static net.microfalx.lang.TextUtils.insertSpaces;

/**
 * A service which provides metadata for a web application.
 */
@Service("WebApplicationService")
public final class ApplicationService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationService.class);

    private static final long timestamp = System.currentTimeMillis();

    @Autowired(required = false) private ApplicationProperties applicationProperties = new ApplicationProperties();

    @Autowired(required = false) private AssetProperties assetProperties = new AssetProperties();

    @Autowired private ApplicationContext applicationContext;
    @Autowired private WebContainerService webContainerService;
    @Autowired private FeatureService featureService;
    @Autowired private ResourceService resourceService;
    @Autowired private net.microfalx.bootstrap.application.ApplicationService applicationService;

    private final AssetBundleManager assetBundleManager = new AssetBundleManager(this);
    private final Map<String, Menu> navigations = new ConcurrentHashMap<>();
    private final Collection<DomainTheme> domainsToTheme = new ArrayList<>();

    private final Application application = new Application();

    /**
     * Returns the active theme. associated with this thread.
     * <p>
     * The active theme is the theme assigned to the current thread, or the default theme
     * if no theme is assigned.
     *
     * @return a non-null in instance
     * @see Theme#get()
     * @see Theme#set(Theme)
     */
    public Theme getCurrentTheme() {
        return Theme.get().orElse(application.getTheme());
    }

    /**
     * Returns the application description.
     *
     * @return a non-null instance
     */
    public Application getApplication() {
        return application;
    }

    /**
     * Registers a navigation.
     *
     * @param navigation the navigation
     */
    public void registerNavigation(Menu navigation) {
        requireNonNull(navigation);
        if (navigations.containsKey(navigation.getId())) {
            throw new ApplicationException("A navigation with identifier '" + navigation.getId() + " is already registered");
        }
        navigations.put(navigation.getId(), navigation);
    }

    /**
     * Returns the navigation with a given identifier.
     *
     * @param id the navigation identifier
     * @return the navigation
     * @throws ApplicationException if the navigation is not registered
     */
    public Menu getNavigation(String id) {
        requireNonNull(id);
        Menu navigation = navigations.get(id.toLowerCase());
        if (navigation == null) {
            throw new ApplicationException("A navigation with identifier '" + id + "' is not registered");
        }
        return navigation;
    }

    /**
     * Returns all registered navigations.
     *
     * @return a non-null instance
     */
    public Collection<Menu> getNavigations() {
        return Collections.unmodifiableCollection(navigations.values());
    }

    /**
     * Returns all registered themes.
     *
     * @return a non-null instance
     */
    public Collection<Theme> getThemes() {
        return assetBundleManager.getThemes();
    }

    /**
     * Returns the theme using its identifier or name.
     *
     * @param idOrName the identifier or the name of the theme
     * @return the theme
     * @throws ApplicationException if the theme does not exist
     */
    public Theme getTheme(String idOrName) {
        return assetBundleManager.getTheme(idOrName);
    }

    /**
     * Returns a theme to be used for a given domain name.
     *
     * @param domainName the domain name
     * @return the theme, or empty if no theme is associated with the domain
     */
    public Optional<Theme> getThemeForDomain(String domainName) {
        requireNotEmpty(domainName);
        for (DomainTheme domainTheme : domainsToTheme) {
            if (domainTheme.getPattern().matcher(domainName).matches()) {
                return Optional.of(domainTheme.getTheme());
            }
        }
        return Optional.empty();
    }

    /**
     * Registers a new theme.
     *
     * @param theme the theme
     */
    public void registerTheme(Theme theme) {
        assetBundleManager.registerTheme(theme);
    }

    /**
     * Registers a new asset bundle.
     *
     * @param assetBundle the asset bundle
     */
    public void registerAssetBundle(AssetBundle assetBundle) {
        assetBundleManager.registerAssetBundle(assetBundle);
    }

    /**
     * Returns a previously registered asset bundle.
     *
     * @param id the asset bundle identifier
     * @return the resource group with the newest version
     */
    public AssetBundle getAssetBundle(String id) {
        return assetBundleManager.getAssetBundle(id);
    }

    /**
     * Returns all asset bundles.
     *
     * @return the resource group with the newest version
     */
    public Collection<AssetBundle> getAssetBundles() {
        return assetBundleManager.getAssetBundles();
    }

    /**
     * Returns a previously registered collection of asset bundle.
     * <p>
     * If an empty list is requested, all asset bundles are returned.
     *
     * @param ids the asset bundle identifiers
     * @return a non-null instance
     */
    public Collection<AssetBundle> getAssetBundles(String... ids) {
        Collection<AssetBundle> finalBundles = new ArrayList<>();
        Theme currentTheme = getCurrentTheme();
        if (ObjectUtils.isEmpty(ids)) {
            finalBundles.addAll(getAssetBundles().stream().filter(assetBundle -> {
                String assetBundleTheme = assetBundle.getTheme();
                boolean matchedByTheme = assetBundleTheme == null || assetBundleTheme.equalsIgnoreCase(currentTheme.getId());
                return matchedByTheme && !assetBundle.isInline();
            }).toList());
        } else {
            for (String id : ids) {
                finalBundles.add(getAssetBundle(id));
            }
        }
        return assetBundleManager.expandBundles(finalBundles);
    }

    /**
     * Returns the content of one or more asset bundle.
     * <p>
     * The assets are separated by a header to distinguish when a bundle ends and another one starts.
     *
     * @param type the type of the asset
     * @param id   ids the asset bundle identifier
     * @return the resource with the bundle content
     */
    public Resource getAssetBundleContent(Asset.Type type, String id) throws IOException {
        return getAssetBundlesContent(type, true, id);
    }

    /**
     * Returns the content of one or more asset bundle.
     *
     * @param type   the type of the asset
     * @param header {@code true} to include a header for each asset, {@code false} otherwise
     * @param ids    ids the asset bundle identifiers
     * @return the resource with the bundle content
     */
    public Resource getAssetBundlesContent(Asset.Type type, boolean header, String... ids) throws IOException {
        return assetBundleManager.getAssetBundlesContent(type, header, ids);
    }

    /**
     * Returns the HTML tag to include this external asset in a web page.
     *
     * @param asset the asset
     * @param type  the asset type to referenced
     * @return the HTML tag
     */
    public String getAssetTag(Asset asset, Asset.Type type) {
        requireNonNull(asset);
        requireNonNull(type);
        if (!(asset.isExternal() || asset.isMemory())) {
            throw new ApplicationException("Asset with identifier '" + asset.getId() + "' is not external or in-memory");
        }
        URI uri = asset.toExternalUri();
        Resource resource = uri == null ? asset.getResource() : null;
        StringBuilder builder = new StringBuilder();
        switch (type) {
            case JAVA_SCRIPT -> {
                builder.append("<script type=\"text/javascript\"");
                if (uri != null) {
                    builder.append(" src=\"").append(uri).append('"');
                }
                if (asset.isAsync()) builder.append(" async");
                if (asset.isDefer()) builder.append(" defer");
                if (isNotEmpty(asset.getOnLoad())) {
                    builder.append(" onload=\"").append(asset.getOnLoad()).append('"');
                }
            }
            case STYLE_SHEET -> {
                builder.append("<link rel=\"stylesheet\" type=\"text/css\"");
                if (uri != null) {
                    builder.append(" href=\"").append(uri).append('"');
                }
            }
            default -> throw new IllegalStateException("Unhandled asset type: " + type);
        }
        appendEndOfTag(builder, type, resource);
        return builder.toString();
    }

    ResourceService getResourceService() {
        return resourceService;
    }

    FeatureService getFeatureService() {
        return featureService;
    }

    ApplicationProperties getProperties() {
        return applicationProperties;
    }

    private void appendEndOfTag(StringBuilder builder, Asset.Type type, Resource resource) {
        builder.append(">");
        if (resource != null) {
            builder.append("\n");
            try {
                builder.append(TextUtils.insertSpaces(resource.loadAsString(), 2));
            } catch (IOException e) {
                ExceptionUtils.rethrowException(e);
            }
            builder.append("\n");
        }
        switch (type) {
            case JAVA_SCRIPT:
                builder.append("</script>");
                break;
            case STYLE_SHEET:
                //builder.append("</link>"); No end tag for link!
                break;
            default:
                throw new IllegalStateException("Unhandled asset type: " + type);
        }
    }

    /**
     * Returns the HTML tag to include this asset bundle in a web page.
     *
     * @param assetBundle the asset
     * @param type        the asset type to referenced
     * @return the HTML tag
     */
    public String getAssetBundleTag(AssetBundle assetBundle, Asset.Type type) {
        requireNonNull(assetBundle);
        requireNonNull(type);
        StringBuilder builder = new StringBuilder();
        String path = "asset/";
        switch (type) {
            case JAVA_SCRIPT -> {
                builder.append("<script type=\"text/javascript\" src=\"");
                path += "js";
            }
            case STYLE_SHEET -> {
                builder.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"");
                path += "css";
            }
            default -> throw new IllegalStateException("Unhandled asset type: " + type);
        }
        path += "/" + assetBundle.getId();
        path = webContainerService.getPath(path);
        builder.append(path).append("?version=");
        if (isNotEmpty(assetBundle.getVersion())) {
            builder.append(assetBundle.getVersion());
        } else {
            builder.append(Long.toString(timestamp, Character.MAX_RADIX));
        }
        builder.append('"');
        appendEndOfTag(builder, type, null);
        return builder.toString();
    }

    /**
     * Returns the HTML tags to include the stylesheets provided by the given bundles.
     * <p>
     * If an empty list is requested, all asset bundles are returned.
     *
     * @param assetBundles a list of asset bundles
     * @param indent       how many spaces to insert in front of each line
     * @return the HTML tags
     */
    public String getStylesheets(int indent, String... assetBundles) {
        return getAssetBundleTags(Asset.Type.STYLE_SHEET, indent, getAssetBundles(assetBundles));
    }

    /**
     * Returns the HTML tags to include the scripts provided by the given bundles.
     * <p>
     * If an empty list is requested, all asset bundles are returned.
     *
     * @param assetBundles a list of asset bundles
     * @param indent       how many spaces to insert in front of each line
     * @return the HTML tags
     */
    public String getScripts(int indent, String... assetBundles) {
        return getAssetBundleTags(Asset.Type.JAVA_SCRIPT, indent, getAssetBundles(assetBundles));
    }

    /**
     * Returns the HTML tags to include the asset bundles.
     *
     * @param type         the asset type to return
     * @param assetBundles a list of asset bundles
     * @param indent       how many spaces to insert in front of each line
     * @return the HTML tags
     */
    public String getAssetBundleTags(Asset.Type type, int indent, Collection<AssetBundle> assetBundles) {
        requireNonNull(assetBundles);
        StringBuilder builder = new StringBuilder();
        Iterator<AssetBundle> assetBundleIterator = assetBundles.iterator();
        while (assetBundleIterator.hasNext()) {
            AssetBundle assetBundle = assetBundleIterator.next();
            if (!(assetBundle.has(type) && shouldRender(assetBundle))) continue;
            if (assetBundle.isExternal()) {
                Iterator<Asset> assetIterator = assetBundle.getAssets().iterator();
                while (assetIterator.hasNext()) {
                    Asset asset = assetIterator.next();
                    if (!shouldRender(asset)) continue;
                    builder.append(getAssetTag(asset, type));
                    if (assetIterator.hasNext()) builder.append('\n');
                }
            } else {
                String assetBundleTag = getAssetBundleTag(assetBundle, type);
                builder.append(assetBundleTag);
            }
            if (assetBundleIterator.hasNext()) builder.append('\n');
        }
        return insertSpaces(builder.toString(), indent);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initApplication();
        initAssets();
        initNavigation();
        initTheme();
        initDomains();
        logApplication();
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onStart(ApplicationStartedEvent event) {
        LOGGER.info("Started application, version: {}, build number: {}, build time: {}",
                application.getVersion(), application.getBuildNumber(), application.getBuildTime());
        assetBundleManager.loadDynamic();
    }

    private void initTheme() {
        application.theme = getTheme(defaultIfNull(applicationProperties.getTheme(), Theme.DEFAULT));
        if (isNotEmpty(applicationProperties.getSystemTheme())) {
            application.theme = getTheme(defaultIfNull(applicationProperties.getSystemTheme(), Theme.SYSTEM));
        } else {
            application.systemTheme = application.theme;
        }
    }

    private void initDomains() {
        Map<String, String> domainThemeMap = applicationProperties.getDomainThemes();
        if (ObjectUtils.isEmpty(domainThemeMap)) return;
        for (Map.Entry<String, String> entry : domainThemeMap.entrySet()) {
            String themeId = entry.getKey();
            String domainPatternValue = entry.getValue();
            try {
                String[] domainPatterns = StringUtils.split(domainPatternValue, ",");
                for (String domainPattern : domainPatterns) {
                    Pattern domainPatternCompiled = Pattern.compile(domainPattern, Pattern.CASE_INSENSITIVE);
                    Theme theme = getTheme(themeId);
                    domainsToTheme.add(new DomainTheme(domainPatternCompiled, theme));
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load domain theme for pattern '{}' and theme id '{}'", domainPatternValue, themeId, e);
            }
        }
    }

    private void initApplication() {
        net.microfalx.bootstrap.application.Application coreApplication = applicationService.getApplication();
        application.name = coreApplication.getName();
        application.description = coreApplication.getDescription();
        application.owner = coreApplication.getVendor();
        application.url = coreApplication.getUrl();
        application.logo = applicationProperties.getLogo();
        application.version = coreApplication.getVersion();
        application.buildNumber = coreApplication.getBuildNumber();
        application.buildTime = coreApplication.getBuildTime();
        application.buildTime = coreApplication.getBuildTime();
        application.timeZone = coreApplication.getTimeZone();
    }

    private void initAssets() {
        assetBundleManager.initialize(applicationContext, assetProperties);
        assetBundleManager.load();
        initAssetBundleListeners();
    }

    private void initNavigation() {
        new NavigationLoader(this).load();
    }

    private void logApplication() {
        LOGGER.info("Initialize application: {} ({} / {}/ {}), theme {}, logo {}", application.getName(), application.getVersion(),
                application.getBuildNumber(), application.getBuildTime(), application.getTheme().getName(), application.getLogo());
    }

    private void initAssetBundleListeners() {
        Collection<AssetBundleListener> listeners = ClassUtils.resolveProviderInstances(AssetBundleListener.class);
        listeners.forEach(assetBundleManager::registerListener);
        LOGGER.info("Registered {} asset bundle listeners", listeners.size());

    }

    private String getImageBasePath() {
        return webContainerService.getPath("image");
    }

    private String getFontBasePath() {
        return webContainerService.getPath("font");
    }

    private boolean shouldRender(Asset asset) {
        FeatureContext featureContext = FeatureContext.get();
        if (asset.getFeature() != null && !featureContext.isEnabled(asset.getFeature())) return false;
        return shouldRender(asset.isRequiresAuthentication());
    }

    private boolean shouldRender(AssetBundle assetBundle) {
        FeatureContext featureContext = FeatureContext.get();
        if (assetBundle.getFeature() != null && !featureContext.isEnabled(assetBundle.getFeature())) return false;
        return shouldRender(assetBundle.isRequiresAuthentication());

    }

    private boolean shouldRender(Boolean requiresAuthentication) {
        if (requiresAuthentication == null) return true;
        boolean authenticated = SecurityUtils.isAuthenticated();
        if (requiresAuthentication && !authenticated) {
            return false;
        } else if (!requiresAuthentication && authenticated) {
            return false;
        } else {
            return true;
        }
    }

    @Getter
    @ToString
    @AllArgsConstructor
    private static class DomainTheme {
        private final Pattern pattern;
        private final Theme theme;
    }


}
