import {
  Student,
  Company,
  PlacementDrive,
  Skill,
  Application,
  User,
  ActivityLog,
  ApplicationStage,
  ApprovalHistoryRecord,
  UnifiedUserProfile,
  isFacultyAuthorizedForStudent,
  getFacultyAuthorizedDepts,
} from '../types';
import {
  INITIAL_STUDENTS,
  INITIAL_COMPANIES,
  INITIAL_DRIVES,
  INITIAL_SKILLS,
  INITIAL_APPLICATIONS,
  INITIAL_USERS,
  INITIAL_ACTIVITY_LOGS,
} from '../data/initialData';

const STORAGE_KEY = 'gcet_placement_hub_v1';

interface DbState {
  students: Student[];
  companies: Company[];
  drives: PlacementDrive[];
  skills: Skill[];
  applications: Application[];
  users: User[];
  activityLogs: ActivityLog[];
  approvalHistory?: ApprovalHistoryRecord[];
  userProfiles?: Record<string, UnifiedUserProfile>;
}

type DbListener = () => void;
const listeners = new Set<DbListener>();

function notifyListeners() {
  listeners.forEach((listener) => {
    try {
      listener();
    } catch (e) {
      console.error('Error notifying DB listener:', e);
    }
  });
}

export function subscribeToDb(listener: DbListener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function loadState(): DbState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      const initial: DbState = {
        students: INITIAL_STUDENTS,
        companies: INITIAL_COMPANIES,
        drives: INITIAL_DRIVES,
        skills: INITIAL_SKILLS,
        applications: INITIAL_APPLICATIONS,
        users: INITIAL_USERS,
        activityLogs: INITIAL_ACTIVITY_LOGS,
      };
      saveState(initial);
      return initial;
    }
    const parsed: DbState = JSON.parse(raw);
    let stateModified = false;
    if (!parsed.approvalHistory || !Array.isArray(parsed.approvalHistory)) {
      parsed.approvalHistory = [];
      stateModified = true;
    }

    // Repair broken legacy seed placeholder hashes without modifying any user-set or registered passwords
    const LEGACY_BROKEN_SEED_HASHES = new Set([
      '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918',
      'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3',
      '04f8996da763b7a969b1028ee3007569eaf3a635486ddab211d512c85b9df8fb',
      'e6c279f628cffd8023be8a057f8eab857e4745fb4c5e000f81169d14f1e05d74',
    ]);

    // Reconcile bootstrap users if missing from cached localStorage state
    if (!parsed.users || !Array.isArray(parsed.users)) {
      parsed.users = [...INITIAL_USERS];
      stateModified = true;
    } else {
      for (const initUser of INITIAL_USERS) {
        const found = parsed.users.find(
          (u) => u.id === initUser.id || u.username.toLowerCase() === initUser.username.toLowerCase()
        );
        if (!found) {
          parsed.users.push({ ...initUser });
          stateModified = true;
        } else {
          if (initUser.authorizedDepartments && (!found.authorizedDepartments || found.authorizedDepartments.length === 0)) {
            found.authorizedDepartments = [...initUser.authorizedDepartments];
            stateModified = true;
          }
          if ((!found.passwordHash || LEGACY_BROKEN_SEED_HASHES.has(found.passwordHash)) && initUser.passwordHash) {
            found.passwordHash = initUser.passwordHash;
            found.salt = initUser.salt;
            stateModified = true;
          }
        }
      }
    }

    // Ensure all faculty accounts have persistent authorized departments configured
    for (const u of parsed.users) {
      if (u.role === 'FACULTY') {
        const expectedDepts = getFacultyAuthorizedDepts(u.username);
        if (expectedDepts.length > 0 && (!u.authorizedDepartments || u.authorizedDepartments.length === 0)) {
          u.authorizedDepartments = expectedDepts;
          stateModified = true;
        }
      }
    }

    // Reconcile and synchronize student and user approval statuses
    if (parsed.students && Array.isArray(parsed.students)) {
      for (const s of parsed.students) {
        const matchingUser = parsed.users.find(
          (u) =>
            u.username.toLowerCase() === s.rollNumber.toLowerCase() ||
            (u.studentRollNumber && u.studentRollNumber.toLowerCase() === s.rollNumber.toLowerCase())
        );
        if (matchingUser) {
          if (s.approvalStatus === 'APPROVED' || s.approvalStatus === 'ACTIVE') {
            if (matchingUser.status !== 'APPROVED') {
              matchingUser.status = 'APPROVED';
              matchingUser.isActive = true;
              stateModified = true;
            }
            if (s.approvalStatus !== 'APPROVED') {
              s.approvalStatus = 'APPROVED';
              stateModified = true;
            }
          }
        }
      }
    }

    if (stateModified) {
      saveState(parsed);
    }
    return parsed;
  } catch (err) {
    console.error('Failed to parse state from localStorage, falling back to initial data', err);
    return {
      students: INITIAL_STUDENTS,
      companies: INITIAL_COMPANIES,
      drives: INITIAL_DRIVES,
      skills: INITIAL_SKILLS,
      applications: INITIAL_APPLICATIONS,
      users: INITIAL_USERS,
      activityLogs: INITIAL_ACTIVITY_LOGS,
    };
  }
}

function saveState(state: DbState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    notifyListeners();
  } catch (err) {
    console.error('Failed to save state to localStorage', err);
  }
}

