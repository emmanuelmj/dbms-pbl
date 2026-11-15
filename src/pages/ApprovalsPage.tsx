import React, { useState, useEffect } from 'react';
import {
  Shield,
  ShieldCheck,
  UserCheck,
  Building2,
  GraduationCap,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Clock,
  Filter,
  Eye,
  FileText,
  Lock,
  CheckSquare,
  Square,
  Users,
  Layers,
  Award,
  AlertCircle,
  Search,
  RefreshCw,
  History,
  Check,
  X,
  BadgeCheck,
} from 'lucide-react';
import {
  User,
  Student,
  Company,
  Skill,
  AdminRequest,
  getFacultyAuthorizedDepts,
  isFacultyAuthorizedForStudent,
  normalizeDeptCode,
} from '../types';
import { api } from '../services/api';

interface FacultyApplicant {
  id: string;
  employeeId: string;
  fullName: string;
  department: string;
  designation: string;
  email: string;
  phone: string;
  status: 'PENDING' | 'ACTIVE' | 'REJECTED';
  approvedByName?: string;
  appliedAt: string;
}

interface StudentApplicant {
  id: string;
  rollNumber: string;
  studentIdCard: string;
  fullName: string;
  branch: string;
  academicYear: number;
  yearOfStudy: number;
  email: string;
  phone: string;
  cgpa: number;
  skills: string[];
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  approvedByName?: string;
  appliedAt: string;
}

interface CompanyApplicant {
  id: string;
  name: string;
  industry: string;
  location: string;
  hrName: string;
  hrEmail: string;
  hrPhone: string;
  website: string;
  tier: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  approvedByName?: string;
  appliedAt: string;
}

