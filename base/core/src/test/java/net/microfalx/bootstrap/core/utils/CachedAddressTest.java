package net.microfalx.bootstrap.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CachedAddressTest {

    @Test
    void getCanonicalHostName() {
        CachedAddress address = CachedAddress.get("google.com");
        assertNotNull(address.getCanonicalHostName());
    }

    @Test
    void getGoogle() {
        CachedAddress address = CachedAddress.get("google.com");
        assertFalse(address.isIp());
        assertTrue(address.isResolved());
        assertNotNull(address.getAddress());
        assertFalse(address.getAddresses().isEmpty());
        assertNotNull(address.getHostname());
        assertNotNull(address.getCanonicalHostName());
        assertFalse(address.getAliases().isEmpty());
    }

    @Test
    void getLocalhost() {
        CachedAddress address = CachedAddress.get("localhost");
        assertFalse(address.isIp());
        assertTrue(address.isResolved());
        assertNotNull(address.getAddress());
        assertFalse(address.getAddresses().isEmpty());
        assertNotNull(address.getHostname());
        assertNotNull(address.getCanonicalHostName());
        assertFalse(address.getAliases().isEmpty());
    }
}