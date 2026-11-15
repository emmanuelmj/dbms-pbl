import { Router, Request, Response } from 'express';
import crypto from 'crypto';
import {
  getPool,
  isDbConnected,
  getDbStatus,
  checkDatabaseHealth,
  hashPassword,
  verifyPassword,
  generateSalt,
  normalizeDeptCode,
  getFacultyAuthorizedDepts,
  isFacultyAuthorizedForStudent,
  FACULTY_ASSIGNMENTS,
  getLocalLanIp,
} from './db';
import { registerProfileRoutes, loadFullProfileByUserId } from './profileRoutes';
import { registerWorkspaceRoutes } from './workspaceRoutes';

export const apiRouter = Router();

// In-Memory Active Sessions Store (keyed by secure random token)
interface Session {
  token: string;
  userId: string;
  username: string;
  role: string;
  fullName: string;
  department: string;
  studentRollNumber?: string;
  createdAt: number;
  expiresAt: number;
}

const activeSessions = new Map<string, Session>();

// Helper to authenticate request session token
export function getSessionFromRequest(req: Request): Session | null {
  const authHeader = req.headers.authorization;
  if (!authHeader) return null;
  const token = authHeader.replace(/^Bearer\s+/i, '').trim();
  if (!token) return null;

  const session = activeSessions.get(token);
  if (!session) return null;
  if (Date.now() > session.expiresAt) {
    activeSessions.delete(token);
    return null;
  }
  return session;
}

// --------------------------------------------------------------------------
// 1. HEALTH, DATABASE STATUS & LAN NETWORK DIAGNOSTICS
// --------------------------------------------------------------------------
apiRouter.get('/health', async (_req: Request, res: Response) => {
  const dbStatus = await checkDatabaseHealth();
  res.json({
    status: dbStatus.connected ? 'ok' : 'degraded',
    backend: 'ok',
    timestamp: new Date().toISOString(),
    mysql: dbStatus,
    activeSessions: activeSessions.size,
    lanAccessUrl: `http://${dbStatus.lanIp}:${process.env.PORT || 3000}`,
  });
});

apiRouter.get('/db/status', async (_req: Request, res: Response) => {
  const dbStatus = await checkDatabaseHealth();
  let tableCounts: Record<string, number> = {};

  const pool = getPool();
  if (pool && isDbConnected()) {
    try {
      const [
        [students],
        [users],
        [faculty],
        [companies],
        [drives],
        [skills],
        [applications],
        [approvals],
      ]: any = await Promise.all([
        pool.query('SELECT COUNT(*) as cnt FROM students'),
        pool.query('SELECT COUNT(*) as cnt FROM users'),
        pool.query('SELECT COUNT(*) as cnt FROM faculty'),
        pool.query('SELECT COUNT(*) as cnt FROM companies'),
        pool.query('SELECT COUNT(*) as cnt FROM placement_drives'),
        pool.query('SELECT COUNT(*) as cnt FROM skills'),
        pool.query('SELECT COUNT(*) as cnt FROM applications'),
        pool.query('SELECT COUNT(*) as cnt FROM approval_records'),
      ]);
      tableCounts = {
        users: users[0]?.cnt || 0,
        students: students[0]?.cnt || 0,
        faculty: faculty[0]?.cnt || 0,
        companies: companies[0]?.cnt || 0,
        placement_drives: drives[0]?.cnt || 0,
        skills: skills[0]?.cnt || 0,
        applications: applications[0]?.cnt || 0,
        approval_records: approvals[0]?.cnt || 0,
      };
    } catch {
      // Ignore count errors if pool is busy
    }
  }

  res.json({
    ...dbStatus,
    tableCounts,
  });
});

// --------------------------------------------------------------------------
// 2. STUDENT REGISTRATION (Saves directly to MySQL)
// --------------------------------------------------------------------------
apiRouter.post('/auth/register-student', async (req: Request, res: Response) => {
  try {
    const {
      fullName,
      rollNumber,
      email,
      phone,
      branch,
      academicYear,
      cgpa,
      tenthPercentage,
      interPercentage,
      graduationYear,
      password,
      confirmPassword,
      skills,
    } = req.body;

    // 1. Validation
    if (!fullName || !fullName.trim()) {
      return res.status(400).json({ error: 'Full Name is required.' });
    }
    const cleanRoll = (rollNumber || '').trim().toUpperCase();
    if (!cleanRoll) {
      return res.status(400).json({ error: 'Student Roll Number is required.' });
    }
    const cleanEmail = (email || '').trim().toLowerCase();
    if (!cleanEmail) {
      return res.status(400).json({ error: 'College email is required.' });
    }
    if (!branch || !branch.trim()) {
      return res.status(400).json({ error: 'Department/Branch is required.' });
    }
    if (!password || password.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters in length.' });
    }
    if (confirmPassword !== undefined && password !== confirmPassword) {
      return res.status(400).json({ error: 'Passwords do not match.' });
    }

    const parsedCgpa = parseFloat(cgpa);
    if (isNaN(parsedCgpa) || parsedCgpa < 0 || parsedCgpa > 10) {
      return res.status(400).json({ error: 'CGPA must be a valid number between 0.00 and 10.00.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({
        error: 'MySQL database is currently in standby. Please ensure MySQL is running on localhost:3306 on your laptop.',
        mysqlStatus: getDbStatus(),
      });
    }

    // 2. Check for duplicate roll number or email in MySQL
    const [dupRoll]: any = await pool.query(
      'SELECT id, full_name FROM students WHERE LOWER(roll_number) = LOWER(?)',
      [cleanRoll]
    );
    if (dupRoll.length > 0) {
      return res.status(409).json({
        error: `Roll number ${cleanRoll} is already registered to ${dupRoll[0].full_name}.`,
      });
    }

    const [dupEmail]: any = await pool.query(
      'SELECT id, username FROM users WHERE LOWER(email) = LOWER(?)',
      [cleanEmail]
    );
    if (dupEmail.length > 0) {
      return res.status(409).json({
        error: `Email address ${cleanEmail} is already registered with account (${dupEmail[0].username}).`,
      });
    }

    // 3. PBKDF2 Password Hashing
    const salt = generateSalt();
    const passwordHash = hashPassword(password, salt);
    const studentId = `stu-${crypto.randomUUID()}`;
    const userId = `usr-${crypto.randomUUID()}`;
    const nowIso = new Date();

    // 4. Transaction execution in MySQL
    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();

      // Insert Student Record with status PENDING_APPROVAL
      await conn.query(
        `INSERT INTO students (
          id, roll_number, student_id_card, full_name, email, phone, branch,
          academic_year, year_of_study, cgpa, tenth_percentage, inter_percentage,
          graduation_year, placement_status, approval_status, user_id, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNPLACED', 'PENDING_APPROVAL', ?, ?)`,
        [
          studentId,
          cleanRoll,
          `GCET-${graduationYear || 2026}-${cleanRoll.slice(-4)}`,
          fullName.trim(),
          cleanEmail,
          phone ? phone.trim() : '+91 98765 00000',
          branch.trim(),
          academicYear ? parseInt(academicYear, 10) : 4,
          academicYear ? parseInt(academicYear, 10) : 4,
          parsedCgpa,
          tenthPercentage ? parseFloat(tenthPercentage) : 85.0,
          interPercentage ? parseFloat(interPercentage) : 88.0,
          graduationYear ? parseInt(graduationYear, 10) : 2026,
          userId,
          nowIso,
        ]
      );

      // Insert User Account with status PENDING_APPROVAL and is_active = FALSE
      await conn.query(
        `INSERT INTO users (
          id, username, email, full_name, role, department, student_roll_number,
          password_hash, salt, status, is_active, created_at
        ) VALUES (?, ?, ?, ?, 'STUDENT', ?, ?, ?, ?, 'PENDING_APPROVAL', FALSE, ?)`,
        [
          userId,
          cleanRoll,
          cleanEmail,
          fullName.trim(),
          branch.trim(),
          cleanRoll,
          passwordHash,
          salt,
          nowIso,
        ]
      );

      // Log in audit trail
      await conn.query(
        `INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details, created_at)
         VALUES (?, 'STUDENT_REGISTERED', ?, ?, 'STUDENT', ?, ?)`,
        [
          crypto.randomUUID(),
          userId,
          cleanRoll,
          `New student registration submitted: ${fullName.trim()} (${cleanRoll}) - Department: ${branch.trim()} - Awaiting Faculty Approval`,
          nowIso,
        ]
      );

      await conn.commit();
    } catch (txErr) {
      await conn.rollback();
      throw txErr;
    } finally {
      conn.release();
    }

    // Determine assigned faculty coordinator for feedback
    let assignedFacultyName = 'Department Faculty Coordinator';
    const normalizedBranch = normalizeDeptCode(branch);
    if (['CSE', 'CSE-AIML', 'CSE-DS'].includes(normalizedBranch)) {
      assignedFacultyName = 'B. Laxmi Lavanya (CSE / AIML / DS Coordinator)';
    } else if (['CSE-CS', 'IT'].includes(normalizedBranch)) {
      assignedFacultyName = 'P. Aparna (Cyber Security & IT Coordinator)';
    } else if (['EEE', 'ECE', 'MECH', 'CIVIL'].includes(normalizedBranch)) {
      assignedFacultyName = 'S. Raja Shakar Reddy (Core Engineering Coordinator)';
    }

    return res.status(201).json({
      success: true,
      message: `Registration submitted successfully. Your student account is now awaiting departmental verification by ${assignedFacultyName}.`,
      studentId,
      rollNumber: cleanRoll,
      status: 'PENDING_APPROVAL',
      assignedFaculty: assignedFacultyName,
    });
  } catch (err: any) {
    console.error('Registration error:', err);
    return res.status(500).json({ error: err.message || 'Server error during student registration.' });
  }
});

