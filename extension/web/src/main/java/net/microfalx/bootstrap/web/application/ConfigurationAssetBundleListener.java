package net.microfalx.bootstrap.web.application;

import net.microfalx.bootstrap.configuration.ConfigurationService;
import net.microfalx.bootstrap.core.utils.ApplicationContextSupport;
import net.microfalx.configuration.Configuration;
import net.microfalx.configuration.Metadata;
import net.microfalx.lang.SecretUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Collection;

import static net.microfalx.lang.StringUtils.defaultIfEmpty;

@Provider
public class ConfigurationAssetBundleListener extends ApplicationContextSupport implements AssetBundleListener {

    @Override
    public boolean supports(AssetBundle assetBundle) {
        return "app".equals(assetBundle.getId());
    }

    @Override
    public void update(AssetBundle assetBundle, Collection<Asset> assets) {
        ConfigurationService configurationService = getBean(ConfigurationService.class);
        String js = generateConfigurationEntries(configurationService);
        assets.add(Asset.resource(Asset.Type.JAVA_SCRIPT, Resource.text(js)).name("Configuration").build());
    }

    private String generateConfigurationEntries(ConfigurationService configurationService) {
        Configuration configuration = configurationService.getConfiguration();
        StringBuilder builder = new StringBuilder();
        builder.append("const APP_CONFIGURATION = {\n");
        MutableInt count = new MutableInt();
        appendKeys(builder, configuration, configurationService.getRootMetadata(), count);
        builder.append("\n};");
        return builder.toString();
    }

    private void appendKeys(StringBuilder builder, Configuration configuration, Metadata metadata, MutableInt count) {
        if (metadata.isLeaf()) {
            appendKey(builder, configuration, metadata, count);
        } else {
            for (Metadata metadataChild : metadata.getChildren()) {
                appendKeys(builder, configuration, metadataChild, count);
            }
        }
    }

    private void appendKey(StringBuilder builder, Configuration configuration, Metadata metadata, MutableInt count) {
        String key = metadata.getFullKey();
        if (SecretUtils.isSecret(key) || !metadata.isClient()) return;
        if (count.intValue() > 0) builder.append(",\n");
        builder.append("  '").append(metadata.getFullKey()).append("': ")
                .append(getValue(configuration, metadata));
        count.increment();
    }

    private String getValue(Configuration configuration, Metadata metadata) {
        String key = metadata.getFullKey();
        Metadata.DataType dataType = metadata.getDataType();
        String value = defaultIfEmpty(configuration.get(key, null), metadata.getDefaultValue());
        if (value == null) {
            if (dataType == Metadata.DataType.BOOLEAN) {
                return Boolean.toString(StringUtils.asBoolean(metadata.getDefaultValue(), false));
            } else {
                return "null";
            }
        }
        if (dataType.isNumeric()) {
            return value;
        } else {
            return "'" + value + "'";
        }
    }
}
