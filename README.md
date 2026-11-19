# GCET Placement Hub

### Smart Placement Cell & Skill Matrix Portal

A placement management system designed for Geethanjali College of Engineering and Technology (GCET) to connect students, faculty, administrators and recruiting companies through a centralized platform.

## About the Project

GCET Placement Hub aims to simplify placement activities by providing a centralized system for student profile management, faculty approvals, company coordination, placement drives and skill tracking.

## Key Modules

- **Student Management:** Student profiles, academic details, skills, projects and certifications.
- **Faculty Management:** Faculty profiles, student coordination and approval workflows.
- **Administration:** Role-based dashboards, user management and approval controls.
- **Company Management:** Recruiter accounts and company information.
- **Placement Management:** Placement drives, eligibility checking and applications.
- **Skill Matrix:** Student skills, skill gaps and placement readiness.
- **Reports:** Placement information and administrative reports.
- **Campus Connect:** A central area for campus-related communication.

*Note: Module availability depends on the current implementation. Refer to the application for completed functionality.*

## Technology Stack

### Web Application
- React
- TypeScript
- Node.js
- Express.js
- MySQL

### Java Desktop Application (MongoDB Edition)
- Java 17
- Java Swing + FlatLaf (Retina / HiDPI aware on macOS and Windows)
- MongoDB Atlas (cloud-first) with local MongoDB Community fallback
- Official MongoDB Java Driver 5.x (`$jsonSchema` validation, indexes, aggregation pipelines)
- Maven Wrapper (`./mvnw`, no Maven install needed)
- JFreeChart, OpenPDF

> New teammate on a fresh laptop? Start with **[SETUP_AND_RUN.md](SETUP_AND_RUN.md)** (zero pre-installed dependencies).  
> Live Faculty Evaluation & Viva Queries: **[DBMS_VIVA_QUERIES.md](DBMS_VIVA_QUERIES.md)**.  
> DBMS design report: **[MONGODB_ARCHITECTURE.md](MONGODB_ARCHITECTURE.md)**.

## Project Structure

```text
gcet-placement-hub/
├── gcet-placement-hub-java/
├── public/
├── scripts/
├── server/
├── src/
├── database/
├── docs/
├── screenshots/
├── .env.example
├── .gitignore
├── package.json
├── server.ts
└── README.md
```

## Getting Started

### Requirements
- Node.js and npm
- Java 17
- Maven
- MySQL Server
- Visual Studio Code or another suitable IDE

### Web Application Setup

1. Clone the repository.

```bash
git clone https://github.com/emmanuelmj/dbms-pbl.git
```

2. Enter the project folder.

```bash
cd dbms-pbl
```

3. Install dependencies.

```bash
npm install
```

4. Create a local `.env` file using `.env.example` as a reference. Do not commit this file.

5. Configure the database connection using your local MySQL credentials.

6. Apply the database migrations in the documented order.

7. Start the development server.

```bash
npm run dev
```

Use the scripts listed in `package.json` if the development and backend commands are different.

### Java Desktop Application (MongoDB Edition)

The Java 17 Desktop application has been migrated from relational SQL to a high-performance **MongoDB Atlas** document architecture (with local MongoDB Community fallback).

#### 🍏 On macOS (Teammate Setup):
```bash
# From repository root:
./run-mac.sh
```
*(Automatically verifies Java 17+, copies `.env.example` -> `.env` if missing, sets Retina HiDPI flags, and launches via `./mvnw clean compile exec:java`).*

#### 🪟 On Windows:
```cmd
:: From repository root (or double-click run-windows.bat):
run-windows.bat
```
*(Or via PowerShell: `.\gcet-placement-hub-java\run-windows.ps1`)*

For complete details on the document schema, `$jsonSchema` governance, indexing strategy, and aggregation pipelines, see [MONGODB_ARCHITECTURE.md](MONGODB_ARCHITECTURE.md). For a step-by-step install on a machine with nothing on it (Git, JDK 17, `.env`, demo credentials, troubleshooting), see [SETUP_AND_RUN.md](SETUP_AND_RUN.md).

## Database

Database name: `gcet_placement_hub`

The database schema, migration scripts and setup instructions will be maintained in the `database/` directory.

Never commit real database passwords, API keys or private student information.

## Security

- Store passwords using secure password hashing.
- Keep database credentials in local environment variables.
- Apply role-based authorization.
- Protect pending accounts from unauthorized login.
- Never publish private student records.

## Screenshots

Application screenshots will be added to the `screenshots/` directory.

## Project Information

**Project:** GCET Placement Hub  
**Institution:** Geethanjali College of Engineering and Technology  
**Domain:** Placement Management and Skill Development  
**Project Type:** Academic Project / Problem-Based Learning

## Development Status

This repository contains the web application and a separate Java application. Refer to the current source code and project documentation for the implementation status of each module.

## License

No license has been assigned yet. All rights are reserved by the project owner unless a license is added.
