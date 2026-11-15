import { Router, Request, Response } from 'express';
import crypto from 'crypto';
import {
  getPool,
  isDbConnected,
  getDbStatus,
  generateSalt,
  hashPassword,
  getFacultyAuthorizedDepts,
} from './db';
import type { SessionInfo } from './profileRoutes';

type SessionResolver = (req: Request) => SessionInfo | null;

function parseJsonArray<T = any>(raw: unknown, fallback: T[] = []): T[] {
  if (!raw) return fallback;
  if (Array.isArray(raw)) return raw as T[];
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : fallback;
    } catch {
      return fallback;
    }
  }
  return fallback;
}

function toIsoDateOnly(val: unknown): string {
  if (!val) return '';
  if (val instanceof Date) return val.toISOString().split('T')[0];
  const str = String(val);
  return str.includes('T') ? str.split('T')[0] : str;
}

function normalizeCompanyStatus(status: unknown): 'PENDING' | 'ACTIVE' | 'REJECTED' {
  const s = String(status || '').toUpperCase();
  if (s === 'PENDING' || s === 'PENDING_APPROVAL') return 'PENDING';
  if (s === 'REJECTED') return 'REJECTED';
  return 'ACTIVE';
}

function normalizeStudentApproval(status: unknown): 'PENDING' | 'APPROVED' | 'REJECTED' {
  const s = String(status || '').toUpperCase();
  if (s === 'APPROVED' || s === 'ACTIVE') return 'APPROVED';
  if (s === 'REJECTED') return 'REJECTED';
  return 'PENDING';
}

const ADMIN_OR_OFFICER_ROLES = new Set([
  'PRINCIPAL',
  'CHAIRMAN',
  'VICE_CHAIRMAN',
  'ADMIN',
  'PLACEMENT_OFFICER',
]);

const STAFF_ROLES = new Set([
  'PRINCIPAL',
  'CHAIRMAN',
  'VICE_CHAIRMAN',
  'ADMIN',
  'PLACEMENT_OFFICER',
  'FACULTY',
]);

