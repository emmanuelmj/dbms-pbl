import { Router, Request, Response } from 'express';
import crypto from 'crypto';
import { getPool, isDbConnected } from './db';

export interface SessionInfo {
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

type SessionResolver = (req: Request) => SessionInfo | null;

const MAX_PHOTO_BYTES = 2 * 1024 * 1024; // 2MB

export function validateWebUrl(raw: unknown, fieldLabel: string): string {
  if (raw === null || raw === undefined) return '';
  const trimmed = String(raw).trim();
  if (!trimmed) return '';

  // Reject dangerous schemes immediately
  const lower = trimmed.toLowerCase();
  if (lower.startsWith('javascript:') || lower.startsWith('data:') || lower.startsWith('vbscript:')) {
    throw new Error(`${fieldLabel} must be a valid http:// or https:// URL.`);
  }

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new Error(`${fieldLabel} is not a valid URL. Include https:// or http:// (e.g. https://linkedin.com/in/username).`);
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error(`${fieldLabel} must start with http:// or https://.`);
  }
  if (!parsed.hostname || !parsed.hostname.includes('.')) {
    if (parsed.hostname !== 'localhost') {
      throw new Error(`${fieldLabel} must contain a valid domain name.`);
    }
  }
  return parsed.toString();
}

export function validateEmailAddress(raw: unknown): string {
  const email = String(raw || '').trim().toLowerCase();
  if (!email) throw new Error('Email address is required.');
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  if (!re.test(email)) {
    throw new Error('Please enter a valid email address.');
  }
  return email;
}

export function validatePhone(raw: unknown): string {
  const phone = String(raw || '').trim();
  if (!phone) return '';
  if (!/^[+\d\s\-().]{7,20}$/.test(phone)) {
    throw new Error('Phone number must be 7–20 characters and contain only digits, spaces, +, -, or parentheses.');
  }
  return phone;
}

export function validateImageDataUrl(dataUrl: unknown): string {
  if (typeof dataUrl !== 'string' || !dataUrl.trim()) {
    throw new Error('Image data is required.');
  }
  const match = /^data:image\/(png|jpeg|jpg|webp);base64,([A-Za-z0-9+/=]+)$/.exec(dataUrl.trim());
  if (!match) {
    throw new Error('Only PNG, JPEG, or WEBP image files are permitted.');
  }

  const mimeSubtype = match[1].toLowerCase();
  const base64Body = match[2];
  const buffer = Buffer.from(base64Body, 'base64');

  if (buffer.length === 0) {
    throw new Error('Uploaded image file is empty.');
  }
  if (buffer.length > MAX_PHOTO_BYTES) {
    throw new Error('Image size exceeds the 2 MB security limit.');
  }

  // Verify magic bytes to prevent spoofed file uploads
  const isPng = buffer.length >= 4 && buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4e && buffer[3] === 0x47;
  const isJpeg = buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff;
  const isWebp =
    buffer.length >= 12 &&
    buffer.toString('ascii', 0, 4) === 'RIFF' &&
    buffer.toString('ascii', 8, 12) === 'WEBP';

  if (mimeSubtype === 'png' && !isPng) {
    throw new Error('Invalid PNG image file signature.');
  }
  if ((mimeSubtype === 'jpeg' || mimeSubtype === 'jpg') && !isJpeg) {
    throw new Error('Invalid JPEG image file signature.');
  }
  if (mimeSubtype === 'webp' && !isWebp) {
    throw new Error('Invalid WEBP image file signature.');
  }

  return dataUrl.trim();
}

function parseJsonOrCsvList(val: any): string[] {
  if (!val) return [];
  if (Array.isArray(val)) return val.map((v) => String(v).trim()).filter(Boolean);
  if (typeof val === 'string') {
    const t = val.trim();
    if (!t) return [];
    if (t.startsWith('[')) {
      try {
        const parsed = JSON.parse(t);
        if (Array.isArray(parsed)) return parsed.map((v) => String(v).trim()).filter(Boolean);
      } catch {
        // Fallback to CSV
      }
    }
    return t.split(',').map((s) => s.trim()).filter(Boolean);
  }
  return [];
}

function serializeList(val: any): string {
  const arr = parseJsonOrCsvList(val);
  return JSON.stringify(arr);
}

export function computeCompletionPercentage(payload: {
  role: string;
  fullName?: string;
  email?: string;
  phone?: string;
  location?: string;
  aboutMe?: string;
  profilePhoto?: string;
  socialLinks?: Record<string, string>;
  customLinks?: any[];
  student?: any;
  faculty?: any;
  company?: any;
}): { percentage: number; missingSections: string[] } {
  const checks: { label: string; ok: boolean }[] = [
    { label: 'Profile Photo', ok: Boolean(payload.profilePhoto || payload.company?.companyLogo) },
    { label: 'Full Name', ok: Boolean(payload.fullName?.trim()) },
    { label: 'Email Address', ok: Boolean(payload.email?.trim()) },
    { label: 'Phone Number', ok: Boolean(payload.phone?.trim()) },
    { label: 'Location', ok: Boolean(payload.location?.trim()) },
    { label: 'About Me / Bio', ok: Boolean(payload.aboutMe?.trim()) },
  ];

  const hasSocial =
    Object.values(payload.socialLinks || {}).some((v) => Boolean(v && v.trim())) ||
    (payload.customLinks?.length || 0) > 0;
  checks.push({ label: 'Social / Professional Link', ok: hasSocial });

  if (payload.role === 'STUDENT' && payload.student) {
    const st = payload.student;
    checks.push({ label: 'Date of Birth / Personal Info', ok: Boolean(st.dob || st.address) });
    checks.push({ label: 'Academic Metrics & Semester', ok: Boolean(st.cgpa > 0 && st.section && st.currentSemester) });
    const hasSkills =
      (st.skills?.length || 0) > 0 ||
      (st.programmingLanguages?.length || 0) > 0 ||
      (st.frameworksTechnologies?.length || 0) > 0;
    checks.push({ label: 'Technical Skills', ok: hasSkills });
    checks.push({ label: 'Projects', ok: (st.projects?.length || 0) > 0 });
    checks.push({ label: 'Certifications', ok: (st.certifications?.length || 0) > 0 });
    checks.push({ label: 'Career Interests & Roles', ok: (st.careerInterests?.length || 0) > 0 || (st.preferredJobRoles?.length || 0) > 0 });
  } else if (payload.role === 'FACULTY' && payload.faculty) {
    const f = payload.faculty;
    checks.push({ label: 'Qualifications', ok: Boolean(f.qualifications?.trim()) });
    checks.push({ label: 'Areas of Expertise', ok: (f.areasOfExpertise?.length || 0) > 0 });
    checks.push({ label: 'Subjects Handled', ok: (f.subjectsHandled?.length || 0) > 0 });
    checks.push({ label: 'Research Interests', ok: Boolean(f.researchInterests?.trim()) });
    checks.push({ label: 'Publications / Certifications', ok: (f.publications?.length || 0) > 0 || (f.certifications?.length || 0) > 0 });
  } else if (payload.role === 'COMPANY' && payload.company) {
    const c = payload.company;
    checks.push({ label: 'Company Name & Industry', ok: Boolean(c.name?.trim() && c.industry?.trim()) });
    checks.push({ label: 'Company Website', ok: Boolean(c.website?.trim()) });
    checks.push({ label: 'Company Description', ok: Boolean(c.description?.trim()) });
    checks.push({ label: 'Company Logo', ok: Boolean(c.companyLogo?.trim()) });
  }

  const completed = checks.filter((c) => c.ok).length;
  const percentage = Math.round((completed / Math.max(1, checks.length)) * 100);
  const missingSections = checks.filter((c) => !c.ok).map((c) => c.label);
  return { percentage, missingSections };
}

