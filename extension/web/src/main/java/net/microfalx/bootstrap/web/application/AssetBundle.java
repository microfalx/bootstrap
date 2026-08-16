package net.microfalx.bootstrap.web.application;

import net.microfalx.lang.Descriptable;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.Nameable;
import net.microfalx.lang.StringUtils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Optional.ofNullable;
import static net.microfalx.bootstrap.web.application.ApplicationUtils.NO_VERSION;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.StringUtils.defaultIfEmpty;

/**
 * Represents a group of web resources.
 */
public final class AssetBundle implements Identifiable<String>, Nameable, Descriptable {

    private String id;
    private String version;
    private String name;
    private String description;
    private String path;
    private String theme;
    private String feature;
    private boolean inline;
    private boolean external;
    private boolean hasCss;
    private boolean hasJs;
    private boolean hasFont;
    private boolean hasImage;
    private int order = Integer.MIN_VALUE;
    private Boolean requiresAuthentication;

    private final Set<String> features = new HashSet<>();
    private final List<Asset> assets = new CopyOnWriteArrayList<>();

    static final ThreadLocal<AssetBundle> ASSET_BUNDLE = new ThreadLocal<>();

    /**
     * Returns the theme associated with the current thread.
     *
     * @return a non-null instance
     * @see ApplicationService#getCurrentTheme()
     */
    public static Optional<AssetBundle> get() {
        return ofNullable(ASSET_BUNDLE.get());
    }

    /**
     * Removes the asset bundle associated with the current thread.
     */
    public static void clear() {
        ASSET_BUNDLE.remove();
    }

    /**
     * Registers an asset to the current asset bundle.
     *
     * @param asset the asset to register
     * @see AssetBundle#get()
     */
    public static void register(Asset asset) {
        requireNonNull(asset);
        AssetBundle current = ASSET_BUNDLE.get();
        if (current == null) {
            current = new AssetBundle.Builder("dynamic_per_thread").inline(true).build();
            ASSET_BUNDLE.set(current);
        }
        current.addAsset(asset);
    }

    /**
     * Creates an asset bundle builder.
     *
     * @param id the bundle identifier
     * @return a non-null instance
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Returns the group identifier.
     *
     * @return a non-null string
     */
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return defaultIfEmpty(name, id);
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Returns the version associated with this resource group.
     *
     * @return a non-null instance
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns an additional path to be added to the resource final path.
     * <p/>
     * Several resources can be grouped together into a directories
     *
     * @return a path or null if resources are not grouped into a directory
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the order of inclusion.
     * <p/>
     * Asset bundles are order by scope and order
     *
     * @return an inclusion order
     */
    public int getOrder() {
        return order;
    }

    /**
     * Returns the feature associated with the asset.
     * <p>
     * If the feature is not active, the asset will not be included.
     *
     * @return the feature id, null if not associated with a feature
     */
    public String getFeature() {
        return feature;
    }

    /**
     * Returns whether the bundle is used to inject inline assets in HTML files.
     *
     * @return {@code true} if inline, {@code false} otherwise
     */
    public boolean isInline() {
        return isDynamic() || inline;
    }

    /**
     * Returns whether the asset bundle is made out of external asserts (i.e. assets with URI).
     *
     * @return {@code true} if external, {@code false} otherwise
     */
    public boolean isExternal() {
        return external;
    }

    /**
     * Returns whether the asset bundle has assets of the given type.
     *
     * @param type the asset type
     * @return {@code true} if has assets of the given type, {@code false} otherwise
     */
    public boolean has(Asset.Type type) {
        requireNonNull(type);
        if (isDynamic()) {
            return getDynamic().has(type);
        } else {
            return switch (type) {
                case JAVA_SCRIPT -> hasJs;
                case STYLE_SHEET -> hasCss;
                case FONT -> hasFont;
                case IMAGE -> hasImage;
            };
        }
    }

    /**
     * Returns the theme identifier.
     * <p>
     * An asset bundle which carries CSS & JS for a theme has the theme identifier associated with it.
     *
     * @return the theme identifier if the bundle supports a theme, null otherwise
     */
    public String getTheme() {
        return theme;
    }

    /**
     * Returns the resource part of this group.
     * <p>
     * The assets are order based on their {@link Asset#getOrder()}.
     *
     * @return a non-null collection
     */
    public Collection<Asset> getAssets() {
        if (isDynamic()) {
            return getDynamic().getAssets();
        } else {
            return unmodifiableCollection(assets);
        }
    }