export function registerWorkspaceRoutes(apiRouter: Router, getSession: SessionResolver) {
  function requireDbSession(req: Request, res: Response) {
    const session = getSession(req);
    if (!session) {
      res.status(401).json({ error: 'Authentication required. Please log in.' });
      return null;
    }
    const pool = getPool();
    if (!pool || !isDbConnected()) {
      res.status(503).json({
        error: 'MySQL database is not connected.',
        mysqlStatus: getDbStatus(),
      });
      return null;
    }
    return { session, pool };
  }

  async function loadWorkspacePayload(pool: any) {
    const [
      [studentRows],
      [stuSkillRows],
      [projRows],
      [certRows],
      [internRows],
      [achRows],
      [companyRows],
      [driveRows],
      [skillRows],
      [appRows],
      [userRows],
      [facultyRows],
    ]: any = await Promise.all([
      pool.query(`SELECT * FROM students ORDER BY created_at DESC`),
      pool.query(`SELECT * FROM student_skills`),
      pool.query(`SELECT * FROM student_projects ORDER BY created_at DESC`),
      pool.query(`SELECT * FROM user_certifications ORDER BY created_at DESC`),
      pool.query(`SELECT * FROM student_internships ORDER BY created_at DESC`),
      pool.query(`SELECT * FROM student_achievements ORDER BY created_at DESC`),
      pool.query(`SELECT * FROM companies ORDER BY created_at DESC`),
      pool.query(`SELECT * FROM placement_drives ORDER BY drive_date DESC, created_at DESC`),
      pool.query(`SELECT * FROM skills ORDER BY name ASC`),
      pool.query(`SELECT * FROM applications ORDER BY applied_at DESC`),
      pool.query(
        `SELECT u.id, u.username, u.email, u.full_name, u.role, u.department,
                u.student_roll_number, u.company_id, u.status, u.is_active,
                u.must_change_password, u.authorized_departments, u.approved_by,
                u.approved_at, u.rejection_reason, u.last_login, u.created_at,
                p.designation, p.phone, p.location, p.about_me, p.profile_photo
         FROM users u
         LEFT JOIN user_profiles p ON p.user_id = u.id
         ORDER BY u.created_at DESC`
      ),
      pool.query(`SELECT * FROM faculty`),
    ]);

    const skillsByStudent = new Map<string, any[]>();
    for (const row of stuSkillRows || []) {
      const list = skillsByStudent.get(row.student_id) || [];
      list.push({
        skillId: row.skill_id,
        proficiency: Number(row.proficiency || 3),
        verifiedByFaculty: Boolean(row.verified_by_faculty),
        verifiedByFacultyId: row.verified_by_faculty_id || undefined,
        verifiedAt: row.verified_at ? new Date(row.verified_at).toISOString() : undefined,
      });
      skillsByStudent.set(row.student_id, list);
    }

    const projectsByStudent = new Map<string, any[]>();
    for (const row of projRows || []) {
      const list = projectsByStudent.get(row.student_id) || [];
      list.push({
        id: row.id,
        title: row.title,
        description: row.description || '',
        technologies: parseJsonArray<string>(row.technologies),
        link: row.project_url || undefined,
        duration: row.duration || undefined,
      });
      projectsByStudent.set(row.student_id, list);
    }

    const certsByStudent = new Map<string, any[]>();
    for (const row of certRows || []) {
      if (!row.student_id) continue;
      const list = certsByStudent.get(row.student_id) || [];
      list.push({
        id: row.id,
        name: row.name,
        issuingOrg: row.issuing_org,
        issueDate: row.issue_date || '',
        credentialId: row.credential_id || undefined,
        credentialUrl: row.credential_url || undefined,
      });
      certsByStudent.set(row.student_id, list);
    }

    const internsByStudent = new Map<string, any[]>();
    for (const row of internRows || []) {
      const list = internsByStudent.get(row.student_id) || [];
      list.push({
        id: row.id,
        companyName: row.company_name,
        role: row.role,
        duration: row.duration || '',
        location: row.location || undefined,
        description: row.description || '',
      });
      internsByStudent.set(row.student_id, list);
    }

    const achByStudent = new Map<string, any[]>();
    for (const row of achRows || []) {
      const list = achByStudent.get(row.student_id) || [];
      list.push({
        id: row.id,
        title: row.title,
        category: row.category || 'AWARD',
        organizer: row.organizer || undefined,
        eventDate: row.event_date || undefined,
        description: row.description || undefined,
        credentialUrl: row.credential_url || undefined,
      });
      achByStudent.set(row.student_id, list);
    }

    const driveCountByCompany = new Map<string, number>();
    for (const d of driveRows || []) {
      driveCountByCompany.set(d.company_id, (driveCountByCompany.get(d.company_id) || 0) + 1);
    }

    const facultyByUserId = new Map<string, any>();
    for (const f of facultyRows || []) {
      if (f.user_id) facultyByUserId.set(f.user_id, f);
    }

    const students = (studentRows || []).map((s: any) => ({
      id: s.id,
      rollNumber: s.roll_number,
      fullName: s.full_name,
      email: s.email,
      phone: s.phone || '',
      branch: s.branch,
      section: s.section || 'A',
      currentSemester: s.current_semester ? Number(s.current_semester) : 7,
      cgpa: Number(s.cgpa ?? 0),
      tenthPercentage: Number(s.tenth_percentage ?? 0),
      interOrDiplomaPercentage: Number(s.inter_percentage ?? 0),
      graduationYear: Number(s.graduation_year ?? 2026),
      academicYear: Number(s.academic_year ?? s.year_of_study ?? 4),
      backlogs: Number(s.backlogs ?? 0),
      skills: skillsByStudent.get(s.id) || [],
      projects: projectsByStudent.get(s.id) || [],
      certifications: certsByStudent.get(s.id) || [],
      internships: internsByStudent.get(s.id) || [],
      achievements: achByStudent.get(s.id) || [],
      dob: s.dob || undefined,
      gender: s.gender || undefined,
      address: s.address || undefined,
      location: s.location || undefined,
      aboutMe: s.about_me || undefined,
      resumeUrl: s.resume_url || undefined,
      placementStatus: s.placement_status || 'UNPLACED',
      placedCompanyId: s.placed_company_id || undefined,
      placedPackageLpa: s.placed_package_lpa !== null && s.placed_package_lpa !== undefined ? Number(s.placed_package_lpa) : undefined,
      approvalStatus: normalizeStudentApproval(s.approval_status),
      approvedByFacultyId: s.approved_by_faculty_id || undefined,
      approvedByName: s.approved_by_faculty_name || undefined,
      approvedAt: s.approved_at ? new Date(s.approved_at).toISOString() : undefined,
      rejectionReason: s.rejection_reason || undefined,
      registeredAt: s.created_at ? new Date(s.created_at).toISOString() : undefined,
    }));

    const studentMap = new Map<string, any>(students.map((st: any) => [st.id, st]));

    const companies = (companyRows || []).map((c: any) => ({
      id: c.id,
      name: c.name,
      industry: c.industry,
      website: c.website || '',
      location: c.location || '',
      description: c.description || '',
      hrName: c.hr_name || '',
      hrEmail: c.hr_email || '',
      hrPhone: c.hr_phone || '',
      recruiterName: c.recruiter_name || c.hr_name || '',
      recruiterDesignation: c.recruiter_designation || '',
      companyLogo: c.company_logo || undefined,
      linkedinUrl: c.linkedin_url || undefined,
      tier: c.tier || 'Standard IT',
      totalDrivesConducted: driveCountByCompany.get(c.id) || 0,
      createdAt: toIsoDateOnly(c.created_at),
      status: normalizeCompanyStatus(c.approval_status),
      approvalStatus: c.approval_status || 'ACTIVE',
      approvedByAdminId: c.approved_by_admin_id || undefined,
      approvedByName: c.approved_by_name || undefined,
      approvedAt: c.approved_at ? new Date(c.approved_at).toISOString() : undefined,
      rejectionReason: c.rejection_reason || undefined,
    }));

    const drives = (driveRows || []).map((d: any) => ({
      id: d.id,
      companyId: d.company_id,
      companyName: d.company_name,
      jobRole: d.job_role,
      jobDescription: d.job_description || '',
      salaryPackageLpa: Number(d.salary_package_lpa ?? 0),
      eligibleBranches: parseJsonArray(d.eligible_branches, ['CSE', 'IT', 'ECE']),
      minCgpa: Number(d.min_cgpa ?? 6.0),
      maxBacklogsAllowed: Number(d.max_backlogs_allowed ?? 0),
      targetGraduationYear: Number(d.target_graduation_year ?? 2026),
      requiredSkills: parseJsonArray(d.required_skills, []),
      driveDate: toIsoDateOnly(d.drive_date),
      applicationDeadline: toIsoDateOnly(d.application_deadline),
      availablePositions: Number(d.available_positions ?? 1),
      status: d.status || 'OPEN',
      selectionProcess: d.selection_process || 'Online Assessment -> Technical Interview -> HR',
      createdAt: toIsoDateOnly(d.created_at),
    }));

    const driveMap = new Map<string, any>(drives.map((dr: any) => [dr.id, dr]));

    const skills = (skillRows || []).map((sk: any) => ({
      id: sk.id,
      name: sk.name,
      category: sk.category,
      description: sk.description || '',
    }));

    const applications = (appRows || []).map((a: any) => {
      const stu = studentMap.get(a.student_id);
      const drv = driveMap.get(a.drive_id);
      return {
        id: a.id,
        driveId: a.drive_id,
        studentId: a.student_id,
        studentRollNumber: a.student_roll_number || stu?.rollNumber || '',
        studentName: a.student_name || stu?.fullName || '',
        studentBranch: a.student_branch || stu?.branch || 'CSE',
        studentCgpa: Number(a.student_cgpa ?? stu?.cgpa ?? 0),
        companyId: a.company_id || drv?.companyId || '',
        companyName: a.company_name || drv?.companyName || '',
        jobRole: a.job_role || drv?.jobRole || '',
        currentStage: a.current_stage || 'APPLICATION_SUBMITTED',
        stageHistory: parseJsonArray(a.stage_history, [
          {
            stage: a.current_stage || 'APPLICATION_SUBMITTED',
            timestamp: a.applied_at ? new Date(a.applied_at).toISOString() : new Date().toISOString(),
            updatedBy: 'System',
          },
        ]),
        appliedAt: a.applied_at ? new Date(a.applied_at).toISOString() : new Date().toISOString(),
        finalOfferLpa: a.final_offer_lpa !== null && a.final_offer_lpa !== undefined ? Number(a.final_offer_lpa) : undefined,
        rejectionReason: a.rejection_reason || undefined,
      };
    });

    const users = (userRows || []).map((u: any) => {
      const fac = facultyByUserId.get(u.id);
      return {
        id: u.id,
        username: u.username,
        email: u.email,
        fullName: u.full_name,
        role: u.role,
        department: u.department || fac?.department || '',
        designation: u.designation || fac?.designation || undefined,
        employeeId: fac?.employee_id || fac?.faculty_id || undefined,
        phone: u.phone || fac?.phone || undefined,
        location: u.location || undefined,
        aboutMe: u.about_me || undefined,
        profilePhoto: u.profile_photo || undefined,
        studentRollNumber: u.student_roll_number || undefined,
        companyId: u.company_id || undefined,
        status: u.status,
        isActive: Boolean(u.is_active),
        mustChangePassword: Boolean(u.must_change_password),
        approvedByName: u.approved_by || undefined,
        approvedAt: u.approved_at ? new Date(u.approved_at).toISOString() : undefined,
        rejectionReason: u.rejection_reason || undefined,
        lastLogin: u.last_login ? new Date(u.last_login).toISOString() : undefined,
        createdAt: u.created_at ? new Date(u.created_at).toISOString() : undefined,
        authorizedDepartments: u.authorized_departments
          ? parseJsonArray<string>(u.authorized_departments)
          : getFacultyAuthorizedDepts(u.username),
      };
    });

    return { students, companies, drives, skills, applications, users };
  }

  // 1. Unified Live MySQL Workspace Snapshot
  apiRouter.get('/workspace/data', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    try {
      const payload = await loadWorkspacePayload(ctx.pool);
      return res.json(payload);
    } catch (err: any) {
      console.error('Load workspace data error:', err);
      return res.status(500).json({ error: err.message || 'Unable to load workspace data from MySQL.' });
    }
  });

  // 2. Faculty list from MySQL
  apiRouter.get('/faculty', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    try {
      const [rows]: any = await ctx.pool.query(
        `SELECT u.id, u.username, u.email, u.full_name, u.department, u.status, u.is_active,
                u.approved_by, u.created_at, f.employee_id, f.faculty_id, f.designation, f.phone
         FROM users u
         LEFT JOIN faculty f ON f.user_id = u.id
         WHERE u.role = 'FACULTY'
         ORDER BY u.created_at DESC`
      );
      const faculty = (rows || []).map((r: any) => ({
        id: r.id,
        username: r.username,
        employeeId: r.employee_id || r.faculty_id || r.username,
        email: r.email,
        fullName: r.full_name,
        department: r.department || 'CSE',
        designation: r.designation || 'Faculty Coordinator',
        phone: r.phone || '',
        status: r.status,
        isActive: Boolean(r.is_active),
        approvedByName: r.approved_by || undefined,
        createdAt: r.created_at ? new Date(r.created_at).toISOString() : undefined,
      }));
      return res.json({ faculty });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  // 3. Export Sanitized MySQL Snapshot
  apiRouter.get('/export', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Staff authorization required to export institutional data.' });
    }
    try {
      const payload = await loadWorkspacePayload(ctx.pool);
      return res.json({
        exportedAt: new Date().toISOString(),
        database: 'gcet_placement_hub',
        ...payload,
      });
    } catch (err: any) {
      return res.status(500).json({ error: err.message || 'Failed to export MySQL snapshot.' });
    }
  });

  // 4. Students CRUD in MySQL
  apiRouter.post('/students', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Only authorized staff can create student records.' });
    }
    const {
      rollNumber, fullName, email, phone, branch, section, currentSemester,
      cgpa, tenthPercentage, interOrDiplomaPercentage, graduationYear,
      academicYear, backlogs, resumeUrl, placementStatus, skills: stuSkills,
    } = req.body;

    const cleanRoll = String(rollNumber || '').trim().toUpperCase();
    const cleanEmail = String(email || '').trim().toLowerCase();
    if (!cleanRoll || !fullName?.trim() || !cleanEmail || !branch?.trim()) {
      return res.status(400).json({ error: 'Roll number, full name, email, and branch are required.' });
    }

    const id = `stu-${crypto.randomUUID()}`;
    const conn = await ctx.pool.getConnection();
    try {
      await conn.beginTransaction();
      await conn.query(
        `INSERT INTO students (
          id, roll_number, student_id_card, full_name, email, phone, branch,
          section, current_semester, year_of_study, academic_year, cgpa,
          tenth_percentage, inter_percentage, graduation_year, backlogs,
          placement_status, approval_status, approved_by_faculty_id, approved_by_faculty_name,
          approved_at, resume_url
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?, NOW(), ?)`,
        [
          id,
          cleanRoll,
          `GCET-${graduationYear || 2026}-${cleanRoll.slice(-4)}`,
          String(fullName).trim(),
          cleanEmail,
          phone ? String(phone).trim() : '',
          String(branch).trim(),
          section ? String(section).trim() : 'A',
          Number(currentSemester || 7),
          Number(academicYear || 4),
          Number(academicYear || 4),
          Number(cgpa ?? 0),
          Number(tenthPercentage ?? 0),
          Number(interOrDiplomaPercentage ?? 0),
          Number(graduationYear || 2026),
          Number(backlogs ?? 0),
          placementStatus || 'UNPLACED',
          ctx.session.userId,
          ctx.session.fullName,
          resumeUrl || null,
        ]
      );

      if (Array.isArray(stuSkills)) {
        for (const sk of stuSkills) {
          if (!sk?.skillId) continue;
          await conn.query(
            `INSERT INTO student_skills (student_id, skill_id, proficiency, verified_by_faculty)
             VALUES (?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE proficiency = VALUES(proficiency)`,
            [id, sk.skillId, Number(sk.proficiency || 3), Boolean(sk.verifiedByFaculty)]
          );
        }
      }
      await conn.commit();
      return res.status(201).json({ success: true, id });
    } catch (err: any) {
      await conn.rollback();
      if (err?.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({ error: 'A student with this roll number or email already exists in MySQL.' });
      }
      return res.status(500).json({ error: err.message || 'Unable to create student in MySQL.' });
    } finally {
      conn.release();
    }
  });

  apiRouter.put('/students/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    const studentId = req.params.id;
    const body = req.body || {};

    const [rows]: any = await ctx.pool.query(
      `SELECT id, roll_number, user_id FROM students WHERE id = ? OR LOWER(roll_number) = LOWER(?) LIMIT 1`,
      [studentId, studentId]
    );
    if (!rows || rows.length === 0) {
      return res.status(404).json({ error: 'Student not found in MySQL.' });
    }
    const existing = rows[0];
    const isOwner =
      ctx.session.role === 'STUDENT' &&
      (existing.user_id === ctx.session.userId ||
        (ctx.session.studentRollNumber && existing.roll_number.toLowerCase() === ctx.session.studentRollNumber.toLowerCase()));

    if (!STAFF_ROLES.has(ctx.session.role) && !isOwner) {
      return res.status(403).json({ error: 'Not authorized to modify this student record.' });
    }

    const conn = await ctx.pool.getConnection();
    try {
      await conn.beginTransaction();

      if (STAFF_ROLES.has(ctx.session.role)) {
        await conn.query(
          `UPDATE students SET
            full_name = COALESCE(?, full_name),
            email = COALESCE(?, email),
            phone = COALESCE(?, phone),
            branch = COALESCE(?, branch),
            section = COALESCE(?, section),
            current_semester = COALESCE(?, current_semester),
            cgpa = COALESCE(?, cgpa),
            tenth_percentage = COALESCE(?, tenth_percentage),
            inter_percentage = COALESCE(?, inter_percentage),
            graduation_year = COALESCE(?, graduation_year),
            academic_year = COALESCE(?, academic_year),
            backlogs = COALESCE(?, backlogs),
            placement_status = COALESCE(?, placement_status),
            resume_url = COALESCE(?, resume_url)
           WHERE id = ?`,
          [
            body.fullName?.trim() || null,
            body.email?.trim()?.toLowerCase() || null,
            body.phone ?? null,
            body.branch || null,
            body.section || null,
            body.currentSemester ?? null,
            body.cgpa ?? null,
            body.tenthPercentage ?? null,
            body.interOrDiplomaPercentage ?? null,
            body.graduationYear ?? null,
            body.academicYear ?? null,
            body.backlogs ?? null,
            body.placementStatus || null,
            body.resumeUrl ?? null,
            existing.id,
          ]
        );
      }

      if (Array.isArray(body.skills)) {
        for (const sk of body.skills) {
          if (!sk?.skillId) continue;
          await conn.query(
            `INSERT INTO student_skills (student_id, skill_id, proficiency, verified_by_faculty, verified_by_faculty_id, verified_at)
             VALUES (?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE
               proficiency = VALUES(proficiency),
               verified_by_faculty = VALUES(verified_by_faculty),
               verified_by_faculty_id = VALUES(verified_by_faculty_id),
               verified_at = VALUES(verified_at)`,
            [
              existing.id,
              sk.skillId,
              Number(sk.proficiency || 3),
              Boolean(sk.verifiedByFaculty),
              sk.verifiedByFacultyId || null,
              sk.verifiedAt ? new Date(sk.verifiedAt) : null,
            ]
          );
        }
      }

      if (Array.isArray(body.projects)) {
        for (const p of body.projects) {
          if (!p?.id || !p?.title) continue;
          await conn.query(
            `INSERT INTO student_projects (id, student_id, user_id, title, description, technologies, project_url, duration)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE title = VALUES(title), description = VALUES(description), technologies = VALUES(technologies), project_url = VALUES(project_url), duration = VALUES(duration)`,
            [
              p.id,
              existing.id,
              existing.user_id || null,
              p.title,
              p.description || '',
              JSON.stringify(p.technologies || []),
              p.link || null,
              p.duration || null,
            ]
          );
        }
      }

      if (Array.isArray(body.certifications) && existing.user_id) {
        for (const c of body.certifications) {
          if (!c?.id || !c?.name) continue;
          await conn.query(
            `INSERT INTO user_certifications (id, user_id, student_id, name, issuing_org, issue_date, credential_id, credential_url)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE name = VALUES(name), issuing_org = VALUES(issuing_org), issue_date = VALUES(issue_date), credential_id = VALUES(credential_id)`,
            [
              c.id,
              existing.user_id,
              existing.id,
              c.name,
              c.issuingOrg || '',
              c.issueDate || '',
              c.credentialId || null,
              c.credentialUrl || null,
            ]
          );
        }
      }

      await conn.commit();
      return res.json({ success: true, id: existing.id });
    } catch (err: any) {
      await conn.rollback();
      return res.status(500).json({ error: err.message || 'Unable to update student in MySQL.' });
    } finally {
      conn.release();
    }
  });

  apiRouter.delete('/students/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!ADMIN_OR_OFFICER_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Administrator authorization required to delete student records.' });
    }
    try {
      await ctx.pool.query(`DELETE FROM student_skills WHERE student_id = ?`, [req.params.id]);
      await ctx.pool.query(`DELETE FROM applications WHERE student_id = ?`, [req.params.id]);
      await ctx.pool.query(`DELETE FROM students WHERE id = ?`, [req.params.id]);
      return res.json({ success: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.post('/students/:id/skills/verify', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Faculty or Admin authorization required to verify student skills.' });
    }
    const { skillId } = req.body || {};
    if (!skillId) return res.status(400).json({ error: 'skillId is required.' });
    try {
      await ctx.pool.query(
        `UPDATE student_skills SET verified_by_faculty = TRUE, verified_by_faculty_id = ?, verified_at = NOW()
         WHERE student_id = ? AND skill_id = ?`,
        [ctx.session.userId, req.params.id, skillId]
      );
      return res.json({ success: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  // 5. Companies CRUD in MySQL
  apiRouter.post('/companies', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Authorized staff privileges required.' });
    }
    const { name, industry, website, location, description, hrName, hrEmail, hrPhone, tier } = req.body || {};
    if (!name?.trim() || !industry?.trim()) {
      return res.status(400).json({ error: 'Company name and industry are required.' });
    }
    const id = `comp-${crypto.randomUUID()}`;
    try {
      await ctx.pool.query(
        `INSERT INTO companies (id, name, industry, website, location, description, hr_name, hr_email, hr_phone, tier, approval_status)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')`,
        [id, name.trim(), industry.trim(), website || '', location || '', description || '', hrName || '', hrEmail || '', hrPhone || '', tier || 'Standard IT']
      );
      return res.status(201).json({ success: true, id });
    } catch (err: any) {
      if (err?.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({ error: 'A company with that name already exists in MySQL.' });
      }
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.put('/companies/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role) && ctx.session.role !== 'COMPANY') {
      return res.status(403).json({ error: 'Not authorized to update company records.' });
    }
    const { name, industry, website, location, description, hrName, hrEmail, hrPhone, tier } = req.body || {};
    try {
      await ctx.pool.query(
        `UPDATE companies SET
          name = COALESCE(?, name),
          industry = COALESCE(?, industry),
          website = COALESCE(?, website),
          location = COALESCE(?, location),
          description = COALESCE(?, description),
          hr_name = COALESCE(?, hr_name),
          hr_email = COALESCE(?, hr_email),
          hr_phone = COALESCE(?, hr_phone),
          tier = COALESCE(?, tier)
         WHERE id = ?`,
        [name || null, industry || null, website ?? null, location ?? null, description ?? null, hrName ?? null, hrEmail ?? null, hrPhone ?? null, tier || null, req.params.id]
      );
      return res.json({ success: true, id: req.params.id });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.delete('/companies/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!ADMIN_OR_OFFICER_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Administrator authorization required to delete companies.' });
    }
    try {
      await ctx.pool.query(`DELETE FROM companies WHERE id = ?`, [req.params.id]);
      return res.json({ success: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  // 6. Placement Drives CRUD in MySQL
  apiRouter.post('/drives', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role) && ctx.session.role !== 'COMPANY') {
      return res.status(403).json({ error: 'Not authorized to create placement drives.' });
    }
    const d = req.body || {};
    if (!d.companyId || !d.jobRole?.trim()) {
      return res.status(400).json({ error: 'Company ID and job role are required.' });
    }
    let companyName = d.companyName || '';
    if (!companyName) {
      const [compRows]: any = await ctx.pool.query(`SELECT name FROM companies WHERE id = ? LIMIT 1`, [d.companyId]);
      companyName = compRows?.[0]?.name || 'Partner Organization';
    }
    const id = `drv-${crypto.randomUUID()}`;
    try {
      await ctx.pool.query(
        `INSERT INTO placement_drives (
          id, company_id, company_name, job_role, job_description, salary_package_lpa,
          min_cgpa, max_backlogs_allowed, target_graduation_year, drive_date,
          application_deadline, available_positions, status, selection_process,
          eligible_branches, required_skills
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          id,
          d.companyId,
          companyName,
          d.jobRole.trim(),
          d.jobDescription || '',
          Number(d.salaryPackageLpa ?? 0),
          Number(d.minCgpa ?? 6.0),
          Number(d.maxBacklogsAllowed ?? 0),
          Number(d.targetGraduationYear ?? 2026),
          d.driveDate || toIsoDateOnly(new Date()),
          d.applicationDeadline || d.driveDate || toIsoDateOnly(new Date()),
          Number(d.availablePositions ?? 1),
          d.status || 'OPEN',
          d.selectionProcess || 'Online Assessment -> Technical Interview -> HR',
          JSON.stringify(d.eligibleBranches || ['CSE', 'IT', 'ECE']),
          JSON.stringify(d.requiredSkills || []),
        ]
      );
      return res.status(201).json({ success: true, id });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.put('/drives/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role) && ctx.session.role !== 'COMPANY') {
      return res.status(403).json({ error: 'Not authorized to update placement drives.' });
    }
    const d = req.body || {};
    try {
      await ctx.pool.query(
        `UPDATE placement_drives SET
          company_id = COALESCE(?, company_id),
          company_name = COALESCE(?, company_name),
          job_role = COALESCE(?, job_role),
          job_description = COALESCE(?, job_description),
          salary_package_lpa = COALESCE(?, salary_package_lpa),
          min_cgpa = COALESCE(?, min_cgpa),
          max_backlogs_allowed = COALESCE(?, max_backlogs_allowed),
          target_graduation_year = COALESCE(?, target_graduation_year),
          drive_date = COALESCE(?, drive_date),
          application_deadline = COALESCE(?, application_deadline),
          available_positions = COALESCE(?, available_positions),
          status = COALESCE(?, status),
          selection_process = COALESCE(?, selection_process),
          eligible_branches = COALESCE(?, eligible_branches),
          required_skills = COALESCE(?, required_skills)
         WHERE id = ?`,
        [
          d.companyId || null,
          d.companyName || null,
          d.jobRole || null,
          d.jobDescription ?? null,
          d.salaryPackageLpa ?? null,
          d.minCgpa ?? null,
          d.maxBacklogsAllowed ?? null,
          d.targetGraduationYear ?? null,
          d.driveDate || null,
          d.applicationDeadline || null,
          d.availablePositions ?? null,
          d.status || null,
          d.selectionProcess ?? null,
          d.eligibleBranches ? JSON.stringify(d.eligibleBranches) : null,
          d.requiredSkills ? JSON.stringify(d.requiredSkills) : null,
          req.params.id,
        ]
      );
      return res.json({ success: true, id: req.params.id });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.delete('/drives/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!ADMIN_OR_OFFICER_ROLES.has(ctx.session.role) && ctx.session.role !== 'COMPANY') {
      return res.status(403).json({ error: 'Not authorized to delete placement drives.' });
    }
    try {
      await ctx.pool.query(`DELETE FROM applications WHERE drive_id = ?`, [req.params.id]);
      await ctx.pool.query(`DELETE FROM placement_drives WHERE id = ?`, [req.params.id]);
      return res.json({ success: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  // 7. Skills CRUD in MySQL
  apiRouter.post('/skills', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Staff authorization required to manage skills.' });
    }
    const { name, category, description } = req.body || {};
    if (!name?.trim() || !category?.trim()) {
      return res.status(400).json({ error: 'Skill name and category are required.' });
    }
    const id = `sk-${crypto.randomUUID()}`;
    try {
      await ctx.pool.query(
        `INSERT INTO skills (id, name, category, description, status) VALUES (?, ?, ?, ?, 'ACTIVE')`,
        [id, name.trim(), category.trim(), description || '']
      );
      return res.status(201).json({ success: true, id });
    } catch (err: any) {
      if (err?.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({ error: 'This skill already exists in the MySQL skill catalog.' });
      }
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.put('/skills/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Staff authorization required.' });
    }
    const { name, category, description } = req.body || {};
    try {
      await ctx.pool.query(
        `UPDATE skills SET name = COALESCE(?, name), category = COALESCE(?, category), description = COALESCE(?, description) WHERE id = ?`,
        [name?.trim() || null, category?.trim() || null, description ?? null, req.params.id]
      );
      return res.json({ success: true, id: req.params.id });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.delete('/skills/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!ADMIN_OR_OFFICER_ROLES.has(ctx.session.role)) {
      return res.status(403).json({ error: 'Administrator authorization required.' });
    }
    try {
      await ctx.pool.query(`DELETE FROM student_skills WHERE skill_id = ?`, [req.params.id]);
      await ctx.pool.query(`DELETE FROM skills WHERE id = ?`, [req.params.id]);
      return res.json({ success: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  // 8. Applications Workflow in MySQL
  apiRouter.post('/applications', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    const { driveId, studentId } = req.body || {};
    if (!driveId || !studentId) {
      return res.status(400).json({ error: 'driveId and studentId are required.' });
    }
    try {
      const [[stRows], [drvRows]]: any = await Promise.all([
        ctx.pool.query(`SELECT * FROM students WHERE id = ? LIMIT 1`, [studentId]),
        ctx.pool.query(`SELECT * FROM placement_drives WHERE id = ? LIMIT 1`, [driveId]),
      ]);
      const student = stRows?.[0];
      const drive = drvRows?.[0];
      if (!student || !drive) {
        return res.status(404).json({ error: 'Student or Placement Drive not found in MySQL.' });
      }
      if (ctx.session.role === 'STUDENT' && student.user_id !== ctx.session.userId && student.roll_number?.toLowerCase() !== ctx.session.studentRollNumber?.toLowerCase()) {
        return res.status(403).json({ error: 'Students may only submit applications for themselves.' });
      }

      const id = `app-${crypto.randomUUID()}`;
      const history = [
        {
          stage: 'APPLICATION_SUBMITTED',
          timestamp: new Date().toISOString(),
          updatedBy: ctx.session.fullName || ctx.session.username,
          notes: 'Application submitted via GCET Placement Hub',
        },
      ];
      await ctx.pool.query(
        `INSERT INTO applications (
          id, drive_id, student_id, student_roll_number, student_name, student_branch,
          student_cgpa, company_id, company_name, job_role, current_stage, stage_history
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPLICATION_SUBMITTED', ?)`,
        [
          id,
          drive.id,
          student.id,
          student.roll_number,
          student.full_name,
          student.branch,
          student.cgpa,
          drive.company_id,
          drive.company_name,
          drive.job_role,
          JSON.stringify(history),
        ]
      );
      return res.status(201).json({ success: true, id });
    } catch (err: any) {
      if (err?.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({ error: 'Student has already applied for this placement drive.' });
      }
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.put('/applications/:id/stage', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!STAFF_ROLES.has(ctx.session.role) && ctx.session.role !== 'COMPANY') {
      return res.status(403).json({ error: 'Only placement staff or recruiters can advance application stages.' });
    }
    const { nextStage, notes, finalOfferLpa, rejectionReason } = req.body || {};
    if (!nextStage) return res.status(400).json({ error: 'nextStage is required.' });

    const conn = await ctx.pool.getConnection();
    try {
      await conn.beginTransaction();
      const [rows]: any = await conn.query(`SELECT * FROM applications WHERE id = ? LIMIT 1`, [req.params.id]);
      if (!rows || rows.length === 0) {
        await conn.rollback();
        return res.status(404).json({ error: 'Application not found in MySQL.' });
      }
      const appRow = rows[0];
      const history = parseJsonArray(appRow.stage_history);
      history.push({
        stage: nextStage,
        timestamp: new Date().toISOString(),
        updatedBy: ctx.session.fullName || ctx.session.username,
        notes: notes || rejectionReason || undefined,
      });

      await conn.query(
        `UPDATE applications
         SET current_stage = ?, stage_history = ?, final_offer_lpa = COALESCE(?, final_offer_lpa), rejection_reason = COALESCE(?, rejection_reason)
         WHERE id = ?`,
        [nextStage, JSON.stringify(history), finalOfferLpa ?? null, rejectionReason ?? null, appRow.id]
      );

      if (['SELECTED', 'OFFERED', 'JOINED'].includes(nextStage)) {
        await conn.query(
          `UPDATE students
           SET placement_status = 'PLACED', placed_company_id = COALESCE(?, placed_company_id), placed_package_lpa = COALESCE(?, placed_package_lpa)
           WHERE id = ?`,
          [appRow.company_id || null, finalOfferLpa ?? null, appRow.student_id]
        );
      } else if (nextStage === 'SHORTLISTED') {
        await conn.query(
          `UPDATE students SET placement_status = 'SHORTLISTED' WHERE id = ? AND placement_status = 'UNPLACED'`,
          [appRow.student_id]
        );
      }

      await conn.commit();
      return res.json({ success: true });
    } catch (err: any) {
      await conn.rollback();
      return res.status(500).json({ error: err.message });
    } finally {
      conn.release();
    }
  });

  apiRouter.post('/applications/:id/withdraw', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    try {
      const [rows]: any = await ctx.pool.query(`SELECT * FROM applications WHERE id = ? LIMIT 1`, [req.params.id]);
      if (!rows || rows.length === 0) {
        return res.status(404).json({ error: 'Application not found in MySQL.' });
      }
      const appRow = rows[0];
      const history = parseJsonArray(appRow.stage_history);
      history.push({
        stage: 'WITHDRAWN',
        timestamp: new Date().toISOString(),
        updatedBy: ctx.session.fullName || ctx.session.username,
        notes: 'Application withdrawn',
      });
      await ctx.pool.query(
        `UPDATE applications SET current_stage = 'WITHDRAWN', stage_history = ? WHERE id = ?`,
        [JSON.stringify(history), appRow.id]
      );
      return res.json({ success: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  // 9. User Management in MySQL (Admin Only)
  apiRouter.post('/users', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(ctx.session.role)) {
      return res.status(403).json({ error: 'Administrator privileges required to create accounts.' });
    }
    const { username, fullName, email, role, department, password } = req.body || {};
    if (!username?.trim() || !fullName?.trim() || !email?.trim() || !role || !password || String(password).length < 6) {
      return res.status(400).json({ error: 'Username, full name, email, role, and password (min 6 chars) are required.' });
    }
    const id = `usr-${crypto.randomUUID()}`;
    const salt = generateSalt();
    const hash = hashPassword(String(password), salt);
    try {
      await ctx.pool.query(
        `INSERT INTO users (id, username, email, full_name, role, department, password_hash, salt, status, is_active, approved_by, approved_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', TRUE, ?, NOW())`,
        [id, username.trim().toLowerCase(), email.trim().toLowerCase(), fullName.trim(), role, department || 'CSE', hash, salt, ctx.session.fullName]
      );
      await ctx.pool.query(
        `INSERT IGNORE INTO user_profiles (user_id, location) VALUES (?, 'Hyderabad, Telangana')`,
        [id]
      );
      return res.status(201).json({ success: true, id });
    } catch (err: any) {
      if (err?.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({ error: 'Username or email already exists in MySQL.' });
      }
      return res.status(500).json({ error: err.message });
    }
  });

  apiRouter.delete('/users/:id', async (req: Request, res: Response) => {
    const ctx = requireDbSession(req, res);
    if (!ctx) return;
    if (!['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(ctx.session.role)) {
      return res.status(403).json({ error: 'Administrator privileges required to remove accounts.' });
    }
    if (req.params.id === ctx.session.userId) {
      return res.status(400).json({ error: 'Cannot delete the currently logged-in account.' });
    }
    try {
      const [rows]: any = await ctx.pool.query(`SELECT username, role FROM users WHERE id = ? LIMIT 1`, [req.params.id]);
      if (rows?.[0] && ['principal.sagar', 'chairman.ravinder', 'vc.harishchandra', 'admin'].includes(rows[0].username.toLowerCase())) {
        return res.status(403).json({ error: 'Primary executive institutional accounts cannot be deleted.' });
      }
      await ctx.pool.query(`DELETE FROM users WHERE id = ?`, [req.params.id]);
      return res.json({ success: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });
}
