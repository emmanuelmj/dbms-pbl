package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.models.DriveRequiredSkill;
import edu.gcet.placementhub.models.PlacementDrive;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC Data Access Object for `placement_drives`, `drive_eligible_branches`, and `drive_required_skills`.
 */
public class DriveDao {

    public List<PlacementDrive> findAll(Connection conn) throws SQLException {
        List<PlacementDrive> drives = new ArrayList<>();
        String sql = "SELECT * FROM placement_drives ORDER BY drive_date ASC, created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                drives.add(mapDrive(rs));
            }
        }

        if (!drives.isEmpty()) {
            Map<String, List<String>> branchMap = loadEligibleBranches(conn);
            Map<String, List<DriveRequiredSkill>> reqSkillMap = loadRequiredSkills(conn);
            for (PlacementDrive d : drives) {
                List<String> branches = branchMap.get(d.getId());
                if (branches != null) d.setEligibleBranches(branches);
                List<DriveRequiredSkill> reqs = reqSkillMap.get(d.getId());
                if (reqs != null) d.setRequiredSkills(reqs);
            }
        }
        return drives;
    }

    public PlacementDrive findById(Connection conn, String driveId) throws SQLException {
        if (driveId == null || driveId.isBlank()) return null;
        PlacementDrive drive = null;
        String sql = "SELECT * FROM placement_drives WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, driveId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    drive = mapDrive(rs);
                }
            }
        }
        if (drive != null) {
            drive.setEligibleBranches(loadBranchesForDrive(conn, drive.getId()));
            drive.setRequiredSkills(loadSkillsForDrive(conn, drive.getId()));
        }
        return drive;
    }

    public void insertDrive(Connection conn, PlacementDrive d) throws SQLException {
        String sql = """
                INSERT INTO placement_drives (
                  id, company_id, company_name, job_role, job_description, salary_package_lpa,
                  min_cgpa, max_backlogs_allowed, target_graduation_year, drive_date,
                  application_deadline, available_positions, status, selection_process
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.getId());
            ps.setString(2, d.getCompanyId());
            ps.setString(3, d.getCompanyName());
            ps.setString(4, d.getJobRole());
            ps.setString(5, d.getJobDescription() != null ? d.getJobDescription() : "");
            ps.setDouble(6, d.getSalaryPackageLpa());
            ps.setDouble(7, d.getMinCgpa());
            ps.setInt(8, d.getMaxBacklogsAllowed());
            ps.setInt(9, d.getTargetGraduationYear() > 0 ? d.getTargetGraduationYear() : 2026);
            ps.setDate(10, parseSqlDate(d.getDriveDate()));
            ps.setDate(11, parseSqlDate(d.getApplicationDeadline()));
            ps.setInt(12, Math.max(1, d.getAvailablePositions()));
            ps.setString(13, d.getStatus() != null && !d.getStatus().isBlank() ? d.getStatus() : "OPEN");
            ps.setString(14, d.getSelectionProcess() != null && !d.getSelectionProcess().isBlank()
                    ? d.getSelectionProcess()
                    : "Online Assessment -> Technical Interview -> HR");
            ps.executeUpdate();
        }

        replaceBranches(conn, d.getId(), d.getEligibleBranches());
        replaceRequiredSkills(conn, d.getId(), d.getRequiredSkills());

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE companies SET total_drives_conducted = COALESCE(total_drives_conducted, 0) + 1 WHERE id = ?")) {
            ps.setString(1, d.getCompanyId());
            ps.executeUpdate();
        }
    }

    public void updateDrive(Connection conn, PlacementDrive d) throws SQLException {
        String sql = """
                UPDATE placement_drives SET
                  company_id = ?, company_name = ?, job_role = ?, job_description = ?,
                  salary_package_lpa = ?, min_cgpa = ?, max_backlogs_allowed = ?,
                  target_graduation_year = ?, drive_date = ?, application_deadline = ?,
                  available_positions = ?, status = ?, selection_process = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.getCompanyId());
            ps.setString(2, d.getCompanyName());
            ps.setString(3, d.getJobRole());
            ps.setString(4, d.getJobDescription() != null ? d.getJobDescription() : "");
            ps.setDouble(5, d.getSalaryPackageLpa());
            ps.setDouble(6, d.getMinCgpa());
            ps.setInt(7, d.getMaxBacklogsAllowed());
            ps.setInt(8, d.getTargetGraduationYear() > 0 ? d.getTargetGraduationYear() : 2026);
            ps.setDate(9, parseSqlDate(d.getDriveDate()));
            ps.setDate(10, parseSqlDate(d.getApplicationDeadline()));
            ps.setInt(11, Math.max(1, d.getAvailablePositions()));
            ps.setString(12, d.getStatus() != null && !d.getStatus().isBlank() ? d.getStatus() : "OPEN");
            ps.setString(13, d.getSelectionProcess() != null ? d.getSelectionProcess() : "");
            ps.setString(14, d.getId());
            ps.executeUpdate();
        }

        replaceBranches(conn, d.getId(), d.getEligibleBranches());
        replaceRequiredSkills(conn, d.getId(), d.getRequiredSkills());
    }

    public int countApplicationsForDrive(Connection conn, String driveId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM applications WHERE drive_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, driveId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void deleteDriveCascade(Connection conn, String driveId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM drive_eligible_branches WHERE drive_id = ?")) {
            ps.setString(1, driveId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM drive_required_skills WHERE drive_id = ?")) {
            ps.setString(1, driveId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM placement_drives WHERE id = ?")) {
            ps.setString(1, driveId);
            ps.executeUpdate();
        }
    }

    private void replaceBranches(Connection conn, String driveId, List<String> branches) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM drive_eligible_branches WHERE drive_id = ?")) {
            del.setString(1, driveId);
            del.executeUpdate();
        }
        if (branches == null || branches.isEmpty()) return;
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT IGNORE INTO drive_eligible_branches (drive_id, branch) VALUES (?, ?)")) {
            for (String branch : branches) {
                if (branch != null && !branch.isBlank()) {
                    ins.setString(1, driveId);
                    ins.setString(2, branch.trim());
                    ins.addBatch();
                }
            }
            ins.executeBatch();
        }
    }

    private void replaceRequiredSkills(Connection conn, String driveId, List<DriveRequiredSkill> reqSkills) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM drive_required_skills WHERE drive_id = ?")) {
            del.setString(1, driveId);
            del.executeUpdate();
        }
        if (reqSkills == null || reqSkills.isEmpty()) return;
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT IGNORE INTO drive_required_skills (drive_id, skill_id, min_proficiency, is_mandatory) VALUES (?, ?, ?, ?)")) {
            for (DriveRequiredSkill r : reqSkills) {
                if (r != null && r.getSkillId() != null && !r.getSkillId().isBlank()) {
                    ins.setString(1, driveId);
                    ins.setString(2, r.getSkillId().trim());
                    ins.setInt(3, Math.max(1, Math.min(5, r.getMinProficiency())));
                    ins.setBoolean(4, r.isMandatory());
                    ins.addBatch();
                }
            }
            ins.executeBatch();
        }
    }

    private List<String> loadBranchesForDrive(Connection conn, String driveId) throws SQLException {
        List<String> branches = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT branch FROM drive_eligible_branches WHERE drive_id = ?")) {
            ps.setString(1, driveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    branches.add(rs.getString("branch"));
                }
            }
        }
        return branches;
    }

    private List<DriveRequiredSkill> loadSkillsForDrive(Connection conn, String driveId) throws SQLException {
        List<DriveRequiredSkill> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT skill_id, min_proficiency, is_mandatory FROM drive_required_skills WHERE drive_id = ?")) {
            ps.setString(1, driveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DriveRequiredSkill(
                            rs.getString("skill_id"),
                            rs.getInt("min_proficiency"),
                            rs.getBoolean("is_mandatory")
                    ));
                }
            }
        }
        return list;
    }

    private static Date parseSqlDate(String value) {
        if (value == null || value.isBlank()) {
            return Date.valueOf(java.time.LocalDate.now());
        }
        try {
            String trimmed = value.trim();
            if (trimmed.length() >= 10) trimmed = trimmed.substring(0, 10);
            return Date.valueOf(trimmed);
        } catch (IllegalArgumentException ex) {
            return Date.valueOf(java.time.LocalDate.now());
        }
    }

    private Map<String, List<String>> loadEligibleBranches(Connection conn) {
        Map<String, List<String>> map = new HashMap<>();
        String sql = "SELECT drive_id, branch FROM drive_eligible_branches";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String driveId = rs.getString("drive_id");
                String branch = rs.getString("branch");
                if (driveId != null && branch != null) {
                    map.computeIfAbsent(driveId, k -> new ArrayList<>()).add(branch);
                }
            }
        } catch (SQLException ignored) {
        }
        return map;
    }

    private Map<String, List<DriveRequiredSkill>> loadRequiredSkills(Connection conn) {
        Map<String, List<DriveRequiredSkill>> map = new HashMap<>();
        String sql = "SELECT drive_id, skill_id, min_proficiency, is_mandatory FROM drive_required_skills";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String driveId = rs.getString("drive_id");
                if (driveId != null) {
                    DriveRequiredSkill r = new DriveRequiredSkill(
                            rs.getString("skill_id"),
                            rs.getInt("min_proficiency"),
                            rs.getBoolean("is_mandatory")
                    );
                    map.computeIfAbsent(driveId, k -> new ArrayList<>()).add(r);
                }
            }
        } catch (SQLException ignored) {
        }
        return map;
    }

    private PlacementDrive mapDrive(ResultSet rs) throws SQLException {
        PlacementDrive d = new PlacementDrive();
        d.setId(rs.getString("id"));
        d.setCompanyId(rs.getString("company_id"));
        d.setCompanyName(rs.getString("company_name"));
        d.setJobRole(rs.getString("job_role"));
        d.setJobDescription(safeString(rs, "job_description"));
        d.setSalaryPackageLpa(safeDouble(rs, "salary_package_lpa", 0));
        d.setMinCgpa(safeDouble(rs, "min_cgpa", 6.0));
        d.setMaxBacklogsAllowed(safeInt(rs, "max_backlogs_allowed", 0));
        d.setTargetGraduationYear(safeInt(rs, "target_graduation_year", 2026));
        Date dd = safeDate(rs, "drive_date");
        d.setDriveDate(dd != null ? dd.toString() : safeString(rs, "drive_date"));
        Date ad = safeDate(rs, "application_deadline");
        d.setApplicationDeadline(ad != null ? ad.toString() : safeString(rs, "application_deadline"));
        d.setAvailablePositions(safeInt(rs, "available_positions", 1));
        String status = safeString(rs, "status");
        d.setStatus(status != null && !status.isBlank() ? status : "OPEN");
        d.setSelectionProcess(safeString(rs, "selection_process"));
        Timestamp createdAt = safeTimestamp(rs, "created_at");
        if (createdAt != null) d.setCreatedAt(createdAt.toInstant().toString());
        return d;
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

    private static int safeInt(ResultSet rs, String col, int def) throws SQLException {
        return hasColumn(rs, col) ? rs.getInt(col) : def;
    }

    private static double safeDouble(ResultSet rs, String col, double def) throws SQLException {
        if (!hasColumn(rs, col)) return def;
        Object obj = rs.getObject(col);
        return obj == null ? def : ((Number) obj).doubleValue();
    }

    private static Date safeDate(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getDate(col) : null;
    }

    private static Timestamp safeTimestamp(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getTimestamp(col) : null;
    }
}
