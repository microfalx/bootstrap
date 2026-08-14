package net.microfalx.bootstrap.web.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalStorageTest {

    @AfterEach
    void afterEach() {
        LocalStorage.clear();
    }

    @Test
    void setAndGetAsMap() {
        LocalStorage.set("language", "en");

        Map<String, Object> values = LocalStorage.get().toMap();

        assertThat(LocalStorage.has("language")).isTrue();
        assertThat(values).containsEntry("language", "en");
    }

    @Test
    void removeValue() {
        LocalStorage.set("theme", "dark");

        LocalStorage.remove("theme");

        assertThat(LocalStorage.has("theme")).isFalse();
        assertThat(LocalStorage.get().toMap()).doesNotContainKey("theme");
    }

    @Test
    void clearResetsStorageForCurrentThread() {
        LocalStorage.set("timezone", "UTC");

        LocalStorage.clear();

        assertThat(LocalStorage.get().toMap()).isEmpty();
    }

    @Test
    void rejectNullId() {
        assertThrows(IllegalArgumentException.class, () -> LocalStorage.set(null, "value"));
        assertThrows(IllegalArgumentException.class, () -> LocalStorage.remove(null));
        assertThrows(IllegalArgumentException.class, () -> LocalStorage.has(null));
    }

    @Test
    void storageIsThreadLocal() throws InterruptedException {
        LocalStorage.set("tenant", "main-thread");
        AtomicReference<Map<String, Object>> valuesFromOtherThread = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            LocalStorage.set("tenant", "worker-thread");
            valuesFromOtherThread.set(LocalStorage.get().toMap());
            LocalStorage.clear();
        });
        thread.start();
        thread.join();

        assertThat(valuesFromOtherThread.get()).containsEntry("tenant", "worker-thread");
        assertThat(valuesFromOtherThread.get()).doesNotContainEntry("tenant", "main-thread");
        assertThat(LocalStorage.get().toMap()).containsEntry("tenant", "main-thread");
    }
}

