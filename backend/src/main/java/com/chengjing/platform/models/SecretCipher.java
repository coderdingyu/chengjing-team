package com.chengjing.platform.models;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-GCM encryption; the master key lives outside Git and is backed up with the database. */
@Component
public class SecretCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${chengjing.secrets.key-file:./data/master.key}") String keyFile) {
        try {
            String configured = System.getenv("CHENGJING_MASTER_KEY");
            if (configured != null && !configured.isBlank()) {
                key = Base64.getDecoder().decode(configured);
            } else {
                Path file = Path.of(keyFile);
                Files.createDirectories(file.toAbsolutePath().getParent());
                if (Files.notExists(file)) {
                    byte[] generated = new byte[32];
                    random.nextBytes(generated);
                    try { Files.writeString(file, Base64.getEncoder().encodeToString(generated), StandardOpenOption.CREATE_NEW); }
                    catch (java.nio.file.FileAlreadyExistsException ignored) { }
                }
                key = Base64.getDecoder().decode(Files.readString(file).trim());
            }
            if (key.length != 32) throw new IllegalArgumentException("Master key must be 32 bytes");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load model secret master key; check CHENGJING_MASTER_KEY or key file", e);
        }
    }

    public String seal(String plain, String binding) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (Exception e) { throw new IllegalStateException("Cannot encrypt model key", e); }
    }

    public String open(String encoded, String binding) {
        try {
            if (!encoded.startsWith("v1:")) throw new IllegalArgumentException("Unsupported secret version");
            byte[] value = Base64.getDecoder().decode(encoded.substring(3));
            if (value.length < 29) throw new IllegalArgumentException("Invalid secret");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, value, 0, 12));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(value, 12, value.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Cannot decrypt model key; check master key backup", e); }
    }
}
