import React, { useMemo, useState } from 'react';
import {
  Users,
  Building2,
  Briefcase,
  Award,
  ArrowRight,
  Calendar,
  Layers,
  Sparkles,
  TrendingUp,
  CheckCircle2,
  Clock,
  Activity,
  FileCheck2,
  Target,
  BarChart3,
  PieChart,
  ShieldCheck,
  ChevronRight,
  Inbox,
  GraduationCap,
  ExternalLink,
  BookOpen,
  MapPin,
  RefreshCw,
  FileText,
  AlertCircle,
  HelpCircle,
  Percent,
  CheckCircle,
  XCircle,
  Eye,
  Filter,
  DollarSign,
  UserCheck,
} from 'lucide-react';
import { User, Student, Company, PlacementDrive, Application, ActivityLog } from '../types';
import { StatCard } from '../components/StatCard';
import { NavPageId } from '../components/Sidebar';
import { DbStatusResponse } from '../services/api';
import { useTheme } from '../context/ThemeContext';
import { GCET_CAMPUS_BUILDING_PHOTO, GCET_COLLEGE_DETAILS } from '../assets/campusImages';
import { CampusHero } from '../components/CampusHero';
import { campusConnectService } from '../services/campusConnectService';

interface DashboardPageProps {
  currentUser: User | null;
  students: Student[];
  companies: Company[];
  drives: PlacementDrive[];
  applications: Application[];
  activityLogs?: ActivityLog[];
  dbStatus?: DbStatusResponse | null;
  isRefreshingExternal?: boolean;
  onRefreshData?: () => Promise<void>;
  onCheckDbHealth?: () => Promise<DbStatusResponse>;
  onNavigate: (page: NavPageId) => void;
  onSelectStudentProfile?: (studentId: string) => void;
  onSelectDrive?: (driveId: string) => void;
}

type AnalyticsTab = 'branch' | 'companies' | 'packages' | 'trends' | 'eligibility';

function formatRelativeTime(isoString: string): string {
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return 'Recently';
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSec = Math.floor(diffMs / 1000);
    const diffMin = Math.floor(diffSec / 60);
    const diffHour = Math.floor(diffMin / 60);
    const diffDay = Math.floor(diffHour / 24);

    if (diffSec < 60) return 'Just now';
    if (diffMin < 60) return `${diffMin}m ago`;
    if (diffHour < 24) return `${diffHour}h ago`;
    if (diffDay === 1) return 'Yesterday';
    if (diffDay < 7) return `${diffDay}d ago`;
    return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  } catch {
    return 'Recently';
  }
}

