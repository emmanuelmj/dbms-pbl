import React, { useState, useEffect, useCallback } from 'react';
import { ThemeProvider } from './context/ThemeContext';
import { HeaderBar } from './components/HeaderBar';
import { Sidebar, NavPageId } from './components/Sidebar';
import { ToastContainer, ToastMessage } from './components/Toast';
import { User, Student, Company, PlacementDrive, Skill, Application } from './types';
import { db, subscribeToDb } from './services/db';
import { authService } from './services/auth';
import { api, DbStatusResponse } from './services/api';

// Pages
import { DashboardPage } from './pages/DashboardPage';
import { StudentsPage } from './pages/StudentsPage';
import { StudentProfilePage } from './pages/StudentProfilePage';
import { SkillMatrixPage } from './pages/SkillMatrixPage';
import { CompaniesPage } from './pages/CompaniesPage';
import { PlacementDrivesPage } from './pages/PlacementDrivesPage';
import { EligibilityCheckerPage } from './pages/EligibilityCheckerPage';
import { SkillGapAnalysisPage } from './pages/SkillGapAnalysisPage';
import { CompanyMatchingPage } from './pages/CompanyMatchingPage';
import { ApplicationsPage } from './pages/ApplicationsPage';
import { ReportsPage } from './pages/ReportsPage';
import { SettingsPage } from './pages/SettingsPage';
import { LoginPage, AuthPortalRole, SubViewMode } from './pages/LoginPage';
import { WelcomePage } from './pages/WelcomePage';
import { CampusConnectPage } from './pages/CampusConnectPage';
import { ApprovalsPage } from './pages/ApprovalsPage';
import { ProfilePage } from './pages/ProfilePage';
import { ForcePasswordChangeModal } from './components/ForcePasswordChangeModal';

const PAGE_TITLES: Record<NavPageId, string> = {
  dashboard: 'Executive Placement Dashboard',
  'campus-connect': 'Campus Connect & GCTC Portal Hub',
  approvals: 'Institutional Approval Center',
  students: 'Student Directory & Records',
  profile: 'My Profile & Account Settings',
  'student-profile': 'Student Placement Profiles',
  skills: 'Technical Skill Matrix',
  companies: 'Corporate Recruitment Partners',
  drives: 'Placement Drives & Opportunities',
  eligibility: 'Automated Eligibility Checker',
  'skill-gap': 'Skill Gap Analysis & Roadmap',
  matching: 'Company Matching Engine',
  applications: 'Applications & Recruitment Stages',
  reports: 'Reports & Institutional Analytics',
  settings: 'Settings, MySQL & Java Package',
};

const getRoleDashboardTitle = (user: User | null): string => {
  if (!user) return 'Executive Placement Dashboard';
  if (user.role === 'CHAIRMAN' || user.username === 'chairman.ravinder' || user.username === 'chairman.gcet') {
    return 'Chairman Dashboard';
  }
  if (user.role === 'VICE_CHAIRMAN' || user.username === 'vc.harishchandra' || user.username === 'vicechairman.gcet') {
    return 'Vice Chairman Dashboard';
  }
  if (user.role === 'PRINCIPAL' || user.username === 'principal.sagar') {
    return 'Principal / Admin Dashboard';
  }
  if (user.role === 'PLACEMENT_OFFICER') {
    return 'Placement Officer Dashboard';
  }
  if (user.role === 'ADMIN' || user.username === 'placement.admin') {
    return 'Placement Admin Dashboard';
  }
  if (user.role === 'FACULTY') {
    return 'Faculty Dashboard';
  }
  if (user.role === 'STUDENT') {
    return 'Student Dashboard';
  }
  if (user.role === 'COMPANY') {
    return 'Recruiter Dashboard';
  }
  return 'Executive Placement Dashboard';
};

