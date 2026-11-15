package edu.gcet.placementhub.database;

import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.GovernanceModels.ExtendedDbStatus;
import edu.gcet.placementhub.models.GovernanceModels.TableDiagnostic;

import java.net.InetAddress;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Non-destructive JDBC connector for the existing local MySQL database `gcet_placement_hub`.
 * Never runs DROP, TRUNCATE, or destructive schema resets.
 */
public final class DatabaseConnection {

    public static final List<String> REQUIRED_17_TABLES = List.of(
            "users",
            "students",
            "faculty",
            "skills",
            "student_skills",
            "companies",
            "placement_drives",
            "applications",
            "approval_records",
            "audit_logs",
            "student_projects",
            "student_certifications",
            "student_internships",
            "drive_eligible_branches",
            "drive_required_skills",
            "application_stage_history",
            "admin_requests"
    );

    private static DatabaseConnection instance;

    private volatile boolean connected = false;
    private volatile String lastError = null;
    private volatile String lastSuccessfulPingIso = null;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[JDBC] MySQL Connector/J driver class not found: " + e.getMessage());
        }
    }

    private DatabaseConnection() {
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Opens a fresh JDBC connection to gcet_placement_hub using external config.
     */
    public Connection getConnection() throws SQLException {
        DatabaseConfig cfg = DatabaseConfig.getInstance();
        try {
            Connection conn = DriverManager.getConnection(cfg.getJdbcUrl(), cfg.getUser(), cfg.getPassword());
            this.connected = true;
            this.lastError = null;
            this.lastSuccessfulPingIso = Instant.now().toString();
            return conn;
        } catch (SQLException ex) {
            this.connected = false;
            this.lastError = ex.getMessage();
            throw ex;
        }
    }

    /**
     * Executes a genuine live `SELECT 1 AS ping` against MySQL and returns diagnostic status.
     */
    public DbStatus pingAndInspect() {
        DatabaseConfig cfg = DatabaseConfig.getInstance();
        DbStatus status = new DbStatus();
        status.setHost(cfg.getHost());
        status.setPort(cfg.getPort());
        status.setDatabase(cfg.getDatabase());
        status.setUser(cfg.getUser());
        status.setConfigSource(cfg.getSourceDescription());
        status.setLanIp(resolveLanIp());

        try (Connection conn = DriverManager.getConnection(cfg.getJdbcUrl(), cfg.getUser(), cfg.getPassword());
             PreparedStatement ps = conn.prepareStatement("SELECT 1 AS ping");
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                this.connected = true;
                this.lastError = null;
                this.lastSuccessfulPingIso = Instant.now().toString();
                status.setConnected(true);
                status.setLastSuccessfulConnectionIso(this.lastSuccessfulPingIso);
                status.setMessage("Connected: MySQL database gcet_placement_hub is responding via JDBC.");
                status.setTableCounts(fetchTableCounts(conn));
            }
        } catch (SQLException ex) {
            this.connected = false;
            this.lastError = ex.getMessage();
            status.setConnected(false);
            status.setLastError(ex.getMessage());
            status.setMessage("Disconnected: " + ex.getMessage());
        }
        return status;
    }

    /**
     * Read-only full diagnostic snapshot checking MySQL version, ping latency,
     * and all 17 required tables + live record counts. Never exposes credentials or modifies schema.
     */
    public ExtendedDbStatus inspectExtendedDiagnostics() {
        DatabaseConfig cfg = DatabaseConfig.getInstance();
        DbStatus status = new DbStatus();
        status.setHost(cfg.getHost());
        status.setPort(cfg.getPort());
        status.setDatabase(cfg.getDatabase());
        status.setUser(cfg.getUser());
        status.setConfigSource(cfg.getSourceDescription());
        status.setLanIp(resolveLanIp());

        String mysqlVersion = "Unavailable";
        long latencyMs = -1;
        List<TableDiagnostic> tableDiagnostics = new ArrayList<>();
        int availableCount = 0;

        long startNanos = System.nanoTime();
        try (Connection conn = DriverManager.getConnection(cfg.getJdbcUrl(), cfg.getUser(), cfg.getPassword())) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 AS ping");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
                    this.connected = true;
                    this.lastError = null;
                    this.lastSuccessfulPingIso = Instant.now().toString();
                    status.setConnected(true);
                    status.setLastSuccessfulConnectionIso(this.lastSuccessfulPingIso);
                    status.setMessage("Connected: MySQL database " + cfg.getDatabase() + " responded in " + latencyMs + " ms.");
                }
            }

            try (PreparedStatement vps = conn.prepareStatement("SELECT VERSION() AS ver");
                 ResultSet vrs = vps.executeQuery()) {
                if (vrs.next()) {
                    mysqlVersion = vrs.getString("ver");
                }
            } catch (SQLException ignored) {
                mysqlVersion = "MySQL 8.x";
            }

            Set<String> existingTables = new HashSet<>();
            try (PreparedStatement ips = conn.prepareStatement(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?")) {
                ips.setString(1, cfg.getDatabase());
                try (ResultSet irs = ips.executeQuery()) {
                    while (irs.next()) {
                        String tName = irs.getString("TABLE_NAME");
                        if (tName != null) {
                            existingTables.add(tName.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }

            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String table : REQUIRED_17_TABLES) {
                boolean exists = existingTables.contains(table.toLowerCase(Locale.ROOT));
                if (!exists) {
                    tableDiagnostics.add(new TableDiagnostic(table, false, 0, "Table missing in schema"));
                    continue;
                }
                availableCount++;
                try (Statement st = conn.createStatement();
                     ResultSet crs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                    int rowCount = crs.next() ? crs.getInt(1) : 0;
                    counts.put(table, rowCount);
                    tableDiagnostics.add(new TableDiagnostic(
                            table,
                            true,
                            rowCount,
                            rowCount == 0 ? "Available (0 records)" : "Available & Verified"
                    ));
                } catch (SQLException tex) {
                    tableDiagnostics.add(new TableDiagnostic(table, true, 0, "Query warning: " + tex.getMessage()));
                }
            }
            status.setTableCounts(counts);
        } catch (SQLException ex) {
            latencyMs = -1;
            this.connected = false;
            this.lastError = ex.getMessage();
            status.setConnected(false);
            status.setLastError(ex.getMessage());
            status.setLastSuccessfulConnectionIso(this.lastSuccessfulPingIso);
            status.setMessage("Disconnected: " + ex.getMessage());
            for (String table : REQUIRED_17_TABLES) {
                tableDiagnostics.add(new TableDiagnostic(table, false, 0, "Database unreachable"));
            }
        }

        return new ExtendedDbStatus(
                status,
                mysqlVersion,
                latencyMs,
                tableDiagnostics,
                availableCount,
                REQUIRED_17_TABLES.size()
        );
    }

    private Map<String, Integer> fetchTableCounts(Connection conn) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : REQUIRED_17_TABLES) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                if (rs.next()) {
                    counts.put(table, rs.getInt(1));
                }
            } catch (SQLException ignored) {
            }
        }
        return counts;
    }

    private String resolveLanIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public boolean isLastKnownConnected() {
        return connected;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastSuccessfulPingIso() {
        return lastSuccessfulPingIso;
    }
}
