package com.yimark;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.security.KeyFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MainProviderTest {

    @Test
    @DisplayName("Ed25519 is available after provider bootstrap")
    void ed25519IsAvailable() {
        Main.ensureEd25519Provider();
        assertDoesNotThrow(() -> KeyFactory.getInstance("Ed25519"));
    }
}
