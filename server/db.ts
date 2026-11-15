import mysql from 'mysql2/promise';
import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import dotenv from 'dotenv';
import os from 'os';

dotenv.config();

// MySQL configuration parameters read cleanly from local .env
export const DB_CONFIG = {
  host: process.env.DB_HOST || '127.0.0.1',
  port: process.env.DB_PORT ? parseInt(process.env.DB_PORT, 10) : 3306,
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD ?? '',
  database: process.env.DB_NAME || 'gcet_placement_hub',
  waitForConnections: true,
  connectionLimit: 10,
  maxIdle: 5,
  idleTimeout: 60000,
  queueLimit: 0,
  enableKeepAlive: true,
  keepAliveInitialDelay: 10000,
};

// Global pool reference
let pool: mysql.Pool | null = null;
let isConnected = false;
let lastConnectionError: string | null = null;
let lastCheckTime = new Date().toISOString();
let lastSuccessfulConnectionTime: string | null = null;
let reconnectTimer: NodeJS.Timeout | null = null;

/**
 * Generate a cryptographically secure random BCrypt salt (cost factor 12)
 */
export function generateSalt(): string {
  return bcrypt.genSaltSync(12);
}

/**
 * Secure password hashing using BCrypt with per-user salt
 */
export function hashPassword(password: string, salt?: string): string {
  const effectiveSalt = salt && /^\$2[aby]\$/.test(salt) ? salt : bcrypt.genSaltSync(12);
  return bcrypt.hashSync(password, effectiveSalt);
}

/**
 * Constant-time password verification supporting BCrypt, scrypt, PBKDF2, and SHA-256 hashes
 */
export function verifyPassword(password: string, salt: string, expectedHash: string): boolean {
  try {
    if (!password || !expectedHash) return false;
    const trimmedHash = expectedHash.trim();

    // 1. Check BCrypt format ($2a$, $2b$, $2y$)
    if (/^\$2[aby]\$/.test(trimmedHash)) {
      return bcrypt.compareSync(password, trimmedHash);
    }

    if (!salt) return false;

    // 2. Check existing scrypt format (preserves existing MySQL accounts)
    if (trimmedHash.startsWith('scrypt$')) {
      const key = crypto.scryptSync(password, salt, 64, { N: 16384, r: 8, p: 1 });
      const computedHash = `scrypt$${key.toString('hex')}`;
      const bufA = Buffer.from(computedHash);
      const bufB = Buffer.from(trimmedHash);
      if (bufA.length !== bufB.length) return false;
      return crypto.timingSafeEqual(bufA, bufB);
    }

    // 3. Support legacy PBKDF2 base64 format for existing accounts
    try {
      const saltBuffer = Buffer.from(salt, 'base64');
      const derivedKey = crypto.pbkdf2Sync(password, saltBuffer, 65536, 32, 'sha256');
      const pbkdf2Hash = derivedKey.toString('base64');
      const bufA = Buffer.from(pbkdf2Hash);
      const bufB = Buffer.from(trimmedHash);
      if (bufA.length === bufB.length && crypto.timingSafeEqual(bufA, bufB)) {
        return true;
      }
    } catch {
      // Ignore conversion error
    }

    // 4. Support legacy salted SHA-256 hex format if present
    if (/^[a-f0-9]{64}$/i.test(trimmedHash)) {
      const sha = crypto.createHash('sha256').update(password + salt).digest('hex');
      const bufA = Buffer.from(sha);
      const bufB = Buffer.from(trimmedHash.toLowerCase());
      if (bufA.length === bufB.length && crypto.timingSafeEqual(bufA, bufB)) {
        return true;
      }
    }

    return false;
  } catch {
    return false;
  }
}

// Canonical Department Normalization
export function normalizeDeptCode(dept: string): string {
  if (!dept) return '';
  const s = dept.trim().toUpperCase().replace(/\s+/g, '');
  if (s.includes('AIML')) return 'CSE-AIML';
  if (s.includes('DS') || s.includes('DATASCIENCE')) return 'CSE-DS';
  if (s.includes('CYBER') || s.includes('SECURITY') || s.includes('CSE-CS') || s.includes('CSECS')) return 'CSE-CS';
  if (s === 'CSE') return 'CSE';
  if (s === 'IT') return 'IT';
  if (s === 'EEE') return 'EEE';
  if (s === 'ECE') return 'ECE';
  if (s === 'MECH' || s === 'MECHANICAL') return 'MECH';
  if (s === 'CIVIL') return 'CIVIL';
  return dept.trim();
}

// Authorized Department Mapping for Faculty Coordinators
export const FACULTY_ASSIGNMENTS: Record<string, string[]> = {
  faculty_lavanya: ['CSE', 'CSE-AIML', 'CSE-DS'],
  'usr-fac-1': ['CSE', 'CSE-AIML', 'CSE-DS'],
  faculty_aparna: ['CSE-CS', 'IT'],
  'usr-fac-2': ['CSE-CS', 'IT'],
  faculty_rajashakar: ['EEE', 'ECE', 'MECH', 'CIVIL'],
  'usr-fac-3': ['EEE', 'ECE', 'MECH', 'CIVIL'],
};

export function getFacultyAuthorizedDepts(facultyUsernameOrId: string): string[] {
  if (!facultyUsernameOrId) return [];
  const key = facultyUsernameOrId.toLowerCase().trim();
  if (FACULTY_ASSIGNMENTS[key]) return FACULTY_ASSIGNMENTS[key];
  if (key.includes('lavanya')) return FACULTY_ASSIGNMENTS['faculty_lavanya'];
  if (key.includes('aparna')) return FACULTY_ASSIGNMENTS['faculty_aparna'];
  if (key.includes('rajashakar') || key.includes('raja')) return FACULTY_ASSIGNMENTS['faculty_rajashakar'];
  return [];
}

export function isFacultyAuthorizedForStudent(facultyUsernameOrId: string, studentBranch: string): boolean {
  const authorized = getFacultyAuthorizedDepts(facultyUsernameOrId);
  if (authorized.length === 0) return false;
  const normalized = normalizeDeptCode(studentBranch);
  return authorized.some((d) => normalizeDeptCode(d) === normalized);
}

