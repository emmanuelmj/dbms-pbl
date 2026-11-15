package edu.gcet.placementhub.db;

import edu.gcet.placementhub.auth.PasswordUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB - Database Initializer
 * Automatically validates MySQL connection, verifies relational schema,
 * and ensures default administrative credentials exist safely.
 */
public class DatabaseInitializer {
    private static final Logger LOGGER = Logger.getLogger(DatabaseInitializer.class.getName());

    public static void initializeSchema() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            LOGGER.info("Connected to MySQL. Verifying schema for gcet_placement_hub...");

            // Execute schema.sql from classpath if available
            InputStream schemaStream = DatabaseInitializer.class.getResourceAsStream("/schema.sql");
            if (schemaStream != null) {
                executeSqlScript(conn, schemaStream);
            }

            // Ensure extended profile columns exist on pre-existing MySQL tables without deleting data
            ensureProfileColumns(conn);

            // 1. Seed top-level institutional administrators (Principal, Chairman, Vice Chairman)
            seedTopLevelAdministrators(conn);

            // 2. Seed default administrative & placement users if not present
            seedDefaultUsersIfEmpty(conn);

            // 3. Seed initial authorized faculty accounts
            seedInitialFaculty(conn);

            // 4. Seed default skills library if empty
            seedDefaultSkillsIfEmpty(conn);

