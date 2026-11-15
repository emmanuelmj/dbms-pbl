import React, { useState, useEffect } from 'react';
import { 
  Building2, 
  GraduationCap, 
  LogOut, 
  Database,
  Bell,
  Calendar,
  Sun,
  Moon,
  CheckCircle2,
  ExternalLink,
} from 'lucide-react';
import { User } from '../types';
import { authService } from '../services/auth';
import { api, DbStatusResponse } from '../services/api';
import { GCET_OFFICIAL_LOGO } from '../assets/campusImages';
import { useTheme } from '../context/ThemeContext';

interface HeaderBarProps {
  currentUser: User | null;
  dbStatus?: DbStatusResponse | null;
  onCheckDbHealth?: () => Promise<DbStatusResponse>;
  onUserChange: (user: User | null) => void;
  onOpenLogin: () => void;
  activePageTitle: string;
  onNavigate?: (page: any) => void;
}

interface NotificationItem {
  id: string;
  title: string;
  message: string;
  time: string;
  type: 'drive' | 'skill' | 'system';
  unread: boolean;
}

export const HeaderBar: React.FC<HeaderBarProps> = ({
  currentUser,
  dbStatus: externalDbStatus,
  onCheckDbHealth,
  onUserChange,
  onOpenLogin,
  activePageTitle,
}) => {
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showDbModal, setShowDbModal] = useState(false);
  const [localDbStatus, setLocalDbStatus] = useState<DbStatusResponse | null>(null);
  const { theme, toggleTheme } = useTheme();

  const dbStatus = externalDbStatus ?? localDbStatus;

  // Dynamic formatted date
  const [currentDateFormatted, setCurrentDateFormatted] = useState('');

  // Fetch fresh DB diagnostics when modal opens or if external status is absent
  useEffect(() => {
    if (!externalDbStatus) {
      api.getDbStatus().then(setLocalDbStatus).catch(() => {});
    }
  }, [externalDbStatus]);

  useEffect(() => {
    if (showDbModal) {
      if (onCheckDbHealth) {
        onCheckDbHealth().catch(() => {});
      } else {
        api.getDbStatus().then(setLocalDbStatus).catch(() => {});
      }
    }
  }, [showDbModal, onCheckDbHealth]);

  useEffect(() => {
    const now = new Date();
    const formatted = now.toLocaleDateString('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
    setCurrentDateFormatted(formatted);
  }, []);

  // Notifications State (reflects genuine connection status)
  const [notifications, setNotifications] = useState<NotificationItem[]>([
    {
      id: 'notif-1',
      title: 'Placement Drive Schedule',
      message: 'Review open campus placement drives and student eligibility criteria.',
      time: 'Today',
      type: 'drive',
      unread: true,
    },
    {
      id: 'notif-2',
      title: 'Skill Matrix Verification',
      message: 'Department faculty coordinators verify submitted student technical skills.',
      time: 'Active',
      type: 'skill',
      unread: false,
    },
  ]);

  const unreadCount = notifications.filter((n) => n.unread).length;

  const handleMarkAllRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, unread: false })));
  };

  const handleConfirmLogout = () => {
    setShowLogoutConfirm(false);
    authService.logout();
    onUserChange(null);
  };

  const handleCancelLogout = () => {
    setShowLogoutConfirm(false);
  };

  return (
    <header className="sticky top-0 z-30 bg-[#101A35] border-b border-[#172B50] text-white shadow-md select-none">
      <div className="w-full px-4 sm:px-6 h-16 flex items-center justify-between">
        
        {/* Left: GCET Crest + Application Brand + Active Module */}
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-3">
            {/* Official GCET College Logo */}
            <div className="w-11 h-11 rounded-full bg-white p-0.5 border-2 border-[#32C5F4]/60 shadow-md flex items-center justify-center shrink-0 overflow-hidden">
              <img
                src={GCET_OFFICIAL_LOGO}
                alt="Geethanjali College of Engineering and Technology (GCET) Official Logo"
                className="w-full h-full object-contain"
              />
            </div>

            <div>
              <div className="flex items-center space-x-2">
                <span className="font-extrabold text-sm sm:text-base tracking-tight text-white">
                  GCET PLACEMENT HUB
                </span>
                <span className="hidden md:inline-flex text-[10px] bg-[#172B50] text-[#32C5F4] font-semibold px-2 py-0.5 rounded border border-blue-500/20">
                  Cheeryal, Telangana
                </span>
              </div>
              <p className="text-[11px] text-slate-300 hidden sm:block">
                Smart Placement Cell &amp; Skill Matrix Portal · <span className="italic text-[#32C5F4]">"Your Skills. Your Opportunities. Your Future."</span>
              </p>
            </div>
          </div>

          <div className="hidden xl:block h-6 w-px bg-[#172B50] mx-1" />

          {/* Active Page Indicator */}
          <div className="hidden xl:flex items-center space-x-2">
            <span className="text-[11px] text-slate-400 font-medium">Module:</span>
            <span className="text-xs font-bold text-white px-2 py-0.5 rounded bg-[#172B50] border border-blue-400/20">
              {activePageTitle}
            </span>
          </div>
        </div>

        {/* Right: Date + MySQL Pulse + Notifications + Theme + LOG OUT ONLY */}
        <div className="flex items-center space-x-3">
          
          {/* Current Date Display */}
          <div className="hidden lg:flex items-center space-x-1.5 px-2.5 py-1.5 rounded-lg bg-[#172B50]/60 border border-[#172B50] text-xs text-slate-300">
            <Calendar className="w-3.5 h-3.5 text-[#32C5F4]" />
            <span className="font-medium text-[11px]">{currentDateFormatted || '4 Nov 2026'}</span>
          </div>

          {/* Database Connection Indicator */}
          <button 
            type="button"
            onClick={() => setShowDbModal(true)}
            className="hidden sm:flex items-center space-x-2 px-2.5 py-1.5 rounded-lg bg-[#172B50]/60 hover:bg-[#172B50] border border-[#172B50] text-xs transition cursor-pointer"
            title="Click to inspect live MySQL database diagnostics"
          >
            <span className="relative flex h-2 w-2">
              {dbStatus?.connected && (
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 bg-emerald-400"></span>
              )}
              <span className={`relative inline-flex rounded-full h-2 w-2 ${
                dbStatus?.connected ? 'bg-emerald-500' : 'bg-rose-500'
              }`}></span>
            </span>
            <Database className="w-3.5 h-3.5 text-[#32C5F4]" />
            <span className="text-slate-300 font-mono text-[11px] hidden md:inline">
              MySQL 8: {dbStatus?.connected ? 'Connected' : 'Disconnected'}
            </span>
          </button>

          {/* GCTC Portal Direct Access */}
          <a
            href="https://www.gctcportal.in/"
            target="_blank"
            rel="noopener noreferrer"
            className="hidden lg:flex items-center space-x-1.5 px-2.5 py-1.5 rounded-lg bg-[#2684FF]/15 hover:bg-[#2684FF]/25 border border-[#2684FF]/40 text-xs text-[#32C5F4] transition cursor-pointer"
            title="Open official GCTC Portal (gctcportal.in)"
          >
            <GraduationCap className="w-3.5 h-3.5" />
            <span className="font-semibold text-[11px]">gctcportal.in</span>
            <ExternalLink className="w-3 h-3 text-slate-400" />
          </a>

          {/* Notification Bell Dropdown */}
          <div className="relative">
            <button
              onClick={() => setShowNotifications(!showNotifications)}
              className="p-2 rounded-lg bg-[#172B50]/60 hover:bg-[#172B50] text-slate-300 hover:text-white border border-[#172B50] transition cursor-pointer relative"
              title="Placement Alerts & Updates"
              aria-label="Placement Alerts"
            >
              <Bell className="w-4 h-4" />
              {unreadCount > 0 && (
                <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-[#32C5F4] ring-2 ring-[#101A35]" />
              )}
            </button>

            {showNotifications && (
              <div className="absolute right-0 mt-2 w-80 rounded-xl bg-[#101A35] border border-[#172B50] shadow-2xl py-2 z-50 animate-in fade-in duration-150">
                <div className="px-4 py-2 border-b border-[#172B50] flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <span className="text-xs font-bold text-white">Placement Alerts</span>
                    {unreadCount > 0 && (
                      <span className="text-[10px] bg-[#2684FF] text-white px-1.5 py-0.2 rounded-full font-bold">
                        {unreadCount}
                      </span>
                    )}
                  </div>
                  {unreadCount > 0 && (
                    <button
                      onClick={handleMarkAllRead}
                      className="text-[10px] text-[#32C5F4] hover:underline cursor-pointer"
                    >
                      Mark all read
                    </button>
                  )}
                </div>

                <div className="divide-y divide-[#172B50] max-h-64 overflow-y-auto">
                  {notifications.map((notif) => (
                    <div
                      key={notif.id}
                      className={`p-3 text-xs transition hover:bg-[#172B50]/50 ${
                        notif.unread ? 'bg-[#172B50]/20' : ''
                      }`}
                    >
                      <div className="flex items-start justify-between">
                        <div className="font-semibold text-slate-100 flex items-center space-x-1.5">
                          {notif.type === 'drive' && <Building2 className="w-3.5 h-3.5 text-[#32C5F4]" />}
                          {notif.type === 'skill' && <CheckCircle2 className="w-3.5 h-3.5 text-amber-400" />}
                          {notif.type === 'system' && <Database className="w-3.5 h-3.5 text-emerald-400" />}
                          <span>{notif.title}</span>
                        </div>
                        <span className="text-[10px] text-slate-500">{notif.time}</span>
                      </div>
                      <p className="text-[11px] text-slate-300 mt-1 leading-relaxed">{notif.message}</p>
                    </div>
                  ))}
                </div>

                <div className="p-2 border-t border-[#172B50] text-center">
                  <span className="text-[10px] text-slate-400">GCET Placement Automation Engine</span>
                </div>
              </div>
            )}
          </div>

          {/* Theme Mode Toggle (Sun/Moon) */}
          <button
            onClick={toggleTheme}
            className="flex items-center space-x-1.5 px-2.5 py-1.5 rounded-lg bg-[#172B50]/60 hover:bg-[#172B50] text-slate-300 hover:text-white border border-[#172B50] transition cursor-pointer text-xs"
            title={theme === 'dark' ? 'Switch to Light Workspace' : 'Switch to Dark Workspace'}
            aria-label="Toggle theme mode"
          >
            {theme === 'dark' ? (
              <>
                <Sun className="w-3.5 h-3.5 text-amber-400" />
                <span className="hidden xl:inline text-[11px] font-medium text-slate-200">Light</span>
              </>
            ) : (
              <>
                <Moon className="w-3.5 h-3.5 text-[#32C5F4]" />
                <span className="hidden xl:inline text-[11px] font-medium text-slate-200">Dark</span>
              </>
            )}
          </button>

          {/* ========================================================================= */}
          {/* TOP-RIGHT ACCOUNT AREA: ONLY ONE PROMINENT, LARGE "LOG OUT" BUTTON */}
          {/* ========================================================================= */}
          {currentUser ? (
            <button
              onClick={() => setShowLogoutConfirm(true)}
              className="inline-flex items-center space-x-2 px-5 py-2.5 sm:px-6 sm:py-2.5 rounded-xl bg-[#2684FF] hover:bg-[#1f73e6] active:bg-[#1560c5] text-white font-extrabold text-xs sm:text-sm tracking-wider uppercase shadow-lg shadow-blue-600/30 border border-blue-400/50 hover:shadow-xl hover:shadow-blue-500/40 hover:-translate-y-0.5 active:translate-y-0 transition-all duration-150 cursor-pointer"
              title="Terminate current session and sign out"
              aria-label="Log Out of GCET Placement Hub"
            >
              <LogOut className="w-4 h-4 sm:w-4.5 sm:h-4.5 text-white stroke-[2.5]" />
              <span className="text-white font-black tracking-wider">LOG OUT</span>
            </button>
          ) : (
            <button
              onClick={onOpenLogin}
              className="inline-flex items-center space-x-2 px-5 py-2.5 sm:px-6 sm:py-2.5 rounded-xl bg-[#2684FF] hover:bg-[#1f73e6] text-white font-extrabold text-xs sm:text-sm tracking-wider uppercase shadow-lg shadow-blue-600/30 border border-blue-400/50 transition-all cursor-pointer"
            >
              <LogOut className="w-4 h-4 sm:w-4.5 sm:h-4.5 text-white stroke-[2.5]" />
              <span className="text-white font-black tracking-wider">LOG OUT</span>
            </button>
          )}

        </div>
      </div>

      {/* ========================================================================= */}
      {/* LOGOUT CONFIRMATION DIALOG */}
      {/* ========================================================================= */}
      {showLogoutConfirm && (
        <div 
          className="fixed inset-0 z-50 bg-[#0B1120]/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-150"
          role="dialog"
          aria-modal="true"
          aria-labelledby="logout-dialog-title"
        >
          <div className="bg-white dark:bg-[#101A35] w-full max-w-md rounded-2xl shadow-2xl border border-slate-200 dark:border-[#172B50] p-6 text-slate-800 dark:text-slate-100 animate-in zoom-in-95 duration-150">
            {/* Header Icon & Title */}
            <div className="flex items-start space-x-3.5">
              <div className="w-12 h-12 rounded-xl bg-blue-500/15 text-[#2684FF] border border-blue-500/30 flex items-center justify-center shrink-0">
                <LogOut className="w-6 h-6 stroke-[2.5]" />
              </div>
              <div className="flex-1">
                <h3 id="logout-dialog-title" className="text-lg font-extrabold text-slate-900 dark:text-white leading-tight">
                  Confirm Session Logout
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  Geethanjali College of Engineering &amp; Technology
                </p>
              </div>
            </div>

            {/* Explanation & User Context */}
            <div className="mt-4 p-3.5 rounded-xl bg-slate-50 dark:bg-[#172B50]/50 border border-slate-200 dark:border-[#172B50] text-xs text-slate-600 dark:text-slate-300 space-y-2">
              <p>
                Are you sure you want to log out of <strong>GCET Placement Hub</strong>?
              </p>
              {currentUser && (
                <div className="pt-2 border-t border-slate-200 dark:border-slate-700/60 flex items-center justify-between text-[11px]">
                  <span className="text-slate-500 dark:text-slate-400">Current Session:</span>
                  <span className="font-semibold text-slate-800 dark:text-slate-200">
                    {currentUser.fullName} <span className="font-mono text-[#2684FF]">[{currentUser.role}]</span>
                  </span>
                </div>
              )}
              <p className="text-[11px] text-slate-500 dark:text-slate-400 italic">
                Your session tokens will be invalidated and you will be returned to the Public GCET Welcome Portal.
              </p>
            </div>

            {/* Action Buttons: Cancel or Confirm Logout */}
            <div className="mt-6 flex items-center justify-end space-x-3">
              <button
                type="button"
                onClick={handleCancelLogout}
                className="px-4 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-[#172B50] text-slate-700 dark:text-slate-300 font-bold text-xs transition cursor-pointer"
              >
                Cancel
              </button>

              <button
                type="button"
                onClick={handleConfirmLogout}
                className="px-5 py-2.5 rounded-xl bg-[#2684FF] hover:bg-blue-600 active:bg-blue-700 text-white font-extrabold text-xs tracking-wider uppercase shadow-md shadow-blue-500/30 flex items-center space-x-2 transition cursor-pointer"
              >
                <LogOut className="w-4 h-4 stroke-[2.5]" />
                <span>Confirm &amp; Log Out</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MySQL 8 Database Diagnostics & LAN Access Modal */}
      {showDbModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-[#101A35] rounded-2xl border border-[#172B50] shadow-2xl max-w-xl w-full p-6 text-white text-left">
            <div className="flex items-center justify-between pb-4 border-b border-[#172B50]">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-xl bg-[#2684FF]/20 border border-[#32C5F4]/40 flex items-center justify-center text-[#32C5F4]">
                  <Database className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white flex items-center space-x-2">
                    <span>MySQL 8 Database Diagnostics</span>
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                      dbStatus?.connected
                        ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                        : 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                    }`}>
                      {dbStatus?.connected ? 'CONNECTED' : 'DISCONNECTED'}
                    </span>
                  </h3>
                  <p className="text-xs text-slate-400">
                    Host: {dbStatus?.host || '127.0.0.1'}:{dbStatus?.port || 3306} · Database: {dbStatus?.database || 'gcet_placement_hub'}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowDbModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-[#172B50] cursor-pointer"
              >
                ✕
              </button>
            </div>

            <div className="py-4 space-y-4 text-xs">
              {/* Configuration Cards */}
              <div className="grid grid-cols-2 gap-3">
                <div className="p-3 rounded-xl bg-[#172B50]/50 border border-[#172B50]">
                  <span className="text-[11px] text-slate-400 block">Database Name</span>
                  <span className="font-mono font-bold text-[#32C5F4] text-sm">gcet_placement_hub</span>
                </div>
                <div className="p-3 rounded-xl bg-[#172B50]/50 border border-[#172B50]">
                  <span className="text-[11px] text-slate-400 block">Host &amp; Port</span>
                  <span className="font-mono font-bold text-slate-200 text-sm">
                    {dbStatus?.host || 'localhost'}:{dbStatus?.port || 3306}
                  </span>
                </div>
                <div className="p-3 rounded-xl bg-[#172B50]/50 border border-[#172B50]">
                  <span className="text-[11px] text-slate-400 block">Database User</span>
                  <span className="font-mono font-bold text-slate-200 text-sm">{dbStatus?.user || 'root'}</span>
                </div>
                <div className="p-3 rounded-xl bg-[#172B50]/50 border border-[#172B50]">
                  <span className="text-[11px] text-slate-400 block">Auth Security Engine</span>
                  <span className="font-mono font-bold text-emerald-400 text-sm">PBKDF2-SHA256</span>
                </div>
              </div>

              {/* Multi-Device LAN Access URL */}
              <div className="p-3.5 rounded-xl bg-gradient-to-r from-[#172B50] to-[#101A35] border border-[#2684FF]/40">
                <span className="text-[11px] font-bold text-[#32C5F4] uppercase tracking-wider block mb-1">
                  Multi-Computer / Local Network (LAN) URL
                </span>
                <p className="text-[11px] text-slate-300 mb-2">
                  Other students, faculty, or placement coordinators on the same Wi-Fi network can access this portal at:
                </p>
                <div className="p-2 rounded-lg bg-[#101A35] border border-[#172B50] flex items-center justify-between font-mono text-cyan-300 font-semibold select-all">
                  <span>http://{dbStatus?.lanIp || 'localhost'}:3000</span>
                  <span className="text-[10px] text-slate-400 font-sans">Port 3000</span>
                </div>
              </div>

              {/* Table Records Summary */}
              {dbStatus?.tableCounts && Object.keys(dbStatus.tableCounts).length > 0 && (
                <div>
                  <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block mb-2">
                    Live Relational Table Records
                  </span>
                  <div className="grid grid-cols-3 gap-2">
                    {Object.entries(dbStatus.tableCounts).map(([tbl, count]) => (
                      <div key={tbl} className="p-2 rounded-lg bg-[#172B50]/30 border border-[#172B50] flex items-center justify-between">
                        <span className="text-slate-400 font-mono text-[11px]">{tbl}</span>
                        <span className="font-bold text-[#32C5F4]">{count}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Status Helper Note */}
              {dbStatus?.connected ? (
                <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-200 text-[11px] leading-relaxed">
                  <strong>Connected: My laptop MySQL database is responding successfully.</strong>
                  {dbStatus.lastSuccessfulConnectionTime && (
                    <div className="mt-1 text-emerald-300/80 font-mono">
                      Last successful ping: {new Date(dbStatus.lastSuccessfulConnectionTime).toLocaleString()}
                    </div>
                  )}
                </div>
              ) : (
                <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-200 text-[11px] leading-relaxed">
                  <strong>Disconnected: The backend or MySQL connection is unavailable.</strong> Start your laptop MySQL Server (<code>net start MySQL80</code>), run the local Express API backend (<code>npm run dev</code>), and verify your HTTPS tunnel URL if accessing remotely.
                </div>
              )}
            </div>

            <div className="pt-3 border-t border-[#172B50] flex justify-end">
              <button
                type="button"
                onClick={() => setShowDbModal(false)}
                className="px-4 py-2 rounded-xl bg-[#2684FF] hover:bg-blue-600 font-bold text-xs text-white transition cursor-pointer"
              >
                Close Diagnostics
              </button>
            </div>
          </div>
        </div>
      )}
    </header>
  );
};
