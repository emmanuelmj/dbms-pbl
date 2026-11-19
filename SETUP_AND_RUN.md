# GCET Placement Hub — Setup & Run Guide (Zero Pre-Installed Dependencies)

This guide is for a teammate starting from a **completely fresh macOS or Windows laptop**: no Java, no Maven, no Git, no MongoDB. Follow it top to bottom and the GCET Placement Hub desktop app will be running on the shared MongoDB Atlas data in about 10 minutes (most of that is downloads).

> **What you do NOT need to install**
>
> - **Apache Maven** – the repository bundles the Maven Wrapper (`mvnw` / `mvnw.cmd`). The first run downloads Maven 3.9.9 into `~/.m2/wrapper` automatically.
> - **MongoDB Server** – the app connects straight to our shared **MongoDB Atlas** cluster through `MONGO_URI`. A local MongoDB is only an optional fallback.
>
> You only need **Git** and a **Java 17+ JDK**.

---

## Part 1 — Installing Prerequisites from Scratch

### macOS (Apple Silicon or Intel)

1. **Xcode Command Line Tools** (gives you `git`):
   ```bash
   xcode-select --install
   ```
   Click *Install* in the dialog and wait for it to finish.

2. **Homebrew** (package manager), if you do not have it:
   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```
   At the end Homebrew prints two `echo ... >> ~/.zprofile` lines. Run them, then open a new Terminal.

3. **Java 17 JDK + Git**:
   ```bash
   brew install openjdk@17 git
   ```

4. **Make Java visible to the system and your shell** (one-time):
   ```bash
   sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
   echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zprofile
   echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zprofile
   source ~/.zprofile
   ```
   On an **Intel** Mac the Homebrew path is `/usr/local/opt/openjdk@17/...` instead of `/opt/homebrew/opt/openjdk@17/...`.

5. **Verify**:
   ```bash
   java -version     # openjdk version "17.x"
   git --version
   ```

### Windows 10 / 11

1. **Git for Windows** and **Java 17 JDK (Eclipse Temurin)** with winget (open *PowerShell* or *Terminal*):
   ```powershell
   winget install --id Git.Git -e --source winget
   winget install --id EclipseAdoptium.Temurin.17.JDK -e --source winget
   ```
   If winget is unavailable, download the installers instead:
   - Git: https://git-scm.com/download/win
   - Temurin 17 JDK (`.msi`): https://adoptium.net/temurin/releases/?version=17 — during install enable **"Set JAVA_HOME variable"** and **"Add to PATH"**.

2. **Set `JAVA_HOME` / `PATH` manually only if the installer did not** (run in PowerShell, then open a *new* window):
   ```powershell
   $jdk = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-17*")[0].FullName
   [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk, "User")
   [Environment]::SetEnvironmentVariable("Path", "$jdk\bin;" + [Environment]::GetEnvironmentVariable("Path", "User"), "User")
   ```

3. **Verify** (new PowerShell window):
   ```powershell
   java -version     # openjdk version "17.x"
   git --version
   ```

---

## Part 2 — Cloning the Repository & Configuring `.env`

1. **Clone**:
   ```bash
   git clone https://github.com/emmanuelmj/dbms-pbl.git
   cd dbms-pbl/gcet-placement-hub-java
   ```

2. **Create your private `.env` from the template** — `.env` is git-ignored and must never be committed:
   ```bash
   cp .env.example .env            # macOS / Linux
   copy .env.example .env          # Windows (cmd)
   Copy-Item .env.example .env     # Windows (PowerShell)
   ```

3. **Paste the shared Atlas connection string** (sent to you privately by the team lead — never in the repo, never in chat screenshots) into `.env`:
   ```dotenv
   MONGO_URI="mongodb+srv://<username>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority&appName=GCETPlacementHub"
   MONGO_DB_NAME="gcet_placement_hub"
   MONGO_LOCAL_FALLBACK="true"
   GCET_DEMO_PASSWORD="Password@123"
   ```
   - If your Atlas password contains `@ : / ? # %`, URL-encode it (for example `@` becomes `%40`).
   - Leave `MONGO_URI="mongodb://localhost:27017"` only if you intentionally want a local MongoDB.