// Initialize MySQL Pool and safe schema migration
export async function initDatabase(): Promise<boolean> {
  try {
    if (pool) {
      await pool.end().catch(() => {});
    }

    // Step 1: Connect to server without database first to ensure database exists
    const rootConn = await mysql.createConnection({
      host: DB_CONFIG.host,
      port: DB_CONFIG.port,
      user: DB_CONFIG.user,
      password: DB_CONFIG.password,
    });

    await rootConn.query(
      `CREATE DATABASE IF NOT EXISTS \`${DB_CONFIG.database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
    );
    await rootConn.end();

    // Step 2: Create connection pool with gcet_placement_hub
    pool = mysql.createPool(DB_CONFIG);

    // Verify connection
    await pool.query('SELECT 1 as ping');
    isConnected = true;
    lastConnectionError = null;
    lastCheckTime = new Date().toISOString();
    lastSuccessfulConnectionTime = lastCheckTime;
    console.log(`[MySQL] Successfully connected to ${DB_CONFIG.database} on ${DB_CONFIG.host}:${DB_CONFIG.port}`);

    // Run schema tables creation and safe migrations
    await runSchemaMigrations();

    // Seed institutional accounts
    await seedMandatoryAccounts();

    return true;
  } catch (err: any) {
    isConnected = false;
    lastConnectionError = err.message || String(err);
    lastCheckTime = new Date().toISOString();
    console.warn(`[MySQL Standby] Could not connect to MySQL at ${DB_CONFIG.host}:${DB_CONFIG.port} (${lastConnectionError})`);
    
    // Start background reconnection retry loop
    scheduleReconnect();
    return false;
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(async () => {
    reconnectTimer = null;
    const ok = await initDatabase();
    if (!ok) {
      scheduleReconnect();
    }
  }, 10000);
}

// Execute safe schema migrations without dropping existing tables or records
async function runSchemaMigrations() {
  if (!pool) return;

  // 1. Users table (all IDs set to VARCHAR(64) to safely accommodate uuid prefixes)
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id VARCHAR(64) PRIMARY KEY,
      username VARCHAR(50) NOT NULL UNIQUE,
      email VARCHAR(100) NOT NULL UNIQUE,
      full_name VARCHAR(100) NOT NULL,
      role VARCHAR(30) NOT NULL,
      department VARCHAR(100),
      student_roll_number VARCHAR(30),
      company_id VARCHAR(64) NULL,
      password_hash VARCHAR(255) NOT NULL,
      salt VARCHAR(128) NOT NULL,
      status VARCHAR(30) DEFAULT 'ACTIVE',
      is_active BOOLEAN DEFAULT TRUE,
      must_change_password BOOLEAN DEFAULT FALSE,
      authorized_departments TEXT NULL,
      approved_by VARCHAR(100) NULL,
      approved_at TIMESTAMP NULL,
      rejection_reason TEXT NULL,
      last_login TIMESTAMP NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_user_role (role),
      INDEX idx_user_status (status)
    ) ENGINE=InnoDB;
  `);

  // Ensure columns exist on users
  await safeAddColumn('users', 'must_change_password', 'BOOLEAN DEFAULT FALSE');
  await safeAddColumn('users', 'authorized_departments', 'TEXT NULL');
  await safeAddColumn('users', 'approved_by', 'VARCHAR(100) NULL');
  await safeAddColumn('users', 'approved_at', 'TIMESTAMP NULL');
  await safeAddColumn('users', 'rejection_reason', 'TEXT NULL');

  // 2. Students table
  await pool.query(`
    CREATE TABLE IF NOT EXISTS students (
      id VARCHAR(64) PRIMARY KEY,
      roll_number VARCHAR(30) NOT NULL UNIQUE,
      student_id_card VARCHAR(50) NULL,
      full_name VARCHAR(100) NOT NULL,
      email VARCHAR(100) NOT NULL UNIQUE,
      phone VARCHAR(20),
      branch VARCHAR(50) NOT NULL,
      year_of_study INT DEFAULT 4,
      section VARCHAR(10) DEFAULT 'A',
      current_semester INT DEFAULT 7,
      cgpa DECIMAL(4, 2) NOT NULL DEFAULT 0.00,
      tenth_percentage DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
      inter_percentage DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
      graduation_year INT NOT NULL DEFAULT 2026,
      academic_year INT NOT NULL DEFAULT 4,
      backlogs INT DEFAULT 0,
      placement_status VARCHAR(30) DEFAULT 'UNPLACED',
      approval_status VARCHAR(30) DEFAULT 'PENDING_APPROVAL',
      approved_by_faculty_id VARCHAR(64) NULL,
      approved_by_faculty_name VARCHAR(100) NULL,
      approved_at TIMESTAMP NULL,
      rejection_reason TEXT NULL,
      user_id VARCHAR(64) NULL,
      placed_company_id VARCHAR(64) NULL,
      placed_package_lpa DECIMAL(5, 2) NULL,
      resume_url VARCHAR(255),
      dob VARCHAR(20) NULL,
      gender VARCHAR(30) NULL,
      address TEXT NULL,
      location VARCHAR(150) NULL,
      about_me TEXT NULL,
      technical_skills TEXT NULL,
      programming_languages TEXT NULL,
      frameworks_technologies TEXT NULL,
      soft_skills TEXT NULL,
      career_interests TEXT NULL,
      preferred_job_roles TEXT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_student_branch (branch),
      INDEX idx_student_status (placement_status),
      INDEX idx_student_approval (approval_status),
      INDEX idx_student_user (user_id)
    ) ENGINE=InnoDB;
  `);

  await safeAddColumn('students', 'academic_year', 'INT NOT NULL DEFAULT 4');
  await safeAddColumn('students', 'current_semester', 'INT DEFAULT 7');
  await safeAddColumn('students', 'approved_by_faculty_id', 'VARCHAR(64) NULL');
  await safeAddColumn('students', 'approved_by_faculty_name', 'VARCHAR(100) NULL');
  await safeAddColumn('students', 'approved_at', 'TIMESTAMP NULL');
  await safeAddColumn('students', 'rejection_reason', 'TEXT NULL');
  await safeAddColumn('students', 'dob', 'VARCHAR(20) NULL');
  await safeAddColumn('students', 'gender', 'VARCHAR(30) NULL');
  await safeAddColumn('students', 'address', 'TEXT NULL');
  await safeAddColumn('students', 'location', 'VARCHAR(150) NULL');
  await safeAddColumn('students', 'about_me', 'TEXT NULL');
  await safeAddColumn('students', 'technical_skills', 'TEXT NULL');
  await safeAddColumn('students', 'programming_languages', 'TEXT NULL');
  await safeAddColumn('students', 'frameworks_technologies', 'TEXT NULL');
  await safeAddColumn('students', 'soft_skills', 'TEXT NULL');
  await safeAddColumn('students', 'career_interests', 'TEXT NULL');
  await safeAddColumn('students', 'preferred_job_roles', 'TEXT NULL');

  // 3. Faculty directory table
  await pool.query(`
    CREATE TABLE IF NOT EXISTS faculty (
      id VARCHAR(64) PRIMARY KEY,
      faculty_id VARCHAR(50) NOT NULL UNIQUE,
      employee_id VARCHAR(50) NULL,
      full_name VARCHAR(100) NOT NULL,
      department VARCHAR(100) NOT NULL,
      designation VARCHAR(100) NOT NULL,
      email VARCHAR(100) NOT NULL UNIQUE,
      phone VARCHAR(20),
      user_id VARCHAR(64) NULL,
      authorized_departments TEXT NULL,
      about_me TEXT NULL,
      qualifications TEXT NULL,
      areas_of_expertise TEXT NULL,
      subjects_handled TEXT NULL,
      research_interests TEXT NULL,
      location VARCHAR(150) NULL,
      faculty_approved BOOLEAN DEFAULT TRUE,
      admin_approved BOOLEAN DEFAULT TRUE,
      status VARCHAR(30) DEFAULT 'ACTIVE',
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_faculty_dept (department),
      INDEX idx_faculty_status (status),
      INDEX idx_faculty_user (user_id)
    ) ENGINE=InnoDB;
  `);

  await safeAddColumn('faculty', 'about_me', 'TEXT NULL');
  await safeAddColumn('faculty', 'qualifications', 'TEXT NULL');
  await safeAddColumn('faculty', 'areas_of_expertise', 'TEXT NULL');
  await safeAddColumn('faculty', 'subjects_handled', 'TEXT NULL');
  await safeAddColumn('faculty', 'research_interests', 'TEXT NULL');
  await safeAddColumn('faculty', 'location', 'VARCHAR(150) NULL');

  // 4. Skills table
  await pool.query(`
    CREATE TABLE IF NOT EXISTS skills (
      id VARCHAR(64) PRIMARY KEY,
      name VARCHAR(100) NOT NULL UNIQUE,
      category VARCHAR(60) NOT NULL,
      description TEXT,
      status VARCHAR(30) DEFAULT 'ACTIVE',
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;
  `);

  // 5. Student skills table
  await pool.query(`
    CREATE TABLE IF NOT EXISTS student_skills (
      student_id VARCHAR(64) NOT NULL,
      skill_id VARCHAR(64) NOT NULL,
      proficiency TINYINT NOT NULL DEFAULT 3,
      experience_duration VARCHAR(50) DEFAULT '6 months',
      certification_evidence VARCHAR(255) NULL,
      verified_by_faculty BOOLEAN DEFAULT FALSE,
      verified_by_faculty_id VARCHAR(64) NULL,
      verified_at TIMESTAMP NULL,
      PRIMARY KEY (student_id, skill_id)
    ) ENGINE=InnoDB;
  `);

  // 6. Companies table
  await pool.query(`
    CREATE TABLE IF NOT EXISTS companies (
      id VARCHAR(64) PRIMARY KEY,
      name VARCHAR(150) NOT NULL UNIQUE,
      industry VARCHAR(100) NOT NULL,
      website VARCHAR(200),
      location VARCHAR(100),
      description TEXT,
      hr_name VARCHAR(100),
      hr_email VARCHAR(100),
      hr_phone VARCHAR(20),
      recruiter_name VARCHAR(120) NULL,
      recruiter_designation VARCHAR(120) NULL,
      company_logo MEDIUMTEXT NULL,
      linkedin_url VARCHAR(500) NULL,
      user_id VARCHAR(64) NULL,
      tier VARCHAR(50) DEFAULT 'Standard IT',
      approval_status VARCHAR(30) DEFAULT 'ACTIVE',
      approved_by_admin_name VARCHAR(100) NULL,
      approved_at TIMESTAMP NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;
  `);

  await safeAddColumn('companies', 'recruiter_name', 'VARCHAR(120) NULL');
  await safeAddColumn('companies', 'recruiter_designation', 'VARCHAR(120) NULL');
  await safeAddColumn('companies', 'company_logo', 'MEDIUMTEXT NULL');
  await safeAddColumn('companies', 'linkedin_url', 'VARCHAR(500) NULL');
  await safeAddColumn('companies', 'user_id', 'VARCHAR(64) NULL');
  await safeAddColumn('companies', 'approved_by_admin_id', 'VARCHAR(64) NULL');
  await safeAddColumn('companies', 'approved_by_name', 'VARCHAR(100) NULL');
  await safeAddColumn('companies', 'rejection_reason', 'TEXT NULL');

  // 7. Placement drives
  await pool.query(`
    CREATE TABLE IF NOT EXISTS placement_drives (
      id VARCHAR(64) PRIMARY KEY,
      company_id VARCHAR(64) NOT NULL,
      company_name VARCHAR(150) NOT NULL,
      job_role VARCHAR(150) NOT NULL,
      job_description TEXT,
      salary_package_lpa DECIMAL(5, 2) NOT NULL,
      min_cgpa DECIMAL(4, 2) NOT NULL DEFAULT 6.00,
      max_backlogs_allowed INT DEFAULT 0,
      target_graduation_year INT NOT NULL DEFAULT 2026,
      drive_date DATE NOT NULL,
      application_deadline DATE NOT NULL,
      available_positions INT NOT NULL DEFAULT 1,
      status VARCHAR(30) DEFAULT 'OPEN',
      selection_process TEXT,
      eligible_branches TEXT NULL,
      required_skills TEXT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;
  `);

  await safeAddColumn('placement_drives', 'eligible_branches', 'TEXT NULL');
  await safeAddColumn('placement_drives', 'required_skills', 'TEXT NULL');

  // 8. Applications table
  await pool.query(`
    CREATE TABLE IF NOT EXISTS applications (
      id VARCHAR(64) PRIMARY KEY,
      drive_id VARCHAR(64) NOT NULL,
      student_id VARCHAR(64) NOT NULL,
      student_roll_number VARCHAR(30) NULL,
      student_name VARCHAR(100) NULL,
      student_branch VARCHAR(50) NULL,
      student_cgpa DECIMAL(4, 2) NULL,
      company_id VARCHAR(64) NULL,
      company_name VARCHAR(150) NOT NULL,
      job_role VARCHAR(150) NOT NULL,
      current_stage VARCHAR(50) DEFAULT 'APPLICATION_SUBMITTED',
      stage_history TEXT NULL,
      applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      final_offer_lpa DECIMAL(5, 2) NULL,
      rejection_reason TEXT NULL,
      UNIQUE KEY uq_student_drive (student_id, drive_id)
    ) ENGINE=InnoDB;
  `);

  await safeAddColumn('applications', 'student_roll_number', 'VARCHAR(30) NULL');
  await safeAddColumn('applications', 'student_name', 'VARCHAR(100) NULL');
  await safeAddColumn('applications', 'student_branch', 'VARCHAR(50) NULL');
  await safeAddColumn('applications', 'student_cgpa', 'DECIMAL(4, 2) NULL');
  await safeAddColumn('applications', 'company_id', 'VARCHAR(64) NULL');
  await safeAddColumn('applications', 'stage_history', 'TEXT NULL');

  // 9. Centralized Approval Records Audit Trail
  await pool.query(`
    CREATE TABLE IF NOT EXISTS approval_records (
      id VARCHAR(64) PRIMARY KEY,
      request_type VARCHAR(50) NOT NULL,
      target_id VARCHAR(64) NOT NULL,
      target_name VARCHAR(150) NOT NULL,
      student_roll_number VARCHAR(30) NULL,
      department VARCHAR(50) NULL,
      approver_id VARCHAR(64) NOT NULL,
      approver_name VARCHAR(100) NOT NULL,
      approver_role VARCHAR(50) NOT NULL,
      decision VARCHAR(30) NOT NULL,
      comments TEXT,
      decided_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_approval_target (target_id),
      INDEX idx_approval_type (request_type)
    ) ENGINE=InnoDB;
  `);

  // 10. Audit Logging
  await pool.query(`
    CREATE TABLE IF NOT EXISTS audit_logs (
      id VARCHAR(64) PRIMARY KEY,
      action VARCHAR(100) NOT NULL,
      actor_id VARCHAR(64) NULL,
      actor_username VARCHAR(50) NULL,
      actor_role VARCHAR(50) NULL,
      ip_address VARCHAR(45) NULL,
      details TEXT,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;
  `);

  // 11. Unified User Profiles (Shared across all roles, linked 1:1 with users.id)
  await pool.query(`
    CREATE TABLE IF NOT EXISTS user_profiles (
      user_id VARCHAR(64) PRIMARY KEY,
      profile_photo MEDIUMTEXT NULL,
      phone VARCHAR(30) NULL,
      designation VARCHAR(120) NULL,
      location VARCHAR(150) NULL,
      address TEXT NULL,
      about_me TEXT NULL,
      date_of_birth VARCHAR(20) NULL,
      gender VARCHAR(30) NULL,
      linkedin_url VARCHAR(500) NULL,
      github_url VARCHAR(500) NULL,
      portfolio_url VARCHAR(500) NULL,
      leetcode_url VARCHAR(500) NULL,
      hackerrank_url VARCHAR(500) NULL,
      codechef_url VARCHAR(500) NULL,
      gfg_url VARCHAR(500) NULL,
      kaggle_url VARCHAR(500) NULL,
      google_dev_url VARCHAR(500) NULL,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;
  `);

  // 12. User Custom Social / Professional Links
  await pool.query(`
    CREATE TABLE IF NOT EXISTS user_custom_links (
      id VARCHAR(64) PRIMARY KEY,
      user_id VARCHAR(64) NOT NULL,
      title VARCHAR(100) NOT NULL,
      url VARCHAR(500) NOT NULL,
      display_order INT DEFAULT 0,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_custom_links_user (user_id),
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;
  `);

  // 13. Student Projects
  await pool.query(`
    CREATE TABLE IF NOT EXISTS student_projects (
      id VARCHAR(64) PRIMARY KEY,
      student_id VARCHAR(64) NOT NULL,
      user_id VARCHAR(64) NULL,
      title VARCHAR(200) NOT NULL,
      description TEXT NULL,
      technologies TEXT NULL,
      project_url VARCHAR(500) NULL,
      duration VARCHAR(80) NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_proj_student (student_id),
      INDEX idx_proj_user (user_id),
      FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;
  `);

  // 14. Student Internships & Work Experience
  await pool.query(`
    CREATE TABLE IF NOT EXISTS student_internships (
      id VARCHAR(64) PRIMARY KEY,
      student_id VARCHAR(64) NOT NULL,
      user_id VARCHAR(64) NULL,
      company_name VARCHAR(150) NOT NULL,
      role VARCHAR(150) NOT NULL,
      duration VARCHAR(100) NULL,
      location VARCHAR(150) NULL,
      description TEXT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_intern_student (student_id),
      INDEX idx_intern_user (user_id),
      FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;
  `);

  // 15. User Certifications (Students & Faculty)
  await pool.query(`
    CREATE TABLE IF NOT EXISTS user_certifications (
      id VARCHAR(64) PRIMARY KEY,
      user_id VARCHAR(64) NOT NULL,
      student_id VARCHAR(64) NULL,
      name VARCHAR(200) NOT NULL,
      issuing_org VARCHAR(200) NOT NULL,
      issue_date VARCHAR(50) NULL,
      credential_id VARCHAR(120) NULL,
      credential_url VARCHAR(500) NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_cert_user (user_id),
      INDEX idx_cert_student (student_id),
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;
  `);

  // 16. Student Achievements, Awards, Workshops & Hackathons
  await pool.query(`
    CREATE TABLE IF NOT EXISTS student_achievements (
      id VARCHAR(64) PRIMARY KEY,
      student_id VARCHAR(64) NOT NULL,
      user_id VARCHAR(64) NULL,
      title VARCHAR(200) NOT NULL,
      category VARCHAR(40) DEFAULT 'AWARD',
      organizer VARCHAR(200) NULL,
      event_date VARCHAR(50) NULL,
      description TEXT NULL,
      credential_url VARCHAR(500) NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_ach_student (student_id),
      INDEX idx_ach_user (user_id),
      FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;
  `);

  // 17. Faculty Publications
  await pool.query(`
    CREATE TABLE IF NOT EXISTS faculty_publications (
      id VARCHAR(64) PRIMARY KEY,
      faculty_id VARCHAR(64) NULL,
      user_id VARCHAR(64) NOT NULL,
      title VARCHAR(300) NOT NULL,
      journal_or_conference VARCHAR(200) NULL,
      publication_year VARCHAR(20) NULL,
      authors VARCHAR(255) NULL,
      doi_or_url VARCHAR(500) NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_pub_user (user_id),
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;
  `);
}

async function safeAddColumn(table: string, column: string, definition: string) {
  if (!pool) return;
  try {
    const [cols]: any = await pool.query(
      `SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?`,
      [DB_CONFIG.database, table, column]
    );
    if (!cols || cols.length === 0) {
      await pool.query(`ALTER TABLE \`${table}\` ADD COLUMN \`${column}\` ${definition}`);
    }
  } catch {
    // Ignore if column already exists
  }
}

interface MandatoryAccountSeed {
  username: string;
  email: string;
  fullName: string;
  role: string;
  department: string;
  designation: string;
  envVar: string;
  password?: string;
  matchNamePattern?: string;
  facultyCode?: string;
  empCode?: string;
  phone?: string;
  companyId?: string;
  authorizedDepartments?: string;
  enforceActiveBootstrap?: boolean;
}

// Seed or safely verify/repair mandatory institutional administrators & faculty accounts in MySQL
export async function seedMandatoryAccounts() {
  if (!pool) return;

  const facultyPass = process.env.GCET_BOOTSTRAP_FACULTY_PASS?.trim() || '';

  const defaultUsers: MandatoryAccountSeed[] = [
    // 1. Dr. K. Sagar (Principal)
    {
      username: 'principal.sagar',
      email: 'principal@gcet.edu.in',
      fullName: 'Dr. K. Sagar',
      role: 'PRINCIPAL',
      department: "Principal's Office",
      designation: 'Principal',
      envVar: 'GCET_BOOTSTRAP_PRINCIPAL_PASS',
      password: process.env.GCET_BOOTSTRAP_PRINCIPAL_PASS?.trim() || '',
      enforceActiveBootstrap: true,
    },
    // 2. Mr. R. Harishchandra Reddy (Vice Chairman)
    {
      username: 'vc.harishchandra',
      email: 'vc@gcet.edu.in',
      fullName: 'Mr. R. Harishchandra Reddy',
      role: 'VICE_CHAIRMAN',
      department: 'Executive Board',
      designation: 'Vice Chairman',
      envVar: 'GCET_BOOTSTRAP_VC_PASS',
      password: process.env.GCET_BOOTSTRAP_VC_PASS?.trim() || '',
      enforceActiveBootstrap: true,
    },
    // 3. Mr. G.R. Ravinder Reddy (Chairman)
    {
      username: 'chairman.ravinder',
      email: 'chairman@gcet.edu.in',
      fullName: 'Mr. G.R. Ravinder Reddy',
      role: 'CHAIRMAN',
      department: 'Governing Body',
      designation: 'Chairman',
      envVar: 'GCET_BOOTSTRAP_CHAIRMAN_PASS',
      password: process.env.GCET_BOOTSTRAP_CHAIRMAN_PASS?.trim() || '',
      enforceActiveBootstrap: true,
    },
    // 4. B. Laxmi Lavanya (Faculty Coordinator 1)
    {
      username: 'faculty_lavanya',
      email: 'lavanya.cse@gcet.edu.in',
      fullName: 'B. Laxmi Lavanya',
      role: 'FACULTY',
      department: 'CSE',
      designation: 'Associate Professor',
      facultyCode: 'GCET-FAC-01',
      empCode: 'EMP-CSE-101',
      phone: '+91 98491 10001',
      authorizedDepartments: JSON.stringify(['CSE', 'CSE-AIML', 'CSE-DS']),
      matchNamePattern: '%lavanya%',
      envVar: 'GCET_BOOTSTRAP_FACULTY_PASS',
      password: facultyPass,
      enforceActiveBootstrap: true,
    },
    // 5. P. Aparna (Faculty Coordinator 2)
    {
      username: 'faculty_aparna',
      email: 'aparna.ece@gcet.edu.in',
      fullName: 'P. Aparna',
      role: 'FACULTY',
      department: 'CSE-CS',
      designation: 'Associate Professor',
      facultyCode: 'GCET-FAC-02',
      empCode: 'EMP-ECE-102',
      phone: '+91 98491 10002',
      authorizedDepartments: JSON.stringify(['CSE-CS', 'IT']),
      matchNamePattern: '%aparna%',
      envVar: 'GCET_BOOTSTRAP_FACULTY_PASS',
      password: facultyPass,
      enforceActiveBootstrap: true,
    },
    // 6. S. Raja Shakar Reddy (Faculty Coordinator 3)
    {
      username: 'faculty_rajashakar',
      email: 'rajashakar.cse@gcet.edu.in',
      fullName: 'S. Raja Shakar Reddy',
      role: 'FACULTY',
      department: 'EEE',
      designation: 'Assistant Professor',
      facultyCode: 'GCET-FAC-03',
      empCode: 'EMP-CSE-103',
      phone: '+91 98491 10003',
      authorizedDepartments: JSON.stringify(['EEE', 'ECE', 'MECH', 'CIVIL']),
      matchNamePattern: '%raja%',
      envVar: 'GCET_BOOTSTRAP_FACULTY_PASS',
      password: facultyPass,
      enforceActiveBootstrap: true,
    },
    // 7. Administrator
    {
      username: 'admin',
      email: 'admin@gcet.edu.in',
      fullName: 'GCET System Administrator',
      role: 'ADMIN',
      department: 'Central Placement Administration',
      designation: 'System Administrator',
      envVar: 'GCET_BOOTSTRAP_ADMIN_PASS',
      password: process.env.GCET_BOOTSTRAP_ADMIN_PASS?.trim() || '',
      enforceActiveBootstrap: true,
    },
    // 8. Head - Training & Placements (CDC)
    {
      username: 'officer',
      email: 'placements@gcet.edu.in',
      fullName: 'Head - Training & Placements (CDC)',
      role: 'PLACEMENT_OFFICER',
      department: 'Placement Cell',
      designation: 'Training & Placement Officer',
      envVar: 'GCET_BOOTSTRAP_OFFICER_PASS',
      password: process.env.GCET_BOOTSTRAP_OFFICER_PASS?.trim() || '',
      enforceActiveBootstrap: true,
    },
    // 9. Placement Officer
    {
      username: 'placement_officer',
      email: 'officer@gcet.edu.in',
      fullName: 'Placement Officer',
      role: 'PLACEMENT_OFFICER',
      department: 'Placement Cell',
      designation: 'Placement Officer',
      envVar: 'GCET_BOOTSTRAP_PLACEMENT_OFFICER_PASS',
      password: process.env.GCET_BOOTSTRAP_PLACEMENT_OFFICER_PASS?.trim() || '',
      enforceActiveBootstrap: true,
    },
    // 10. Faculty CSE Mentor
    {
      username: 'faculty_cse',
      email: 'mentor.cse@gcet.edu.in',
      fullName: 'CSE Faculty Mentor',
      role: 'FACULTY',
      department: 'CSE',
      designation: 'Assistant Professor',
      facultyCode: 'GCET-FAC-04',
      empCode: 'EMP-CSE-104',
      phone: '+91 98491 10004',
      authorizedDepartments: JSON.stringify(['CSE']),
      envVar: 'GCET_BOOTSTRAP_FACULTY_PASS',
      password: facultyPass,
    },
    // 11. Recruiter Partner (Tata Consultancy Services)
    {
      username: 'recruiter_tcs',
      email: 'srinivas.k@tcs.com',
      fullName: 'K. Srinivas (TCS Campus Lead)',
      role: 'COMPANY',
      department: 'Corporate Recruitment Partner',
      designation: 'Regional Talent Acquisition Lead',
      companyId: 'comp-1',
      envVar: 'GCET_BOOTSTRAP_RECRUITER_PASS',
      password: process.env.GCET_BOOTSTRAP_RECRUITER_PASS?.trim() || '',
      enforceActiveBootstrap: true,
    },
  ];

  for (const u of defaultUsers) {
    // Match by username or email first so existing accounts (including custom faculty usernames) are preserved without duplicates
    let [existing]: any = await pool.query(
      'SELECT id, username, role, password_hash, salt, status, is_active, company_id, must_change_password FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?) LIMIT 1',
      [u.username, u.email]
    );

    if ((!existing || existing.length === 0) && u.matchNamePattern && u.role === 'FACULTY') {
      [existing] = await pool.query(
        `SELECT id, username, role, password_hash, salt, status, is_active, company_id, must_change_password FROM users WHERE role = 'FACULTY' AND LOWER(full_name) LIKE LOWER(?) LIMIT 1`,
        [u.matchNamePattern]
      );
    }

    let resolvedUserId = '';
    if (!existing || existing.length === 0) {
      if (!u.password) {
        console.warn(`[MySQL Bootstrap] Skipping creation of ${u.username}: ${u.envVar} is not set in environment.`);
        continue;
      }
      const salt = generateSalt();
      const hash = hashPassword(u.password, salt);
      resolvedUserId = `usr-${crypto.randomUUID()}`;
      await pool.query(
        `INSERT INTO users (id, username, email, full_name, role, department, company_id, password_hash, salt, status, is_active, must_change_password, authorized_departments)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', TRUE, FALSE, ?)`,
        [resolvedUserId, u.username, u.email, u.fullName, u.role, u.department, u.companyId || null, hash, salt, u.authorizedDepartments || null]
      );
    } else {
      const existingRow = existing[0];
      resolvedUserId = existingRow.id;

      // Safely verify and repair password hash if mismatched or not BCrypt cost 12 for this specific listed account
      if (u.password) {
        const currentHashMatches =
          Boolean(existingRow.password_hash) &&
          verifyPassword(u.password, existingRow.salt || '', existingRow.password_hash);
        const isBcryptCost12 = /^\$2[aby]\$12\$/.test((existingRow.password_hash || '').trim());

        if (!currentHashMatches || !isBcryptCost12) {
          const newSalt = generateSalt();
          const newHash = hashPassword(u.password, newSalt);
          await pool.query(
            `UPDATE users SET password_hash = ?, salt = ? WHERE id = ?`,
            [newHash, newSalt, resolvedUserId]
          );
        }
      }

      if (u.enforceActiveBootstrap && (existingRow.status !== 'ACTIVE' || !existingRow.is_active)) {
        await pool.query(
          `UPDATE users SET status = 'ACTIVE', is_active = TRUE WHERE id = ?`,
          [resolvedUserId]
        );
      }

      if (u.authorizedDepartments) {
        await pool.query(
          `UPDATE users SET authorized_departments = COALESCE(NULLIF(authorized_departments, ''), ?) WHERE id = ?`,
          [u.authorizedDepartments, resolvedUserId]
        );
      }
      if (u.companyId && !existingRow.company_id) {
        await pool.query(
          `UPDATE users SET company_id = ? WHERE id = ?`,
          [u.companyId, resolvedUserId]
        );
      }
    }

    // Ensure user_profiles row exists for every mandatory user
    await pool.query(
      `INSERT IGNORE INTO user_profiles (user_id, designation, location) VALUES (?, ?, 'Hyderabad, Telangana')`,
      [resolvedUserId, u.designation || u.role]
    );

    // Ensure faculty table record exists and is linked for seeded faculty
    if (u.role === 'FACULTY' && u.facultyCode) {
      await pool.query(
        `INSERT INTO faculty (id, faculty_id, employee_id, full_name, department, designation, email, phone, user_id, authorized_departments, faculty_approved, admin_approved, status)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE, 'ACTIVE')
         ON DUPLICATE KEY UPDATE user_id = COALESCE(user_id, VALUES(user_id)), authorized_departments = COALESCE(NULLIF(authorized_departments, ''), VALUES(authorized_departments))`,
        [
          `fac-${u.facultyCode.toLowerCase()}`,
          u.facultyCode,
          u.empCode || u.facultyCode,
          u.fullName,
          u.department,
          u.designation || 'Faculty Coordinator',
          u.email,
          u.phone || '+91 98491 10000',
          resolvedUserId,
          u.authorizedDepartments || null,
        ]
      );
    }
  }

  // Seed default skills library if empty
  const [skillCountRows]: any = await pool.query('SELECT COUNT(*) as cnt FROM skills');
  if (skillCountRows[0].cnt === 0) {
    const defaultSkills = [
      ['Java', 'Programming Languages', 'Core Java, OOPs, Collections, Multithreading, Lambdas'],
      ['Python', 'Programming Languages', 'Python 3, Data Structures, OOPs, Standard Libraries'],
      ['SQL & Relational DBs', 'Database Management', 'MySQL, Indexing, Transactions, Normalization'],
      ['Spring Boot', 'Web Development', 'REST APIs, Spring Data JPA, Microservices'],
      ['React & TypeScript', 'Web Development', 'Hooks, State Management, Responsive UI'],
      ['Data Structures & Algorithms', 'Data Structures and Algorithms', 'Trees, Graphs, DP, Complexity Analysis'],
      ['Machine Learning', 'Artificial Intelligence', 'Scikit-Learn, Regression, Classification'],
      ['AWS Cloud Fundamentals', 'Cloud Computing', 'EC2, S3, IAM, Lambda, VPC essentials'],
    ];

    for (const [name, cat, desc] of defaultSkills) {
      await pool.query(
        `INSERT INTO skills (id, name, category, description, status) VALUES (?, ?, ?, ?, 'ACTIVE')`,
        [`sk-${crypto.randomUUID()}`, name, cat, desc]
      );
    }
  }

  // Seed sample company partners if empty
  const [compCount]: any = await pool.query('SELECT COUNT(*) as cnt FROM companies');
  if (compCount[0].cnt === 0) {
    const [tcsUserRows]: any = await pool.query(`SELECT id FROM users WHERE LOWER(username) = 'recruiter_tcs' LIMIT 1`);
    const tcsUserId = tcsUserRows?.[0]?.id || null;
    await pool.query(`
      INSERT INTO companies (id, name, industry, website, location, description, hr_name, hr_email, hr_phone, recruiter_name, recruiter_designation, linkedin_url, user_id, tier, approval_status)
      VALUES 
      ('comp-1', 'Tata Consultancy Services', 'IT Services', 'https://www.tcs.com', 'Hyderabad, Telangana', 'Global IT services, consulting, and digital transformation partner recruiting across engineering branches.', 'K. Srinivas', 'srinivas.k@tcs.com', '+91 98480 11223', 'K. Srinivas', 'Regional Talent Acquisition Lead', 'https://www.linkedin.com/company/tata-consultancy-services/', ?, 'Tier 1 (Dream)', 'ACTIVE'),
      ('comp-2', 'Cognizant Technology Solutions', 'Technology Consulting', 'https://www.cognizant.com', 'Hyderabad, Telangana', 'Leading technology services and digital engineering provider', 'Sneha Rao', 'sneha.rao@cognizant.com', '+91 98480 22334', 'Sneha Rao', 'Campus Hiring Partner', 'https://www.linkedin.com/company/cognizant/', NULL, 'Tier 2 (Super Dream)', 'ACTIVE'),
      ('comp-3', 'Infosys Limited', 'Digital Services', 'https://www.infosys.com', 'Hyderabad, Telangana', 'Next-generation digital services and consulting', 'Anil Kumar', 'anil.kumar@infosys.com', '+91 98480 33445', 'Anil Kumar', 'Talent Acquisition Lead', 'https://www.linkedin.com/company/infosys/', NULL, 'Tier 1 (Dream)', 'ACTIVE')
    `, [tcsUserId]);
  } else {
    // Ensure recruiter_tcs links to comp-1 if not yet linked
    await pool.query(
      `UPDATE companies c
       JOIN users u ON LOWER(u.username) = 'recruiter_tcs'
       SET c.user_id = COALESCE(c.user_id, u.id),
           c.recruiter_name = COALESCE(c.recruiter_name, c.hr_name),
           c.recruiter_designation = COALESCE(c.recruiter_designation, 'Regional Talent Acquisition Lead')
       WHERE c.id = 'comp-1'`
    );
  }

  // Seed & verify initial sample students cleanly without touching other student records
  const studentSeedPass = process.env.GCET_BOOTSTRAP_STUDENT_SEED_PASS?.trim() || '';
  if (studentSeedPass) {
    const seedStudents = [
      {
        rollNumber: '21R11A0501',
        fullName: 'K. Rahul Sharma',
        email: 'rahul.21r11a0501@gcet.edu.in',
        phone: '+91 98480 12345',
        branch: 'CSE',
        academicYear: 4,
        cgpa: 8.75,
        tenth: 92.5,
        inter: 94.0,
        gradYear: 2026,
        status: 'APPROVED',
        isActive: true,
        password: studentSeedPass,
      },
      {
        rollNumber: '21R11A0502',
        fullName: 'S. Sneha Reddy',
        email: 'sneha.21r11a0502@gcet.edu.in',
        phone: '+91 98480 23456',
        branch: 'CSE',
        academicYear: 4,
        cgpa: 9.10,
        tenth: 95.0,
        inter: 96.5,
        gradYear: 2026,
        status: 'APPROVED',
        isActive: true,
        password: studentSeedPass,
      },
      {
        rollNumber: '22R11A0501',
        fullName: 'V. Sai Krishna',
        email: 'saikrishna.22r11a0501@gcet.edu.in',
        phone: '+91 98480 34567',
        branch: 'CSE',
        academicYear: 3,
        cgpa: 8.40,
        tenth: 89.0,
        inter: 91.0,
        gradYear: 2026,
        status: 'APPROVED',
        isActive: true,
        password: studentSeedPass,
      },
      {
        rollNumber: '22R11A0599',
        fullName: 'P. Divya',
        email: 'divya.22r11a0599@gcet.edu.in',
        phone: '+91 98480 45678',
        branch: 'CSE-AIML',
        academicYear: 3,
        cgpa: 8.20,
        tenth: 88.0,
        inter: 89.5,
        gradYear: 2026,
        status: 'PENDING_APPROVAL',
        isActive: false, // Inactive until approved
        password: studentSeedPass,
      },
    ];

    for (const st of seedStudents) {
      const [existingStUser]: any = await pool.query(
        `SELECT id, password_hash, salt FROM users
         WHERE LOWER(username) = LOWER(?) OR LOWER(student_roll_number) = LOWER(?) OR LOWER(email) = LOWER(?)
         LIMIT 1`,
        [st.rollNumber, st.rollNumber, st.email]
      );
      const [existingStudent]: any = await pool.query(
        `SELECT id, user_id, approval_status FROM students WHERE LOWER(roll_number) = LOWER(?) LIMIT 1`,
        [st.rollNumber]
      );

      let resolvedStudentUserId = existingStUser?.[0]?.id || existingStudent?.[0]?.user_id || '';

      if (!existingStUser || existingStUser.length === 0) {
        const userId = resolvedStudentUserId || `usr-${crypto.randomUUID()}`;
        const salt = generateSalt();
        const hash = hashPassword(st.password, salt);
        const effStatus = existingStudent?.[0]?.approval_status || st.status;
        const effActive = effStatus === 'APPROVED' || effStatus === 'ACTIVE';

        await pool.query(
          `INSERT INTO users (
            id, username, email, full_name, role, department, student_roll_number,
            password_hash, salt, status, is_active
          ) VALUES (?, ?, ?, ?, 'STUDENT', ?, ?, ?, ?, ?, ?)`,
          [
            userId,
            st.rollNumber,
            st.email,
            st.fullName,
            st.branch,
            st.rollNumber,
            hash,
            salt,
            effStatus,
            effActive,
          ]
        );
        resolvedStudentUserId = userId;
      } else {
        const row = existingStUser[0];
        resolvedStudentUserId = row.id;
        const currentMatches =
          Boolean(row.password_hash) && verifyPassword(st.password, row.salt || '', row.password_hash);
        const isBcryptCost12 = /^\$2[aby]\$12\$/.test((row.password_hash || '').trim());

        if (!currentMatches || !isBcryptCost12) {
          const newSalt = generateSalt();
          const newHash = hashPassword(st.password, newSalt);
          await pool.query(
            `UPDATE users SET password_hash = ?, salt = ? WHERE id = ?`,
            [newHash, newSalt, row.id]
          );
        }
      }

      if (!existingStudent || existingStudent.length === 0) {
        const studentId = `stu-${crypto.randomUUID()}`;
        await pool.query(
          `INSERT INTO students (
            id, roll_number, student_id_card, full_name, email, phone, branch,
            academic_year, year_of_study, cgpa, tenth_percentage, inter_percentage,
            graduation_year, placement_status, approval_status, user_id
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNPLACED', ?, ?)`,
          [
            studentId,
            st.rollNumber,
            `GCET-${st.gradYear}-${st.rollNumber.slice(-4)}`,
            st.fullName,
            st.email,
            st.phone,
            st.branch,
            st.academicYear,
            st.academicYear,
            st.cgpa,
            st.tenth,
            st.inter,
            st.gradYear,
            st.status,
            resolvedStudentUserId,
          ]
        );
      } else if (!existingStudent[0].user_id && resolvedStudentUserId) {
        await pool.query(
          `UPDATE students SET user_id = ? WHERE id = ?`,
          [resolvedStudentUserId, existingStudent[0].id]
        );
      }

      await pool.query(
        `INSERT IGNORE INTO user_profiles (user_id, phone, location) VALUES (?, ?, 'Hyderabad, Telangana')`,
        [resolvedStudentUserId, st.phone]
      );
    }
  }
}

// Export database pool and state
export function getPool(): mysql.Pool | null {
  return pool;
}

export function isDbConnected(): boolean {
  return isConnected;
}

export async function checkDatabaseHealth() {
  lastCheckTime = new Date().toISOString();
  if (!pool) {
    return getDbStatus();
  }
  try {
    await pool.query('SELECT 1 as ping');
    isConnected = true;
    lastConnectionError = null;
    lastSuccessfulConnectionTime = lastCheckTime;
  } catch (err: any) {
    isConnected = false;
    lastConnectionError = err?.message || String(err);
  }
  return getDbStatus();
}

export function getDbStatus() {
  return {
    connected: isConnected,
    host: DB_CONFIG.host,
    port: DB_CONFIG.port,
    database: DB_CONFIG.database,
    lastCheckTime,
    lastSuccessfulConnectionTime: lastSuccessfulConnectionTime || undefined,
    lastError: lastConnectionError,
    lanIp: getLocalLanIp(),
  };
}

export function getLocalLanIp(): string {
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name] || []) {
      // Skip over non-IPv4 and internal (i.e. 127.0.0.1) addresses
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return 'localhost';
}