// --------------------------------------------------------------------------
// 3. FACULTY REGISTRATION (Saves directly to MySQL - Awaiting Admin Approval)
// --------------------------------------------------------------------------
apiRouter.post('/auth/register-faculty', async (req: Request, res: Response) => {
  try {
    const { fullName, employeeId, email, department, designation, phone, password } = req.body;
    if (!fullName || !fullName.trim()) return res.status(400).json({ error: 'Full Name is required.' });
    const cleanEmpId = (employeeId || '').trim().toUpperCase();
    if (!cleanEmpId) return res.status(400).json({ error: 'Faculty Employee ID is required.' });
    const cleanEmail = (email || '').trim().toLowerCase();
    if (!cleanEmail) return res.status(400).json({ error: 'Official college email is required.' });
    if (!department || !department.trim()) return res.status(400).json({ error: 'Department is required.' });
    if (!designation || !designation.trim()) return res.status(400).json({ error: 'Designation is required.' });
    if (!password || password.length < 6) return res.status(400).json({ error: 'Password must be at least 6 characters in length.' });

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database is currently in standby.' });
    }

    const [dupEmp]: any = await pool.query(
      'SELECT id FROM users WHERE LOWER(username) = LOWER(?)',
      [cleanEmpId]
    );
    if (dupEmp.length > 0) {
      return res.status(409).json({ error: `Faculty ID ${cleanEmpId} is already registered.` });
    }

    const [dupEmail]: any = await pool.query(
      'SELECT id FROM users WHERE LOWER(email) = LOWER(?)',
      [cleanEmail]
    );
    if (dupEmail.length > 0) {
      return res.status(409).json({ error: `Email address ${cleanEmail} is already registered.` });
    }

    const salt = generateSalt();
    const hash = hashPassword(password, salt);
    const userId = `usr-fac-${crypto.randomUUID()}`;
    const facId = `fac-${crypto.randomUUID()}`;
    const nowIso = new Date();

    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();
      await conn.query(
        `INSERT INTO users (id, username, email, full_name, role, department, password_hash, salt, status, is_active, created_at)
         VALUES (?, ?, ?, ?, 'FACULTY', ?, ?, ?, 'PENDING_APPROVAL', FALSE, ?)`,
        [userId, cleanEmpId.toLowerCase(), cleanEmail, fullName.trim(), department.trim(), hash, salt, nowIso]
      );

      await conn.query(
        `INSERT INTO faculty (id, faculty_id, employee_id, full_name, department, designation, email, phone, user_id, faculty_approved, admin_approved, status, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, FALSE, 'PENDING_APPROVAL', ?)`,
        [facId, cleanEmpId, cleanEmpId, fullName.trim(), department.trim(), designation.trim(), cleanEmail, phone ? phone.trim() : '+91 98765 00000', userId, nowIso]
      );

      await conn.query(
        `INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details, created_at)
         VALUES (?, 'FACULTY_REGISTERED', ?, ?, 'FACULTY', ?, ?)`,
        [crypto.randomUUID(), userId, cleanEmpId, `New faculty registration: ${fullName.trim()} (${cleanEmpId}) - Awaiting College Administrator Approval`, nowIso]
      );
      await conn.commit();
    } catch (err) {
      await conn.rollback();
      throw err;
    } finally {
      conn.release();
    }

    return res.status(201).json({
      success: true,
      message: 'Faculty registration submitted successfully. Your account is awaiting clearance from College Administration.',
      employeeId: cleanEmpId,
      status: 'PENDING_APPROVAL',
    });
  } catch (err: any) {
    console.error('Faculty registration error:', err);
    return res.status(500).json({ error: err.message || 'Server error during faculty registration.' });
  }
});

