package net.microfalx.bootstrap.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A facade for data type conversions.
 * <p>
 * The string representation of a collection or map or a complex object is expected to be in JSON format. JSON content
 * can be provided as a String, Reader, File, InputStream or Resource.
 *
 * @deprecated Use {@link net.microfalx.lang.convert.Types} instead.
 */
@Deprecated
public class Types {

    private Types() {
    }

    /**
     * Converts an object to a target type.
     *
     * @param value  the value to convert
     * @param target the target class
     * @param <T>    the type of the target value
     * @return the converted value
     */
    public static <T> T from(Object value, Class<T> target) {
        return net.microfalx.lang.convert.Types.from(value, target);
    }

    /**
     * Converts the object to a String.
     * <p>
     * If complex object is provided, it will be converted to JSON string.
     *
     * @param value the value to convert
     * @return the converted value
     */
    public static String asString(Object value) {
        return net.microfalx.lang.convert.Types.asString(value);
    }

    /**
     * Converts a JSON object to a Collection.
     *
     * @param value the value
     * @return the converted collection
     */
    public static Collection<?> asCollection(Object value) {
        return net.microfalx.lang.convert.Types.asCollection(value);
    }

    /**
     * Converts a JSON object to a Collection of a given type.
     *
     * @param value       the value
     * @param elementType the element class
     * @param <T>         the element type
     * @return the converted collection
     */
    public static <T> Collection<T> asCollection(Object value, Class<T> elementType) {
        return net.microfalx.lang.convert.Types.asCollection(value, elementType);
    }

    /**
     * Converts a JSON object to a Set.
     *
     * @param value the value
     * @return the converted collection
     */
    public static Set<?> asSet(Object value) {
        return net.microfalx.lang.convert.Types.asSet(value);
    }

    /**
     * Converts a JSON object to a Set of a given type.
     *
     * @param value       the value
     * @param elementType the element class
     * @param <T>         the element type
     * @return the converted collection
     */
    static <T> Set<T> asSet(Object value, Class<T> elementType) {
        return net.microfalx.lang.convert.Types.asSet(value, elementType);
    }

    /**
     * Converts a JSON object to a Map.
     *
     * @param value the value
     * @return the converted collection
     */
    public static <T> Map<String, T> asMap(Object value) {
        return net.microfalx.lang.convert.Types.asMap(value);
    }

    /**
     * Converts a JSON object to a Set of a given type.
     *
     * @param value       the value
     * @param elementType the element class
     * @param <T>         the element type
     * @return the converted collection
     */
    public static <T> T asObject(Object value, Class<T> elementType) {
        return net.microfalx.lang.convert.Types.asObject(value, elementType);
    }

}
