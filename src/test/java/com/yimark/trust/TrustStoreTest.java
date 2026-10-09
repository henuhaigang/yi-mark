package com.yimark.trust;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yimark.crypto.Ed25519Keys;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrustStoreTest {

    @TempDir
    Path tempDir;

    private static PublicKey generatedKey() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        return pair.getPublic();
    }

    private static String base64(PublicKey key) {
        return Ed25519Keys.publicOnly(key).publicKeyBase64();
    }

    @Test
    @DisplayName("an empty store trusts nothing but still reports a fingerprint")
    void emptyStoreTrustsNothing() throws Exception {
        TrustStore store = TrustStore.empty();
        TrustStore.Lookup lookup = store.lookup(base64(generatedKey()));
        assertFalse(lookup.trusted());
        assertFalse(lookup.fingerprint().isEmpty());
        assertNull(lookup.pinnedKeyBase64());
    }

    @Test
    @DisplayName("a pinned key resolves as trusted with its label and fingerprint")
    void pinnedKeyResolves() throws Exception {
        PublicKey key = generatedKey();
        TrustStore store = TrustStore.empty().trust(key, "Acme Bank", "2026-01-01");
        TrustStore.Lookup lookup = store.lookup(base64(key));
        assertTrue(lookup.trusted());
        assertEquals("Acme Bank", lookup.label());
        assertEquals(Ed25519Keys.fingerprint(key), lookup.fingerprint());
        assertEquals(base64(key), lookup.pinnedKeyBase64());
    }

    @Test
    @DisplayName("a key the attacker generated is not trusted just because the store is non-empty")
    void unknownKeyIsNotTrusted() throws Exception {
        TrustStore store = TrustStore.empty().trust(generatedKey(), "Acme Bank", "2026-01-01");
        TrustStore.Lookup lookup = store.lookup(base64(generatedKey()));
        assertFalse(lookup.trusted());
        assertNull(lookup.entry());
        assertNull(lookup.pinnedKeyBase64());
    }

    @Test
    @DisplayName("a blank manifest key is treated as no key rather than crashing")
    void blankKeyIsNoKey() {
        TrustStore.Lookup lookup = TrustStore.empty().lookup("   ");
        assertFalse(lookup.trusted());
        assertTrue(lookup.fingerprint().isEmpty());
    }

    @Test
    @DisplayName("entries survive a save and load round trip")
    void saveLoadRoundTrip() throws Exception {
        PublicKey key = generatedKey();
        Path file = tempDir.resolve("trusted-issuers.json");
        TrustStore.empty().trust(key, "Acme Bank", "2026-01-01").save(file);

        TrustStore loaded = TrustStore.load(file);
        assertFalse(loaded.isEmpty());
        assertEquals(1, loaded.size());
        assertEquals("Acme Bank", loaded.entries().get(0).label());
        assertTrue(loaded.lookup(base64(key)).trusted());
    }

    @Test
    @DisplayName("removing a fingerprint revokes trust")
    void removeRevokesTrust() throws Exception {
        PublicKey key = generatedKey();
        TrustStore store = TrustStore.empty().trust(key, "Acme Bank", "2026-01-01");
        assertTrue(store.remove(Ed25519Keys.fingerprint(key)));
        assertFalse(store.lookup(base64(key)).trusted());
    }

    @Test
    @DisplayName("loading a missing file yields an empty store instead of an error")
    void missingFileIsEmpty() throws Exception {
        assertTrue(TrustStore.load(tempDir.resolve("absent.json")).isEmpty());
    }
}
