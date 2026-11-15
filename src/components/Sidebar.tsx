import React from 'react';
import {
  LayoutDashboard,
  Users,
  UserCheck,
  Grid3X3,
  Building2,
  Briefcase,
  CheckCircle2,
  SplitSquareVertical,
  Target,
  FileCheck2,
  BarChart3,
  Settings,
  Code2,
  ChevronLeft,
  ChevronRight,
  Shield,
  GraduationCap,
  UserCircle,
} from 'lucide-react';
import { UserRole } from '../types';
import { GCET_OFFICIAL_LOGO } from '../assets/campusImages';

export type NavPageId = 
  | 'dashboard'
  | 'campus-connect'
  | 'approvals'
  | 'students'
  | 'profile'
  | 'student-profile'
  | 'skills'
  | 'companies'
  | 'drives'
  | 'eligibility'
  | 'skill-gap'
  | 'matching'
  | 'applications'
  | 'reports'
  | 'settings';

interface SidebarProps {
  activePage: NavPageId;
  onNavigate: (page: NavPageId) => void;
  userRole?: UserRole;
  isCollapsed?: boolean;
  onToggleCollapse?: () => void;
}

interface NavItem {
  id: NavPageId;
  label: string;
  icon: React.ElementType;
  roles?: UserRole[]; // If not specified, available to all
  badge?: string;
  tooltip: string;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activePage,
  onNavigate,
  userRole = 'PLACEMENT_OFFICER',
  isCollapsed = false,
  onToggleCollapse,
}) => {
  const navItems: NavItem[] = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard, tooltip: 'Main Placement Overview & KPIs' },
    { id: 'campus-connect', label: 'Campus Connect', icon: GraduationCap, badge: 'GCTC', tooltip: 'Real GCTC Portal, Announcements & Campus Resources' },
    { 
      id: 'approvals', 
      label: 'Approval Center', 
      icon: Shield, 
      badge: 'RBAC',
      roles: ['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN', 'PLACEMENT_OFFICER', 'FACULTY'], 
      tooltip: 'Principal Requests, Faculty Dual Approval & Student/Company Clearances' 
    },
    { id: 'students', label: 'Students', icon: Users, roles: ['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN', 'PLACEMENT_OFFICER', 'FACULTY'], tooltip: 'Student Academic Directory' },
    { id: 'profile', label: 'My Profile', icon: UserCheck, tooltip: 'View & Edit Personal and Professional Profile' },
    { id: 'student-profile', label: 'Student Profiles', icon: UserCircle, roles: ['PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN', 'PLACEMENT_OFFICER', 'FACULTY', 'COMPANY'], tooltip: 'Individual Student Academic & Skill Profiles' },
    { id: 'skills', label: 'Skill Matrix', icon: Grid3X3, tooltip: 'Technical Competency & Level 1-5 Matrix' },
    { id: 'companies', label: 'Companies', icon: Building2, tooltip: 'Corporate Recruiting Partners' },
    { id: 'drives', label: 'Placement Drives', icon: Briefcase, tooltip: 'Campus Recruitment Drives & Criteria' },
    { id: 'eligibility', label: 'Eligibility Checker', icon: CheckCircle2, tooltip: 'Automated Multi-Criteria Eligibility Engine' },
    { id: 'skill-gap', label: 'Skill Gap Analysis', icon: SplitSquareVertical, tooltip: 'Candidate vs Company Skill Gap Reports' },
    { id: 'matching', label: 'Company Matching', icon: Target, tooltip: 'Smart Candidate-to-Company Matching' },
    { id: 'applications', label: 'Applications', icon: FileCheck2, tooltip: '10-Stage Recruitment Funnel Tracking' },
    { id: 'reports', label: 'Reports & Analytics', icon: BarChart3, tooltip: 'Institutional Placement Analytics & Charts' },
    { id: 'settings', label: 'Settings & Java Bundle', icon: Settings, tooltip: 'MySQL 8, RBAC & Java 17 Swing Source' },
  ];

  const visibleItems = navItems.filter((item) => {
    if (!item.roles) return true;
    return item.roles.includes(userRole);
  });

  return (
    <aside
      className={`${
        isCollapsed ? 'w-18' : 'w-64'
      } bg-[#101A35] text-slate-300 border-r border-[#172B50] flex flex-col shrink-0 min-h-[calc(100vh-4rem)] transition-all duration-300 ease-in-out relative select-none`}
    >
      {/* Top Header / Branding Sub-strip */}
      <div className="p-3.5 border-b border-[#172B50] flex items-center justify-between">
        {!isCollapsed ? (
          <div className="flex items-center gap-2.5 truncate">
            <div className="w-8 h-8 rounded-full bg-white p-0.5 border border-[#32C5F4]/40 shadow-xs flex items-center justify-center shrink-0 overflow-hidden">
              <img
                src={GCET_OFFICIAL_LOGO}
                alt="GCET Logo"
                className="w-full h-full object-contain"
              />
            </div>
            <div className="truncate">
              <div className="text-[10px] uppercase tracking-widest text-[#32C5F4] font-extrabold flex items-center space-x-1.5">
                <span>GCET Placement Cell</span>
              </div>
              <div className="text-[10px] text-slate-400 font-medium truncate">
                UGC Autonomous · NBA
              </div>
            </div>
          </div>
        ) : (
          <div className="w-full flex justify-center">
            <div className="w-8 h-8 rounded-full bg-white p-0.5 border border-[#32C5F4]/40 shadow-xs flex items-center justify-center overflow-hidden" title="GCET Placement Cell">
              <img
                src={GCET_OFFICIAL_LOGO}
                alt="GCET Logo"
                className="w-full h-full object-contain"
              />
            </div>
          </div>
        )}

        {onToggleCollapse && (
          <button
            onClick={onToggleCollapse}
            className={`p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#172B50] transition cursor-pointer shrink-0 ${
              isCollapsed ? 'mx-auto' : ''
            }`}
            title={isCollapsed ? 'Expand Sidebar' : 'Collapse Sidebar'}
            aria-label={isCollapsed ? 'Expand Sidebar' : 'Collapse Sidebar'}
          >
            {isCollapsed ? <ChevronRight className="w-4 h-4 text-[#32C5F4]" /> : <ChevronLeft className="w-4 h-4" />}
          </button>
        )}
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 p-2 space-y-1 overflow-y-auto overflow-x-hidden">
        {visibleItems.map((item) => {
          const Icon = item.icon;
          const isActive = activePage === item.id;

          return (
            <div key={item.id} className="relative group">
              <button
                onClick={() => onNavigate(item.id)}
                className={`w-full flex items-center ${
                  isCollapsed ? 'justify-center px-2' : 'justify-between px-3'
                } py-2.5 rounded-lg text-xs font-medium transition cursor-pointer text-left ${
                  isActive
                    ? 'bg-[#2684FF] text-white shadow-md font-semibold ring-1 ring-blue-400/30'
                    : 'text-slate-300 hover:bg-[#172B50] hover:text-white'
                }`}
                title={isCollapsed ? item.tooltip : undefined}
              >
                <div className="flex items-center space-x-3 truncate">
                  <Icon
                    className={`w-4 h-4 shrink-0 transition-transform ${
                      isActive ? 'text-white scale-110' : 'text-slate-400 group-hover:text-white'
                    }`}
                  />
                  {!isCollapsed && <span className="truncate">{item.label}</span>}
                </div>

                {!isCollapsed && item.badge && (
                  <span className="text-[10px] px-1.5 py-0.5 rounded bg-[#172B50] text-[#32C5F4] border border-blue-500/30">
                    {item.badge}
                  </span>
                )}
              </button>

              {/* Tooltip on Collapsed Mode */}
              {isCollapsed && (
                <div className="absolute left-full ml-2 top-1/2 -translate-y-1/2 z-50 pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity duration-150">
                  <div className="bg-[#172B50] text-white text-[11px] font-semibold py-1.5 px-3 rounded-lg shadow-xl border border-slate-700 whitespace-nowrap">
                    <div>{item.label}</div>
                    <div className="text-[10px] text-[#32C5F4] font-normal">{item.tooltip}</div>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </nav>

      {/* Footer info strip */}
      <div className="p-3 border-t border-[#172B50] bg-[#0c142b] text-[11px] text-slate-400">
        {!isCollapsed ? (
          <div className="space-y-1.5">
            <div className="flex items-center space-x-2 text-slate-200 font-semibold">
              <Code2 className="w-3.5 h-3.5 text-[#32C5F4]" />
              <span className="text-[11px]">Java 17 Swing Architecture</span>
            </div>
            <p className="text-[10px] text-slate-400 leading-tight">
              FlatLaf 3.5.4 · JFreeChart 1.5.5 · MySQL 8 Connector
            </p>
          </div>
        ) : (
          <div className="flex justify-center" title="Java 17 Swing Desktop Architecture (FlatLaf + MySQL)">
            <Code2 className="w-4 h-4 text-[#32C5F4]" />
          </div>
        )}
      </div>
    </aside>
  );
};