// --------------------------------------------------------------------------
// 4. COMPANY REGISTRATION (Saves directly to MySQL - Awaiting Admin Approval)
// --------------------------------------------------------------------------
apiRouter.post('/auth/register-company', async (req: Request, res: Response) => {
  try {
    const { name, industry, website, location, hrName, hrEmail, hrPhone, tier, password } = req.body;
    if (!name || !name.trim()) return res.status(400).json({ error: 'Company Name is required.' });
    if (!hrName || !hrName.trim()) return res.status(400).json({ error: 'HR Contact Name is required.' });
    const cleanEmail = (hrEmail || '').trim().toLowerCase();
    if (!cleanEmail) return res.status(400).json({ error: 'Corporate HR email is required.' });
    if (!password || String(password).length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters in length.' });
    }
    const effectivePassword = String(password);

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database is currently in standby.' });
    }

    const companyUsername = `recruiter_${name.trim().toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 15)}`;
    const [dupUser]: any = await pool.query(
      'SELECT id FROM users WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?)',
      [cleanEmail, companyUsername]
    );
    if (dupUser.length > 0) {
      return res.status(409).json({ error: `An account with email ${cleanEmail} or company name already exists.` });
    }

    const salt = generateSalt();
    const hash = hashPassword(effectivePassword, salt);
    const userId = `usr-comp-${crypto.randomUUID()}`;
    const compId = `comp-${crypto.randomUUID()}`;
    const nowIso = new Date();

    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();
      await conn.query(
        `INSERT INTO companies (id, name, industry, website, location, description, hr_name, hr_email, hr_phone, tier, approval_status, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_APPROVAL', ?)`,
        [compId, name.trim(), industry || 'IT / Software Services', website || '', location || 'Hyderabad, Telangana', `Recruitment partnership for ${name.trim()}`, hrName.trim(), cleanEmail, hrPhone || '+91 98480 00000', tier || 'Standard IT', nowIso]
      );

      await conn.query(
        `INSERT INTO users (id, username, email, full_name, role, department, company_id, password_hash, salt, status, is_active, created_at)
         VALUES (?, ?, ?, ?, 'COMPANY', ?, ?, ?, ?, 'PENDING_APPROVAL', FALSE, ?)`,
        [userId, companyUsername, cleanEmail, `${hrName.trim()} (${name.trim()})`, name.trim(), compId, hash, salt, nowIso]
      );

      await conn.query(
        `INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details, created_at)
         VALUES (?, 'COMPANY_REGISTERED', ?, ?, 'COMPANY', ?, ?)`,
        [crypto.randomUUID(), userId, companyUsername, `New corporate recruiter registration: ${name.trim()} - Awaiting College Placement Admin Approval`, nowIso]
      );
      await conn.commit();
    } catch (err) {
      await conn.rollback();
      throw err;
    } finally {
      conn.release();
    }

    return res.status(201).json({
      success: true,
      message: 'Company registration submitted successfully. Your corporate profile is awaiting verification by College Placement Administration.',
      username: companyUsername,
      status: 'PENDING_APPROVAL',
    });
  } catch (err: any) {
    console.error('Company registration error:', err);
    return res.status(500).json({ error: err.message || 'Server error during company registration.' });
  }
});

// --------------------------------------------------------------------------
// 5. SECURE MYSQL AUTHENTICATION (All 4 Portals: Admin, Faculty, Students, Companies)
// --------------------------------------------------------------------------
apiRouter.post('/auth/login', async (req: Request, res: Response) => {
  try {
    const { username, password, portalRole } = req.body;
    const isStudentPortal = portalRole === 'STUDENT' || portalRole === 'STUDENTS';

    if (!username || !username.trim()) {
      return res.status(400).json({
        error: isStudentPortal ? 'Student Roll Number is required.' : 'Username or Registered ID is required.',
      });
    }
    if (!password) {
      return res.status(400).json({ error: 'Please enter your password.' });
    }

    const cleanUsername = username.trim();
    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({
        error: 'MySQL database is currently in standby. Please ensure MySQL is running on localhost:3306 on your laptop.',
        mysqlStatus: getDbStatus(),
      });
    }

    // 1. Prepared SQL query checking by username, student_roll_number, or email (exact username match first)
    let [userRows]: any = await pool.query(
      `SELECT * FROM users
       WHERE LOWER(username) = LOWER(?) OR LOWER(student_roll_number) = LOWER(?) OR LOWER(email) = LOWER(?)
       ORDER BY (LOWER(username) = LOWER(?)) DESC, (LOWER(student_roll_number) = LOWER(?)) DESC
       LIMIT 1`,
      [cleanUsername, cleanUsername, cleanUsername, cleanUsername, cleanUsername]
    );

    if (!userRows || userRows.length === 0) {
      const [facLoginRows]: any = await pool.query(
        `SELECT u.* FROM users u
         INNER JOIN faculty f ON f.user_id = u.id
         WHERE LOWER(f.faculty_id) = LOWER(?) OR LOWER(f.employee_id) = LOWER(?)
         LIMIT 1`,
        [cleanUsername, cleanUsername]
      );
      if (facLoginRows && facLoginRows.length > 0) {
        userRows = facLoginRows;
      }
    }

    let user = userRows[0];
    let studentRecord: any = null;

    // Check student record by roll number if applicable
    const [studentRows]: any = await pool.query(
      `SELECT * FROM students WHERE LOWER(roll_number) = LOWER(?) LIMIT 1`,
      [cleanUsername]
    );
    if (studentRows.length > 0) {
      studentRecord = studentRows[0];
    }

    const invalidCredentialsMsg = 'Incorrect username or password. Please try again.';

    // If user record not found: uniform error message to avoid account enumeration
    if (!user) {
      return res.status(401).json({
        error: invalidCredentialsMsg,
      });
    }

    // 2. Strict backend verification against stored password hash
    if (!user.password_hash) {
      return res.status(401).json({
        error: invalidCredentialsMsg,
      });
    }

    const isPasswordCorrect = verifyPassword(password, user.salt || '', user.password_hash);
    if (!isPasswordCorrect) {
      return res.status(401).json({
        error: invalidCredentialsMsg,
      });
    }

    // 3. Portal permission match check (if portalRole specified)
    if (portalRole) {
      const pRole = portalRole.toUpperCase();
      if ((pRole === 'STUDENT' || pRole === 'STUDENTS') && user.role !== 'STUDENT') {
        return res.status(403).json({ error: 'This account does not have permission to access the Student Portal.' });
      }
      if (pRole === 'FACULTY' && user.role !== 'FACULTY' && user.role !== 'PLACEMENT_OFFICER') {
        return res.status(403).json({ error: 'This account does not have permission to access the Faculty Portal.' });
      }
      if ((pRole === 'COMPANY' || pRole === 'COMPANIES') && user.role !== 'COMPANY') {
        return res.status(403).json({ error: 'This account does not have permission to access the Corporate Recruitment Portal.' });
      }
      if (pRole === 'ADMIN' && !['PRINCIPAL', 'VICE_CHAIRMAN', 'CHAIRMAN', 'ADMIN', 'PLACEMENT_OFFICER'].includes(user.role)) {
        return res.status(403).json({ error: 'This account does not have administrative privileges.' });
      }
    }

    // 4. Approval status checks
    const status = (user.status || '').toUpperCase();

    if (user.role === 'STUDENT') {
      const studentApproval = (studentRecord?.approval_status || status).toUpperCase();
      const isApproved = studentApproval === 'APPROVED' || studentApproval === 'ACTIVE' || status === 'APPROVED' || status === 'ACTIVE';

      if (!isApproved) {
        if (studentApproval === 'REJECTED' || status === 'REJECTED') {
          return res.status(403).json({
            error: 'Your registration was declined. Please contact your Department Faculty Coordinator.',
            status: 'REJECTED',
          });
        }
        return res.status(403).json({
          error: 'Your registration is awaiting faculty approval.',
          status: 'PENDING_APPROVAL',
        });
      }

      // Self-heal active flag in MySQL if approved
      if (user.is_active === 0) {
        await pool.query(`UPDATE users SET is_active = TRUE, status = 'APPROVED' WHERE id = ?`, [user.id]);
        user.is_active = 1;
        user.status = 'APPROVED';
      }
    } else if (user.role === 'FACULTY') {
      if (status === 'PENDING' || status === 'PENDING_APPROVAL') {
        return res.status(403).json({
          error: 'Your faculty registration is awaiting administrator approval.',
          status: 'PENDING_APPROVAL',
        });
      }
      if (status === 'REJECTED') {
        return res.status(403).json({
          error: 'Your account registration was not approved. Please contact the administrator.',
          status: 'REJECTED',
        });
      }
    } else if (user.role === 'COMPANY') {
      if (status === 'PENDING' || status === 'PENDING_APPROVAL') {
        return res.status(403).json({
          error: 'Your company registration is awaiting administrator approval.',
          status: 'PENDING_APPROVAL',
        });
      }
      if (status === 'REJECTED') {
        return res.status(403).json({
          error: 'Your account registration was not approved. Please contact the administrator.',
          status: 'REJECTED',
        });
      }
    } else {
      if (status === 'PENDING' || status === 'PENDING_APPROVAL') {
        return res.status(403).json({
          error: 'Your administrator registration is awaiting Principal approval.',
          status: 'PENDING_APPROVAL',
        });
      }
      if (status === 'REJECTED') {
        return res.status(403).json({
          error: 'Your account registration was not approved. Please contact the administrator.',
          status: 'REJECTED',
        });
      }
    }

    // 5. Account active condition check
    if (user.is_active === 0 || user.is_active === false) {
      return res.status(403).json({
        error: 'Your account has been deactivated. Contact the administrator.',
      });
    }

    // 6. Update last login timestamp in MySQL
    await pool.query('UPDATE users SET last_login = NOW() WHERE id = ?', [user.id]);

    // 7. Create secure session token stored server-side
    const token = `gcet_sec_${crypto.randomBytes(32).toString('hex')}`;
    const session: Session = {
      token,
      userId: user.id,
      username: user.username,
      role: user.role,
      fullName: user.full_name,
      department: user.department,
      studentRollNumber: user.student_roll_number,
      createdAt: Date.now(),
      expiresAt: Date.now() + 24 * 60 * 60 * 1000,
    };

    activeSessions.set(token, session);

    // Return sanitized user object - NEVER expose password_hash or salt!
    const [profRows]: any = await pool.query('SELECT profile_photo, phone, designation, location, about_me FROM user_profiles WHERE user_id = ? LIMIT 1', [user.id]);
    const prof = profRows?.[0] || {};

    return res.json({
      success: true,
      token,
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        fullName: user.full_name,
        role: user.role,
        department: user.department,
        designation: prof.designation || undefined,
        phone: prof.phone || undefined,
        location: prof.location || undefined,
        aboutMe: prof.about_me || undefined,
        profilePhoto: prof.profile_photo || undefined,
        studentRollNumber: user.student_roll_number,
        companyId: user.company_id,
        status: user.status,
        isActive: !!user.is_active,
        mustChangePassword: Boolean(user.must_change_password),
        authorizedDepartments: user.authorized_departments
          ? (typeof user.authorized_departments === 'string'
              ? JSON.parse(user.authorized_departments)
              : user.authorized_departments)
          : getFacultyAuthorizedDepts(user.username),
      },
      studentRecord,
    });
  } catch (err: any) {
    console.error('Login error:', err);
    return res.status(500).json({ error: err.message || 'Authentication error.' });
  }
});

