# GCET Placement Hub — MongoDB Atlas Edition

### Smart Placement Cell, Automated Eligibility Engine & Skill Matrix Portal
**Geethanjali College of Engineering and Technology (GCET Hyderabad — UGC Autonomous, Accredited by NAAC A+ & NBA)**  
**DBMS PBL Project:** High-Performance Cloud Document Database Architecture

A cross-platform Java 17 desktop system built with **Java Swing**, **FlatLaf 3.5.4**, and the official **MongoDB Java Driver 5.x**. Designed to run seamlessly on both **macOS (Retina/HiDPI)** and **Windows (10/11)** against a shared **MongoDB Atlas Cloud** cluster (with a resilient local MongoDB Community fallback).

---

## 🏛️ System Architecture

```mermaid
flowchart TD
    subgraph Client["Java 17 Desktop Client (Windows 11 & macOS Retina)"]
        SWING["Swing Desktop UI (FlatLaf Modern Theme)"]
        DAO["DAO Layer (BSON Serialization)"]
        DBCONN["DatabaseConnection.java (Singleton Engine)"]
        SWING --> DAO
        DAO --> DBCONN
    end

    subgraph Atlas["MongoDB Atlas Cloud (Engine 8.0.37)"]
        SRV["DNS SRV Resolution (mongodb+srv://)"]
        REPLICA["3-Node High-Availability Replica Set"]
        SRV --> REPLICA
    end

    subgraph Fallback["Offline Resiliency"]
        LOCAL["Local MongoDB Community Server (localhost:27017)"]
    end

    DBCONN -->|1. Primary: Cloud TLS 1.3 Handshake| SRV
    DBCONN -.->|2. Automatic Fallback on Timeout| LOCAL
```

---

## 📊 Live MongoDB Document Schema (Mermaid ER Diagram)

```mermaid
erDiagram
    USERS ||--o| STUDENTS : "1:1 Reference (userId)"
    USERS ||--o| FACULTY : "1:1 Reference (userId)"
    USERS ||--o{ AUDIT_LOGS : "1:N Reference (actorId)"
    COMPANIES ||--o{ PLACEMENT_DRIVES : "1:N Reference (companyId)"
    STUDENTS ||--o{ APPLICATIONS : "1:N Reference (studentId)"
    PLACEMENT_DRIVES ||--o{ APPLICATIONS : "1:N Reference (driveId)"
    COMPANIES ||--o{ APPLICATIONS : "1:N Reference (companyId)"
    USERS ||--o{ APPROVALS : "1:N Reference (approverId)"

    STUDENTS {
        ObjectId _id PK
        string rollNumber UK "Unique JNTUH / GCET Roll"
        string fullName
        string email UK "Institutional Email"
        string branch "CSE, IT, ECE, CSE-AIML, etc."
        object academicMetrics "Embedded: cgpa, backlogs, graduationYear"
        array skills "Embedded: skillId, name, rating, verifiedByFaculty"
        array projects "Embedded: title, technologies, githubUrl"
        array certifications "Embedded: certificateName, issuingBody, issueDate"
        array internships "Embedded: company, role, durationMonths"
        string approvalStatus "APPROVED / PENDING / REJECTED"
        string placementStatus "PLACED / UNPLACED / SHORTLISTED"
    }

    PLACEMENT_DRIVES {
        ObjectId _id PK
        ObjectId companyId FK "Reference -> companies._id"
        string companyName "Denormalized for instant UI reads"
        string jobRole
        double ctcLpa "Annual Package"
        object eligibilityCriteria "Embedded: minCgpa, maxBacklogs, allowedBranches[]"
        array requiredSkills "Embedded: skillId, name, minRating, mandatory"
        string status "OPEN / UPCOMING / COMPLETED"
        date driveDate
    }

    APPLICATIONS {
        ObjectId _id PK
        ObjectId studentId FK "Reference -> students._id"
        ObjectId driveId FK "Reference -> placement_drives._id"
        ObjectId companyId FK "Reference -> companies._id"
        string studentRollNumber "Denormalized"
        string studentName "Denormalized"
        string companyName "Denormalized"
        string currentStage "APPLIED / SHORTLISTED / INTERVIEW / OFFERED"
        string status "SUBMITTED / IN_PROGRESS / SELECTED / REJECTED"
        array stageHistory "Embedded Event Trail: stage, updatedBy, enteredAt, notes"
    }

    USERS {
        ObjectId _id PK
        string username UK "Unique Login Identifier"
        string email UK "Unique Email Address"
        string role "PRINCIPAL / TPO / FACULTY / STUDENT / COMPANY"
        string passwordHash "BCrypt Hashed"
        boolean isActive
    }

    COMPANIES {
        ObjectId _id PK
        string companyName UK "Unique Corporate Partner Name"
        string industry
        string tier "TIER_1_DREAM / SUPER_DREAM / CORE"
        object hrContact "Embedded: name, email, phone"
        string description "Full-Text Indexed"
    }
```

