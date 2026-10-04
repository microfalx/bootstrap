package net.microfalx.bootstrap.core.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.IOUtils;
import net.microfalx.lang.NetworkUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.metrics.Metrics;

import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.StringUtils.*;

/**
 * A utility class to perform IP WhoIs lookups.
 */
@Getter
@ToString
public class IpWhoIs implements GeoLocation {

    private static final String URL = "https://ipwho.is/";
    private final String id;
    private final String ip;

    private String country = StringUtils.EMPTY_STRING;
    private String countryCode = StringUtils.EMPTY_STRING;
    private String region = StringUtils.EMPTY_STRING;
    private String regionCode = StringUtils.EMPTY_STRING;
    private String city = StringUtils.EMPTY_STRING;
    private String serviceProvider = StringUtils.EMPTY_STRING;
    private Double latitude;
    private Double longitude;

    private volatile boolean resolved;

    private static final Metrics METRICS = Metrics.of("IpWhoIs");
    private static final Metrics RESOLVED = METRICS.withGroup("Resolved");
    private static final Metrics FAILED = METRICS.withGroup("Failed");
    private static final Map<String, IpWhoIs> CACHE = new ConcurrentHashMap<>();

    public static IpWhoIs lookup(String ip) {
        requireNonNull(ip);
        return CACHE.computeIfAbsent(ip, s -> new IpWhoIs(ip));
    }

    private IpWhoIs(String ip) {
        requireNotEmpty(ip);
        this.id = toIdentifier(ip);
        this.ip = ip;
        resolve();
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public String getName() {
        return ip;
    }

    @Override
    public String getDescription() {
        if (resolved && (isNotEmpty(country) || isNotEmpty(region) || isNotEmpty(city))) {
            return defaultIfEmpty(region, NA_STRING) + " / " + defaultIfEmpty(city, NA_STRING)
                    + " (" + defaultIfEmpty(country, NA_STRING) + ")";
        } else {
            return EMPTY_STRING;
        }
    }

    private String getString(JsonNode root, String name) {
        JsonNode child = root.get(name);
        return child != null ? child.asText() : null;
    }

    private boolean getBoolean(JsonNode root, String name, boolean defaultValue) {
        JsonNode child = root.get(name);
        return child != null ? child.asBoolean() : defaultValue;
    }

    private Double getDouble(JsonNode root, String name) {
        JsonNode child = root.get(name);
        return child != null ? child.asDouble() : null;
    }

    private void resolve() {
        if (NetworkUtils.isLocalHost(ip) || NetworkUtils.isLocalNetwork(ip)) return;
        try {
            URL url = URI.create(URL + "/" + ip).toURL();
            String json = IOUtils.getInputStreamAsString(url.openStream());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            boolean success = getBoolean(root, "success", false);
            if (success) {
                country = getString(root, "country");
                countryCode = getString(root, "country_code");
                region = getString(root, "region");
                regionCode = getString(root, "region_code");
                city = getString(root, "city");
                JsonNode connectionNode = root.get("connection");
                if (connectionNode != null) {
                    serviceProvider = getString(connectionNode, "isp");
                }
                latitude = getDouble(root, "latitude");
                longitude = getDouble(root, "longitude");
            }
            RESOLVED.count(countryCode + " / " + regionCode);
            resolved = true;
        } catch (Exception e) {
            FAILED.count("Failed: " + ExceptionUtils.getRootCauseName(e));
        }
    }
}