// --------------------------------------------------------------------------
// 4. CURRENT SESSION VERIFICATION & LOGOUT
// --------------------------------------------------------------------------
apiRouter.get('/auth/me', async (req: Request, res: Response) => {
  const session = getSessionFromRequest(req);
  if (!session) {
    return res.status(401).json({ error: 'Unauthenticated or session expired.' });
  }

  const pool = getPool();
  if (pool && isDbConnected()) {
    try {
      const [rows]: any = await pool.query('SELECT * FROM users WHERE id = ?', [session.userId]);
      if (rows.length > 0) {
        const u = rows[0];
        const [profRows]: any = await pool.query('SELECT profile_photo, phone, designation, location, about_me FROM user_profiles WHERE user_id = ? LIMIT 1', [u.id]);
        const prof = profRows?.[0] || {};
        let studentRecord: any = null;
        if (u.role === 'STUDENT' && u.student_roll_number) {
          const [stRows]: any = await pool.query('SELECT * FROM students WHERE LOWER(roll_number) = LOWER(?)', [u.student_roll_number]);
          if (stRows.length > 0) studentRecord = stRows[0];
        }
        return res.json({
          user: {
            id: u.id,
            username: u.username,
            email: u.email,
            fullName: u.full_name,
            role: u.role,
            department: u.department,
            designation: prof.designation || undefined,
            phone: prof.phone || undefined,
            location: prof.location || undefined,
            aboutMe: prof.about_me || undefined,
            profilePhoto: prof.profile_photo || undefined,
            studentRollNumber: u.student_roll_number,
            companyId: u.company_id,
            status: u.status,
            isActive: !!u.is_active,
            mustChangePassword: Boolean(u.must_change_password),
            authorizedDepartments: u.authorized_departments
              ? (typeof u.authorized_departments === 'string'
                  ? JSON.parse(u.authorized_departments)
                  : u.authorized_departments)
              : getFacultyAuthorizedDepts(u.username),
          },
          studentRecord,
        });
      }
    } catch {
      // Fallback to session metadata
    }
  }

  return res.json({
    user: {
      id: session.userId,
      username: session.username,
      fullName: session.fullName,
      role: session.role,
      department: session.department,
      studentRollNumber: session.studentRollNumber,
      status: 'APPROVED',
      isActive: true,
      mustChangePassword: false,
      authorizedDepartments: getFacultyAuthorizedDepts(session.username),
    },
  });
});

apiRouter.post('/auth/change-password', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    if (!session) {
      return res.status(401).json({ error: 'Authentication required. Please log in.' });
    }

    const { currentPassword, newPassword, confirmPassword } = req.body;
    if (!currentPassword) {
      return res.status(400).json({ error: 'Current / temporary password is required.' });
    }
    if (!newPassword || String(newPassword).length < 6) {
      return res.status(400).json({ error: 'New password must be at least 6 characters long.' });
    }
    if (confirmPassword !== undefined && newPassword !== confirmPassword) {
      return res.status(400).json({ error: 'New password and confirmation do not match.' });
    }
    if (currentPassword === newPassword) {
      return res.status(400).json({ error: 'New password must be different from your current password.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.', mysqlStatus: getDbStatus() });
    }

    const [rows]: any = await pool.query(
      `SELECT id, username, email, full_name, role, department, student_roll_number, company_id,
              status, is_active, authorized_departments, password_hash, salt, must_change_password
       FROM users WHERE id = ? LIMIT 1`,
      [session.userId]
    );
    if (!rows || rows.length === 0) {
      return res.status(404).json({ error: 'User account not found in MySQL.' });
    }

    const user = rows[0];
    if (!user.password_hash || !verifyPassword(currentPassword, user.salt || '', user.password_hash)) {
      return res.status(401).json({ error: 'Current / temporary password is incorrect.' });
    }

    const newSalt = generateSalt();
    const newHash = hashPassword(String(newPassword), newSalt);

    await pool.query(
      `UPDATE users SET password_hash = ?, salt = ?, must_change_password = FALSE WHERE id = ?`,
      [newHash, newSalt, user.id]
    );

    await pool.query(
      `INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details, created_at)
       VALUES (?, 'PASSWORD_CHANGED', ?, ?, ?, ?, NOW())`,
      [crypto.randomUUID(), user.id, user.username, user.role, `User @${user.username} updated their password.`]
    ).catch(() => {});

    const [profRows]: any = await pool.query(
      'SELECT profile_photo, phone, designation, location, about_me FROM user_profiles WHERE user_id = ? LIMIT 1',
      [user.id]
    );
    const prof = profRows?.[0] || {};

    return res.json({
      success: true,
      message: 'Password updated successfully.',
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        fullName: user.full_name,
        role: user.role,
        department: user.department,
        designation: prof.designation || undefined,
        phone: prof.phone || undefined,
        location: prof.location || undefined,
        aboutMe: prof.about_me || undefined,
        profilePhoto: prof.profile_photo || undefined,
        studentRollNumber: user.student_roll_number,
        companyId: user.company_id,
        status: user.status,
        isActive: !!user.is_active,
        mustChangePassword: false,
        authorizedDepartments: user.authorized_departments
          ? (typeof user.authorized_departments === 'string'
              ? JSON.parse(user.authorized_departments)
              : user.authorized_departments)
          : getFacultyAuthorizedDepts(user.username),
      },
    });
  } catch (err: any) {
    console.error('Change password error:', err);
    return res.status(500).json({ error: err.message || 'Unable to update password.' });
  }
});

