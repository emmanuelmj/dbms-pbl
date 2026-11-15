-- ========================================================
-- GCET PLACEMENT HUB - MySQL 8 Relational Schema
-- Geethanjali College of Engineering and Technology
-- ========================================================

CREATE DATABASE IF NOT EXISTS gcet_placement_hub
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE gcet_placement_hub;

-- 1. Users & RBAC
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    role ENUM('PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN', 'PLACEMENT_OFFICER', 'FACULTY', 'STUDENT', 'COMPANY') NOT NULL,
    department VARCHAR(100),
    student_roll_number VARCHAR(30),
    company_id VARCHAR(64) NULL,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(128) NOT NULL,
    status ENUM('PENDING', 'PENDING_APPROVAL', 'ACTIVE', 'APPROVED', 'REJECTED', 'SUSPENDED') DEFAULT 'ACTIVE',
    is_active BOOLEAN DEFAULT TRUE,
    must_change_password BOOLEAN DEFAULT FALSE,
    last_login TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_role (role),
    INDEX idx_user_status (status)
) ENGINE=InnoDB;

-- 2. Skills Catalog
CREATE TABLE IF NOT EXISTS skills (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(60) NOT NULL,
    description TEXT,
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_category (category),
    INDEX idx_skill_status (status)
) ENGINE=InnoDB;

-- 2b. Faculty Directory & Dual Approval
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
    faculty_approved BOOLEAN DEFAULT FALSE,
    faculty_approver_id VARCHAR(64) NULL,
    faculty_approver_name VARCHAR(100) NULL,
    faculty_approved_at TIMESTAMP NULL,
    admin_approved BOOLEAN DEFAULT FALSE,
    admin_approver_id VARCHAR(64) NULL,
    admin_approver_name VARCHAR(100) NULL,
    admin_approved_at TIMESTAMP NULL,
    status ENUM('PENDING', 'ACTIVE', 'REJECTED') DEFAULT 'ACTIVE',
    rejection_reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_faculty_dept (department),
    INDEX idx_faculty_status (status)
) ENGINE=InnoDB;

-- 3. Students & Approval
CREATE TABLE IF NOT EXISTS students (
    id VARCHAR(64) PRIMARY KEY,
    roll_number VARCHAR(30) NOT NULL UNIQUE,
    student_id_card VARCHAR(50) NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    branch VARCHAR(50) NOT NULL,
    year_of_study INT DEFAULT 1,
    academic_year INT DEFAULT 4,
    section VARCHAR(10) DEFAULT 'A',
    current_semester INT DEFAULT 7,
    cgpa DECIMAL(4, 2) NOT NULL DEFAULT 0.0,
    tenth_percentage DECIMAL(5, 2) NOT NULL DEFAULT 0.0,
    inter_percentage DECIMAL(5, 2) NOT NULL DEFAULT 0.0,
    graduation_year INT NOT NULL DEFAULT 2026,
    backlogs INT DEFAULT 0,
    placement_status ENUM('UNPLACED', 'SHORTLISTED', 'PLACED', 'OPTED_OUT') DEFAULT 'UNPLACED',
    approval_status ENUM('PENDING', 'PENDING_APPROVAL', 'ACTIVE', 'APPROVED', 'REJECTED') DEFAULT 'ACTIVE',
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
    INDEX idx_student_approval (approval_status)
) ENGINE=InnoDB;

