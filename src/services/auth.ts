import { User, UserRole, Branch } from '../types';
import { api, getStoredToken, setStoredToken } from './api';

const CURRENT_USER_KEY = 'gcet_current_user_v1';

export const authService = {
  getCurrentUser(): User | null {
    try {
      const token = getStoredToken();
      if (!token) return null;
      const raw = sessionStorage.getItem(CURRENT_USER_KEY) || localStorage.getItem(CURRENT_USER_KEY);
      return raw ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  },

  setCurrentUser(user: User | null): void {
    try {
      if (user) {
        const serialized = JSON.stringify(user);
        sessionStorage.setItem(CURRENT_USER_KEY, serialized);
        localStorage.setItem(CURRENT_USER_KEY, serialized);
      } else {
        sessionStorage.removeItem(CURRENT_USER_KEY);
        localStorage.removeItem(CURRENT_USER_KEY);
      }
    } catch {
      // Ignore storage access errors
    }
  },

  /**
   * Verify the stored bearer token strictly against the backend + MySQL database.
   * Returns null if no token exists or if verification fails (clearing invalid tokens).
   */
  async restoreSession(): Promise<{ user: User; studentRecord?: any } | null> {
    const token = getStoredToken();
    if (!token) return null;

    try {
      const res = await api.getMe();
      if (res?.user) {
        this.setCurrentUser(res.user);
        return { user: res.user, studentRecord: res.studentRecord };
      }
      return null;
    } catch (err: any) {
      if (err?.status === 401 || err?.status === 403) {
        setStoredToken(null);
        this.setCurrentUser(null);
      }
      return null;
    }
  },

  /**
   * Authenticate strictly against MySQL via Express backend API.
   * Never uses localStorage, demo accounts, or hardcoded passwords.
   */
  async login(username: string, plainPassword: string, portalRole?: string): Promise<User> {
    const cleanTerm = username.trim();
    if (!cleanTerm) {
      throw new Error('Username or Registered ID is required.');
    }
    if (!plainPassword) {
      throw new Error('Please enter your password.');
    }

    const apiRes = await api.login(cleanTerm, plainPassword, portalRole);
    if (!apiRes || !apiRes.user) {
      throw new Error('Incorrect username or password. Please try again.');
    }
    this.setCurrentUser(apiRes.user);
    return apiRes.user;
  },

  async logout(): Promise<void> {
    await api.logout().catch(() => {});
    setStoredToken(null);
    this.setCurrentUser(null);
  },

  async changePassword(currentPassword: string, newPassword: string, confirmPassword?: string): Promise<User> {
    if (!currentPassword) {
      throw new Error('Current password is required.');
    }
    if (!newPassword || newPassword.length < 6) {
      throw new Error('New password must be at least 6 characters long.');
    }
    if (confirmPassword !== undefined && newPassword !== confirmPassword) {
      throw new Error('New password and confirmation do not match.');
    }
    if (currentPassword === newPassword) {
      throw new Error('New password must be different from your current password.');
    }
    const res = await api.changePassword(currentPassword, newPassword, confirmPassword);
    return res.user;
  },

  async registerStudent(params: {
    rollNumber: string;
    fullName: string;
    email: string;
    phone: string;
    branch: Branch;
    cgpa: number;
    academicYear?: number;
    tenthPercentage?: number;
    interPercentage?: number;
    graduationYear?: number;
    password: string;
    skills?: string[];
  }) {
    const rollNumber = params.rollNumber.trim().toUpperCase();
    if (!rollNumber) throw new Error('Roll Number is required.');
    if (!params.fullName.trim()) throw new Error('Full Name is required.');
    if (!params.email.trim()) throw new Error('Email address is required.');
    if (!params.password || params.password.length < 6) {
      throw new Error('Password must be at least 6 characters in length.');
    }
    if (isNaN(params.cgpa) || params.cgpa < 0 || params.cgpa > 10) {
      throw new Error('CGPA must be a valid number between 0.00 and 10.00.');
    }

    return api.registerStudent({
      fullName: params.fullName.trim(),
      rollNumber,
      email: params.email.trim().toLowerCase(),
      phone: params.phone.trim() || '+91 98765 00000',
      branch: params.branch,
      academicYear: params.academicYear ?? 4,
      cgpa: Number(params.cgpa.toFixed(2)),
      tenthPercentage: params.tenthPercentage ?? 85.0,
      interPercentage: params.interPercentage ?? 88.0,
      graduationYear: params.graduationYear ?? 2026,
      password: params.password,
      skills: params.skills,
    });
  },

  async registerFaculty(params: {
    fullName: string;
    employeeId: string;
    email: string;
    department: string;
    designation: string;
    phone?: string;
    password: string;
  }) {
    const employeeId = params.employeeId.trim().toUpperCase();
    const fullName = params.fullName.trim();
    const email = params.email.trim().toLowerCase();

    if (!fullName) throw new Error('Full Name is required.');
    if (!employeeId) throw new Error('Faculty Employee ID is required.');
    if (!email) throw new Error('Official college email is required.');
    if (!params.department) throw new Error('Academic department is required.');
    if (!params.designation) throw new Error('Faculty designation is required.');
    if (!params.password || params.password.length < 6) {
      throw new Error('Password must be at least 6 characters in length.');
    }

    return api.registerFaculty({
      fullName,
      employeeId,
      email,
      department: params.department,
      designation: params.designation,
      phone: params.phone,
      password: params.password,
    });
  },

  async registerCompany(params: {
    name: string;
    industry: string;
    website?: string;
    location: string;
    hrName: string;
    hrEmail: string;
    hrPhone?: string;
    password: string;
    description?: string;
    tier?: 'Tier 1 (Dream)' | 'Tier 2 (Super Dream)' | 'Core' | 'Standard IT';
  }) {
    const name = params.name.trim();
    const hrEmail = params.hrEmail.trim().toLowerCase();
    const hrName = params.hrName.trim();

    if (!name) throw new Error('Company name is required.');
    if (!params.industry) throw new Error('Industry domain is required.');
    if (!hrName) throw new Error('HR / Recruiter contact name is required.');
    if (!hrEmail) throw new Error('Corporate email is required.');
    if (!params.location) throw new Error('Company location / headquarters is required.');
    if (!params.password || params.password.length < 6) {
      throw new Error('Password must be at least 6 characters in length.');
    }

    return api.registerCompany({
      name,
      industry: params.industry,
      website: params.website,
      location: params.location,
      hrName,
      hrEmail,
      hrPhone: params.hrPhone,
      password: params.password,
      description: params.description,
      tier: params.tier,
    });
  },

  hasPermission(
    role: UserRole,
    action:
      | 'MANAGE_USERS'
      | 'MANAGE_DRIVES'
      | 'MANAGE_COMPANIES'
      | 'MANAGE_STUDENTS'
      | 'ASSESS_SKILLS'
      | 'APPLY_DRIVE'
      | 'SHORTLIST_CANDIDATES'
      | 'SYSTEM_SETTINGS'
  ): boolean {
    switch (action) {
      case 'MANAGE_USERS':
      case 'SYSTEM_SETTINGS':
        return role === 'ADMIN';
      case 'MANAGE_DRIVES':
      case 'MANAGE_COMPANIES':
      case 'SHORTLIST_CANDIDATES':
        return role === 'ADMIN' || role === 'PLACEMENT_OFFICER';
      case 'MANAGE_STUDENTS':
        return role === 'ADMIN' || role === 'PLACEMENT_OFFICER';
      case 'ASSESS_SKILLS':
        return role === 'ADMIN' || role === 'PLACEMENT_OFFICER' || role === 'FACULTY';
      case 'APPLY_DRIVE':
        return role === 'STUDENT';
      default:
        return false;
    }
  },
};