apiRouter.post('/auth/logout', (req: Request, res: Response) => {
  const authHeader = req.headers.authorization;
  if (authHeader) {
    const token = authHeader.replace(/^Bearer\s+/i, '').trim();
    activeSessions.delete(token);
  }
  return res.json({ success: true, message: 'Logged out successfully.' });
});

// --------------------------------------------------------------------------
// 5. FACULTY APPROVAL ENDPOINTS (Transactional MySQL Updates)
// --------------------------------------------------------------------------
apiRouter.get('/approvals/pending-students', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.', mysqlStatus: getDbStatus() });
    }

    let authorizedDepts: string[] = [];
    if (session && session.role === 'FACULTY') {
      authorizedDepts = getFacultyAuthorizedDepts(session.username || session.userId);
    }

    let query = `
      SELECT s.*, u.id as user_id, u.status as user_status
      FROM students s
      LEFT JOIN users u ON LOWER(u.username) = LOWER(s.roll_number)
      WHERE s.approval_status IN ('PENDING', 'PENDING_APPROVAL')
    `;
    const params: any[] = [];

    // Filter by assigned departments for faculty coordinator
    if (authorizedDepts.length > 0) {
      query += ` AND s.branch IN (?)`;
      params.push(authorizedDepts);
    }

    query += ` ORDER BY s.created_at DESC`;

    const [rows]: any = await pool.query(query, params);
    return res.json({ students: rows, authorizedDepts });
  } catch (err: any) {
    console.error('Fetch pending students error:', err);
    return res.status(500).json({ error: err.message });
  }
});

// Approve Student in MySQL
apiRouter.post('/approvals/approve-student', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    const { studentIdOrRoll } = req.body;
    if (!studentIdOrRoll) {
      return res.status(400).json({ error: 'Student ID or Roll Number is required.' });
    }

    // Verify session
    if (!session) {
      return res.status(401).json({ error: 'Faculty authorization required. Please log in.' });
    }

    // Strict Rule: Administrators must NOT approve student registrations
    if (['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(session.role)) {
      return res.status(403).json({
        error: 'Strict Institutional Rule: Administrators must NOT approve student registrations. Only authorized Department Faculty Mentors may approve student accounts.',
      });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.', mysqlStatus: getDbStatus() });
    }

    // Retrieve Student Record from MySQL
    const [stRows]: any = await pool.query(
      'SELECT * FROM students WHERE id = ? OR LOWER(roll_number) = LOWER(?)',
      [studentIdOrRoll, studentIdOrRoll]
    );

    if (stRows.length === 0) {
      return res.status(404).json({ error: `Student record "${studentIdOrRoll}" not found in MySQL database.` });
    }

    const student = stRows[0];

    // Department Authorization Check
    if (session.role === 'FACULTY') {
      if (!isFacultyAuthorizedForStudent(session.username || session.userId, student.branch)) {
        const myDepts = getFacultyAuthorizedDepts(session.username).join(', ');
        return res.status(403).json({
          error: `Unauthorized Department: You (${session.fullName}) are assigned to [${myDepts}]. You cannot approve student ${student.full_name} from department "${student.branch}".`,
        });
      }
    }

    // Execute atomic MySQL transaction
    const conn = await pool.getConnection();
    const nowIso = new Date();

    try {
      await conn.beginTransaction();

      // 1. Update Student Table
      await conn.query(
        `UPDATE students 
         SET approval_status = 'APPROVED',
             approved_by_faculty_id = ?,
             approved_by_faculty_name = ?,
             approved_at = ?
         WHERE id = ?`,
        [session.userId, session.fullName, nowIso, student.id]
      );

      // 2. Update Users Table (resolves user by student's roll number or ID)
      await conn.query(
        `UPDATE users
         SET status = 'APPROVED',
             is_active = TRUE,
             approved_by = ?,
             approved_at = ?
         WHERE LOWER(username) = LOWER(?) OR LOWER(student_roll_number) = LOWER(?)`,
        [session.fullName, nowIso, student.roll_number, student.roll_number]
      );

      // 3. Add to immutable approval_records log
      await conn.query(
        `INSERT INTO approval_records (
          id, request_type, target_id, target_name, student_roll_number, department,
          approver_id, approver_name, approver_role, decision, comments, decided_at
        ) VALUES (?, 'STUDENT_REGISTRATION', ?, ?, ?, ?, ?, ?, 'FACULTY', 'APPROVED', ?, ?)`,
        [
          crypto.randomUUID(),
          student.id,
          student.full_name,
          student.roll_number,
          student.branch,
          session.userId,
          session.fullName,
          `Faculty approval verified for ${student.branch}. Full portal access enabled.`,
          nowIso,
        ]
      );

      // 4. Record Audit Log
      await conn.query(
        `INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details, created_at)
         VALUES (?, 'STUDENT_APPROVED', ?, ?, 'FACULTY', ?, ?)`,
        [
          crypto.randomUUID(),
          session.userId,
          session.username,
          `Faculty ${session.fullName} approved student registration: ${student.full_name} (${student.roll_number}) [${student.branch}]`,
          nowIso,
        ]
      );

      await conn.commit();
    } catch (txErr) {
      await conn.rollback();
      throw txErr;
    } finally {
      conn.release();
    }

    return res.json({
      success: true,
      message: `Student ${student.full_name} (${student.roll_number}) approved successfully. Account is now ACTIVE in MySQL.`,
      studentId: student.id,
      rollNumber: student.roll_number,
      approvalStatus: 'APPROVED',
      approvedBy: session.fullName,
      approvedAt: nowIso.toISOString(),
    });
  } catch (err: any) {
    console.error('Approve student error:', err);
    return res.status(500).json({ error: err.message || 'Error processing student approval in MySQL.' });
  }
});