export const DashboardPage: React.FC<DashboardPageProps> = ({
  currentUser,
  students,
  companies,
  drives,
  applications,
  activityLogs = [],
  dbStatus,
  isRefreshingExternal = false,
  onRefreshData,
  onCheckDbHealth,
  onNavigate,
  onSelectStudentProfile,
  onSelectDrive,
}) => {
  const { theme } = useTheme();
  const [activeTab, setActiveTab] = useState<AnalyticsTab>('branch');
  const [isRefreshingLocal, setIsRefreshingLocal] = useState<boolean>(false);
  const isRefreshing = isRefreshingLocal || isRefreshingExternal;

  // Handle manual live MySQL refresh & connection test
  const handleRefreshData = async () => {
    setIsRefreshingLocal(true);
    try {
      await Promise.all([
        onCheckDbHealth ? onCheckDbHealth() : Promise.resolve(),
        onRefreshData ? onRefreshData() : Promise.resolve(),
      ]);
    } finally {
      setIsRefreshingLocal(false);
    }
  };

  // 1. Core Database Statistics
  const totalStudents = students.length;
  const totalCompanies = companies.length;
  const activeDrives = drives.filter((d) => d.status === 'OPEN').length;
  const upcomingDrives = drives.filter((d) => d.status === 'UPCOMING' || d.status === 'PLANNED').length;

  const placedStudents = useMemo(() => {
    return students.filter((s) => s.placementStatus === 'PLACED');
  }, [students]);

  const placedCount = placedStudents.length;

  // Actual placement percentage calculated strictly from database records
  const placementPercentage = useMemo(() => {
    if (totalStudents === 0) return '0.0';
    return ((placedCount / totalStudents) * 100).toFixed(1);
  }, [totalStudents, placedCount]);

  // Average package among placed students with valid recorded package
  const avgPackage = useMemo(() => {
    const withPackage = placedStudents.filter((s) => s.placedPackageLpa && s.placedPackageLpa > 0);
    if (withPackage.length === 0) return '0.00';
    const sum = withPackage.reduce((acc, curr) => acc + (curr.placedPackageLpa || 0), 0);
    return (sum / withPackage.length).toFixed(2);
  }, [placedStudents]);

  // Highest package across placed students and active drives
  const highestPackage = useMemo(() => {
    let max = 0;
    drives.forEach((d) => {
      if (d.salaryPackageLpa && d.salaryPackageLpa > max) max = d.salaryPackageLpa;
    });
    placedStudents.forEach((s) => {
      if (s.placedPackageLpa && s.placedPackageLpa > max) max = s.placedPackageLpa;
    });
    return max > 0 ? max.toFixed(1) : '0.0';
  }, [drives, placedStudents]);

  // 2. Branch-wise placement distribution & percentage
  const branchAnalytics = useMemo(() => {
    const data: Record<string, { total: number; placed: number; unplaced: number; avgCgpa: number; totalCgpa: number }> = {};
    students.forEach((s) => {
      if (!data[s.branch]) {
        data[s.branch] = { total: 0, placed: 0, unplaced: 0, avgCgpa: 0, totalCgpa: 0 };
      }
      data[s.branch].total += 1;
      data[s.branch].totalCgpa += s.cgpa || 0;
      if (s.placementStatus === 'PLACED') {
        data[s.branch].placed += 1;
      } else {
        data[s.branch].unplaced += 1;
      }
    });

    // Calculate branch avg cgpa
    Object.keys(data).forEach((b) => {
      if (data[b].total > 0) {
        data[b].avgCgpa = parseFloat((data[b].totalCgpa / data[b].total).toFixed(2));
      }
    });

    return data;
  }, [students]);

  // 3. Company-wise recruitment information
  const companyAnalytics = useMemo(() => {
    return companies.map((c) => {
      const companyDrives = drives.filter(
        (d) => d.companyId === c.id || d.companyName.toLowerCase() === c.name.toLowerCase()
      );
      
      const companyApps = applications.filter(
        (a) => a.companyId === c.id || a.companyName.toLowerCase() === c.name.toLowerCase()
      );

      const offersCount = companyApps.filter((a) =>
        ['OFFERED', 'JOINED', 'SELECTED'].includes(a.currentStage)
      ).length;

      let maxPackage = 0;
      companyDrives.forEach((d) => {
        if (d.salaryPackageLpa > maxPackage) maxPackage = d.salaryPackageLpa;
      });

      return {
        company: c,
        drivesCount: companyDrives.length,
        offersCount,
        maxPackage: maxPackage > 0 ? `${maxPackage} LPA` : 'TBD',
        activeDrive: companyDrives.find((d) => d.status === 'OPEN'),
      };
    });
  }, [companies, drives, applications]);

  // 4. Package distribution brackets
  const packageDistribution = useMemo(() => {
    const brackets = [
      { id: 'b1', label: '< 4.5 LPA', description: 'Mass IT & Foundation Services', count: 0, color: 'from-blue-500 to-cyan-500' },
      { id: 'b2', label: '4.5 - 7.0 LPA', description: 'Core Engineering & Product IT', count: 0, color: 'from-cyan-500 to-teal-500' },
      { id: 'b3', label: '7.0 - 10.0 LPA', description: 'High-Growth Tech / Marquee', count: 0, color: 'from-indigo-500 to-blue-600' },
      { id: 'b4', label: '10.0+ LPA', description: 'Dream & Super Dream Offers', count: 0, color: 'from-emerald-500 to-green-600' },
    ];

    placedStudents.forEach((s) => {
      const pkg = s.placedPackageLpa || 0;
      if (pkg > 0 && pkg < 4.5) brackets[0].count += 1;
      else if (pkg >= 4.5 && pkg < 7.0) brackets[1].count += 1;
      else if (pkg >= 7.0 && pkg < 10.0) brackets[2].count += 1;
      else if (pkg >= 10.0) brackets[3].count += 1;
    });

    return brackets;
  }, [placedStudents]);

  // 5. Monthly Placement Trends
  const monthlyTrends = useMemo(() => {
    const months = [
      { month: 'Aug', drives: 0, offers: 0 },
      { month: 'Sep', drives: 0, offers: 0 },
      { month: 'Oct', drives: 0, offers: 0 },
      { month: 'Nov', drives: 0, offers: 0 },
      { month: 'Dec', drives: 0, offers: 0 },
      { month: 'Jan', drives: 0, offers: 0 },
      { month: 'Feb', drives: 0, offers: 0 },
      { month: 'Mar', drives: 0, offers: 0 },
    ];

    // Compute from drive dates
    drives.forEach((d) => {
      try {
        const dDate = new Date(d.driveDate);
        if (!isNaN(dDate.getTime())) {
          const mIndex = dDate.getMonth(); // 0-11
          // Map to academic cycle (Aug=7, Sep=8, Oct=9, Nov=10, Dec=11, Jan=0, Feb=1, Mar=2)
          const map: Record<number, number> = { 7: 0, 8: 1, 9: 2, 10: 3, 11: 4, 0: 5, 1: 6, 2: 7 };
          if (map[mIndex] !== undefined) {
            months[map[mIndex]].drives += 1;
          }
        }
      } catch {
        // Ignore date parse issues
      }
    });

    // Compute offers from application stage history or appliedAt
    applications.forEach((a) => {
      if (['SELECTED', 'OFFERED', 'JOINED'].includes(a.currentStage)) {
        try {
          const aDate = new Date(a.appliedAt);
          if (!isNaN(aDate.getTime())) {
            const mIndex = aDate.getMonth();
            const map: Record<number, number> = { 7: 0, 8: 1, 9: 2, 10: 3, 11: 4, 0: 5, 1: 6, 2: 7 };
            if (map[mIndex] !== undefined) {
              months[map[mIndex]].offers += 1;
            }
          }
        } catch {
          // Ignore
        }
      }
    });

    return months;
  }, [drives, applications]);

  // 6. Eligible vs Placed Funnel
  const funnelMetrics = useMemo(() => {
    const total = students.length;
    const academicallyEligible = students.filter((s) => s.cgpa >= 6.5 && s.backlogs === 0).length;
    const studentIdsApplied = new Set(applications.map((a) => a.studentId)).size;
    const studentIdsShortlisted = new Set(
      applications
        .filter((a) => ['SHORTLISTED', 'ONLINE_ASSESSMENT', 'TECHNICAL_INTERVIEW', 'HR_INTERVIEW', 'SELECTED', 'OFFERED', 'JOINED'].includes(a.currentStage))
        .map((a) => a.studentId)
    ).size;
    const finalPlaced = placedCount;

    return {
      total,
      academicallyEligible,
      studentIdsApplied,
      studentIdsShortlisted,
      finalPlaced,
    };
  }, [students, applications, placedCount]);

  // 7. Recent activities from live MySQL audit_logs
  const recentActivities = useMemo(() => {
    return activityLogs.slice(0, 6);
  }, [activityLogs]);

  // 8. Upcoming & Open placement drives
  const upcomingAndOpenDrives = useMemo(() => {
    return drives
      .filter((d) => d.status === 'OPEN' || d.status === 'UPCOMING' || d.status === 'PLANNED')
      .slice(0, 5);
  }, [drives]);

  // 9. Verified Announcements from GCTC Portal
  const recentAnnouncements = useMemo(() => {
    return campusConnectService.getAnnouncements().slice(0, 3);
  }, []);

  // 10. Student specific record (if logged in user is a student)
  const currentStudentRecord = useMemo(() => {
    if (currentUser?.role !== 'STUDENT') return null;
    return students.find((s) => s.rollNumber === currentUser.studentRollNumber) || students[0];
  }, [currentUser, students]);

  // Role checks for dedicated role-based dashboards
  const isPrincipal = useMemo(() => {
    if (!currentUser) return false;
    return (
      currentUser.username === 'principal.sagar' ||
      currentUser.role === 'PRINCIPAL' ||
      (currentUser.role === 'ADMIN' &&
        (currentUser.fullName.toLowerCase().includes('principal') ||
          currentUser.fullName.toLowerCase().includes('udaya kumar')))
    );
  }, [currentUser]);

  const isChairman = useMemo(() => {
    if (!currentUser) return false;
    return (
      currentUser.username === 'chairman.gcet' ||
      currentUser.role === 'CHAIRMAN' ||
      (currentUser.fullName.toLowerCase().includes('chairman') &&
        !currentUser.fullName.toLowerCase().includes('vice'))
    );
  }, [currentUser]);

  const isViceChairman = useMemo(() => {
    if (!currentUser) return false;
    return (
      currentUser.username === 'vicechairman.gcet' ||
      currentUser.role === 'VICE_CHAIRMAN' ||
      currentUser.fullName.toLowerCase().includes('vice chairman')
    );
  }, [currentUser]);

  const isPlacementAdmin = useMemo(() => {
    if (!currentUser) return false;
    if (isPrincipal || isChairman || isViceChairman) return false;
    return (
      currentUser.role === 'ADMIN' ||
      currentUser.role === 'PLACEMENT_OFFICER' ||
      currentUser.username === 'placement.admin'
    );
  }, [currentUser, isPrincipal, isChairman, isViceChairman]);

  const isFaculty = currentUser?.role === 'FACULTY';
  const isStudent = currentUser?.role === 'STUDENT';
  const isCompany = currentUser?.role === 'COMPANY';

  // Campus building image based on light/dark mode
  return (
    <div className="space-y-6 animate-in fade-in duration-150">
      
      {/* 1. UNIFIED PREMIUM CAMPUS HERO SECTION (Using Shared Reusable CampusHero Component) */}
      <CampusHero
        institutionBadge="GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY"
        accreditationBadge="UGC AUTONOMOUS | NAAC A+ & NBA"
        title={
          <>
            GCET <span className="text-[#32C5F4] drop-shadow">PLACEMENT HUB</span>
          </>
        }
        subtitle="Smart Placement Cell & Skill Matrix Portal"
        tagline="Your Skills. Your Opportunities. Your Future."
        description="Welcome to GCET Placement Hub. Connecting student talent with career opportunities across premier engineering disciplines."
        details={
          <>
            <span className="flex items-center space-x-1.5 text-slate-200 font-medium bg-black/40 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/15 shadow-xs">
              <MapPin className="w-3.5 h-3.5 text-[#32C5F4]" />
              <span>Cheeryal, Keesara, Telangana - 501301</span>
            </span>

            <button
              onClick={() => onNavigate('campus-connect')}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-blue-500/20 hover:bg-blue-500/30 backdrop-blur-md border border-cyan-400/40 text-[#32C5F4] hover:text-white font-bold transition-all cursor-pointer shadow-xs"
            >
              <span>Official GCTC Academic Portal</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </>
        }
        actions={
          <>
            {/* Live MySQL Connection Indicator */}
            <div className="flex items-center justify-between bg-black/40 backdrop-blur-md border border-white/15 rounded-xl px-3.5 py-2 text-xs text-slate-200 shadow-sm">
              <div className="flex items-center space-x-2">
                <span
                  className={`w-2 h-2 rounded-full ${
                    dbStatus?.connected ? 'bg-emerald-400 animate-ping' : 'bg-rose-500'
                  }`}
                />
                <span
                  className={`text-[11px] font-mono font-semibold ${
                    dbStatus?.connected ? 'text-emerald-300' : 'text-rose-300'
                  }`}
                >
                  {dbStatus?.connected
                    ? `MySQL Connected${
                        dbStatus.lastSuccessfulConnectionTime
                          ? ` · ${new Date(dbStatus.lastSuccessfulConnectionTime).toLocaleTimeString([], {
                              hour: '2-digit',
                              minute: '2-digit',
                              second: '2-digit',
                            })}`
                          : ''
                      }`
                    : 'MySQL Disconnected'}
                </span>
              </div>
              <button
                onClick={handleRefreshData}
                disabled={isRefreshing}
                title="Refresh Live MySQL Database Statistics & Test Connection"
                className="ml-3 p-1 rounded-md text-[#32C5F4] hover:bg-white/10 transition cursor-pointer disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
              </button>
            </div>

            {/* 1. Campus Connect Portal Button */}
            <button
              onClick={() => onNavigate('campus-connect')}
              className="px-4 py-3 rounded-xl bg-gradient-to-r from-[#2684FF] to-blue-600 hover:from-blue-500 hover:to-blue-700 text-white text-xs font-bold transition-all cursor-pointer flex items-center justify-center space-x-2 shadow-lg shadow-blue-600/30 active:scale-[0.98] border border-blue-400/40"
            >
              <GraduationCap className="w-4 h-4 text-white" />
              <span>Campus Connect Portal</span>
            </button>

            {/* 2. Create Placement Drive Button */}
            <button
              onClick={() => onNavigate('drives')}
              className="px-4 py-3 rounded-xl bg-slate-900/70 hover:bg-slate-800/80 backdrop-blur-md text-white text-xs font-semibold border border-white/20 hover:border-cyan-400/50 transition-all cursor-pointer flex items-center justify-center space-x-2 shadow-md active:scale-[0.98]"
            >
              <Briefcase className="w-4 h-4 text-[#32C5F4]" />
              <span>Create Placement Drive</span>
            </button>

            {/* 3. Eligibility Engine Button */}
            <button
              onClick={() => onNavigate('eligibility')}
              className="px-4 py-3 rounded-xl bg-white/10 hover:bg-white/20 backdrop-blur-md text-white text-xs font-semibold border border-white/20 hover:border-white/40 transition-all cursor-pointer flex items-center justify-center space-x-2 shadow-md active:scale-[0.98]"
            >
              <CheckCircle2 className="w-4 h-4 text-[#32C5F4]" />
              <span>Eligibility Engine</span>
            </button>

            {currentUser?.role === 'STUDENT' && (
              <button
                onClick={() => onNavigate('matching')}
                className="px-4 py-2.5 rounded-xl bg-blue-900/50 hover:bg-blue-800/60 backdrop-blur-md text-cyan-200 text-xs font-semibold border border-cyan-500/30 transition-all cursor-pointer flex items-center justify-center space-x-1.5"
              >
                <Sparkles className="w-3.5 h-3.5 text-cyan-300" />
                <span>Smart Company Matching</span>
              </button>
            )}
          </>
        }
      />

      {/* 2. Database-Driven Primary Statistics Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Registered Students"
          value={totalStudents}
          subtitle="Real records across GCET Engineering branches"
          icon={Users}
          accentColor="blue"
        />

        <StatCard
          title="Approved Hiring Partners"
          value={totalCompanies}
          subtitle="Corporate partners across Tier 1, Super Dream & Core"
          icon={Building2}
          accentColor="cyan"
        />

        <StatCard
          title="Active Placement Drives"
          value={activeDrives}
          subtitle={`${upcomingDrives} upcoming recruitment drives scheduled`}
          icon={Briefcase}
          accentColor="emerald"
        />

        <StatCard
          title="Students Placed"
          value={placedCount}
          subtitle={`Average Package: ${avgPackage} LPA · Highest: ${highestPackage} LPA`}
          icon={Award}
          trend={{ text: `${placementPercentage}% Placement Rate`, positive: true }}
          accentColor="amber"
        />
      </div>

      {/* 3. DEDICATED ROLE-BASED DASHBOARD DESKS */}

      {/* Principal / Executive Authority Dashboard View */}
      {isPrincipal && (
        <div className="bg-white dark:bg-[#101A35] rounded-xl border-2 border-blue-500/40 dark:border-blue-500/40 p-5 shadow-md">
          <div className="flex flex-col md:flex-row md:items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50] gap-3">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-xl bg-blue-500/20 text-[#32C5F4] flex items-center justify-center font-bold border border-blue-400/30">
                <ShieldCheck className="w-5 h-5 text-[#2684FF]" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <span className="text-[10px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded bg-blue-500/15 text-[#2684FF] dark:text-[#32C5F4] border border-blue-400/30">
                    Principal &amp; Executive Authority Dashboard
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">UGC Autonomous</span>
                </div>
                <h3 className="text-base font-bold text-[#172033] dark:text-white mt-1">
                  {currentUser?.fullName || 'Dr. S. Udaya Kumar'} <span className="text-xs font-normal text-slate-500 dark:text-slate-400">· Principal &amp; Chief Academic Executive</span>
                </h3>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => onNavigate('approvals')}
                className="px-3.5 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-bold transition flex items-center space-x-1.5 shadow-sm cursor-pointer"
              >
                <ShieldCheck className="w-3.5 h-3.5" />
                <span>Approval Center (RBAC)</span>
              </button>
              <button
                onClick={() => onNavigate('reports')}
                className="px-3.5 py-1.5 rounded-lg bg-slate-100 dark:bg-[#172B50] hover:bg-slate-200 dark:hover:bg-slate-700 text-[#172033] dark:text-white text-xs font-semibold transition flex items-center space-x-1.5 border border-slate-200 dark:border-slate-700 cursor-pointer"
              >
                <BarChart3 className="w-3.5 h-3.5 text-[#32C5F4]" />
                <span>Institutional Analytics</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-xs">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Overall Placement Rate</div>
              <div className="text-sm font-bold text-[#2684FF] mt-1">{placementPercentage}%</div>
              <div className="text-[10px] text-slate-400 mt-0.5">{placedCount} of {totalStudents} Eligible</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Tier-1 &amp; Dream Drives</div>
              <div className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-1">{activeDrives} Active</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Top: {highestPackage} LPA</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Department Clearances</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">Dual-Tier Clearance</div>
              <div className="text-[10px] text-emerald-500 mt-0.5">Faculty + Admin Verified</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Affiliation Standards</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">JNTUH &amp; NAAC A+</div>
              <div className="text-[10px] text-slate-400 mt-0.5">NBA Tier-1 Accredited</div>
            </div>
          </div>
        </div>
      )}

      {/* Chairman Dashboard View */}
      {isChairman && (
        <div className="bg-white dark:bg-[#101A35] rounded-xl border-2 border-cyan-500/40 dark:border-cyan-500/40 p-5 shadow-md">
          <div className="flex flex-col md:flex-row md:items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50] gap-3">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-xl bg-cyan-500/20 text-[#32C5F4] flex items-center justify-center font-bold border border-cyan-400/30">
                <Building2 className="w-5 h-5 text-cyan-500" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <span className="text-[10px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded bg-cyan-500/15 text-cyan-600 dark:text-[#32C5F4] border border-cyan-400/30">
                    Chairman Governing Body Dashboard
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">Teja Educational Society</span>
                </div>
                <h3 className="text-base font-bold text-[#172033] dark:text-white mt-1">
                  {currentUser?.fullName || 'Sri G. R. Ravinder Reddy'} <span className="text-xs font-normal text-slate-500 dark:text-slate-400">· Chairman &amp; Founder</span>
                </h3>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => onNavigate('companies')}
                className="px-3.5 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-700 text-white text-xs font-bold transition flex items-center space-x-1.5 shadow-sm cursor-pointer"
              >
                <Building2 className="w-3.5 h-3.5" />
                <span>Corporate Partners</span>
              </button>
              <button
                onClick={() => onNavigate('reports')}
                className="px-3.5 py-1.5 rounded-lg bg-slate-100 dark:bg-[#172B50] hover:bg-slate-200 dark:hover:bg-slate-700 text-[#172033] dark:text-white text-xs font-semibold transition flex items-center space-x-1.5 border border-slate-200 dark:border-slate-700 cursor-pointer"
              >
                <TrendingUp className="w-3.5 h-3.5 text-[#32C5F4]" />
                <span>Strategic Overview</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-xs">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Institutional Talent Pool</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">{totalStudents} Scholars</div>
              <div className="text-[10px] text-[#32C5F4] mt-0.5">Across All Branches</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Corporate Hiring Alliances</div>
              <div className="text-sm font-bold text-[#2684FF] mt-1">{totalCompanies} Companies</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Tier-1 &amp; Dream Recruiters</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Average Package Milestone</div>
              <div className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-1">{avgPackage} LPA</div>
              <div className="text-[10px] text-emerald-500 mt-0.5">Peak Offer: {highestPackage} LPA</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Autonomous Accreditation</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">NAAC A+ &amp; NBA</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Cheeryal Campus</div>
            </div>
          </div>
        </div>
      )}

      {/* Vice Chairman Dashboard View */}
      {isViceChairman && (
        <div className="bg-white dark:bg-[#101A35] rounded-xl border-2 border-indigo-500/40 dark:border-indigo-500/40 p-5 shadow-md">
          <div className="flex flex-col md:flex-row md:items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50] gap-3">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-xl bg-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold border border-indigo-400/30">
                <Target className="w-5 h-5 text-indigo-500" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <span className="text-[10px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-400/30">
                    Vice Chairman Dashboard
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">Institutional Governance</span>
                </div>
                <h3 className="text-base font-bold text-[#172033] dark:text-white mt-1">
                  {currentUser?.fullName || 'Sri G. V. V. S. N. V. Prasad'} <span className="text-xs font-normal text-slate-500 dark:text-slate-400">· Vice Chairman</span>
                </h3>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => onNavigate('skills')}
                className="px-3.5 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold transition flex items-center space-x-1.5 shadow-sm cursor-pointer"
              >
                <Layers className="w-3.5 h-3.5" />
                <span>Skill Matrix</span>
              </button>
              <button
                onClick={() => onNavigate('matching')}
                className="px-3.5 py-1.5 rounded-lg bg-slate-100 dark:bg-[#172B50] hover:bg-slate-200 dark:hover:bg-slate-700 text-[#172033] dark:text-white text-xs font-semibold transition flex items-center space-x-1.5 border border-slate-200 dark:border-slate-700 cursor-pointer"
              >
                <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
                <span>Company Matching</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-xs">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Skill Readiness Matrix</div>
              <div className="text-sm font-bold text-indigo-600 dark:text-indigo-400 mt-1">Level 1 - 5 Audited</div>
              <div className="text-[10px] text-slate-400 mt-0.5">CSE, ECE &amp; Emerging Branches</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Placed Students</div>
              <div className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-1">{placedCount} Students</div>
              <div className="text-[10px] text-slate-400 mt-0.5">{placementPercentage}% Success Index</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Corporate Drives Scheduled</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">{activeDrives + upcomingDrives} Drives</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Active &amp; Upcoming Funnel</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Industry Synergy</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">CDC Programs</div>
              <div className="text-[10px] text-emerald-500 mt-0.5">Continuous Training</div>
            </div>
          </div>
        </div>
      )}

      {/* Placement System Admin Dashboard View */}
      {isPlacementAdmin && (
        <div className="bg-white dark:bg-[#101A35] rounded-xl border-2 border-emerald-500/40 dark:border-emerald-500/40 p-5 shadow-md">
          <div className="flex flex-col md:flex-row md:items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50] gap-3">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-500/20 text-emerald-500 flex items-center justify-center font-bold border border-emerald-400/30">
                <Briefcase className="w-5 h-5 text-emerald-500" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <span className="text-[10px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border border-emerald-400/30">
                    Placement Admin Dashboard
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">Central Placement Cell</span>
                </div>
                <h3 className="text-base font-bold text-[#172033] dark:text-white mt-1">
                  {currentUser?.fullName || 'Placement System Administrator'} <span className="text-xs font-normal text-slate-500 dark:text-slate-400">· System Admin &amp; IT Infrastructure</span>
                </h3>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => onNavigate('drives')}
                className="px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold transition flex items-center space-x-1.5 shadow-sm cursor-pointer"
              >
                <Briefcase className="w-3.5 h-3.5" />
                <span>Create Placement Drive</span>
              </button>
              <button
                onClick={() => onNavigate('eligibility')}
                className="px-3.5 py-1.5 rounded-lg bg-slate-100 dark:bg-[#172B50] hover:bg-slate-200 dark:hover:bg-slate-700 text-[#172033] dark:text-white text-xs font-semibold transition flex items-center space-x-1.5 border border-slate-200 dark:border-slate-700 cursor-pointer"
              >
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
                <span>Eligibility Engine</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-xs">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Active Recruitment Drives</div>
              <div className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-1">{activeDrives} Open Drives</div>
              <div className="text-[10px] text-slate-400 mt-0.5">{upcomingDrives} Upcoming</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Eligibility Rules Engine</div>
              <div className="text-sm font-bold text-[#2684FF] mt-1">Multi-Criteria Active</div>
              <div className="text-[10px] text-slate-400 mt-0.5">CGPA + Backlogs + Skills</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Relational Database</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">
                MySQL 8 ({dbStatus?.database || 'gcet_placement_hub'})
              </div>
              <div
                className={`text-[10px] mt-0.5 font-semibold ${
                  dbStatus?.connected ? 'text-emerald-500' : 'text-rose-500'
                }`}
              >
                {dbStatus?.connected
                  ? 'Connected: My laptop MySQL database is responding successfully.'
                  : 'Disconnected: The backend or MySQL connection is unavailable.'}
              </div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Recruitment Pipeline</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">{applications.length} Applications</div>
              <div className="text-[10px] text-slate-400 mt-0.5">10-Stage Funnel Tracking</div>
            </div>
          </div>
        </div>
      )}

      {/* Faculty Coordinator Dashboard View */}
      {isFaculty && (
        <div className="bg-white dark:bg-[#101A35] rounded-xl border-2 border-amber-500/40 dark:border-amber-500/40 p-5 shadow-md">
          <div className="flex flex-col md:flex-row md:items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50] gap-3">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-xl bg-amber-500/20 text-amber-500 flex items-center justify-center font-bold border border-amber-400/30">
                <GraduationCap className="w-5 h-5 text-amber-500" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <span className="text-[10px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-400/30">
                    Faculty Dashboard
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">{currentUser?.department || 'Department Coordinator'}</span>
                </div>
                <h3 className="text-base font-bold text-[#172033] dark:text-white mt-1">
                  {currentUser?.fullName} <span className="text-xs font-normal text-slate-500 dark:text-slate-400">· Department Coordinator ({currentUser?.department || 'CSE'})</span>
                </h3>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => onNavigate('approvals')}
                className="px-3.5 py-1.5 rounded-lg bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold transition flex items-center space-x-1.5 shadow-sm cursor-pointer"
              >
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>Approval Center</span>
              </button>
              <button
                onClick={() => onNavigate('students')}
                className="px-3.5 py-1.5 rounded-lg bg-slate-100 dark:bg-[#172B50] hover:bg-slate-200 dark:hover:bg-slate-700 text-[#172033] dark:text-white text-xs font-semibold transition flex items-center space-x-1.5 border border-slate-200 dark:border-slate-700 cursor-pointer"
              >
                <Users className="w-3.5 h-3.5 text-amber-500" />
                <span>Department Students</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-xs">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Department Cohort</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">
                {students.filter(s => s.branch === (currentUser?.department || 'CSE')).length} Students
              </div>
              <div className="text-[10px] text-slate-400 mt-0.5">{currentUser?.department || 'CSE'} Enrolled</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Approval Responsibilities</div>
              <div className="text-sm font-bold text-amber-600 dark:text-amber-400 mt-1">Tier-1 Clearances</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Academic &amp; Attendance Checks</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Technical Skill Matrix</div>
              <div className="text-sm font-bold text-[#2684FF] mt-1">Endorsement Ready</div>
              <div className="text-[10px] text-emerald-500 mt-0.5">Faculty Validation Enabled</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Academic Branch Sync</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">GCTC Cheeryal</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Autonomous Standards</div>
            </div>
          </div>
        </div>
      )}

      {/* Recruiter / Company Dashboard View */}
      {isCompany && (
        <div className="bg-white dark:bg-[#101A35] rounded-xl border-2 border-purple-500/40 dark:border-purple-500/40 p-5 shadow-md">
          <div className="flex flex-col md:flex-row md:items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50] gap-3">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-xl bg-purple-500/20 text-purple-400 flex items-center justify-center font-bold border border-purple-400/30">
                <Briefcase className="w-5 h-5 text-purple-500" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <span className="text-[10px] font-extrabold uppercase tracking-wider px-2 py-0.5 rounded bg-purple-500/15 text-purple-600 dark:text-purple-400 border border-purple-400/30">
                    Recruiter Dashboard
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">Talent Acquisition Partner</span>
                </div>
                <h3 className="text-base font-bold text-[#172033] dark:text-white mt-1">
                  {currentUser?.fullName} <span className="text-xs font-normal text-slate-500 dark:text-slate-400">· Corporate Partner ({currentUser?.department || 'Tech Hiring'})</span>
                </h3>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => onNavigate('drives')}
                className="px-3.5 py-1.5 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold transition flex items-center space-x-1.5 shadow-sm cursor-pointer"
              >
                <Briefcase className="w-3.5 h-3.5" />
                <span>Placement Drives</span>
              </button>
              <button
                onClick={() => onNavigate('applications')}
                className="px-3.5 py-1.5 rounded-lg bg-slate-100 dark:bg-[#172B50] hover:bg-slate-200 dark:hover:bg-slate-700 text-[#172033] dark:text-white text-xs font-semibold transition flex items-center space-x-1.5 border border-slate-200 dark:border-slate-700 cursor-pointer"
              >
                <FileCheck2 className="w-3.5 h-3.5 text-purple-400" />
                <span>Candidate Pipeline</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-xs">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Active Campus Drives</div>
              <div className="text-sm font-bold text-purple-600 dark:text-purple-400 mt-1">
                {drives.filter(d => d.companyName.toLowerCase().includes(currentUser?.department?.toLowerCase() || '')).length || activeDrives} Drives
              </div>
              <div className="text-[10px] text-slate-400 mt-0.5">GCET Campus Recruitment</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Applicants in Funnel</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">
                {applications.filter(a => a.companyName.toLowerCase().includes(currentUser?.department?.toLowerCase() || '')).length || applications.length} Candidates
              </div>
              <div className="text-[10px] text-slate-400 mt-0.5">Across All Rounds</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Skill-Verified Candidates</div>
              <div className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-1">{totalStudents} Assessed</div>
              <div className="text-[10px] text-emerald-500 mt-0.5">Automated Matrix Match</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Recruiter Partnership</div>
              <div className="text-sm font-bold text-slate-800 dark:text-white mt-1">Approved Partner</div>
              <div className="text-[10px] text-slate-400 mt-0.5">GCET Placement Cell</div>
            </div>
          </div>
        </div>
      )}

      {/* Student Placement & Academic Desk View */}
      {isStudent && currentStudentRecord && (
        <div className="bg-white dark:bg-[#101A35] rounded-xl border-2 border-cyan-500/40 dark:border-cyan-500/40 p-5 shadow-md">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50] gap-2">
            <div>
              <span className="text-[10px] font-bold uppercase tracking-wider text-[#32C5F4]">
                Student Dashboard · {currentStudentRecord.rollNumber}
              </span>
              <h3 className="text-sm font-bold text-[#172033] dark:text-white mt-0.5">
                My Academic Credentials &amp; Placement Readiness ({currentStudentRecord.fullName})
              </h3>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-xs text-slate-500 dark:text-slate-400">
                Roll No: <strong className="text-[#2684FF] font-mono">{currentStudentRecord.rollNumber}</strong>
              </span>
              <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${
                currentStudentRecord.placementStatus === 'PLACED'
                  ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                  : currentStudentRecord.placementStatus === 'SHORTLISTED'
                  ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20'
                  : 'bg-slate-500/10 text-slate-600 dark:text-slate-400 border-slate-500/20'
              }`}>
                {currentStudentRecord.placementStatus}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-xs">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Department</div>
              <div className="text-xs font-bold text-slate-800 dark:text-white mt-1">{currentStudentRecord.branch}</div>
              <div className="text-[10px] text-slate-400 mt-0.5">Class of {currentStudentRecord.graduationYear}</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Cumulative CGPA</div>
              <div className="text-xs font-bold text-[#2684FF] mt-1">{currentStudentRecord.cgpa} / 10.0</div>
              <div className="text-[10px] text-slate-400 mt-0.5">{currentStudentRecord.backlogs} Backlogs</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Technical Skills</div>
              <div className="text-xs font-bold text-slate-800 dark:text-white mt-1">
                {currentStudentRecord.skills?.length || 0} Skills Logged
              </div>
              <div className="text-[10px] text-emerald-500 mt-0.5">Verified on Matrix</div>
            </div>

            <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#172B50]/40 border border-slate-200 dark:border-slate-800">
              <div className="text-[10px] text-slate-500 uppercase font-semibold">Academic Portal</div>
              <div className="mt-1 flex items-center space-x-2">
                <a
                  href="https://www.gctcportal.in/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs font-bold text-[#2684FF] hover:underline flex items-center space-x-1"
                >
                  <span>Results &amp; Circulars</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>
              <div className="text-[10px] text-slate-400 mt-0.5">GCTC Autonomous</div>
            </div>
          </div>
        </div>
      )}

      {/* 4. OFFICIAL GCTC ACADEMIC CIRCULARS ARCHIVE (Strictly labeled as archive with clear publication dates) */}
      <div className="bg-gradient-to-r from-blue-900/20 via-[#101A35] to-blue-900/10 rounded-xl border border-[#2684FF]/30 p-5 shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 border-b border-[#172B50] gap-3">
          <div className="flex items-center space-x-2.5">
            <span className="p-1.5 rounded-lg bg-[#2684FF]/20 text-[#32C5F4]">
              <GraduationCap className="w-4 h-4" />
            </span>
            <div>
              <h3 className="text-sm font-bold text-[#172033] dark:text-white flex items-center space-x-2">
                <span>Official GCTC Academic Circulars Archive</span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-blue-500/20 text-[#32C5F4] font-medium border border-blue-400/30">
                  Archived Official Notices
                </span>
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Official institutional notifications indexed from the Cheeryal campus examination branch &amp; academic cell
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-3 shrink-0">
            <a
              href="https://www.gctcportal.in/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs font-semibold text-slate-300 hover:text-white flex items-center space-x-1"
            >
              <span>gctcportal.in</span>
              <ExternalLink className="w-3 h-3 text-[#32C5F4]" />
            </a>
            <span className="text-slate-600">|</span>
            <button
              onClick={() => onNavigate('campus-connect')}
              className="text-xs font-bold text-[#2684FF] dark:text-[#32C5F4] hover:underline flex items-center space-x-1 cursor-pointer"
            >
              <span>Explore All Circulars</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5 mt-3">
          {recentAnnouncements.map((ann) => (
            <div
              key={ann.id}
              className="p-3.5 rounded-lg bg-white/70 dark:bg-[#172B50]/50 border border-slate-200 dark:border-slate-700/60 hover:border-[#2684FF]/40 transition flex flex-col justify-between"
            >
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-[9px] font-bold px-2 py-0.5 rounded uppercase tracking-wider bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20">
                    {ann.category}
                  </span>
                  <span className="text-[10px] text-slate-500 dark:text-slate-400 flex items-center space-x-1 font-mono">
                    <Calendar className="w-3 h-3 text-slate-400" />
                    <span>Published: {ann.date}</span>
                  </span>
                </div>
                <h4 className="text-xs font-bold text-[#172033] dark:text-white line-clamp-2 leading-snug">
                  {ann.title}
                </h4>
              </div>

              <div className="mt-3 pt-2 border-t border-slate-200/60 dark:border-slate-700/40 flex items-center justify-between text-[11px]">
                <span className="text-slate-500 truncate max-w-[150px]">{ann.postedBy}</span>
                <a
                  href={ann.link}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="font-bold text-[#2684FF] hover:text-[#32C5F4] flex items-center space-x-1 shrink-0"
                >
                  <span>Read Notice</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 5. Comprehensive Placement Analytics (5 Detailed Interactive Tabs) */}
      <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-5 shadow-xs">
        
        {/* Header & Tab Switcher */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between pb-4 border-b border-[#E3EAF3] dark:border-[#172B50] gap-3">
          <div>
            <div className="flex items-center space-x-2">
              <BarChart3 className="w-4 h-4 text-[#2684FF]" />
              <h2 className="text-sm font-bold text-[#172033] dark:text-white">
                Comprehensive Placement Analytics
              </h2>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Live computational model derived strictly from verified MySQL 8 records
            </p>
          </div>

          {/* 5 Tab Navigation Controls */}
          <div className="flex flex-wrap items-center p-1 rounded-lg bg-[#F7F9FC] dark:bg-[#172B50]/60 border border-[#E3EAF3] dark:border-[#172B50] text-xs font-semibold gap-1">
            <button
              onClick={() => setActiveTab('branch')}
              className={`px-3 py-1.5 rounded-md transition cursor-pointer flex items-center space-x-1.5 ${
                activeTab === 'branch'
                  ? 'bg-white dark:bg-[#2684FF] text-[#2684FF] dark:text-white shadow-xs font-bold'
                  : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white'
              }`}
            >
              <Users className="w-3.5 h-3.5" />
              <span>Branch-Wise</span>
            </button>

            <button
              onClick={() => setActiveTab('companies')}
              className={`px-3 py-1.5 rounded-md transition cursor-pointer flex items-center space-x-1.5 ${
                activeTab === 'companies'
                  ? 'bg-white dark:bg-[#2684FF] text-[#2684FF] dark:text-white shadow-xs font-bold'
                  : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white'
              }`}
            >
              <Building2 className="w-3.5 h-3.5" />
              <span>Company Recruitment</span>
            </button>

            <button
              onClick={() => setActiveTab('packages')}
              className={`px-3 py-1.5 rounded-md transition cursor-pointer flex items-center space-x-1.5 ${
                activeTab === 'packages'
                  ? 'bg-white dark:bg-[#2684FF] text-[#2684FF] dark:text-white shadow-xs font-bold'
                  : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white'
              }`}
            >
              <Award className="w-3.5 h-3.5" />
              <span>Package Distribution</span>
            </button>

            <button
              onClick={() => setActiveTab('trends')}
              className={`px-3 py-1.5 rounded-md transition cursor-pointer flex items-center space-x-1.5 ${
                activeTab === 'trends'
                  ? 'bg-white dark:bg-[#2684FF] text-[#2684FF] dark:text-white shadow-xs font-bold'
                  : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white'
              }`}
            >
              <TrendingUp className="w-3.5 h-3.5" />
              <span>Monthly Trends</span>
            </button>

            <button
              onClick={() => setActiveTab('eligibility')}
              className={`px-3 py-1.5 rounded-md transition cursor-pointer flex items-center space-x-1.5 ${
                activeTab === 'eligibility'
                  ? 'bg-white dark:bg-[#2684FF] text-[#2684FF] dark:text-white shadow-xs font-bold'
                  : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white'
              }`}
            >
              <Target className="w-3.5 h-3.5" />
              <span>Eligible vs Placed</span>
            </button>
          </div>
        </div>

        {/* Tab 1: Branch-Wise Placement Analysis */}
        {activeTab === 'branch' && (
          <div className="py-4 space-y-4">
            <div className="text-[11px] font-semibold text-slate-500 dark:text-slate-400 flex justify-between items-center px-1">
              <span>Department / Engineering Branch</span>
              <span>Placed / Registered (% Placed · Avg CGPA)</span>
            </div>

            {Object.keys(branchAnalytics).length === 0 ? (
              <div className="py-12 text-center text-xs text-slate-400 flex flex-col items-center">
                <Inbox className="w-8 h-8 text-slate-300 mb-2" />
                <span>No student branch records found in the database.</span>
                <span className="text-[11px] text-slate-500 mt-1">Register students to view branch performance metrics.</span>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-3">
                {Object.entries(branchAnalytics).map(([branch, data]) => {
                  const pct = data.total > 0 ? Math.round((data.placed / data.total) * 100) : 0;
                  return (
                    <div 
                      key={branch}
                      className="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-[#F7F9FC]/60 dark:bg-[#172B50]/30 hover:border-[#2684FF]/40 transition"
                    >
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-2 gap-1 text-[#172033] dark:text-slate-200">
                        <div className="flex items-center space-x-2">
                          <span className="font-bold text-xs">{branch}</span>
                          <span className="text-[10px] px-2 py-0.2 rounded bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-300">
                            Avg CGPA: {data.avgCgpa}
                          </span>
                        </div>
                        <div className="text-[11px] font-mono text-slate-500 dark:text-slate-400">
                          <strong className="text-[#2684FF] dark:text-[#32C5F4]">{data.placed}</strong> placed of {data.total} students (
                          <strong className={pct >= 60 ? 'text-emerald-500' : pct >= 30 ? 'text-amber-500' : 'text-slate-400'}>
                            {pct}%
                          </strong>)
                        </div>
                      </div>

                      {/* Progress Bar */}
                      <div className="w-full bg-[#E3EAF3] dark:bg-[#0B1120] h-2.5 rounded-full overflow-hidden flex">
                        <div
                          className="bg-gradient-to-r from-[#2684FF] to-[#32C5F4] h-full rounded-full transition-all duration-700 ease-out"
                          style={{ width: `${Math.max(pct > 0 ? 4 : 0, pct)}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Company Recruitment Information */}
        {activeTab === 'companies' && (
          <div className="py-4">
            {companyAnalytics.length === 0 ? (
              <div className="py-12 text-center text-xs text-slate-400 flex flex-col items-center">
                <Building2 className="w-8 h-8 text-slate-300 mb-2" />
                <span>No approved hiring partners recorded yet.</span>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-800 text-[11px] text-slate-500 dark:text-slate-400 font-semibold uppercase">
                      <th className="py-2.5 px-3">Company Partner</th>
                      <th className="py-2.5 px-3">Tier / Category</th>
                      <th className="py-2.5 px-3">Location</th>
                      <th className="py-2.5 px-3 text-center">Drives Held</th>
                      <th className="py-2.5 px-3 text-center">Offers Made</th>
                      <th className="py-2.5 px-3">Max Package</th>
                      <th className="py-2.5 px-3 text-right">Drive Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {companyAnalytics.map(({ company, drivesCount, offersCount, maxPackage, activeDrive }) => (
                      <tr key={company.id} className="hover:bg-slate-50 dark:hover:bg-[#172B50]/30 transition">
                        <td className="py-3 px-3 font-bold text-[#172033] dark:text-white">
                          <div className="flex items-center space-x-2">
                            <span className="w-6 h-6 rounded-md bg-[#2684FF]/10 text-[#2684FF] flex items-center justify-center font-bold text-[10px]">
                              {company.name.substring(0, 2).toUpperCase()}
                            </span>
                            <span>{company.name}</span>
                          </div>
                        </td>
                        <td className="py-3 px-3">
                          <span className="text-[10px] px-2 py-0.5 rounded font-medium bg-blue-50 dark:bg-blue-950/40 text-[#2684FF] dark:text-[#32C5F4] border border-blue-200 dark:border-blue-900/60">
                            {company.tier}
                          </span>
                        </td>
                        <td className="py-3 px-3 text-slate-500 dark:text-slate-400">
                          {company.location || 'Hyderabad / Pan-India'}
                        </td>
                        <td className="py-3 px-3 text-center font-mono font-semibold text-slate-700 dark:text-slate-300">
                          {drivesCount}
                        </td>
                        <td className="py-3 px-3 text-center font-mono font-bold text-emerald-600 dark:text-emerald-400">
                          {offersCount}
                        </td>
                        <td className="py-3 px-3 font-bold text-slate-800 dark:text-white">
                          {maxPackage}
                        </td>
                        <td className="py-3 px-3 text-right">
                          {activeDrive ? (
                            <span className="text-[10px] px-2 py-0.5 rounded-full font-bold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                              Active Drive
                            </span>
                          ) : (
                            <span className="text-[10px] px-2 py-0.5 rounded-full font-medium bg-slate-500/10 text-slate-400">
                              Completed / Inactive
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Package Distribution Analysis */}
        {activeTab === 'packages' && (
          <div className="py-4 space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              {packageDistribution.map((b) => {
                const pct = placedCount > 0 ? Math.round((b.count / placedCount) * 100) : 0;
                return (
                  <div
                    key={b.id}
                    className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-[#F7F9FC]/60 dark:bg-[#172B50]/30 flex flex-col justify-between"
                  >
                    <div>
                      <div className="text-[11px] font-bold uppercase tracking-wider text-[#2684FF] dark:text-[#32C5F4]">
                        {b.label}
                      </div>
                      <div className="text-2xl font-extrabold text-[#172033] dark:text-white mt-1">
                        {b.count}{' '}
                        <span className="text-xs font-normal text-slate-500">
                          ({pct}% of placed)
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1 leading-snug">
                        {b.description}
                      </p>
                    </div>

                    <div className="w-full bg-[#E3EAF3] dark:bg-[#0B1120] h-2 rounded-full overflow-hidden mt-3">
                      <div
                        className={`bg-gradient-to-r ${b.color} h-full rounded-full transition-all duration-700`}
                        style={{ width: `${Math.max(pct > 0 ? 5 : 0, pct)}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="p-4 rounded-xl bg-slate-50 dark:bg-[#172B50]/20 border border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-600 dark:text-slate-300 gap-2">
              <div className="flex items-center space-x-2">
                <Award className="w-4 h-4 text-amber-500" />
                <span>
                  <strong>Highest Institution Offer:</strong> {highestPackage} LPA · <strong>Average Offer:</strong> {avgPackage} LPA
                </span>
              </div>
              <span className="text-[11px] text-slate-400">
                Data strictly compiled from registered GCET student placement records
              </span>
            </div>
          </div>
        )}

        {/* Tab 4: Monthly Placement Trends */}
        {activeTab === 'trends' && (
          <div className="py-4 space-y-4">
            <div className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">
              Campus Placement Drive &amp; Selection Velocity (Academic Session 2025-2026)
            </div>

            <div className="grid grid-cols-4 sm:grid-cols-8 gap-2">
              {monthlyTrends.map((t) => (
                <div
                  key={t.month}
                  className="p-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-[#F7F9FC]/60 dark:bg-[#172B50]/30 text-center flex flex-col justify-between"
                >
                  <div className="text-xs font-bold text-slate-700 dark:text-slate-200">{t.month}</div>
                  
                  <div className="my-2 space-y-1">
                    <div className="text-lg font-extrabold text-[#2684FF] dark:text-[#32C5F4]">
                      {t.drives}
                    </div>
                    <div className="text-[10px] text-slate-400">Drives</div>
                  </div>

                  <div className="pt-2 border-t border-slate-200 dark:border-slate-800 text-[11px] font-bold text-emerald-600 dark:text-emerald-400">
                    {t.offers} Offers
                  </div>
                </div>
              ))}
            </div>

            <div className="text-xs text-slate-500 dark:text-slate-400 flex items-center space-x-1.5 pt-1">
              <Clock className="w-3.5 h-3.5 text-[#2684FF]" />
              <span>Placement season peaks across September - December for Tier 1 hiring partners.</span>
            </div>
          </div>
        )}

        {/* Tab 5: Eligible vs Placed Funnel Comparison */}
        {activeTab === 'eligibility' && (
          <div className="py-4 space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-5 gap-3">
              <div className="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-[#172B50]/30 text-center">
                <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">1. Total Registered</div>
                <div className="text-2xl font-extrabold text-[#172033] dark:text-white mt-1">{funnelMetrics.total}</div>
                <div className="text-[10px] text-slate-400 mt-0.5">Students on Portal</div>
              </div>

              <div className="p-3.5 rounded-xl border border-blue-200 dark:border-blue-900/60 bg-blue-50/50 dark:bg-blue-950/20 text-center">
                <div className="text-[10px] font-bold uppercase tracking-wider text-[#2684FF]">2. Academically Eligible</div>
                <div className="text-2xl font-extrabold text-[#2684FF] dark:text-[#32C5F4] mt-1">{funnelMetrics.academicallyEligible}</div>
                <div className="text-[10px] text-slate-400 mt-0.5">CGPA ≥ 6.5 &amp; 0 Backlogs</div>
              </div>

              <div className="p-3.5 rounded-xl border border-cyan-200 dark:border-cyan-900/60 bg-cyan-50/50 dark:bg-cyan-950/20 text-center">
                <div className="text-[10px] font-bold uppercase tracking-wider text-cyan-600 dark:text-cyan-400">3. Drive Applicants</div>
                <div className="text-2xl font-extrabold text-cyan-600 dark:text-cyan-300 mt-1">{funnelMetrics.studentIdsApplied}</div>
                <div className="text-[10px] text-slate-400 mt-0.5">Applied to at least 1 drive</div>
              </div>

              <div className="p-3.5 rounded-xl border border-indigo-200 dark:border-indigo-900/60 bg-indigo-50/50 dark:bg-indigo-950/20 text-center">
                <div className="text-[10px] font-bold uppercase tracking-wider text-indigo-600 dark:text-indigo-400">4. Shortlisted</div>
                <div className="text-2xl font-extrabold text-indigo-600 dark:text-indigo-300 mt-1">{funnelMetrics.studentIdsShortlisted}</div>
                <div className="text-[10px] text-slate-400 mt-0.5">Cleared Online Assessments</div>
              </div>

              <div className="p-3.5 rounded-xl border border-emerald-200 dark:border-emerald-900/60 bg-emerald-50/50 dark:bg-emerald-950/20 text-center">
                <div className="text-[10px] font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">5. Offers Confirmed</div>
                <div className="text-2xl font-extrabold text-emerald-600 dark:text-emerald-400 mt-1">{funnelMetrics.finalPlaced}</div>
                <div className="text-[10px] text-slate-400 mt-0.5">{placementPercentage}% Conversion</div>
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-blue-50/50 dark:bg-[#172B50]/40 border border-blue-200 dark:border-blue-900/40 text-xs text-slate-700 dark:text-slate-300 flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <ShieldCheck className="w-4 h-4 text-[#2684FF]" />
                <span>Eligibility Engine evaluates real-time criteria: Branch suitability, CGPA thresholds, backlogs and required skill matrix levels.</span>
              </div>
              <button
                onClick={() => onNavigate('eligibility')}
                className="text-[#2684FF] dark:text-[#32C5F4] font-bold hover:underline shrink-0 cursor-pointer"
              >
                Run Eligibility Engine
              </button>
            </div>
          </div>
        )}

        <div className="pt-3 border-t border-[#E3EAF3] dark:border-[#172B50] flex flex-wrap items-center justify-between text-xs text-slate-500 dark:text-slate-400">
          <span className="flex items-center space-x-1.5">
            <span className="w-2 h-2 rounded-full bg-[#2684FF]" />
            <span>Strict institutional data integrity: No fabricated figures or mock averages</span>
          </span>
          <button
            onClick={() => onNavigate('reports')}
            className="text-[#2684FF] dark:text-[#32C5F4] font-bold hover:underline flex items-center space-x-1 cursor-pointer"
          >
            <span>Generate Full Statistical Placement Report</span>
            <ChevronRight className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* 6. Upcoming & Active Placement Drives Section */}
      <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-5 shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-[#E3EAF3] dark:border-[#172B50] gap-2">
          <div>
            <h2 className="text-sm font-bold text-[#172033] dark:text-white flex items-center space-x-2">
              <Briefcase className="w-4 h-4 text-[#2684FF]" />
              <span>Upcoming &amp; Active Placement Drives</span>
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Verified corporate hiring opportunities for Geethanjali College students
            </p>
          </div>
          <button
            onClick={() => onNavigate('drives')}
            className="text-xs font-bold text-[#2684FF] dark:text-[#32C5F4] hover:underline flex items-center space-x-1 cursor-pointer"
          >
            <span>View All Drives in Catalog</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="divide-y divide-[#E3EAF3] dark:divide-[#172B50] mt-2">
          {upcomingAndOpenDrives.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-400 flex flex-col items-center">
              <Inbox className="w-8 h-8 text-slate-300 mb-2" />
              <span className="font-semibold text-slate-600 dark:text-slate-300">No recruitment drives currently scheduled.</span>
              <span className="text-[11px] text-slate-500 mt-1">Placement officers and administrators can add new drives via the Placement Drives module.</span>
              <button
                onClick={() => onNavigate('drives')}
                className="mt-3 px-3.5 py-1.5 rounded-lg bg-[#2684FF] text-white text-xs font-bold hover:bg-blue-600 transition cursor-pointer"
              >
                Schedule New Drive
              </button>
            </div>
          ) : (
            upcomingAndOpenDrives.map((drive) => {
              // Role-aware application status
              let studentApp = null;
              if (currentUser?.role === 'STUDENT' && currentStudentRecord) {
                studentApp = applications.find(
                  (a) => a.driveId === drive.id && (a.studentId === currentStudentRecord.id || a.studentRollNumber === currentStudentRecord.rollNumber)
                );
              }
              const driveApplicationsCount = applications.filter((a) => a.driveId === drive.id).length;

              return (
                <div
                  key={drive.id}
                  className="py-4 flex flex-col lg:flex-row lg:items-center justify-between gap-4 hover:bg-[#F7F9FC] dark:hover:bg-[#172B50]/30 px-3 rounded-lg transition"
                >
                  <div className="space-y-1.5 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-bold text-sm text-[#172033] dark:text-white">
                        {drive.companyName}
                      </span>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                        drive.status === 'OPEN' 
                          ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800' 
                          : 'bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800'
                      }`}>
                        {drive.status}
                      </span>

                      {/* Role-aware status badge */}
                      {currentUser?.role === 'STUDENT' ? (
                        studentApp ? (
                          <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-blue-500/10 text-[#2684FF] border border-blue-500/20">
                            Applied ({studentApp.currentStage})
                          </span>
                        ) : (
                          <span className="text-[10px] font-medium px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                            Eligible / Open
                          </span>
                        )
                      ) : (
                        <span className="text-[10px] text-slate-500 dark:text-slate-400 font-mono">
                          {driveApplicationsCount} Applicants
                        </span>
                      )}
                    </div>

                    <div className="text-xs font-semibold text-slate-700 dark:text-slate-200">
                      {drive.jobRole}
                    </div>

                    {/* Metadata strip */}
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-slate-500 dark:text-slate-400">
                      <span className="flex items-center space-x-1">
                        <Award className="w-3.5 h-3.5 text-[#2684FF]" />
                        <span>Package: <strong className="text-[#172033] dark:text-white">{drive.salaryPackageLpa} LPA</strong></span>
                      </span>
                      <span>·</span>
                      <span className="flex items-center space-x-1">
                        <Calendar className="w-3.5 h-3.5 text-slate-400" />
                        <span>Drive Date: <strong>{drive.driveDate}</strong></span>
                      </span>
                      <span>·</span>
                      <span className="flex items-center space-x-1">
                        <Clock className="w-3.5 h-3.5 text-rose-400" />
                        <span>Deadline: <strong className="text-rose-600 dark:text-rose-400">{drive.applicationDeadline}</strong></span>
                      </span>
                      <span>·</span>
                      <span>Min CGPA: <strong>{drive.minCgpa}</strong></span>
                    </div>

                    {/* Eligible Branches Chips */}
                    <div className="flex flex-wrap items-center gap-1.5 pt-1">
                      <span className="text-[10px] text-slate-400 uppercase font-bold mr-1">Eligible Branches:</span>
                      {drive.eligibleBranches.map((br) => (
                        <span
                          key={br}
                          className="text-[10px] px-1.5 py-0.2 rounded bg-slate-100 dark:bg-[#172B50] text-slate-600 dark:text-slate-300 font-mono"
                        >
                          {br}
                        </span>
                      ))}
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex items-center space-x-2 shrink-0">
                    <button
                      onClick={() => {
                        if (onSelectDrive) onSelectDrive(drive.id);
                        onNavigate('eligibility');
                      }}
                      className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 text-[#172033] dark:text-slate-200 text-xs font-semibold transition cursor-pointer"
                    >
                      Check Eligibility
                    </button>
                    
                    <button
                      onClick={() => {
                        if (onSelectDrive) onSelectDrive(drive.id);
                        onNavigate('drives');
                      }}
                      className="px-3.5 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-bold transition cursor-pointer shadow-xs flex items-center space-x-1"
                    >
                      <span>Open Drive</span>
                      <ArrowRight className="w-3 h-3" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* 7. Live Database Activity Log & Quick Action Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left 1 Col: Live Audit Log from MySQL application_status_history */}
        <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-5 shadow-xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50]">
              <div className="flex items-center space-x-2">
                <Activity className="w-4 h-4 text-[#32C5F4]" />
                <h2 className="text-sm font-bold text-[#172033] dark:text-white">Recent System Activities</h2>
              </div>
              <span className="text-[10px] bg-[#172B50] text-[#32C5F4] font-mono px-2 py-0.5 rounded">
                Live Audit
              </span>
            </div>

            <div className="divide-y divide-[#E3EAF3] dark:divide-[#172B50] mt-1">
              {recentActivities.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-400">
                  No recent activities recorded yet.
                </div>
              ) : (
                recentActivities.map((act) => (
                  <div key={act.id} className="py-2.5 text-xs">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-[#172033] dark:text-slate-200 truncate">
                        {act.userName}
                      </span>
                      <span className="text-[10px] text-slate-400 shrink-0 font-mono">
                        {formatRelativeTime(act.timestamp)}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5 leading-snug">
                      {act.details}
                    </p>
                  </div>
                ))
              )}
            </div>
          </div>

          <div className="mt-4 pt-3 border-t border-[#E3EAF3] dark:border-[#172B50] text-center space-y-1">
            <span className="text-[10px] text-slate-400 block">
              Audit log stored in MySQL table <code className="text-[#32C5F4]">audit_logs</code> ({dbStatus?.database || 'gcet_placement_hub'})
            </span>
            <span className="text-[10px] text-slate-400 block font-mono">
              {dbStatus?.lastSuccessfulConnectionTime
                ? `Last successful connection time: ${new Date(dbStatus.lastSuccessfulConnectionTime).toLocaleString()}`
                : 'Last successful connection time: Not yet connected'}
            </span>
          </div>
        </div>

        {/* Right 2 Cols: Institutional Quick Access Grid */}
        <div className="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4">
          <button
            onClick={() => onNavigate('matching')}
            className="p-4 rounded-xl bg-white dark:bg-[#101A35] border border-[#E3EAF3] dark:border-[#172B50] hover:border-[#2684FF] dark:hover:border-[#32C5F4] hover:shadow-md transition text-left cursor-pointer group flex flex-col justify-between"
          >
            <div className="flex items-center space-x-3">
              <div className="p-2.5 rounded-lg bg-[#2684FF]/10 text-[#2684FF] group-hover:bg-[#2684FF] group-hover:text-white transition">
                <Target className="w-5 h-5" />
              </div>
              <div>
                <div className="text-xs font-bold text-[#172033] dark:text-white">Matching Engine</div>
                <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">Automated student-company fit %</div>
              </div>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px] text-[#2684FF] dark:text-[#32C5F4]">
              <span>Run Matching Matrix</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </div>
          </button>

          <button
            onClick={() => onNavigate('skill-gap')}
            className="p-4 rounded-xl bg-white dark:bg-[#101A35] border border-[#E3EAF3] dark:border-[#172B50] hover:border-[#2684FF] dark:hover:border-[#32C5F4] hover:shadow-md transition text-left cursor-pointer group flex flex-col justify-between"
          >
            <div className="flex items-center space-x-3">
              <div className="p-2.5 rounded-lg bg-[#32C5F4]/10 text-[#32C5F4] group-hover:bg-[#32C5F4] group-hover:text-[#101A35] transition">
                <Layers className="w-5 h-5" />
              </div>
              <div>
                <div className="text-xs font-bold text-[#172033] dark:text-white">Skill Gap Analysis</div>
                <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">Curriculum bridge roadmaps</div>
              </div>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px] text-[#2684FF] dark:text-[#32C5F4]">
              <span>Identify Deficiencies</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </div>
          </button>

          <button
            onClick={() => onNavigate('skills')}
            className="p-4 rounded-xl bg-white dark:bg-[#101A35] border border-[#E3EAF3] dark:border-[#172B50] hover:border-[#2684FF] dark:hover:border-[#32C5F4] hover:shadow-md transition text-left cursor-pointer group flex flex-col justify-between"
          >
            <div className="flex items-center space-x-3">
              <div className="p-2.5 rounded-lg bg-emerald-500/10 text-emerald-500 group-hover:bg-emerald-500 group-hover:text-white transition">
                <CheckCircle2 className="w-5 h-5" />
              </div>
              <div>
                <div className="text-xs font-bold text-[#172033] dark:text-white">Skill Matrix Library</div>
                <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">Proficiency 1-5 faculty validations</div>
              </div>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px] text-emerald-600 dark:text-emerald-400">
              <span>Manage Matrix</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </div>
          </button>

          <button
            onClick={() => onNavigate('reports')}
            className="p-4 rounded-xl bg-white dark:bg-[#101A35] border border-[#E3EAF3] dark:border-[#172B50] hover:border-[#2684FF] dark:hover:border-[#32C5F4] hover:shadow-md transition text-left cursor-pointer group flex flex-col justify-between"
          >
            <div className="flex items-center space-x-3">
              <div className="p-2.5 rounded-lg bg-amber-500/10 text-amber-500 group-hover:bg-amber-500 group-hover:text-white transition">
                <Calendar className="w-5 h-5" />
              </div>
              <div>
                <div className="text-xs font-bold text-[#172033] dark:text-white">Placement Reports</div>
                <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">Institutional CSV and PDF export</div>
              </div>
            </div>
            <div className="mt-3 pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px] text-amber-600 dark:text-amber-400">
              <span>Export Reports</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </div>
          </button>
        </div>

      </div>

    </div>
  );
};
