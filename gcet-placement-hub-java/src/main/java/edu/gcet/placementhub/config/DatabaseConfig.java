package edu.gcet.placementhub.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Loads MySQL JDBC configuration exclusively from external configuration files
 * (.env / config.properties / environment variables) without hardcoded secrets.
 */
public final class DatabaseConfig {

    private String host;
    private int port;
    private String database;
    private String user;
    private String password;
    private String sourceDescription;
    private Map<String, String> envValues = new HashMap<>();

    private static DatabaseConfig instance;

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
        Map<String, String> loadedEnv = new HashMap<>();
        StringBuilder sources = new StringBuilder();

        // 1. Load fallback from parent or project .env regardless of launch working directory
        loadDotEnv(Paths.get("..", ".env"), loadedEnv, sources);
        loadDotEnv(Paths.get("gcet-placement-hub-java", ".env"), loadedEnv, sources);
        // 2. Load local .env inside working directory (higher priority)
        loadDotEnv(Paths.get(".env"), loadedEnv, sources);
        this.envValues = loadedEnv;

        // 3. Load config.properties if present (higher priority)
        Properties props = new Properties();
        loadPropertiesFile(Paths.get("gcet-placement-hub-java", "config.properties"), props, sources);
        loadPropertiesFile(Paths.get("config.properties"), props, sources);

        this.host = firstNonEmpty(
                System.getenv("DB_HOST"),
                props.getProperty("db.host"),
                envValues.get("DB_HOST"),
                "127.0.0.1"
        );

        String portStr = firstNonEmpty(
                System.getenv("DB_PORT"),
                props.getProperty("db.port"),
                envValues.get("DB_PORT"),
                "3306"
        );
        try {
            this.port = Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            this.port = 3306;
        }

        this.database = firstNonEmpty(
                System.getenv("DB_NAME"),
                props.getProperty("db.name"),
                envValues.get("DB_NAME"),
                "gcet_placement_hub"
        );

        this.user = firstNonEmpty(
                System.getenv("DB_USER"),
                props.getProperty("db.user"),
                envValues.get("DB_USER"),
                "root"
        );

        // Password may legitimately be empty for unconfigured local instances; never hardcoded
        String envPass = System.getenv("DB_PASSWORD");
        String propPass = props.getProperty("db.password");
        String dotEnvPass = envValues.get("DB_PASSWORD");
        if (envPass != null) {
            this.password = envPass;
        } else if (propPass != null) {
            this.password = propPass;
        } else if (dotEnvPass != null) {
            this.password = dotEnvPass;
        } else {
            this.password = "";
        }

        this.sourceDescription = sources.length() > 0
                ? sources.toString()
                : "No .env or config.properties found (configure MySQL connection settings)";
    }

    private void loadPropertiesFile(Path propPath, Properties props, StringBuilder sources) {
        if (!Files.isRegularFile(propPath)) return;
        try (InputStream in = Files.newInputStream(propPath)) {
            props.load(in);
            appendSource(sources, propPath.toAbsolutePath().normalize().toString());
        } catch (IOException ignored) {
        }
    }

    private void loadDotEnv(Path path, Map<String, String> target, StringBuilder sources) {
        if (!Files.isRegularFile(path)) return;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                    if (val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                }
                target.put(key, val);
            }
            appendSource(sources, path.toAbsolutePath().normalize().toString());
        } catch (IOException ignored) {
        }
    }

    private void appendSource(StringBuilder sb, String path) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(path);
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public String getEnvValue(String key) {
        String sys = System.getenv(key);
        if (sys != null && !sys.trim().isEmpty()) {
            return sys.trim();
        }
        String dotVal = envValues.get(key);
        return (dotVal != null && !dotVal.trim().isEmpty()) ? dotVal.trim() : null;
    }

    public String getJdbcUrl() {
        return String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&connectTimeout=4000&socketTimeout=15000",
                host, port, database
        );
    }

    public synchronized void updateRuntimeCredentials(String host, int port, String database, String user, String password) {
        if (host != null && !host.isBlank()) this.host = host.trim();
        if (port > 0) this.port = port;
        if (database != null && !database.isBlank()) this.database = database.trim();
        if (user != null && !user.isBlank()) this.user = user.trim();
        this.password = password != null ? password : "";
    }

    /**
     * Explicitly saves current runtime MySQL JDBC configuration into local .env when requested by the user.
     */
    public synchronized Path saveToLocalDotEnv() throws IOException {
        Path targetDir = Files.isDirectory(Paths.get("gcet-placement-hub-java"))
                ? Paths.get("gcet-placement-hub-java")
                : Paths.get(".");
        Path targetEnv = targetDir.resolve(".env").toAbsolutePath().normalize();
        String safePass = this.password == null ? "" : this.password.replace("\"", "\\\"");
        String content = "# GCET Placement Hub (Java 17 Swing) - MySQL Configuration\n"
                + "DB_HOST=" + this.host + "\n"
                + "DB_PORT=" + this.port + "\n"
                + "DB_NAME=" + this.database + "\n"
                + "DB_USER=" + this.user + "\n"
                + "DB_PASSWORD=\"" + safePass + "\"\n";
        Files.writeString(targetEnv, content, StandardCharsets.UTF_8);
        this.sourceDescription = targetEnv.toString();
        return targetEnv;
    }
}
