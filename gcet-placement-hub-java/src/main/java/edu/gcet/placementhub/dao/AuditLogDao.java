package edu.gcet.placementhub.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Non-blocking JDBC audit trail DAO writing to `audit_logs`.
 */
public class AuditLogDao {

    public void insertLog(Connection conn, String action, String actorId, String actorUsername, String actorRole, String details) {
        String sql = "INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, action);
            ps.setString(3, actorId);
            ps.setString(4, actorUsername != null ? actorUsername : "system");
            ps.setString(5, actorRole != null ? actorRole : "SYSTEM");
            ps.setString(6, details);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Non-blocking audit log write
        }
    }

    public java.util.List<edu.gcet.placementhub.models.AuditLog> findRecent(Connection conn, int limit) {
        return findFiltered(conn, "", "ALL", "ALL", "", "", null, limit);
    }

    public java.util.List<edu.gcet.placementhub.models.AuditLog> findFiltered(
            Connection conn,
            String search,
            String actionFilter,
            String roleFilter,
            String fromDate,
            String toDate,
            String restrictedActorId,
            int limit
    ) {
        java.util.List<edu.gcet.placementhub.models.AuditLog> logs = new java.util.ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, action, actor_id, actor_username, actor_role, details, created_at FROM audit_logs WHERE 1=1 "
        );
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (restrictedActorId != null && !restrictedActorId.isBlank()) {
            sql.append(" AND actor_id = ? ");
            params.add(restrictedActorId.trim());
        }
        if (actionFilter != null && !actionFilter.isBlank() && !"ALL".equalsIgnoreCase(actionFilter)) {
            sql.append(" AND UPPER(action) = UPPER(?) ");
            params.add(actionFilter.trim());
        }
        if (roleFilter != null && !roleFilter.isBlank() && !"ALL".equalsIgnoreCase(roleFilter)) {
            sql.append(" AND UPPER(actor_role) = UPPER(?) ");
            params.add(roleFilter.trim());
        }
        if (fromDate != null && !fromDate.isBlank()) {
            sql.append(" AND DATE(created_at) >= ? ");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.isBlank()) {
            sql.append(" AND DATE(created_at) <= ? ");
            params.add(toDate.trim());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(action) LIKE ? OR LOWER(actor_username) LIKE ? OR LOWER(actor_role) LIKE ? OR LOWER(details) LIKE ?) ");
            String q = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 1000)));

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object val = params.get(i);
                if (val instanceof Integer n) {
                    ps.setInt(i + 1, n);
                } else {
                    ps.setString(i + 1, String.valueOf(val));
                }
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    edu.gcet.placementhub.models.AuditLog log = new edu.gcet.placementhub.models.AuditLog();
                    log.setId(rs.getString("id"));
                    log.setAction(rs.getString("action"));
                    log.setActorId(rs.getString("actor_id"));
                    log.setActorUsername(rs.getString("actor_username"));
                    log.setActorRole(rs.getString("actor_role"));
                    log.setDetails(rs.getString("details"));
                    java.sql.Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        log.setCreatedAt(ts.toInstant().toString());
                    }
                    logs.add(log);
                }
            }
        } catch (SQLException ignored) {
            // Return empty list if audit_logs unavailable
        }
        return logs;
    }

    public java.util.List<String> findDistinctActions(Connection conn) {
        java.util.List<String> actions = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT action FROM audit_logs ORDER BY action ASC");
             java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String a = rs.getString(1);
                if (a != null && !a.isBlank()) actions.add(a);
            }
        } catch (SQLException ignored) {
        }
        return actions;
    }
}
