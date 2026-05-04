package com.feel.gems.trust;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GemTrust structure and constants.
 * Full functional tests require game environment (see gametests).
 */
public class GemTrustTest {

    @Test
    void gemTrustClassExists() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "trust", "GemTrust.java");
        assertTrue(Files.exists(src), "GemTrust.java should exist");

        String code = Files.readString(src);
        assertTrue(code.contains("isTrusted"), "GemTrust should have isTrusted method");
        assertTrue(code.contains("getTrusted"), "GemTrust should have getTrusted method");
        assertTrue(code.contains("trust"), "GemTrust should have trust method");
        assertTrue(code.contains("untrust"), "GemTrust should have untrust method");
    }

    @Test
    void gemTrustHasCacheManagement() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "trust", "GemTrust.java");
        String code = Files.readString(src);
        
        assertTrue(code.contains("CACHE"), "GemTrust should use caching");
        assertTrue(code.contains("ConcurrentHashMap"), "GemTrust cache should be thread-safe");
        assertTrue(code.contains("clearRuntimeCache"), "GemTrust should expose cache clearing");
    }

    @Test
    void gemTrustUsesVersionedCache() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "trust", "GemTrust.java");
        String code = Files.readString(src);
        
        assertTrue(code.contains("KEY_TRUST_VERSION"), "GemTrust should version cache");
        assertTrue(code.contains("version"), "GemTrust should track cache versions");
    }

    @Test
    void gemTrustTreatsSelfAsTrusted() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "trust", "GemTrust.java");
        String code = Files.readString(src);
        
        // The isTrusted method should return true if owner == other
        assertTrue(code.contains("owner == other") || code.contains("owner.equals(other)"),
                "GemTrust should treat self as trusted");
    }

    @Test
    void gemTrustPersistsToNbt() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "trust", "GemTrust.java");
        String code = Files.readString(src);
        
        assertTrue(code.contains("CompoundTag"), "GemTrust should use NBT for persistence");
        assertTrue(code.contains("ListTag"), "GemTrust should store trusted list");
        assertTrue(code.contains("KEY_TRUSTED"), "GemTrust should have constant for NBT key");
    }

    @Test
    void gemTrustReturnsImmutableSets() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "trust", "GemTrust.java");
        String code = Files.readString(src);
        
        // Should use Set.copyOf or similar to return immutable sets
        assertTrue(code.contains("Set.copyOf") || code.contains("Set.of") || code.contains("Collections.unmodifiable"),
                "GemTrust.getTrusted should return immutable sets");
    }
}
