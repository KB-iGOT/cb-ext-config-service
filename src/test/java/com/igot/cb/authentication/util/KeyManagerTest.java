package com.igot.cb.authentication.util;

import com.igot.cb.authentication.model.KeyData;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeyManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void init_shouldLoadPublicKeyFilesIntoKeyMap() throws Exception {
        PropertiesCache propertiesCache = mock(PropertiesCache.class);
        when(propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH))
                .thenReturn(tempDir.toString());

        KeyPair keyPair = generateRsaKeyPair();
        String publicKeyPem = toPem(keyPair.getPublic());

        Path keyFile = tempDir.resolve("test-key");
        Files.writeString(keyFile, publicKeyPem);

        KeyManager keyManager = new KeyManager(propertiesCache);

        keyManager.init();

        KeyData result = keyManager.getPublicKey("test-key");

        assertNotNull(result);
        assertEquals("test-key", result.getKeyId());
        assertNotNull(result.getPublicKey());
        assertEquals(keyPair.getPublic(), result.getPublicKey());
    }

    @Test
    void init_shouldHandleInvalidBasePathWithoutException() {
        PropertiesCache propertiesCache = mock(PropertiesCache.class);
        when(propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH))
                .thenReturn("/invalid/path/not-exist");

        KeyManager keyManager = new KeyManager(propertiesCache);

        assertDoesNotThrow(keyManager::init);
        assertNull(keyManager.getPublicKey("test-key"));
    }

    @Test
    void init_shouldIgnoreInvalidKeyFile() throws Exception {
        PropertiesCache propertiesCache = mock(PropertiesCache.class);
        when(propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH))
                .thenReturn(tempDir.toString());

        Files.writeString(tempDir.resolve("invalid-key"), "invalid-public-key-content");

        KeyManager keyManager = new KeyManager(propertiesCache);

        assertDoesNotThrow(keyManager::init);
        assertNull(keyManager.getPublicKey("invalid-key"));
    }

    @Test
    void getPublicKey_shouldReturnNullForUnknownKey() {
        PropertiesCache propertiesCache = mock(PropertiesCache.class);
        KeyManager keyManager = new KeyManager(propertiesCache);

        KeyData result = keyManager.getPublicKey("unknown-key");

        assertNull(result);
    }

    @Test
    void loadPublicKey_shouldLoadValidPublicKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String publicKeyPem = toPem(keyPair.getPublic());

        PublicKey result = KeyManager.loadPublicKey(publicKeyPem);

        assertNotNull(result);
        assertEquals(keyPair.getPublic(), result);
    }

    @Test
    void loadPublicKey_shouldThrowExceptionForInvalidKey() {
        assertThrows(Exception.class, () -> KeyManager.loadPublicKey("invalid-key"));
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String toPem(PublicKey publicKey) {
        String encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        return "-----BEGIN PUBLIC KEY-----\n"
                + encoded
                + "\n-----END PUBLIC KEY-----";
    }
}