The app reads `.env` from `gcet-placement-hub-java/`, from the repository root, or from real environment variables (`MONGO_URI`, `MONGO_DB_NAME`) — identical behaviour on macOS and Windows.

---

## Part 3 — Running the App (One-Command Launch)

### macOS
```bash
cd gcet-placement-hub11/gcet-placement-hub-java
chmod +x run-mac.sh mvnw
./run-mac.sh
```
`run-mac.sh` checks Java 17+, creates `.env` from `.env.example` if it is missing, enables Retina/HiDPI rendering and runs `./mvnw clean compile exec:java`. You can also run `./run-mac.sh` from the repository root.

### Windows
```powershell
cd gcet-placement-hub11\gcet-placement-hub-java
.\run-windows.bat          # or double-click it in Explorer; PowerShell users can use .\run-windows.ps1
```

**First launch takes 2–5 minutes** (Maven 3.9.9 + libraries download once into `~/.m2`). Later launches take ~20 seconds.

On the first connection to an empty database the app automatically:
1. applies the `$jsonSchema` validators and 49 indexes (`MongoSchemaInitializer`),
2. seeds the GCET demo data (`MongoSeedData`): 32 students, 12 companies, 12 drives, 78 applications, faculty, recruiters, approvals, announcements and audit logs.

Seeding is idempotent: whoever launches first populates the shared Atlas cluster; everyone else simply sees the same data.

### Optional: headless verification before the presentation
```bash
./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=edu.gcet.placementhub.SmokeTest
```
Runs 60 checks (connection, validators, indexes, seed idempotency, every DAO, logins, all five aggregation pipelines with `explain`, and a write round-trip) and exits with code 0 when everything passes. It never drops data on Atlas.

---

## Part 4 — Demo Login Credentials & Troubleshooting

### Demo accounts (seeded by `MongoSeedData.java`)

All accounts use the password from `GCET_DEMO_PASSWORD` in `.env` — **`Password@123`** when you copied `.env.example`. (If `.env` is missing entirely the built-in fallback is `Gcet@2025`; role-specific overrides such as `GCET_BOOTSTRAP_PRINCIPAL_PASS` win when set.)

| Sign-in tab | Username | Role | Person |
| :--- | :--- | :--- | :--- |
| ADMIN | `principal.sagar` | PRINCIPAL | Dr. S. Udaya Kumar |
| ADMIN | `chairman.gcet` | CHAIRMAN | Sri G. R. Ravinder Reddy |
| ADMIN | `vicechairman.gcet` | VICE_CHAIRMAN | Sri G. Harishchandra Reddy |
| ADMIN | `placement.admin` | ADMIN | Placement Administration Office |
| ADMIN or FACULTY | `tpo.gcet` | PLACEMENT_OFFICER | Dr. K. Srinivas (TPO) |
| FACULTY | `faculty_lavanya` | FACULTY (CSE, AIML, DS) | B. Laxmi Lavanya |
| FACULTY | `faculty_aparna` | FACULTY (IT, Cyber Security) | P. Aparna |
| FACULTY | `faculty_rajashakar` | FACULTY (ECE, EEE, MECH, CIVIL) | S. Raja Shakar Reddy |
| STUDENTS | `22R11A0501` | STUDENT (CSE, placed at Amazon) | Akhil Reddy Gaddam |
| STUDENTS | `22R11A1204` | STUDENT (IT, unplaced, strong profile) | Lahari Devi Thota |
| STUDENTS | `22R11A6702` | STUDENT (CSE-DS, eligible for Deloitte) | Sahithi Rao Vuppala |
| STUDENTS | `22R11A0402` | STUDENT (ECE, eligible for ZF India) | Varsha Reddy Kota |
| RECRUITERS | `recruiter_amazon` | COMPANY | Sneha Kulkarni (Amazon) |
| RECRUITERS | `recruiter_infosys` | COMPANY | Rahul Nair (Infosys) |
| RECRUITERS | `recruiter_tataconsultancy` | COMPANY | Meena Iyer (TCS) |
| RECRUITERS | `recruiter_<company>` | COMPANY | one per seeded company (`recruiter_wipro`, `recruiter_deloitte`, `recruiter_microsoft`, `recruiter_zfindia`, `recruiter_cyient`, `recruiter_capgemini`, `recruiter_cognizant`, `recruiter_accenture`, `recruiter_techmahindra`) |