-- 4. Student Skills (Proficiency Matrix 1-5, Certifications, Experience)
CREATE TABLE IF NOT EXISTS student_skills (
    student_id VARCHAR(64) NOT NULL,
    skill_id VARCHAR(64) NOT NULL,
    proficiency TINYINT NOT NULL CHECK (proficiency BETWEEN 1 AND 5),
    experience_duration VARCHAR(50) DEFAULT '6 months',
    certification_evidence VARCHAR(255) NULL,
    verified_by_faculty BOOLEAN DEFAULT FALSE,
    verified_by_faculty_id VARCHAR(64) NULL,
    verified_at TIMESTAMP NULL,
    PRIMARY KEY (student_id, skill_id),
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE,
    FOREIGN KEY (verified_by_faculty_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 5. Companies & Approval
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
    tier VARCHAR(50) DEFAULT 'Standard IT',
    user_id VARCHAR(64) NULL,
    approval_status ENUM('PENDING', 'PENDING_APPROVAL', 'ACTIVE', 'APPROVED', 'REJECTED') DEFAULT 'ACTIVE',
    approved_by_admin_id VARCHAR(64) NULL,
    approved_by_admin_name VARCHAR(100) NULL,
    approved_at TIMESTAMP NULL,
    rejection_reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_company_approval (approval_status)
) ENGINE=InnoDB;

-- 6. Placement Drives
CREATE TABLE IF NOT EXISTS placement_drives (
    id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL,
    job_role VARCHAR(150) NOT NULL,
    job_description TEXT,
    salary_package_lpa DECIMAL(5, 2) NOT NULL,
    min_cgpa DECIMAL(4, 2) NOT NULL DEFAULT 6.0,
    max_backlogs_allowed INT DEFAULT 0,
    target_graduation_year INT NOT NULL,
    drive_date DATE NOT NULL,
    application_deadline DATE NOT NULL,
    available_positions INT NOT NULL DEFAULT 1,
    status ENUM('PLANNED', 'UPCOMING', 'OPEN', 'CLOSED', 'COMPLETED', 'CANCELLED') DEFAULT 'UPCOMING',
    selection_process TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    INDEX idx_drive_status (status)
) ENGINE=InnoDB;

-- 7. Applications (10-Stage Pipeline)
CREATE TABLE IF NOT EXISTS applications (
    id VARCHAR(64) PRIMARY KEY,
    drive_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    current_stage ENUM(
        'APPLICATION_SUBMITTED', 'APPLICATION_REVIEWED', 'SHORTLISTED',
        'ONLINE_ASSESSMENT', 'TECHNICAL_INTERVIEW', 'HR_INTERVIEW',
        'SELECTED', 'OFFERED', 'JOINED', 'REJECTED', 'WITHDRAWN'
    ) DEFAULT 'APPLICATION_SUBMITTED',
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    final_offer_lpa DECIMAL(5, 2) NULL,
    rejection_reason TEXT NULL,
    UNIQUE KEY uq_student_drive (student_id, drive_id),
    FOREIGN KEY (drive_id) REFERENCES placement_drives(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 8. Administrator Privilege Requests (Principal Exclusive Decision)
CREATE TABLE IF NOT EXISTS admin_requests (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    applicant_name VARCHAR(100) NOT NULL,
    applicant_email VARCHAR(100) NOT NULL,
    current_role VARCHAR(50) NOT NULL,
    requested_role ENUM('ADMIN', 'PLACEMENT_OFFICER') DEFAULT 'ADMIN',
    department VARCHAR(100),
    reason TEXT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    decided_by_principal_id VARCHAR(64) NULL,
    decided_by_principal_name VARCHAR(100) NULL,
    decision_date TIMESTAMP NULL,
    decision_notes TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_admin_req_status (status)
) ENGINE=InnoDB;

-- 9. Centralized Approval Records Audit Trail
CREATE TABLE IF NOT EXISTS approval_records (
    id VARCHAR(64) PRIMARY KEY,
    request_type ENUM('ADMIN_PRIVILEGE', 'FACULTY_REGISTRATION', 'STUDENT_REGISTRATION', 'COMPANY_REGISTRATION') NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    target_name VARCHAR(150) NOT NULL,
    approver_id VARCHAR(64) NOT NULL,
    approver_name VARCHAR(100) NOT NULL,
    approver_role VARCHAR(50) NOT NULL,
    decision ENUM('APPROVED', 'REJECTED', 'CORRECTION_REQUESTED') NOT NULL,
    comments TEXT,
    decided_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_approval_target (target_id),
    INDEX idx_approval_type (request_type)
) ENGINE=InnoDB;

-- 10. Audit Logging
CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(64) PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    actor_id VARCHAR(64) NULL,
    actor_username VARCHAR(50) NULL,
    actor_role VARCHAR(50) NULL,
    ip_address VARCHAR(45) NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB;

-- 11. Unified User Profiles
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

-- 12. Custom Social / Professional Links
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

-- 13. Student Projects
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
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 14. Student Internships & Work Experience
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
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 15. User Certifications (Students & Faculty)
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

-- 16. Student Achievements, Awards, Workshops & Hackathons
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
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 17. Faculty Publications
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