export async function loadFullProfileByUserId(userId: string) {
  const pool = getPool();
  if (!pool || !isDbConnected()) return null;

  const [userRows]: any = await pool.query(
    `SELECT id, username, email, full_name, role, department, student_roll_number, company_id,
            status, is_active, authorized_departments, last_login, created_at
     FROM users WHERE id = ? LIMIT 1`,
    [userId]
  );
  if (!userRows || userRows.length === 0) return null;
  const u = userRows[0];

  // Unified profile
  const [profRows]: any = await pool.query(`SELECT * FROM user_profiles WHERE user_id = ? LIMIT 1`, [u.id]);
  const prof = profRows?.[0] || {};

  // Custom links
  const [customLinkRows]: any = await pool.query(
    `SELECT id, title, url, display_order FROM user_custom_links WHERE user_id = ? ORDER BY display_order ASC, created_at ASC`,
    [u.id]
  );

  // User certifications (shared for Student and Faculty)
  const [certRows]: any = await pool.query(
    `SELECT id, name, issuing_org, issue_date, credential_id, credential_url
     FROM user_certifications WHERE user_id = ? ORDER BY created_at DESC`,
    [u.id]
  );

  const socialLinks = {
    linkedin: prof.linkedin_url || '',
    github: prof.github_url || '',
    portfolio: prof.portfolio_url || '',
    leetcode: prof.leetcode_url || '',
    hackerrank: prof.hackerrank_url || '',
    codechef: prof.codechef_url || '',
    geeksforgeeks: prof.gfg_url || '',
    kaggle: prof.kaggle_url || '',
    googleDeveloper: prof.google_dev_url || '',
  };

  let studentData: any = null;
  let facultyData: any = null;
  let companyData: any = null;

  let resolvedPhone = prof.phone || '';
  let resolvedLocation = prof.location || '';
  let resolvedAbout = prof.about_me || '';
  let resolvedDesignation = prof.designation || '';

  if (u.role === 'STUDENT') {
    const [stRows]: any = await pool.query(
      `SELECT * FROM students WHERE user_id = ? OR LOWER(roll_number) = LOWER(?) LIMIT 1`,
      [u.id, u.student_roll_number || u.username]
    );
    if (stRows && stRows.length > 0) {
      const s = stRows[0];
      const [skillsRows]: any = await pool.query(
        `SELECT ss.skill_id, sk.name, sk.category, ss.proficiency, ss.verified_by_faculty, ss.verified_by_faculty_id, ss.verified_at
         FROM student_skills ss JOIN skills sk ON sk.id = ss.skill_id WHERE ss.student_id = ?`,
        [s.id]
      );
      const [projRows]: any = await pool.query(
        `SELECT id, title, description, technologies, project_url, duration FROM student_projects WHERE student_id = ? ORDER BY created_at DESC`,
        [s.id]
      );
      const [internRows]: any = await pool.query(
        `SELECT id, company_name, role, duration, location, description FROM student_internships WHERE student_id = ? ORDER BY created_at DESC`,
        [s.id]
      );
      const [achRows]: any = await pool.query(
        `SELECT id, title, category, organizer, event_date, description, credential_url FROM student_achievements WHERE student_id = ? ORDER BY created_at DESC`,
        [s.id]
      );
      const [stCertRows]: any = await pool.query(
        `SELECT id, name, issuing_org, issue_date, credential_id, credential_url FROM user_certifications WHERE student_id = ? OR user_id = ? ORDER BY created_at DESC`,
        [s.id, u.id]
      );

      // Deduplicate certs if any
      const certMap = new Map<string, any>();
      for (const c of stCertRows || []) {
        certMap.set(c.id, {
          id: c.id,
          name: c.name,
          issuingOrg: c.issuing_org,
          issueDate: c.issue_date || '',
          credentialId: c.credential_id || '',
          credentialUrl: c.credential_url || '',
        });
      }

      resolvedPhone = resolvedPhone || s.phone || '';
      resolvedLocation = resolvedLocation || s.location || '';
      resolvedAbout = resolvedAbout || s.about_me || '';
      resolvedDesignation = resolvedDesignation || `${s.branch} Undergraduate`;

      studentData = {
        id: s.id,
        rollNumber: s.roll_number,
        fullName: s.full_name,
        email: s.email,
        phone: resolvedPhone,
        branch: s.branch,
        section: s.section || 'A',
        academicYear: s.academic_year || s.year_of_study || 4,
        currentSemester: s.current_semester || 7,
        graduationYear: s.graduation_year || 2026,
        cgpa: Number(s.cgpa || 0),
        tenthPercentage: Number(s.tenth_percentage || 0),
        interOrDiplomaPercentage: Number(s.inter_percentage || 0),
        backlogs: Number(s.backlogs || 0),
        placementStatus: s.placement_status || 'UNPLACED',
        approvalStatus: s.approval_status || 'APPROVED',
        placedCompanyId: s.placed_company_id || undefined,
        placedPackageLpa: s.placed_package_lpa ? Number(s.placed_package_lpa) : undefined,
        resumeUrl: s.resume_url || '',
        dob: prof.date_of_birth || s.dob || '',
        gender: prof.gender || s.gender || '',
        address: prof.address || s.address || '',
        location: resolvedLocation,
        aboutMe: resolvedAbout,
        technicalSkills: parseJsonOrCsvList(s.technical_skills),
        programmingLanguages: parseJsonOrCsvList(s.programming_languages),
        frameworksTechnologies: parseJsonOrCsvList(s.frameworks_technologies),
        softSkills: parseJsonOrCsvList(s.soft_skills),
        careerInterests: parseJsonOrCsvList(s.career_interests),
        preferredJobRoles: parseJsonOrCsvList(s.preferred_job_roles),
        skills: (skillsRows || []).map((sk: any) => ({
          skillId: sk.skill_id,
          skillName: sk.name,
          category: sk.category,
          proficiency: sk.proficiency,
          verifiedByFaculty: Boolean(sk.verified_by_faculty),
          verifiedByFacultyId: sk.verified_by_faculty_id || undefined,
          verifiedAt: sk.verified_at ? new Date(sk.verified_at).toISOString() : undefined,
        })),
        projects: (projRows || []).map((p: any) => ({
          id: p.id,
          title: p.title,
          description: p.description || '',
          technologies: parseJsonOrCsvList(p.technologies),
          link: p.project_url || '',
          duration: p.duration || '',
        })),
        internships: (internRows || []).map((i: any) => ({
          id: i.id,
          companyName: i.company_name,
          role: i.role,
          duration: i.duration || '',
          location: i.location || '',
          description: i.description || '',
        })),
        certifications: Array.from(certMap.values()),
        achievements: (achRows || []).map((a: any) => ({
          id: a.id,
          title: a.title,
          category: a.category || 'AWARD',
          organizer: a.organizer || '',
          eventDate: a.event_date || '',
          description: a.description || '',
          credentialUrl: a.credential_url || '',
        })),
      };
    }
  } else if (u.role === 'FACULTY') {
    const [facRows]: any = await pool.query(
      `SELECT * FROM faculty WHERE user_id = ? OR LOWER(email) = LOWER(?) LIMIT 1`,
      [u.id, u.email]
    );
    const f = facRows?.[0];
    const [pubRows]: any = await pool.query(
      `SELECT id, title, journal_or_conference, publication_year, authors, doi_or_url
       FROM faculty_publications WHERE user_id = ? ORDER BY publication_year DESC, created_at DESC`,
      [u.id]
    );

    resolvedPhone = resolvedPhone || f?.phone || '';
    resolvedLocation = resolvedLocation || f?.location || 'Hyderabad, Telangana';
    resolvedAbout = resolvedAbout || f?.about_me || '';
    resolvedDesignation = resolvedDesignation || f?.designation || 'Faculty Coordinator';

    facultyData = {
      id: f?.id || '',
      facultyId: f?.faculty_id || u.username.toUpperCase(),
      employeeId: f?.employee_id || f?.faculty_id || '',
      department: f?.department || u.department || 'CSE',
      designation: resolvedDesignation,
      qualifications: f?.qualifications || '',
      areasOfExpertise: parseJsonOrCsvList(f?.areas_of_expertise),
      subjectsHandled: parseJsonOrCsvList(f?.subjects_handled),
      researchInterests: f?.research_interests || '',
      facultyApproved: f ? Boolean(f.faculty_approved) : true,
      adminApproved: f ? Boolean(f.admin_approved) : true,
      status: f?.status || u.status || 'ACTIVE',
      publications: (pubRows || []).map((p: any) => ({
        id: p.id,
        title: p.title,
        journalOrConference: p.journal_or_conference || '',
        year: p.publication_year || '',
        authors: p.authors || '',
        url: p.doi_or_url || '',
      })),
      certifications: (certRows || []).map((c: any) => ({
        id: c.id,
        name: c.name,
        issuingOrg: c.issuing_org,
        issueDate: c.issue_date || '',
        credentialId: c.credential_id || '',
        credentialUrl: c.credential_url || '',
      })),
    };
  } else if (u.role === 'COMPANY') {
    const [compRows]: any = await pool.query(
      `SELECT * FROM companies WHERE id = ? OR user_id = ? OR LOWER(hr_email) = LOWER(?) LIMIT 1`,
      [u.company_id || '', u.id, u.email]
    );
    const c = compRows?.[0];
    resolvedPhone = resolvedPhone || c?.hr_phone || '';
    resolvedLocation = resolvedLocation || c?.location || 'Hyderabad, Telangana';
    resolvedAbout = resolvedAbout || c?.description || '';
    resolvedDesignation = resolvedDesignation || c?.recruiter_designation || 'Campus Recruiter';

    if (c?.linkedin_url && !socialLinks.linkedin) {
      socialLinks.linkedin = c.linkedin_url;
    }

    companyData = c
      ? {
          id: c.id,
          name: c.name,
          industry: c.industry || '',
          website: c.website || '',
          location: c.location || '',
          description: c.description || '',
          recruiterName: c.recruiter_name || c.hr_name || u.full_name,
          recruiterDesignation: c.recruiter_designation || resolvedDesignation,
          hrName: c.hr_name || u.full_name,
          hrEmail: c.hr_email || u.email,
          hrPhone: c.hr_phone || resolvedPhone,
          companyLogo: c.company_logo || '',
          linkedinUrl: c.linkedin_url || socialLinks.linkedin || '',
          tier: c.tier || 'Standard IT',
          approvalStatus: c.approval_status || 'ACTIVE',
        }
      : {
          id: u.company_id || '',
          name: u.department || '',
          industry: 'IT Services',
          website: '',
          location: resolvedLocation,
          description: resolvedAbout,
          recruiterName: u.full_name,
          recruiterDesignation: resolvedDesignation,
          hrName: u.full_name,
          hrEmail: u.email,
          hrPhone: resolvedPhone,
          companyLogo: '',
          linkedinUrl: socialLinks.linkedin || '',
          tier: 'Standard IT',
          approvalStatus: u.status || 'ACTIVE',
        };
  }

  const profilePayload = {
    user: {
      id: u.id,
      username: u.username,
      email: u.email,
      fullName: u.full_name,
      role: u.role,
      department: u.department,
      designation: resolvedDesignation,
      studentRollNumber: u.student_roll_number,
      companyId: u.company_id || companyData?.id || undefined,
      status: u.status,
      isActive: Boolean(u.is_active),
      phone: resolvedPhone,
      location: resolvedLocation,
      address: prof.address || studentData?.address || '',
      aboutMe: resolvedAbout,
      profilePhoto: prof.profile_photo || '',
      authorizedDepartments: u.authorized_departments
        ? typeof u.authorized_departments === 'string'
          ? JSON.parse(u.authorized_departments)
          : u.authorized_departments
        : [],
    },
    socialLinks,
    customLinks: (customLinkRows || []).map((cl: any) => ({
      id: cl.id,
      title: cl.title,
      url: cl.url,
    })),
    student: studentData,
    faculty: facultyData,
    company: companyData,
  };

  const completion = computeCompletionPercentage({
    role: u.role,
    fullName: u.full_name,
    email: u.email,
    phone: resolvedPhone,
    location: resolvedLocation,
    aboutMe: resolvedAbout,
    profilePhoto: prof.profile_photo || '',
    socialLinks,
    customLinks: profilePayload.customLinks,
    student: studentData,
    faculty: facultyData,
    company: companyData,
  });

  return {
    ...profilePayload,
    completionPercentage: completion.percentage,
    missingSections: completion.missingSections,
  };
}

