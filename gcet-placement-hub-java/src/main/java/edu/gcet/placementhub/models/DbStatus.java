package edu.gcet.placementhub.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live MySQL JDBC diagnostic snapshot.
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
    private String lastSuccessfulConnectionIso;
    private String configSource;
    private Map<String, Integer> tableCounts = new LinkedHashMap<>();

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

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

    public String getLastSuccessfulConnectionIso() { return lastSuccessfulConnectionIso; }
    public void setLastSuccessfulConnectionIso(String lastSuccessfulConnectionIso) {
        this.lastSuccessfulConnectionIso = lastSuccessfulConnectionIso;
    }

    public String getConfigSource() { return configSource; }
    public void setConfigSource(String configSource) { this.configSource = configSource; }

    public Map<String, Integer> getTableCounts() { return tableCounts; }
    public void setTableCounts(Map<String, Integer> tableCounts) {
        this.tableCounts = tableCounts != null ? tableCounts : new LinkedHashMap<>();
    }
}
