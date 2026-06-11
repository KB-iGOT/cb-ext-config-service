package com.igot.cb.authentication.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.igot.cb.authentication.model.KeyData;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Mahesh RV
 */
@Component
@Slf4j
public class KeyManager {
  private final PropertiesCache propertiesCache;
  private final Map<String, KeyData> keyMap = new HashMap<>();

  public KeyManager(PropertiesCache propertiesCache) {
    this.propertiesCache = propertiesCache;
  }

  @PostConstruct
  public void init() {
    // Read the content of the file and load it as a PublicKey
    String basePath = propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH);
    try (Stream<Path> walk = Files.walk(Paths.get(basePath))) {
      List<String> result =
              walk.filter(Files::isRegularFile).map(Path::toString).collect(Collectors.toList());
      result.forEach(file -> {
        try {
          Path path = Paths.get(file);
          List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
          String content = String.join("", lines);
          KeyData keyData = new KeyData(path.getFileName().toString(), loadPublicKey(content));
          // Store the KeyData object in the keyMap
          keyMap.put(path.getFileName().toString(), keyData);
        } catch (Exception e) {
          log.error("KeyManager:init: exception in reading public keys ", e);
        }
      });
    } catch (Exception e) {
      log.error("KeyManager:init: exception in loading publickeys ", e);
    }
  }

  public KeyData getPublicKey(String keyId) {
    return keyMap.get(keyId);
  }


  /**
   * Loads a public key from a string representation.
   *
   * @param key The string representation of the public key
   * @return The loaded public key
   * @throws Exception If there's an error during the loading process
   */
  public static PublicKey loadPublicKey(String key) throws Exception {
    // Remove header and footer from the key string
    String cleanedKey = key.replaceAll("(-+BEGIN PUBLIC KEY-+)", "")
            .replaceAll("(-+END PUBLIC KEY-+)", "")
            .replaceAll("[\\r\\n]+", "");
    // Decode Base64 content
    byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
    // Generate PublicKey object
    X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePublic(spec);
  }
}