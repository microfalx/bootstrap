package net.microfalx.bootstrap.web.application;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A replica of the browser local storage. This class is used to store data
 * on the server side and replicated in the browser local storage.
 */
public class LocalStorage {

    private final Map<String, Object> storage = new HashMap<>();

    private final static ThreadLocal<LocalStorage> LOCAL_STORAGE = ThreadLocal.withInitial(LocalStorage::new);

    /**
     * Returns the local storage instance associated with the current thread.
     *
     * @return a non-null instance
     */
    public static LocalStorage current() {
        return LOCAL_STORAGE.get();
    }

    /**
     * Changes the local storage instance associated with the current thread.
     *
     * @param id the id, null to remove (not applicable for local storage)
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String id) {
        requireNonNull(id);
        return (T) current().storage.get(id);
    }

    /**
     * Changes the local storage instance associated with the current thread.
     *
     * @param id the id, null to remove (not applicable for local storage)
     */
    public static <T> void set(String id, T value) {
        requireNonNull(id);
        current().storage.put(id, value);
    }

    /**
     * Marks the entry as "to be cleared" from local storage instance associated with the current thread.
     * <p>
     * A clear operation is replicated to the client local storage, which is different from a remove operation,
     * which is not replicated to the client local storage.
     *
     * @param id the id of the value to remove
     */
    public static void clear(String id) {
        requireNonNull(id);
        current().storage.put(id, null);
    }

    /**
     * Removes a value from the local storage instance associated with the current thread.
     *
     * @param id the id of the value to remove
     */
    public static void remove(String id) {
        requireNonNull(id);
        current().storage.remove(id);
    }

    /**
     * Returns whether the local storage instance associated with the current thread contains
     * a value for the given id.
     *
     * @param id the id of the value to check
     * @return {@code true} if the local storage contains a value for the given id,
     * {@code false} otherwise
     */
    public static boolean has(String id) {
        requireNonNull(id);
        return current().storage.containsKey(id);
    }

    /**
     * Returns whether the local storage instance associated with the current thread is empty.
     *
     * @return {@code true} if the local storage is empty, {@code false} otherwise
     */
    public static boolean isEmpty() {
        return current().storage.isEmpty();
    }

    /**
     * Removes the local storage instance associated with the current thread.
     */
    public static void clear() {
        LOCAL_STORAGE.remove();
    }

    /**
     * Returns the values associated with the current thread local storage
     * as an unmodifiable map.
     *
     * @return a non-null instance
     */
    public Map<String, Object> toMap() {
        return unmodifiableMap(storage);
    }
}
