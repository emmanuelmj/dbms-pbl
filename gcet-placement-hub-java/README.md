# GCET PLACEMENT HUB — Java 17 Desktop (MongoDB Edition)

**Geethanjali College of Engineering and Technology (UGC Autonomous, Accredited by NAAC A+ & NBA)**  
*Smart Placement Cell, Automated Eligibility Engine & Skill Matrix Portal*

A cross-platform Java 17 desktop system built with **Java Swing**, **FlatLaf 3.5.4**, **JFreeChart 1.5.5**, **OpenPDF**, and the official **MongoDB Java Driver 5.x**. Designed to run seamlessly on both **macOS (Retina/HiDPI)** and **Windows (10/11)** against a shared **MongoDB Atlas** cloud cluster (or local MongoDB Community fallback).

---

## ⚡ 3-Step Quickstart (Mac & Windows)

### 🍏 On macOS (Teammate Setup)

```bash
# 1. Clone repository & enter the Java project directory
git clone https://github.com/your-username/gcet-placement-hub.git
cd gcet-placement-hub/gcet-placement-hub-java

# 2. Configure MongoDB Atlas URI (or keep default local MongoDB)
cp .env.example .env
# Edit .env in nano/TextEdit to paste your MongoDB Atlas URI:
# MONGO_URI="mongodb+srv://<username>:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority"
# MONGO_DB_NAME="gcet_placement_hub"

# 3. Launch via the automated macOS script (checks Java 17+, sets Retina flags, runs mvnw)
chmod +x run-mac.sh
./run-mac.sh
```

*(You can also run `./run-mac.sh` directly from the repository root).*

---

### 🪟 On Windows (Command Prompt / PowerShell)

```powershell
# 1. Clone repository & navigate to gcet-placement-hub-java
cd gcet-placement-hub-java

# 2. Configure .env
copy .env.example .env
notepad .env

# 3. Run the automated Windows launcher (or double-click run-windows.bat)
.\run-windows.bat
# Or via PowerShell:
.\run-windows.ps1
```

---

## 🔑 Default GCET Hyderabad Demo Credentials

On first launch, if the database is empty, the application automatically initializes `$jsonSchema` validation rules, creates high-performance indexes, and seeds realistic GCET Hyderabad student, faculty, and company records.

All demo accounts use the password from `GCET_DEMO_PASSWORD` in `.env` (**`Password@123`** when you copied `.env.example`; built-in fallback `Gcet@2025` if no `.env` exists).

| Sign-in tab | Username | Role | Person |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `principal.sagar` | PRINCIPAL | Dr. S. Udaya Kumar |
| **ADMIN** | `chairman.gcet` | CHAIRMAN | Sri G. R. Ravinder Reddy |
| **ADMIN** | `vicechairman.gcet` | VICE_CHAIRMAN | Sri G. Harishchandra Reddy |
| **ADMIN** | `placement.admin` | ADMIN | Placement Administration Office |
| **ADMIN / FACULTY** | `tpo.gcet` | PLACEMENT_OFFICER | Dr. K. Srinivas (TPO) |
| **FACULTY** | `faculty_lavanya` | FACULTY (CSE, AIML, DS) | B. Laxmi Lavanya |
| **FACULTY** | `faculty_aparna` | FACULTY (IT, Cyber Security) | P. Aparna |
| **FACULTY** | `faculty_rajashakar` | FACULTY (ECE, EEE, MECH, CIVIL) | S. Raja Shakar Reddy |
| **STUDENTS** | `22R11A0501` | STUDENT (CSE, placed at Amazon) | Akhil Reddy Gaddam |
| **STUDENTS** | `22R11A6702` | STUDENT (CSE-DS, can apply to Deloitte live) | Sahithi Rao Vuppala |
| **RECRUITERS** | `recruiter_amazon`, `recruiter_infosys`, `recruiter_tataconsultancy`, … | COMPANY | one `recruiter_<company>` account per seeded company |

The complete list of accounts and demo scenarios (pending approvals, live applications, skill-gap examples) is in [SETUP_AND_RUN.md](../SETUP_AND_RUN.md).

---

## 🏗️ Cross-Platform Architecture & Tooling

1. **Self-Contained Maven Wrapper (`./mvnw` / `mvnw.cmd`):**
   - Teammates do not need to install Apache Maven on their system.
   - macOS: `./mvnw clean compile exec:java`
   - Windows: `.\mvnw.cmd clean compile exec:java`
2. **Cloud-First with Resilient Local Fallback:**
   - Detects `MONGO_URI` from `.env`, `config.properties`, or system environment variables.
   - If unset or unreachable, seamlessly falls back to `mongodb://localhost:27017` with informative diagnostics.
3. **Retina & HiDPI Aware:**
   - Automatically injects `-Dapple.laf.useScreenMenuBar=true` and `-Dapple.awt.application.appearance=system` on macOS.
   - Built on FlatLaf modern UI tokens for native dark/light theme switching.
4. **POSIX-Safe File Handling:**
   - Zero hardcoded Windows backslashes (`\`); all path resolutions strictly use `Paths.get(...)` and `File.separator`.

---

## 🔬 Live Schema & Aggregation Inspector (DBMS Evaluation)

The desktop application includes a dedicated **Live Schema & Aggregation Inspector** (`Settings` -> `Database Diagnostics` or top-right `● MongoDB: Atlas` status pill):

- **Tab 1: Collections & Indexes:** Live document counts, storage sizes, and full BSON index metadata (unique, compound, multikey, text).
- **Tab 2: Live BSON Samples:** Real-time query inspector comparing embedded documents (`students`, `placement_drives`, `applications`) against referenced collections.
- **Tab 3: Aggregation & EXPLAIN:** Live execution of five real pipelines — branch analytics (`$group`), drive eligibility (`$match`/`$project`), skill-gap (`$setIntersection`/`$setDifference`/`$lookup`), CTC distribution (`$bucket`) and the application funnel (`$facet`/`$unwind`) — each with `.explain("executionStats")` index metrics. The JSON shown is rendered from the exact pipeline the Java code sends.
- **Tab 4: $jsonSchema Rules:** Live inspection of server-enforced JSON validation contracts ensuring zero data anomalies.

Headless check of the same features (60 assertions, exit code 0 on success):

```bash
./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=edu.gcet.placementhub.SmokeTest
```

For the comprehensive theoretical and architectural evaluation submitted for our DBMS review, see [MONGODB_ARCHITECTURE.md](../MONGODB_ARCHITECTURE.md); for a from-scratch machine setup see [SETUP_AND_RUN.md](../SETUP_AND_RUN.md).

---

## 🛠️ Build & Package Commands

```bash
# Clean & compile all Java 17 sources
./mvnw clean compile

# Launch GUI application
./mvnw exec:java

# Build standalone shaded executable fat JAR
./mvnw clean package

# Run shaded JAR directly
java -jar target/gcet-placement-hub-java.jar
```

---

## ⚠️ MongoDB Atlas Checklist for Teams

If connecting both macOS and Windows teammates to a shared MongoDB Atlas cluster:
1. **IP Access List:** Ensure your Atlas Network Access has `0.0.0.0/0` (Allow Access from Anywhere) enabled for presentation testing.
2. **Database User:** Verify the database user has `readWriteAnyDatabase` or `dbAdmin` privileges on `gcet_placement_hub`.
3. **SRV Connection String:** Ensure standard URI format: `mongodb+srv://<user>:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority`.
