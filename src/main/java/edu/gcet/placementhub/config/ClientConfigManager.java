package edu.gcet.placementhub.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB - Client Configuration Manager
 * Handles local persistence of user preferences, last portal selection,
 * and obfuscated credentials in client_config.properties to prevent cleartext exposure.
 */
public class ClientConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ClientConfigManager.class.getName());
    private static final String CONFIG_FILE = "client_config.properties";
    private static final byte[] MASK_KEY = "GCET_PLACEMENT_HUB_KEY_2026".getBytes(StandardCharsets.UTF_8);

    private static final Properties props = new Properties();
    private static boolean loaded = false;

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not load " + CONFIG_FILE, e);
            }
        }
        loaded = true;
    }

    public static synchronized void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "GCET Placement Hub - Desktop Client Local Configuration");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save " + CONFIG_FILE, e);
        }
    }

    public static String getProperty(String key, String defaultVal) {
        ensureLoaded();
        return props.getProperty(key, defaultVal);
    }

    public static void setProperty(String key, String value) {
        ensureLoaded();
        if (value == null) {
            props.remove(key);
        } else {
            props.setProperty(key, value);
        }
        saveConfig();
    }

    public static String getLastPortalTab() {
        return getProperty("auth.last_portal_tab", "ADMIN");
    }

    public static void setLastPortalTab(String tab) {
        setProperty("auth.last_portal_tab", tab);
    }

    public static String getLastAdminUsername() {
        return getProperty("auth.last_admin_username", "principal.sagar");
    }

    public static void setLastAdminUsername(String username) {
        setProperty("auth.last_admin_username", username);
    }

    public static String getLastFacultyUsername() {
        return getProperty("auth.last_faculty_username", "faculty_lavanya");
    }

    public static void setLastFacultyUsername(String username) {
        setProperty("auth.last_faculty_username", username);
    }

    public static String getLastStudentRoll() {
        return getProperty("auth.last_student_roll", "22R11A0501");
    }

    public static void setLastStudentRoll(String roll) {
        setProperty("auth.last_student_roll", roll);
    }

    public static String getLastCompanyUsername() {
        return getProperty("auth.last_company_username", "recruiter_tcs");
    }

    public static void setLastCompanyUsername(String username) {
        setProperty("auth.last_company_username", username);
    }

    public static boolean isRememberMeEnabled() {
        return "true".equalsIgnoreCase(getProperty("auth.remember_me", "true"));
    }

    public static void setRememberMeEnabled(boolean enabled) {
        setProperty("auth.remember_me", String.valueOf(enabled));
    }

    /**
     * Stores obfuscated credential token to prevent cleartext exposure on disk.
     */
    public static void saveObfuscatedCredential(String username, String plaintext) {
        if (!isRememberMeEnabled() || plaintext == null || plaintext.isEmpty()) {
            setProperty("cred." + username, null);
            return;
        }
        try {
            byte[] raw = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] obfuscated = new byte[raw.length];
            for (int i = 0; i < raw.length; i++) {
                obfuscated[i] = (byte) (raw[i] ^ MASK_KEY[i % MASK_KEY.length]);
            }
            String b64 = Base64.getEncoder().encodeToString(obfuscated);
            setProperty("cred." + username, b64);
        } catch (Exception e) {
            LOGGER.warning("Could not persist credential: " + e.getMessage());
        }
    }

    /**
     * Retrieves obfuscated credential token for convenience auto-fill.
     */
    public static String getObfuscatedCredential(String username) {
        if (!isRememberMeEnabled()) return null;
        String b64 = getProperty("cred." + username, null);
        if (b64 == null) return null;
        try {
            byte[] obfuscated = Base64.getDecoder().decode(b64);
            byte[] raw = new byte[obfuscated.length];
            for (int i = 0; i < obfuscated.length; i++) {
                raw[i] = (byte) (obfuscated[i] ^ MASK_KEY[i % MASK_KEY.length]);
            }
            return new String(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearSavedCredentials() {
        ensureLoaded();
        props.entrySet().removeIf(entry -> entry.getKey().toString().startsWith("cred."));
        saveConfig();
    }
}