---

## ⚡ 3-Step Quickstart (Mac & Windows)

### 🍏 On macOS (Teammate Setup)
```bash
# 1. Clone repository & enter the Java project directory
git clone https://github.com/emmanuelmj/dbms-pbl.git
cd dbms-pbl/gcet-placement-hub-java

# 2. Configure .env with your shared MongoDB Atlas URI
cp .env.example .env
open -e .env

# 3. Launch via the automated macOS script
chmod +x run-mac.sh
./run-mac.sh
```
*(Automatically verifies Java 17+, copies `.env`, sets Apple Metal Retina flags, and launches via the bundled Maven Wrapper `./mvnw`).*

---

### 🪟 On Windows (Command Prompt / PowerShell)
```cmd
:: 1. Navigate to gcet-placement-hub-java
cd gcet-placement-hub-java

:: 2. Configure .env
copy .env.example .env
notepad .env

:: 3. Launch application
run-windows.bat
```
*(Or double-click `run-windows.bat` directly in Windows File Explorer).*

---

## 🔬 In-App DBMS Faculty Evaluation Console

The application includes a built-in **DBMS Faculty Console** accessible in 1 click from the sidebar (**⚡ DBMS Faculty Console**) or by clicking the top database status badge:

1. **⚡ Live Cloud Write-and-Read Persistence Probe:**
   - Performs a real-time write into MongoDB Atlas `audit_logs` and reads it back immediately over the network.
   - Displays round-trip write latency (**33 ms**), read latency (**31 ms**), and the saved BSON document.
2. **🛡️ Server-Side $jsonSchema Violation Rejection:**
   - Attempts to insert an invalid student document (CGPA `14.5`, negative backlogs, invalid email regex pattern).
   - Demonstrates database-level schema enforcement returning **Error Code 121 (`DocumentValidationFailure`)**.
3. **Tab 1 — Collections & Indexes:** Live document counts, storage stats, and 49 active indexes.
4. **Tab 2 — Live BSON Samples:** Compare embedded documents (`students.skills[]`, `applications.stageHistory[]`) against referenced collections.
5. **Tab 3 — Aggregation & EXPLAIN:** Run 5 real multi-stage aggregation pipelines and generate `.explain("executionStats")` plans showing `IXSCAN` index hits.
6. **Tab 4 — $jsonSchema Rules:** Programmatic validation rules enforced on collections.
7. **Tab 5 — Custom Query Console:** Select query presets or write custom MongoDB JSON filters, execute find queries live, and inspect the execution plan.

---

## 🔑 Default GCET Hyderabad Demo Credentials

On first launch against an empty database, `MongoSeedData` auto-populates realistic GCET records. All demo accounts use the password: **`Password@123`**:

| Portal / Sign-In Tab | Username | Role | Person |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `principal.sagar` | PRINCIPAL | Dr. S. Udaya Kumar (Principal) |
| **ADMIN** | `chairman.gcet` | CHAIRMAN | Sri G. R. Ravinder Reddy |
| **ADMIN / FACULTY** | `tpo.gcet` | PLACEMENT_OFFICER | Dr. K. Srinivas (TPO) |
| **FACULTY** | `faculty_lavanya` | FACULTY (CSE, AIML, DS) | B. Laxmi Lavanya |
| **FACULTY** | `faculty_aparna` | FACULTY (IT, Cyber Security) | P. Aparna |
| **FACULTY** | `faculty_rajashakar` | FACULTY (ECE, EEE, MECH, CIVIL) | S. Raja Shakar Reddy |
| **STUDENTS** | `22R11A0501` | STUDENT (Placed at Amazon) | Akhil Reddy Gaddam |
| **STUDENTS** | `22R11A6702` | STUDENT (Eligible for Deloitte) | Sahithi Rao Vuppala |
| **RECRUITERS** | `recruiter_amazon` | COMPANY | Amazon AWS Recruiter |
| **RECRUITERS** | `recruiter_infosys`, `recruiter_deloitte`, `recruiter_tataconsultancy`, … | COMPANY | Seeded Corporate Recruiters |

---

## 📚 Comprehensive Documentation Links

- **[MONGODB_ARCHITECTURE.md](MONGODB_ARCHITECTURE.md):** Complete technical report with connection pooling, transparent fallback, Mermaid ER/class diagrams, indexing strategy, and aggregation pipelines.
- **[DBMS_VIVA_QUERIES.md](DBMS_VIVA_QUERIES.md):** Standalone query pack with copy-pasteable `mongosh` / Atlas Data Explorer queries and viva Q&A talking points.
- **[SETUP_AND_RUN.md](SETUP_AND_RUN.md):** Step-by-step setup guide for fresh macOS and Windows laptops with zero pre-installed dependencies.
