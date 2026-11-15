package edu.gcet.placementhub.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.GovernanceModels.CollectionDiagnostic;
import edu.gcet.placementhub.models.GovernanceModels.ExtendedDbStatus;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Single shared {@link MongoClient} for the desktop app (the driver already pools connections).
 *
 * <ul>
 *   <li>Cloud-first: connects to {@code MONGO_URI} (normally a MongoDB Atlas cluster).</li>
 *   <li>Local fallback: when Atlas is unreachable and {@code MONGO_LOCAL_FALLBACK} is not "false",
 *       transparently retries {@code mongodb://localhost:27017}.</li>
 *   <li>On the first successful connection it applies {@code $jsonSchema} validators + indexes
 *       ({@link MongoSchemaInitializer}) and seeds demo data once ({@link MongoSeedData}).</li>
 *   <li>Never drops collections or databases.</li>
 * </ul>
 */
public final class DatabaseConnection {

    /** Collections the application expects (replaces the former 17 relational tables). */
    public static final List<String> REQUIRED_COLLECTIONS = List.of(
            "users",
            "students",
            "faculty",
            "companies",
            "skills",
            "placement_drives",
            "applications",
            "approvals",
            "admin_requests",
            "announcements",
            "audit_logs"
    );

    private static final Map<String, String> MODEL_NOTES = Map.ofEntries(
            Map.entry("users", "Referenced: authentication + RBAC identity; linked from students/faculty via ObjectId"),
            Map.entry("students", "Embedded: skills[], projects[], certifications[], internships[], academicMetrics{}"),
            Map.entry("faculty", "Referenced: userId -> users._id; authorizedDepartments[] embedded"),
            Map.entry("companies", "Referenced: independent lifecycle; hrContact{} embedded; text index"),
            Map.entry("skills", "Referenced catalog: skillId copied + skillName denormalized into students/drives"),
            Map.entry("placement_drives", "Embedded: eligibilityCriteria{}, requiredSkills[]; denormalized companyName/companyTier"),
            Map.entry("applications", "Embedded: immutable stageHistory[] trail; references studentId/driveId/companyId"),
            Map.entry("approvals", "Referenced: immutable decision records (approverId -> users._id)"),
            Map.entry("admin_requests", "Referenced: Principal-only privilege petitions (userId -> users._id)"),
            Map.entry("announcements", "Referenced: GCTC circulars shown on Campus Connect / Dashboard"),
            Map.entry("audit_logs", "Referenced: append-only trail (actorId -> users._id)")
    );

    private static final int SERVER_SELECTION_TIMEOUT_SECONDS = 8;

    private static DatabaseConnection instance;

    private MongoClient client;
    private String activeUri;
    private boolean usingLocalFallback;
    private boolean initialized;

    private volatile boolean connected = false;
    private volatile String lastError = null;
    private volatile String lastRemediationHint = null;
    private volatile String lastSuccessfulPingIso = null;
    private volatile String serverVersion = null;

    private DatabaseConnection() {
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    // ------------------------------------------------------------------ connect

    /**
     * Returns the application database, connecting (and initializing schema + seed data) on first use.
     * Equivalent of the old {@code getConnection()} - every DAO call receives this handle.
     */
    public synchronized MongoDatabase getDatabase() throws DatabaseException {
        DatabaseConfig cfg = DatabaseConfig.getInstance();
        if (client == null) {
            connectWithFallback(cfg);
        }
        MongoDatabase db = client.getDatabase(cfg.getDatabaseName());
        if (!initialized) {
            initialized = true; // guard against re-entrancy even if initialization throws
            try {
                MongoSchemaInitializer.apply(db);
                MongoSeedData.seedIfEmpty(db);
            } catch (MongoException ex) {
                System.err.println("[MongoDB] Schema/seed initialization warning: " + ex.getMessage());
            }
        }
        return db;
    }

    public MongoCollection<Document> getCollection(String name) throws DatabaseException {
        return getDatabase().getCollection(name);
    }

    private void connectWithFallback(DatabaseConfig cfg) throws DatabaseException {
        String primaryUri = cfg.getMongoUri();
        try {
            openAndPing(primaryUri, cfg.getDatabaseName());
            usingLocalFallback = false;
            return;
        } catch (DatabaseException primaryFailure) {
            boolean canFallback = cfg.isLocalFallbackEnabled() && !DatabaseConfig.isLocalUri(primaryUri);
            if (!canFallback) {
                throw primaryFailure;
            }
            System.err.println("[MongoDB] " + DatabaseConfig.describeClusterType(primaryUri)
                    + " unreachable (" + primaryFailure.getMessage() + ") - trying local fallback " + cfg.getLocalFallbackUri());
            try {
                openAndPing(cfg.getLocalFallbackUri(), cfg.getDatabaseName());
                usingLocalFallback = true;
            } catch (DatabaseException fallbackFailure) {
                // Report the primary (Atlas) failure: that is the one the teammate must fix
                this.lastError = primaryFailure.getMessage();
                this.lastRemediationHint = primaryFailure.getRemediationHint()
                        + "\nLocal fallback also failed: " + fallbackFailure.getMessage();
                throw new DatabaseException(primaryFailure.getMessage(), this.lastRemediationHint, primaryFailure);
            }
        }
    }

    private void openAndPing(String uri, String dbName) throws DatabaseException {
        MongoClient candidate = null;
        try {
            candidate = buildClient(uri);
            Document pong = candidate.getDatabase(dbName).runCommand(new Document("ping", 1));
            if (pong == null || BsonUtil.dbl(pong, "ok", 0) != 1.0) {
                throw new DatabaseException("MongoDB ping returned a non-OK response.");
            }
            closeQuietly(this.client);
            this.client = candidate;
            this.activeUri = uri;
            this.connected = true;
            this.lastError = null;
            this.lastRemediationHint = null;
            this.lastSuccessfulPingIso = Instant.now().toString();
            this.serverVersion = fetchServerVersion(candidate.getDatabase(dbName));
        } catch (DatabaseException ex) {
            closeQuietly(candidate);
            throw ex;
        } catch (Exception ex) {
            closeQuietly(candidate);
            this.connected = false;
            String message = describeFailure(uri, ex);
            String hint = remediationFor(uri, ex);
            this.lastError = message;
            this.lastRemediationHint = hint;
            throw new DatabaseException(message, hint, ex);
        }
    }

    private MongoClient buildClient(String uri) {
        ConnectionString cs = new ConnectionString(uri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(cs)
                .applicationName("GCET Placement Hub (Java 17 Swing)")
                .applyToClusterSettings(b -> b.serverSelectionTimeout(SERVER_SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .applyToSocketSettings(b -> b.connectTimeout(SERVER_SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS))
                .retryWrites(true)
                .retryReads(true)
                .build();
        return MongoClients.create(settings);
    }

    private String fetchServerVersion(MongoDatabase db) {
        try {
            Document info = db.runCommand(new Document("buildInfo", 1));
            return BsonUtil.str(info, "version", "unknown");
        } catch (Exception ex) {
            return "unknown";
        }
    }

    /** Drops the cached client so the next call reconnects with the (possibly updated) configuration. */
    public synchronized void reconnect() {
        closeQuietly(client);
        client = null;
        activeUri = null;
        initialized = false;
        connected = false;
    }

    public synchronized void close() {
        closeQuietly(client);
        client = null;
        connected = false;
    }

    private static void closeQuietly(MongoClient c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    // --------------------------------------------------------------- diagnostics

    /** Lightweight health check: runs {@code {ping: 1}} and counts documents in each collection. */
    public DbStatus pingAndInspect() {
        DatabaseConfig cfg = DatabaseConfig.getInstance();
        DbStatus status = baseStatus(cfg);
        try {
            MongoDatabase db = getDatabase();
            long start = System.nanoTime();
            db.runCommand(new Document("ping", 1));
            long latency = Math.max(1L, (System.nanoTime() - start) / 1_000_000L);
            fillConnected(status, cfg, latency);
            status.setTableCounts(fetchCollectionCounts(db));
        } catch (DatabaseException | MongoException ex) {
            fillDisconnected(status, ex);
        }
        return status;
    }

    /**
     * Read-only full diagnostic snapshot: server version, latency, dbStats, every required
     * collection with live counts, index names and whether a $jsonSchema validator is active.
     */
    public ExtendedDbStatus inspectExtendedDiagnostics() {
        DatabaseConfig cfg = DatabaseConfig.getInstance();
        DbStatus status = baseStatus(cfg);
        List<CollectionDiagnostic> diagnostics = new ArrayList<>();
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        Map<String, Object> dbStats = new LinkedHashMap<>();
        String version = "Unavailable";
        long latencyMs = -1;
        int available = 0;

        try {
            MongoDatabase db = getDatabase();
            long start = System.nanoTime();
            db.runCommand(new Document("ping", 1));
            latencyMs = Math.max(1L, (System.nanoTime() - start) / 1_000_000L);
            fillConnected(status, cfg, latencyMs);
            version = serverVersion != null ? serverVersion : fetchServerVersion(db);

            List<String> existing = new ArrayList<>();
            db.listCollectionNames().into(existing);
            Map<String, Document> validators = MongoSchemaInitializer.currentValidators(db);

            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String name : REQUIRED_COLLECTIONS) {
                boolean exists = existing.contains(name);
                if (!exists) {
                    diagnostics.add(new CollectionDiagnostic(name, false, 0, 0, false,
                            MODEL_NOTES.getOrDefault(name, ""), "Collection not created yet"));
                    continue;
                }
                available++;
                long count = db.getCollection(name).countDocuments();
                List<String> idx = new ArrayList<>();
                for (Document d : db.getCollection(name).listIndexes()) {
                    idx.add(describeIndex(d));
                }
                indexes.put(name, idx);
                counts.put(name, (int) count);
                boolean validated = validators.containsKey(name);
                diagnostics.add(new CollectionDiagnostic(name, true, count, idx.size(), validated,
                        MODEL_NOTES.getOrDefault(name, ""),
                        count == 0 ? "Available (0 documents)" : "Available & Verified"));
            }
            status.setTableCounts(counts);

            try {
                Document stats = db.runCommand(new Document("dbStats", 1).append("scale", 1024));
                for (String key : List.of("db", "collections", "objects", "dataSize", "storageSize", "indexes", "indexSize", "totalSize")) {
                    if (stats.containsKey(key)) dbStats.put(key, stats.get(key));
                }
            } catch (MongoException ignored) {
            }
        } catch (DatabaseException | MongoException ex) {
            fillDisconnected(status, ex);
            for (String name : REQUIRED_COLLECTIONS) {
                diagnostics.add(new CollectionDiagnostic(name, false, 0, 0, false,
                        MODEL_NOTES.getOrDefault(name, ""), "Database unreachable"));
            }
        }

        return new ExtendedDbStatus(status, version, latencyMs, diagnostics, available,
                REQUIRED_COLLECTIONS.size(), status.getClusterType(), dbStats, indexes);
    }

    public static String modelNote(String collection) {
        return MODEL_NOTES.getOrDefault(collection, "");
    }

    private static String describeIndex(Document idx) {
        StringBuilder sb = new StringBuilder(BsonUtil.str(idx, "name", "?"));
        Document key = BsonUtil.sub(idx, "key");
        if (!key.isEmpty()) sb.append(" ").append(key.toJson());
        if (BsonUtil.bool(idx, "unique", false)) sb.append(" [unique]");
        if (idx.containsKey("weights")) sb.append(" [text]");
        if (idx.containsKey("collation")) sb.append(" [ci]");
        if (BsonUtil.bool(idx, "sparse", false)) sb.append(" [sparse]");
        return sb.toString();
    }

    private Map<String, Integer> fetchCollectionCounts(MongoDatabase db) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : REQUIRED_COLLECTIONS) {
            try {
                counts.put(name, (int) db.getCollection(name).countDocuments());
            } catch (MongoException ignored) {
            }
        }
        return counts;
    }

    private DbStatus baseStatus(DatabaseConfig cfg) {
        DbStatus status = new DbStatus();
        status.setHost(cfg.getHost());
        status.setPort(cfg.getPort());
        status.setDatabase(cfg.getDatabaseName());
        status.setUser(cfg.getUser());
        status.setConfigSource(cfg.getSourceDescription());
        status.setClusterType(cfg.getClusterType());
        status.setMaskedUri(cfg.getMaskedUri());
        status.setLanIp(resolveLanIp());
        return status;
    }

    private void fillConnected(DbStatus status, DatabaseConfig cfg, long latencyMs) {
        status.setConnected(true);
        status.setUsingLocalFallback(usingLocalFallback);
        status.setServerVersion(serverVersion);
        status.setLastSuccessfulConnectionIso(lastSuccessfulPingIso);
        if (usingLocalFallback) {
            status.setClusterType("Local MongoDB Community (fallback)");
            status.setHost("localhost");
            status.setPort(27017);
            status.setMaskedUri(cfg.getLocalFallbackUri());
            status.setUser("(no auth)");
        }
        status.setMessage("Connected: " + status.getClusterType() + " · database `" + status.getDatabase()
                + "` answered {ping:1} in " + latencyMs + " ms (MongoDB " + (serverVersion != null ? serverVersion : "?") + ").");
    }

    private void fillDisconnected(DbStatus status, Exception ex) {
        connected = false;
        String message = ex instanceof DatabaseException ? ex.getMessage() : describeFailure(activeUri, ex);
        String hint = ex instanceof DatabaseException de && de.getRemediationHint() != null
                ? de.getRemediationHint()
                : remediationFor(activeUri, ex);
        lastError = message;
        lastRemediationHint = hint;
        status.setConnected(false);
        status.setLastError(message);
        status.setRemediationHint(hint);
        status.setLastSuccessfulConnectionIso(lastSuccessfulPingIso);
        status.setMessage("Disconnected: " + message);
    }

    // ---------------------------------------------------------- error messages

    /** Short, human-readable failure description for the status bar / dialogs. */
    public static String describeFailure(String uri, Throwable ex) {
        Throwable root = rootCause(ex);
        String cluster = DatabaseConfig.describeClusterType(uri);
        String raw = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        if (ex instanceof MongoSecurityException || raw.toLowerCase(Locale.ROOT).contains("authentication failed")) {
            return cluster + ": authentication failed - the username/password inside MONGO_URI is wrong.";
        }
        if (ex instanceof MongoTimeoutException || raw.toLowerCase(Locale.ROOT).contains("timed out")) {
            return cluster + ": timed out while selecting a server (" + DatabaseConfig.maskUri(uri) + ").";
        }
        if (ex instanceof MongoSocketException || raw.toLowerCase(Locale.ROOT).contains("connection refused")) {
            return cluster + ": connection refused at " + DatabaseConfig.maskUri(uri) + ".";
        }
        if (raw.toLowerCase(Locale.ROOT).contains("srv") || raw.toLowerCase(Locale.ROOT).contains("dns")
                || raw.toLowerCase(Locale.ROOT).contains("unknownhost")) {
            return cluster + ": DNS/SRV lookup failed for " + DatabaseConfig.maskUri(uri) + " (offline Wi-Fi or typo in cluster host).";
        }
        if (raw.length() > 220) raw = raw.substring(0, 220) + "...";
        return cluster + ": " + raw;
    }

    /** Step-by-step fix shown in the non-crashing startup dialog. */
    public static String remediationFor(String uri, Throwable ex) {
        boolean atlas = DatabaseConfig.isAtlasUri(uri);
        String raw = rootCause(ex).getMessage() != null ? rootCause(ex).getMessage().toLowerCase(Locale.ROOT) : "";
        StringBuilder sb = new StringBuilder();
        if (atlas) {
            sb.append("1. Atlas -> Network Access -> Add IP Address -> ALLOW ACCESS FROM ANYWHERE (0.0.0.0/0) so both laptops can connect.\n");
            sb.append("2. Check the Wi-Fi/VPN: college networks sometimes block port 27017 - try a mobile hotspot.\n");
            if (ex instanceof MongoSecurityException || raw.contains("authentication")) {
                sb.append("3. Atlas -> Database Access: confirm the user exists and paste the exact password into MONGO_URI (URL-encode @ : / characters).\n");
            } else {
                sb.append("3. Verify MONGO_URI in .env: mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority\n");
            }
            sb.append("4. Or set MONGO_URI=mongodb://localhost:27017 to use a local MongoDB Community server.");
        } else {
            sb.append("1. Start MongoDB locally: `brew services start mongodb-community` (macOS) or start the MongoDB service (Windows).\n");
            sb.append("2. Or point MONGO_URI in .env at the shared MongoDB Atlas cluster (recommended for the demo).\n");
            sb.append("3. Use the Connection Settings dialog (top bar) to test and save a new URI without restarting.");
        }
        return sb.toString();
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur == null ? new RuntimeException("Unknown error") : cur;
    }

    private String resolveLanIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // ---------------------------------------------------------------- inspector

    /** Runs an aggregation and returns both the result documents and explain("executionStats"). */
    public Document explainAggregate(String collection, List<? extends Bson> pipeline) throws DatabaseException {
        MongoDatabase db = getDatabase();
        Document cmd = new Document("explain",
                new Document("aggregate", collection)
                        .append("pipeline", BsonUtil.toDocuments(pipeline))
                        .append("cursor", new Document()))
                .append("verbosity", "executionStats");
        return db.runCommand(cmd);
    }

    public List<Document> sampleDocuments(String collection, int limit) throws DatabaseException {
        List<Document> out = new ArrayList<>();
        getDatabase().getCollection(collection).find().limit(Math.max(1, limit)).into(out);
        return out;
    }

    // ----------------------------------------------------------------- getters

    public boolean isLastKnownConnected() {
        return connected;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastRemediationHint() {
        return lastRemediationHint;
    }

    public String getLastSuccessfulPingIso() {
        return lastSuccessfulPingIso;
    }

    public synchronized boolean isUsingLocalFallback() {
        return usingLocalFallback;
    }

    public synchronized String getActiveUriMasked() {
        return activeUri == null ? "" : DatabaseConfig.maskUri(activeUri);
    }

    public String getServerVersion() {
        return serverVersion;
    }
}
