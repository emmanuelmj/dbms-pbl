package edu.gcet.placementhub.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live MongoDB connection diagnostic snapshot (Atlas cloud cluster or local server).
 * Never carries credentials: the URI is stored masked.
 */
public class DbStatus {
    private boolean connected;
    private String host;
    private int port;
    private String database;
    private String user;
    private String lanIp;
    private String message;
    private String lastError;
    private String remediationHint;
    private String lastSuccessfulConnectionIso;
    private String configSource;
    private String clusterType;
    private String serverVersion;
    private String maskedUri;
    private boolean usingLocalFallback;
    private long pingLatencyMs = -1;
    private Map<String, Integer> tableCounts = new LinkedHashMap<>();

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    /** "host:port" or "host (SRV)" for Atlas clusters resolved through DNS. */
    public String getHostLabel() {
        if (host == null) return "-";
        return port < 0 ? host + " (SRV)" : host + ":" + port;
    }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getLanIp() { return lanIp; }
    public void setLanIp(String lanIp) { this.lanIp = lanIp; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getRemediationHint() { return remediationHint; }
    public void setRemediationHint(String remediationHint) { this.remediationHint = remediationHint; }

    public String getLastSuccessfulConnectionIso() { return lastSuccessfulConnectionIso; }
    public void setLastSuccessfulConnectionIso(String lastSuccessfulConnectionIso) {
        this.lastSuccessfulConnectionIso = lastSuccessfulConnectionIso;
    }

    public String getConfigSource() { return configSource; }
    public void setConfigSource(String configSource) { this.configSource = configSource; }

    public String getClusterType() { return clusterType; }
    public void setClusterType(String clusterType) { this.clusterType = clusterType; }

    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }

    public String getMaskedUri() { return maskedUri; }
    public void setMaskedUri(String maskedUri) { this.maskedUri = maskedUri; }

    public boolean isUsingLocalFallback() { return usingLocalFallback; }
    public void setUsingLocalFallback(boolean usingLocalFallback) { this.usingLocalFallback = usingLocalFallback; }

    public boolean isAtlas() {
        return (clusterType != null && clusterType.toLowerCase().contains("atlas"))
                || (maskedUri != null && maskedUri.contains("mongodb+srv"))
                || (host != null && host.toLowerCase().contains("mongodb.net"));
    }

    public long getPingLatencyMs() { return pingLatencyMs; }
    public void setPingLatencyMs(long pingLatencyMs) { this.pingLatencyMs = pingLatencyMs; }

    /** Collection name -> live document count (kept under the historical name for UI compatibility). */
    public Map<String, Integer> getTableCounts() { return tableCounts; }
    public void setTableCounts(Map<String, Integer> tableCounts) {
        this.tableCounts = tableCounts != null ? tableCounts : new LinkedHashMap<>();
    }

    public Map<String, Integer> getCollectionCounts() { return getTableCounts(); }
    public void setCollectionCounts(Map<String, Integer> counts) { setTableCounts(counts); }
}