// Bulk Batch Approval
apiRouter.post('/approvals/batch-approve', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    const { studentIdsOrRolls } = req.body;
    if (!studentIdsOrRolls || !Array.isArray(studentIdsOrRolls) || studentIdsOrRolls.length === 0) {
      return res.status(400).json({ error: 'Array of student IDs or Roll Numbers is required.' });
    }

    if (!session) {
      return res.status(401).json({ error: 'Faculty authorization required.' });
    }

    if (['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(session.role)) {
      return res.status(403).json({ error: 'Administrators must NOT approve student registrations.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.', mysqlStatus: getDbStatus() });
    }

    const conn = await pool.getConnection();
    let approvedCount = 0;
    const nowIso = new Date();

    try {
      await conn.beginTransaction();

      for (const target of studentIdsOrRolls) {
        const [stRows]: any = await conn.query(
          'SELECT * FROM students WHERE id = ? OR LOWER(roll_number) = LOWER(?)',
          [target, target]
        );
        if (stRows.length === 0) continue;
        const student = stRows[0];

        // Verify departmental permission
        if (session.role === 'FACULTY') {
          if (!isFacultyAuthorizedForStudent(session.username, student.branch)) {
            continue; // Skip unauthorized
          }
        }

        await conn.query(
          `UPDATE students 
           SET approval_status = 'APPROVED', approved_by_faculty_id = ?, approved_by_faculty_name = ?, approved_at = ?
           WHERE id = ?`,
          [session.userId, session.fullName, nowIso, student.id]
        );

        await conn.query(
          `UPDATE users SET status = 'APPROVED', is_active = TRUE, approved_by = ?, approved_at = ?
           WHERE LOWER(username) = LOWER(?) OR LOWER(student_roll_number) = LOWER(?)`,
          [session.fullName, nowIso, student.roll_number, student.roll_number]
        );

        await conn.query(
          `INSERT INTO approval_records (id, request_type, target_id, target_name, student_roll_number, department, approver_id, approver_name, approver_role, decision, comments, decided_at)
           VALUES (?, 'STUDENT_REGISTRATION', ?, ?, ?, ?, ?, ?, 'FACULTY', 'APPROVED', 'Batch approved by departmental coordinator', ?)`,
          [crypto.randomUUID(), student.id, student.full_name, student.roll_number, student.branch, session.userId, session.fullName, nowIso]
        );

        approvedCount++;
      }

      await conn.commit();
    } catch (txErr) {
      await conn.rollback();
      throw txErr;
    } finally {
      conn.release();
    }

    return res.json({
      success: true,
      message: `Successfully approved ${approvedCount} candidates in MySQL. Their accounts are now active.`,
      count: approvedCount,
    });
  } catch (err: any) {
    console.error('Batch approve error:', err);
    return res.status(500).json({ error: err.message });
  }
});

// Reject Student in MySQL
apiRouter.post('/approvals/reject-student', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    const { studentIdOrRoll, reason } = req.body;
    if (!studentIdOrRoll) {
      return res.status(400).json({ error: 'Student ID or Roll Number is required.' });
    }

    if (!session) {
      return res.status(401).json({ error: 'Faculty authorization required.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.' });
    }

    const [stRows]: any = await pool.query(
      'SELECT * FROM students WHERE id = ? OR LOWER(roll_number) = LOWER(?)',
      [studentIdOrRoll, studentIdOrRoll]
    );

    if (stRows.length === 0) {
      return res.status(404).json({ error: 'Student record not found in MySQL.' });
    }

    const student = stRows[0];
    const rejectReason = reason || 'Registration details did not meet departmental criteria.';
    const nowIso = new Date();

    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();

      await conn.query(
        `UPDATE students SET approval_status = 'REJECTED', rejection_reason = ? WHERE id = ?`,
        [rejectReason, student.id]
      );

      await conn.query(
        `UPDATE users SET status = 'REJECTED', is_active = FALSE, rejection_reason = ? WHERE LOWER(username) = LOWER(?)`,
        [rejectReason, student.roll_number]
      );

      await conn.query(
        `INSERT INTO approval_records (id, request_type, target_id, target_name, student_roll_number, department, approver_id, approver_name, approver_role, decision, comments, decided_at)
         VALUES (?, 'STUDENT_REGISTRATION', ?, ?, ?, ?, ?, ?, 'FACULTY', 'REJECTED', ?, ?)`,
        [crypto.randomUUID(), student.id, student.full_name, student.roll_number, student.branch, session.userId, session.fullName, rejectReason, nowIso]
      );

      await conn.commit();
    } catch (txErr) {
      await conn.rollback();
      throw txErr;
    } finally {
      conn.release();
    }

    return res.json({
      success: true,
      message: `Student registration marked as REJECTED in MySQL.`,
      studentId: student.id,
      rollNumber: student.roll_number,
    });
  } catch (err: any) {
    console.error('Reject student error:', err);
    return res.status(500).json({ error: err.message });
  }
});

// Approval Audit History from MySQL
apiRouter.get('/approvals/history', async (req: Request, res: Response) => {
  try {
    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.json({ history: [] });
    }

    const [rows]: any = await pool.query(
      `SELECT * FROM approval_records ORDER BY decided_at DESC LIMIT 100`
    );
    return res.json({ history: rows });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});

// Approve Faculty in MySQL (Admin Only)
apiRouter.post('/approvals/approve-faculty', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    const { facultyIdOrUsername } = req.body;
    if (!facultyIdOrUsername) {
      return res.status(400).json({ error: 'Faculty ID or username is required.' });
    }
    if (!session) {
      return res.status(401).json({ error: 'Administrator authorization required.' });
    }
    if (!['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(session.role)) {
      return res.status(403).json({ error: 'Only authorized College Administrators may approve faculty registrations.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.' });
    }

    const [userRows]: any = await pool.query(
      `SELECT * FROM users WHERE role = 'FACULTY' AND (id = ? OR LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)) LIMIT 1`,
      [facultyIdOrUsername, facultyIdOrUsername, facultyIdOrUsername]
    );
    if (userRows.length === 0) {
      return res.status(404).json({ error: 'Faculty account not found in MySQL.' });
    }

    const facUser = userRows[0];
    const nowIso = new Date();
    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();
      await conn.query(
        `UPDATE users SET status = 'ACTIVE', is_active = TRUE, approved_by = ?, approved_at = ? WHERE id = ?`,
        [session.fullName, nowIso, facUser.id]
      );
      await conn.query(
        `UPDATE faculty SET admin_approved = TRUE, status = 'ACTIVE' WHERE user_id = ? OR LOWER(email) = LOWER(?)`,
        [facUser.id, facUser.email]
      );
      await conn.query(
        `INSERT INTO approval_records (id, request_type, target_id, target_name, department, approver_id, approver_name, approver_role, decision, comments, decided_at)
         VALUES (?, 'FACULTY_REGISTRATION', ?, ?, ?, ?, ?, ?, 'APPROVED', 'Approved by College Administration', ?)`,
        [crypto.randomUUID(), facUser.id, facUser.full_name, facUser.department || 'CSE', session.userId, session.fullName, session.role, nowIso]
      );
      await conn.commit();
    } catch (txErr) {
      await conn.rollback();
      throw txErr;
    } finally {
      conn.release();
    }

    return res.json({ success: true, message: `Faculty member ${facUser.full_name} approved in MySQL.` });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});

