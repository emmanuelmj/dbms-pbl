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

### Java Application
- Java 17
- Java Swing
- FlatLaf
- JDBC
- MySQL
- Maven
- JFreeChart

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
git clone YOUR_GITHUB_REPOSITORY_URL
```

2. Enter the project folder.

```bash
cd gcet-placement-hub
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

### Java Application Setup

1. Open the `gcet-placement-hub-java` folder.
2. Configure the required MySQL connection settings.
3. Open a terminal in the folder containing `pom.xml`.
4. Build the application.

```bash
mvn clean package
```

5. Run the generated application according to its Maven configuration.

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
