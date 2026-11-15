package edu.gcet.placementhub.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.bouncycastle.crypto.generators.SCrypt;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Multi-format Salted Hashing & Constant-Time Verification
 * Primary: BCrypt ($2b$, cost 12) compatible with Express server/db.ts bcryptjs
 * Legacy Verification: scrypt$, PBKDF2WithHmacSHA256, salted SHA-256 hex
 */
public class PasswordUtil {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int BCRYPT_COST = 12;
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$.*");
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    public static String generateSalt() {
        String dummy = BCrypt.with(BCrypt.Version.VERSION_2B).hashToString(BCRYPT_COST, UUID_RANDOM_SEED());
        return dummy.length() >= 29 ? dummy.substring(0, 29) : dummy;
    }

    private static char[] UUID_RANDOM_SEED() {
        byte[] buf = new byte[16];
        new SecureRandom().nextBytes(buf);
        return Base64.getEncoder().encodeToString(buf).toCharArray();
    }

    public static String hashPassword(String password, String salt) {
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null");
        }
        return BCrypt.with(BCrypt.Version.VERSION_2B).hashToString(BCRYPT_COST, password.toCharArray());
    }

    public static boolean isBcryptHash(String hash) {
        return hash != null && BCRYPT_PATTERN.matcher(hash.trim()).matches();
    }

    public static boolean verifyPassword(String password, String salt, String expectedHash) {
        if (password == null || password.isEmpty() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        String trimmedHash = expectedHash.trim();
        String safeSalt = salt == null ? "" : salt;

        // 1. BCrypt ($2a$, $2b$, $2y$)
        if (BCRYPT_PATTERN.matcher(trimmedHash).matches()) {
            try {
                return BCrypt.verifyer().verify(password.toCharArray(), trimmedHash).verified;
            } catch (Exception ignored) {
                return false;
            }
        }

        // 2. scrypt$
        if (trimmedHash.startsWith("scrypt$")) {
            try {
                String effectiveSalt = safeSalt;
                String targetHex = trimmedHash;
                String[] parts = trimmedHash.split("\\$");
                if (parts.length == 3 && !parts[1].isEmpty() && !parts[2].isEmpty()) {
                    effectiveSalt = parts[1];
                    targetHex = "scrypt$" + parts[2];
                }
                if (effectiveSalt.isEmpty()) return false;
                byte[] derived = SCrypt.generate(
                        password.getBytes(StandardCharsets.UTF_8),
                        effectiveSalt.getBytes(StandardCharsets.UTF_8),
                        16384, 8, 1, 64
                );
                String computed = "scrypt$" + toHex(derived);
                return slowEquals(computed, targetHex.toLowerCase(Locale.ROOT));
            } catch (Exception ignored) {
                return false;
            }
        }

        if (safeSalt.isEmpty()) return false;

        // 3. Legacy PBKDF2WithHmacSHA256
        try {
            byte[] saltBytes = Base64.getDecoder().decode(safeSalt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            String pbkdf2 = Base64.getEncoder().encodeToString(hash);
            if (slowEquals(pbkdf2, trimmedHash)) {
                return true;
            }
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException ignored) {
        }

        // 4. Legacy salted SHA-256 hex
        if (SHA256_HEX_PATTERN.matcher(trimmedHash).matches()) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest((password + safeSalt).getBytes(StandardCharsets.UTF_8));
                return slowEquals(toHex(digest), trimmedHash.toLowerCase(Locale.ROOT));
            } catch (NoSuchAlgorithmException ignored) {
                return false;
            }
        }

        return false;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Constant-time comparison to prevent side-channel timing attacks
     */
    private static boolean slowEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
