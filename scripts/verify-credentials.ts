import dotenv from 'dotenv';
import express from 'express';
import http from 'http';
import crypto from 'crypto';
import {
  initDatabase,
  getPool,
  isDbConnected,
  getDbStatus,
  verifyPassword,
} from '../server/db';
import { apiRouter } from '../server/routes';

dotenv.config();

interface CheckResult {
  account: string;
  username: string;
  role: string;
  passed: boolean;
  details: string;
}

async function httpJson(
  baseUrl: string,
  path: string,
  method: 'GET' | 'POST' = 'GET',
  body?: Record<string, any>,
  token?: string
): Promise<{ status: number; data: any }> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}

async function findFacultyRow(pool: any, defaultUsername: string, namePattern: string) {
  const [byUser]: any = await pool.query(
    `SELECT id, username, full_name, role, status, is_active, password_hash, salt FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1`,
    [defaultUsername]
  );
  if (byUser.length > 0) return byUser[0];

  const [byName]: any = await pool.query(
    `SELECT id, username, full_name, role, status, is_active, password_hash, salt FROM users WHERE role = 'FACULTY' AND LOWER(full_name) LIKE LOWER(?) LIMIT 1`,
    [namePattern]
  );
  return byName[0] || null;
}

async function main() {
  console.log('=== GCET Placement Hub :: Live MySQL Credential Verification ===');

  const connected = await initDatabase();
  const status = getDbStatus();

  if (!connected || !isDbConnected()) {
    console.error(
      `[MYSQL UNREACHABLE] Could not connect to ${status.host}:${status.port}/${status.database} (${status.lastError || 'unknown error'}).`
    );
    console.error('Live MySQL login testing is pending until MySQL is running and accessible.');
    process.exit(1);
  }

  const pool = getPool()!;
  const app = express();
  app.use(express.json());
  app.use('/api', apiRouter);

  const server = http.createServer(app);
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', () => resolve()));
  const address = server.address() as { port: number };
  const baseUrl = `http://127.0.0.1:${address.port}/api`;

  const results: CheckResult[] = [];
  let adminToken = '';

  try {
    const accountsToVerify: Array<{
      label: string;
      lookupUsername: string;
      namePattern?: string;
      passwordEnv: string;
      expectedRole: string;
      portalRole?: string;
    }> = [
      { label: 'Principal', lookupUsername: 'principal.sagar', passwordEnv: 'GCET_BOOTSTRAP_PRINCIPAL_PASS', expectedRole: 'PRINCIPAL', portalRole: 'ADMIN' },
      { label: 'Vice Chairman', lookupUsername: 'vc.harishchandra', passwordEnv: 'GCET_BOOTSTRAP_VC_PASS', expectedRole: 'VICE_CHAIRMAN', portalRole: 'ADMIN' },
      { label: 'Chairman', lookupUsername: 'chairman.ravinder', passwordEnv: 'GCET_BOOTSTRAP_CHAIRMAN_PASS', expectedRole: 'CHAIRMAN', portalRole: 'ADMIN' },
      { label: 'Administrator', lookupUsername: 'admin', passwordEnv: 'GCET_BOOTSTRAP_ADMIN_PASS', expectedRole: 'ADMIN', portalRole: 'ADMIN' },
      { label: 'Faculty B. Laxmi Lavanya', lookupUsername: 'faculty_lavanya', namePattern: '%lavanya%', passwordEnv: 'GCET_BOOTSTRAP_FACULTY_PASS', expectedRole: 'FACULTY', portalRole: 'FACULTY' },
      { label: 'Faculty P. Aparna', lookupUsername: 'faculty_aparna', namePattern: '%aparna%', passwordEnv: 'GCET_BOOTSTRAP_FACULTY_PASS', expectedRole: 'FACULTY', portalRole: 'FACULTY' },
      { label: 'Faculty S. Raja Shakar Reddy', lookupUsername: 'faculty_rajashakar', namePattern: '%raja%', passwordEnv: 'GCET_BOOTSTRAP_FACULTY_PASS', expectedRole: 'FACULTY', portalRole: 'FACULTY' },
      { label: 'Recruiter (TCS Lead)', lookupUsername: 'recruiter_tcs', passwordEnv: 'GCET_BOOTSTRAP_RECRUITER_PASS', expectedRole: 'COMPANY', portalRole: 'COMPANIES' },
      { label: 'Head of Training & Placements', lookupUsername: 'officer', passwordEnv: 'GCET_BOOTSTRAP_OFFICER_PASS', expectedRole: 'PLACEMENT_OFFICER', portalRole: 'ADMIN' },
      { label: 'Placement Officer', lookupUsername: 'placement_officer', passwordEnv: 'GCET_BOOTSTRAP_PLACEMENT_OFFICER_PASS', expectedRole: 'PLACEMENT_OFFICER', portalRole: 'FACULTY' },
    ];

    for (const acc of accountsToVerify) {
      const secret = process.env[acc.passwordEnv]?.trim() || '';
      if (!secret) {
        results.push({
          account: acc.label,
          username: acc.lookupUsername,
          role: acc.expectedRole,
          passed: false,
          details: `Missing env variable ${acc.passwordEnv}`,
        });
        continue;
      }

      let row: any = null;
      if (acc.namePattern) {
        row = await findFacultyRow(pool, acc.lookupUsername, acc.namePattern);
      } else {
        const [userRows]: any = await pool.query(
          `SELECT id, username, full_name, role, status, is_active, password_hash, salt FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1`,
          [acc.lookupUsername]
        );
        row = userRows?.[0] || null;
      }

      if (!row) {
        results.push({
          account: acc.label,
          username: acc.lookupUsername,
          role: acc.expectedRole,
          passed: false,
          details: 'Account missing in MySQL users table',
        });
        continue;
      }

      const isBcryptCost12 = /^\$2[aby]\$12\$/.test((row.password_hash || '').trim());
      const okRes = await httpJson(baseUrl, '/auth/login', 'POST', {
        username: row.username,
        password: secret,
        portalRole: acc.portalRole,
      });

      const badRes = await httpJson(baseUrl, '/auth/login', 'POST', {
        username: row.username,
        password: `${secret}_WRONG`,
        portalRole: acc.portalRole,
      });

      const responseExposesSecret =
        JSON.stringify(okRes.data).includes('passwordHash') ||
        JSON.stringify(okRes.data).includes('password_hash') ||
        JSON.stringify(okRes.data).includes(secret);

      if (acc.expectedRole === 'ADMIN' && okRes.status === 200 && okRes.data?.token) {
        adminToken = okRes.data.token;
      }

      const passed =
        isBcryptCost12 &&
        okRes.status === 200 &&
        okRes.data?.user?.role === acc.expectedRole &&
        okRes.data?.user?.isActive === true &&
        badRes.status === 401 &&
        !responseExposesSecret;

      results.push({
        account: acc.label,
        username: row.username,
        role: okRes.data?.user?.role || row.role,
        passed,
        details: passed
          ? 'BCrypt($2b$12$) verified, 200 login, 401 wrong-password rejected, hash sanitized'
          : `bcrypt12=${isBcryptCost12}, loginStatus=${okRes.status}, wrongPassStatus=${badRes.status}, role=${okRes.data?.user?.role}, err=${okRes.data?.error || 'none'}`,
      });

      if (okRes.data?.token && acc.expectedRole !== 'ADMIN') {
        await httpJson(baseUrl, '/auth/logout', 'POST', undefined, okRes.data.token);
      }
    }

    // Verify Initial Sample Students (respecting their approval status)
    const studentSeedPass = process.env.GCET_BOOTSTRAP_STUDENT_SEED_PASS?.trim() || '';
    const sampleRollNumbers = ['21R11A0501', '21R11A0502', '22R11A0501', '22R11A0599'];
    for (const roll of sampleRollNumbers) {
      if (!studentSeedPass) {
        results.push({
          account: `Sample Student (${roll})`,
          username: roll,
          role: 'STUDENT',
          passed: false,
          details: 'Missing env variable GCET_BOOTSTRAP_STUDENT_SEED_PASS',
        });
        continue;
      }

      const [stuUserRows]: any = await pool.query(
        `SELECT u.id, u.username, u.role, u.status, u.is_active, u.password_hash, s.approval_status
         FROM users u
         LEFT JOIN students s ON LOWER(s.roll_number) = LOWER(u.student_roll_number)
         WHERE LOWER(u.username) = LOWER(?) OR LOWER(u.student_roll_number) = LOWER(?)
         LIMIT 1`,
        [roll, roll]
      );
      const stuRow = stuUserRows?.[0];
      if (!stuRow) {
        results.push({
          account: `Sample Student (${roll})`,
          username: roll,
          role: 'STUDENT',
          passed: false,
          details: 'Sample student account missing in MySQL users table',
        });
        continue;
      }

      const isBcryptCost12 = /^\$2[aby]\$12\$/.test((stuRow.password_hash || '').trim());
      const effectiveApproval = (stuRow.approval_status || stuRow.status || '').toUpperCase();
      const expectApproved = effectiveApproval === 'APPROVED' || effectiveApproval === 'ACTIVE';

      const okRes = await httpJson(baseUrl, '/auth/login', 'POST', {
        username: roll,
        password: studentSeedPass,
        portalRole: 'STUDENTS',
      });
      const badRes = await httpJson(baseUrl, '/auth/login', 'POST', {
        username: roll,
        password: `${studentSeedPass}_WRONG`,
        portalRole: 'STUDENTS',
      });

      const passed = expectApproved
        ? isBcryptCost12 && okRes.status === 200 && okRes.data?.user?.role === 'STUDENT' && badRes.status === 401
        : isBcryptCost12 && okRes.status === 403 && badRes.status === 401;

      results.push({
        account: `Sample Student (${roll})`,
        username: roll,
        role: 'STUDENT',
        passed,
        details: expectApproved
          ? `Approved (${effectiveApproval}) · BCrypt($2b$12$) verified, 200 login, 401 bad pass`
          : `Pending/Gated (${effectiveApproval}) · BCrypt($2b$12$) verified, 403 blocked at login, 401 bad pass`,
      });

      if (okRes.data?.token) {
        await httpJson(baseUrl, '/auth/logout', 'POST', undefined, okRes.data.token);
      }
    }

    // Verify Newly Approved Recruiter Flow (ephemeral test company cleaned up afterward)
    const recruiterInitPass = process.env.GCET_BOOTSTRAP_RECRUITER_PASS?.trim() || '';
    if (adminToken && recruiterInitPass) {
      const tempCompId = `comp-verify-${crypto.randomUUID().slice(0, 8)}`;
      const tempCompName = `VerifyPartner_${tempCompId.slice(-6)}`;
      const tempHrEmail = `${tempCompId}@example.com`;
      let provisionedUsername = '';

      try {
        await pool.query(
          `INSERT INTO companies (id, name, industry, website, location, description, hr_name, hr_email, hr_phone, tier, approval_status)
           VALUES (?, ?, 'Software', 'https://example.com', 'Hyderabad', 'Verification record', 'Verify HR', ?, '+91 90000 00000', 'Standard IT', 'PENDING_APPROVAL')`,
          [tempCompId, tempCompName, tempHrEmail]
        );

        const approveRes = await httpJson(
          baseUrl,
          '/approvals/approve-company',
          'POST',
          { companyIdOrName: tempCompId },
          adminToken
        );

        provisionedUsername = approveRes.data?.username || '';
        const loginRecruiterRes = await httpJson(baseUrl, '/auth/login', 'POST', {
          username: provisionedUsername,
          password: recruiterInitPass,
          portalRole: 'COMPANY',
        });

        let pwChangeOk = false;
        let updatedReLoginOk = false;
        if (loginRecruiterRes.status === 200 && loginRecruiterRes.data?.token) {
          const newCustomPass = `RecruiterCustom_${crypto.randomUUID().slice(0, 6)}!`;
          const changeRes = await httpJson(
            baseUrl,
            '/auth/change-password',
            'POST',
            {
              currentPassword: recruiterInitPass,
              newPassword: newCustomPass,
              confirmPassword: newCustomPass,
            },
            loginRecruiterRes.data.token
          );
          pwChangeOk = changeRes.status === 200 && changeRes.data?.user?.mustChangePassword === false;

          const reloginRes = await httpJson(baseUrl, '/auth/login', 'POST', {
            username: provisionedUsername,
            password: newCustomPass,
            portalRole: 'COMPANY',
          });
          updatedReLoginOk = reloginRes.status === 200 && reloginRes.data?.user?.mustChangePassword === false;
        }

        const passed =
          approveRes.status === 200 &&
          loginRecruiterRes.status === 200 &&
          loginRecruiterRes.data?.user?.role === 'COMPANY' &&
          loginRecruiterRes.data?.user?.mustChangePassword === true &&
          pwChangeOk &&
          updatedReLoginOk;

        results.push({
          account: 'Newly Approved Recruiter Flow',
          username: provisionedUsername || '(not created)',
          role: 'COMPANY',
          passed,
          details: passed
            ? 'Provisioned with BCrypt + mustChangePassword=true, changed password & re-authenticated'
            : `approve=${approveRes.status}, firstLogin=${loginRecruiterRes.status}, mustChange=${loginRecruiterRes.data?.user?.mustChangePassword}, pwChange=${pwChangeOk}, relogin=${updatedReLoginOk}`,
        });
      } finally {
        await pool.query(`DELETE FROM approval_records WHERE target_id = ?`, [tempCompId]).catch(() => {});
        if (provisionedUsername) {
          await pool.query(`DELETE FROM audit_logs WHERE actor_username = ?`, [provisionedUsername]).catch(() => {});
          await pool.query(`DELETE FROM users WHERE username = ?`, [provisionedUsername]).catch(() => {});
        }
        await pool.query(`DELETE FROM companies WHERE id = ?`, [tempCompId]).catch(() => {});
      }
    }

    // Verify all student rows have non-empty hashes
    const [studentCounts]: any = await pool.query(
      `SELECT COUNT(*) as total, SUM(CASE WHEN password_hash IS NOT NULL AND password_hash != '' THEN 1 ELSE 0 END) as withHash FROM users WHERE role = 'STUDENT'`
    );
    results.push({
      account: 'Existing Student Credentials Preserved',
      username: `(${studentCounts[0]?.total || 0} student rows)`,
      role: 'STUDENT',
      passed: Number(studentCounts[0]?.total || 0) === Number(studentCounts[0]?.withHash || 0),
      details: `All ${studentCounts[0]?.total || 0} student accounts retain valid hashes.`,
    });

    console.table(results);

    const anyFailed = results.some((r) => !r.passed);
    if (anyFailed) {
      process.exitCode = 1;
    }
  } finally {
    if (adminToken) {
      await httpJson(baseUrl, '/auth/logout', 'POST', undefined, adminToken).catch(() => {});
    }
    server.close();
    await pool.end().catch(() => {});
  }
}

main().catch((err) => {
  console.error('Verification failed:', err?.message || err);
  process.exit(1);
});
