# GCET PLACEMENT HUB — Java 17 Swing Desktop Edition

Complete desktop edition of the **Geethanjali College of Engineering and Technology (UGC Autonomous)** *Smart Placement Cell & Skill Matrix Portal*, engineered in **Java 17**, **Java Swing**, **FlatLaf 3.5.4**, **JFreeChart 1.5.5**, **OpenPDF 1.3.43**, and **MySQL Connector/J 8.4.0** via direct **JDBC** connectivity to your local `gcet_placement_hub` MySQL 8 database.

> **Important:** This Maven project (`gcet-placement-hub-java/`) is completely independent and does not alter the original React/TypeScript/Node.js/Express codebase. Never `DROP`, `TRUNCATE`, or reset `gcet_placement_hub`—all DAOs perform non-destructive reads/writes directly against the existing 17 MySQL tables.

---

## 1. Prerequisites on Windows

Install and verify the following on your Windows laptop:

1. **Java JDK 17+** (Eclipse Temurin / Oracle / Microsoft OpenJDK 17+)
2. **Apache Maven 3.8+**
3. **MySQL Server 8.x** running locally on `127.0.0.1:3306` with schema `gcet_placement_hub` populated

Run these PowerShell commands to verify prerequisites:

```powershell
java -version
mvn -version
Get-Service -Name "*MySQL*"
```

---

## 2. Configure Database Credentials Securely

No database passwords or application user passwords are hardcoded in Java source code. Keep MySQL bound to `127.0.0.1` (do **not** expose port `3306` publicly).

In PowerShell inside `gcet-placement-hub-java\`:

```powershell
Copy-Item .env.example .env
notepad .env
```

Set your local MySQL credentials inside `.env` (or use `config.properties` copied from `config.properties.example`, or system environment variables):

```env
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=gcet_placement_hub
DB_USER=root
DB_PASSWORD=your_actual_local_mysql_password
```

Alternatively, you can configure or test runtime JDBC settings directly inside the application via the **MySQL Connection Settings** modal on the Login / Header bar (and optionally click **Save Settings to .env**).

---

## 3. Build, Run & Package Commands (Windows PowerShell)

From inside `gcet-placement-hub-java\`:

```powershell
# 1. Clean & compile Java 17 sources + copy resources
mvn clean compile

# 2. Launch directly via Maven
mvn exec:java

# 3. Package standalone executable shaded fat JAR (excludes signed META-INF/*.SF|DSA|RSA digests)
mvn clean package

# 4. Run the standalone executable fat JAR
java -jar target\gcet-placement-hub-java.jar
```

You can also use the included helper scripts:

- **PowerShell:** `.\run-windows.ps1` (auto-checks Java 17+, Maven, `.env`, compiles, and runs)
- **Command Prompt / Double-click:** `run-windows.bat`

---

## 4. Verify All 17 Existing MySQL Tables (Read-Only)

Run in MySQL CLI or MySQL Workbench, or inspect live inside the app under **DB Diagnostics**:

```sql
USE gcet_placement_hub;
SHOW TABLES;
```

Expected 17 tables:
1. `users`
2. `students`
3. `faculty`
4. `skills`
5. `student_skills`
6. `companies`
7. `placement_drives`
8. `applications`
9. `approval_records`
10. `audit_logs`
11. `student_projects`
12. `student_certifications`
13. `student_internships`
14. `drive_eligible_branches`
15. `drive_required_skills`
16. `application_stage_history`
17. `admin_requests`

---

## 5. Authentication & Safe Password Recovery

- **Students:** Sign in with your registered **Roll Number** (e.g., `22R11A0501`) under **STUDENTS** (`PENDING_APPROVAL` / `REJECTED` accounts are blocked until approved by Department Faculty Coordinator).
- **Faculty / TPO:** Sign in with Faculty ID, username, or email under **FACULTY**.
- **Recruiters / Companies:** Sign in with corporate email or username under **RECRUITERS**.
- **Administrators & Leadership:** Sign in under **ADMIN** (`PRINCIPAL`, `CHAIRMAN`, `VICE_CHAIRMAN`, `ADMIN`).
- **Supported Hashes:** `PasswordHasher` verifies BCrypt (`$2a$` / `$2b$` / `$2y$`), legacy Node `scrypt$` (`N=16384, r=8, p=1, dkLen=64`), `PBKDF2WithHmacSHA256`, and salted SHA-256 hex without modifying existing password hashes automatically.
- **Safe Recovery:** If you forgot a password for an existing account in `gcet_placement_hub.users`, click **Forgot / Set Password for an Account I Own** on the Sign In screen. It requires BOTH matching `users.email` AND valid local MySQL Server authentication before hashing a new BCrypt (`$2b$`, cost 12) password, while preserving all approval and activation gates.

---

## 6. Complete Module Inventory

- **Welcome & Auth Portal:** Public GCET Welcome portal, 4-role Login tabs, self-service Student/Faculty/Company registrations with approval receipts, MySQL-verified password recovery.
- **Executive & Role Dashboards:** Role-tailored analytics cards, JFreeChart placement charts, active drives overview, live MySQL KPIs.
- **Campus Connect & Student Directory:** GCTC news/connect portal, Student Directory with departmental search/filtering, Individual Student Profiles (Skills, Projects, Certifications, Internships), Skill Matrix proficiency verification, and Faculty Directory.
- **Placement Management:** Company Directory & Profiles, Placement Drive CRUD with eligible branches & required skills, Eligibility Checker with itemized breakdown, Skill Gap Analysis with training plans, Company-Student Matching, and 10-stage Application Pipeline (`application_stage_history`).
- **Governance, Reports & Settings:** RBAC-enforced Approval Center (Department-scoped Faculty approval for students; Admin approval for Faculty/Companies; Principal-only approval for Admin privilege requests), Reports Center with CSV & GCET-branded OpenPDF exports, Read-only 17-table DB Diagnostics, Scoped Audit Logs, and Light/Dark FlatLaf Settings.