            LOGGER.info("Database schema verification and initialization completed successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Database initialization warning: " + e.getMessage());
        }
    }

    private static void ensureProfileColumns(Connection conn) {
        safeAddColumn(conn, "students", "current_semester", "INT DEFAULT 7");
        safeAddColumn(conn, "students", "academic_year", "INT DEFAULT 4");
        safeAddColumn(conn, "students", "dob", "VARCHAR(20) NULL");
        safeAddColumn(conn, "students", "gender", "VARCHAR(30) NULL");
        safeAddColumn(conn, "students", "address", "TEXT NULL");
        safeAddColumn(conn, "students", "location", "VARCHAR(150) NULL");
        safeAddColumn(conn, "students", "about_me", "TEXT NULL");
        safeAddColumn(conn, "students", "technical_skills", "TEXT NULL");
        safeAddColumn(conn, "students", "programming_languages", "TEXT NULL");
        safeAddColumn(conn, "students", "frameworks_technologies", "TEXT NULL");
        safeAddColumn(conn, "students", "soft_skills", "TEXT NULL");
        safeAddColumn(conn, "students", "career_interests", "TEXT NULL");
        safeAddColumn(conn, "students", "preferred_job_roles", "TEXT NULL");

        safeAddColumn(conn, "faculty", "about_me", "TEXT NULL");
        safeAddColumn(conn, "faculty", "qualifications", "TEXT NULL");
        safeAddColumn(conn, "faculty", "areas_of_expertise", "TEXT NULL");
        safeAddColumn(conn, "faculty", "subjects_handled", "TEXT NULL");
        safeAddColumn(conn, "faculty", "research_interests", "TEXT NULL");
        safeAddColumn(conn, "faculty", "location", "VARCHAR(150) NULL");

        safeAddColumn(conn, "companies", "recruiter_name", "VARCHAR(120) NULL");
        safeAddColumn(conn, "companies", "recruiter_designation", "VARCHAR(120) NULL");
        safeAddColumn(conn, "companies", "company_logo", "MEDIUMTEXT NULL");
        safeAddColumn(conn, "companies", "linkedin_url", "VARCHAR(500) NULL");
    }

    private static void safeAddColumn(Connection conn, String table, String column, String definition) {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
                    }
                }
            }
        } catch (SQLException ignored) {
            // Safe ignore if already exists
        }
    }

    private static void executeSqlScript(Connection conn, InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             Statement stmt = conn.createStatement()) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--") || line.startsWith("//")) {
                    continue;
                }
                sb.append(line).append(" ");
                if (line.endsWith(";")) {
                    String query = sb.toString().trim();
                    if (!query.toUpperCase().startsWith("CREATE DATABASE") && !query.toUpperCase().startsWith("USE ")) {
                        try {
                            stmt.execute(query);
                        } catch (SQLException ex) {
                            LOGGER.fine("SQL execution note: " + ex.getMessage());
                        }
                    }
                    sb.setLength(0);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error reading schema.sql script", e);
        }
    }

    /**
     * Top-Level Administrators: Env-configured seeding & safe BCrypt verification
     * Principal: Dr. K. Sagar (Role: PRINCIPAL)
     * Vice Chairman: Mr. R. Harishchandra Reddy (Role: VICE_CHAIRMAN)
     * Chairman: Mr. G.R. Ravinder Reddy (Role: CHAIRMAN)
     */
    private static void seedTopLevelAdministrators(Connection conn) {
        ensureMandatoryAccount(conn, "principal.sagar", "principal@gcet.edu.in", "Dr. K. Sagar", "PRINCIPAL", "Principal's Office",
                "GCET_BOOTSTRAP_PRINCIPAL_PASS", null, false);
        ensureMandatoryAccount(conn, "vc.harishchandra", "vc@gcet.edu.in", "Mr. R. Harishchandra Reddy", "VICE_CHAIRMAN", "Executive Board",
                "GCET_BOOTSTRAP_VC_PASS", null, false);
        ensureMandatoryAccount(conn, "chairman.ravinder", "chairman@gcet.edu.in", "Mr. G.R. Ravinder Reddy", "CHAIRMAN", "Governing Body",
                "GCET_BOOTSTRAP_CHAIRMAN_PASS", null, false);
    }

    /**
     * Initial Authorized Faculty Accounts (preserving existing registered usernames):
     * 1. B. Laxmi Lavanya
     * 2. P. Aparna
     * 3. S. Raja Shakar Reddy
     */
    private static void seedInitialFaculty(Connection conn) {
        String uid1 = ensureMandatoryAccount(conn, "faculty_lavanya", "lavanya.cse@gcet.edu.in", "B. Laxmi Lavanya", "FACULTY", "CSE",
                "GCET_BOOTSTRAP_FACULTY_PASS", "%lavanya%", false);
        if (uid1 != null) {
            insertOrUpdateFaculty(conn, "GCET-FAC-01", "EMP-CSE-101", "B. Laxmi Lavanya", "CSE", "Associate Professor", "lavanya.cse@gcet.edu.in", "+91 98491 10001", uid1);
        }

        String uid2 = ensureMandatoryAccount(conn, "faculty_aparna", "aparna.ece@gcet.edu.in", "P. Aparna", "FACULTY", "CSE-CS",
                "GCET_BOOTSTRAP_FACULTY_PASS", "%aparna%", false);
        if (uid2 != null) {
            insertOrUpdateFaculty(conn, "GCET-FAC-02", "EMP-ECE-102", "P. Aparna", "CSE-CS", "Associate Professor", "aparna.ece@gcet.edu.in", "+91 98491 10002", uid2);
        }

        String uid3 = ensureMandatoryAccount(conn, "faculty_rajashakar", "rajashakar.cse@gcet.edu.in", "S. Raja Shakar Reddy", "FACULTY", "EEE",
                "GCET_BOOTSTRAP_FACULTY_PASS", "%raja%", false);
        if (uid3 != null) {
            insertOrUpdateFaculty(conn, "GCET-FAC-03", "EMP-CSE-103", "S. Raja Shakar Reddy", "EEE", "Assistant Professor", "rajashakar.cse@gcet.edu.in", "+91 98491 10003", uid3);
        }
    }

    private static void seedDefaultUsersIfEmpty(Connection conn) {
        ensureMandatoryAccount(conn, "admin", "admin@gcet.edu.in", "GCET System Administrator", "ADMIN", "Administration",
                "GCET_BOOTSTRAP_ADMIN_PASS", null, false);
        ensureMandatoryAccount(conn, "officer", "placements@gcet.edu.in", "Head - Training & Placements (CDC)", "PLACEMENT_OFFICER", "Placement Cell",
                "GCET_BOOTSTRAP_OFFICER_PASS", null, false);
    }

    private static void seedDefaultSkillsIfEmpty(Connection conn) {
        String countSql = "SELECT COUNT(*) FROM skills";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                LOGGER.info("Skills table is empty. Seeding standard GCET technical competency library...");

                String[][] defaultSkills = {
                    {"Java", "Programming Languages", "Core Java, OOPs, Collections, Multithreading, Lambdas"},
                    {"Python", "Programming Languages", "Python 3, Data Structures, OOPs, Standard Libraries"},
                    {"C++", "Programming Languages", "C++ 17/20, STL, Memory Management, Algorithms"},
                    {"Data Structures & Algorithms", "Data Structures and Algorithms", "Trees, Graphs, Dynamic Programming, Complexity Analysis"},
                    {"SQL & Relational DBs", "Database Management", "MySQL, PostgreSQL, Indexing, Joins, Transactions, Normalization"},
                    {"Spring Boot", "Web Development", "REST APIs, Spring Data JPA, Security, Microservices"},
                    {"React & TypeScript", "Web Development", "Hooks, State Management, Modern ES6+, Responsive UI"},
                    {"Machine Learning", "Artificial Intelligence", "Scikit-Learn, Regression, Classification, Model Evaluation"},
                    {"AWS Cloud Fundamentals", "Cloud Computing", "EC2, S3, IAM, Lambda, VPC essentials"},
                    {"Git & CI/CD", "DevOps & Tools", "Git workflows, GitHub Actions, Docker containers"},
                    {"Verilog & VLSI", "Core Engineering", "Digital System Design, RTL synthesis, FPGA prototyping"},
                    {"Embedded Systems & IoT", "Core Engineering", "ARM Microcontrollers, RTOS, Sensors, SPI/I2C protocols"}
                };

                String insertSql = "INSERT INTO skills (id, name, category, description, status) VALUES (?, ?, ?, ?, 'ACTIVE')";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (String[] sk : defaultSkills) {
                        ps.setString(1, UUID.randomUUID().toString());
                        ps.setString(2, sk[0]);
                        ps.setString(3, sk[1]);
                        ps.setString(4, sk[2]);
                        ps.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not seed default skills: " + e.getMessage());
        }
    }

    private static String ensureMandatoryAccount(
            Connection conn,
            String username,
            String email,
            String fullName,
            String role,
            String dept,
            String passwordEnvVar,
            String facultyNamePattern,
            boolean mustChangePassword
    ) {
        String configuredPass = getConfiguredPassword(passwordEnvVar);
        String findSql = "SELECT id, password_hash, salt, status, is_active FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?) LIMIT 1";
        String existingId = null;
        String existingHash = null;
        String existingSalt = null;
        String existingStatus = null;
        boolean existingActive = true;

        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, username);
            ps.setString(2, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existingId = rs.getString("id");
                    existingHash = rs.getString("password_hash");
                    existingSalt = rs.getString("salt");
                    existingStatus = rs.getString("status");
                    existingActive = rs.getBoolean("is_active");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error looking up mandatory user " + username + ": " + e.getMessage());
        }

        if (existingId == null && facultyNamePattern != null && "FACULTY".equals(role)) {
            String facLookup = "SELECT id, password_hash, salt, status, is_active FROM users WHERE role = 'FACULTY' AND LOWER(full_name) LIKE LOWER(?) LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(facLookup)) {
                ps.setString(1, facultyNamePattern);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getString("id");
                        existingHash = rs.getString("password_hash");
                        existingSalt = rs.getString("salt");
                        existingStatus = rs.getString("status");
                        existingActive = rs.getBoolean("is_active");
                    }
                }
            } catch (SQLException ignored) {
            }
        }

        if (existingId == null) {
            if (configuredPass == null || configuredPass.isEmpty()) {
                LOGGER.warning("Skipping creation of " + username + ": environment variable " + passwordEnvVar + " is not set.");
                return null;
            }
            return insertUser(conn, username, email, fullName, role, dept, null, configuredPass, mustChangePassword);
        }

        if (configuredPass != null && !configuredPass.isEmpty()) {
            boolean matches = existingHash != null && PasswordUtil.verifyPassword(configuredPass, existingSalt, existingHash);
            boolean isBcrypt = PasswordUtil.isBcryptHash(existingHash);
            if (!matches || !isBcrypt) {
                String newSalt = PasswordUtil.generateSalt();
                String newHash = PasswordUtil.hashPassword(configuredPass, newSalt);
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ?, salt = ? WHERE id = ?")) {
                    ps.setString(1, newHash);
                    ps.setString(2, newSalt);
                    ps.setString(3, existingId);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Failed updating BCrypt hash for " + username + ": " + e.getMessage());
                }
            }
        }

        if (!"ACTIVE".equalsIgnoreCase(existingStatus) || !existingActive) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET status = 'ACTIVE', is_active = TRUE WHERE id = ?")) {
                ps.setString(1, existingId);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        }

        return existingId;
    }

    private static String insertUser(Connection conn, String username, String email, String fullName, String role, String dept, String rollNo, String password, boolean mustChangePassword) {
        String sql = "INSERT INTO users (id, username, email, full_name, role, department, student_roll_number, password_hash, salt, status, is_active, must_change_password) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', TRUE, ?)";
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(password, salt);
        String id = UUID.randomUUID().toString();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, fullName);
            ps.setString(5, role);
            ps.setString(6, dept);
            ps.setString(7, rollNo);
            ps.setString(8, hash);
            ps.setString(9, salt);
            ps.setBoolean(10, mustChangePassword);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "User creation note for " + username + ": " + e.getMessage());
            return null;
        }
    }

    private static void insertOrUpdateFaculty(Connection conn, String facId, String empId, String name, String dept, String desig, String email, String phone, String userId) {
        String sql = "INSERT INTO faculty (id, faculty_id, employee_id, full_name, department, designation, email, phone, user_id, faculty_approved, admin_approved, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE, 'ACTIVE') " +
                     "ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), user_id = COALESCE(user_id, VALUES(user_id)), faculty_approved = TRUE, admin_approved = TRUE, status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, facId);
            ps.setString(3, empId);
            ps.setString(4, name);
            ps.setString(5, dept);
            ps.setString(6, desig);
            ps.setString(7, email);
            ps.setString(8, phone);
            ps.setString(9, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Faculty creation note: " + e.getMessage());
        }
    }

    private static String getConfiguredPassword(String key) {
        String v = System.getenv(key);
        if (v != null && !v.trim().isEmpty()) {
            return v.trim();
        }
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.isRegularFile(envPath)) {
                for (String raw : java.nio.file.Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String k = line.substring(0, eq).trim();
                    if (!k.equals(key)) continue;
                    String val = line.substring(eq + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) val = val.substring(1, val.length() - 1);
                    }
                    if (!val.trim().isEmpty()) return val.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
