package edu.gcet.placementhub.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.bouncycastle.crypto.generators.SCrypt;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Password hashing and constant-time verification utility.
 * Supports:
 * 1. BCrypt ($2a$, $2b$, $2y$) - primary format matching Node bcryptjs cost 12
 * 2. scrypt$ (N=16384, r=8, p=1, dkLen=64) - preserves legacy Node scrypt accounts
 * 3. PBKDF2WithHmacSHA256 (65536 iterations, Base64 salt/hash)
 * 4. Legacy salted SHA-256 hex
 */
public final class PasswordHasher {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$.*");
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    private PasswordHasher() {
    }

    /**
     * Hashes a password with BCrypt ($2b$, cost 12) and returns [hash, saltPrefix].
     */
    public static String[] hashWithBCrypt(String plainPassword) {
        String hash = BCrypt.with(BCrypt.Version.VERSION_2B).hashToString(12, plainPassword.toCharArray());
        String saltPrefix = hash.length() >= 29 ? hash.substring(0, 29) : hash;
        return new String[]{hash, saltPrefix};
    }

    /**
     * Constant-time verification matching server/db.ts verifyPassword().
     */
    public static boolean verifyPassword(String plainPassword, String salt, String expectedHash) {
        if (plainPassword == null || plainPassword.isEmpty() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        String trimmedHash = expectedHash.trim();
        String safeSalt = salt == null ? "" : salt;

        // 1. BCrypt ($2a$, $2b$, $2y$)
        if (BCRYPT_PATTERN.matcher(trimmedHash).matches()) {
            try {
                BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), trimmedHash);
                return result.verified;
            } catch (Exception ignored) {
                return false;
            }
        }

        // 2. scrypt$ (supports Node crypto.scryptSync(password, salt, 64, {N:16384, r:8, p:1}) and embedded scrypt$salt$hash)
        if (trimmedHash.startsWith("scrypt$")) {
            try {
                String effectiveSalt = safeSalt;
                String targetHex = trimmedHash;
                String[] parts = trimmedHash.split("\\$");
                if (parts.length == 3 && !parts[1].isEmpty() && !parts[2].isEmpty()) {
                    effectiveSalt = parts[1];
                    targetHex = "scrypt$" + parts[2];
                }
                if (effectiveSalt.isEmpty()) {
                    return false;
                }
                byte[] derived = SCrypt.generate(
                        plainPassword.getBytes(StandardCharsets.UTF_8),
                        effectiveSalt.getBytes(StandardCharsets.UTF_8),
                        16384,
                        8,
                        1,
                        64
                );
                String computed = "scrypt$" + toHex(derived);
                return MessageDigest.isEqual(
                        computed.getBytes(StandardCharsets.UTF_8),
                        targetHex.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8)
                );
            } catch (Exception ignored) {
                return false;
            }
        }

        if (safeSalt.isEmpty()) {
            return false;
        }

        // 3. PBKDF2-HMAC-SHA256 (Base64 salt & hash, 65536 iterations, 256-bit)
        try {
            byte[] saltBytes = Base64.getDecoder().decode(safeSalt);
            PBEKeySpec spec = new PBEKeySpec(plainPassword.toCharArray(), saltBytes, 65536, 256);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] derived = skf.generateSecret(spec).getEncoded();
            String pbkdf2Base64 = Base64.getEncoder().encodeToString(derived);
            if (MessageDigest.isEqual(
                    pbkdf2Base64.getBytes(StandardCharsets.UTF_8),
                    trimmedHash.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        } catch (Exception ignored) {
        }

        // 4. Salted SHA-256 hex (password + salt)
        if (SHA256_HEX_PATTERN.matcher(trimmedHash).matches()) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest((plainPassword + safeSalt).getBytes(StandardCharsets.UTF_8));
                String hex = toHex(digest);
                return MessageDigest.isEqual(
                        hex.getBytes(StandardCharsets.UTF_8),
                        trimmedHash.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8)
                );
            } catch (Exception ignored) {
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
}