// Reject Faculty in MySQL (Admin Only)
apiRouter.post('/approvals/reject-faculty', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    const { facultyIdOrUsername, reason } = req.body;
    if (!facultyIdOrUsername) {
      return res.status(400).json({ error: 'Faculty ID or username is required.' });
    }
    if (!session) {
      return res.status(401).json({ error: 'Administrator authorization required.' });
    }
    if (!['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(session.role)) {
      return res.status(403).json({ error: 'Only authorized College Administrators may reject faculty registrations.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.' });
    }

    const [userRows]: any = await pool.query(
      `SELECT * FROM users WHERE role = 'FACULTY' AND (id = ? OR LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)) LIMIT 1`,
      [facultyIdOrUsername, facultyIdOrUsername, facultyIdOrUsername]
    );
    if (userRows.length === 0) {
      return res.status(404).json({ error: 'Faculty account not found in MySQL.' });
    }

    const facUser = userRows[0];
    const rejectReason = reason || 'Application details could not be validated.';
    const nowIso = new Date();
    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();
      await conn.query(
        `UPDATE users SET status = 'REJECTED', is_active = FALSE, rejection_reason = ? WHERE id = ?`,
        [rejectReason, facUser.id]
      );
      await conn.query(
        `UPDATE faculty SET admin_approved = FALSE, status = 'REJECTED' WHERE user_id = ? OR LOWER(email) = LOWER(?)`,
        [facUser.id, facUser.email]
      );
      await conn.query(
        `INSERT INTO approval_records (id, request_type, target_id, target_name, department, approver_id, approver_name, approver_role, decision, comments, decided_at)
         VALUES (?, 'FACULTY_REGISTRATION', ?, ?, ?, ?, ?, ?, 'REJECTED', ?, ?)`,
        [crypto.randomUUID(), facUser.id, facUser.full_name, facUser.department || 'CSE', session.userId, session.fullName, session.role, rejectReason, nowIso]
      );
      await conn.commit();
    } catch (txErr) {
      await conn.rollback();
      throw txErr;
    } finally {
      conn.release();
    }

    return res.json({ success: true, message: `Faculty registration rejected in MySQL.` });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});

// Approve Company/Recruiter in MySQL (Admin Only)
apiRouter.post('/approvals/approve-company', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    const { companyIdOrName } = req.body;
    if (!companyIdOrName) {
      return res.status(400).json({ error: 'Company ID or name is required.' });
    }
    if (!session) {
      return res.status(401).json({ error: 'Administrator authorization required.' });
    }
    if (!['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(session.role)) {
      return res.status(403).json({ error: 'Only authorized College Administrators may approve corporate recruiters.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.' });
    }

    const [compRows]: any = await pool.query(
      `SELECT * FROM companies WHERE id = ? OR LOWER(name) = LOWER(?) LIMIT 1`,
      [companyIdOrName, companyIdOrName]
    );
    if (compRows.length === 0) {
      return res.status(404).json({ error: 'Company record not found in MySQL.' });
    }

    const company = compRows[0];
    const nowIso = new Date();
    let provisionedRecruiterUsername: string | null = null;
    let requiresPasswordChange = false;
    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();
      await conn.query(
        `UPDATE companies SET approval_status = 'APPROVED', approved_by_admin_id = ?, approved_by_name = ?, approved_at = ? WHERE id = ?`,
        [session.userId, session.fullName, nowIso, company.id]
      );

      const [existingUsers]: any = await conn.query(
        `SELECT id, username, password_hash FROM users WHERE company_id = ? OR LOWER(email) = LOWER(?) LIMIT 1`,
        [company.id, company.hr_email || '']
      );

      if (existingUsers.length > 0 && existingUsers[0].password_hash && String(existingUsers[0].password_hash).trim() !== '') {
        // Activate existing user without overwriting their stored password hash
        await conn.query(
          `UPDATE users SET status = 'APPROVED', is_active = TRUE, company_id = COALESCE(NULLIF(company_id, ''), ?), approved_by = ?, approved_at = ? WHERE id = ?`,
          [company.id, session.fullName, nowIso, existingUsers[0].id]
        );
      } else {
        const initialRecruiterPass = process.env.GCET_BOOTSTRAP_RECRUITER_PASS?.trim() || '';
        if (!initialRecruiterPass) {
          await conn.rollback();
          return res.status(500).json({
            error: 'GCET_BOOTSTRAP_RECRUITER_PASS is not configured on the server. Set it in .env before provisioning new recruiter credentials.',
          });
        }

        const salt = generateSalt();
        const hash = hashPassword(initialRecruiterPass, salt);
        requiresPasswordChange = true;

        if (existingUsers.length > 0) {
          provisionedRecruiterUsername = existingUsers[0].username;
          await conn.query(
            `UPDATE users
             SET password_hash = ?, salt = ?, must_change_password = TRUE, status = 'APPROVED', is_active = TRUE,
                 company_id = COALESCE(NULLIF(company_id, ''), ?), approved_by = ?, approved_at = ?
             WHERE id = ?`,
            [hash, salt, company.id, session.fullName, nowIso, existingUsers[0].id]
          );
        } else {
          // Ensure unique recruiter username if prefix already exists
          const baseSlug = company.name.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 15) || 'partner';
          let recruiterUsername = `recruiter_${baseSlug}`;
          const [unameDup]: any = await conn.query(
            `SELECT id FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1`,
            [recruiterUsername]
          );
          if (unameDup.length > 0) {
            const suffix = String(company.id).replace(/[^a-zA-Z0-9]/g, '').slice(-4) || crypto.randomUUID().slice(0, 4);
            recruiterUsername = `${recruiterUsername}_${suffix}`;
          }
          provisionedRecruiterUsername = recruiterUsername;
          const recruiterUserId = `usr-comp-${crypto.randomUUID()}`;
          await conn.query(
            `INSERT INTO users (id, username, email, full_name, role, department, company_id, password_hash, salt, must_change_password, status, is_active, approved_by, approved_at, created_at)
             VALUES (?, ?, ?, ?, 'COMPANY', ?, ?, ?, ?, TRUE, 'APPROVED', TRUE, ?, ?, ?)`,
            [
              recruiterUserId,
              recruiterUsername,
              company.hr_email || `${recruiterUsername}@company.com`,
              `${company.hr_name || 'HR Lead'} (${company.name})`,
              company.name,
              company.id,
              hash,
              salt,
              session.fullName,
              nowIso,
              nowIso,
            ]
          );
          await conn.query(
            `UPDATE companies SET user_id = COALESCE(user_id, ?) WHERE id = ?`,
            [recruiterUserId, company.id]
          );
        }
      }

      await conn.query(
        `INSERT INTO approval_records (id, request_type, target_id, target_name, department, approver_id, approver_name, approver_role, decision, comments, decided_at)
         VALUES (?, 'COMPANY_REGISTRATION', ?, ?, ?, ?, ?, ?, 'APPROVED', 'Corporate recruiting partner approved by College Administration', ?)`,
        [crypto.randomUUID(), company.id, company.name, company.industry || 'Corporate', session.userId, session.fullName, session.role, nowIso]
      );
      await conn.commit();
    } catch (txErr) {
      await conn.rollback();
      throw txErr;
    } finally {
      conn.release();
    }

    return res.json({
      success: true,
      message: `Corporate partner ${company.name} approved in MySQL.`,
      username: provisionedRecruiterUsername,
      mustChangePassword: requiresPasswordChange,
    });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});