Useful demo scenarios already in the data:
- **Pending approvals**: students `23R11A0511` and `23R11A1212` (approve as `faculty_lavanya` / `faculty_aparna`), faculty `M. Srikanth` (approve as an ADMIN), one pending Admin-privilege request (decide as `principal.sagar`).
- **Live application**: `22R11A6702` can apply to Deloitte, `22R11A0402` to ZF India, `22R11A0505` to Tech Mahindra; Amazon / Microsoft show skill gaps for most students.
- **Live Schema & Aggregation Inspector**: sidebar → *DB Diagnostics* → tabs *Collections & Indexes*, *Live BSON Samples*, *Aggregation & EXPLAIN* (5 pipelines with `.explain("executionStats")`), *$jsonSchema Rules*.

### Troubleshooting

| Symptom | Fix |
| :--- | :--- |
| Startup dialog **"Cannot reach MongoDB Atlas"** / `timed out while selecting a server` | Atlas → **Network Access** → *Add IP Address* → **Allow access from anywhere (0.0.0.0/0)**. College Wi-Fi often blocks port 27017: switch to a phone hotspot. Click **Retry Connection** in the dialog. |
| `authentication failed` | The user/password in `MONGO_URI` is wrong or not URL-encoded. Check Atlas → **Database Access**, then update `.env` or use the top-bar *Connection Settings* dialog → *Save Settings to .env*. |
| `DNS/SRV lookup failed` | No internet, a typo in the cluster host, or a DNS server that blocks SRV records; try hotspot / `8.8.8.8` DNS. |
| App starts but says **Local MongoDB Community (fallback)** | Atlas was unreachable and `MONGO_LOCAL_FALLBACK=true` switched to `localhost:27017`. Fix the Atlas URI and press *Retry* so both laptops share the same data. |
| `java: command not found` / `JAVA_HOME is not defined` (macOS) | Re-run step 4 of Part 1 and open a new Terminal; check with `echo $JAVA_HOME`. |
| `'java' is not recognized` (Windows) | Open a **new** PowerShell window after installing the JDK, or set `JAVA_HOME`/`PATH` as in Part 1 step 2. |
| macOS: **"run-mac.sh cannot be opened because it is from an unidentified developer"** or *Permission denied* | Run `chmod +x run-mac.sh mvnw` and start it from Terminal (`./run-mac.sh`), or `xattr -d com.apple.quarantine run-mac.sh mvnw`. Allow Java network access if the macOS firewall prompts. |
| macOS: Terminal asks for permission to access *Desktop/Documents* | Click *OK*; Maven needs to read the project folder. |
| Build error `Java 17 required` / `release version 17 not supported` | You are running Java 8 or 11. Install JDK 17+ (Part 1) and make sure `java -version` reports 17 or newer. |
| Very slow first launch | Normal: the wrapper downloads Maven and ~15 MB of libraries once. Subsequent launches are fast. |
| Blurry text / clipped layout on a Retina display | Already handled (`Main.java` sets the macOS rendering flags). If it persists run with `JAVA_TOOL_OPTIONS="-Dsun.java2d.uiScale=2"`. |
| "Document failed validation" when adding data | The server-side `$jsonSchema` rejected the input (for example CGPA outside 0–10 or proficiency outside 1–5). Enter valid values. |

### Sharing the `.env` safely before the presentation
- Send the Atlas URI through a private channel (personal message, password manager share, or AirDrop), never by committing it or pasting it in a public group.
- Both laptops should use the **same `MONGO_URI` and `MONGO_DB_NAME=gcet_placement_hub`** so they see identical seeded data.
- Keep `GCET_DEMO_PASSWORD` identical on both laptops; it only matters for the laptop that seeds first (passwords are stored as BCrypt hashes).

More detail on the document model, validators, indexes and pipelines: [MONGODB_ARCHITECTURE.md](MONGODB_ARCHITECTURE.md).