export default function App() {
  const [currentUser, setCurrentUser] = useState<User | null>(() => authService.getCurrentUser());
  const [activePage, setActivePage] = useState<NavPageId>('dashboard');
  const [isLoginOpen, setIsLoginOpen] = useState(false);
  const [authPortalModal, setAuthPortalModal] = useState<{
    role: AuthPortalRole;
    subView: SubViewMode;
  } | null>(null);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);

  // Cross-page navigation context IDs
  const [selectedStudentId, setSelectedStudentId] = useState<string | undefined>(undefined);
  const [selectedDriveId, setSelectedDriveId] = useState<string | undefined>(undefined);

  // Reactive DB records & Diagnostics
  const [students, setStudents] = useState<Student[]>(() => db.getStudents());
  const [companies, setCompanies] = useState<Company[]>(() => db.getCompanies());
  const [drives, setDrives] = useState<PlacementDrive[]>(() => db.getDrives());
  const [skills, setSkills] = useState<Skill[]>(() => db.getSkills());
  const [applications, setApplications] = useState<Application[]>(() => db.getApplications());
  const [users, setUsers] = useState<User[]>(() => db.getUsers());
  const [dbStatus, setDbStatus] = useState<DbStatusResponse | null>(null);

  // Toasts
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const addToast = useCallback((type: 'success' | 'error' | 'info', title: string, message?: string) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).substring(2, 5)}`;
    setToasts((prev) => [...prev, { id, type, title, message }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const handleCheckDbHealth = useCallback(async (): Promise<DbStatusResponse> => {
    try {
      const status = await api.getDbStatus();
      const normalized: DbStatusResponse = {
        ...status,
        lastSuccessfulConnectionTime: status.connected ? new Date().toISOString() : status.lastSuccessfulConnectionTime,
      };
      setDbStatus(normalized);
      return normalized;
    } catch (err: any) {
      const fallback: DbStatusResponse = {
        connected: false,
        host: '127.0.0.1',
        port: 3306,
        database: 'gcet_placement_hub',
        user: 'root',
        lastCheckTime: new Date().toISOString(),
        lastError: err?.message || 'Unable to reach backend diagnostics endpoint.',
        lanIp: 'localhost',
      };
      setDbStatus(fallback);
      return fallback;
    }
  }, []);

  const handleRefreshData = useCallback(async () => {
    setStudents(db.getStudents());
    setCompanies(db.getCompanies());
    setDrives(db.getDrives());
    setSkills(db.getSkills());
    setApplications(db.getApplications());
    setUsers(db.getUsers());
    await handleCheckDbHealth().catch(() => {});
  }, [handleCheckDbHealth]);

  // Restore session from token & check DB health on mount
  useEffect(() => {
    authService.restoreSession().then((res) => {
      if (res?.user) {
        setCurrentUser(res.user);
      }
    }).catch(() => {});
    handleCheckDbHealth().catch(() => {});
  }, [handleCheckDbHealth]);

  // Sync DB updates
  useEffect(() => {
    const unsubscribe = subscribeToDb(() => {
      setStudents(db.getStudents());
      setCompanies(db.getCompanies());
      setDrives(db.getDrives());
      setSkills(db.getSkills());
      setApplications(db.getApplications());
      setUsers(db.getUsers());
    });
    return unsubscribe;
  }, []);

  // Handlers for cross-page jumps
  const handleSelectStudentProfile = (studentId: string) => {
    setSelectedStudentId(studentId);
    if (currentUser?.role === 'STUDENT') {
      setActivePage('profile');
    } else {
      setActivePage('student-profile');
    }
  };

  const handleSelectDriveForEligibility = (driveId: string) => {
    setSelectedDriveId(driveId);
    setActivePage('eligibility');
  };

  const handleSelectDriveForApplications = (driveId: string) => {
    setSelectedDriveId(driveId);
    setActivePage('applications');
  };

  // If user is unauthenticated, render Public GCET Welcome Page or chosen auth portal
  if (!currentUser) {
    if (authPortalModal) {
      return (
        <ThemeProvider>
          <LoginPage
            initialRole={authPortalModal.role}
            initialSubView={authPortalModal.subView}
            onLoginSuccess={(user) => {
              setCurrentUser(user);
              setAuthPortalModal(null);
              setActivePage('dashboard');
              addToast('success', 'Authentication Successful', `Welcome, ${user.fullName} [${user.role}]`);
            }}
            onBackToWelcome={() => setAuthPortalModal(null)}
            canDismiss={false}
          />
          <ToastContainer toasts={toasts} onDismiss={dismissToast} />
        </ThemeProvider>
      );
    }

    return (
      <ThemeProvider>
        <WelcomePage
          onOpenPortal={(category, subView) => {
            setAuthPortalModal({ role: category as AuthPortalRole, subView });
          }}
        />
        <ToastContainer toasts={toasts} onDismiss={dismissToast} />
      </ThemeProvider>
    );
  }

  return (
    <ThemeProvider>
      <div className="min-h-screen bg-[#F3F6FB] dark:bg-[#0B1120] flex flex-col text-[#172033] dark:text-slate-100 font-sans antialiased selection:bg-[#2684FF]/20 dark:selection:bg-cyan-900 selection:text-[#2684FF] dark:selection:text-cyan-100 transition-colors duration-200">
        
        {/* Top Institutional Header */}
        <HeaderBar
          currentUser={currentUser}
          dbStatus={dbStatus}
          onCheckDbHealth={handleCheckDbHealth}
          onUserChange={(newUser) => {
            setCurrentUser(newUser);
            if (!newUser) {
              setActivePage('dashboard');
            }
          }}
          onOpenLogin={() => setIsLoginOpen(true)}
          activePageTitle={activePage === 'dashboard' ? getRoleDashboardTitle(currentUser) : (PAGE_TITLES[activePage] || 'Portal')}
        />

        {/* Main Layout: Left Sidebar + Right Content Area */}
        <div className="flex-1 flex overflow-hidden">
          
          {/* Navigation Sidebar */}
          <Sidebar
            activePage={activePage}
            onNavigate={(page) => {
              setActivePage(page);
              // If navigating to students, clear specific selection so list shows
              if (page === 'students') setSelectedStudentId(undefined);
            }}
            userRole={currentUser?.role}
            isCollapsed={isSidebarCollapsed}
            onToggleCollapse={() => setIsSidebarCollapsed((prev) => !prev)}
          />

          {/* Scrollable Work Surface */}
          <main className="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8 max-w-7xl mx-auto w-full">
          {activePage === 'dashboard' && (
            <DashboardPage
              currentUser={currentUser}
              students={students}
              companies={companies}
              drives={drives}
              applications={applications}
              dbStatus={dbStatus}
              onCheckDbHealth={handleCheckDbHealth}
              onRefreshData={handleRefreshData}
              onNavigate={setActivePage}
              onSelectStudentProfile={handleSelectStudentProfile}
              onSelectDrive={handleSelectDriveForEligibility}
            />
          )}

          {activePage === 'campus-connect' && (
            <CampusConnectPage
              currentUser={currentUser}
            />
          )}

          {activePage === 'approvals' && (
            <ApprovalsPage
              currentUser={currentUser}
              students={students}
              companies={companies}
              skills={skills}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}

          {activePage === 'students' && (
            <StudentsPage
              currentUser={currentUser}
              students={students}
              onSelectStudentProfile={handleSelectStudentProfile}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}

          {activePage === 'profile' && (
            <ProfilePage
              currentUser={currentUser}
              skills={skills}
              onUserUpdated={(updatedUser) => setCurrentUser(updatedUser)}
              onShowToast={addToast}
            />
          )}

          {activePage === 'student-profile' && (
            <StudentProfilePage
              currentUser={currentUser}
              students={students}
              skills={skills}
              initialStudentId={selectedStudentId}
              onShowToast={addToast}
            />
          )}

          {activePage === 'skills' && (
            <SkillMatrixPage
              currentUser={currentUser}
              skills={skills}
              students={students}
              onSelectStudentProfile={handleSelectStudentProfile}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}

          {activePage === 'companies' && (
            <CompaniesPage
              currentUser={currentUser}
              companies={companies}
              drives={drives}
              onNavigateToDrives={() => setActivePage('drives')}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}

          {activePage === 'drives' && (
            <PlacementDrivesPage
              currentUser={currentUser}
              drives={drives}
              companies={companies}
              skills={skills}
              applications={applications}
              onCheckEligibility={handleSelectDriveForEligibility}
              onViewApplications={handleSelectDriveForApplications}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}

          {activePage === 'eligibility' && (
            <EligibilityCheckerPage
              currentUser={currentUser}
              students={students}
              drives={drives}
              skills={skills}
              applications={applications}
              initialStudentId={selectedStudentId}
              initialDriveId={selectedDriveId}
              onNavigateToApplications={() => setActivePage('applications')}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}

          {activePage === 'skill-gap' && (
            <SkillGapAnalysisPage
              currentUser={currentUser}
              students={students}
              drives={drives}
              skills={skills}
              initialStudentId={selectedStudentId}
              initialDriveId={selectedDriveId}
              onNavigateToSkillMatrix={() => setActivePage('skills')}
            />
          )}

          {activePage === 'matching' && (
            <CompanyMatchingPage
              currentUser={currentUser}
              students={students}
              drives={drives}
              companies={companies}
              skills={skills}
              applications={applications}
              initialStudentId={selectedStudentId}
              onCheckEligibility={handleSelectDriveForEligibility}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}

          {activePage === 'applications' && (
            <ApplicationsPage
              currentUser={currentUser}
              applications={applications}
              drives={drives}
              students={students}
              initialDriveId={selectedDriveId}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}

          {activePage === 'reports' && (
            <ReportsPage
              students={students}
              companies={companies}
              drives={drives}
              applications={applications}
              skills={skills}
              onShowToast={addToast}
            />
          )}

          {activePage === 'settings' && (
            <SettingsPage
              currentUser={currentUser}
              users={users}
              dbStatus={dbStatus}
              onCheckDbHealth={handleCheckDbHealth}
              onRefreshData={handleRefreshData}
              onShowToast={addToast}
            />
          )}
        </main>
      </div>

        {/* Desktop System Status Bar */}
        <footer className="h-7 bg-[#101A35] border-t border-[#172B50] px-4 flex items-center justify-between text-[11px] text-slate-400 select-none shrink-0 z-20">
          <div className="flex items-center space-x-3">
            <span className="flex items-center space-x-1.5 text-slate-300">
              <span className="w-1.5 h-1.5 rounded-full bg-[#32C5F4]" />
              <strong className="text-white">GCET Placement Hub</strong>
              <span className="text-slate-500">·</span>
              <span>UGC Autonomous</span>
            </span>
            <span className="hidden md:inline text-slate-500">|</span>
            <span className="hidden md:inline text-slate-400">Affiliated to JNTU Hyderabad · NBA &amp; NAAC A+</span>
          </div>

          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-1.5 font-mono text-[10px] text-slate-300">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span>MySQL 8 : gcet_placement_hub</span>
            </div>
            <span className="text-slate-600">|</span>
            <div className="hidden sm:flex items-center space-x-1.5 text-[10px]">
              <span className="text-slate-400">User:</span>
              <strong className="text-[#32C5F4]">{currentUser ? `${currentUser.username} [${currentUser.role}]` : 'Guest'}</strong>
            </div>
          </div>
        </footer>

      {/* Login Screen Modal */}
      {isLoginOpen && (
        <LoginPage
          onLoginSuccess={(user) => {
            setCurrentUser(user);
            setIsLoginOpen(false);
            setActivePage('dashboard');
            addToast('success', 'Authentication Successful', `Welcome, ${user.fullName} [${user.role}]`);
          }}
          onClose={() => setIsLoginOpen(false)}
          canDismiss={!!currentUser}
        />
      )}

      {/* Mandatory First-Login Password Change Modal (e.g., Newly Approved Recruiters) */}
      {currentUser && currentUser.mustChangePassword && (
        <ForcePasswordChangeModal
          user={currentUser}
          onPasswordChanged={(updatedUser) => {
            authService.setCurrentUser(updatedUser);
            setCurrentUser(updatedUser);
            addToast('success', 'Password Updated', 'Your personal password is now active.');
          }}
          onSignOut={() => {
            authService.logout();
            setCurrentUser(null);
            setActivePage('dashboard');
          }}
        />
      )}

      {/* Global Toast Notifications */}
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      </div>
    </ThemeProvider>
  );
}
