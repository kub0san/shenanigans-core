package com.hizeric.shenaniganscore.resourcepack;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * F-01/F-02 regression: the advertised resource-pack URL must never point at a
 * loopback/wildcard address, and invalid configuration must fail fast in the pure
 * resolver rather than escaping enable() during plugin startup.
 */
class ResourcePackUriTest {

    @Test
    void explicitReachableHostIsAdvertised() {
        URI uri = ResourcePackManager.resolveAdvertisedUri("play.example.com", 8123, "0.0.0.0");
        assertEquals("http://play.example.com:8123/shenanigans-resource-pack.zip", uri.toString());
    }

    @Test
    void blankHostFallsBackToReachableBindAddress() {
        URI uri = ResourcePackManager.resolveAdvertisedUri("", 8123, "10.0.0.5");
        assertEquals("http://10.0.0.5:8123/shenanigans-resource-pack.zip", uri.toString());
    }

    @Test
    void configuredLoopbackIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackManager.resolveAdvertisedUri("127.0.0.1", 8123, "10.0.0.5"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackManager.resolveAdvertisedUri("localhost", 8123, "10.0.0.5"));
    }

    @Test
    void loopbackOrWildcardBindIsRejectedWhenNoHostConfigured() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackManager.resolveAdvertisedUri("", 8123, "127.0.0.1"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackManager.resolveAdvertisedUri("  ", 8123, "0.0.0.0"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackManager.resolveAdvertisedUri(null, 8123, "::1"));
    }

    @Test
    void invalidPortIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackManager.resolveAdvertisedUri("play.example.com", 0, "0.0.0.0"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackManager.resolveAdvertisedUri("play.example.com", 65536, "0.0.0.0"));
    }

    @Test
    void configuredHostStillWinsOverDetection() {
        assertEquals("play.example.com", ResourcePackManager.chooseAdvertisedHost(
                " play.example.com ", "0.0.0.0", List.of("192.168.1.50")));
    }

    @Test
    void reachableBindIsUsedWithoutDetection() {
        assertEquals("10.0.0.5", ResourcePackManager.chooseAdvertisedHost(
                "", "10.0.0.5", List.of("192.168.1.50")));
    }

    @Test
    void wildcardBindFallsBackToADetectedSiteLocalAddress() {
        String host = ResourcePackManager.chooseAdvertisedHost(
                "", "0.0.0.0", List.of("127.0.0.1", "0.0.0.0", "203.0.113.9", "192.168.1.50"));
        assertEquals("192.168.1.50", host);
        assertEquals("http://192.168.1.50:8123/shenanigans-resource-pack.zip",
                ResourcePackManager.resolveAdvertisedUri(host, 8123, "0.0.0.0").toString());
    }

    @Test
    void publicAddressIsUsedWhenNoSiteLocalExists() {
        assertEquals("203.0.113.9", ResourcePackManager.pickReachableHost(
                List.of("203.0.113.9", "198.51.100.2")));
    }

    @Test
    void loopbackLinkLocalAndWildcardCandidatesAreIgnored() {
        assertNull(ResourcePackManager.pickReachableHost(
                List.of("127.0.0.1", "169.254.3.4", "0.0.0.0", "::1", "100.64.0.1")));
    }

    @Test
    void detectedHostProducesAUsableUri() {
        String detected = ResourcePackManager.pickReachableHost(ResourcePackManager.detectHostCandidates());
        Assumptions.assumeTrue(detected != null, "no client-reachable interface on this machine");
        assertEquals("http://" + detected + ":8123/shenanigans-resource-pack.zip",
                ResourcePackManager.resolveAdvertisedUri(detected, 8123, "0.0.0.0").toString());
    }
}
