package edu.gcet.placementhub.db;

import edu.gcet.placementhub.config.AppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Robust JDBC Connection Manager for MySQL 8 instance 'gcet_placement_hub'
 */
public class DatabaseConnection {
    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());

    public static String getJdbcUrl() {
        return String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8",
            AppConfig.getDbHost(), AppConfig.getDbPort(), AppConfig.getDbName()
        );
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "MySQL JDBC Driver not found on classpath", e);
        }
        return DriverManager.getConnection(getJdbcUrl(), AppConfig.getDbUser(), AppConfig.getDbPassword());
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            LOGGER.warning("Database ping returned: " + e.getMessage());
            return false;
        }
    }
}
