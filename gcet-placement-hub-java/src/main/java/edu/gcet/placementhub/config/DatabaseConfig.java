package edu.gcet.placementhub.config;

import com.mongodb.ConnectionString;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Cloud-first MongoDB configuration loaded exclusively from external sources, never hardcoded:
 *
 * <pre>
 *   Priority (highest wins):
 *     1. Runtime overrides entered in the in-app Connection Settings dialog
 *     2. Real OS environment variables      (MONGO_URI, MONGO_DB_NAME, ...)
 *     3. config.properties                  (mongo.uri, mongo.db.name)
 *     4. .env (dotenv-java)                 searched in ./, ./gcet-placement-hub-java/, ../
 *     5. Defaults                           mongodb://localhost:27017 / gcet_placement_hub
 * </pre>
 *
 * Works identically on Windows and macOS because every path is built with {@link Paths#get}.
 * {@code MONGO_URI} normally points at a shared MongoDB Atlas cluster so a teammate can clone the
 * repository and immediately see the seeded demo data without installing MongoDB locally.
 * When it is unset (or the Atlas cluster is unreachable and {@code MONGO_LOCAL_FALLBACK=true})
 * the app falls back to a local MongoDB Community server.
 */
public final class DatabaseConfig {

    public static final String DEFAULT_LOCAL_URI = "mongodb://localhost:27017";
    public static final String DEFAULT_DB_NAME = "gcet_placement_hub";

    public static final String KEY_URI = "MONGO_URI";
    public static final String KEY_DB_NAME = "MONGO_DB_NAME";
    public static final String KEY_LOCAL_FALLBACK = "MONGO_LOCAL_FALLBACK";
    public static final String KEY_DEMO_PASSWORD = "GCET_DEMO_PASSWORD";

    private static DatabaseConfig instance;

    private String mongoUri;
    private String databaseName;
    private boolean localFallbackEnabled;
    private String sourceDescription;
    private boolean runtimeOverride;
    private Map<String, String> envValues = new LinkedHashMap<>();
    private final List<Path> loadedFiles = new ArrayList<>();

    private DatabaseConfig() {
        reload();
    }

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    public synchronized void reload() {
        Map<String, String> loaded = new LinkedHashMap<>();
        loadedFiles.clear();

        // 1. .env files: furthest first so the closest one wins
        loadDotEnv(Paths.get("..", ".env"), loaded);
        loadDotEnv(Paths.get("gcet-placement-hub-java", ".env"), loaded);
        loadDotEnv(Paths.get(".env"), loaded);
        this.envValues = loaded;

        // 2. config.properties (optional, higher priority than .env)
        Properties props = new Properties();
        loadPropertiesFile(Paths.get("gcet-placement-hub-java", "config.properties"), props);
        loadPropertiesFile(Paths.get("config.properties"), props);

        this.mongoUri = firstNonEmpty(
                System.getenv(KEY_URI),
                props.getProperty("mongo.uri"),
                loaded.get(KEY_URI),
                DEFAULT_LOCAL_URI
        );
        this.databaseName = firstNonEmpty(
                System.getenv(KEY_DB_NAME),
                props.getProperty("mongo.db.name"),
                loaded.get(KEY_DB_NAME),
                DEFAULT_DB_NAME
        );
        String fallbackRaw = firstNonEmpty(
                System.getenv(KEY_LOCAL_FALLBACK),
                props.getProperty("mongo.local.fallback"),
                loaded.get(KEY_LOCAL_FALLBACK),
                "true"
        );
        this.localFallbackEnabled = !"false".equalsIgnoreCase(fallbackRaw.trim());
        this.runtimeOverride = false;

        if (loadedFiles.isEmpty()) {
            this.sourceDescription = System.getenv(KEY_URI) != null
                    ? "OS environment variable " + KEY_URI
                    : "No .env / config.properties found - using built-in local default (" + DEFAULT_LOCAL_URI + ")";
        } else {
            StringBuilder sb = new StringBuilder();
            for (Path p : loadedFiles) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p.toAbsolutePath().normalize());
            }
            this.sourceDescription = sb.toString();
        }
    }

    // ------------------------------------------------------------------ loading

    private void loadDotEnv(Path path, Map<String, String> target) {
        if (!Files.isRegularFile(path)) return;
        Path dir = path.toAbsolutePath().normalize().getParent();
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory(dir.toString())
                    .filename(path.getFileName().toString())
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
            for (DotenvEntry entry : dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE)) {
                target.put(entry.getKey(), entry.getValue());
            }
            loadedFiles.add(path);
        } catch (Exception ex) {
            // Fall back to a tolerant manual parser so a single odd line never blocks startup
            manualParseDotEnv(path, target);
        }
    }

    private void manualParseDotEnv(Path path, Map<String, String> target) {
        try {
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                    if (val.length() >= 2) val = val.substring(1, val.length() - 1);
                }
                target.put(key, val);
            }
            loadedFiles.add(path);
        } catch (IOException ignored) {
        }
    }

    private void loadPropertiesFile(Path propPath, Properties props) {
        if (!Files.isRegularFile(propPath)) return;
        try (InputStream in = Files.newInputStream(propPath)) {
            props.load(in);
            loadedFiles.add(propPath);
        } catch (IOException ignored) {
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    // ----------------------------------------------------------------- accessors

    public synchronized String getMongoUri() {
        return mongoUri;
    }

    public synchronized String getDatabase() {
        return databaseName;
    }

    public synchronized String getDatabaseName() {
        return databaseName;
    }

    public synchronized boolean isLocalFallbackEnabled() {
        return localFallbackEnabled;
    }

    public synchronized boolean isRuntimeOverride() {
        return runtimeOverride;
    }

    public synchronized String getSourceDescription() {
        return runtimeOverride ? "Runtime override (Connection Settings dialog)" : sourceDescription;
    }

    public String getLocalFallbackUri() {
        return DEFAULT_LOCAL_URI;
    }

    /** True when the configured URI targets a MongoDB Atlas cluster (SRV record or mongodb.net host). */
    public synchronized boolean isAtlasUri() {
        return isAtlasUri(mongoUri);
    }

    public static boolean isAtlasUri(String uri) {
        if (uri == null) return false;
        String u = uri.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("mongodb+srv://") || u.contains("mongodb.net");
    }

    public static boolean isLocalUri(String uri) {
        if (uri == null || uri.isBlank()) return true;
        String u = uri.trim().toLowerCase(Locale.ROOT);
        return u.contains("localhost") || u.contains("127.0.0.1") || u.contains("0.0.0.0");
    }

    public synchronized String getClusterType() {
        return describeClusterType(mongoUri);
    }

    public static String describeClusterType(String uri) {
        if (isAtlasUri(uri)) return "MongoDB Atlas (Cloud)";
        if (isLocalUri(uri)) return "Local MongoDB Community";
        return "Self-hosted MongoDB";
    }

    /** First host of the connection string (SRV hostname for Atlas). */
    public synchronized String getHost() {
        ConnectionString cs = parse(mongoUri);
        if (cs != null && cs.getHosts() != null && !cs.getHosts().isEmpty()) {
            String h = cs.getHosts().get(0);
            int colon = h.lastIndexOf(':');
            return colon > 0 ? h.substring(0, colon) : h;
        }
        return "localhost";
    }

    /** Port of the first host; -1 when resolved through DNS SRV (Atlas). */
    public synchronized int getPort() {
        ConnectionString cs = parse(mongoUri);
        if (cs == null) return 27017;
        if (cs.isSrvProtocol()) return -1;
        if (cs.getHosts() != null && !cs.getHosts().isEmpty()) {
            String h = cs.getHosts().get(0);
            int colon = h.lastIndexOf(':');
            if (colon > 0) {
                try {
                    return Integer.parseInt(h.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 27017;
    }

    /** Host:port label used by the UI ("cluster0.abcde.mongodb.net (SRV)" or "localhost:27017"). */
    public synchronized String getHostLabel() {
        int port = getPort();
        return port < 0 ? getHost() + " (SRV)" : getHost() + ":" + port;
    }

    /** Database user embedded in the URI, or "(no auth)" for a local server. */
    public synchronized String getUser() {
        ConnectionString cs = parse(mongoUri);
        String u = cs != null ? cs.getUsername() : null;
        return (u == null || u.isBlank()) ? "(no auth)" : u;
    }

    /** Password embedded in the URI (never displayed; used only for owner verification flows). */
    public synchronized String getPassword() {
        ConnectionString cs = parse(mongoUri);
        char[] pw = cs != null ? cs.getPassword() : null;
        return pw == null ? "" : new String(pw);
    }

    /** URI with the password replaced by ***** so it can be logged or shown in diagnostics. */
    public synchronized String getMaskedUri() {
        return maskUri(mongoUri);
    }

    public static String maskUri(String uri) {
        if (uri == null) return "";
        return uri.replaceAll("(?i)(mongodb(?:\\+srv)?://[^:/@]+:)([^@]*)(@)", "$1*****$3");
    }

    private static ConnectionString parse(String uri) {
        try {
            return new ConnectionString(uri);
        } catch (Exception ex) {
            return null;
        }
    }

    public synchronized String getEnvValue(String key) {
        String sys = System.getenv(key);
        if (sys != null && !sys.trim().isEmpty()) return sys.trim();
        String dotVal = envValues.get(key);
        return (dotVal != null && !dotVal.trim().isEmpty()) ? dotVal.trim() : null;
    }

    /** Demo / bootstrap password used by the idempotent seeder when a role-specific one is absent. */
    public synchronized String getBootstrapPassword(String roleKey) {
        String specific = getEnvValue(roleKey);
        if (specific != null && !specific.startsWith("set_")) return specific;
        String demo = getEnvValue(KEY_DEMO_PASSWORD);
        return (demo != null && !demo.isBlank()) ? demo : "Gcet@2025";
    }

    // ----------------------------------------------------------- runtime updates

    public synchronized void updateRuntimeConnection(String uri, String dbName) {
        if (uri != null && !uri.isBlank()) this.mongoUri = uri.trim();
        if (dbName != null && !dbName.isBlank()) this.databaseName = dbName.trim();
        this.runtimeOverride = true;
    }

    /**
     * Explicitly saves the current MongoDB settings into the project's local .env when the user asks.
     * Other keys already present in the file (bootstrap passwords etc.) are preserved.
     */
    public synchronized Path saveToLocalDotEnv() throws IOException {
        Path targetDir = Files.isDirectory(Paths.get("gcet-placement-hub-java"))
                ? Paths.get("gcet-placement-hub-java")
                : Paths.get(".");
        Path targetEnv = targetDir.resolve(".env").toAbsolutePath().normalize();

        Map<String, String> merged = new LinkedHashMap<>();
        merged.put(KEY_URI, this.mongoUri);
        merged.put(KEY_DB_NAME, this.databaseName);
        merged.put(KEY_LOCAL_FALLBACK, String.valueOf(this.localFallbackEnabled));
        for (Map.Entry<String, String> e : envValues.entrySet()) {
            merged.putIfAbsent(e.getKey(), e.getValue());
        }

        StringBuilder content = new StringBuilder();
        content.append("# GCET Placement Hub (Java 17 Swing) - MongoDB Configuration\n");
        content.append("# Saved from the in-app Connection Settings dialog. Never commit this file.\n");
        for (Map.Entry<String, String> e : merged.entrySet()) {
            String val = e.getValue() == null ? "" : e.getValue().replace("\"", "\\\"");
            content.append(e.getKey()).append("=\"").append(val).append("\"\n");
        }
        Files.writeString(targetEnv, content.toString(), StandardCharsets.UTF_8);
        this.sourceDescription = targetEnv.toString();
        this.runtimeOverride = false;
        return targetEnv;
    }
}