// Reject Company/Recruiter in MySQL (Admin Only)
apiRouter.post('/approvals/reject-company', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    const { companyIdOrName, reason } = req.body;
    if (!companyIdOrName) {
      return res.status(400).json({ error: 'Company ID or name is required.' });
    }
    if (!session) {
      return res.status(401).json({ error: 'Administrator authorization required.' });
    }
    if (!['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(session.role)) {
      return res.status(403).json({ error: 'Only authorized College Administrators may reject corporate recruiters.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.' });
    }

    const [compRows]: any = await pool.query(
      `SELECT * FROM companies WHERE id = ? OR LOWER(name) = LOWER(?) LIMIT 1`,
      [companyIdOrName, companyIdOrName]
    );
    if (compRows.length === 0) {
      return res.status(404).json({ error: 'Company record not found in MySQL.' });
    }

    const company = compRows[0];
    const rejectReason = reason || 'Corporate credentials could not be verified.';
    const nowIso = new Date();
    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();
      await conn.query(
        `UPDATE companies SET approval_status = 'REJECTED', rejection_reason = ? WHERE id = ?`,
        [rejectReason, company.id]
      );
      await conn.query(
        `UPDATE users SET status = 'REJECTED', is_active = FALSE, rejection_reason = ? WHERE company_id = ? OR LOWER(email) = LOWER(?)`,
        [rejectReason, company.id, company.hr_email || '']
      );
      await conn.query(
        `INSERT INTO approval_records (id, request_type, target_id, target_name, department, approver_id, approver_name, approver_role, decision, comments, decided_at)
         VALUES (?, 'COMPANY_REGISTRATION', ?, ?, ?, ?, ?, ?, 'REJECTED', ?, ?)`,
        [crypto.randomUUID(), company.id, company.name, company.industry || 'Corporate', session.userId, session.fullName, session.role, rejectReason, nowIso]
      );
      await conn.commit();
    } catch (txErr) {
      await conn.rollback();
      throw txErr;
    } finally {
      conn.release();
    }

    return res.json({ success: true, message: `Corporate partner registration rejected in MySQL.` });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});

// --------------------------------------------------------------------------
// 6. STUDENT DASHBOARD & PERSONAL DATA
// --------------------------------------------------------------------------
apiRouter.get('/students/:id/profile', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    if (!session) {
      return res.status(401).json({ error: 'Authentication required.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.' });
    }

    const studentIdOrRoll = req.params.id;
    const [stRows]: any = await pool.query(
      'SELECT * FROM students WHERE id = ? OR LOWER(roll_number) = LOWER(?)',
      [studentIdOrRoll, studentIdOrRoll]
    );

    if (stRows.length === 0) {
      return res.status(404).json({ error: 'Student not found in MySQL.' });
    }

    const student = stRows[0];

    // Students may only view their own full profile
    if (session.role === 'STUDENT' && student.user_id !== session.userId && (!session.studentRollNumber || student.roll_number.toLowerCase() !== session.studentRollNumber.toLowerCase())) {
      return res.status(403).json({ error: 'You are only authorized to access your own student profile.' });
    }

    if (student.user_id) {
      const fullProfile = await loadFullProfileByUserId(student.user_id);
      if (fullProfile) {
        const [appRows]: any = await pool.query(
          `SELECT * FROM applications WHERE student_id = ? ORDER BY applied_at DESC`,
          [student.id]
        );
        return res.json({
          ...fullProfile,
          applications: appRows,
        });
      }
    }

    // Fetch Student Skills fallback
    const [skillsRows]: any = await pool.query(
      `SELECT sk.id, sk.name, sk.category, ss.proficiency, ss.verified_by_faculty
       FROM student_skills ss
       JOIN skills sk ON sk.id = ss.skill_id
       WHERE ss.student_id = ?`,
      [student.id]
    );

    // Fetch Placement Applications
    const [appRows]: any = await pool.query(
      `SELECT * FROM applications WHERE student_id = ? ORDER BY applied_at DESC`,
      [student.id]
    );

    return res.json({
      student,
      skills: skillsRows,
      applications: appRows,
    });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});

// Update student permitted profile fields back to MySQL
apiRouter.put('/students/:id/profile', async (req: Request, res: Response) => {
  try {
    const session = getSessionFromRequest(req);
    if (!session) {
      return res.status(401).json({ error: 'Authentication required.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database not connected.' });
    }

    const { phone, resumeUrl } = req.body;
    const studentId = req.params.id;

    // Security: Only the student owner or authorized Admin / Placement Officer may update student profile fields here
    const isAdminOrOfficer = ['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN', 'PLACEMENT_OFFICER'].includes(session.role);
    if (session.role === 'STUDENT') {
      const [ownerRows]: any = await pool.query(
        'SELECT id FROM students WHERE (id = ? OR LOWER(roll_number) = LOWER(?)) AND (user_id = ? OR LOWER(roll_number) = LOWER(?))',
        [studentId, studentId, session.userId, session.studentRollNumber || session.username]
      );
      if (ownerRows.length === 0) {
        return res.status(403).json({ error: 'You are only authorized to edit your own student profile.' });
      }
    } else if (!isAdminOrOfficer) {
      return res.status(403).json({ error: 'Users may only modify their own profiles.' });
    }

    await pool.query(
      `UPDATE students SET phone = COALESCE(?, phone), resume_url = COALESCE(?, resume_url) WHERE id = ? OR LOWER(roll_number) = LOWER(?)`,
      [phone || null, resumeUrl || null, studentId, studentId]
    );

    return res.json({ success: true, message: 'Profile updated in MySQL successfully.' });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});

// Mount Unified Multi-Role Profile Management Endpoints
registerProfileRoutes(apiRouter, getSessionFromRequest);

// Mount MySQL-Backed Workspace CRUD & Snapshot Endpoints
registerWorkspaceRoutes(apiRouter, getSessionFromRequest);

// --------------------------------------------------------------------------
// 7. REAL DATABASE STATISTICS FOR DASHBOARD
// --------------------------------------------------------------------------
apiRouter.get('/dashboard/stats', async (req: Request, res: Response) => {
  try {
    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.json({
        totalStudents: 0,
        placedCount: 0,
        placementPercentage: '0.0',
        avgPackage: '0.00',
        highestPackage: '0.0',
        activeDrives: 0,
        approvedCompanies: 0,
        mysqlConnected: false,
      });
    }

    const [stStats]: any = await pool.query(`
      SELECT 
        COUNT(*) as totalStudents,
        SUM(CASE WHEN placement_status = 'PLACED' THEN 1 ELSE 0 END) as placedCount,
        AVG(CASE WHEN placement_status = 'PLACED' AND placed_package_lpa > 0 THEN placed_package_lpa ELSE NULL END) as avgPackage,
        MAX(placed_package_lpa) as highestPackage
      FROM students
    `);

    const [compStats]: any = await pool.query(
      `SELECT COUNT(*) as approvedCompanies FROM companies WHERE approval_status IN ('ACTIVE', 'APPROVED')`
    );

    const [driveStats]: any = await pool.query(
      `SELECT COUNT(*) as activeDrives, MAX(salary_package_lpa) as maxDrivePackage FROM placement_drives WHERE status = 'OPEN'`
    );

    const statRow = stStats[0] || {};
    const total = statRow.totalStudents || 0;
    const placed = statRow.placedCount || 0;
    const pct = total > 0 ? ((placed / total) * 100).toFixed(1) : '0.0';
    const avg = statRow.avgPackage ? parseFloat(statRow.avgPackage).toFixed(2) : '0.00';
    let high = statRow.highestPackage ? parseFloat(statRow.highestPackage) : 0;
    if (driveStats[0]?.maxDrivePackage && driveStats[0].maxDrivePackage > high) {
      high = parseFloat(driveStats[0].maxDrivePackage);
    }

    return res.json({
      totalStudents: total,
      placedCount: placed,
      placementPercentage: pct,
      avgPackage: avg,
      highestPackage: high > 0 ? high.toFixed(1) : '0.0',
      activeDrives: driveStats[0]?.activeDrives || 0,
      approvedCompanies: compStats[0]?.approvedCompanies || 0,
      mysqlConnected: true,
    });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});
