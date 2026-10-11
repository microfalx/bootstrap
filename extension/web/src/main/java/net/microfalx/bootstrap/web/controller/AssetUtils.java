package net.microfalx.bootstrap.web.controller;

import net.microfalx.bootstrap.web.application.ApplicationException;
import net.microfalx.bootstrap.web.application.Asset;
import net.microfalx.lang.StringUtils;
import net.microfalx.resource.ClassPathResource;
import net.microfalx.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Duration;

import static net.microfalx.lang.StringUtils.removeStartSlash;

/**
 * Utilities around assets.
 */
public class AssetUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetUtils.class);

    /**
     * Returns a response for a static resource.
     *
     * @param path the path
     * @param type the type
     * @return the entity
     */
    public static ResponseEntity<Object> getResource(String path, Asset.Type type) {
        return getResource(path, type, null);
    }

    /**
     * Returns a response for a static resource.
     *
     * @param path        the path
     * @param type        the type
     * @param defaultPath the default path, if the resource at path does not exists (can be null)
     * @return the entity
     */
    public static ResponseEntity<Object> getResource(String path, Asset.Type type, String defaultPath) {
        String typePath = switch (type) {
            case IMAGE -> "image";
            case FONT -> "font";
            default -> throw new ApplicationException("Unknown resource type: " + type);
        };
        try {
            Resource content = ClassPathResource.file(typePath + "/" + removeStartSlash(path));
            if (!content.exists() && StringUtils.isNotEmpty(defaultPath)) {
                content = ClassPathResource.file(typePath + "/" + removeStartSlash(defaultPath));
            }
            if (!content.exists()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(getContentType(type)))
                    .eTag(content.toHash()).cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)))
                    .body(new InputStreamResource(content.getInputStream()));
        } catch (IOException e) {
            String message = "Failed to retrieve " + type.name().toLowerCase() + " at " + path;
            LOGGER.error(message, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message);
        }
    }

    /**
     * Returns the content type for a given asset type
     *
     * @param type
     * @return
     */
    public static String getContentType(Asset.Type type) {
        return switch (type) {
            case STYLE_SHEET -> "text/css;charset=UTF-8";
            case JAVA_SCRIPT -> "text/javascript;charset=UTF-8";
            default -> "application/octet-stream";
        };
    }
}
