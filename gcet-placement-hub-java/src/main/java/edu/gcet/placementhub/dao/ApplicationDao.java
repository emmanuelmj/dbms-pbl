package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.models.Application;
import edu.gcet.placementhub.models.ApplicationStage;
import edu.gcet.placementhub.models.StageHistoryEntry;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC Data Access Object for `applications` and `application_stage_history`.
 */
public class ApplicationDao {

    public List<Application> findAllForUser(Connection conn, User user) throws SQLException {
        List<Application> apps = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT a.*, "
                        + "s.roll_number AS student_roll_number, "
                        + "s.full_name AS student_name, "
                        + "s.branch AS student_branch, "
                        + "s.cgpa AS student_cgpa, "
                        + "d.company_id AS company_id "
                        + "FROM applications a "
                        + "JOIN students s ON s.id = a.student_id "
                        + "LEFT JOIN placement_drives d ON d.id = a.drive_id "
        );

        String param = null;
        if (user != null && user.getRole() == UserRole.STUDENT && user.getStudentRollNumber() != null && !user.getStudentRollNumber().isBlank()) {
            sql.append("WHERE LOWER(s.roll_number) = LOWER(?) ");
            param = user.getStudentRollNumber().trim();
        } else if (user != null && user.getRole() == UserRole.COMPANY && user.getCompanyId() != null && !user.getCompanyId().isBlank()) {
            sql.append("WHERE d.company_id = ? ");
            param = user.getCompanyId().trim();
        }
        sql.append("ORDER BY a.applied_at DESC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (param != null) {
                ps.setString(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    apps.add(mapApplication(rs));
                }
            }
        }

        if (!apps.isEmpty()) {
            Map<String, List<StageHistoryEntry>> histMap = loadStageHistory(conn);
            for (Application a : apps) {
                List<StageHistoryEntry> entries = histMap.get(a.getId());
                if (entries != null) a.setStageHistory(entries);
            }
        }
        return apps;
    }

    public Application findById(Connection conn, String appId) throws SQLException {
        if (appId == null || appId.isBlank()) return null;
        String sql = """
                SELECT a.*,
                  s.roll_number AS student_roll_number,
                  s.full_name AS student_name,
                  s.branch AS student_branch,
                  s.cgpa AS student_cgpa,
                  d.company_id AS company_id
                FROM applications a
                JOIN students s ON s.id = a.student_id
                LEFT JOIN placement_drives d ON d.id = a.drive_id
                WHERE a.id = ? LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Application a = mapApplication(rs);
                    a.setStageHistory(loadHistoryForApp(conn, a.getId()));
                    return a;
                }
            }
        }
        return null;
    }

    public Application findByStudentAndDrive(Connection conn, String studentId, String driveId) throws SQLException {
        String sql = """
                SELECT a.*,
                  s.roll_number AS student_roll_number,
                  s.full_name AS student_name,
                  s.branch AS student_branch,
                  s.cgpa AS student_cgpa,
                  d.company_id AS company_id
                FROM applications a
                JOIN students s ON s.id = a.student_id
                LEFT JOIN placement_drives d ON d.id = a.drive_id
                WHERE a.student_id = ? AND a.drive_id = ? LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, driveId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapApplication(rs);
                }
            }
        }
        return null;
    }

    public void insertApplicationWithHistory(
            Connection conn,
            String appId,
            String driveId,
            String studentId,
            String companyName,
            String jobRole,
            String actorFullName
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO applications (id, drive_id, student_id, company_name, job_role, current_stage, applied_at) VALUES (?, ?, ?, ?, ?, 'APPLICATION_SUBMITTED', NOW())")) {
            ps.setString(1, appId);
            ps.setString(2, driveId);
            ps.setString(3, studentId);
            ps.setString(4, companyName);
            ps.setString(5, jobRole);
            ps.executeUpdate();
        }
        insertStageHistory(conn, appId, ApplicationStage.APPLICATION_SUBMITTED, actorFullName,
                "Application submitted via GCET Placement Hub");
    }

    public void reapplyWithdrawnApplication(Connection conn, String appId, String actorFullName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE applications SET current_stage = 'APPLICATION_SUBMITTED', applied_at = NOW(), rejection_reason = NULL WHERE id = ?")) {
            ps.setString(1, appId);
            ps.executeUpdate();
        }
        insertStageHistory(conn, appId, ApplicationStage.APPLICATION_SUBMITTED, actorFullName,
                "Re-submitted application after withdrawal");
    }

    public void updateApplicationStage(
            Connection conn,
            String appId,
            String studentId,
            String companyId,
            ApplicationStage nextStage,
            Double offerLpa,
            String rejectionReason,
            String notes,
            String actorFullName
    ) throws SQLException {
        String updateAppSql = """
                UPDATE applications SET
                  current_stage = ?,
                  final_offer_lpa = CASE WHEN ? IN ('SELECTED', 'OFFERED', 'JOINED') THEN COALESCE(?, final_offer_lpa) ELSE final_offer_lpa END,
                  rejection_reason = CASE WHEN ? = 'REJECTED' THEN COALESCE(?, rejection_reason) ELSE rejection_reason END
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(updateAppSql)) {
            ps.setString(1, nextStage.name());
            ps.setString(2, nextStage.name());
            if (offerLpa != null) {
                ps.setDouble(3, offerLpa);
            } else {
                ps.setNull(3, Types.DECIMAL);
            }
            ps.setString(4, nextStage.name());
            ps.setString(5, rejectionReason != null && !rejectionReason.isBlank() ? rejectionReason : notes);
            ps.setString(6, appId);
            ps.executeUpdate();
        }

        String effectiveNotes = (notes != null && !notes.isBlank()) ? notes : "Moved to " + nextStage.getLabel();
        insertStageHistory(conn, appId, nextStage, actorFullName, effectiveNotes);

        if (nextStage.isOfferOrSelected()) {
            String updateStudentSql = """
                    UPDATE students SET
                      placement_status = 'PLACED',
                      placed_company_id = COALESCE(?, placed_company_id),
                      placed_package_lpa = COALESCE(?, placed_package_lpa)
                    WHERE id = ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(updateStudentSql)) {
                ps.setString(1, companyId != null && !companyId.isBlank() ? companyId : null);
                if (offerLpa != null) {
                    ps.setDouble(2, offerLpa);
                } else {
                    ps.setNull(2, Types.DECIMAL);
                }
                ps.setString(3, studentId);
                ps.executeUpdate();
            }
        } else if (nextStage == ApplicationStage.SHORTLISTED) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE students SET placement_status = CASE WHEN placement_status = 'UNPLACED' THEN 'SHORTLISTED' ELSE placement_status END WHERE id = ?")) {
                ps.setString(1, studentId);
                ps.executeUpdate();
            }
        }
    }

    public void withdrawApplication(Connection conn, String appId, String actorFullName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE applications SET current_stage = 'WITHDRAWN' WHERE id = ?")) {
            ps.setString(1, appId);
            ps.executeUpdate();
        }
        insertStageHistory(conn, appId, ApplicationStage.WITHDRAWN, actorFullName, "Withdrawn by candidate");
    }

    private void insertStageHistory(
            Connection conn,
            String appId,
            ApplicationStage stage,
            String updatedBy,
            String notes
    ) throws SQLException {
        String sql = "INSERT INTO application_stage_history (id, application_id, stage, updated_by, notes) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, java.util.UUID.randomUUID().toString());
            ps.setString(2, appId);
            ps.setString(3, stage.name());
            ps.setString(4, updatedBy != null && !updatedBy.isBlank() ? updatedBy : "Placement Cell");
            ps.setString(5, notes != null ? notes : "");
            ps.executeUpdate();
        }
    }

    private List<StageHistoryEntry> loadHistoryForApp(Connection conn, String appId) throws SQLException {
        List<StageHistoryEntry> list = new ArrayList<>();
        String sql = "SELECT application_id, stage, timestamp, updated_by, notes FROM application_stage_history WHERE application_id = ? ORDER BY timestamp ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StageHistoryEntry entry = new StageHistoryEntry();
                    entry.setStage(ApplicationStage.fromString(rs.getString("stage")));
                    Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) entry.setTimestamp(ts.toInstant().toString());
                    entry.setUpdatedBy(rs.getString("updated_by"));
                    entry.setNotes(rs.getString("notes"));
                    list.add(entry);
                }
            }
        }
        return list;
    }

    private Map<String, List<StageHistoryEntry>> loadStageHistory(Connection conn) {
        Map<String, List<StageHistoryEntry>> map = new HashMap<>();
        String sql = "SELECT application_id, stage, timestamp, updated_by, notes FROM application_stage_history ORDER BY timestamp ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String appId = rs.getString("application_id");
                if (appId != null) {
                    StageHistoryEntry entry = new StageHistoryEntry();
                    entry.setStage(ApplicationStage.fromString(rs.getString("stage")));
                    Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) entry.setTimestamp(ts.toInstant().toString());
                    entry.setUpdatedBy(rs.getString("updated_by"));
                    entry.setNotes(rs.getString("notes"));
                    map.computeIfAbsent(appId, k -> new ArrayList<>()).add(entry);
                }
            }
        } catch (SQLException ignored) {
        }
        return map;
    }

    private Application mapApplication(ResultSet rs) throws SQLException {
        Application a = new Application();
        a.setId(rs.getString("id"));
        a.setDriveId(rs.getString("drive_id"));
        a.setStudentId(rs.getString("student_id"));
        a.setStudentRollNumber(safeString(rs, "student_roll_number"));
        a.setStudentName(safeString(rs, "student_name"));
        a.setStudentBranch(safeString(rs, "student_branch"));
        a.setStudentCgpa(safeDouble(rs, "student_cgpa", 0));
        a.setCompanyId(safeString(rs, "company_id"));
        a.setCompanyName(rs.getString("company_name"));
        a.setJobRole(rs.getString("job_role"));
        a.setCurrentStage(ApplicationStage.fromString(safeString(rs, "current_stage")));
        Timestamp appliedAt = safeTimestamp(rs, "applied_at");
        if (appliedAt != null) a.setAppliedAt(appliedAt.toInstant().toString());
        double offer = safeDouble(rs, "final_offer_lpa", -1);
        a.setFinalOfferLpa(offer >= 0 ? offer : null);
        a.setRejectionReason(safeString(rs, "rejection_reason"));
        return a;
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (column.equalsIgnoreCase(meta.getColumnLabel(i))) return true;
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    private static String safeString(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getString(col) : null;
    }

    private static double safeDouble(ResultSet rs, String col, double def) throws SQLException {
        if (!hasColumn(rs, col)) return def;
        Object obj = rs.getObject(col);
        return obj == null ? def : ((Number) obj).doubleValue();
    }

    private static Timestamp safeTimestamp(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getTimestamp(col) : null;
    }
}