interface ApprovalsPageProps {
  currentUser: User | null;
  students: Student[];
  companies: Company[];
  skills: Skill[];
  onRefreshData: () => Promise<void> | void;
  onShowToast?: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

export const ApprovalsPage: React.FC<ApprovalsPageProps> = ({
  currentUser,
  students,
  companies,
  skills,
  onRefreshData,
  onShowToast,
}) => {
  const isPrincipal = currentUser?.role === 'PRINCIPAL' || currentUser?.username === 'principal.sagar';
  const isAdmin = isPrincipal || currentUser?.role === 'CHAIRMAN' || currentUser?.role === 'VICE_CHAIRMAN' || currentUser?.role === 'ADMIN';
  const isFaculty = currentUser?.role === 'FACULTY' || currentUser?.role === 'PLACEMENT_OFFICER';

  // Faculty's authorized departments from institutional assignments
  const facultyUserKey = currentUser?.username || currentUser?.id || '';
  const facultyAuthorizedDepts = isFaculty && !isAdmin ? getFacultyAuthorizedDepts(facultyUserKey) : [];

  // Role-appropriate default tab
  const [activeTab, setActiveTab] = useState<'admin' | 'faculty' | 'students' | 'companies'>(
    isFaculty && !isAdmin ? 'students' : 'faculty'
  );
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('PENDING');
  const [studentDeptFilter, setStudentDeptFilter] = useState<string>('ALL');
  const [studentSearchQuery, setStudentSearchQuery] = useState<string>('');
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  // Multi-selection state for batch student approvals
  const [selectedStudentIds, setSelectedStudentIds] = useState<string[]>([]);

  // Confirmation Modal state for bulk approvals / rejections
  const [confirmModal, setConfirmModal] = useState<{
    isOpen: boolean;
    action: 'APPROVE' | 'REJECT';
    students: StudentApplicant[];
  }>({
    isOpen: false,
    action: 'APPROVE',
    students: [],
  });

  // Approval Audit History modal & live MySQL auxiliary state
  const [showHistoryModal, setShowHistoryModal] = useState<boolean>(false);
  const [adminRequests, setAdminRequests] = useState<AdminRequest[]>([]);
  const [facultyApplicants, setFacultyApplicants] = useState<FacultyApplicant[]>([]);
  const [approvalHistory, setApprovalHistory] = useState<any[]>([]);

  // Map Students from live MySQL prop (Department-scoped for faculty)
  const studentApplicants: StudentApplicant[] = React.useMemo(() => {
    let scoped = students || [];
    if (isFaculty && !isAdmin && facultyUserKey) {
      scoped = scoped.filter((s) => isFacultyAuthorizedForStudent(facultyUserKey, s.branch));
    }

    return scoped.map((s) => {
      const rawApproval = (s.approvalStatus || '').toUpperCase();
      const status: 'PENDING' | 'APPROVED' | 'REJECTED' =
        rawApproval === 'REJECTED'
          ? 'REJECTED'
          : rawApproval === 'APPROVED' || rawApproval === 'ACTIVE'
          ? 'APPROVED'
          : 'PENDING';

      const skillNames = s.skills
        ? s.skills.map((sk) => {
            const found = (skills || []).find((k) => k.id === sk.skillId);
            return found ? found.name : sk.skillId;
          })
        : [];

      return {
        id: s.id,
        rollNumber: s.rollNumber,
        studentIdCard: `GCET-${s.graduationYear || 2026}-${s.rollNumber.slice(-4)}`,
        fullName: s.fullName,
        branch: s.branch,
        academicYear: s.academicYear ?? (s.graduationYear === 2026 ? 4 : 3),
        yearOfStudy: s.academicYear ?? (s.graduationYear === 2026 ? 4 : 3),
        email: s.email,
        phone: s.phone,
        cgpa: s.cgpa,
        skills: skillNames,
        status,
        approvedByName: s.approvedByName,
        appliedAt: s.registeredAt || '2026-11-04T10:00:00Z',
      };
    });
  }, [students, skills, isFaculty, isAdmin, facultyUserKey]);

  // Map Companies from live MySQL prop
  const companyApplicants: CompanyApplicant[] = React.useMemo(() => {
    return (companies || []).map((c) => ({
      id: c.id,
      name: c.name,
      industry: c.industry,
      location: c.location,
      hrName: c.hrName,
      hrEmail: c.hrEmail,
      hrPhone: c.hrPhone,
      website: c.website,
      tier: c.tier,
      status: (c.status === 'PENDING' ? 'PENDING' : c.status === 'REJECTED' ? 'REJECTED' : 'APPROVED') as
        | 'PENDING'
        | 'APPROVED'
        | 'REJECTED',
      approvedByName: c.approvedByName,
      appliedAt: c.createdAt || '2026-11-04T09:45:00Z',
    }));
  }, [companies]);

  // Load faculty list, approval history, and admin requests directly from MySQL backend
  const loadAuxiliaryApprovalsData = React.useCallback(async () => {
    if (!currentUser || (!isAdmin && !isFaculty)) return;
    try {
      const [facRes, histRes, reqRes] = await Promise.all([
        api.getFaculty().catch(() => ({ faculty: [] as any[] })),
        api.getApprovalHistory().catch(() => ({ history: [] as any[] })),
        isAdmin ? api.getAdminRequests().catch(() => ({ adminRequests: [] as AdminRequest[] })) : Promise.resolve({ adminRequests: [] as AdminRequest[] }),
      ]);

      const mappedFaculty: FacultyApplicant[] = (facRes.faculty || []).map((u: any) => ({
        id: u.id,
        employeeId: u.employeeId || u.username,
        fullName: u.fullName,
        department: u.department || 'CSE',
        designation: u.designation || 'Faculty Coordinator',
        email: u.email,
        phone: u.phone || '',
        status: (u.status === 'PENDING' || u.status === 'PENDING_APPROVAL'
          ? 'PENDING'
          : u.status === 'REJECTED'
          ? 'REJECTED'
          : 'ACTIVE') as 'PENDING' | 'ACTIVE' | 'REJECTED',
        approvedByName: u.approvedByName || u.approvedBy,
        appliedAt: u.createdAt || '2026-11-04T08:15:00Z',
      }));

      setFacultyApplicants(mappedFaculty);
      setApprovalHistory(histRes.history || []);
      if (isAdmin) {
        setAdminRequests(reqRes.adminRequests || []);
      }
    } catch (err) {
      console.error('Failed to fetch approvals auxiliary MySQL data:', err);
    }
  }, [currentUser, isAdmin, isFaculty]);

  useEffect(() => {
    loadAuxiliaryApprovalsData();
  }, [loadAuxiliaryApprovalsData]);

  const handleManualRefresh = async () => {
    setIsRefreshing(true);
    try {
      await Promise.all([onRefreshData(), loadAuxiliaryApprovalsData()]);
      onShowToast?.('info', 'Database Refreshed', 'Latest registration records synced from MySQL database.');
    } catch (err: any) {
      onShowToast?.('error', 'Refresh Failed', err?.message || 'Unable to sync records from MySQL.');
    } finally {
      setIsRefreshing(false);
    }
  };

  // Guard: Access restricted for students and companies
  if (!isAdmin && !isFaculty) {
    return (
      <div className="max-w-2xl mx-auto my-12 p-8 bg-white dark:bg-[#101A35] rounded-2xl border border-slate-200 dark:border-slate-800 shadow-xl text-center">
        <div className="w-14 h-14 rounded-full bg-rose-100 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400 flex items-center justify-center mx-auto mb-4">
          <Lock className="w-7 h-7" />
        </div>
        <h2 className="text-xl font-bold text-slate-900 dark:text-white">
          Access Denied: Restricted Institutional Area
        </h2>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 leading-relaxed">
          The Institutional Approval Center is strictly restricted to authorized College Administrators (Principal, Chairman, Vice Chairman) and approved Department Faculty Coordinators.
        </p>
        <div className="mt-4 p-3 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-[11px] text-slate-600 dark:text-slate-400">
          Your current session role is <strong>{currentUser?.role || 'UNAUTHENTICATED'}</strong>.
        </div>
      </div>
    );
  }

  // =========================================================================
  // ACTIONS
  // =========================================================================

  // Principal-only Admin privilege petitions (MySQL persisted)
  const handleDecideAdminRequest = async (reqId: string, approve: boolean) => {
    if (!isPrincipal) {
      onShowToast?.('error', 'Principal Authority Required', 'Only Principal Dr. K. Sagar can approve administrator privilege petitions.');
      return;
    }

    try {
      await api.decideAdminRequest(
        reqId,
        approve ? 'APPROVED' : 'REJECTED',
        approve ? 'Executive approval granted by Principal.' : 'Petition declined by Principal.'
      );
      await Promise.all([loadAuxiliaryApprovalsData(), onRefreshData()]);
      onShowToast?.(
        'success',
        approve ? 'Petition Approved' : 'Petition Rejected',
        `Principal decision saved in MySQL for request ${reqId}.`
      );
    } catch (err: any) {
      onShowToast?.('error', 'Decision Failed', err?.message || 'Unable to record Principal decision in MySQL.');
    }
  };

  // Faculty Approvals (ADMIN ONLY)
  const handleApproveFacultyAdmin = async (applicantId: string) => {
    if (!isAdmin) {
      onShowToast?.('error', 'Unauthorized', 'College Administrative clearance requires administrator credentials.');
      return;
    }

    try {
      const target = facultyApplicants.find((f) => f.id === applicantId);
      await api.approveFaculty(target?.id || target?.employeeId || target?.email || applicantId);
      await Promise.all([loadAuxiliaryApprovalsData(), onRefreshData()]);
      onShowToast?.('success', 'Faculty Approved', 'Faculty member account cleared in MySQL by College Administration.');
    } catch (err: any) {
      onShowToast?.('error', 'Approval Error', err?.message || 'Unable to approve faculty in MySQL.');
    }
  };

  const handleRejectFacultyAdmin = async (applicantId: string) => {
    if (!isAdmin) {
      onShowToast?.('error', 'Unauthorized', 'Only administrators can reject faculty accounts.');
      return;
    }

    try {
      const target = facultyApplicants.find((f) => f.id === applicantId);
      await api.rejectFaculty(target?.id || target?.employeeId || target?.email || applicantId);
      await Promise.all([loadAuxiliaryApprovalsData(), onRefreshData()]);
      onShowToast?.('info', 'Faculty Registration Rejected', 'Faculty registration declined in MySQL by College Administration.');
    } catch (err: any) {
      onShowToast?.('error', 'Rejection Error', err?.message || 'Unable to reject faculty in MySQL.');
    }
  };

  // Student Approvals (FACULTY ONLY - ADMINS STRICTLY FORBIDDEN)
  const handleApproveStudent = async (applicantIdOrRoll: string) => {
    if (isAdmin) {
      onShowToast?.(
        'error',
        'Strict Institutional Rule',
        'Administrators must NOT approve student registrations under this workflow. Only authorized Department Faculty Coordinators may approve student accounts.'
      );
      return;
    }

    try {
      const apiResult = await api.approveStudent(applicantIdOrRoll);
      setSelectedStudentIds((prev) => prev.filter((id) => id !== applicantIdOrRoll));
      await Promise.all([onRefreshData(), loadAuxiliaryApprovalsData()]);

      onShowToast?.(
        'success',
        'Student Approved & Active',
        apiResult?.message || `Student registration verified and approved in MySQL. Candidate can now sign in immediately.`
      );
    } catch (err: any) {
      onShowToast?.('error', 'Approval Failed', err?.message || 'Unable to approve student in MySQL.');
    }
  };

  const handleRejectStudent = async (applicantIdOrRoll: string) => {
    if (isAdmin) {
      onShowToast?.(
        'error',
        'Strict Institutional Rule',
        'Administrators must NOT reject student registrations under this workflow.'
      );
      return;
    }

    try {
      await api.rejectStudent(applicantIdOrRoll, 'Declined by Department Faculty Coordinator');
      setSelectedStudentIds((prev) => prev.filter((id) => id !== applicantIdOrRoll));
      await Promise.all([onRefreshData(), loadAuxiliaryApprovalsData()]);

      onShowToast?.('info', 'Registration Rejected', `Student registration marked as rejected in MySQL by Faculty Coordinator.`);
    } catch (err: any) {
      onShowToast?.('error', 'Rejection Error', err?.message || 'Unable to reject student in MySQL.');
    }
  };

  // Batch Approval Handlers (Faculty Only) with Confirmation Dialog
  const handleBatchApproveStudents = () => {
    if (isAdmin) {
      onShowToast?.('error', 'Strict Institutional Rule', 'Administrators must NOT approve student registrations.');
      return;
    }
    if (selectedStudentIds.length === 0) return;

    const selectedList = studentApplicants.filter((s) => selectedStudentIds.includes(s.id));
    setConfirmModal({
      isOpen: true,
      action: 'APPROVE',
      students: selectedList,
    });
  };

  const handleBatchRejectStudents = () => {
    if (isAdmin) {
      onShowToast?.('error', 'Strict Institutional Rule', 'Administrators must NOT reject student registrations.');
      return;
    }
    if (selectedStudentIds.length === 0) return;

    const selectedList = studentApplicants.filter((s) => selectedStudentIds.includes(s.id));
    setConfirmModal({
      isOpen: true,
      action: 'REJECT',
      students: selectedList,
    });
  };

  const executeConfirmedBatchAction = async () => {
    if (confirmModal.students.length === 0) return;
    const ids = confirmModal.students.map((s) => s.id);

    if (confirmModal.action === 'APPROVE') {
      try {
        const res = await api.batchApproveStudents(ids);
        setSelectedStudentIds([]);
        setConfirmModal({ isOpen: false, action: 'APPROVE', students: [] });
        await Promise.all([onRefreshData(), loadAuxiliaryApprovalsData()]);
        onShowToast?.(
          'success',
          'Batch Approval Complete',
          res?.message || `Successfully approved ${res?.count ?? ids.length} candidate(s) in MySQL! Their accounts are now ACTIVE.`
        );
      } catch (err: any) {
        onShowToast?.('error', 'Batch Approval Error', err?.message || 'Unable to process batch approval in MySQL.');
      }
    } else {
      try {
        const res = await api.batchRejectStudents(ids, 'Batch rejected by Faculty Coordinator');
        setSelectedStudentIds([]);
        setConfirmModal({ isOpen: false, action: 'REJECT', students: [] });
        await Promise.all([onRefreshData(), loadAuxiliaryApprovalsData()]);
        onShowToast?.('info', 'Batch Rejection', res?.message || `Rejected ${res?.count ?? ids.length} student registration(s) in MySQL.`);
      } catch (err: any) {
        onShowToast?.('error', 'Batch Rejection Error', err?.message || 'Unable to process batch rejection in MySQL.');
      }
    }
  };

  const toggleSelectStudent = (id: string) => {
    setSelectedStudentIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  // Filtered Students list applying status, department, and search filters
  const filteredStudents = studentApplicants.filter((stu) => {
    // 1. Status Filter
    if (statusFilter !== 'ALL' && stu.status !== statusFilter) return false;

    // 2. Department Filter
    if (studentDeptFilter !== 'ALL') {
      if (normalizeDeptCode(stu.branch) !== normalizeDeptCode(studentDeptFilter)) {
        return false;
      }
    }

    // 3. Search Query (Roll Number or Name or Email)
    if (studentSearchQuery.trim()) {
      const q = studentSearchQuery.trim().toLowerCase();
      const matchRoll = stu.rollNumber.toLowerCase().includes(q);
      const matchName = stu.fullName.toLowerCase().includes(q);
      const matchEmail = stu.email.toLowerCase().includes(q);
      if (!matchRoll && !matchName && !matchEmail) return false;
    }

    return true;
  });

  const pendingStudents = filteredStudents.filter((s) => s.status === 'PENDING');
  const allPendingSelected =
    pendingStudents.length > 0 &&
    pendingStudents.every((s) => selectedStudentIds.includes(s.id));

  const toggleSelectAllPending = () => {
    if (allPendingSelected) {
      setSelectedStudentIds([]);
    } else {
      setSelectedStudentIds(pendingStudents.map((s) => s.id));
    }
  };

  // Company Approvals (ADMIN ONLY)
  const handleApproveCompany = async (applicantId: string) => {
    if (!isAdmin) {
      onShowToast?.('error', 'Administrator Clearance Required', 'Corporate recruiter approvals must be confirmed by an Administrator.');
      return;
    }

    try {
      const target = companyApplicants.find((c) => c.id === applicantId);
      await api.approveCompany(target?.id || target?.name || applicantId);
      await Promise.all([onRefreshData(), loadAuxiliaryApprovalsData()]);
      onShowToast?.('success', 'Corporate Recruiter Cleared', 'Hiring organization approved in MySQL for campus placement drives.');
    } catch (err: any) {
      onShowToast?.('error', 'Company Approval Error', err?.message || 'Unable to approve company in MySQL.');
    }
  };

  const handleRejectCompany = async (applicantId: string) => {
    if (!isAdmin) {
      onShowToast?.('error', 'Administrator Required', 'Only administrators can reject corporate partners.');
      return;
    }

    try {
      const target = companyApplicants.find((c) => c.id === applicantId);
      await api.rejectCompany(target?.id || target?.name || applicantId);
      await Promise.all([onRefreshData(), loadAuxiliaryApprovalsData()]);
      onShowToast?.('info', 'Registration Rejected', 'Corporate registration rejected in MySQL.');
    } catch (err: any) {
      onShowToast?.('error', 'Company Rejection Error', err?.message || 'Unable to reject company in MySQL.');
    }
  };

  // KPI Calculations
  const pendingAdminCount = adminRequests.filter((r) => r.status === 'PENDING').length;
  const pendingFacultyCount = facultyApplicants.filter((f) => f.status === 'PENDING').length;
  const pendingStudentCount = studentApplicants.filter((s) => s.status === 'PENDING').length;
  const pendingCompanyCount = companyApplicants.filter((c) => c.status === 'PENDING').length;

  return (
    <div className="space-y-6">
      {/* Top Title & Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white dark:bg-[#101A35] p-5 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm">
        <div>
          <div className="flex items-center space-x-2">
            <ShieldCheck className="w-6 h-6 text-cyan-600 dark:text-cyan-400" />
            <h1 className="text-xl font-bold text-[#101a35] dark:text-white">
              {isFaculty && !isAdmin ? 'Faculty Approval Center (Student Registrations)' : 'Admin Approval Center & Institutional Governance'}
            </h1>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            {isFaculty && !isAdmin
              ? 'Department Faculty Coordinators verify student registrations, endorse skill matrices, and unblock candidate logins.'
              : 'Institutional Administrative Gateway: Faculty clearance, corporate partner approvals, and Principal governance.'}
          </p>
        </div>

        <div className="flex items-center space-x-2 text-xs flex-wrap">
          <span className="px-3 py-1 rounded-full bg-cyan-50 dark:bg-cyan-950/60 text-cyan-700 dark:text-cyan-300 font-semibold border border-cyan-200 dark:border-cyan-900">
            Active User: <strong>{currentUser?.fullName}</strong> ({currentUser?.role})
          </span>
          {isPrincipal && (
            <span className="px-3 py-1 rounded-full bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 font-bold border border-emerald-300 dark:border-emerald-800 flex items-center space-x-1">
              <span>👑 Principal Authority Active</span>
            </span>
          )}
        </div>
      </div>

      {/* KPI Cards Row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {/* If Faculty: only relevant KPI */}
        {isFaculty && !isAdmin ? (
          <>
            <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs">
              <div className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Pending Student Requests
              </div>
              <div className="text-2xl font-black text-cyan-600 dark:text-cyan-400 mt-1">{pendingStudentCount}</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Awaiting Faculty Verification</div>
            </div>

            <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs">
              <div className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Approved Candidates
              </div>
              <div className="text-2xl font-black text-emerald-600 mt-1">
                {studentApplicants.filter((s) => s.status === 'APPROVED').length}
              </div>
              <div className="text-[10px] text-slate-400 mt-0.5">Active & Login Enabled</div>
            </div>

            <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs">
              <div className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Total Registrations
              </div>
              <div className="text-2xl font-black text-[#101a35] dark:text-white mt-1">
                {studentApplicants.length}
              </div>
              <div className="text-[10px] text-slate-400 mt-0.5">Department Database</div>
            </div>

            <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs">
              <div className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Selected for Batch
              </div>
              <div className="text-2xl font-black text-indigo-600 mt-1">
                {selectedStudentIds.length}
              </div>
              <div className="text-[10px] text-slate-400 mt-0.5">Ready for Bulk Action</div>
            </div>
          </>
        ) : (
          /* If Admin: show full governance KPIs */
          <>
            <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs">
              <div className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Pending Faculty
              </div>
              <div className="text-2xl font-black text-emerald-600 mt-1">{pendingFacultyCount}</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Admin approval required</div>
            </div>

            <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs">
              <div className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Pending Companies
              </div>
              <div className="text-2xl font-black text-purple-600 mt-1">{pendingCompanyCount}</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Recruiter partner clearance</div>
            </div>

            <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs">
              <div className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Principal Petitions
              </div>
              <div className="text-2xl font-black text-rose-600 mt-1">{pendingAdminCount}</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Principal Dr. K. Sagar</div>
            </div>

            <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs">
              <div className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Student Queue (Faculty)
              </div>
              <div className="text-2xl font-black text-cyan-600 mt-1">{pendingStudentCount}</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Faculty-managed only</div>
            </div>
          </>
        )}
      </div>

      {/* Main Approval Dashboard Container */}
      <div className="bg-white dark:bg-[#101A35] rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm overflow-hidden">
        {/* Tab Headers */}
        <div className="flex border-b border-slate-200 dark:border-slate-800 px-4 pt-3 gap-2 overflow-x-auto bg-slate-50/80 dark:bg-slate-900/50">
          {/* TAB: STUDENTS (Visible to both, but actions are strictly Faculty-only) */}
          <button
            onClick={() => setActiveTab('students')}
            className={`px-4 py-2.5 text-xs font-bold rounded-t-xl transition flex items-center space-x-2 border-b-2 cursor-pointer ${
              activeTab === 'students'
                ? 'border-cyan-500 text-cyan-600 dark:text-cyan-400 bg-white dark:bg-[#101A35]'
                : 'border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            <GraduationCap className="w-4 h-4" />
            <span>Student Registrations {isFaculty && !isAdmin ? '(Faculty Approval Center)' : ''}</span>
            {pendingStudentCount > 0 && (
              <span className="px-1.5 py-0.5 rounded-full bg-cyan-600 text-white text-[10px]">
                {pendingStudentCount}
              </span>
            )}
          </button>

          {/* TABS FOR ADMINS ONLY */}
          {isAdmin && (
            <>
              <button
                onClick={() => setActiveTab('faculty')}
                className={`px-4 py-2.5 text-xs font-bold rounded-t-xl transition flex items-center space-x-2 border-b-2 cursor-pointer ${
                  activeTab === 'faculty'
                    ? 'border-emerald-500 text-emerald-600 dark:text-emerald-400 bg-white dark:bg-[#101A35]'
                    : 'border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                <UserCheck className="w-4 h-4" />
                <span>Faculty Registrations (Admin Only)</span>
                {pendingFacultyCount > 0 && (
                  <span className="px-1.5 py-0.5 rounded-full bg-emerald-600 text-white text-[10px]">
                    {pendingFacultyCount}
                  </span>
                )}
              </button>

              <button
                onClick={() => setActiveTab('companies')}
                className={`px-4 py-2.5 text-xs font-bold rounded-t-xl transition flex items-center space-x-2 border-b-2 cursor-pointer ${
                  activeTab === 'companies'
                    ? 'border-purple-500 text-purple-600 dark:text-purple-400 bg-white dark:bg-[#101A35]'
                    : 'border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                <Building2 className="w-4 h-4" />
                <span>Company Registrations (Admin Only)</span>
                {pendingCompanyCount > 0 && (
                  <span className="px-1.5 py-0.5 rounded-full bg-purple-600 text-white text-[10px]">
                    {pendingCompanyCount}
                  </span>
                )}
              </button>

              <button
                onClick={() => setActiveTab('admin')}
                className={`px-4 py-2.5 text-xs font-bold rounded-t-xl transition flex items-center space-x-2 border-b-2 cursor-pointer ${
                  activeTab === 'admin'
                    ? 'border-rose-500 text-rose-600 dark:text-rose-400 bg-white dark:bg-[#101A35]'
                    : 'border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                <Shield className="w-4 h-4" />
                <span>Admin Petitions (Principal Only)</span>
                {pendingAdminCount > 0 && (
                  <span className="px-1.5 py-0.5 rounded-full bg-rose-600 text-white text-[10px]">
                    {pendingAdminCount}
                  </span>
                )}
              </button>
            </>
          )}
        </div>

        {/* Filter bar */}
        <div className="p-3 bg-slate-50/50 dark:bg-slate-900/30 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs">
          <div className="flex items-center space-x-2">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <span className="font-semibold text-slate-600 dark:text-slate-300">Filter Status:</span>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as any)}
              className="px-2.5 py-1 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#101A35] text-slate-800 dark:text-slate-200 font-medium"
            >
              <option value="PENDING">Pending Only</option>
              <option value="APPROVED">Approved / Active Only</option>
              <option value="REJECTED">Rejected Only</option>
              <option value="ALL">Show All Records</option>
            </select>
          </div>
        </div>

        {/* ================================================================= */}
        {/* TAB: STUDENT REGISTRATIONS (FACULTY APPROVAL CENTER) */}
        {/* ================================================================= */}
        {activeTab === 'students' && (
          <div className="p-5 space-y-4">
            {/* Faculty Department Assignment Banner */}
            {isFaculty && !isAdmin ? (
              <div className="p-4 rounded-xl bg-gradient-to-r from-cyan-900/20 via-blue-900/10 to-slate-900/20 border border-cyan-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                <div className="space-y-1">
                  <div className="flex items-center space-x-2">
                    <BadgeCheck className="w-5 h-5 text-cyan-500" />
                    <span className="font-extrabold text-slate-900 dark:text-white text-sm">
                      {currentUser?.fullName || 'Faculty Coordinator'} — Department Approval Console
                    </span>
                  </div>
                  <p className="text-slate-600 dark:text-slate-300 text-[11px]">
                    You have exclusive institutional authority to review, approve, and activate student registrations for your assigned departments.
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-1.5 shrink-0">
                  <span className="text-[10px] uppercase font-bold text-slate-500 dark:text-slate-400 mr-1">
                    Authorized Departments:
                  </span>
                  {facultyAuthorizedDepts.length > 0 ? (
                    facultyAuthorizedDepts.map((d) => (
                      <span
                        key={d}
                        className="px-2.5 py-1 rounded-md bg-cyan-100 text-cyan-800 dark:bg-cyan-950/80 dark:text-cyan-300 dark:border dark:border-cyan-800 font-bold text-xs"
                      >
                        {d}
                      </span>
                    ))
                  ) : (
                    <span className="px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold text-xs">
                      All Assigned
                    </span>
                  )}
                </div>
              </div>
            ) : (
              <div className="p-3.5 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-800 text-xs text-amber-900 dark:text-amber-200 flex items-start gap-2.5">
                <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                <div>
                  <strong className="block font-bold">Strict Institutional Rule:</strong>
                  Student registration verification is handled exclusively by <strong>Department Faculty Coordinators</strong> in their Faculty Approval Center. Administrators must NOT approve or reject student registrations under this workflow. Actions are disabled for administrative accounts.
                </div>
              </div>
            )}

            {/* Comprehensive Toolbar: Search, Department Filter, Status Filter, Refresh & Audit History */}
            <div className="p-3 bg-slate-50 dark:bg-slate-900/60 rounded-xl border border-slate-200 dark:border-slate-800 flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 text-xs">
              {/* Search Bar */}
              <div className="relative flex-1 min-w-[200px]">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Search by Student Roll Number or Name..."
                  value={studentSearchQuery}
                  onChange={(e) => setStudentSearchQuery(e.target.value)}
                  className="w-full pl-9 pr-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#101A35] text-slate-900 dark:text-white placeholder:text-slate-400 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                />
              </div>

              {/* Department Filter (Scoped to Faculty's Authorized Departments) */}
              <div className="flex items-center space-x-2">
                <span className="font-semibold text-slate-600 dark:text-slate-300 shrink-0">Dept:</span>
                <select
                  value={studentDeptFilter}
                  onChange={(e) => setStudentDeptFilter(e.target.value)}
                  className="px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#101A35] text-slate-800 dark:text-slate-200 font-medium text-xs"
                >
                  <option value="ALL">All Authorized Departments</option>
                  {(facultyAuthorizedDepts.length > 0
                    ? facultyAuthorizedDepts
                    : ['CSE', 'CSE-AIML', 'CSE-DS', 'CSE-CS', 'IT', 'EEE', 'ECE', 'MECH', 'CIVIL']
                  ).map((dept) => (
                    <option key={dept} value={dept}>
                      {dept}
                    </option>
                  ))}
                </select>
              </div>

              {/* Action Buttons: Refresh & Audit History */}
              <div className="flex items-center space-x-2 shrink-0">
                <button
                  type="button"
                  onClick={handleManualRefresh}
                  disabled={isRefreshing}
                  className="px-3 py-1.5 rounded-lg bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-700 font-semibold text-slate-700 dark:text-slate-200 flex items-center space-x-1.5 transition cursor-pointer disabled:opacity-50"
                  title="Reload registration queue from database"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-cyan-500' : ''}`} />
                  <span>Refresh</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    loadAuxiliaryApprovalsData();
                    setShowHistoryModal(true);
                  }}
                  className="px-3 py-1.5 rounded-lg bg-cyan-50 text-cyan-700 dark:bg-cyan-950/60 dark:text-cyan-300 border border-cyan-300 dark:border-cyan-800 font-semibold flex items-center space-x-1.5 hover:bg-cyan-100 transition cursor-pointer"
                  title="View complete audit log of approvals from MySQL"
                >
                  <History className="w-3.5 h-3.5" />
                  <span>Audit History</span>
                </button>
              </div>
            </div>

            {/* Batch Action Strip for Faculty */}
            {!isAdmin && isFaculty && (
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3 bg-slate-50 dark:bg-slate-900/50 rounded-xl border border-slate-200 dark:border-slate-800">
                <div className="flex items-center space-x-2 text-xs">
                  <button
                    type="button"
                    onClick={toggleSelectAllPending}
                    className="flex items-center space-x-1.5 font-bold text-cyan-600 dark:text-cyan-400 hover:underline cursor-pointer"
                  >
                    {allPendingSelected ? <CheckSquare className="w-4 h-4" /> : <Square className="w-4 h-4" />}
                    <span>{allPendingSelected ? 'Deselect All Pending' : 'Select All Pending'}</span>
                  </button>
                  <span className="text-slate-400">|</span>
                  <span className="text-slate-600 dark:text-slate-300 font-semibold">
                    {selectedStudentIds.length} candidate(s) selected
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={selectedStudentIds.length === 0}
                    onClick={handleBatchApproveStudents}
                    className="px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs flex items-center space-x-1.5 disabled:opacity-40 transition cursor-pointer shadow-sm"
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>Approve Selected ({selectedStudentIds.length})</span>
                  </button>
                  <button
                    type="button"
                    disabled={selectedStudentIds.length === 0}
                    onClick={handleBatchRejectStudents}
                    className="px-3 py-1.5 rounded-lg bg-rose-50 text-rose-700 hover:bg-rose-100 dark:bg-rose-950/40 dark:text-rose-300 border border-rose-200 dark:border-rose-900 font-bold text-xs flex items-center space-x-1 disabled:opacity-40 transition cursor-pointer"
                  >
                    <XCircle className="w-3.5 h-3.5" />
                    <span>Reject Selected</span>
                  </button>
                </div>
              </div>
            )}

            {/* Student Table */}
            <div className="border border-slate-200 dark:border-slate-800 rounded-xl overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 dark:bg-slate-900 text-slate-500 uppercase tracking-wider font-bold text-[10px]">
                  <tr>
                    {!isAdmin && <th className="p-3 w-10 text-center">Sel</th>}
                    <th className="p-3">Student Name</th>
                    <th className="p-3">Roll Number</th>
                    <th className="p-3">Department / Branch</th>
                    <th className="p-3">Academic Year</th>
                    <th className="p-3">CGPA</th>
                    <th className="p-3">Registration Date</th>
                    <th className="p-3">Approval Status</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {filteredStudents.length === 0 ? (
                    <tr>
                      <td colSpan={isAdmin ? 8 : 9} className="p-8 text-center text-slate-500 dark:text-slate-400">
                        <Users className="w-8 h-8 mx-auto mb-2 text-slate-400 opacity-60" />
                        <p className="font-semibold text-sm">No student registrations found</p>
                        <p className="text-xs text-slate-400 mt-0.5">
                          {studentSearchQuery
                            ? `No students match your search "${studentSearchQuery}"`
                            : 'All pending student accounts for your assigned departments have been reviewed.'}
                        </p>
                      </td>
                    </tr>
                  ) : (
                    filteredStudents.map((stu) => {
                      const isPending = stu.status === 'PENDING';
                      const isSelected = selectedStudentIds.includes(stu.id);

                      return (
                        <tr key={stu.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30 transition">
                          {!isAdmin && (
                            <td className="p-3 text-center">
                              {isPending ? (
                                <button
                                  type="button"
                                  onClick={() => toggleSelectStudent(stu.id)}
                                  className="text-slate-400 hover:text-cyan-600 cursor-pointer"
                                >
                                  {isSelected ? (
                                    <CheckSquare className="w-4 h-4 text-cyan-600" />
                                  ) : (
                                    <Square className="w-4 h-4" />
                                  )}
                                </button>
                              ) : (
                                <span className="text-slate-300">-</span>
                              )}
                            </td>
                          )}
                          <td className="p-3">
                            <span className="font-bold text-slate-900 dark:text-white block">{stu.fullName}</span>
                            <span className="text-[10px] text-slate-400">{stu.email}</span>
                          </td>
                          <td className="p-3 font-mono font-bold text-cyan-700 dark:text-cyan-400">
                            {stu.rollNumber}
                          </td>
                          <td className="p-3">
                            <span className="px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 font-semibold text-[11px]">
                              {stu.branch}
                            </span>
                          </td>
                          <td className="p-3 font-semibold">
                            Year {stu.academicYear} (Grad: {stu.yearOfStudy ? 2022 + stu.yearOfStudy : 2026})
                          </td>
                          <td className="p-3 font-bold text-slate-800 dark:text-slate-200">
                            {stu.cgpa.toFixed(2)}
                          </td>
                          <td className="p-3 text-slate-500 text-[11px]">
                            {new Date(stu.appliedAt).toLocaleDateString()}
                          </td>
                          <td className="p-3">
                            {stu.status === 'PENDING' ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-900">
                                PENDING_APPROVAL
                              </span>
                            ) : stu.status === 'APPROVED' ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-900 flex items-center space-x-1 w-fit">
                                <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                                <span>APPROVED ({stu.approvedByName || 'Faculty'})</span>
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-900">
                                REJECTED
                              </span>
                            )}
                          </td>
                          <td className="p-3 text-right">
                            {isAdmin ? (
                              <span className="text-[10px] text-slate-400 italic">Faculty Action Only</span>
                            ) : isPending ? (
                              <div className="inline-flex items-center gap-1.5">
                                <button
                                  type="button"
                                  onClick={() => handleApproveStudent(stu.id)}
                                  className="px-2.5 py-1 rounded-md bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-[11px] transition cursor-pointer shadow-xs"
                                >
                                  Approve
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleRejectStudent(stu.id)}
                                  className="px-2 py-1 rounded-md bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-[11px] transition cursor-pointer"
                                >
                                  Reject
                                </button>
                              </div>
                            ) : (
                              <span className="text-[10px] text-emerald-600 dark:text-emerald-400 font-semibold">Active & Logged In</span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ================================================================= */}
        {/* TAB: FACULTY REGISTRATIONS (ADMIN ONLY) */}
        {/* ================================================================= */}
        {activeTab === 'faculty' && isAdmin && (
          <div className="p-5 space-y-4">
            <div className="p-3.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-300 dark:border-emerald-800 text-xs text-emerald-900 dark:text-emerald-200 flex items-start gap-2.5">
              <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
              <div>
                <strong className="block font-bold">Admin Authority Required:</strong>
                Faculty coordinator registrations route exclusively to the College Administration. Only authorized administrators (Principal, Chairman, Vice Chairman) may approve or reject new faculty accounts.
              </div>
            </div>

            <div className="border border-slate-200 dark:border-slate-800 rounded-xl overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 dark:bg-slate-900 text-slate-500 uppercase tracking-wider font-bold text-[10px]">
                  <tr>
                    <th className="p-3">Faculty Name</th>
                    <th className="p-3">Employee ID</th>
                    <th className="p-3">Official Email</th>
                    <th className="p-3">Department</th>
                    <th className="p-3">Designation</th>
                    <th className="p-3">Registration Date</th>
                    <th className="p-3">Status</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {facultyApplicants
                    .filter((f) => (statusFilter === 'ALL' ? true : f.status === (statusFilter === 'APPROVED' ? 'ACTIVE' : statusFilter)))
                    .map((fac) => {
                      const isPending = fac.status === 'PENDING';

                      return (
                        <tr key={fac.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30 transition">
                          <td className="p-3 font-bold text-slate-900 dark:text-white">
                            {fac.fullName}
                          </td>
                          <td className="p-3 font-mono font-bold text-emerald-700 dark:text-emerald-400">
                            {fac.employeeId}
                          </td>
                          <td className="p-3 text-slate-600 dark:text-slate-300">
                            {fac.email}
                          </td>
                          <td className="p-3 font-semibold">
                            {fac.department}
                          </td>
                          <td className="p-3 text-slate-600 dark:text-slate-400">
                            {fac.designation}
                          </td>
                          <td className="p-3 text-slate-500 text-[11px]">
                            {new Date(fac.appliedAt).toLocaleDateString()}
                          </td>
                          <td className="p-3">
                            {fac.status === 'PENDING' ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-900">
                                PENDING_APPROVAL
                              </span>
                            ) : fac.status === 'ACTIVE' ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-900">
                                APPROVED ({fac.approvedByName || 'Admin'})
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-900">
                                REJECTED
                              </span>
                            )}
                          </td>
                          <td className="p-3 text-right">
                            {isPending ? (
                              <div className="inline-flex items-center gap-1.5">
                                <button
                                  type="button"
                                  onClick={() => handleApproveFacultyAdmin(fac.id)}
                                  className="px-2.5 py-1 rounded-md bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-[11px] transition cursor-pointer"
                                >
                                  Approve Faculty
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleRejectFacultyAdmin(fac.id)}
                                  className="px-2 py-1 rounded-md bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-[11px] transition cursor-pointer"
                                >
                                  Reject
                                </button>
                              </div>
                            ) : (
                              <span className="text-[10px] text-slate-400">Decision Recorded</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ================================================================= */}
        {/* TAB: COMPANY REGISTRATIONS (ADMIN ONLY) */}
        {/* ================================================================= */}
        {activeTab === 'companies' && isAdmin && (
          <div className="p-5 space-y-4">
            <div className="p-3.5 rounded-xl bg-purple-50 dark:bg-purple-950/40 border border-purple-300 dark:border-purple-800 text-xs text-purple-900 dark:text-purple-200 flex items-start gap-2.5">
              <Building2 className="w-4 h-4 text-purple-600 shrink-0 mt-0.5" />
              <div>
                <strong className="block font-bold">Recruiter Corporate Clearance:</strong>
                All companies and corporate recruiters register with status PENDING_APPROVAL. College Placement Administration must verify organizational legitimacy before campus recruitment drives can proceed.
              </div>
            </div>

            <div className="border border-slate-200 dark:border-slate-800 rounded-xl overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 dark:bg-slate-900 text-slate-500 uppercase tracking-wider font-bold text-[10px]">
                  <tr>
                    <th className="p-3">Company Name</th>
                    <th className="p-3">Industry Domain</th>
                    <th className="p-3">HQ / Location</th>
                    <th className="p-3">HR Contact</th>
                    <th className="p-3">Official Email</th>
                    <th className="p-3">Recruitment Tier</th>
                    <th className="p-3">Status</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {companyApplicants
                    .filter((c) => (statusFilter === 'ALL' ? true : c.status === statusFilter))
                    .map((comp) => {
                      const isPending = comp.status === 'PENDING';

                      return (
                        <tr key={comp.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30 transition">
                          <td className="p-3">
                            <span className="font-bold text-slate-900 dark:text-white block">{comp.name}</span>
                            {comp.website && (
                              <a href={comp.website} target="_blank" rel="noreferrer" className="text-[10px] text-cyan-600 hover:underline">
                                {comp.website}
                              </a>
                            )}
                          </td>
                          <td className="p-3 text-slate-600 dark:text-slate-300">{comp.industry}</td>
                          <td className="p-3 text-slate-500">{comp.location}</td>
                          <td className="p-3 font-semibold">{comp.hrName}</td>
                          <td className="p-3 font-mono text-[11px]">{comp.hrEmail}</td>
                          <td className="p-3">
                            <span className="px-2 py-0.5 rounded bg-purple-50 text-purple-700 dark:bg-purple-950/40 dark:text-purple-300 font-semibold text-[10px]">
                              {comp.tier}
                            </span>
                          </td>
                          <td className="p-3">
                            {comp.status === 'PENDING' ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-900">
                                PENDING_APPROVAL
                              </span>
                            ) : comp.status === 'APPROVED' ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-900">
                                APPROVED ({comp.approvedByName || 'Admin'})
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-900">
                                REJECTED
                              </span>
                            )}
                          </td>
                          <td className="p-3 text-right">
                            {isPending ? (
                              <div className="inline-flex items-center gap-1.5">
                                <button
                                  type="button"
                                  onClick={() => handleApproveCompany(comp.id)}
                                  className="px-2.5 py-1 rounded-md bg-purple-600 hover:bg-purple-700 text-white font-bold text-[11px] transition cursor-pointer"
                                >
                                  Approve Partner
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleRejectCompany(comp.id)}
                                  className="px-2 py-1 rounded-md bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-[11px] transition cursor-pointer"
                                >
                                  Reject
                                </button>
                              </div>
                            ) : (
                              <span className="text-[10px] text-slate-400">Processed</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ================================================================= */}
        {/* TAB: ADMIN REQUESTS (PRINCIPAL DR. K. SAGAR ONLY) */}
        {/* ================================================================= */}
        {activeTab === 'admin' && isAdmin && (
          <div className="p-5 space-y-4">
            <div
              className={`p-3.5 rounded-xl border text-xs flex items-start gap-2.5 ${
                isPrincipal
                  ? 'bg-emerald-50 dark:bg-emerald-950/40 border-emerald-300 dark:border-emerald-800 text-emerald-900 dark:text-emerald-200'
                  : 'bg-rose-50 dark:bg-rose-950/40 border-rose-300 dark:border-rose-800 text-rose-900 dark:text-rose-200'
              }`}
            >
              <Award className="w-4 h-4 shrink-0 mt-0.5" />
              <div>
                <strong className="block font-bold">Principal Executive Authority:</strong>
                {isPrincipal ? (
                  <span>
                    You are authenticated as <strong>Principal Dr. K. Sagar</strong>. You hold sole executive authorization to elevate roles, grant administrative permissions, and authorize Placement Officer credentials.
                  </span>
                ) : (
                  <span>
                    Principal Dr. K. Sagar exclusively decides administrator role petitions. You are authenticated as an institutional administrator; petitions are displayed in audit view only.
                  </span>
                )}
              </div>
            </div>

            <div className="border border-slate-200 dark:border-slate-800 rounded-xl overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 dark:bg-slate-900 text-slate-500 uppercase tracking-wider font-bold text-[10px]">
                  <tr>
                    <th className="p-3">Applicant</th>
                    <th className="p-3">Current Role</th>
                    <th className="p-3">Requested Privilege</th>
                    <th className="p-3">Department</th>
                    <th className="p-3">Justification & Notes</th>
                    <th className="p-3">Status</th>
                    <th className="p-3 text-right">Principal Decision</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {adminRequests
                    .filter((r) => (statusFilter === 'ALL' ? true : r.status === statusFilter))
                    .map((req) => (
                      <tr key={req.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30 transition">
                        <td className="p-3">
                          <span className="font-bold text-slate-900 dark:text-white block">{req.applicantName}</span>
                          <span className="text-[10px] text-slate-400">{req.applicantEmail}</span>
                        </td>
                        <td className="p-3">
                          <span className="px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-[10px] font-semibold">
                            {req.currentRole}
                          </span>
                        </td>
                        <td className="p-3">
                          <span className="px-2 py-0.5 rounded bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300 font-bold text-[10px]">
                            {req.requestedRole}
                          </span>
                        </td>
                        <td className="p-3 font-semibold">{req.department}</td>
                        <td className="p-3 text-slate-600 dark:text-slate-400 max-w-xs">{req.reason}</td>
                        <td className="p-3">
                          {req.status === 'PENDING' ? (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-900">
                              PENDING
                            </span>
                          ) : req.status === 'APPROVED' ? (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-900">
                              APPROVED ({req.decidedByName || 'Principal'})
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-900">
                              REJECTED
                            </span>
                          )}
                        </td>
                        <td className="p-3 text-right">
                          {req.status === 'PENDING' && isPrincipal ? (
                            <div className="inline-flex items-center gap-1.5">
                              <button
                                type="button"
                                onClick={() => handleDecideAdminRequest(req.id, true)}
                                className="px-2.5 py-1 rounded-md bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-[11px] transition cursor-pointer"
                              >
                                Grant Privilege
                              </button>
                              <button
                                type="button"
                                onClick={() => handleDecideAdminRequest(req.id, false)}
                                className="px-2 py-1 rounded-md bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-[11px] transition cursor-pointer"
                              >
                                Decline
                              </button>
                            </div>
                          ) : (
                            <span className="text-[10px] text-slate-400">
                              {req.status !== 'PENDING' ? 'Decision Recorded' : 'Principal Only'}
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {/* ========================================================================= */}
      {/* 1. BATCH APPROVAL / REJECTION CONFIRMATION MODAL */}
      {/* ========================================================================= */}
      {confirmModal.isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-[#101A35] rounded-2xl border border-slate-200 dark:border-slate-800 shadow-2xl max-w-lg w-full overflow-hidden">
            {/* Modal Header */}
            <div className={`p-5 border-b flex items-start space-x-3 ${
              confirmModal.action === 'APPROVE'
                ? 'bg-emerald-50/50 dark:bg-emerald-950/30 border-emerald-200 dark:border-emerald-900/50'
                : 'bg-rose-50/50 dark:bg-rose-950/30 border-rose-200 dark:border-rose-900/50'
            }`}>
              <div className={`p-2 rounded-xl shrink-0 ${
                confirmModal.action === 'APPROVE' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300' : 'bg-rose-100 text-rose-700 dark:bg-rose-900 dark:text-rose-300'
              }`}>
                {confirmModal.action === 'APPROVE' ? <CheckCircle2 className="w-6 h-6" /> : <AlertTriangle className="w-6 h-6" />}
              </div>
              <div className="flex-1">
                <h3 className="text-base font-bold text-slate-900 dark:text-white">
                  {confirmModal.action === 'APPROVE' ? 'Confirm Batch Student Approval' : 'Confirm Batch Rejection'}
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                  You are about to {confirmModal.action === 'APPROVE' ? 'verify & approve' : 'reject'}{' '}
                  <strong>{confirmModal.students.length} student registration(s)</strong> from your assigned departments.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setConfirmModal({ isOpen: false, action: 'APPROVE', students: [] })}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer p-1"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Candidate List Preview */}
            <div className="p-5 space-y-3">
              <div className="text-xs font-semibold text-slate-700 dark:text-slate-300 flex items-center justify-between">
                <span>Selected Candidates:</span>
                <span className="text-[11px] text-slate-400">{confirmModal.students.length} items</span>
              </div>

              <div className="max-h-56 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800 border border-slate-200 dark:border-slate-800 rounded-xl p-2 bg-slate-50/50 dark:bg-slate-900/40">
                {confirmModal.students.map((stu) => (
                  <div key={stu.id} className="py-2 px-2 flex items-center justify-between text-xs">
                    <div>
                      <span className="font-bold text-slate-900 dark:text-white block">{stu.fullName}</span>
                      <span className="font-mono text-[10px] text-cyan-600 dark:text-cyan-400">{stu.rollNumber}</span>
                    </div>
                    <div className="text-right">
                      <span className="px-2 py-0.5 rounded bg-slate-200 dark:bg-slate-800 font-semibold text-[10px]">
                        {stu.branch}
                      </span>
                      <span className="text-[10px] text-slate-400 block mt-0.5">CGPA: {stu.cgpa.toFixed(2)}</span>
                    </div>
                  </div>
                ))}
              </div>

              {confirmModal.action === 'APPROVE' ? (
                <div className="p-3 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-[11px] text-emerald-800 dark:text-emerald-200">
                  <strong>Notice:</strong> Once approved, the student accounts will be marked as <strong>ACTIVE</strong> in the persistent database and candidates will be able to log in immediately with their Roll Number and password.
                </div>
              ) : (
                <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800 text-[11px] text-rose-800 dark:text-rose-200">
                  <strong>Warning:</strong> Rejected students will not have access to the placement hub until an updated registration is submitted and verified.
                </div>
              )}
            </div>

            {/* Modal Actions */}
            <div className="p-4 bg-slate-50 dark:bg-slate-900/70 border-t border-slate-200 dark:border-slate-800 flex items-center justify-end space-x-2">
              <button
                type="button"
                onClick={() => setConfirmModal({ isOpen: false, action: 'APPROVE', students: [] })}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-800 transition cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={executeConfirmedBatchAction}
                className={`px-4 py-2 rounded-xl text-xs font-bold text-white transition cursor-pointer shadow-md flex items-center space-x-1.5 ${
                  confirmModal.action === 'APPROVE'
                    ? 'bg-emerald-600 hover:bg-emerald-700'
                    : 'bg-rose-600 hover:bg-rose-700'
                }`}
              >
                {confirmModal.action === 'APPROVE' ? (
                  <>
                    <Check className="w-4 h-4" />
                    <span>Confirm &amp; Approve ({confirmModal.students.length})</span>
                  </>
                ) : (
                  <>
                    <XCircle className="w-4 h-4" />
                    <span>Confirm &amp; Reject ({confirmModal.students.length})</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* 2. PERSISTENT APPROVAL AUDIT HISTORY MODAL */}
      {/* ========================================================================= */}
      {showHistoryModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-[#101A35] rounded-2xl border border-slate-200 dark:border-slate-800 shadow-2xl max-w-3xl w-full overflow-hidden flex flex-col max-h-[85vh]">
            {/* Header */}
            <div className="p-5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
              <div className="flex items-center space-x-2.5">
                <div className="p-2 rounded-xl bg-cyan-100 dark:bg-cyan-950 text-cyan-600 dark:text-cyan-400">
                  <History className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900 dark:text-white">
                    Institutional Approval Audit Log
                  </h3>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Real-time immutable transaction trail of student approvals &amp; departmental clearances
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowHistoryModal(false)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer p-1"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Audit Records Table */}
            <div className="p-5 overflow-y-auto flex-1">
              {approvalHistory.length === 0 ? (
                <div className="p-8 text-center text-slate-400">
                  <Clock className="w-8 h-8 mx-auto mb-2 opacity-50" />
                  <p className="font-semibold text-xs">No approval actions logged yet</p>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    When faculty coordinators or administrators verify or reject registrations, MySQL audit records appear here.
                  </p>
                </div>
              ) : (
                <div className="border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-50 dark:bg-slate-900 text-slate-500 uppercase tracking-wider font-bold text-[10px]">
                      <tr>
                        <th className="p-3">Timestamp</th>
                        <th className="p-3">Candidate / Target</th>
                        <th className="p-3">Department</th>
                        <th className="p-3">Approver</th>
                        <th className="p-3">Decision</th>
                        <th className="p-3">Notes</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                      {approvalHistory.map((item: any) => {
                        const ts = item.decided_at || item.timestamp;
                        const targetName = item.target_name || item.studentName || 'Unknown';
                        const roll = item.student_roll_number || item.studentRollNumber || item.request_type;
                        const dept = item.department || '-';
                        const approver = item.approver_name || item.approverName || 'System';
                        const decision = item.decision || item.action;
                        const notes = item.comments || item.notes || 'Institutional clearance';
                        return (
                          <tr key={item.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30">
                            <td className="p-3 text-[11px] text-slate-500 font-mono">
                              {ts
                                ? new Date(ts).toLocaleString([], {
                                    month: 'short',
                                    day: 'numeric',
                                    hour: '2-digit',
                                    minute: '2-digit',
                                  })
                                : '-'}
                            </td>
                            <td className="p-3">
                              <span className="font-bold text-slate-900 dark:text-white block">{targetName}</span>
                              {roll && <span className="text-[10px] text-cyan-600 dark:text-cyan-400 font-mono">{roll}</span>}
                            </td>
                            <td className="p-3 font-semibold">{dept}</td>
                            <td className="p-3 text-slate-700 dark:text-slate-300 font-medium">{approver}</td>
                            <td className="p-3">
                              {decision === 'APPROVED' ? (
                                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border border-emerald-300 dark:border-emerald-800">
                                  APPROVED
                                </span>
                              ) : (
                                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 border border-rose-300 dark:border-rose-800">
                                  REJECTED
                                </span>
                              )}
                            </td>
                            <td className="p-3 text-[11px] text-slate-500 dark:text-slate-400 max-w-xs truncate">
                              {notes}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Footer */}
            <div className="p-4 bg-slate-50 dark:bg-slate-900/70 border-t border-slate-200 dark:border-slate-800 flex items-center justify-end">
              <button
                type="button"
                onClick={() => setShowHistoryModal(false)}
                className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 text-white transition cursor-pointer"
              >
                Close Audit Log
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
