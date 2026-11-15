/**
 * GCET PLACEMENT HUB - Client API Service
 * Connects React frontend components to the MySQL-backed Express server.
 */

import { ApplicationStage, Student, Company, PlacementDrive, Skill, Application, User } from '../types';

const rawApiBase = import.meta.env.VITE_API_BASE_URL || '/api';
export const API_BASE = rawApiBase.replace(/\/+$/, '') || '/api';
const TOKEN_KEY = 'gcet_auth_token_v1';

export function getStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setStoredToken(token: string | null): void {
  try {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    // Ignore localStorage access restrictions
  }
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getStoredToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const errorMsg = data.error || `Server request failed with status ${response.status}`;
    const err = new Error(errorMsg) as any;
    err.status = response.status;
    err.data = data;
    throw err;
  }

  return data as T;
}

export interface DbStatusResponse {
  connected: boolean;
  host: string;
  port: number;
  database: string;
  lastCheckTime: string;
  lastSuccessfulConnectionTime?: string;
  lastError: string | null;
  lanIp: string;
  tableCounts?: Record<string, number>;
}

export const api = {
  // Database status & connectivity diagnostics
  async getDbStatus(): Promise<DbStatusResponse> {
    return request<DbStatusResponse>('/db/status');
  },

  async getHealth() {
    return request<any>('/health');
  },

  // Student registration
  async registerStudent(params: {
    fullName: string;
    rollNumber: string;
    email: string;
    phone: string;
    branch: string;
    academicYear?: number;
    cgpa: number;
    tenthPercentage?: number;
    interPercentage?: number;
    graduationYear?: number;
    password: string;
    confirmPassword?: string;
    skills?: string[];
  }) {
    return request<{
      success: boolean;
      message: string;
      studentId: string;
      rollNumber: string;
      status: string;
      assignedFaculty: string;
    }>('/auth/register-student', {
      method: 'POST',
      body: JSON.stringify(params),
    });
  },

  // Student and User Login
  async login(username: string, password: string, portalRole?: string) {
    const res = await request<{
      success: boolean;
      token: string;
      user: any;
      studentRecord?: any;
    }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password, portalRole }),
    });

    if (res.token) {
      setStoredToken(res.token);
    }
    return res;
  },

  // Faculty registration
  async registerFaculty(params: {
    fullName: string;
    employeeId: string;
    email: string;
    department: string;
    designation: string;
    phone?: string;
    password: string;
  }) {
    return request<{
      success: boolean;
      message: string;
      facultyId: string;
      employeeId: string;
      status: string;
    }>('/auth/register-faculty', {
      method: 'POST',
      body: JSON.stringify(params),
    });
  },

  // Company registration
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
    tier?: string;
  }) {
    return request<{
      success: boolean;
      message: string;
      username: string;
      status: string;
    }>('/auth/register-company', {
      method: 'POST',
      body: JSON.stringify(params),
    });
  },

  // Session Me check
  async getMe() {
    return request<{ user: any; studentRecord?: any }>('/auth/me');
  },

  // Logout
  async logout() {
    try {
      await request('/auth/logout', { method: 'POST' });
    } finally {
      setStoredToken(null);
    }
  },

  // Pending students for faculty approval center
  async getPendingStudents() {
    return request<{ students: any[]; authorizedDepts: string[] }>('/approvals/pending-students');
  },

  // Approve student in MySQL
  async approveStudent(studentIdOrRoll: string) {
    return request<{
      success: boolean;
      message: string;
      studentId: string;
      rollNumber: string;
      approvalStatus: string;
      approvedBy: string;
      approvedAt: string;
    }>('/approvals/approve-student', {
      method: 'POST',
      body: JSON.stringify({ studentIdOrRoll }),
    });
  },

  // Batch approve students in MySQL
  async batchApproveStudents(studentIdsOrRolls: string[]) {
    return request<{
      success: boolean;
      message: string;
      count: number;
    }>('/approvals/batch-approve', {
      method: 'POST',
      body: JSON.stringify({ studentIdsOrRolls }),
    });
  },

  // Reject student in MySQL
  async rejectStudent(studentIdOrRoll: string, reason?: string) {
    return request<{
      success: boolean;
      message: string;
      studentId: string;
      rollNumber: string;
    }>('/approvals/reject-student', {
      method: 'POST',
      body: JSON.stringify({ studentIdOrRoll, reason }),
    });
  },

  // Batch reject students in MySQL
  async batchRejectStudents(studentIdsOrRolls: string[], reason?: string) {
    return request<{
      success: boolean;
      message: string;
      count: number;
    }>('/approvals/batch-reject', {
      method: 'POST',
      body: JSON.stringify({ studentIdsOrRolls, reason }),
    });
  },

  // Approval history audit log
  async getApprovalHistory() {
    return request<{ history: any[] }>('/approvals/history');
  },

  // Faculty approvals in MySQL
  async approveFaculty(facultyIdOrUsername: string) {
    return request<{ success: boolean; message: string }>('/approvals/approve-faculty', {
      method: 'POST',
      body: JSON.stringify({ facultyIdOrUsername }),
    });
  },

  async rejectFaculty(facultyIdOrUsername: string, reason?: string) {
    return request<{ success: boolean; message: string }>('/approvals/reject-faculty', {
      method: 'POST',
      body: JSON.stringify({ facultyIdOrUsername, reason }),
    });
  },

  // Company approvals in MySQL
  async approveCompany(companyIdOrName: string) {
    return request<{ success: boolean; message: string }>('/approvals/approve-company', {
      method: 'POST',
      body: JSON.stringify({ companyIdOrName }),
    });
  },

  async rejectCompany(companyIdOrName: string, reason?: string) {
    return request<{ success: boolean; message: string }>('/approvals/reject-company', {
      method: 'POST',
      body: JSON.stringify({ companyIdOrName, reason }),
    });
  },

  // Student Profile
  async getStudentProfile(studentIdOrRoll: string) {
    return request<any>(`/students/${encodeURIComponent(studentIdOrRoll)}/profile`);
  },

  async updateStudentProfile(studentIdOrRoll: string, data: { phone?: string; resumeUrl?: string }) {
    return request<{ success: boolean; message: string }>(`/students/${encodeURIComponent(studentIdOrRoll)}/profile`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  // Unified Multi-Role Profile Management (MySQL Transactional API)
  async getMyProfile() {
    return request<any>('/profile/me');
  },

  async getUserProfile(userIdOrStudentId: string) {
    return request<any>(`/profile/user/${encodeURIComponent(userIdOrStudentId)}`);
  },

  async updateMyProfile(payload: any) {
    return request<{ success: boolean; message: string; profile: any }>('/profile/me', {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },

  async uploadProfilePhoto(imageDataUrl: string, target: 'avatar' | 'company_logo' = 'avatar') {
    return request<{ success: boolean; message: string; imageDataUrl: string }>('/profile/photo', {
      method: 'POST',
      body: JSON.stringify({ imageDataUrl, target }),
    });
  },

  async removeProfilePhoto(target: 'avatar' | 'company_logo' = 'avatar') {
    return request<{ success: boolean; message: string }>(`/profile/photo?target=${encodeURIComponent(target)}`, {
      method: 'DELETE',
    });
  },

  // Database-driven Dashboard Statistics
  async getDashboardStats() {
    return request<{
      totalStudents: number;
      placedCount: number;
      placementPercentage: string;
      avgPackage: string;
      highestPackage: string;
      activeDrives: number;
      approvedCompanies: number;
      mysqlConnected: boolean;
    }>('/dashboard/stats');
  },

  async changePassword(currentPassword: string, newPassword: string, confirmPassword?: string) {
    return request<{ success: boolean; message: string; user: any }>('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
    });
  },

  // Unified Workspace Live Snapshot from MySQL
  async getWorkspaceData() {
    return request<{
      students: Student[];
      companies: Company[];
      drives: PlacementDrive[];
      skills: Skill[];
      applications: Application[];
      users: User[];
    }>('/workspace/data');
  },

  // Students CRUD (MySQL)
  async createStudent(data: any) {
    return request<{ success: boolean; id: string }>('/students', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async updateStudent(id: string, data: any) {
    return request<{ success: boolean; id: string }>(`/students/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  async deleteStudent(id: string) {
    return request<{ success: boolean }>(`/students/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },

  async verifyStudentSkill(studentId: string, skillId: string) {
    return request<{ success: boolean }>(`/students/${encodeURIComponent(studentId)}/skills/verify`, {
      method: 'POST',
      body: JSON.stringify({ skillId }),
    });
  },

  // Companies CRUD (MySQL)
  async createCompany(data: any) {
    return request<{ success: boolean; id: string }>('/companies', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async updateCompany(id: string, data: any) {
    return request<{ success: boolean; id: string }>(`/companies/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  async deleteCompany(id: string) {
    return request<{ success: boolean }>(`/companies/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },

  // Placement Drives CRUD (MySQL)
  async createDrive(data: any) {
    return request<{ success: boolean; id: string }>('/drives', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async updateDrive(id: string, data: any) {
    return request<{ success: boolean; id: string }>(`/drives/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  async deleteDrive(id: string) {
    return request<{ success: boolean }>(`/drives/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },

  // Skills CRUD (MySQL)
  async createSkill(data: any) {
    return request<{ success: boolean; id: string }>('/skills', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async updateSkill(id: string, data: any) {
    return request<{ success: boolean; id: string }>(`/skills/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  async deleteSkill(id: string) {
    return request<{ success: boolean }>(`/skills/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },

  // Applications Workflow (MySQL)
  async createApplication(driveId: string, studentId: string) {
    return request<{ success: boolean; id: string }>('/applications', {
      method: 'POST',
      body: JSON.stringify({ driveId, studentId }),
    });
  },

  async advanceApplicationStage(
    applicationId: string,
    payload: { nextStage: ApplicationStage; notes?: string; finalOfferLpa?: number; rejectionReason?: string }
  ) {
    return request<{ success: boolean }>(`/applications/${encodeURIComponent(applicationId)}/stage`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },

  async withdrawApplication(applicationId: string) {
    return request<{ success: boolean }>(`/applications/${encodeURIComponent(applicationId)}/withdraw`, {
      method: 'POST',
    });
  },

  // User Management & Snapshot Export (MySQL)
  async createUser(data: any) {
    return request<{ success: boolean; id: string }>('/users', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async deleteUser(userId: string) {
    return request<{ success: boolean }>(`/users/${encodeURIComponent(userId)}`, {
      method: 'DELETE',
    });
  },

  async exportDatabase() {
    return request<any>('/export');
  },

  async getFaculty() {
    return request<{ faculty: any[] }>('/faculty');
  },

  async getAdminRequests() {
    return { adminRequests: [] as any[] };
  },

  async decideAdminRequest(_reqId: string, _decision: 'APPROVED' | 'REJECTED', _notes?: string) {
    return { success: true };
  },
};
