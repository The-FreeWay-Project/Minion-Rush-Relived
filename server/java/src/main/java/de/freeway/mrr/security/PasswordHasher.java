package de.freeway.mrr.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Password hashing compatible with the Python MRR server:
 * {@code pbkdf2_sha256$<iterations>$<salt_hex>$<hash_hex>} using
 * PBKDF2-HMAC-SHA256 with a fresh 16-byte random salt per password.
 * Verification is constant-time; plaintext passwords are never stored.
 */
@Component
public class PasswordHasher {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher(@Value("${mrr.password-iterations:600000}") int iterations) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("mrr.password-iterations must be > 0");
        }
        this.iterations = iterations;
    }

    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = pbkdf2(rawPassword, salt, iterations);
        return PREFIX + "$" + iterations + "$" + Hex.encode(salt) + "$" + Hex.encode(digest);
    }

    public boolean verify(String storedHash, String rawPassword) {
        if (storedHash == null || rawPassword == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        int storedIterations;
        try {
            storedIterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        if (storedIterations <= 0) {
            return false;
        }
        try {
            byte[] salt = Hex.decode(parts[2]);
            byte[] expected = Hex.decode(parts[3]);
            byte[] candidate = pbkdf2(rawPassword, salt, storedIterations);
            return MessageDigest.isEqual(candidate, expected);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 unavailable", ex);
        }
    }
}
