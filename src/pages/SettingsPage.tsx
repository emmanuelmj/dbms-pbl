import React, { useState } from 'react';
import {
  Settings,
  ShieldCheck,
  Database,
  Code2,
  Copy,
  Check,
  Download,
  RefreshCw,
  UserPlus,
  Trash2,
  Terminal,
  Building,
  CheckCircle2,
  AlertTriangle,
  Palette,
  Sun,
  Moon,
  Sparkles,
} from 'lucide-react';
import { User, UserRole } from '../types';
import { api, DbStatusResponse } from '../services/api';
import { JAVA_PROJECT_FILES, JavaFileEntry } from '../services/javaSourceExporter';
import { ConfirmationModal } from '../components/ConfirmationModal';
import { useTheme } from '../context/ThemeContext';

interface SettingsPageProps {
  currentUser: User | null;
  users: User[];
  dbStatus: DbStatusResponse | null;
  onCheckDbHealth: () => Promise<DbStatusResponse>;
  onRefreshData: () => Promise<void> | void;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({
  currentUser,
  users,
  dbStatus,
  onCheckDbHealth,
  onRefreshData,
  onShowToast,
}) => {
  const { theme, setTheme } = useTheme();
  const [activeTab, setActiveTab] = useState<'institution' | 'appearance' | 'users' | 'database' | 'javasource'>('institution');

  // Java files inspector state
  const [selectedJavaFile, setSelectedJavaFile] = useState<JavaFileEntry>(JAVA_PROJECT_FILES[0]);
  const [copiedFile, setCopiedFile] = useState(false);

  // User management form state
  const [isAddUserOpen, setIsAddUserOpen] = useState(false);
  const [isSavingUser, setIsSavingUser] = useState(false);
  const [userForm, setUserForm] = useState({
    username: '',
    fullName: '',
    email: '',
    role: 'STUDENT' as UserRole,
    department: 'CSE',
    password: '',
  });

  // Delete user confirmation modal
  const [deleteUserTarget, setDeleteUserTarget] = useState<{ id: string; username: string } | null>(null);
  const [isTestingConnection, setIsTestingConnection] = useState(false);
  const [isExporting, setIsExporting] = useState(false);

  const isAdmin = ['ADMIN', 'PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN'].includes(currentUser?.role || '');

  const handleCopyCode = () => {
    navigator.clipboard.writeText(selectedJavaFile.content);
    setCopiedFile(true);
    setTimeout(() => setCopiedFile(false), 2000);
    onShowToast('info', 'Copied to Clipboard', `Copied ${selectedJavaFile.filename}`);
  };

  const handleDownloadFile = (file: JavaFileEntry) => {
    const blob = new Blob([file.content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    onShowToast('success', 'File Downloaded', `Downloaded ${file.filename}`);
  };

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!userForm.username.trim() || !userForm.fullName.trim() || !userForm.email.trim()) return;
    if (!userForm.password || userForm.password.length < 6) {
      onShowToast('error', 'Password Required', 'Initial password must be at least 6 characters.');
      return;
    }

    setIsSavingUser(true);
    try {
      await api.createUser({
        username: userForm.username.trim().toLowerCase(),
        fullName: userForm.fullName.trim(),
        email: userForm.email.trim().toLowerCase(),
        role: userForm.role,
        department: userForm.department,
        password: userForm.password,
      });
      await onRefreshData();
      onShowToast('success', 'Account Created in MySQL', `Created ${userForm.username.trim().toLowerCase()} (${userForm.role})`);
      setIsAddUserOpen(false);
      setUserForm({
        username: '',
        fullName: '',
        email: '',
        role: 'STUDENT',
        department: 'CSE',
        password: '',
      });
    } catch (err: any) {
      onShowToast('error', 'Failed to Create User', err?.message || 'Unable to save user to MySQL.');
    } finally {
      setIsSavingUser(false);
    }
  };

  const handleConfirmDeleteUser = async () => {
    if (!deleteUserTarget) return;
    try {
      await api.deleteUser(deleteUserTarget.id);
      await onRefreshData();
      onShowToast('success', 'User Deleted', `Account ${deleteUserTarget.username} deleted from MySQL.`);
      setDeleteUserTarget(null);
    } catch (err: any) {
      onShowToast('error', 'Cannot Delete User', err?.message || 'Unable to delete user in MySQL.');
    }
  };

  const handleTestMySqlConnection = async () => {
    setIsTestingConnection(true);
    try {
      const status = await onCheckDbHealth();
      if (status.connected) {
        await onRefreshData();
        onShowToast('success', 'MySQL Connected', 'Connected: My laptop MySQL database is responding successfully.');
      } else {
        onShowToast('error', 'MySQL Disconnected', 'Disconnected: The backend or MySQL connection is unavailable.');
      }
    } finally {
      setIsTestingConnection(false);
    }
  };

  const handleExportJson = async () => {
    setIsExporting(true);
    try {
      const snapshot = await api.exportDatabase();
      const data = JSON.stringify(snapshot, null, 2);
      const blob = new Blob([data], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `GCET_Placement_Hub_MySQL_Snapshot_${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      onShowToast('success', 'MySQL Snapshot Exported', 'Read-only JSON snapshot downloaded directly from MySQL.');
    } catch (err: any) {
      onShowToast('error', 'Export Failed', err?.message || 'Unable to export live MySQL snapshot.');
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div className="space-y-6">
      
      {/* Header */}
      <div>
        <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Settings, Administration &amp; Java Desktop Sources</h1>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Configure institutional parameters, theme &amp; appearance, user access control, MySQL data storage, and inspect the runnable Java 17 Swing PBL Maven project.
        </p>
      </div>

      {/* Tabs */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-2 shadow-xs flex flex-wrap gap-1">
        <button
          onClick={() => setActiveTab('institution')}
          className={`px-3.5 py-2 rounded-lg text-xs font-medium transition cursor-pointer flex items-center space-x-2 ${
            activeTab === 'institution'
              ? 'bg-slate-900 dark:bg-cyan-600 text-white font-semibold'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          <Building className="w-3.5 h-3.5" />
          <span>Institution Identity</span>
        </button>

        <button
          onClick={() => setActiveTab('appearance')}
          className={`px-3.5 py-2 rounded-lg text-xs font-medium transition cursor-pointer flex items-center space-x-2 ${
            activeTab === 'appearance'
              ? 'bg-slate-900 dark:bg-cyan-600 text-white font-semibold'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          <Palette className="w-3.5 h-3.5 text-cyan-500" />
          <span>Theme &amp; Appearance</span>
        </button>

        <button
          onClick={() => setActiveTab('users')}
          className={`px-3.5 py-2 rounded-lg text-xs font-medium transition cursor-pointer flex items-center space-x-2 ${
            activeTab === 'users'
              ? 'bg-slate-900 dark:bg-cyan-600 text-white font-semibold'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          <ShieldCheck className="w-3.5 h-3.5" />
          <span>User Accounts &amp; Roles</span>
        </button>

        <button
          onClick={() => setActiveTab('database')}
          className={`px-3.5 py-2 rounded-lg text-xs font-medium transition cursor-pointer flex items-center space-x-2 ${
            activeTab === 'database'
              ? 'bg-slate-900 dark:bg-cyan-600 text-white font-semibold'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
          }`}
        >
          <Database className="w-3.5 h-3.5" />
          <span>MySQL Database &amp; Backup</span>
        </button>

        <button
          onClick={() => setActiveTab('javasource')}
          className={`px-3.5 py-2 rounded-lg text-xs font-medium transition cursor-pointer flex items-center space-x-2 ${
            activeTab === 'javasource'
              ? 'bg-cyan-700 dark:bg-cyan-600 text-white font-semibold shadow-xs'
              : 'text-cyan-700 dark:text-cyan-400 hover:bg-cyan-50 dark:hover:bg-slate-800 font-semibold'
          }`}
        >
          <Code2 className="w-3.5 h-3.5" />
          <span>Java Swing PBL Desktop Package</span>
        </button>
      </div>

      {/* Tab 1: Institution Identity */}
      {activeTab === 'institution' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 shadow-xs space-y-6">
          <div className="flex items-start space-x-4">
            <div className="w-14 h-14 rounded-xl bg-gradient-to-br from-cyan-600 to-blue-800 text-white flex items-center justify-center font-bold text-2xl shadow-sm shrink-0">
              G
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">Geethanjali College of Engineering and Technology</h2>
              <div className="text-xs text-cyan-800 dark:text-cyan-400 font-semibold mt-0.5">
                Cheeryal (V), Keesara (M), Medchal-Malkajgiri District, Telangana, PIN: 501301
              </div>
              <p className="text-xs text-slate-600 dark:text-slate-300 mt-2 leading-relaxed">
                Autonomous Institution permanently affiliated to Jawaharlal Nehru Technological University Hyderabad (JNTUH), approved by AICTE New Delhi, and accredited by NAAC with 'A+' Grade and NBA.
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4 pt-4 border-t border-slate-100 dark:border-slate-800 text-xs">
            <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-950/80 border border-slate-200 dark:border-slate-800">
              <span className="text-[10px] text-slate-400 dark:text-slate-500 uppercase font-semibold block">Official Tagline</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 text-xs mt-1 block italic">
                "Your Skills. Your Opportunities. Your Future."
              </span>
            </div>

            <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-950/80 border border-slate-200 dark:border-slate-800">
              <span className="text-[10px] text-slate-400 dark:text-slate-500 uppercase font-semibold block">Placement Cell In-Charge</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 text-xs mt-1 block">
                Prof. B. Ravinder Reddy (Training &amp; Placements)
              </span>
            </div>

            <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-950/80 border border-slate-200 dark:border-slate-800">
              <span className="text-[10px] text-slate-400 dark:text-slate-500 uppercase font-semibold block">Academic PBL Demo</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 text-xs mt-1 block font-mono">
                PBL Course Project · Class of 2026
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Tab: Appearance & Theme Switcher */}
      {activeTab === 'appearance' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 shadow-xs space-y-6">
          <div>
            <h2 className="text-sm font-bold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
              <Palette className="w-4 h-4 text-cyan-600 dark:text-cyan-400" />
              <span>Theme &amp; Appearance Configuration</span>
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
              Select your interface theme preference. Both modes maintain the GCET institutional navy blue branding color palette with high-contrast accessibility.
            </p>
          </div>

          {/* Theme Selection Tiles */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            
            {/* Light Mode Tile */}
            <div
              onClick={() => {
                setTheme('light');
                onShowToast('info', 'Theme Changed', 'Switched to Light Mode');
              }}
              className={`p-5 rounded-xl border-2 transition cursor-pointer flex flex-col justify-between ${
                theme === 'light'
                  ? 'border-cyan-600 bg-cyan-50/40 shadow-xs'
                  : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-950 hover:border-slate-300 dark:hover:border-slate-700'
              }`}
            >
              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-2.5">
                    <div className="p-2 rounded-lg bg-amber-100 text-amber-700">
                      <Sun className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-sm text-slate-900 dark:text-slate-100">Light Mode</h3>
                      <span className="text-[11px] text-slate-500 dark:text-slate-400">Crisp daytime readability</span>
                    </div>
                  </div>
                  {theme === 'light' && (
                    <span className="text-xs font-bold text-cyan-700 flex items-center space-x-1">
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Active</span>
                    </span>
                  )}
                </div>

                <p className="text-xs text-slate-600 dark:text-slate-300 mt-3 leading-relaxed">
                  Clean slate background with institutional navy sidebar (`#0f172a`), deep slate typography, and electric cyan accents. Ideal for classroom PBL presentations and bright lab environments.
                </p>

                {/* Color swatch row */}
                <div className="mt-4 pt-3 border-t border-slate-200/80 dark:border-slate-800 flex items-center space-x-2">
                  <span className="text-[10px] text-slate-400 font-mono">Palette:</span>
                  <span className="w-5 h-5 rounded-full bg-slate-900 border border-slate-700" title="Navy #0f172a" />
                  <span className="w-5 h-5 rounded-full bg-slate-100 border border-slate-300" title="Surface #f1f5f9" />
                  <span className="w-5 h-5 rounded-full bg-cyan-600" title="Accent Cyan #0891b2" />
                  <span className="w-5 h-5 rounded-full bg-white border border-slate-300" title="Card White #ffffff" />
                </div>
              </div>

              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setTheme('light');
                  onShowToast('info', 'Theme Changed', 'Switched to Light Mode');
                }}
                className={`mt-4 w-full py-2 rounded-lg text-xs font-semibold transition cursor-pointer ${
                  theme === 'light'
                    ? 'bg-cyan-600 text-white shadow-xs'
                    : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200'
                }`}
              >
                {theme === 'light' ? 'Current Active Theme' : 'Switch to Light Mode'}
              </button>
            </div>

            {/* Dark Mode Tile */}
            <div
              onClick={() => {
                setTheme('dark');
                onShowToast('info', 'Theme Changed', 'Switched to Dark Mode');
              }}
              className={`p-5 rounded-xl border-2 transition cursor-pointer flex flex-col justify-between ${
                theme === 'dark'
                  ? 'border-cyan-500 bg-slate-950 ring-1 ring-cyan-500 shadow-xs'
                  : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-950 hover:border-slate-300 dark:hover:border-slate-700'
              }`}
            >
              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-2.5">
                    <div className="p-2 rounded-lg bg-cyan-950 text-cyan-400 border border-cyan-800/60">
                      <Moon className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-sm text-slate-900 dark:text-slate-100">Dark Mode (Institutional Navy)</h3>
                      <span className="text-[11px] text-slate-500 dark:text-slate-400">Night coding &amp; low-glare comfort</span>
                    </div>
                  </div>
                  {theme === 'dark' && (
                    <span className="text-xs font-bold text-cyan-400 flex items-center space-x-1">
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Active</span>
                    </span>
                  )}
                </div>

                <p className="text-xs text-slate-600 dark:text-slate-300 mt-3 leading-relaxed">
                  Midnight Navy background (`#101A35`) with Deep Blue navigation (`#172B50`), Electric Blue action highlights (`#2684FF`), and Cyan accents (`#32C5F4`). Optimized for low glare and high visual impact.
                </p>

                {/* Color swatch row */}
                <div className="mt-4 pt-3 border-t border-slate-200/80 dark:border-slate-800 flex items-center space-x-2">
                  <span className="text-[10px] text-slate-400 font-mono">Palette:</span>
                  <span className="w-5 h-5 rounded-full bg-[#101A35] border border-slate-700" title="Midnight Navy #101A35" />
                  <span className="w-5 h-5 rounded-full bg-[#172B50] border border-slate-700" title="Deep Blue #172B50" />
                  <span className="w-5 h-5 rounded-full bg-[#2684FF]" title="Electric Blue #2684FF" />
                  <span className="w-5 h-5 rounded-full bg-[#32C5F4]" title="Cyan Accent #32C5F4" />
                </div>
              </div>

              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setTheme('dark');
                  onShowToast('info', 'Theme Changed', 'Switched to Dark Mode');
                }}
                className={`mt-4 w-full py-2 rounded-lg text-xs font-semibold transition cursor-pointer ${
                  theme === 'dark'
                    ? 'bg-cyan-600 text-white shadow-xs'
                    : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200'
                }`}
              >
                {theme === 'dark' ? 'Current Active Theme' : 'Switch to Dark Mode'}
              </button>
            </div>

          </div>

          {/* Quick Info Box */}
          <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-950/80 border border-slate-200 dark:border-slate-800 text-xs text-slate-600 dark:text-slate-300 flex items-start space-x-3">
            <Sparkles className="w-4 h-4 text-cyan-600 dark:text-cyan-400 shrink-0 mt-0.5" />
            <div className="space-y-1">
              <span className="font-bold text-slate-900 dark:text-slate-100">Persistent Theme Storage:</span>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-relaxed">
                Your theme preference is automatically stored in browser local storage (`gcet_theme_mode`) and instantly applied whenever you open GCET Placement Hub. You can also toggle quickly using the Sun/Moon icon in the top header bar at any time.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: User Accounts & Roles */}
      {activeTab === 'users' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs">
          <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center text-xs">
            <div>
              <span className="font-bold text-slate-800 dark:text-slate-100">Institutional Users &amp; Role-Based Access Control</span>
              <div className="text-[11px] text-slate-500 dark:text-slate-400">Supported Roles: ADMIN, PLACEMENT_OFFICER, FACULTY, STUDENT</div>
            </div>

            {isAdmin && (
              <button
                onClick={() => setIsAddUserOpen(true)}
                className="px-3 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold transition cursor-pointer flex items-center space-x-1"
              >
                <UserPlus className="w-3.5 h-3.5" />
                <span>Create User</span>
              </button>
            )}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 dark:bg-slate-950/60 text-slate-600 dark:text-slate-300 font-semibold border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th className="py-3 px-4">Username</th>
                  <th className="py-3 px-4">Full Name</th>
                  <th className="py-3 px-4">Role</th>
                  <th className="py-3 px-4">Department / Roll No</th>
                  <th className="py-3 px-4">Last Activity</th>
                  {isAdmin && <th className="py-3 px-4 text-right">Action</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800/80">
                {users.map((u) => (
                  <tr key={u.id} className="hover:bg-slate-50/80 dark:hover:bg-slate-800/50 transition">
                    <td className="py-3 px-4 font-mono font-medium text-slate-800 dark:text-slate-200">{u.username}</td>
                    <td className="py-3 px-4 font-semibold text-slate-900 dark:text-slate-100">{u.fullName}</td>
                    <td className="py-3 px-4">
                      <span className={`text-[10px] font-semibold px-2 py-0.5 rounded ${
                        u.role === 'ADMIN'
                          ? 'bg-rose-50 dark:bg-rose-950/50 text-rose-800 dark:text-rose-300 border border-rose-200 dark:border-rose-800'
                          : u.role === 'PLACEMENT_OFFICER'
                          ? 'bg-cyan-50 dark:bg-cyan-950/50 text-cyan-800 dark:text-cyan-300 border border-cyan-200 dark:border-cyan-800'
                          : u.role === 'FACULTY'
                          ? 'bg-amber-50 dark:bg-amber-950/50 text-amber-800 dark:text-amber-300 border border-amber-200 dark:border-amber-800'
                          : 'bg-emerald-50 dark:bg-emerald-950/50 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
                      }`}>
                        {u.role}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-600 dark:text-slate-300">
                      {u.studentRollNumber || u.department || 'Central Admin'}
                    </td>
                    <td className="py-3 px-4 text-slate-400 dark:text-slate-500 font-mono text-[11px]">
                      {u.lastLogin ? new Date(u.lastLogin).toLocaleString() : 'Never'}
                    </td>
                    {isAdmin && (
                      <td className="py-3 px-4 text-right">
                        {u.id !== currentUser?.id && (
                          <button
                            onClick={() => setDeleteUserTarget({ id: u.id, username: u.username })}
                            className="p-1 rounded text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/50"
                            title="Delete User"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Create User Modal */}
          {isAddUserOpen && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
              <div className="bg-white dark:bg-slate-900 rounded-xl max-w-md w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
                <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100 pb-2 border-b border-slate-100 dark:border-slate-800">
                  Register User Account (MySQL)
                </h3>

                <form onSubmit={handleCreateUser} className="mt-4 space-y-3 text-xs">
                  <div>
                    <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Username *</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. faculty_ece or 22R11A0515"
                      value={userForm.username}
                      onChange={(e) => setUserForm({ ...userForm, username: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 font-mono focus:outline-none focus:ring-1 focus:ring-cyan-500"
                    />
                  </div>

                  <div>
                    <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Full Legal Name *</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. Dr. K. Ramesh"
                      value={userForm.fullName}
                      onChange={(e) => setUserForm({ ...userForm, fullName: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                    />
                  </div>

                  <div>
                    <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Email *</label>
                    <input
                      type="email"
                      required
                      placeholder="user@gcet.edu.in"
                      value={userForm.email}
                      onChange={(e) => setUserForm({ ...userForm, email: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Role Permission *</label>
                      <select
                        value={userForm.role}
                        onChange={(e) => setUserForm({ ...userForm, role: e.target.value as UserRole })}
                        className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                      >
                        <option value="STUDENT">STUDENT</option>
                        <option value="FACULTY">FACULTY</option>
                        <option value="PLACEMENT_OFFICER">PLACEMENT_OFFICER</option>
                        <option value="ADMIN">ADMIN</option>
                      </select>
                    </div>

                    <div>
                      <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Department</label>
                      <input
                        type="text"
                        placeholder="e.g. CSE"
                        value={userForm.department}
                        onChange={(e) => setUserForm({ ...userForm, department: e.target.value })}
                        className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Initial Password * (min 6 characters)</label>
                    <input
                      type="password"
                      required
                      minLength={6}
                      placeholder="Enter secure initial password"
                      value={userForm.password}
                      onChange={(e) => setUserForm({ ...userForm, password: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                    />
                  </div>

                  <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-2">
                    <button
                      type="button"
                      onClick={() => setIsAddUserOpen(false)}
                      className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={isSavingUser}
                      className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold disabled:opacity-50"
                    >
                      {isSavingUser ? 'Saving...' : 'Register Account'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Tab 3: MySQL Database & Backup */}
      {activeTab === 'database' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 shadow-xs space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
            <div>
              <h2 className="text-sm font-bold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
                <Database className="w-4 h-4 text-cyan-600 dark:text-cyan-400" />
                <span>MySQL 8 Relational Database Diagnostics</span>
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                {dbStatus?.connected
                  ? 'Connected: My laptop MySQL database is responding successfully.'
                  : 'Disconnected: The backend or MySQL connection is unavailable.'}
              </p>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              <div
                className={`flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold border ${
                  dbStatus?.connected
                    ? 'bg-emerald-50 dark:bg-emerald-950/50 border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300'
                    : 'bg-rose-50 dark:bg-rose-950/50 border-rose-200 dark:border-rose-800 text-rose-800 dark:text-rose-300'
                }`}
              >
                <span
                  className={`w-2 h-2 rounded-full ${
                    dbStatus?.connected ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'
                  }`}
                />
                <span>{dbStatus?.connected ? 'Connected' : 'Disconnected'}</span>
              </div>

              <button
                type="button"
                onClick={handleTestMySqlConnection}
                disabled={isTestingConnection}
                className="px-3 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition cursor-pointer flex items-center space-x-1.5 disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isTestingConnection ? 'animate-spin' : ''}`} />
                <span>Refresh / Test Connection</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs pt-2 border-t border-slate-100 dark:border-slate-800">
            <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-950/70 border border-slate-200 dark:border-slate-800">
              <span className="text-[10px] uppercase font-bold text-slate-400 block">Target Database</span>
              <span className="font-mono font-bold text-slate-900 dark:text-slate-100 mt-1 block">
                {dbStatus?.database || 'gcet_placement_hub'}
              </span>
            </div>
            <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-950/70 border border-slate-200 dark:border-slate-800">
              <span className="text-[10px] uppercase font-bold text-slate-400 block">Host &amp; Port</span>
              <span className="font-mono font-bold text-slate-900 dark:text-slate-100 mt-1 block">
                {dbStatus?.host || '127.0.0.1'}:{dbStatus?.port || 3306}
              </span>
            </div>
            <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-950/70 border border-slate-200 dark:border-slate-800">
              <span className="text-[10px] uppercase font-bold text-slate-400 block">Last Successful Connection Time</span>
              <span className="font-mono font-semibold text-slate-800 dark:text-slate-200 mt-1 block">
                {dbStatus?.lastSuccessfulConnectionTime
                  ? new Date(dbStatus.lastSuccessfulConnectionTime).toLocaleString()
                  : 'Not yet connected'}
              </span>
            </div>
          </div>

          {!dbStatus?.connected && dbStatus?.lastError && (
            <div className="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800 text-xs text-rose-800 dark:text-rose-200 flex items-start space-x-2">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-rose-500" />
              <div>
                <span className="font-bold block">Diagnostic Error:</span>
                <span className="font-mono text-[11px]">{dbStatus.lastError}</span>
              </div>
            </div>
          )}

          {dbStatus?.tableCounts && Object.keys(dbStatus.tableCounts).length > 0 && (
            <div className="pt-2 border-t border-slate-100 dark:border-slate-800">
              <h3 className="text-xs font-bold text-slate-800 dark:text-slate-200 mb-3">
                Live MySQL Table Row Counts (Verified via SQL Queries)
              </h3>
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5 text-xs">
                {Object.entries(dbStatus.tableCounts).map(([table, count]) => (
                  <div
                    key={table}
                    className="p-2.5 rounded-lg bg-slate-50 dark:bg-slate-950/80 border border-slate-200 dark:border-slate-800 flex items-center justify-between"
                  >
                    <span className="font-mono text-[11px] text-slate-600 dark:text-slate-300 truncate">{table}</span>
                    <span className="font-bold text-cyan-600 dark:text-cyan-400 ml-2">{count}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t border-slate-100 dark:border-slate-800 text-xs">
            <div className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60 flex flex-col justify-between">
              <div>
                <h3 className="font-bold text-slate-900 dark:text-slate-100">Export Live MySQL Snapshot (Read-Only)</h3>
                <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">
                  Downloads a sanitized JSON snapshot queried directly from your laptop's <code className="font-mono">gcet_placement_hub</code> MySQL tables (password hashes excluded).
                </p>
              </div>
              <button
                type="button"
                onClick={handleExportJson}
                disabled={isExporting || !dbStatus?.connected}
                className="mt-4 px-3 py-1.5 rounded-lg bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-200 font-semibold transition cursor-pointer flex items-center justify-center space-x-1.5 disabled:opacity-50"
              >
                <Download className="w-3.5 h-3.5 text-slate-500 dark:text-slate-400" />
                <span>{isExporting ? 'Exporting MySQL Snapshot...' : 'Download Live MySQL JSON'}</span>
              </button>
            </div>

            <div className="p-4 rounded-xl border border-cyan-200 dark:border-cyan-900/50 bg-cyan-50/40 dark:bg-cyan-950/20 flex flex-col justify-between">
              <div>
                <h3 className="font-bold text-slate-900 dark:text-slate-100">Non-Destructive Data Protection</h3>
                <p className="text-[11px] text-slate-600 dark:text-slate-300 mt-1 leading-relaxed">
                  All records are persisted permanently in MySQL (<code className="font-mono">gcet_placement_hub</code>). Browser localStorage database caching and destructive one-click database resets are disabled to protect existing institutional records.
                </p>
              </div>
              <div className="mt-4 text-[11px] font-mono text-cyan-700 dark:text-cyan-400">
                Faculty Verification: SELECT * FROM students / applications / audit_logs;
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab 4: Complete Java Swing Desktop Package */}
      {activeTab === 'javasource' && (
        <div className="space-y-4">
          <div className="bg-slate-900 dark:bg-slate-950 rounded-xl p-5 text-white border border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <div className="flex items-center space-x-2 text-cyan-400 font-bold uppercase tracking-wider text-[11px]">
                <Code2 className="w-4 h-4" />
                <span>Java 17 Swing Desktop PBL Application Source Bundle</span>
              </div>
              <h2 className="text-base font-bold text-slate-100 mt-1">
                Complete Maven Project (FlatLaf 3.5.4 + JFreeChart 1.5.5 + MySQL 8 JDBC)
              </h2>
              <p className="text-xs text-slate-300 mt-1 leading-relaxed max-w-2xl">
                Ready for offline terminal execution. Build with <code className="text-cyan-300 font-mono">mvn clean compile</code> and run with <code className="text-cyan-300 font-mono">mvn exec:java</code> or package into an executable fat JAR.
              </p>
            </div>

            <div className="flex items-center space-x-2 shrink-0">
              <button
                onClick={handleCopyCode}
                className="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium border border-slate-700 transition cursor-pointer flex items-center space-x-1.5"
              >
                {copiedFile ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedFile ? 'Copied' : 'Copy Active File'}</span>
              </button>

              <button
                onClick={() => handleDownloadFile(selectedJavaFile)}
                className="px-3.5 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition cursor-pointer flex items-center space-x-1.5 shadow-xs"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Download {selectedJavaFile.filename}</span>
              </button>
            </div>
          </div>

          {/* Compilation Instructions Pill */}
          <div className="bg-slate-950 rounded-xl p-4 border border-slate-800 text-xs font-mono text-slate-300 space-y-2">
            <div className="flex items-center space-x-2 text-cyan-400 font-bold">
              <Terminal className="w-4 h-4" />
              <span>Standard Academic Demonstration Commands:</span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-2 pt-1 text-[11px]">
              <div className="bg-slate-900 p-2.5 rounded border border-slate-800">
                <span className="text-slate-500 block"># 1. Compile all Java classes</span>
                <span className="text-emerald-400 font-bold">mvn clean compile</span>
              </div>
              <div className="bg-slate-900 p-2.5 rounded border border-slate-800">
                <span className="text-slate-500 block"># 2. Launch Main Desktop App</span>
                <span className="text-emerald-400 font-bold">mvn exec:java</span>
              </div>
              <div className="bg-slate-900 p-2.5 rounded border border-slate-800">
                <span className="text-slate-500 block"># 3. Create Standalone JAR</span>
                <span className="text-emerald-400 font-bold">mvn clean package</span>
              </div>
            </div>
          </div>

          {/* File Picker & Code Viewer */}
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs grid grid-cols-1 md:grid-cols-4">
            
            {/* File List */}
            <div className="border-r border-slate-200 dark:border-slate-800 p-3 bg-slate-50 dark:bg-slate-950/80 space-y-1">
              <span className="text-[10px] uppercase font-bold text-slate-400 px-2 block mb-2">
                Project Files ({JAVA_PROJECT_FILES.length})
              </span>
              {JAVA_PROJECT_FILES.map((f) => (
                <button
                  key={f.filename}
                  onClick={() => setSelectedJavaFile(f)}
                  className={`w-full text-left px-3 py-2 rounded-lg text-xs font-medium transition cursor-pointer flex flex-col ${
                    selectedJavaFile.filename === f.filename
                      ? 'bg-cyan-700 text-white font-semibold shadow-xs'
                      : 'text-slate-700 dark:text-slate-300 hover:bg-slate-200/70 dark:hover:bg-slate-800'
                  }`}
                >
                  <span className="truncate">{f.filename}</span>
                  <span className={`text-[10px] truncate ${
                    selectedJavaFile.filename === f.filename ? 'text-cyan-200' : 'text-slate-500 dark:text-slate-400'
                  }`}>
                    {f.path}
                  </span>
                </button>
              ))}
            </div>

            {/* Code Display */}
            <div className="md:col-span-3 flex flex-col bg-slate-950 text-slate-100">
              <div className="p-3 bg-slate-900 border-b border-slate-800 flex justify-between items-center text-xs">
                <div>
                  <span className="font-bold text-cyan-300 font-mono">{selectedJavaFile.path}</span>
                  <div className="text-[11px] text-slate-400">{selectedJavaFile.description}</div>
                </div>
                <button
                  onClick={handleCopyCode}
                  className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 font-mono text-[11px] flex items-center space-x-1"
                >
                  {copiedFile ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                  <span>{copiedFile ? 'Copied' : 'Copy'}</span>
                </button>
              </div>

              <div className="p-4 overflow-x-auto max-h-[500px] overflow-y-auto font-mono text-xs text-slate-200 leading-relaxed select-text">
                <pre>{selectedJavaFile.content}</pre>
              </div>
            </div>

          </div>
        </div>
      )}

      {/* Delete User Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!deleteUserTarget}
        title="Delete User Account"
        message={`Permanently delete user account "${deleteUserTarget?.username || ''}" from MySQL? This action cannot be undone.`}
        confirmLabel="Delete User"
        isDestructive={true}
        onConfirm={handleConfirmDeleteUser}
        onCancel={() => setDeleteUserTarget(null)}
      />

    </div>
  );
};
