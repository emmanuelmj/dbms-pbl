import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Lock,
  User as UserIcon,
  Eye,
  EyeOff,
  Building2,
  ShieldCheck,
  GraduationCap,
  UserCheck,
  ArrowRight,
  Database,
  X,
  AlertCircle,
  CheckCircle2,
  UserPlus,
  Check,
  Sparkles,
  ArrowLeft,
  ChevronRight,
  Briefcase,
  Layers,
  Award,
} from 'lucide-react';
import { User, Branch } from '../types';
import { authService } from '../services/auth';
import { GCET_OFFICIAL_LOGO } from '../assets/campusImages';

export type AuthPortalRole = 'ADMIN' | 'FACULTY' | 'STUDENTS' | 'COMPANIES';
export type SubViewMode = 'LOGIN' | 'REGISTER';

interface LoginPageProps {
  onLoginSuccess: (user: User) => void;
  onClose?: () => void;
  onBackToWelcome?: () => void;
  canDismiss?: boolean;
  initialRole?: AuthPortalRole;
  initialSubView?: SubViewMode;
}

interface AdminOption {
  username: string;
  roleTitle: string;
  department: string;
}

interface FacultyOption {
  username: string;
  roleTitle: string;
  dept: string;
}

interface RecruiterOption {
  username: string;
  roleTitle: string;
  dept: string;
}

const ADMIN_OPTIONS: AdminOption[] = [
  { username: 'principal.sagar', roleTitle: 'Principal (Executive Authority)', department: 'Executive Approvals & Leadership' },
  { username: 'vc.harishchandra', roleTitle: 'Vice Chairman', department: 'Institutional Governance' },
  { username: 'chairman.ravinder', roleTitle: 'Chairman', department: 'Governing Body Leadership' },
  { username: 'admin', roleTitle: 'Placement System Admin', department: 'System Infrastructure' },
  { username: 'officer', roleTitle: 'Head of Training & Placements', department: 'Placement Cell Leadership' },
  { username: 'placement_officer', roleTitle: 'Placement Officer', department: 'Drive Coordination & Scheduling' },
];

const FACULTY_OPTIONS: FacultyOption[] = [
  { username: 'faculty_lavanya', roleTitle: 'B. Laxmi Lavanya · CSE Coordinator', dept: 'CSE & Emerging Tech (AIML/DS/CS)' },
  { username: 'faculty_aparna', roleTitle: 'P. Aparna · ECE Coordinator', dept: 'Electronics & Communication' },
  { username: 'faculty_rajashakar', roleTitle: 'S. Raja Shakar Reddy · EEE Coordinator', dept: 'Electrical & Electronics' },
  { username: 'faculty_cse', roleTitle: 'Dr. G. Neeraja · Faculty Mentor', dept: 'Computer Science Department' },
  { username: 'placement_officer', roleTitle: 'Prof. B. Ravinder Reddy · TPO', dept: 'Training & Placement Cell' },
];

const RECRUITER_OPTIONS: RecruiterOption[] = [
  { username: 'recruiter_tcs', roleTitle: 'Tier-1 Partner (TCS Lead)', dept: 'Campus Drives & Shortlists' },
];

const GCET_BRANCHES: Branch[] = [
  'CSE',
  'CSE - AIML',
  'CSE - DS',
  'CSE - Cyber Security',
  'IT',
  'ECE',
  'EEE',
  'MECH',
  'CIVIL',
];

const AVAILABLE_SKILLS = [
  'Java',
  'Python',
  'C++',
  'SQL',
  'React',
  'Data Structures',
  'Algorithms',
  'Spring Boot',
  'Machine Learning',
  'Cloud Computing',
  'Web Development',
  'DevOps',
];