    /**
     * Returns whether this asset will be rendered based on the security context.
     *
     * @return {@code true} if the asset is required only on authenticated pages, {@code false} if the asset is required on
     * pages accessible to anonymous users as well, null if not specified (i.e. the asset is required on all pages)
     */
    public Boolean isRequiresAuthentication() {
        return requiresAuthentication;
    }

    /**
     * Returns the content type of this asset bundle.
     *
     * @return a non-null instance
     */
    public String getContentType() {
        if (assets.isEmpty()) return "application/octet-stream";
        return assets.getFirst().getContentType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssetBundle that = (AssetBundle) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Registers a new asset as is at the end of the list.
     *
     * @param asset the asset
     */
    void addAsset(Asset asset) {
        requireNonNull(asset);
        if (asset.order == Integer.MIN_VALUE) asset.order = this.assets.size();
        if (!this.assets.contains(asset)) this.assets.add(asset);
        updateAfterAdd();
        asset.assetBundle = this;
    }

    /**
     * Registers a new asset as is as first item.
     *
     * @param asset the asset
     */
    void insertAsset(Asset asset) {
        requireNonNull(asset);
        if (asset.order == Integer.MIN_VALUE) asset.order = this.assets.size();
        if (!this.assets.contains(asset)) this.assets.addFirst(asset);
        asset.assetBundle = this;
    }

    private void updateAfterAdd() {
        int externalCount = (int) this.assets.stream().filter(Asset::isExternal).count();
        hasCss = this.assets.stream().anyMatch(a -> a.getType() == Asset.Type.STYLE_SHEET);
        hasJs = this.assets.stream().anyMatch(a -> a.getType() == Asset.Type.JAVA_SCRIPT);
        hasFont = this.assets.stream().anyMatch(a -> a.getType() == Asset.Type.FONT);
        hasImage = this.assets.stream().anyMatch(a -> a.getType() == Asset.Type.IMAGE);
        this.external = externalCount > 0;
        if (this.external && externalCount != this.assets.size()) {
            throw new IllegalStateException("An asset bundle cannot mix external and non-external assets");
        }
    }

    private boolean isDynamic() {
        return "dynamic".equals(getId()) && AssetBundle.get().isPresent();
    }

    private static AssetBundle getDynamic() {
        return AssetBundle.get().orElseThrow(() -> new IllegalStateException("No dynamic asset bundle is set"));
    }

    @Override
    public String toString() {
        return "AssetBundle{" +
                "id='" + id + '\'' +
                ", version='" + version + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", path='" + path + '\'' +
                ", order=" + order +
                ", requiresAuthentication=" + requiresAuthentication +
                ", assets=" + assets.size() +
                '}';
    }

    public static class Builder {

        private final String id;
        private String version = NO_VERSION;
        private String name;
        private String description;
        private String path;
        private String theme;
        private String feature;
        private boolean inline;
        private int order;
        private Boolean requiresAuthentication;
        private final List<Asset> assets = new ArrayList<>();

        Builder(String id) {
            requireNonNull(id);
            this.id = StringUtils.toIdentifier(id);
        }

        public Builder name(String name) {
            requireNonNull(name);
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            requireNonNull(version);
            this.version = version;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder inline(boolean inline) {
            this.inline = inline;
            return this;
        }

        public Builder feature(String feature) {
            this.feature = feature;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder theme(String theme) {
            this.theme = theme != null ? StringUtils.toIdentifier(theme) : null;
            return this;
        }

        public Builder asset(Asset asset) {
            requireNonNull(asset);
            // if order is not provisioned, give one based the current position in the list
            if (asset.order == Integer.MIN_VALUE) asset.order = this.assets.size();
            this.assets.add(asset);
            return this;
        }

        public Builder requiresAuthentication(boolean requiresAuthentication) {
            this.requiresAuthentication = requiresAuthentication;
            return this;
        }

        public AssetBundle build() {
            AssetBundle assetBundle = new AssetBundle();
            assetBundle.id = id;
            assetBundle.name = name;
            assetBundle.description = description;
            assetBundle.version = version;
            assetBundle.path = path;
            assetBundle.feature = feature;
            assetBundle.inline = inline;
            assetBundle.theme = theme;
            assetBundle.requiresAuthentication = requiresAuthentication;
            assetBundle.order = order;
            for (Asset asset : assets) {
                asset.assetBundle = assetBundle;
                assetBundle.assets.add(asset);
            }
            assetBundle.assets.sort(Comparator.comparingInt(Asset::getOrder));
            assetBundle.updateAfterAdd();
            return assetBundle;
        }
    }


}