export function registerProfileRoutes(apiRouter: Router, getSession: SessionResolver) {
  // 1. Get Own Full Profile
  apiRouter.get('/profile/me', async (req: Request, res: Response) => {
    try {
      const session = getSession(req);
      if (!session) {
        return res.status(401).json({ error: 'Authentication required to access your profile.' });
      }

      const pool = getPool();
      if (!pool || !isDbConnected()) {
        return res.status(503).json({ error: 'MySQL database is currently in standby.' });
      }

      const profile = await loadFullProfileByUserId(session.userId);
      if (!profile) {
        return res.status(404).json({ error: 'User profile not found in MySQL.' });
      }

      return res.json(profile);
    } catch (err: any) {
      console.error('[Profile GET /me] Error:', err);
      return res.status(500).json({ error: err.message || 'Failed to load profile from MySQL.' });
    }
  });

  // 2. Get Read-Only Public Profile by User ID (or Student ID / Roll Number)
  apiRouter.get('/profile/user/:id', async (req: Request, res: Response) => {
    try {
      const session = getSession(req);
      if (!session) {
        return res.status(401).json({ error: 'Authentication required.' });
      }

      const pool = getPool();
      if (!pool || !isDbConnected()) {
        return res.status(503).json({ error: 'MySQL database is currently in standby.' });
      }

      const target = req.params.id;
      const [uRows]: any = await pool.query(
        `SELECT u.id FROM users u
         LEFT JOIN students s ON s.user_id = u.id OR LOWER(s.roll_number) = LOWER(u.student_roll_number)
         WHERE u.id = ? OR LOWER(u.username) = LOWER(?) OR s.id = ? OR LOWER(s.roll_number) = LOWER(?)
         LIMIT 1`,
        [target, target, target, target]
      );

      if (!uRows || uRows.length === 0) {
        return res.status(404).json({ error: 'Requested profile was not found.' });
      }

      const profile = await loadFullProfileByUserId(uRows[0].id);
      if (!profile) {
        return res.status(404).json({ error: 'Requested profile was not found.' });
      }

      // Omit empty social links in public/read-only view
      const nonEmptyLinks: Record<string, string> = {};
      for (const [k, v] of Object.entries(profile.socialLinks || {})) {
        if (v && String(v).trim()) {
          nonEmptyLinks[k] = String(v).trim();
        }
      }

      return res.json({
        ...profile,
        socialLinks: nonEmptyLinks,
        isOwnProfile: uRows[0].id === session.userId,
      });
    } catch (err: any) {
      console.error('[Profile GET /user/:id] Error:', err);
      return res.status(500).json({ error: err.message || 'Failed to load profile.' });
    }
  });

  // 3. Upload / Replace Profile Photo or Company Logo
  apiRouter.post('/profile/photo', async (req: Request, res: Response) => {
    try {
      const session = getSession(req);
      if (!session) {
        return res.status(401).json({ error: 'Authentication required.' });
      }

      const pool = getPool();
      if (!pool || !isDbConnected()) {
        return res.status(503).json({ error: 'MySQL database is currently in standby.' });
      }

      const { imageDataUrl, target } = req.body;
      const cleanImage = validateImageDataUrl(imageDataUrl);

      if (target === 'company_logo') {
        if (session.role !== 'COMPANY') {
          return res.status(403).json({ error: 'Only corporate recruiter accounts can update company logos.' });
        }
        const [uRows]: any = await pool.query(`SELECT company_id, email FROM users WHERE id = ? LIMIT 1`, [session.userId]);
        const compId = uRows?.[0]?.company_id;
        await pool.query(
          `UPDATE companies SET company_logo = ? WHERE id = ? OR user_id = ? OR LOWER(hr_email) = LOWER(?)`,
          [cleanImage, compId || '', session.userId, uRows?.[0]?.email || '']
        );
        return res.json({ success: true, message: 'Company logo uploaded and saved to MySQL.', imageDataUrl: cleanImage });
      }

      await pool.query(
        `INSERT INTO user_profiles (user_id, profile_photo) VALUES (?, ?)
         ON DUPLICATE KEY UPDATE profile_photo = VALUES(profile_photo)`,
        [session.userId, cleanImage]
      );

      return res.json({ success: true, message: 'Profile photo updated in MySQL.', imageDataUrl: cleanImage });
    } catch (err: any) {
      return res.status(400).json({ error: err.message || 'Invalid photo upload.' });
    }
  });

  // 4. Remove Profile Photo or Company Logo
  apiRouter.delete('/profile/photo', async (req: Request, res: Response) => {
    try {
      const session = getSession(req);
      if (!session) {
        return res.status(401).json({ error: 'Authentication required.' });
      }
      const pool = getPool();
      if (!pool || !isDbConnected()) {
        return res.status(503).json({ error: 'MySQL database is currently in standby.' });
      }

      const target = req.query.target;
      if (target === 'company_logo') {
        if (session.role !== 'COMPANY') {
          return res.status(403).json({ error: 'Only corporate recruiter accounts can remove company logos.' });
        }
        const [uRows]: any = await pool.query(`SELECT company_id, email FROM users WHERE id = ? LIMIT 1`, [session.userId]);
        await pool.query(
          `UPDATE companies SET company_logo = NULL WHERE id = ? OR user_id = ? OR LOWER(hr_email) = LOWER(?)`,
          [uRows?.[0]?.company_id || '', session.userId, uRows?.[0]?.email || '']
        );
        return res.json({ success: true, message: 'Company logo removed.' });
      }

      await pool.query(`UPDATE user_profiles SET profile_photo = NULL WHERE user_id = ?`, [session.userId]);
      return res.json({ success: true, message: 'Profile photo removed.' });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  });

  // 5. Update Own Profile (Transactional MySQL Save Across All Role Tables)
  apiRouter.put('/profile/me', async (req: Request, res: Response) => {
    const session = getSession(req);
    if (!session) {
      return res.status(401).json({ error: 'Authentication required. Please log in again.' });
    }

    const pool = getPool();
    if (!pool || !isDbConnected()) {
      return res.status(503).json({ error: 'MySQL database is currently in standby. Changes could not be saved to MySQL.' });
    }

    try {
      const body = req.body || {};

      // Validate common profile fields
      const fullName = String(body.fullName || '').trim();
      if (!fullName) {
        return res.status(400).json({ error: 'Full Name cannot be empty.' });
      }

      const email = validateEmailAddress(body.email);
      const phone = validatePhone(body.phone);
      const location = String(body.location || '').trim().slice(0, 150);
      const address = String(body.address || '').trim();
      const aboutMe = String(body.aboutMe || '').trim();
      const designation = String(body.designation || '').trim().slice(0, 120);
      const dob = String(body.dob || body.student?.dob || '').trim().slice(0, 20);
      const gender = String(body.gender || body.student?.gender || '').trim().slice(0, 30);

      let profilePhoto: string | undefined = undefined;
      if (body.profilePhoto !== undefined) {
        profilePhoto = body.profilePhoto ? validateImageDataUrl(body.profilePhoto) : '';
      }

      // Validate Social Media & Professional Links
      const rawLinks = body.socialLinks || {};
      const cleanLinks = {
        linkedin: validateWebUrl(rawLinks.linkedin, 'LinkedIn URL'),
        github: validateWebUrl(rawLinks.github, 'GitHub URL'),
        portfolio: validateWebUrl(rawLinks.portfolio, 'Personal Portfolio URL'),
        leetcode: validateWebUrl(rawLinks.leetcode, 'LeetCode URL'),
        hackerrank: validateWebUrl(rawLinks.hackerrank, 'HackerRank URL'),
        codechef: validateWebUrl(rawLinks.codechef, 'CodeChef URL'),
        geeksforgeeks: validateWebUrl(rawLinks.geeksforgeeks, 'GeeksforGeeks URL'),
        kaggle: validateWebUrl(rawLinks.kaggle, 'Kaggle URL'),
        googleDeveloper: validateWebUrl(rawLinks.googleDeveloper, 'Google Developer URL'),
      };

      const rawCustomLinks = Array.isArray(body.customLinks) ? body.customLinks : [];
      const cleanCustomLinks: { id: string; title: string; url: string }[] = [];
      for (const item of rawCustomLinks) {
        const title = String(item?.title || '').trim();
        const urlStr = String(item?.url || '').trim();
        if (!title && !urlStr) continue;
        if (!title) return res.status(400).json({ error: 'Every custom link must have a title.' });
        const validatedUrl = validateWebUrl(urlStr, `Custom link "${title}"`);
        if (!validatedUrl) return res.status(400).json({ error: `Custom link "${title}" requires a valid URL.` });
        cleanCustomLinks.push({
          id: item.id && String(item.id).trim() ? String(item.id).trim() : `lnk-${crypto.randomUUID()}`,
          title: title.slice(0, 100),
          url: validatedUrl,
        });
      }

      // Check email uniqueness across other users
      const [dupEmail]: any = await pool.query(
        `SELECT id FROM users WHERE LOWER(email) = LOWER(?) AND id != ? LIMIT 1`,
        [email, session.userId]
      );
      if (dupEmail && dupEmail.length > 0) {
        return res.status(409).json({ error: `Email "${email}" is already used by another account.` });
      }

      const conn = await pool.getConnection();
      try {
        await conn.beginTransaction();

        // A. Update core users table (NEVER touch role, status, is_active, password_hash, salt, roll_number)
        await conn.query(`UPDATE users SET full_name = ?, email = ? WHERE id = ?`, [fullName, email, session.userId]);
        session.fullName = fullName;

        // B. Upsert user_profiles
        if (profilePhoto !== undefined) {
          await conn.query(
            `INSERT INTO user_profiles (
              user_id, profile_photo, phone, designation, location, address, about_me, date_of_birth, gender,
              linkedin_url, github_url, portfolio_url, leetcode_url, hackerrank_url, codechef_url, gfg_url, kaggle_url, google_dev_url
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              profile_photo = VALUES(profile_photo),
              phone = VALUES(phone),
              designation = VALUES(designation),
              location = VALUES(location),
              address = VALUES(address),
              about_me = VALUES(about_me),
              date_of_birth = VALUES(date_of_birth),
              gender = VALUES(gender),
              linkedin_url = VALUES(linkedin_url),
              github_url = VALUES(github_url),
              portfolio_url = VALUES(portfolio_url),
              leetcode_url = VALUES(leetcode_url),
              hackerrank_url = VALUES(hackerrank_url),
              codechef_url = VALUES(codechef_url),
              gfg_url = VALUES(gfg_url),
              kaggle_url = VALUES(kaggle_url),
              google_dev_url = VALUES(google_dev_url)`,
            [
              session.userId,
              profilePhoto || null,
              phone || null,
              designation || null,
              location || null,
              address || null,
              aboutMe || null,
              dob || null,
              gender || null,
              cleanLinks.linkedin || null,
              cleanLinks.github || null,
              cleanLinks.portfolio || null,
              cleanLinks.leetcode || null,
              cleanLinks.hackerrank || null,
              cleanLinks.codechef || null,
              cleanLinks.geeksforgeeks || null,
              cleanLinks.kaggle || null,
              cleanLinks.googleDeveloper || null,
            ]
          );
        } else {
          await conn.query(
            `INSERT INTO user_profiles (
              user_id, phone, designation, location, address, about_me, date_of_birth, gender,
              linkedin_url, github_url, portfolio_url, leetcode_url, hackerrank_url, codechef_url, gfg_url, kaggle_url, google_dev_url
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              phone = VALUES(phone),
              designation = VALUES(designation),
              location = VALUES(location),
              address = VALUES(address),
              about_me = VALUES(about_me),
              date_of_birth = VALUES(date_of_birth),
              gender = VALUES(gender),
              linkedin_url = VALUES(linkedin_url),
              github_url = VALUES(github_url),
              portfolio_url = VALUES(portfolio_url),
              leetcode_url = VALUES(leetcode_url),
              hackerrank_url = VALUES(hackerrank_url),
              codechef_url = VALUES(codechef_url),
              gfg_url = VALUES(gfg_url),
              kaggle_url = VALUES(kaggle_url),
              google_dev_url = VALUES(google_dev_url)`,
            [
              session.userId,
              phone || null,
              designation || null,
              location || null,
              address || null,
              aboutMe || null,
              dob || null,
              gender || null,
              cleanLinks.linkedin || null,
              cleanLinks.github || null,
              cleanLinks.portfolio || null,
              cleanLinks.leetcode || null,
              cleanLinks.hackerrank || null,
              cleanLinks.codechef || null,
              cleanLinks.geeksforgeeks || null,
              cleanLinks.kaggle || null,
              cleanLinks.googleDeveloper || null,
            ]
          );
        }

        // C. Replace user_custom_links
        await conn.query(`DELETE FROM user_custom_links WHERE user_id = ?`, [session.userId]);
        for (let i = 0; i < cleanCustomLinks.length; i++) {
          const lnk = cleanCustomLinks[i];
          await conn.query(
            `INSERT INTO user_custom_links (id, user_id, title, url, display_order) VALUES (?, ?, ?, ?, ?)`,
            [lnk.id, session.userId, lnk.title, lnk.url, i]
          );
        }

        // D. Role-Specific Transactional Updates
        if (session.role === 'STUDENT' && body.student) {
          const stInput = body.student;
          const [stRows]: any = await conn.query(
            `SELECT * FROM students WHERE user_id = ? OR LOWER(roll_number) = LOWER(?) LIMIT 1`,
            [session.userId, session.studentRollNumber || session.username]
          );
          if (!stRows || stRows.length === 0) {
            throw new Error('Linked student record could not be located.');
          }
          const stRecord = stRows[0];

          const cgpa = Number(stInput.cgpa ?? stRecord.cgpa);
          if (isNaN(cgpa) || cgpa < 0 || cgpa > 10) {
            throw new Error('CGPA must be a valid number between 0.00 and 10.00.');
          }
          const backlogs = parseInt(String(stInput.backlogs ?? stRecord.backlogs ?? 0), 10);
          if (isNaN(backlogs) || backlogs < 0 || backlogs > 50) {
            throw new Error('Backlogs must be a non-negative integer.');
          }
          const academicYear = parseInt(String(stInput.academicYear ?? stRecord.academic_year ?? 4), 10);
          const currentSemester = parseInt(String(stInput.currentSemester ?? stRecord.current_semester ?? 7), 10);
          const graduationYear = parseInt(String(stInput.graduationYear ?? stRecord.graduation_year ?? 2026), 10);
          const section = String(stInput.section ?? stRecord.section ?? 'A').trim().slice(0, 10) || 'A';
          const resumeUrl = stInput.resumeUrl ? validateWebUrl(stInput.resumeUrl, 'Resume URL') : '';

          // Protected fields (roll_number, branch, approval_status, placement_status, placed_package_lpa) are NEVER updated here!
          await conn.query(
            `UPDATE students SET
              full_name = ?,
              email = ?,
              phone = ?,
              section = ?,
              academic_year = ?,
              year_of_study = ?,
              current_semester = ?,
              graduation_year = ?,
              cgpa = ?,
              backlogs = ?,
              resume_url = ?,
              dob = ?,
              gender = ?,
              address = ?,
              location = ?,
              about_me = ?,
              technical_skills = ?,
              programming_languages = ?,
              frameworks_technologies = ?,
              soft_skills = ?,
              career_interests = ?,
              preferred_job_roles = ?
            WHERE id = ?`,
            [
              fullName,
              email,
              phone || stRecord.phone,
              section,
              academicYear,
              academicYear,
              currentSemester,
              graduationYear,
              cgpa,
              backlogs,
              resumeUrl || null,
              dob || null,
              gender || null,
              address || null,
              location || null,
              aboutMe || null,
              serializeList(stInput.technicalSkills),
              serializeList(stInput.programmingLanguages),
              serializeList(stInput.frameworksTechnologies),
              serializeList(stInput.softSkills),
              serializeList(stInput.careerInterests),
              serializeList(stInput.preferredJobRoles),
              stRecord.id,
            ]
          );

          // Update Projects
          if (Array.isArray(stInput.projects)) {
            await conn.query(`DELETE FROM student_projects WHERE student_id = ?`, [stRecord.id]);
            for (const p of stInput.projects) {
              const title = String(p?.title || '').trim();
              if (!title) continue;
              const projUrl = p.link ? validateWebUrl(p.link, `Project "${title}" link`) : '';
              await conn.query(
                `INSERT INTO student_projects (id, student_id, user_id, title, description, technologies, project_url, duration)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                  p.id && String(p.id).trim() ? String(p.id).trim() : `prj-${crypto.randomUUID()}`,
                  stRecord.id,
                  session.userId,
                  title.slice(0, 200),
                  String(p.description || '').trim(),
                  serializeList(p.technologies),
                  projUrl || null,
                  String(p.duration || '').trim().slice(0, 80),
                ]
              );
            }
          }

          // Update Internships
          if (Array.isArray(stInput.internships)) {
            await conn.query(`DELETE FROM student_internships WHERE student_id = ?`, [stRecord.id]);
            for (const intr of stInput.internships) {
              const companyName = String(intr?.companyName || '').trim();
              const roleTitle = String(intr?.role || '').trim();
              if (!companyName || !roleTitle) continue;
              await conn.query(
                `INSERT INTO student_internships (id, student_id, user_id, company_name, role, duration, location, description)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                  intr.id && String(intr.id).trim() ? String(intr.id).trim() : `int-${crypto.randomUUID()}`,
                  stRecord.id,
                  session.userId,
                  companyName.slice(0, 150),
                  roleTitle.slice(0, 150),
                  String(intr.duration || '').trim().slice(0, 100),
                  String(intr.location || '').trim().slice(0, 150),
                  String(intr.description || '').trim(),
                ]
              );
            }
          }

          // Update Certifications
          if (Array.isArray(stInput.certifications)) {
            await conn.query(`DELETE FROM user_certifications WHERE user_id = ? OR student_id = ?`, [session.userId, stRecord.id]);
            for (const cert of stInput.certifications) {
              const name = String(cert?.name || '').trim();
              const org = String(cert?.issuingOrg || '').trim();
              if (!name || !org) continue;
              const credUrl = cert.credentialUrl ? validateWebUrl(cert.credentialUrl, `Certification "${name}" URL`) : '';
              await conn.query(
                `INSERT INTO user_certifications (id, user_id, student_id, name, issuing_org, issue_date, credential_id, credential_url)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                  cert.id && String(cert.id).trim() ? String(cert.id).trim() : `crt-${crypto.randomUUID()}`,
                  session.userId,
                  stRecord.id,
                  name.slice(0, 200),
                  org.slice(0, 200),
                  String(cert.issueDate || '').trim().slice(0, 50),
                  String(cert.credentialId || '').trim().slice(0, 120),
                  credUrl || null,
                ]
              );
            }
          }

          // Update Achievements, Awards, Workshops & Hackathons
          if (Array.isArray(stInput.achievements)) {
            await conn.query(`DELETE FROM student_achievements WHERE student_id = ?`, [stRecord.id]);
            for (const ach of stInput.achievements) {
              const title = String(ach?.title || '').trim();
              if (!title) continue;
              const credUrl = ach.credentialUrl ? validateWebUrl(ach.credentialUrl, `Achievement "${title}" URL`) : '';
              const category = ['AWARD', 'WORKSHOP', 'HACKATHON'].includes(String(ach.category || '').toUpperCase())
                ? String(ach.category).toUpperCase()
                : 'AWARD';
              await conn.query(
                `INSERT INTO student_achievements (id, student_id, user_id, title, category, organizer, event_date, description, credential_url)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                  ach.id && String(ach.id).trim() ? String(ach.id).trim() : `ach-${crypto.randomUUID()}`,
                  stRecord.id,
                  session.userId,
                  title.slice(0, 200),
                  category,
                  String(ach.organizer || '').trim().slice(0, 200),
                  String(ach.eventDate || '').trim().slice(0, 50),
                  String(ach.description || '').trim(),
                  credUrl || null,
                ]
              );
            }
          }
        } else if (session.role === 'FACULTY' && body.faculty) {
          const facInput = body.faculty;
          const [facRows]: any = await conn.query(
            `SELECT * FROM faculty WHERE user_id = ? OR LOWER(email) = LOWER(?) LIMIT 1`,
            [session.userId, email]
          );

          // Institutional fields (faculty_id, employee_id, department, faculty_approved, admin_approved, status) are protected
          if (facRows && facRows.length > 0) {
            const fac = facRows[0];
            await conn.query(
              `UPDATE faculty SET
                full_name = ?,
                email = ?,
                phone = ?,
                designation = ?,
                about_me = ?,
                qualifications = ?,
                areas_of_expertise = ?,
                subjects_handled = ?,
                research_interests = ?,
                location = ?
              WHERE id = ?`,
              [
                fullName,
                email,
                phone || fac.phone,
                designation || fac.designation,
                aboutMe || null,
                String(facInput.qualifications || '').trim() || null,
                serializeList(facInput.areasOfExpertise),
                serializeList(facInput.subjectsHandled),
                String(facInput.researchInterests || '').trim() || null,
                location || null,
                fac.id,
              ]
            );
          }

          // Publications
          if (Array.isArray(facInput.publications)) {
            await conn.query(`DELETE FROM faculty_publications WHERE user_id = ?`, [session.userId]);
            const facId = facRows?.[0]?.id || null;
            for (const pub of facInput.publications) {
              const title = String(pub?.title || '').trim();
              if (!title) continue;
              const doiUrl = pub.url ? validateWebUrl(pub.url, `Publication "${title}" URL`) : '';
              await conn.query(
                `INSERT INTO faculty_publications (id, faculty_id, user_id, title, journal_or_conference, publication_year, authors, doi_or_url)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                  pub.id && String(pub.id).trim() ? String(pub.id).trim() : `pub-${crypto.randomUUID()}`,
                  facId,
                  session.userId,
                  title.slice(0, 300),
                  String(pub.journalOrConference || '').trim().slice(0, 200),
                  String(pub.year || '').trim().slice(0, 20),
                  String(pub.authors || '').trim().slice(0, 255),
                  doiUrl || null,
                ]
              );
            }
          }

          // Certifications for Faculty
          if (Array.isArray(facInput.certifications)) {
            await conn.query(`DELETE FROM user_certifications WHERE user_id = ?`, [session.userId]);
            for (const cert of facInput.certifications) {
              const name = String(cert?.name || '').trim();
              const org = String(cert?.issuingOrg || '').trim();
              if (!name || !org) continue;
              const credUrl = cert.credentialUrl ? validateWebUrl(cert.credentialUrl, `Certification "${name}" URL`) : '';
              await conn.query(
                `INSERT INTO user_certifications (id, user_id, student_id, name, issuing_org, issue_date, credential_id, credential_url)
                 VALUES (?, ?, NULL, ?, ?, ?, ?, ?)`,
                [
                  cert.id && String(cert.id).trim() ? String(cert.id).trim() : `crt-${crypto.randomUUID()}`,
                  session.userId,
                  name.slice(0, 200),
                  org.slice(0, 200),
                  String(cert.issueDate || '').trim().slice(0, 50),
                  String(cert.credentialId || '').trim().slice(0, 120),
                  credUrl || null,
                ]
              );
            }
          }
        } else if (session.role === 'COMPANY' && body.company) {
          const compInput = body.company;
          const companyName = String(compInput.name || '').trim();
          if (!companyName) {
            throw new Error('Company Name is required.');
          }
          const website = compInput.website ? validateWebUrl(compInput.website, 'Company Website') : '';
          const linkedinUrl = cleanLinks.linkedin || (compInput.linkedinUrl ? validateWebUrl(compInput.linkedinUrl, 'LinkedIn Company Page') : '');
          const recruiterName = String(compInput.recruiterName || fullName).trim();
          const recruiterDesignation = String(compInput.recruiterDesignation || designation || 'Campus Recruiter').trim();
          const companyLogo = compInput.companyLogo ? validateImageDataUrl(compInput.companyLogo) : undefined;

          const [uRows]: any = await conn.query(`SELECT company_id FROM users WHERE id = ? LIMIT 1`, [session.userId]);
          const compId = uRows?.[0]?.company_id;

          // Strictly match ONLY the recruiter's own company record; NEVER touch approval_status or tier!
          const [compRows]: any = await conn.query(
            `SELECT id FROM companies WHERE id = ? OR user_id = ? LIMIT 1`,
            [compId || '', session.userId]
          );

          if (compRows && compRows.length > 0) {
            const targetCompId = compRows[0].id;
            if (companyLogo !== undefined) {
              await conn.query(
                `UPDATE companies SET
                  name = ?, industry = ?, website = ?, location = ?, description = ?,
                  hr_name = ?, hr_email = ?, hr_phone = ?,
                  recruiter_name = ?, recruiter_designation = ?, linkedin_url = ?, company_logo = ?, user_id = ?
                WHERE id = ?`,
                [
                  companyName,
                  String(compInput.industry || 'IT Services').trim(),
                  website || null,
                  location || null,
                  String(compInput.description || aboutMe || '').trim() || null,
                  recruiterName,
                  email,
                  phone || null,
                  recruiterName,
                  recruiterDesignation,
                  linkedinUrl || null,
                  companyLogo || null,
                  session.userId,
                  targetCompId,
                ]
              );
            } else {
              await conn.query(
                `UPDATE companies SET
                  name = ?, industry = ?, website = ?, location = ?, description = ?,
                  hr_name = ?, hr_email = ?, hr_phone = ?,
                  recruiter_name = ?, recruiter_designation = ?, linkedin_url = ?, user_id = ?
                WHERE id = ?`,
                [
                  companyName,
                  String(compInput.industry || 'IT Services').trim(),
                  website || null,
                  location || null,
                  String(compInput.description || aboutMe || '').trim() || null,
                  recruiterName,
                  email,
                  phone || null,
                  recruiterName,
                  recruiterDesignation,
                  linkedinUrl || null,
                  session.userId,
                  targetCompId,
                ]
              );
            }
            await conn.query(`UPDATE users SET department = ?, company_id = ? WHERE id = ?`, [companyName, targetCompId, session.userId]);
          }
        }

        // Audit Trail
        await conn.query(
          `INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details, created_at)
           VALUES (?, 'PROFILE_UPDATED', ?, ?, ?, ?, NOW())`,
          [
            crypto.randomUUID(),
            session.userId,
            session.username,
            session.role,
            `${session.role} user ${fullName} (@${session.username}) updated their profile in MySQL.`,
          ]
        );

        await conn.commit();
      } catch (txErr) {
        await conn.rollback();
        throw txErr;
      } finally {
        conn.release();
      }

      const updatedProfile = await loadFullProfileByUserId(session.userId);
      return res.json({
        success: true,
        message: 'Profile changes saved permanently to MySQL database.',
        profile: updatedProfile,
      });
    } catch (err: any) {
      console.error('[Profile PUT /me] Error:', err);
      const status = err.message && (err.message.includes('valid') || err.message.includes('required') || err.message.includes('URL') || err.message.includes('CGPA') || err.message.includes('Backlogs') || err.message.includes('Image') || err.message.includes('PNG')) ? 400 : 500;
      return res.status(status).json({ error: err.message || 'Failed to save profile changes.' });
    }
  });
}