export const LoginPage: React.FC<LoginPageProps> = ({
  onLoginSuccess,
  onClose,
  onBackToWelcome,
  canDismiss = true,
  initialRole = 'ADMIN',
  initialSubView = 'LOGIN',
}) => {
  const [activeRole, setActiveRole] = useState<AuthPortalRole>(initialRole);
  const [subView, setSubView] = useState<SubViewMode>(initialSubView);

  // Common Login fields
  const [username, setUsername] = useState('principal.sagar');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);

  // Quick preset selections
  const [selectedAdmin, setSelectedAdmin] = useState<string>('principal.sagar');
  const [selectedFaculty, setSelectedFaculty] = useState<string>('faculty_lavanya');
  const [selectedRecruiter, setSelectedRecruiter] = useState<string>('recruiter_tcs');

  // Student Registration Form State
  const [stuFullName, setStuFullName] = useState('');
  const [stuRollNumber, setStuRollNumber] = useState('');
  const [stuBranch, setStuBranch] = useState<Branch>('CSE');
  const [stuAcademicYear, setStuAcademicYear] = useState<number>(4);
  const [stuGradYear, setStuGradYear] = useState<number>(2026);
  const [stuCgpa, setStuCgpa] = useState('8.40');
  const [stuEmail, setStuEmail] = useState('');
  const [stuPhone, setStuPhone] = useState('');
  const [stuPassword, setStuPassword] = useState('');
  const [stuConfirmPassword, setStuConfirmPassword] = useState('');
  const [stuSkills, setStuSkills] = useState<string[]>(['Java', 'SQL', 'Data Structures']);
  const [stuRegError, setStuRegError] = useState('');
  const [stuRegSuccess, setStuRegSuccess] = useState<{ rollNumber: string; fullName: string } | null>(null);
  const [stuRegLoading, setStuRegLoading] = useState(false);

  // Faculty Registration Form State
  const [facFullName, setFacFullName] = useState('');
  const [facEmployeeId, setFacEmployeeId] = useState('');
  const [facEmail, setFacEmail] = useState('');
  const [facDepartment, setFacDepartment] = useState('CSE');
  const [facDesignation, setFacDesignation] = useState('Assistant Professor');
  const [facPhone, setFacPhone] = useState('');
  const [facPassword, setFacPassword] = useState('');
  const [facConfirmPassword, setFacConfirmPassword] = useState('');
  const [facRegError, setFacRegError] = useState('');
  const [facRegSuccess, setFacRegSuccess] = useState<{ employeeId: string; fullName: string } | null>(null);
  const [facRegLoading, setFacRegLoading] = useState(false);

  // Company Registration Form State
  const [compName, setCompName] = useState('');
  const [compIndustry, setCompIndustry] = useState('IT / Software Services');
  const [compWebsite, setCompWebsite] = useState('');
  const [compLocation, setCompLocation] = useState('Hyderabad, Telangana');
  const [compHrName, setCompHrName] = useState('');
  const [compHrEmail, setCompHrEmail] = useState('');
  const [compHrPhone, setCompHrPhone] = useState('');
  const [compTier, setCompTier] = useState<'Tier 1 (Dream)' | 'Tier 2 (Super Dream)' | 'Core' | 'Standard IT'>('Standard IT');
  const [compPassword, setCompPassword] = useState('');
  const [compConfirmPassword, setCompConfirmPassword] = useState('');
  const [compRegError, setCompRegError] = useState('');
  const [compRegSuccess, setCompRegSuccess] = useState<{ companyName: string; hrName: string } | null>(null);
  const [compRegLoading, setCompRegLoading] = useState(false);

  const autoLoginTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const loginInFlightRef = useRef(false);
  const lastAttemptedCredsRef = useRef<string | null>(null);

  const cancelAutoLoginTimer = useCallback(() => {
    if (autoLoginTimerRef.current) {
      clearTimeout(autoLoginTimerRef.current);
      autoLoginTimerRef.current = null;
    }
  }, []);

  const clearAllPasswordFields = useCallback(() => {
    cancelAutoLoginTimer();
    lastAttemptedCredsRef.current = null;
    setPassword('');
    setShowPassword(false);
    setStuPassword('');
    setStuConfirmPassword('');
    setFacPassword('');
    setFacConfirmPassword('');
    setCompPassword('');
    setCompConfirmPassword('');
  }, [cancelAutoLoginTimer]);

  // Synchronize role/subview switches - Never prefill passwords
  const handleRoleChange = (role: AuthPortalRole, targetSubView: SubViewMode = 'LOGIN') => {
    cancelAutoLoginTimer();
    setActiveRole(role);
    setSubView(targetSubView);
    setLoginError('');
    clearAllPasswordFields();

    if (role === 'ADMIN') {
      setUsername('principal.sagar');
      setSelectedAdmin('principal.sagar');
    } else if (role === 'FACULTY') {
      setUsername(selectedFaculty || 'faculty_lavanya');
    } else if (role === 'STUDENTS') {
      setUsername('');
    } else if (role === 'COMPANIES') {
      setUsername(selectedRecruiter || 'recruiter_tcs');
    }
  };

  const handleSubViewChange = (targetSubView: SubViewMode) => {
    cancelAutoLoginTimer();
    setSubView(targetSubView);
    setLoginError('');
    clearAllPasswordFields();
  };

  useEffect(() => {
    handleRoleChange(initialRole, initialSubView);
  }, [initialRole, initialSubView]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    return () => cancelAutoLoginTimer();
  }, [cancelAutoLoginTimer]);

  // Skill toggle helper
  const toggleStuSkill = (skill: string) => {
    setStuSkills((prev) =>
      prev.includes(skill) ? prev.filter((s) => s !== skill) : [...prev, skill]
    );
  };

  const performLogin = useCallback(
    async (term: string, pwd: string, role: AuthPortalRole) => {
      if (loginInFlightRef.current) return;
      cancelAutoLoginTimer();
      loginInFlightRef.current = true;
      lastAttemptedCredsRef.current = `${role}|${term}|${pwd}`;
      setLoginError('');
      setLoginLoading(true);
      try {
        const user = await authService.login(term, pwd, role);
        lastAttemptedCredsRef.current = null;
        setLoginError('');
        setPassword('');
        setShowPassword(false);
        onLoginSuccess(user);
      } catch (err: any) {
        setLoginError(err?.message || 'Incorrect password');
      } finally {
        loginInFlightRef.current = false;
        setLoginLoading(false);
      }
    },
    [cancelAutoLoginTimer, onLoginSuccess]
  );

  // Automatic login ~1.2 seconds after typing stops in the password field
  useEffect(() => {
    cancelAutoLoginTimer();
    if (subView !== 'LOGIN' || loginLoading || loginInFlightRef.current) return;

    const term = username.trim();
    if (!term || !password) return;

    const credKey = `${activeRole}|${term}|${password}`;
    if (lastAttemptedCredsRef.current === credKey) return;

    autoLoginTimerRef.current = setTimeout(() => {
      autoLoginTimerRef.current = null;
      if (!loginInFlightRef.current && subView === 'LOGIN') {
        void performLogin(term, password, activeRole);
      }
    }, 1200);

    return () => cancelAutoLoginTimer();
  }, [activeRole, subView, username, password, loginLoading, cancelAutoLoginTimer, performLogin]);

  // Submit Handler for standard login (Enter key or Login button)
  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    cancelAutoLoginTimer();
    if (loginLoading || loginInFlightRef.current) return; // Prevent duplicate login requests
    setLoginError('');

    const term = username.trim();
    if (!term) {
      if (activeRole === 'STUDENTS') {
        setLoginError('Student Roll Number is required. Enter your registered roll number.');
      } else if (activeRole === 'FACULTY') {
        setLoginError('Faculty ID or username is required.');
      } else if (activeRole === 'COMPANIES') {
        setLoginError('Recruiter corporate email or username is required.');
      } else {
        setLoginError('Administrator username is required.');
      }
      return;
    }

    if (!password) {
      setLoginError('Please enter your password.');
      return;
    }

    lastAttemptedCredsRef.current = null;
    await performLogin(term, password, activeRole);
  };

  // Student Registration Handler
  const handleStudentRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setStuRegError('');
    setStuRegSuccess(null);

    const cleanRoll = stuRollNumber.trim().toUpperCase();
    if (!cleanRoll) {
      setStuRegError('Student Roll Number is required.');
      return;
    }
    if (!stuFullName.trim()) {
      setStuRegError('Full Name is required.');
      return;
    }
    if (!stuEmail.trim()) {
      setStuRegError('Official college or personal email is required.');
      return;
    }
    const parsedCgpa = parseFloat(stuCgpa);
    if (isNaN(parsedCgpa) || parsedCgpa < 0 || parsedCgpa > 10) {
      setStuRegError('CGPA must be a valid number between 0.00 and 10.00.');
      return;
    }
    if (!stuPassword || stuPassword.length < 6) {
      setStuRegError('Password must be at least 6 characters in length.');
      return;
    }
    if (stuPassword !== stuConfirmPassword) {
      setStuRegError('Passwords do not match. Please verify.');
      return;
    }

    setStuRegLoading(true);
    try {
      await authService.registerStudent({
        fullName: stuFullName.trim(),
        rollNumber: cleanRoll,
        branch: stuBranch,
        academicYear: Number(stuAcademicYear),
        graduationYear: Number(stuGradYear),
        cgpa: parsedCgpa,
        email: stuEmail.trim(),
        phone: stuPhone.trim(),
        password: stuPassword,
        skills: stuSkills,
      });

      setStuPassword('');
      setStuConfirmPassword('');
      setStuRegSuccess({
        rollNumber: cleanRoll,
        fullName: stuFullName.trim(),
      });
    } catch (err: any) {
      setStuRegError(err.message || 'Registration failed. Please check your inputs.');
    } finally {
      setStuRegLoading(false);
    }
  };

  // Faculty Registration Handler
  const handleFacultyRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setFacRegError('');
    setFacRegSuccess(null);

    const cleanEmpId = facEmployeeId.trim().toUpperCase();
    if (!facFullName.trim()) {
      setFacRegError('Full Name is required.');
      return;
    }
    if (!cleanEmpId) {
      setFacRegError('Faculty ID / Employee ID is required.');
      return;
    }
    if (!facEmail.trim()) {
      setFacRegError('Official college email is required.');
      return;
    }
    if (!facPassword || facPassword.length < 6) {
      setFacRegError('Password must be at least 6 characters in length.');
      return;
    }
    if (facPassword !== facConfirmPassword) {
      setFacRegError('Passwords do not match.');
      return;
    }

    setFacRegLoading(true);
    try {
      await authService.registerFaculty({
        fullName: facFullName.trim(),
        employeeId: cleanEmpId,
        email: facEmail.trim(),
        department: facDepartment,
        designation: facDesignation,
        phone: facPhone.trim(),
        password: facPassword,
      });

      setFacPassword('');
      setFacConfirmPassword('');
      setFacRegSuccess({
        employeeId: cleanEmpId,
        fullName: facFullName.trim(),
      });
    } catch (err: any) {
      setFacRegError(err.message || 'Faculty registration failed.');
    } finally {
      setFacRegLoading(false);
    }
  };

  // Company Registration Handler
  const handleCompanyRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setCompRegError('');
    setCompRegSuccess(null);

    if (!compName.trim()) {
      setCompRegError('Company Name is required.');
      return;
    }
    if (!compHrName.trim()) {
      setCompRegError('HR / Recruiter contact name is required.');
      return;
    }
    if (!compHrEmail.trim()) {
      setCompRegError('Official corporate email is required.');
      return;
    }
    if (!compPassword || compPassword.length < 6) {
      setCompRegError('Password must be at least 6 characters in length.');
      return;
    }
    if (compPassword !== compConfirmPassword) {
      setCompRegError('Passwords do not match.');
      return;
    }

    setCompRegLoading(true);
    try {
      await authService.registerCompany({
        name: compName.trim(),
        industry: compIndustry,
        website: compWebsite.trim(),
        location: compLocation.trim(),
        hrName: compHrName.trim(),
        hrEmail: compHrEmail.trim(),
        hrPhone: compHrPhone.trim(),
        tier: compTier,
        password: compPassword,
      });

      setCompPassword('');
      setCompConfirmPassword('');
      setCompRegSuccess({
        companyName: compName.trim(),
        hrName: compHrName.trim(),
      });
    } catch (err: any) {
      setCompRegError(err.message || 'Company registration failed.');
    } finally {
      setCompRegLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-[#0b1329]/90 backdrop-blur-md flex items-center justify-center p-3 sm:p-4 overflow-y-auto font-sans selection:bg-cyan-500 selection:text-white">
      <div className="bg-white dark:bg-[#101a35] w-full max-w-4xl rounded-2xl shadow-2xl border border-slate-200 dark:border-slate-800 overflow-hidden my-auto flex flex-col text-slate-800 dark:text-slate-100 transition-all">
        
        {/* TOP BAR WITH BRANDING & RETURN TO PUBLIC PAGE */}
        <div className="bg-gradient-to-r from-[#101a35] via-[#152347] to-[#0c162e] text-white px-5 sm:px-6 py-4 flex items-center justify-between border-b border-slate-800">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-full bg-white p-0.5 border border-cyan-400/40 shadow-md flex items-center justify-center shrink-0 overflow-hidden">
              <img
                src={GCET_OFFICIAL_LOGO}
                alt="Geethanjali College of Engineering and Technology (GCET) Official Logo"
                className="w-full h-full object-contain"
              />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base sm:text-lg font-black tracking-wide text-white">
                  GCET <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-blue-400">PLACEMENT HUB</span>
                </h2>
                <span className="hidden sm:inline-flex text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">
                  Institutional Auth
                </span>
              </div>
              <p className="text-[11px] text-slate-300">
                Geethanjali College of Engineering and Technology · Hyderabad (501301)
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {onBackToWelcome && (
              <button
                onClick={onBackToWelcome}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-300 hover:text-white bg-slate-800/80 hover:bg-slate-700 border border-slate-700 transition-all cursor-pointer"
              >
                <ArrowLeft className="w-3.5 h-3.5 text-cyan-400" />
                <span className="hidden sm:inline">Back to Public Portal</span>
                <span className="sm:hidden">Back</span>
              </button>
            )}
            {canDismiss && onClose && (
              <button
                onClick={onClose}
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                title="Close"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>
        </div>

        {/* 4 PRIMARY ROLE TABS */}
        <div className="bg-slate-100 dark:bg-slate-900/60 p-2 border-b border-slate-200 dark:border-slate-800 grid grid-cols-2 sm:grid-cols-4 gap-1.5">
          {/* TAB 1: ADMIN */}
          <button
            onClick={() => handleRoleChange('ADMIN', 'LOGIN')}
            className={`flex items-center justify-center gap-2 py-2.5 px-3 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeRole === 'ADMIN'
                ? 'bg-white dark:bg-[#152347] text-blue-600 dark:text-cyan-400 shadow-sm border border-slate-200 dark:border-blue-500/40'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-white/60 dark:hover:bg-slate-800/50'
            }`}
          >
            <ShieldCheck className="w-4 h-4 shrink-0" />
            <span>ADMIN</span>
          </button>

          {/* TAB 2: FACULTY */}
          <button
            onClick={() => handleRoleChange('FACULTY', 'LOGIN')}
            className={`flex items-center justify-center gap-2 py-2.5 px-3 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeRole === 'FACULTY'
                ? 'bg-white dark:bg-[#152347] text-emerald-600 dark:text-emerald-400 shadow-sm border border-slate-200 dark:border-emerald-500/40'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-white/60 dark:hover:bg-slate-800/50'
            }`}
          >
            <UserCheck className="w-4 h-4 shrink-0" />
            <span>FACULTY</span>
          </button>

          {/* TAB 3: STUDENTS */}
          <button
            onClick={() => handleRoleChange('STUDENTS', 'LOGIN')}
            className={`flex items-center justify-center gap-2 py-2.5 px-3 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeRole === 'STUDENTS'
                ? 'bg-white dark:bg-[#152347] text-cyan-600 dark:text-cyan-400 shadow-sm border border-slate-200 dark:border-cyan-500/40'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-white/60 dark:hover:bg-slate-800/50'
            }`}
          >
            <GraduationCap className="w-4 h-4 shrink-0" />
            <span>STUDENTS</span>
          </button>

          {/* TAB 4: COMPANIES */}
          <button
            onClick={() => handleRoleChange('COMPANIES', 'LOGIN')}
            className={`flex items-center justify-center gap-2 py-2.5 px-3 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeRole === 'COMPANIES'
                ? 'bg-white dark:bg-[#152347] text-purple-600 dark:text-purple-400 shadow-sm border border-slate-200 dark:border-purple-500/40'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-white/60 dark:hover:bg-slate-800/50'
            }`}
          >
            <Building2 className="w-4 h-4 shrink-0" />
            <span>RECRUITERS</span>
          </button>
        </div>

        {/* SUB-HEADER / MODE TOGGLE (FOR FACULTY, STUDENTS, COMPANIES) */}
        {activeRole !== 'ADMIN' && (
          <div className="bg-slate-50 dark:bg-slate-900/40 px-6 py-2.5 flex items-center justify-between border-b border-slate-200 dark:border-slate-800">
            <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">
              {activeRole === 'STUDENTS' && 'Student Access Gateway'}
              {activeRole === 'FACULTY' && 'Department Coordinator Gateway'}
              {activeRole === 'COMPANIES' && 'Corporate Partner Gateway'}
            </span>

            <div className="inline-flex rounded-lg p-0.5 bg-slate-200 dark:bg-slate-800">
              <button
                type="button"
                onClick={() => handleSubViewChange('LOGIN')}
                className={`px-3 py-1 text-xs font-bold rounded-md transition-all cursor-pointer ${
                  subView === 'LOGIN'
                    ? 'bg-white dark:bg-[#101a35] text-slate-900 dark:text-white shadow'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                Sign In
              </button>
              <button
                type="button"
                onClick={() => handleSubViewChange('REGISTER')}
                className={`px-3 py-1 text-xs font-bold rounded-md transition-all cursor-pointer ${
                  subView === 'REGISTER'
                    ? 'bg-white dark:bg-[#101a35] text-slate-900 dark:text-white shadow'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                New Registration
              </button>
            </div>
          </div>
        )}

        {/* MAIN BODY AREA */}
        <div className="p-6 sm:p-8 max-h-[75vh] overflow-y-auto">

          {/* ============================================================== */}
          {/* 1. ADMIN PORTAL (LOGIN ONLY - LEADERSHIP SELECTOR) */}
          {/* ============================================================== */}
          {activeRole === 'ADMIN' && (
            <div>
              <div className="mb-6">
                <span className="text-[11px] font-bold uppercase tracking-wider text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/60 px-2.5 py-1 rounded-md border border-blue-200 dark:border-blue-900">
                  Institutional Leadership Gateway
                </span>
                <h3 className="text-xl font-bold text-[#101a35] dark:text-white mt-2">
                  Administrator & Executive Sign In
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  Reserved for Principal Dr. K. Sagar, Chairman Mr. G.R. Ravinder Reddy, Vice Chairman Mr. R. Harishchandra Reddy, and Placement System Admin.
                </p>
              </div>

              {/* Leadership Role Picker */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-6">
                {ADMIN_OPTIONS.map((opt) => {
                  const isSelected = selectedAdmin === opt.username;
                  return (
                    <button
                      key={opt.username}
                      type="button"
                      onClick={() => {
                        setSelectedAdmin(opt.username);
                        setUsername(opt.username);
                        setPassword('');
                        setShowPassword(false);
                        setLoginError('');
                      }}
                      className={`text-left p-3.5 rounded-xl border transition-all cursor-pointer ${
                        isSelected
                          ? 'border-blue-500 bg-blue-50/50 dark:bg-blue-950/30 ring-2 ring-blue-500/20'
                          : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-slate-900 dark:text-white">{opt.roleTitle}</span>
                        {isSelected && <Check className="w-4 h-4 text-blue-600 dark:text-blue-400" />}
                      </div>
                      <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">{opt.department}</p>
                      <span className="text-[10px] text-blue-600 dark:text-cyan-400 font-mono mt-1 inline-block">
                        @{opt.username}
                      </span>
                    </button>
                  );
                })}
              </div>

              {/* Login Form */}
              <form onSubmit={handleLoginSubmit} autoComplete="off" className="space-y-4 max-w-lg mx-auto">
                {loginError && (
                  <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-start gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                    <span>{loginError}</span>
                  </div>
                )}

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                    Administrator Username
                  </label>
                  <input
                    type="text"
                    name="gcet-admin-username"
                    autoComplete="off"
                    value={username}
                    onChange={(e) => {
                      setUsername(e.target.value);
                      if (loginError) setLoginError('');
                    }}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-blue-500 outline-none"
                    placeholder="Enter username"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                    Password
                  </label>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      name="gcet-admin-password"
                      autoComplete="new-password"
                      value={password}
                      onChange={(e) => {
                        setPassword(e.target.value);
                        if (loginError) setLoginError('');
                      }}
                      className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-blue-500 outline-none pr-10"
                      placeholder="Enter password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="pt-2">
                  <button
                    type="submit"
                    disabled={loginLoading}
                    className="w-full py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs flex items-center justify-center gap-2 shadow transition-all cursor-pointer disabled:opacity-50"
                  >
                    <ShieldCheck className="w-4 h-4" />
                    {loginLoading ? 'Verifying Credentials...' : 'Authenticate & Access Admin Portal'}
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* ============================================================== */}
          {/* 2. FACULTY PORTAL (LOGIN & REGISTRATION) */}
          {/* ============================================================== */}
          {activeRole === 'FACULTY' && (
            <div>
              {subView === 'LOGIN' ? (
                <div>
                  <div className="mb-6">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/60 px-2.5 py-1 rounded-md border border-emerald-200 dark:border-emerald-900">
                      Faculty & Coordinator Gateway
                    </span>
                    <h3 className="text-xl font-bold text-[#101a35] dark:text-white mt-2">
                      Faculty Coordinator Sign In
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                      Department Coordinators verify student registrations, endorse skill matrix proficiencies, and review cohort readiness.
                    </p>
                  </div>

                  {/* Registered Faculty Presets */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 mb-6">
                    {FACULTY_OPTIONS.slice(0, 3).map((opt) => {
                      const isSelected = selectedFaculty === opt.username;
                      return (
                        <button
                          key={opt.username}
                          type="button"
                          onClick={() => {
                            setSelectedFaculty(opt.username);
                            setUsername(opt.username);
                            setPassword('');
                            setShowPassword(false);
                            setLoginError('');
                          }}
                          className={`text-left p-3 rounded-xl border transition-all cursor-pointer ${
                            isSelected
                              ? 'border-emerald-500 bg-emerald-50/50 dark:bg-emerald-950/30 ring-2 ring-emerald-500/20'
                              : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
                          }`}
                        >
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-slate-900 dark:text-white">{opt.roleTitle}</span>
                            {isSelected && <Check className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />}
                          </div>
                          <p className="text-[10px] text-slate-500 dark:text-slate-400 mt-0.5">{opt.dept}</p>
                          <span className="text-[10px] text-emerald-600 dark:text-emerald-400 font-mono mt-1 inline-block">
                            @{opt.username}
                          </span>
                        </button>
                      );
                    })}
                  </div>

                  <form onSubmit={handleLoginSubmit} autoComplete="off" className="space-y-4 max-w-lg mx-auto">
                    {loginError && (
                      <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-start gap-2">
                        <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                        <span>{loginError}</span>
                      </div>
                    )}

                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Faculty ID or Registered Username
                      </label>
                      <input
                        type="text"
                        name="gcet-faculty-username"
                        autoComplete="off"
                        value={username}
                        onChange={(e) => {
                          setUsername(e.target.value);
                          if (loginError) setLoginError('');
                        }}
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none"
                        placeholder="Enter Faculty ID or username"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Password
                      </label>
                      <div className="relative">
                        <input
                          type={showPassword ? 'text' : 'password'}
                          name="gcet-faculty-password"
                          autoComplete="new-password"
                          value={password}
                          onChange={(e) => {
                            setPassword(e.target.value);
                            if (loginError) setLoginError('');
                          }}
                          className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none pr-10"
                          placeholder="Enter password"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                        >
                          {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="submit"
                        disabled={loginLoading}
                        className="w-full py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs flex items-center justify-center gap-2 shadow transition-all cursor-pointer disabled:opacity-50"
                      >
                        <UserCheck className="w-4 h-4" />
                        {loginLoading ? 'Verifying Faculty Credentials...' : 'Authenticate & Open Faculty Portal'}
                      </button>
                    </div>

                    <div className="text-center pt-2">
                      <button
                        type="button"
                        onClick={() => handleSubViewChange('REGISTER')}
                        className="text-xs text-emerald-600 dark:text-emerald-400 hover:underline font-semibold"
                      >
                        New faculty coordinator? Register your account here →
                      </button>
                    </div>
                  </form>
                </div>
              ) : (
                /* NEW FACULTY REGISTRATION FORM */
                <div>
                  <div className="mb-6">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/60 px-2.5 py-1 rounded-md border border-emerald-200 dark:border-emerald-900">
                      Faculty Registration
                    </span>
                    <h3 className="text-xl font-bold text-[#101a35] dark:text-white mt-2">
                      Register as New Faculty Coordinator
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                      Faculty accounts require official clearance from College Administration before activation.
                    </p>
                  </div>

                  {facRegSuccess ? (
                    <div className="p-6 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-300 dark:border-emerald-800 text-center max-w-lg mx-auto">
                      <div className="w-12 h-12 rounded-full bg-emerald-100 dark:bg-emerald-900/60 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mx-auto mb-3">
                        <CheckCircle2 className="w-6 h-6" />
                      </div>
                      <h4 className="text-base font-bold text-emerald-900 dark:text-emerald-200">
                        Faculty Application Submitted
                      </h4>
                      <p className="text-xs text-emerald-700 dark:text-emerald-300 mt-2 leading-relaxed">
                        Account for <strong>{facRegSuccess.fullName}</strong> ({facRegSuccess.employeeId}) has been registered with status <span className="font-bold uppercase">PENDING_APPROVAL</span>.
                      </p>
                      <div className="mt-4 p-3 rounded-xl bg-white/80 dark:bg-slate-900/80 border border-emerald-200 dark:border-emerald-800 text-[11px] text-slate-600 dark:text-slate-300">
                        Institutional Rule: New faculty registrations route exclusively to the <strong>Admin Approval Center</strong>. Only authorized administrators (Principal / College Administration) may approve your account before you can log in.
                      </div>
                      <div className="mt-6 flex flex-col sm:flex-row items-center justify-center gap-3">
                        <button
                          type="button"
                          onClick={() => {
                            setSubView('LOGIN');
                            setUsername(facRegSuccess.employeeId);
                            setPassword('');
                            setShowPassword(false);
                            setLoginError('');
                          }}
                          className="w-full sm:w-auto px-5 py-2 rounded-xl bg-emerald-600 text-white font-bold text-xs hover:bg-emerald-700 transition"
                        >
                          Go to Faculty Sign In
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setFacFullName('');
                            setFacEmployeeId('');
                            setFacEmail('');
                            setFacPhone('');
                            setFacPassword('');
                            setFacConfirmPassword('');
                            setFacRegSuccess(null);
                          }}
                          className="w-full sm:w-auto px-4 py-2 rounded-xl bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 text-xs font-semibold border border-slate-300 dark:border-slate-700 hover:bg-slate-50"
                        >
                          Register Another Faculty
                        </button>
                      </div>
                    </div>
                  ) : (
                    <form onSubmit={handleFacultyRegister} className="space-y-4 max-w-2xl mx-auto">
                      {facRegError && (
                        <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-start gap-2">
                          <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                          <span>{facRegError}</span>
                        </div>
                      )}

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Full Official Name *
                          </label>
                          <input
                            type="text"
                            required
                            value={facFullName}
                            onChange={(e) => setFacFullName(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none"
                            placeholder="e.g. Dr. K. Sreenivasa Rao"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Faculty Employee ID *
                          </label>
                          <input
                            type="text"
                            required
                            value={facEmployeeId}
                            onChange={(e) => setFacEmployeeId(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none uppercase font-mono"
                            placeholder="e.g. EMP-CSE-204"
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Official College Email *
                          </label>
                          <input
                            type="email"
                            required
                            value={facEmail}
                            onChange={(e) => setFacEmail(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none"
                            placeholder="e.g. sreenivasa.cse@gcet.edu.in"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Contact Phone
                          </label>
                          <input
                            type="tel"
                            value={facPhone}
                            onChange={(e) => setFacPhone(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none"
                            placeholder="+91 98480 12345"
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Academic Department *
                          </label>
                          <select
                            value={facDepartment}
                            onChange={(e) => setFacDepartment(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none"
                          >
                            {GCET_BRANCHES.map((b) => (
                              <option key={b} value={b}>{b}</option>
                            ))}
                            <option value="Science & Humanities">Science & Humanities (S&H)</option>
                          </select>
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Designation *
                          </label>
                          <select
                            value={facDesignation}
                            onChange={(e) => setFacDesignation(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none"
                          >
                            <option value="Assistant Professor">Assistant Professor</option>
                            <option value="Associate Professor">Associate Professor</option>
                            <option value="Professor">Professor</option>
                            <option value="Department Coordinator">Department Placement Coordinator</option>
                            <option value="HOD">Head of the Department (HOD)</option>
                          </select>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Account Password (min. 6 chars) *
                          </label>
                          <input
                            type="password"
                            required
                            autoComplete="new-password"
                            value={facPassword}
                            onChange={(e) => setFacPassword(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none"
                            placeholder="Create password"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Confirm Password *
                          </label>
                          <input
                            type="password"
                            required
                            autoComplete="new-password"
                            value={facConfirmPassword}
                            onChange={(e) => setFacConfirmPassword(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-emerald-500 outline-none"
                            placeholder="Re-enter password"
                          />
                        </div>
                      </div>

                      <div className="pt-2">
                        <button
                          type="submit"
                          disabled={facRegLoading}
                          className="w-full py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs flex items-center justify-center gap-2 shadow transition cursor-pointer disabled:opacity-50"
                        >
                          <UserPlus className="w-4 h-4" />
                          {facRegLoading ? 'Submitting Faculty Registration...' : 'Submit Faculty Registration for Admin Approval'}
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </div>
          )}

          {/* ============================================================== */}
          {/* 3. STUDENT PORTAL (LOGIN & REGISTRATION) */}
          {/* ============================================================== */}
          {activeRole === 'STUDENTS' && (
            <div>
              {subView === 'LOGIN' ? (
                /* STUDENT LOGIN (STRICT 2-FIELD: ROLL NUMBER & PASSWORD) */
                <div>
                  <div className="mb-6">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-cyan-600 dark:text-cyan-400 bg-cyan-50 dark:bg-cyan-950/60 px-2.5 py-1 rounded-md border border-cyan-200 dark:border-cyan-900">
                      Engineering Candidate Portal
                    </span>
                    <h3 className="text-xl font-bold text-[#101a35] dark:text-white mt-2">
                      Registered Student Sign In
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                      Sign in using your registered college roll number and password. Only accounts approved by your Department Faculty Coordinator can access the portal.
                    </p>
                  </div>

                  <form onSubmit={handleLoginSubmit} autoComplete="off" className="space-y-4 max-w-lg mx-auto">
                    {loginError && (
                      <div className="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-start gap-2.5">
                        <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-rose-600" />
                        <span className="leading-relaxed">{loginError}</span>
                      </div>
                    )}

                    {/* FIELD 1: ROLL NUMBER ONLY */}
                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Student Roll Number
                      </label>
                      <input
                        type="text"
                        name="gcet-student-roll"
                        autoComplete="off"
                        value={username}
                        onChange={(e) => {
                          setUsername(e.target.value.toUpperCase());
                          if (loginError) setLoginError('');
                        }}
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none uppercase font-mono tracking-wider"
                        placeholder="Enter your registered roll number (e.g. 22R11A0501)"
                      />
                    </div>

                    {/* FIELD 2: PASSWORD */}
                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Password
                      </label>
                      <div className="relative">
                        <input
                          type={showPassword ? 'text' : 'password'}
                          name="gcet-student-password"
                          autoComplete="new-password"
                          value={password}
                          onChange={(e) => {
                            setPassword(e.target.value);
                            if (loginError) setLoginError('');
                          }}
                          className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none pr-10"
                          placeholder="Enter your password"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                        >
                          {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="submit"
                        disabled={loginLoading}
                        className="w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-md transition-all cursor-pointer disabled:opacity-50"
                      >
                        <GraduationCap className="w-4 h-4" />
                        {loginLoading ? 'Authenticating Candidate...' : 'Authenticate & Sign In'}
                      </button>
                    </div>

                    <div className="text-center pt-2">
                      <button
                        type="button"
                        onClick={() => handleSubViewChange('REGISTER')}
                        className="text-xs text-cyan-600 dark:text-cyan-400 hover:underline font-semibold"
                      >
                        New to GCET Placement Hub? Register as a candidate →
                      </button>
                    </div>
                  </form>
                </div>
              ) : (
                /* NEW STUDENT REGISTRATION FORM */
                <div>
                  <div className="mb-6">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-cyan-600 dark:text-cyan-400 bg-cyan-50 dark:bg-cyan-950/60 px-2.5 py-1 rounded-md border border-cyan-200 dark:border-cyan-900">
                      Candidate Onboarding
                    </span>
                    <h3 className="text-xl font-bold text-[#101a35] dark:text-white mt-2">
                      New Student Registration
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                      Register once with your official college credentials. In accordance with GCET policy, your registration routes to your Department Faculty Coordinator for review.
                    </p>
                  </div>

                  {stuRegSuccess ? (
                    <div className="p-6 rounded-2xl bg-cyan-50 dark:bg-cyan-950/40 border border-cyan-300 dark:border-cyan-800 text-center max-w-lg mx-auto">
                      <div className="w-12 h-12 rounded-full bg-cyan-100 dark:bg-cyan-900/60 text-cyan-600 dark:text-cyan-400 flex items-center justify-center mx-auto mb-3">
                        <CheckCircle2 className="w-6 h-6" />
                      </div>
                      <h4 className="text-base font-bold text-cyan-900 dark:text-cyan-200">
                        Registration Successfully Submitted!
                      </h4>
                      <p className="text-xs text-cyan-800 dark:text-cyan-300 mt-2 leading-relaxed">
                        Account for <strong>{stuRegSuccess.fullName}</strong> ({stuRegSuccess.rollNumber}) has been stored in the central database with status <span className="font-bold uppercase">PENDING_APPROVAL</span>.
                      </p>
                      <div className="mt-4 p-3.5 rounded-xl bg-white/80 dark:bg-slate-900/80 border border-cyan-200 dark:border-cyan-800 text-[11px] text-slate-600 dark:text-slate-300 leading-relaxed text-left">
                        <p className="font-bold text-cyan-950 dark:text-cyan-200 mb-1 flex items-center gap-1.5">
                          <UserCheck className="w-3.5 h-3.5 text-cyan-600" /> Awaiting Faculty Verification
                        </p>
                        Your application is now visible in the <strong>Faculty Approval Center</strong>. Only an authorized Department Faculty Coordinator can verify your details and approve your login. Administrators cannot approve student accounts.
                      </div>
                      <div className="mt-6 flex flex-col sm:flex-row items-center justify-center gap-3">
                        <button
                          type="button"
                          onClick={() => {
                            setSubView('LOGIN');
                            setUsername(stuRegSuccess.rollNumber);
                            setPassword('');
                            setShowPassword(false);
                            setLoginError('');
                          }}
                          className="w-full sm:w-auto px-5 py-2.5 rounded-xl bg-cyan-600 text-white font-bold text-xs hover:bg-cyan-700 transition"
                        >
                          Go to Student Sign In
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setStuFullName('');
                            setStuRollNumber('');
                            setStuEmail('');
                            setStuPhone('');
                            setStuPassword('');
                            setStuConfirmPassword('');
                            setStuRegSuccess(null);
                          }}
                          className="w-full sm:w-auto px-4 py-2.5 rounded-xl bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 text-xs font-semibold border border-slate-300 dark:border-slate-700 hover:bg-slate-50"
                        >
                          Register Another Student
                        </button>
                      </div>
                    </div>
                  ) : (
                    <form onSubmit={handleStudentRegister} className="space-y-4 max-w-2xl mx-auto">
                      {stuRegError && (
                        <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-start gap-2">
                          <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                          <span>{stuRegError}</span>
                        </div>
                      )}

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Full Student Name *
                          </label>
                          <input
                            type="text"
                            required
                            value={stuFullName}
                            onChange={(e) => setStuFullName(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                            placeholder="e.g. Rahul Sharma"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Student Roll Number *
                          </label>
                          <input
                            type="text"
                            required
                            value={stuRollNumber}
                            onChange={(e) => setStuRollNumber(e.target.value.toUpperCase())}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none uppercase font-mono tracking-wider"
                            placeholder="e.g. 22R11A0501"
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            College / Contact Email *
                          </label>
                          <input
                            type="email"
                            required
                            value={stuEmail}
                            onChange={(e) => setStuEmail(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                            placeholder="e.g. 22r11a0501@gcet.edu.in"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Contact Phone Number
                          </label>
                          <input
                            type="tel"
                            value={stuPhone}
                            onChange={(e) => setStuPhone(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                            placeholder="+91 98765 43210"
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Branch / Department *
                          </label>
                          <select
                            value={stuBranch}
                            onChange={(e) => setStuBranch(e.target.value as Branch)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                          >
                            {GCET_BRANCHES.map((b) => (
                              <option key={b} value={b}>{b}</option>
                            ))}
                          </select>
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Academic Year *
                          </label>
                          <select
                            value={stuAcademicYear}
                            onChange={(e) => setStuAcademicYear(Number(e.target.value))}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                          >
                            <option value={1}>1st Year (B.Tech)</option>
                            <option value={2}>2nd Year (B.Tech)</option>
                            <option value={3}>3rd Year (B.Tech)</option>
                            <option value={4}>4th Year (B.Tech)</option>
                          </select>
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Current CGPA *
                          </label>
                          <input
                            type="number"
                            step="0.01"
                            min="0"
                            max="10"
                            required
                            value={stuCgpa}
                            onChange={(e) => setStuCgpa(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                            placeholder="8.40"
                          />
                        </div>
                      </div>

                      {/* Technical Skills Selection */}
                      <div>
                        <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1.5">
                          Technical Skills & Competencies (Skill Matrix)
                        </label>
                        <div className="flex flex-wrap gap-1.5">
                          {AVAILABLE_SKILLS.map((sk) => {
                            const isSelected = stuSkills.includes(sk);
                            return (
                              <button
                                key={sk}
                                type="button"
                                onClick={() => toggleStuSkill(sk)}
                                className={`px-2.5 py-1 rounded-lg text-xs font-medium transition cursor-pointer ${
                                  isSelected
                                    ? 'bg-cyan-600 text-white font-semibold'
                                    : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 hover:bg-slate-200'
                                }`}
                              >
                                {isSelected ? `✓ ${sk}` : `+ ${sk}`}
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Account Password (min. 6 chars) *
                          </label>
                          <input
                            type="password"
                            required
                            autoComplete="new-password"
                            value={stuPassword}
                            onChange={(e) => setStuPassword(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                            placeholder="Create password"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Confirm Password *
                          </label>
                          <input
                            type="password"
                            required
                            autoComplete="new-password"
                            value={stuConfirmPassword}
                            onChange={(e) => setStuConfirmPassword(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-cyan-500 outline-none"
                            placeholder="Re-enter password"
                          />
                        </div>
                      </div>

                      <div className="pt-2">
                        <button
                          type="submit"
                          disabled={stuRegLoading}
                          className="w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-md transition cursor-pointer disabled:opacity-50"
                        >
                          <UserPlus className="w-4 h-4" />
                          {stuRegLoading ? 'Submitting Registration...' : 'Submit Student Registration for Faculty Approval'}
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </div>
          )}

          {/* ============================================================== */}
          {/* 4. COMPANIES PORTAL (LOGIN & REGISTRATION) */}
          {/* ============================================================== */}
          {activeRole === 'COMPANIES' && (
            <div>
              {subView === 'LOGIN' ? (
                <div>
                  <div className="mb-6">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-purple-600 dark:text-purple-400 bg-purple-50 dark:bg-purple-950/60 px-2.5 py-1 rounded-md border border-purple-200 dark:border-purple-900">
                      Corporate Recruitment Gateway
                    </span>
                    <h3 className="text-xl font-bold text-[#101a35] dark:text-white mt-2">
                      Recruiter & Company Sign In
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                      Partner companies post on-campus drives, filter candidates by technical skill scores, and manage hiring stages.
                    </p>
                  </div>

                  {/* Registered Partner Preset */}
                  <div className="max-w-md mx-auto mb-6">
                    <button
                      type="button"
                      onClick={() => {
                        setUsername('recruiter_tcs');
                        setPassword('');
                        setShowPassword(false);
                        setLoginError('');
                      }}
                      className="w-full text-left p-3 rounded-xl border border-purple-200 dark:border-purple-900/60 bg-purple-50/40 dark:bg-purple-950/20 hover:bg-purple-50 transition cursor-pointer"
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-purple-900 dark:text-purple-200">
                          Tier-1 Partner (Tata Consultancy Services)
                        </span>
                        <Check className="w-4 h-4 text-purple-600 dark:text-purple-400" />
                      </div>
                      <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">Campus Drives & Shortlists</p>
                      <span className="text-[10px] text-purple-600 dark:text-purple-400 font-mono mt-1 inline-block">
                        @recruiter_tcs
                      </span>
                    </button>
                  </div>

                  <form onSubmit={handleLoginSubmit} autoComplete="off" className="space-y-4 max-w-lg mx-auto">
                    {loginError && (
                      <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-start gap-2">
                        <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                        <span>{loginError}</span>
                      </div>
                    )}

                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Corporate HR Email or Username
                      </label>
                      <input
                        type="text"
                        name="gcet-recruiter-username"
                        autoComplete="off"
                        value={username}
                        onChange={(e) => {
                          setUsername(e.target.value);
                          if (loginError) setLoginError('');
                        }}
                        className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                        placeholder="Enter corporate email or username"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                        Password
                      </label>
                      <div className="relative">
                        <input
                          type={showPassword ? 'text' : 'password'}
                          name="gcet-recruiter-password"
                          autoComplete="new-password"
                          value={password}
                          onChange={(e) => {
                            setPassword(e.target.value);
                            if (loginError) setLoginError('');
                          }}
                          className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none pr-10"
                          placeholder="Enter password"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                        >
                          {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </div>

                    <div className="pt-2">
                      <button
                        type="submit"
                        disabled={loginLoading}
                        className="w-full py-2.5 px-4 rounded-xl bg-purple-600 hover:bg-purple-700 text-white font-bold text-xs flex items-center justify-center gap-2 shadow transition-all cursor-pointer disabled:opacity-50"
                      >
                        <Building2 className="w-4 h-4" />
                        {loginLoading ? 'Verifying Recruiter...' : 'Authenticate & Open Recruiter Portal'}
                      </button>
                    </div>

                    <div className="text-center pt-2">
                      <button
                        type="button"
                        onClick={() => handleSubViewChange('REGISTER')}
                        className="text-xs text-purple-600 dark:text-purple-400 hover:underline font-semibold"
                      >
                        New recruiting partner? Register your company here →
                      </button>
                    </div>
                  </form>
                </div>
              ) : (
                /* NEW COMPANY REGISTRATION FORM */
                <div>
                  <div className="mb-6">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-purple-600 dark:text-purple-400 bg-purple-50 dark:bg-purple-950/60 px-2.5 py-1 rounded-md border border-purple-200 dark:border-purple-900">
                      Corporate Onboarding
                    </span>
                    <h3 className="text-xl font-bold text-[#101a35] dark:text-white mt-2">
                      Register as Corporate Recruiting Partner
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                      All recruiting organizations are reviewed and approved exclusively by College Administration before campus drives can be announced.
                    </p>
                  </div>

                  {compRegSuccess ? (
                    <div className="p-6 rounded-2xl bg-purple-50 dark:bg-purple-950/40 border border-purple-300 dark:border-purple-800 text-center max-w-lg mx-auto">
                      <div className="w-12 h-12 rounded-full bg-purple-100 dark:bg-purple-900/60 text-purple-600 dark:text-purple-400 flex items-center justify-center mx-auto mb-3">
                        <CheckCircle2 className="w-6 h-6" />
                      </div>
                      <h4 className="text-base font-bold text-purple-900 dark:text-purple-200">
                        Company Registration Submitted
                      </h4>
                      <p className="text-xs text-purple-800 dark:text-purple-300 mt-2 leading-relaxed">
                        Profile for <strong>{compRegSuccess.companyName}</strong> (HR: {compRegSuccess.hrName}) is stored with status <span className="font-bold uppercase">PENDING_APPROVAL</span>.
                      </p>
                      <div className="mt-4 p-3 rounded-xl bg-white/80 dark:bg-slate-900/80 border border-purple-200 dark:border-purple-800 text-[11px] text-slate-600 dark:text-slate-300 text-left">
                        Institutional Rule: New corporate partners route exclusively to the <strong>Admin Approval Center</strong>. Only authorized administrators (Principal / College Administration) may approve company accounts.
                      </div>
                      <div className="mt-6 flex flex-col sm:flex-row items-center justify-center gap-3">
                        <button
                          type="button"
                          onClick={() => {
                            setSubView('LOGIN');
                            setUsername(compHrEmail);
                            setPassword('');
                            setShowPassword(false);
                            setLoginError('');
                          }}
                          className="w-full sm:w-auto px-5 py-2 rounded-xl bg-purple-600 text-white font-bold text-xs hover:bg-purple-700 transition"
                        >
                          Go to Recruiter Sign In
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setCompName('');
                            setCompHrName('');
                            setCompHrEmail('');
                            setCompHrPhone('');
                            setCompWebsite('');
                            setCompPassword('');
                            setCompConfirmPassword('');
                            setCompRegSuccess(null);
                          }}
                          className="w-full sm:w-auto px-4 py-2 rounded-xl bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 text-xs font-semibold border border-slate-300 dark:border-slate-700 hover:bg-slate-50"
                        >
                          Register Another Company
                        </button>
                      </div>
                    </div>
                  ) : (
                    <form onSubmit={handleCompanyRegister} className="space-y-4 max-w-2xl mx-auto">
                      {compRegError && (
                        <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-start gap-2">
                          <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                          <span>{compRegError}</span>
                        </div>
                      )}

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Company Name *
                          </label>
                          <input
                            type="text"
                            required
                            value={compName}
                            onChange={(e) => setCompName(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                            placeholder="e.g. Infosys Ltd."
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Industry Domain *
                          </label>
                          <select
                            value={compIndustry}
                            onChange={(e) => setCompIndustry(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                          >
                            <option value="IT / Software Services">IT / Software Services</option>
                            <option value="Core Engineering">Core Engineering (Mechanical/Civil/Electrical)</option>
                            <option value="Cloud & AI Solutions">Cloud & AI Solutions</option>
                            <option value="Product Engineering">Product Engineering</option>
                            <option value="Financial Services / FinTech">Financial Services / FinTech</option>
                            <option value="Automotive & Mobility">Automotive & Mobility</option>
                          </select>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Company Website
                          </label>
                          <input
                            type="url"
                            value={compWebsite}
                            onChange={(e) => setCompWebsite(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                            placeholder="https://company.com"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Location / HQ
                          </label>
                          <input
                            type="text"
                            value={compLocation}
                            onChange={(e) => setCompLocation(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                            placeholder="e.g. HITEC City, Hyderabad"
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            HR / Recruiter Name *
                          </label>
                          <input
                            type="text"
                            required
                            value={compHrName}
                            onChange={(e) => setCompHrName(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                            placeholder="e.g. Priya Sharma"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Corporate Email *
                          </label>
                          <input
                            type="email"
                            required
                            value={compHrEmail}
                            onChange={(e) => setCompHrEmail(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                            placeholder="campus@company.com"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Recruitment Tier
                          </label>
                          <select
                            value={compTier}
                            onChange={(e) => setCompTier(e.target.value as any)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                          >
                            <option value="Standard IT">Standard IT</option>
                            <option value="Tier 1 (Dream)">Tier 1 (Dream)</option>
                            <option value="Tier 2 (Super Dream)">Tier 2 (Super Dream)</option>
                            <option value="Core">Core</option>
                          </select>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Account Password (min. 6 chars) *
                          </label>
                          <input
                            type="password"
                            required
                            autoComplete="new-password"
                            value={compPassword}
                            onChange={(e) => setCompPassword(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                            placeholder="Create password"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-bold text-slate-700 dark:text-slate-300 mb-1">
                            Confirm Password *
                          </label>
                          <input
                            type="password"
                            required
                            autoComplete="new-password"
                            value={compConfirmPassword}
                            onChange={(e) => setCompConfirmPassword(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-xs focus:ring-2 focus:ring-purple-500 outline-none"
                            placeholder="Re-enter password"
                          />
                        </div>
                      </div>

                      <div className="pt-2">
                        <button
                          type="submit"
                          disabled={compRegLoading}
                          className="w-full py-2.5 px-4 rounded-xl bg-purple-600 hover:bg-purple-700 text-white font-bold text-xs flex items-center justify-center gap-2 shadow transition cursor-pointer disabled:opacity-50"
                        >
                          <UserPlus className="w-4 h-4" />
                          {compRegLoading ? 'Submitting Company Registration...' : 'Submit Company Registration for Admin Approval'}
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </div>
          )}

        </div>
      </div>
    </div>
  );
};