export const db = {
  // Activity Logging
  logActivity(userId: string, userName: string, action: string, category: ActivityLog['category'], details: string) {
    const state = loadState();
    const newLog: ActivityLog = {
      id: `act-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
      timestamp: new Date().toISOString(),
      userId,
      userName,
      action,
      category,
      details,
    };
    state.activityLogs.unshift(newLog);
    if (state.activityLogs.length > 200) {
      state.activityLogs = state.activityLogs.slice(0, 200);
    }
    saveState(state);
  },

  getActivityLogs(): ActivityLog[] {
    return loadState().activityLogs;
  },

  // Students
  getStudents(): Student[] {
    return loadState().students;
  },

  getStudentById(id: string): Student | undefined {
    return loadState().students.find((s) => s.id === id);
  },

  getStudentByRollNumber(rollNumber: string): Student | undefined {
    return loadState().students.find(
      (s) => s.rollNumber.toLowerCase() === rollNumber.trim().toLowerCase()
    );
  },

  saveStudent(student: any, actorName = 'System'): void {
    const state = loadState();
    const roll = (student.rollNumber || student.roll_number || '').trim().toLowerCase();
    const existingIndex = state.students.findIndex(
      (s) => s.id === student.id || (roll && s.rollNumber && s.rollNumber.toLowerCase() === roll)
    );

    const normalizedStudent: Student = {
      ...(existingIndex !== -1 ? state.students[existingIndex] : {}),
      id: student.id,
      rollNumber: student.rollNumber || student.roll_number || '',
      fullName: student.fullName || student.full_name || '',
      email: student.email || '',
      phone: student.phone || '',
      branch: student.branch,
      section: student.section || state.students[existingIndex]?.section || 'A',
      currentSemester: student.currentSemester || student.current_semester || state.students[existingIndex]?.currentSemester || 7,
      academicYear: student.academicYear || student.academic_year || 4,
      cgpa: typeof student.cgpa === 'number' ? student.cgpa : parseFloat(student.cgpa) || 8.0,
      tenthPercentage: typeof student.tenthPercentage === 'number' ? student.tenthPercentage : parseFloat(student.tenth_percentage) || 85.0,
      interOrDiplomaPercentage: typeof student.interOrDiplomaPercentage === 'number' ? student.interOrDiplomaPercentage : parseFloat(student.inter_percentage) || 88.0,
      graduationYear: student.graduationYear || student.graduation_year || 2026,
      backlogs: student.backlogs ?? 0,
      placementStatus: student.placementStatus || student.placement_status || 'UNPLACED',
      approvalStatus: student.approvalStatus || student.approval_status || 'APPROVED',
      skills: student.skills || state.students[existingIndex]?.skills || [],
      projects: student.projects || state.students[existingIndex]?.projects || [],
      certifications: student.certifications || state.students[existingIndex]?.certifications || [],
      internships: student.internships || state.students[existingIndex]?.internships || [],
      achievements: student.achievements || state.students[existingIndex]?.achievements || [],
      dob: student.dob ?? state.students[existingIndex]?.dob,
      gender: student.gender ?? state.students[existingIndex]?.gender,
      address: student.address ?? state.students[existingIndex]?.address,
      location: student.location ?? state.students[existingIndex]?.location,
      aboutMe: student.aboutMe ?? state.students[existingIndex]?.aboutMe,
      profilePhoto: student.profilePhoto ?? state.students[existingIndex]?.profilePhoto,
      technicalSkills: student.technicalSkills ?? state.students[existingIndex]?.technicalSkills,
      programmingLanguages: student.programmingLanguages ?? state.students[existingIndex]?.programmingLanguages,
      frameworksTechnologies: student.frameworksTechnologies ?? state.students[existingIndex]?.frameworksTechnologies,
      softSkills: student.softSkills ?? state.students[existingIndex]?.softSkills,
      careerInterests: student.careerInterests ?? state.students[existingIndex]?.careerInterests,
      preferredJobRoles: student.preferredJobRoles ?? state.students[existingIndex]?.preferredJobRoles,
      socialLinks: student.socialLinks ?? state.students[existingIndex]?.socialLinks,
      customLinks: student.customLinks ?? state.students[existingIndex]?.customLinks,
      resumeUrl: student.resumeUrl ?? student.resume_url ?? state.students[existingIndex]?.resumeUrl,
      registeredAt: student.registeredAt || student.created_at || new Date().toISOString(),
    };

    if (existingIndex === -1) {
      state.students.unshift(normalizedStudent);
      this.logActivity('usr-sys', actorName, 'STUDENT_CREATED', 'STUDENT', `Added student ${normalizedStudent.fullName} (${normalizedStudent.rollNumber})`);
    } else {
      state.students[existingIndex] = { ...state.students[existingIndex], ...normalizedStudent };
      this.logActivity('usr-sys', actorName, 'STUDENT_UPDATED', 'STUDENT', `Updated profile of ${normalizedStudent.fullName} (${normalizedStudent.rollNumber})`);
    }
    saveState(state);
    notifyListeners();
  },

  deleteStudent(id: string, actorName = 'System'): void {
    const state = loadState();
    const student = state.students.find((s) => s.id === id);
    if (!student) return;

    // Check applications
    const studentApps = state.applications.filter((a) => a.studentId === id);
    if (studentApps.length > 0) {
      throw new Error(`Cannot delete student ${student.fullName}. They have ${studentApps.length} active placement application(s).`);
    }

    state.students = state.students.filter((s) => s.id !== id);
    this.logActivity('usr-sys', actorName, 'STUDENT_DELETED', 'STUDENT', `Removed student record ${student.fullName} (${student.rollNumber})`);
    saveState(state);
  },

  getApprovalHistory(): ApprovalHistoryRecord[] {
    return loadState().approvalHistory || [];
  },

  approveStudent(studentIdOrRoll: string, approver: User | string = 'Faculty Coordinator'): void {
    const approverRole = typeof approver === 'object' ? approver.role : undefined;
    const approverName = typeof approver === 'object' ? approver.fullName : approver;
    const approverId = typeof approver === 'object' ? approver.id : undefined;
    const approverKey = typeof approver === 'object' ? (approver.username || approver.id) : (approverId || approverName);

    // Strict Permission Check: Administrators must NOT approve student registrations under this workflow.
    if (approverRole && ['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN'].includes(approverRole)) {
      throw new Error('Strict Permission Violation: Administrators must NOT approve student registrations under this workflow. Only authorized Department Faculty Mentors may approve student registrations.');
    }

    const state = loadState();
    const term = studentIdOrRoll.trim().toLowerCase();
    
    const student = state.students.find(
      (s) => s.id.toLowerCase() === term || s.rollNumber.toLowerCase() === term
    );

    if (!student) {
      throw new Error(`Student record with ID or Roll Number "${studentIdOrRoll}" was not found in the database.`);
    }

    // Department Authorization Check for Faculty
    if (typeof approver === 'object' && approver.role === 'FACULTY') {
      if (!isFacultyAuthorizedForStudent(approverKey, student.branch)) {
        const authorizedDepts = getFacultyAuthorizedDepts(approverKey);
        throw new Error(
          `Unauthorized Department: You (${approverName}) are assigned to [${authorizedDepts.join(', ')}]. You cannot approve student ${student.fullName} from department "${student.branch}".`
        );
      }
    }

    const nowIso = new Date().toISOString();

    // 1. Update Student Table
    student.approvalStatus = 'APPROVED';
    student.approvedByFacultyId = approverId;
    student.approvedByName = approverName;
    student.approvedAt = nowIso;

    // 2. Update User Table (matched by ID, username, or student's rollNumber)
    const rollToMatch = student.rollNumber.toLowerCase();
    let user = state.users.find(
      (u) =>
        u.id.toLowerCase() === term ||
        u.username.toLowerCase() === term ||
        u.username.toLowerCase() === rollToMatch ||
        (u.studentRollNumber && u.studentRollNumber.toLowerCase() === rollToMatch)
    );

    if (user) {
      user.status = 'APPROVED';
      user.isActive = true;
      user.approvedBy = approverId;
      user.approvedByName = approverName;
      user.approvedAt = nowIso;
      user.studentRollNumber = student.rollNumber;
      user.department = student.branch;
    } else {
      // Auto-provision user account for this student if missing
      user = {
        id: `usr-${student.id}`,
        username: student.rollNumber,
        email: student.email,
        fullName: student.fullName,
        role: 'STUDENT',
        department: student.branch,
        studentRollNumber: student.rollNumber,
        status: 'APPROVED',
        isActive: true,
        approvedBy: approverId,
        approvedByName: approverName,
        approvedAt: nowIso,
        createdAt: student.registeredAt || nowIso,
      };
      state.users.push(user);
    }

    // 3. Append to persistent Approval History Log
    if (!state.approvalHistory) {
      state.approvalHistory = [];
    }
    state.approvalHistory.unshift({
      id: `appr-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
      studentId: student.id,
      studentRollNumber: student.rollNumber,
      studentName: student.fullName,
      department: student.branch,
      approverId: approverId || 'usr-fac',
      approverName: approverName,
      action: 'APPROVED',
      timestamp: nowIso,
      notes: `Faculty approval verified for ${student.branch}. Full portal access enabled.`,
    });

    this.logActivity(
      approverId || 'usr-fac',
      approverName,
      'STUDENT_APPROVED',
      'AUTH',
      `Faculty approved student registration: ${student.fullName} (${student.rollNumber}) [${student.branch}]`
    );

    saveState(state);
  },

  batchApproveStudents(studentIdsOrRolls: string[], approver: User | string = 'Faculty Coordinator'): number {
    const approverRole = typeof approver === 'object' ? approver.role : undefined;
    const approverName = typeof approver === 'object' ? approver.fullName : approver;
    const approverId = typeof approver === 'object' ? approver.id : undefined;
    const approverKey = typeof approver === 'object' ? (approver.username || approver.id) : (approverId || approverName);

    if (approverRole && ['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN'].includes(approverRole)) {
      throw new Error('Strict Permission Violation: Administrators must NOT approve student registrations under this workflow.');
    }

    const state = loadState();
    let count = 0;
    const nowIso = new Date().toISOString();
    const targets = studentIdsOrRolls.map((t) => t.trim().toLowerCase());

    if (!state.approvalHistory) {
      state.approvalHistory = [];
    }

    for (const target of targets) {
      const student = state.students.find(
        (s) => s.id.toLowerCase() === target || s.rollNumber.toLowerCase() === target
      );
      if (!student) continue;

      // Department authorization check
      if (typeof approver === 'object' && approver.role === 'FACULTY') {
        if (!isFacultyAuthorizedForStudent(approverKey, student.branch)) {
          continue; // Skip candidates outside authorized departments in batch
        }
      }

      student.approvalStatus = 'APPROVED';
      student.approvedByFacultyId = approverId;
      student.approvedByName = approverName;
      student.approvedAt = nowIso;
      count++;

      const rollToMatch = student.rollNumber.toLowerCase();
      let user = state.users.find(
        (u) =>
          u.id.toLowerCase() === target ||
          u.username.toLowerCase() === target ||
          u.username.toLowerCase() === rollToMatch ||
          (u.studentRollNumber && u.studentRollNumber.toLowerCase() === rollToMatch)
      );

      if (user) {
        user.status = 'APPROVED';
        user.isActive = true;
        user.approvedBy = approverId;
        user.approvedByName = approverName;
        user.approvedAt = nowIso;
        user.studentRollNumber = student.rollNumber;
      } else {
        user = {
          id: `usr-${student.id}`,
          username: student.rollNumber,
          email: student.email,
          fullName: student.fullName,
          role: 'STUDENT',
          department: student.branch,
          studentRollNumber: student.rollNumber,
          status: 'APPROVED',
          isActive: true,
          approvedBy: approverId,
          approvedByName: approverName,
          approvedAt: nowIso,
          createdAt: student.registeredAt || nowIso,
        };
        state.users.push(user);
      }

      state.approvalHistory.unshift({
        id: `appr-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
        studentId: student.id,
        studentRollNumber: student.rollNumber,
        studentName: student.fullName,
        department: student.branch,
        approverId: approverId || 'usr-fac',
        approverName: approverName,
        action: 'APPROVED',
        timestamp: nowIso,
        notes: `Bulk faculty approval processed for ${student.branch}.`,
      });
    }

    this.logActivity(
      approverId || 'usr-fac',
      approverName,
      'BATCH_STUDENT_APPROVAL',
      'AUTH',
      `Faculty batch approved ${count} student registrations`
    );
    saveState(state);
    return count;
  },

  rejectStudent(studentIdOrRoll: string, approver: User | string = 'Faculty Coordinator', reason = 'Registration details did not meet departmental criteria'): void {
    const approverRole = typeof approver === 'object' ? approver.role : undefined;
    const approverName = typeof approver === 'object' ? approver.fullName : approver;
    const approverId = typeof approver === 'object' ? approver.id : undefined;
    const approverKey = typeof approver === 'object' ? (approver.username || approver.id) : (approverId || approverName);

    if (approverRole && ['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN'].includes(approverRole)) {
      throw new Error('Strict Permission Violation: Administrators must NOT reject student registrations under this workflow.');
    }

    const state = loadState();
    const term = studentIdOrRoll.trim().toLowerCase();
    const nowIso = new Date().toISOString();
    
    const student = state.students.find(
      (s) => s.id.toLowerCase() === term || s.rollNumber.toLowerCase() === term
    );

    if (student && typeof approver === 'object' && approver.role === 'FACULTY') {
      if (!isFacultyAuthorizedForStudent(approverKey, student.branch)) {
        const authorizedDepts = getFacultyAuthorizedDepts(approverKey);
        throw new Error(
          `Unauthorized Department: You (${approverName}) are assigned to [${authorizedDepts.join(', ')}]. You cannot reject student ${student.fullName} from department "${student.branch}".`
        );
      }
    }

    if (student) {
      student.approvalStatus = 'REJECTED';
      student.rejectionReason = reason;
    }

    const rollToMatch = student ? student.rollNumber.toLowerCase() : term;
    const user = state.users.find(
      (u) =>
        u.id.toLowerCase() === term ||
        u.username.toLowerCase() === term ||
        u.username.toLowerCase() === rollToMatch ||
        (u.studentRollNumber && u.studentRollNumber.toLowerCase() === rollToMatch)
    );
    if (user) {
      user.status = 'REJECTED';
      user.isActive = false;
      user.rejectionReason = reason;
    }

    if (!state.approvalHistory) {
      state.approvalHistory = [];
    }
    state.approvalHistory.unshift({
      id: `appr-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
      studentId: student?.id || '',
      studentRollNumber: student?.rollNumber || term,
      studentName: student?.fullName || user?.fullName || 'Student',
      department: student?.branch || user?.department || '',
      approverId: approverId || 'usr-fac',
      approverName: approverName,
      action: 'REJECTED',
      timestamp: nowIso,
      notes: reason,
    });

    const name = student?.fullName || user?.fullName || studentIdOrRoll;
    this.logActivity(approverId || 'usr-fac', approverName, 'STUDENT_REJECTED', 'AUTH', `Faculty rejected registration for ${name}: ${reason}`);
    saveState(state);
  },

  batchRejectStudents(studentIdsOrRolls: string[], approver: User | string = 'Faculty Coordinator'): number {
    const approverRole = typeof approver === 'object' ? approver.role : undefined;
    const approverName = typeof approver === 'object' ? approver.fullName : approver;
    const approverId = typeof approver === 'object' ? approver.id : undefined;
    const approverKey = typeof approver === 'object' ? (approver.username || approver.id) : (approverId || approverName);

    if (approverRole && ['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN'].includes(approverRole)) {
      throw new Error('Strict Permission Violation: Administrators must NOT reject student registrations under this workflow.');
    }

    const state = loadState();
    let count = 0;
    const nowIso = new Date().toISOString();
    const targets = studentIdsOrRolls.map((t) => t.trim().toLowerCase());

    if (!state.approvalHistory) {
      state.approvalHistory = [];
    }

    for (const target of targets) {
      const student = state.students.find(
        (s) => s.id.toLowerCase() === target || s.rollNumber.toLowerCase() === target
      );
      if (!student) continue;

      if (typeof approver === 'object' && approver.role === 'FACULTY') {
        if (!isFacultyAuthorizedForStudent(approverKey, student.branch)) {
          continue;
        }
      }

      student.approvalStatus = 'REJECTED';
      student.rejectionReason = 'Declined during departmental review batch';
      count++;

      const rollToMatch = student.rollNumber.toLowerCase();
      const user = state.users.find(
        (u) =>
          u.id.toLowerCase() === target ||
          u.username.toLowerCase() === target ||
          u.username.toLowerCase() === rollToMatch ||
          (u.studentRollNumber && u.studentRollNumber.toLowerCase() === rollToMatch)
      );
      if (user) {
        user.status = 'REJECTED';
        user.isActive = false;
        user.rejectionReason = 'Declined during departmental review batch';
      }

      state.approvalHistory.unshift({
        id: `appr-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
        studentId: student.id,
        studentRollNumber: student.rollNumber,
        studentName: student.fullName,
        department: student.branch,
        approverId: approverId || 'usr-fac',
        approverName: approverName,
        action: 'REJECTED',
        timestamp: nowIso,
        notes: 'Bulk rejection during departmental batch review',
      });
    }

    this.logActivity(approverId || 'usr-fac', approverName, 'BATCH_STUDENT_REJECTION', 'AUTH', `Faculty batch rejected ${count} student registrations`);
    saveState(state);
    return count;
  },

  approveFaculty(facultyIdOrUsername: string, approver: User | string = 'College Administrator'): void {
    const approverRole = typeof approver === 'object' ? approver.role : undefined;
    const approverName = typeof approver === 'object' ? approver.fullName : approver;
    const approverId = typeof approver === 'object' ? approver.id : undefined;

    // Strict Permission Check: Only authorized administrators may approve faculty accounts.
    if (approverRole && !['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN'].includes(approverRole)) {
      throw new Error('Strict Permission Violation: Only authorized College Administrators may approve faculty registrations. Faculty members cannot approve faculty accounts.');
    }

    const state = loadState();
    const term = facultyIdOrUsername.trim().toLowerCase();

    const user = state.users.find(
      (u) =>
        u.id.toLowerCase() === term ||
        u.username.toLowerCase() === term ||
        (u.employeeId && u.employeeId.toLowerCase() === term) ||
        u.email.toLowerCase() === term
    );
    if (user) {
      user.status = 'ACTIVE';
      user.isActive = true;
      user.approvedBy = approverId;
      user.approvedByName = approverName;
      user.approvedAt = new Date().toISOString();
      this.logActivity(approverId || 'usr-adm', approverName, 'FACULTY_APPROVED', 'AUTH', `Administrator approved faculty member: ${user.fullName} (${user.employeeId || user.username})`);
      saveState(state);
    }
  },

  rejectFaculty(facultyIdOrUsername: string, approver: User | string = 'College Administrator', reason = 'Application details could not be validated.'): void {
    const approverRole = typeof approver === 'object' ? approver.role : undefined;
    const approverName = typeof approver === 'object' ? approver.fullName : approver;
    const approverId = typeof approver === 'object' ? approver.id : undefined;

    if (approverRole && !['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN'].includes(approverRole)) {
      throw new Error('Strict Permission Violation: Only authorized College Administrators may reject faculty accounts.');
    }

    const state = loadState();
    const term = facultyIdOrUsername.trim().toLowerCase();

    const user = state.users.find(
      (u) =>
        u.id.toLowerCase() === term ||
        u.username.toLowerCase() === term ||
        (u.employeeId && u.employeeId.toLowerCase() === term) ||
        u.email.toLowerCase() === term
    );
    if (user) {
      user.status = 'REJECTED';
      user.isActive = false;
      user.rejectionReason = reason;
      this.logActivity(approverId || 'usr-adm', approverName, 'FACULTY_REJECTED', 'AUTH', `Administrator rejected faculty member: ${user.fullName}`);
      saveState(state);
    }
  },

  approveCompany(companyIdOrName: string, approver: User | string = 'College Administrator'): void {
    const approverRole = typeof approver === 'object' ? approver.role : undefined;
    const approverName = typeof approver === 'object' ? approver.fullName : approver;
    const approverId = typeof approver === 'object' ? approver.id : undefined;

    if (approverRole && !['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN'].includes(approverRole)) {
      throw new Error('Strict Permission Violation: Only authorized College Administrators may approve corporate recruiting partners.');
    }

    const state = loadState();
    const term = companyIdOrName.trim().toLowerCase();

    const company = state.companies.find(
      (c) => c.id.toLowerCase() === term || c.name.toLowerCase() === term
    );
    if (company) {
      company.status = 'ACTIVE';
      company.approvedByAdminId = approverId;
      company.approvedByName = approverName;
      company.approvedAt = new Date().toISOString();
    }

    const user = state.users.find(
      (u) =>
        u.id.toLowerCase() === term ||
        (u.companyId && u.companyId.toLowerCase() === term) ||
        (u.companyName && u.companyName.toLowerCase() === term) ||
        (company && u.companyId === company.id)
    );
    const nowIso = new Date().toISOString();
    if (user) {
      user.status = 'ACTIVE';
      user.isActive = true;
      user.approvedBy = approverId;
      user.approvedByName = approverName;
      user.approvedAt = nowIso;
    } else if (company) {
      const slug = company.name
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '_')
        .replace(/^_+|_+$/g, '')
        .slice(0, 20);
      const recruiterUsername = `recruiter_${slug || company.id}`;
      state.users.push({
        id: `usr-comp-${Date.now()}`,
        username: recruiterUsername,
        email: company.hrEmail,
        fullName: `${company.hrName} (${company.name})`,
        role: 'COMPANY',
        companyId: company.id,
        companyName: company.name,
        department: `${company.name} Corporate Talent Acquisition`,
        status: 'ACTIVE',
        isActive: true,
        approvedBy: approverId,
        approvedByName: approverName,
        approvedAt: nowIso,
        salt: 'gcet_salt_recruiter',
        passwordHash: '02d5f76d1647e19b66209d9859b5f9e69dc7ff5eab8bcd9526c0044748cbffec',
        createdAt: nowIso,
      });
    }

    const name = company?.name || user?.fullName || companyIdOrName;
    this.logActivity(approverId || 'usr-adm', approverName, 'COMPANY_APPROVED', 'AUTH', `Administrator approved corporate partner: ${name}`);
    saveState(state);
  },

  rejectCompany(companyIdOrName: string, approver: User | string = 'College Administrator', reason = 'Corporate credentials could not be verified.'): void {
    const approverRole = typeof approver === 'object' ? approver.role : undefined;
    const approverName = typeof approver === 'object' ? approver.fullName : approver;
    const approverId = typeof approver === 'object' ? approver.id : undefined;

    if (approverRole && !['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN'].includes(approverRole)) {
      throw new Error('Strict Permission Violation: Only authorized College Administrators may reject corporate recruiting partners.');
    }

    const state = loadState();
    const term = companyIdOrName.trim().toLowerCase();

    const company = state.companies.find(
      (c) => c.id.toLowerCase() === term || c.name.toLowerCase() === term
    );
    if (company) {
      company.status = 'REJECTED';
      company.rejectionReason = reason;
    }

    const user = state.users.find(
      (u) =>
        u.id.toLowerCase() === term ||
        (u.companyId && u.companyId.toLowerCase() === term) ||
        (company && u.companyId === company.id)
    );
    if (user) {
      user.status = 'REJECTED';
      user.isActive = false;
      user.rejectionReason = reason;
    }

    const name = company?.name || companyIdOrName;
    this.logActivity(approverId || 'usr-adm', approverName, 'COMPANY_REJECTED', 'AUTH', `Administrator rejected corporate partner: ${name}`);
    saveState(state);
  },

  getPendingStudents(): Student[] {
    const state = loadState();
    return state.students.filter((s) => s.approvalStatus === 'PENDING');
  },

  // Companies
  getCompanies(): Company[] {
    return loadState().companies;
  },

  getCompanyById(id: string): Company | undefined {
    return loadState().companies.find((c) => c.id === id);
  },

  saveCompany(company: Company, actorName = 'System'): void {
    const state = loadState();
    const index = state.companies.findIndex((c) => c.id === company.id);
    if (index === -1) {
      state.companies.unshift(company);
      this.logActivity('usr-sys', actorName, 'COMPANY_CREATED', 'COMPANY', `Registered company ${company.name}`);
    } else {
      state.companies[index] = company;
      this.logActivity('usr-sys', actorName, 'COMPANY_UPDATED', 'COMPANY', `Updated company ${company.name}`);
    }
    saveState(state);
  },

  deleteCompany(id: string, actorName = 'System'): void {
    const state = loadState();
    const comp = state.companies.find((c) => c.id === id);
    if (!comp) return;

    const linkedDrives = state.drives.filter((d) => d.companyId === id);
    if (linkedDrives.length > 0) {
      throw new Error(`Cannot delete company ${comp.name}. Associated with ${linkedDrives.length} placement drive(s).`);
    }

    state.companies = state.companies.filter((c) => c.id !== id);
    this.logActivity('usr-sys', actorName, 'COMPANY_DELETED', 'COMPANY', `Deleted company ${comp.name}`);
    saveState(state);
  },

  // Placement Drives
  getDrives(): PlacementDrive[] {
    return loadState().drives;
  },

  getDriveById(id: string): PlacementDrive | undefined {
    return loadState().drives.find((d) => d.id === id);
  },

  saveDrive(drive: PlacementDrive, actorName = 'System'): void {
    const state = loadState();
    const index = state.drives.findIndex((d) => d.id === drive.id);
    if (index === -1) {
      state.drives.unshift(drive);
      this.logActivity('usr-sys', actorName, 'DRIVE_CREATED', 'DRIVE', `Created placement drive for ${drive.companyName}: ${drive.jobRole}`);
    } else {
      state.drives[index] = drive;
      this.logActivity('usr-sys', actorName, 'DRIVE_UPDATED', 'DRIVE', `Updated drive ${drive.jobRole} (${drive.companyName})`);
    }
    saveState(state);
  },

  deleteDrive(id: string, actorName = 'System'): void {
    const state = loadState();
    const drive = state.drives.find((d) => d.id === id);
    if (!drive) return;

    const apps = state.applications.filter((a) => a.driveId === id);
    if (apps.length > 0) {
      throw new Error(`Cannot delete drive ${drive.jobRole}. It has ${apps.length} candidate application(s).`);
    }

    state.drives = state.drives.filter((d) => d.id !== id);
    this.logActivity('usr-sys', actorName, 'DRIVE_DELETED', 'DRIVE', `Removed drive ${drive.jobRole} for ${drive.companyName}`);
    saveState(state);
  },

  // Skills
  getSkills(): Skill[] {
    return loadState().skills;
  },

  getSkillById(id: string): Skill | undefined {
    return loadState().skills.find((s) => s.id === id);
  },

  saveSkill(skill: Skill, actorName = 'System'): void {
    const state = loadState();
    const index = state.skills.findIndex((s) => s.id === skill.id);
    if (index === -1) {
      state.skills.push(skill);
      this.logActivity('usr-sys', actorName, 'SKILL_CREATED', 'SKILL', `Added skill ${skill.name} under ${skill.category}`);
    } else {
      state.skills[index] = skill;
      this.logActivity('usr-sys', actorName, 'SKILL_UPDATED', 'SKILL', `Updated skill ${skill.name}`);
    }
    saveState(state);
  },

  deleteSkill(id: string, actorName = 'System'): void {
    const state = loadState();
    const skill = state.skills.find((s) => s.id === id);
    if (!skill) return;

    state.skills = state.skills.filter((s) => s.id !== id);
    this.logActivity('usr-sys', actorName, 'SKILL_DELETED', 'SKILL', `Deleted skill ${skill.name}`);
    saveState(state);
  },

  // Applications
  getApplications(): Application[] {
    return loadState().applications;
  },

  getApplicationById(id: string): Application | undefined {
    return loadState().applications.find((a) => a.id === id);
  },

  getApplicationsByStudent(studentId: string): Application[] {
    return loadState().applications.filter((a) => a.studentId === studentId);
  },

  getApplicationsByDrive(driveId: string): Application[] {
    return loadState().applications.filter((a) => a.driveId === driveId);
  },

  applyForDrive(student: Student, drive: PlacementDrive, actorName: string): Application {
    const state = loadState();
    const existing = state.applications.find(
      (a) => a.studentId === student.id && a.driveId === drive.id
    );
    if (existing) {
      if (existing.currentStage === 'WITHDRAWN') {
        // Re-apply
        existing.currentStage = 'APPLICATION_SUBMITTED';
        existing.stageHistory.push({
          stage: 'APPLICATION_SUBMITTED',
          timestamp: new Date().toISOString(),
          updatedBy: actorName,
          notes: 'Re-submitted application',
        });
        saveState(state);
        this.logActivity('usr-sys', actorName, 'APPLICATION_RESUBMITTED', 'APPLICATION', `${student.fullName} re-applied for ${drive.companyName} (${drive.jobRole})`);
        return existing;
      }
      throw new Error(`You have already submitted an application for ${drive.companyName} (${drive.jobRole}). Current stage: ${existing.currentStage}`);
    }

    const newApp: Application = {
      id: `app-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
      driveId: drive.id,
      studentId: student.id,
      studentRollNumber: student.rollNumber,
      studentName: student.fullName,
      studentBranch: student.branch,
      studentCgpa: student.cgpa,
      companyId: drive.companyId,
      companyName: drive.companyName,
      jobRole: drive.jobRole,
      currentStage: 'APPLICATION_SUBMITTED',
      stageHistory: [
        {
          stage: 'APPLICATION_SUBMITTED',
          timestamp: new Date().toISOString(),
          updatedBy: actorName,
          notes: 'Initial application received via portal',
        },
      ],
      appliedAt: new Date().toISOString(),
    };

    state.applications.unshift(newApp);
    this.logActivity('usr-sys', actorName, 'APPLICATION_SUBMITTED', 'APPLICATION', `${student.fullName} applied for ${drive.companyName} - ${drive.jobRole}`);
    saveState(state);
    return newApp;
  },

  withdrawApplication(appId: string, actorName: string, reason?: string): void {
    const state = loadState();
    const app = state.applications.find((a) => a.id === appId);
    if (!app) throw new Error('Application not found');

    if (['SELECTED', 'OFFERED', 'JOINED'].includes(app.currentStage)) {
      throw new Error('Cannot withdraw application once offer has been extended or accepted.');
    }

    app.currentStage = 'WITHDRAWN';
    app.stageHistory.push({
      stage: 'WITHDRAWN',
      timestamp: new Date().toISOString(),
      updatedBy: actorName,
      notes: reason || 'Application withdrawn by student',
    });

    this.logActivity('usr-sys', actorName, 'APPLICATION_WITHDRAWN', 'APPLICATION', `Application withdrawn for ${app.companyName}`);
    saveState(state);
  },

  updateApplicationStage(
    appId: string,
    newStage: ApplicationStage,
    updatedBy: string,
    notes?: string,
    optionalOfferLpa?: number,
    rejectionReason?: string
  ): void {
    const state = loadState();
    const app = state.applications.find((a) => a.id === appId);
    if (!app) throw new Error('Application not found');

    app.currentStage = newStage;
    if (optionalOfferLpa) {
      app.finalOfferLpa = optionalOfferLpa;
    }
    if (rejectionReason) {
      app.rejectionReason = rejectionReason;
    }

    app.stageHistory.push({
      stage: newStage,
      timestamp: new Date().toISOString(),
      updatedBy,
      notes: notes || `Stage updated to ${newStage}`,
    });

    // If placed / joined, update student's placement status
    if (newStage === 'JOINED' || newStage === 'OFFERED') {
      const student = state.students.find((s) => s.id === app.studentId);
      if (student) {
        student.placementStatus = 'PLACED';
        student.placedCompanyId = app.companyId;
        student.placedPackageLpa = optionalOfferLpa || app.finalOfferLpa || 7.0;
      }
    } else if (newStage === 'SHORTLISTED') {
      const student = state.students.find((s) => s.id === app.studentId);
      if (student && student.placementStatus === 'UNPLACED') {
        student.placementStatus = 'SHORTLISTED';
      }
    }

    this.logActivity('usr-sys', updatedBy, 'APPLICATION_STAGE_CHANGED', 'APPLICATION', `Candidate ${app.studentName} updated to stage: ${newStage} (${app.companyName})`);
    saveState(state);
  },

  // Users
  getUsers(): User[] {
    return loadState().users;
  },

  getUserByUsername(identifier: string): User | undefined {
    const term = identifier.trim().toLowerCase();
    return loadState().users.find(
      (u) =>
        u.username.toLowerCase() === term ||
        u.email.toLowerCase() === term ||
        (u.studentRollNumber && u.studentRollNumber.toLowerCase() === term) ||
        u.fullName.toLowerCase() === term ||
        u.fullName.toLowerCase().replace(/^(dr\.|mr\.|prof\.|mrs\.|ms\.)\s+/i, '') === term
    );
  },

  saveUser(user: User, actorName = 'System'): void {
    const state = loadState();
    const index = state.users.findIndex(
      (u) => u.id === user.id || u.username.toLowerCase() === user.username.toLowerCase()
    );
    if (index === -1) {
      state.users.push(user);
      this.logActivity('usr-sys', actorName, 'USER_CREATED', 'AUTH', `Created user ${user.username} with role ${user.role}`);
    } else {
      state.users[index] = { ...state.users[index], ...user };
      this.logActivity('usr-sys', actorName, 'USER_UPDATED', 'AUTH', `Updated user ${user.username}`);
    }
    saveState(state);
  },

  deleteUser(userId: string, actorName = 'System'): void {
    const state = loadState();
    const user = state.users.find((u) => u.id === userId);
    if (!user) return;

    if (user.role === 'ADMIN' && state.users.filter((u) => u.role === 'ADMIN').length <= 1) {
      throw new Error('Cannot delete the last remaining Administrator account.');
    }

    state.users = state.users.filter((u) => u.id !== userId);
    this.logActivity('usr-sys', actorName, 'USER_DELETED', 'AUTH', `Deleted account ${user.username}`);
    saveState(state);
  },

  // Verification of skills
  verifyStudentSkill(studentId: string, skillId: string, facultyId: string, facultyName: string): void {
    const state = loadState();
    const student = state.students.find((s) => s.id === studentId);
    if (!student) throw new Error('Student not found');

    const skillEntry = student.skills.find((sk) => sk.skillId === skillId);
    if (!skillEntry) throw new Error('Skill not found in student profile');

    skillEntry.verifiedByFaculty = true;
    skillEntry.verifiedByFacultyId = facultyId;
    skillEntry.verifiedAt = new Date().toISOString().split('T')[0];

    const skillObj = state.skills.find((s) => s.id === skillId);
    const skillName = skillObj ? skillObj.name : skillId;

    this.logActivity('usr-sys', facultyName, 'SKILL_VERIFIED', 'SKILL', `Verified proficiency for ${student.fullName} in ${skillName}`);
    saveState(state);
  },

  // Per-user Unified Profile Store (Standby fallback & local reactive cache)
  getUnifiedProfile(targetUser: User): UnifiedUserProfile {
    const state = loadState();
    const map = state.userProfiles || {};
    const stored = map[targetUser.id];
    const latestUser = state.users.find((u) => u.id === targetUser.id || u.username.toLowerCase() === targetUser.username.toLowerCase()) || targetUser;

    let student: Student | null = null;
    if (latestUser.role === 'STUDENT') {
      const roll = latestUser.studentRollNumber || latestUser.username;
      student = state.students.find((s) => s.rollNumber.toLowerCase() === roll.toLowerCase()) || stored?.student || null;
    }

    let company: Partial<Company> | null = stored?.company || null;
    if (latestUser.role === 'COMPANY') {
      const matchedComp =
        state.companies.find((c) => c.id === latestUser.companyId) ||
        state.companies.find((c) => c.hrEmail?.toLowerCase() === latestUser.email?.toLowerCase()) ||
        state.companies.find((c) => c.name.toLowerCase().includes('tcs') && latestUser.username.toLowerCase().includes('tcs'));
      if (matchedComp) {
        company = { ...matchedComp, ...(stored?.company || {}) };
      } else if (!company) {
        company = {
          id: latestUser.companyId || 'comp-custom',
          name: latestUser.companyName || latestUser.department || 'Corporate Partner',
          industry: 'IT Services',
          website: '',
          location: 'Hyderabad, Telangana',
          description: '',
          recruiterName: latestUser.fullName,
          recruiterDesignation: latestUser.designation || 'Campus Recruiter',
          hrName: latestUser.fullName,
          hrEmail: latestUser.email,
          hrPhone: latestUser.phone || '',
          approvalStatus: 'ACTIVE',
        };
      }
    }

    let faculty = stored?.faculty || null;
    if (latestUser.role === 'FACULTY' && !faculty) {
      faculty = {
        facultyId: `GCET-${latestUser.username.toUpperCase()}`,
        department: latestUser.department || 'CSE',
        designation: latestUser.designation || 'Faculty Coordinator',
        qualifications: '',
        areasOfExpertise: [],
        subjectsHandled: [],
        researchInterests: '',
        facultyApproved: true,
        adminApproved: true,
        status: latestUser.status || 'ACTIVE',
        publications: [],
        certifications: [],
      };
    }

    const baseUser: User = {
      ...latestUser,
      ...(stored?.user || {}),
      id: latestUser.id,
      username: latestUser.username,
      role: latestUser.role,
    };

    const socialLinks = stored?.socialLinks || student?.socialLinks || {};
    const customLinks = stored?.customLinks || student?.customLinks || [];
    const mergedStudent = student
      ? {
          ...student,
          ...(stored?.student || {}),
          rollNumber: student.rollNumber,
          branch: student.branch,
          approvalStatus: student.approvalStatus,
          placementStatus: student.placementStatus,
        }
      : null;

    const result: UnifiedUserProfile = {
      user: baseUser,
      socialLinks,
      customLinks,
      student: mergedStudent,
      faculty,
      company,
      completionPercentage: 0,
      missingSections: [],
      isOwnProfile: true,
    };
    const comp = computeLocalProfileCompletion(result);
    result.completionPercentage = comp.percentage;
    result.missingSections = comp.missingSections;
    return result;
  },

  saveUnifiedProfile(targetUser: User, profile: UnifiedUserProfile): UnifiedUserProfile {
    const state = loadState();
    if (!state.userProfiles) state.userProfiles = {};

    const cleanProfile: UnifiedUserProfile = {
      ...profile,
      user: {
        ...targetUser,
        ...profile.user,
        id: targetUser.id,
        username: targetUser.username,
        role: targetUser.role, // protected
        status: targetUser.status, // protected
        isActive: targetUser.isActive, // protected
      },
    };
    const comp = computeLocalProfileCompletion(cleanProfile);
    cleanProfile.completionPercentage = comp.percentage;
    cleanProfile.missingSections = comp.missingSections;
    state.userProfiles[targetUser.id] = cleanProfile;

    // Sync to users list
    const uIdx = state.users.findIndex((u) => u.id === targetUser.id || u.username.toLowerCase() === targetUser.username.toLowerCase());
    if (uIdx !== -1) {
      state.users[uIdx] = {
        ...state.users[uIdx],
        fullName: cleanProfile.user.fullName,
        email: cleanProfile.user.email,
        phone: cleanProfile.user.phone,
        designation: cleanProfile.user.designation,
        location: cleanProfile.user.location,
        address: cleanProfile.user.address,
        aboutMe: cleanProfile.user.aboutMe,
        profilePhoto: cleanProfile.user.profilePhoto,
      };
    }

    // Sync to student record if student
    if (targetUser.role === 'STUDENT' && cleanProfile.student) {
      const roll = (targetUser.studentRollNumber || targetUser.username).toLowerCase();
      const sIdx = state.students.findIndex((s) => s.id === cleanProfile.student!.id || s.rollNumber.toLowerCase() === roll);
      if (sIdx !== -1) {
        const existing = state.students[sIdx];
        const mergedStudent: Student = {
          ...existing,
          ...cleanProfile.student,
          // Protect verified institutional fields
          id: existing.id,
          rollNumber: existing.rollNumber,
          branch: existing.branch,
          approvalStatus: existing.approvalStatus,
          placementStatus: existing.placementStatus,
          placedCompanyId: existing.placedCompanyId,
          placedPackageLpa: existing.placedPackageLpa,
          fullName: cleanProfile.user.fullName,
          email: cleanProfile.user.email,
          phone: cleanProfile.user.phone || existing.phone,
          location: cleanProfile.user.location,
          address: cleanProfile.user.address,
          aboutMe: cleanProfile.user.aboutMe,
          profilePhoto: cleanProfile.user.profilePhoto,
          socialLinks: cleanProfile.socialLinks,
          customLinks: cleanProfile.customLinks,
        };
        state.students[sIdx] = mergedStudent;
        cleanProfile.student = mergedStudent;
      }
    }

    // Sync protected faculty institutional fields
    if (targetUser.role === 'FACULTY' && cleanProfile.faculty) {
      const prevFac = state.userProfiles[targetUser.id]?.faculty;
      cleanProfile.faculty = {
        ...cleanProfile.faculty,
        department: targetUser.department || prevFac?.department || cleanProfile.faculty.department,
        facultyApproved: prevFac?.facultyApproved ?? cleanProfile.faculty.facultyApproved,
        adminApproved: prevFac?.adminApproved ?? cleanProfile.faculty.adminApproved,
        status: targetUser.status || prevFac?.status || cleanProfile.faculty.status,
      };
    }

    // Sync to company record if recruiter
    if (targetUser.role === 'COMPANY' && cleanProfile.company) {
      const cIdx = state.companies.findIndex(
        (c) => c.id === cleanProfile.company!.id || c.id === targetUser.companyId
      );
      if (cIdx !== -1) {
        const existing = state.companies[cIdx];
        const mergedCompany: Company = {
          ...existing,
          name: cleanProfile.company.name || existing.name,
          industry: cleanProfile.company.industry || existing.industry,
          website: cleanProfile.company.website ?? existing.website,
          location: cleanProfile.company.location ?? cleanProfile.user.location ?? existing.location,
          description: cleanProfile.company.description ?? existing.description,
          hrName: cleanProfile.company.recruiterName || cleanProfile.user.fullName,
          hrEmail: cleanProfile.user.email,
          hrPhone: cleanProfile.user.phone || existing.hrPhone,
          recruiterName: cleanProfile.company.recruiterName || cleanProfile.user.fullName,
          recruiterDesignation: cleanProfile.company.recruiterDesignation || cleanProfile.user.designation,
          companyLogo: cleanProfile.company.companyLogo ?? existing.companyLogo,
          linkedinUrl: cleanProfile.socialLinks?.linkedin || cleanProfile.company.linkedinUrl || existing.linkedinUrl,
          approvalStatus: existing.approvalStatus,
          tier: existing.tier,
        };
        state.companies[cIdx] = mergedCompany;
        cleanProfile.company = mergedCompany;
      }
    }

    state.userProfiles[targetUser.id] = cleanProfile;
    this.logActivity(targetUser.id, cleanProfile.user.fullName, 'PROFILE_UPDATED', 'AUTH', `Updated profile for ${cleanProfile.user.fullName} (${targetUser.role})`);
    saveState(state);
    return cleanProfile;
  },

  // Reset & Backup
  resetToDefault(): void {
    const initial: DbState = {
      students: INITIAL_STUDENTS,
      companies: INITIAL_COMPANIES,
      drives: INITIAL_DRIVES,
      skills: INITIAL_SKILLS,
      applications: INITIAL_APPLICATIONS,
      users: INITIAL_USERS,
      activityLogs: INITIAL_ACTIVITY_LOGS,
    };
    saveState(initial);
  },

  exportDatabase(): string {
    return JSON.stringify(loadState(), null, 2);
  },

  importDatabase(jsonStr: string): void {
    try {
      const parsed = JSON.parse(jsonStr) as DbState;
      if (!parsed.students || !parsed.companies || !parsed.drives || !parsed.users) {
        throw new Error('Invalid database backup structure.');
      }
      saveState(parsed);
    } catch (e: any) {
      throw new Error('Failed to import database: ' + (e.message || 'Invalid JSON'));
    }
  },
};

export function computeLocalProfileCompletion(profile: UnifiedUserProfile): { percentage: number; missingSections: string[] } {
  const u = profile.user;
  const checks: { label: string; ok: boolean }[] = [
    { label: 'Profile Photo', ok: Boolean(u.profilePhoto || profile.company?.companyLogo) },
    { label: 'Full Name', ok: Boolean(u.fullName?.trim()) },
    { label: 'Email Address', ok: Boolean(u.email?.trim()) },
    { label: 'Phone Number', ok: Boolean(u.phone?.trim()) },
    { label: 'Location', ok: Boolean(u.location?.trim()) },
    { label: 'About Me / Bio', ok: Boolean(u.aboutMe?.trim()) },
  ];

  const hasSocial =
    Object.values(profile.socialLinks || {}).some((v) => Boolean(v && String(v).trim())) ||
    (profile.customLinks?.length || 0) > 0;
  checks.push({ label: 'Social / Professional Link', ok: hasSocial });

  if (u.role === 'STUDENT' && profile.student) {
    const st = profile.student;
    checks.push({ label: 'Date of Birth / Address', ok: Boolean(st.dob || u.address || st.address) });
    checks.push({ label: 'Academic Metrics & Semester', ok: Boolean(st.cgpa > 0 && (st.section || 'A') && (st.currentSemester || 1)) });
    const hasSkills =
      (st.skills?.length || 0) > 0 ||
      (st.programmingLanguages?.length || 0) > 0 ||
      (st.frameworksTechnologies?.length || 0) > 0;
    checks.push({ label: 'Technical Skills', ok: hasSkills });
    checks.push({ label: 'Projects', ok: (st.projects?.length || 0) > 0 });
    checks.push({ label: 'Certifications', ok: (st.certifications?.length || 0) > 0 });
    checks.push({ label: 'Career Interests & Roles', ok: (st.careerInterests?.length || 0) > 0 || (st.preferredJobRoles?.length || 0) > 0 });
  } else if (u.role === 'FACULTY' && profile.faculty) {
    const f = profile.faculty;
    checks.push({ label: 'Qualifications', ok: Boolean(f.qualifications?.trim()) });
    checks.push({ label: 'Areas of Expertise', ok: (f.areasOfExpertise?.length || 0) > 0 });
    checks.push({ label: 'Subjects Handled', ok: (f.subjectsHandled?.length || 0) > 0 });
    checks.push({ label: 'Research Interests', ok: Boolean(f.researchInterests?.trim()) });
    checks.push({ label: 'Publications / Certifications', ok: (f.publications?.length || 0) > 0 || (f.certifications?.length || 0) > 0 });
  } else if (u.role === 'COMPANY' && profile.company) {
    const c = profile.company;
    checks.push({ label: 'Company Name & Industry', ok: Boolean(c.name?.trim() && c.industry?.trim()) });
    checks.push({ label: 'Company Website', ok: Boolean(c.website?.trim()) });
    checks.push({ label: 'Company Description', ok: Boolean(c.description?.trim()) });
    checks.push({ label: 'Company Logo', ok: Boolean(c.companyLogo?.trim()) });
  }

  const completed = checks.filter((c) => c.ok).length;
  return {
    percentage: Math.round((completed / Math.max(1, checks.length)) * 100),
    missingSections: checks.filter((c) => !c.ok).map((c) => c.label),
  };
}
