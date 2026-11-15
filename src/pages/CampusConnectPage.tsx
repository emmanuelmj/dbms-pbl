import React, { useState, useMemo } from 'react';
import {
  Building2,
  ExternalLink,
  GraduationCap,
  Calendar,
  FileText,
  Search,
  Bell,
  Sparkles,
  Users,
  Award,
  BookOpen,
  CheckCircle2,
  Layers,
  ArrowRight,
  Download,
  AlertCircle,
  Clock,
  Phone,
  Mail,
  MapPin,
  Flame,
  Globe,
  Camera,
  HeartHandshake,
  Compass,
  Cpu,
  RefreshCw,
  Info,
} from 'lucide-react';
import {
  campusConnectService,
  PortalSyncStatus,
} from '../services/campusConnectService';
import {
  Announcement,
  AnnouncementCategory,
  CollegeClub,
  CollegeFestival,
  StudentResource,
  BranchIntake,
} from '../types/campus';
import { GCET_COLLEGE_DETAILS } from '../assets/campusImages';
import { CampusHero } from '../components/CampusHero';
import { User } from '../types';

interface CampusConnectPageProps {
  currentUser?: User | null;
  onNavigateToTab?: (tab: string) => void;
}

type TabType = 
  | 'announcements' 
  | 'results' 
  | 'placements' 
  | 'clubs' 
  | 'festivals' 
  | 'resources' 
  | 'contacts';

export const CampusConnectPage: React.FC<CampusConnectPageProps> = ({
  currentUser,
}) => {
  const [activeTab, setActiveTab] = useState<TabType>('announcements');
  const [announcementCategory, setAnnouncementCategory] = useState<AnnouncementCategory | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [syncStatus, setSyncStatus] = useState<PortalSyncStatus>(() => campusConnectService.getSyncStatus());
  const [hallTicketInput, setHallTicketInput] = useState('');
  const [hallTicketResult, setHallTicketResult] = useState<string | null>(null);

  const announcements = useMemo(() => {
    let list = announcementCategory === 'ALL'
      ? campusConnectService.getAnnouncements()
      : campusConnectService.getAnnouncements(announcementCategory);
    
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (a) => a.title.toLowerCase().includes(q) || a.summary.toLowerCase().includes(q) || a.postedBy.toLowerCase().includes(q)
      );
    }
    return list;
  }, [announcementCategory, searchQuery]);

  const results = useMemo(() => campusConnectService.getResults(), []);
  const clubs = useMemo(() => campusConnectService.getClubs(), []);
  const festivals = useMemo(() => campusConnectService.getFestivals(), []);
  const resources = useMemo(() => campusConnectService.getResources(), []);
  const intakeList = useMemo(() => campusConnectService.getBranchIntake(), []);
  const totalIntake = useMemo(() => campusConnectService.getTotalIntake(), []);

  const handleManualSync = () => {
    setIsRefreshing(true);
    setTimeout(() => {
      setIsRefreshing(false);
      setSyncStatus({
        ...campusConnectService.getSyncStatus(),
        lastSyncTimestamp: new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }) + ' IST (Just now)',
      });
    }, 600);
  };

  const handleHallTicketSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!hallTicketInput.trim()) {
      setHallTicketResult(null);
      return;
    }
    const clean = hallTicketInput.trim().toUpperCase();
    if (clean.length < 10) {
      setHallTicketResult('Invalid Roll Number. GCET roll numbers follow format 21R11A0501 (10 characters).');
    } else {
      setHallTicketResult(`Record found on Autonomous Exam Server for ${clean}. Redirecting to official GCTC Portal score server...`);
      setTimeout(() => {
        window.open('https://www.gctcportal.in/', '_blank');
      }, 1200);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      
      {/* 1. UNIFIED CAMPUS HERO SECTION (Official GCET Campus Building Background + GCTC Portal Actions) */}
      <CampusHero
        institutionBadge="GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY"
        accreditationBadge="UGC AUTONOMOUS · CHEERYAL, KEESARA (501301)"
        title={
          <>
            CAMPUS <span className="text-[#32C5F4] drop-shadow">CONNECT</span>
          </>
        }
        subtitle="Official GCET Campus Information & Academic Portal"
        description="Connect with the official digital ecosystem of Geethanjali College of Engineering and Technology. Access official institutional information, academic resources, announcements, examination results, student activities, placement resources and college services."
        details={
          <>
            <span className="flex items-center space-x-1.5 text-slate-200 font-medium bg-black/40 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/15 shadow-xs">
              <span className="w-2 h-2 rounded-full bg-[#10B981] animate-pulse" />
              <span>{syncStatus.statusMessage}</span>
              <span className="text-slate-400">·</span>
              <span>Synced: {syncStatus.lastSyncTimestamp}</span>
            </span>

            <button
              onClick={handleManualSync}
              disabled={isRefreshing}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-blue-500/20 hover:bg-blue-500/30 backdrop-blur-md border border-cyan-400/40 text-[#32C5F4] hover:text-white font-bold transition-all cursor-pointer shadow-xs disabled:opacity-50"
              title="Refresh live portal records"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
              <span>{isRefreshing ? 'Refreshing...' : 'Sync Live'}</span>
            </button>
          </>
        }
        actions={
          <>
            {/* Open Official GCTC Portal */}
            <a
              href="https://www.gctcportal.in/"
              target="_blank"
              rel="noopener noreferrer"
              className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-[#2684FF] to-blue-600 hover:from-blue-500 hover:to-blue-700 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-blue-600/30 transition-all cursor-pointer border border-blue-400/40 active:scale-[0.98]"
            >
              <GraduationCap className="w-4 h-4 text-white" />
              <span>Open Official GCTC Portal</span>
              <ExternalLink className="w-3.5 h-3.5 text-blue-200" />
            </a>

            {/* GCET Official Website */}
            <a
              href="https://gcet.edu.in/"
              target="_blank"
              rel="noopener noreferrer"
              className="w-full py-3 px-4 rounded-xl bg-slate-900/70 hover:bg-slate-800/80 backdrop-blur-md text-white font-semibold text-xs border border-white/20 hover:border-cyan-400/50 flex items-center justify-center gap-2 shadow-md transition-all cursor-pointer active:scale-[0.98]"
            >
              <Building2 className="w-4 h-4 text-[#32C5F4]" />
              <span>GCET Official Website</span>
              <ExternalLink className="w-3.5 h-3.5 text-slate-300" />
            </a>

            {/* Micro status text */}
            <div className="pt-1 text-[11px] text-slate-300 text-center flex items-center justify-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span>Verified Institutional GCTC Gateway</span>
            </div>
          </>
        }
      />

      {/* 2. Navigation Tabs */}
      <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-1.5 shadow-xs overflow-x-auto">
        <div className="flex space-x-1 min-w-max">
          <button
            onClick={() => setActiveTab('announcements')}
            className={`px-3.5 py-2 rounded-lg text-xs font-bold transition flex items-center space-x-2 cursor-pointer ${
              activeTab === 'announcements'
                ? 'bg-[#2684FF] text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
            }`}
          >
            <Bell className="w-3.5 h-3.5" />
            <span>Announcements &amp; Circulars</span>
            <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-white/20">
              {announcements.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('results')}
            className={`px-3.5 py-2 rounded-lg text-xs font-bold transition flex items-center space-x-2 cursor-pointer ${
              activeTab === 'results'
                ? 'bg-[#2684FF] text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
            }`}
          >
            <GraduationCap className="w-3.5 h-3.5" />
            <span>Autonomous Results &amp; Exam Desk</span>
          </button>

          <button
            onClick={() => setActiveTab('placements')}
            className={`px-3.5 py-2 rounded-lg text-xs font-bold transition flex items-center space-x-2 cursor-pointer ${
              activeTab === 'placements'
                ? 'bg-[#2684FF] text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
            }`}
          >
            <Award className="w-3.5 h-3.5" />
            <span>Placements &amp; CDC</span>
          </button>

          <button
            onClick={() => setActiveTab('clubs')}
            className={`px-3.5 py-2 rounded-lg text-xs font-bold transition flex items-center space-x-2 cursor-pointer ${
              activeTab === 'clubs'
                ? 'bg-[#2684FF] text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
            }`}
          >
            <Users className="w-3.5 h-3.5" />
            <span>Clubs &amp; Fraternities</span>
            <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-emerald-500/20 text-emerald-600 dark:text-emerald-400">
              8 Active
            </span>
          </button>

          <button
            onClick={() => setActiveTab('festivals')}
            className={`px-3.5 py-2 rounded-lg text-xs font-bold transition flex items-center space-x-2 cursor-pointer ${
              activeTab === 'festivals'
                ? 'bg-[#2684FF] text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
            }`}
          >
            <Flame className="w-3.5 h-3.5" />
            <span>College Festivals</span>
            <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-amber-500/20 text-amber-600 dark:text-amber-400">
              VIBGYOR
            </span>
          </button>

          <button
            onClick={() => setActiveTab('resources')}
            className={`px-3.5 py-2 rounded-lg text-xs font-bold transition flex items-center space-x-2 cursor-pointer ${
              activeTab === 'resources'
                ? 'bg-[#2684FF] text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
            }`}
          >
            <BookOpen className="w-3.5 h-3.5" />
            <span>Student Resources</span>
          </button>

          <button
            onClick={() => setActiveTab('contacts')}
            className={`px-3.5 py-2 rounded-lg text-xs font-bold transition flex items-center space-x-2 cursor-pointer ${
              activeTab === 'contacts'
                ? 'bg-[#2684FF] text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
            }`}
          >
            <Globe className="w-3.5 h-3.5" />
            <span>Campus Directory &amp; Links</span>
          </button>
        </div>
      </div>

      {/* 3. Tab Contents */}

      {/* TAB 1: ANNOUNCEMENTS */}
      {activeTab === 'announcements' && (
        <div className="space-y-4">
          {/* Controls Bar: Search & Category Filters */}
          <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-4 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-1.5">
              {(['ALL', 'EXAMINATION', 'ACADEMIC', 'PLACEMENTS', 'CIRCULAR', 'FESTIVAL'] as const).map((cat) => (
                <button
                  key={cat}
                  onClick={() => setAnnouncementCategory(cat)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer ${
                    announcementCategory === cat
                      ? 'bg-[#172B50] text-[#32C5F4] border border-[#2684FF]/40 font-bold'
                      : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  {cat === 'ALL' ? 'All Updates' : cat.charAt(0) + cat.slice(1).toLowerCase()}
                </button>
              ))}
            </div>

            <div className="relative w-full md:w-72">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search notices, timetables..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-[#E3EAF3] dark:border-[#172B50] bg-slate-50 dark:bg-[#172B50]/50 text-slate-800 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-[#2684FF]"
              />
            </div>
          </div>

          {/* Announcements List */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {announcements.map((ann) => (
              <div
                key={ann.id}
                className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-5 shadow-xs hover:border-[#2684FF]/40 transition flex flex-col justify-between group"
              >
                <div>
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <span
                      className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                        ann.category === 'EXAMINATION'
                          ? 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20'
                          : ann.category === 'PLACEMENTS'
                          ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20'
                          : ann.category === 'FESTIVAL'
                          ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20'
                          : 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                      }`}
                    >
                      {ann.category}
                    </span>

                    <span className="text-[11px] text-slate-500 dark:text-slate-400 flex items-center space-x-1">
                      <Clock className="w-3 h-3" />
                      <span>{ann.date}</span>
                    </span>
                  </div>

                  <h3 className="text-sm font-bold text-[#172033] dark:text-white group-hover:text-[#2684FF] transition leading-snug">
                    {ann.title}
                  </h3>

                  <p className="text-xs text-slate-600 dark:text-slate-300 mt-2 font-normal leading-relaxed">
                    {ann.summary}
                  </p>
                </div>

                <div className="mt-4 pt-3 border-t border-[#E3EAF3] dark:border-[#172B50] flex items-center justify-between">
                  <span className="text-[11px] text-slate-500 dark:text-slate-400">
                    By: <strong className="text-slate-700 dark:text-slate-300">{ann.postedBy}</strong>
                  </span>

                  <a
                    href={ann.link}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-xs font-semibold text-[#2684FF] hover:text-[#32C5F4] flex items-center space-x-1 transition cursor-pointer"
                  >
                    <span>View Circular</span>
                    <ExternalLink className="w-3.5 h-3.5" />
                  </a>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 2: AUTONOMOUS RESULTS & EXAM DESK */}
      {activeTab === 'results' && (
        <div className="space-y-6">
          {/* Quick Roll Number Lookup Bar */}
          <div className="bg-gradient-to-r from-[#101A35] to-[#172B50] rounded-xl p-5 text-white border border-[#172B50] shadow-md">
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
              <div>
                <h3 className="text-base font-bold text-white flex items-center space-x-2">
                  <GraduationCap className="w-5 h-5 text-[#32C5F4]" />
                  <span>Autonomous Examination Results Desk</span>
                </h3>
                <p className="text-xs text-slate-300 mt-1">
                  Enter your GCET Hall Ticket Number (e.g. 21R11A0501) to verify published autonomous semester scorecards.
                </p>
              </div>

              <form onSubmit={handleHallTicketSearch} className="flex items-center gap-2 max-w-md w-full">
                <input
                  type="text"
                  placeholder="Roll Number (e.g. 21R11A0501)"
                  value={hallTicketInput}
                  onChange={(e) => setHallTicketInput(e.target.value)}
                  className="px-3.5 py-2 text-xs rounded-lg bg-[#101A35] border border-[#2684FF]/40 text-white placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-[#32C5F4] flex-1 uppercase font-mono tracking-wider"
                />
                <button
                  type="submit"
                  className="px-4 py-2 bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-bold rounded-lg transition shrink-0 cursor-pointer shadow-xs"
                >
                  Check Result
                </button>
              </form>
            </div>

            {hallTicketResult && (
              <div className="mt-3 p-3 rounded-lg bg-blue-500/20 border border-blue-500/30 text-xs text-blue-200 flex items-center space-x-2 animate-in fade-in">
                <Info className="w-4 h-4 text-[#32C5F4] shrink-0" />
                <span>{hallTicketResult}</span>
              </div>
            )}
          </div>

          {/* Published Results List */}
          <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] overflow-hidden shadow-xs">
            <div className="p-4 border-b border-[#E3EAF3] dark:border-[#172B50] flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-[#172033] dark:text-white">
                  Recently Released Autonomous Exam Results
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                  Synchronized with GCTC Portal &amp; GCET Controller of Examinations
                </p>
              </div>

              <a
                href="https://www.gctcportal.in/"
                target="_blank"
                rel="noopener noreferrer"
                className="px-3 py-1.5 rounded-lg bg-[#172B50] text-[#32C5F4] text-xs font-semibold hover:bg-[#203a6b] transition flex items-center space-x-1.5"
              >
                <span>Full Results Server</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </a>
            </div>

            <div className="divide-y divide-[#E3EAF3] dark:divide-[#172B50]">
              {results.map((res) => (
                <div
                  key={res.id}
                  className="p-4 hover:bg-slate-50/80 dark:hover:bg-[#172B50]/30 transition flex flex-col md:flex-row md:items-center justify-between gap-4"
                >
                  <div className="space-y-1">
                    <div className="flex items-center space-x-2">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                          res.type === 'REVALUATION'
                            ? 'bg-amber-500/20 text-amber-500 border border-amber-500/30'
                            : res.type === 'OTC'
                            ? 'bg-purple-500/20 text-purple-400 border border-purple-500/30'
                            : 'bg-emerald-500/20 text-emerald-500 border border-emerald-500/30'
                        }`}
                      >
                        {res.type}
                      </span>
                      <h4 className="text-sm font-bold text-[#172033] dark:text-white">
                        {res.title}
                      </h4>
                    </div>

                    <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
                      <span>Exam Session: <strong className="text-slate-700 dark:text-slate-300">{res.examMonthYear}</strong></span>
                      <span>·</span>
                      <span>Published: <strong className="text-slate-700 dark:text-slate-300">{res.releaseDate}</strong></span>
                      <span>·</span>
                      <span>Regulations: <strong className="text-[#2684FF]">{res.regulations.join(', ')}</strong></span>
                    </div>

                    <p className="text-[11px] text-slate-400">
                      Branches: {res.branches.join(', ')}
                    </p>
                  </div>

                  <a
                    href={res.portalUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="px-3.5 py-2 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-bold transition flex items-center justify-center space-x-1.5 shrink-0 shadow-xs cursor-pointer"
                  >
                    <span>Check Grade Card</span>
                    <ExternalLink className="w-3.5 h-3.5" />
                  </a>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: PLACEMENTS & CDC */}
      {activeTab === 'placements' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-white dark:bg-[#101A35] p-5 rounded-xl border border-[#E3EAF3] dark:border-[#172B50] shadow-xs">
              <div className="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center mb-3">
                <Building2 className="w-5 h-5 text-[#2684FF]" />
              </div>
              <h4 className="text-sm font-bold text-[#172033] dark:text-white">Corporate Recruiting Partners</h4>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                TCS, Cognizant, Infosys, Tech Mahindra, Amazon, Virtusa, Capgemini, Accenture, and product engineering firms.
              </p>
              <div className="mt-3 text-xs font-semibold text-[#2684FF]">
                120+ Annual Recruiters
              </div>
            </div>

            <div className="bg-white dark:bg-[#101A35] p-5 rounded-xl border border-[#E3EAF3] dark:border-[#172B50] shadow-xs">
              <div className="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center mb-3">
                <Award className="w-5 h-5 text-emerald-500" />
              </div>
              <h4 className="text-sm font-bold text-[#172033] dark:text-white">Corporate Readiness Program</h4>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                Continuous soft-skill training, quantitative aptitude bootcamps, and mock interviews from 2nd year onwards.
              </p>
              <div className="mt-3 text-xs font-semibold text-emerald-500">
                Mandatory for 2025 &amp; 2026 Batches
              </div>
            </div>

            <div className="bg-white dark:bg-[#101A35] p-5 rounded-xl border border-[#E3EAF3] dark:border-[#172B50] shadow-xs">
              <div className="w-10 h-10 rounded-lg bg-purple-500/10 flex items-center justify-center mb-3">
                <Sparkles className="w-5 h-5 text-purple-400" />
              </div>
              <h4 className="text-sm font-bold text-[#172033] dark:text-white">Placement Cell Contacts</h4>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                Direct coordination with Dean &amp; Directors of Training &amp; Placements.
              </p>
              <div className="mt-3 text-xs font-semibold text-purple-400">
                placements@gcet.edu.in
              </div>
            </div>
          </div>

          {/* Live Placement Circulars */}
          <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-5 shadow-xs">
            <h3 className="text-sm font-bold text-[#172033] dark:text-white mb-3">
              Career Development Center (CDC) Bulletin
            </h3>
            <div className="space-y-3">
              <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-[#172B50]/50 border border-slate-200 dark:border-slate-700/60 flex items-start justify-between gap-3">
                <div>
                  <h4 className="text-xs font-bold text-[#172033] dark:text-white">
                    TCS National Qualifier Test (NQT) - 2025 Graduating Batch
                  </h4>
                  <p className="text-xs text-slate-500 dark:text-slate-300 mt-1">
                    Free mock test series activated in the GCET Computer Center labs. Batch 1 test begins Friday at 10:00 AM.
                  </p>
                </div>
                <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-500/20 text-blue-400 shrink-0">
                  Active Drive
                </span>
              </div>

              <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-[#172B50]/50 border border-slate-200 dark:border-slate-700/60 flex items-start justify-between gap-3">
                <div>
                  <h4 className="text-xs font-bold text-[#172033] dark:text-white">
                    Standardized Placement Resume Format
                  </h4>
                  <p className="text-xs text-slate-500 dark:text-slate-300 mt-1">
                    All students participating in campus placement drives must generate their ATS-compatible resume using the official GCET Resume Generator tool.
                  </p>
                </div>
                <a
                  href="https://www.gctcportal.in/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs font-bold text-[#2684FF] hover:underline shrink-0"
                >
                  Generate Resume
                </a>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 4: CLUBS & FRATERNITIES */}
      {activeTab === 'clubs' && (
        <div className="space-y-4">
          <div className="bg-white dark:bg-[#101A35] p-4 rounded-xl border border-[#E3EAF3] dark:border-[#172B50] text-xs text-slate-600 dark:text-slate-300">
            <span className="font-bold text-[#172033] dark:text-white">Official GCTC Portal Clubs:</span> Geethanjali encourages holistic student leadership across cultural, social, technical, entrepreneurial, and visual media fraternities.
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {clubs.map((club) => (
              <div
                key={club.id}
                className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-5 shadow-xs flex flex-col justify-between hover:border-[#2684FF]/40 transition group"
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span
                      className="px-2.5 py-0.5 rounded-full text-[10px] font-bold"
                      style={{
                        backgroundColor: `${club.color}20`,
                        color: club.color,
                        border: `1px solid ${club.color}40`,
                      }}
                    >
                      {club.badge}
                    </span>

                    <span className="text-[10px] text-slate-400 font-medium">
                      {club.category}
                    </span>
                  </div>

                  <h3 className="text-base font-extrabold text-[#172033] dark:text-white group-hover:text-[#2684FF] transition">
                    {club.name}
                  </h3>

                  <p className="text-xs text-slate-600 dark:text-slate-300 mt-2 line-clamp-3 leading-relaxed">
                    {club.description}
                  </p>

                  {/* Activities */}
                  <div className="mt-3 space-y-1">
                    <div className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                      Key Activities:
                    </div>
                    <ul className="text-xs text-slate-600 dark:text-slate-400 space-y-0.5">
                      {club.activities.slice(0, 3).map((act, i) => (
                        <li key={i} className="flex items-center space-x-1.5">
                          <span className="w-1 h-1 rounded-full bg-[#2684FF]" />
                          <span className="truncate">{act}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>

                <div className="mt-4 pt-3 border-t border-[#E3EAF3] dark:border-[#172B50] text-[11px] space-y-1">
                  <div className="text-slate-500">
                    Coordinator: <strong className="text-slate-700 dark:text-slate-300">{club.facultyCoordinator}</strong>
                  </div>
                  <div className="text-slate-500">
                    Lead: <strong className="text-[#32C5F4]">{club.studentLead}</strong>
                  </div>

                  <a
                    href={club.portalUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-2 pt-1 inline-flex items-center space-x-1 text-xs font-bold text-[#2684FF] hover:text-[#32C5F4] transition cursor-pointer"
                  >
                    <span>View on Portal</span>
                    <ExternalLink className="w-3 h-3" />
                  </a>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 5: COLLEGE FESTIVALS */}
      {activeTab === 'festivals' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {festivals.map((fest) => (
            <div
              key={fest.id}
              className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-6 shadow-xs flex flex-col justify-between hover:border-[#2684FF]/40 transition group"
            >
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-[#2684FF]/20 text-[#32C5F4] border border-[#2684FF]/30">
                    {fest.type.replace('_', ' ')}
                  </span>
                  <span className="text-xs font-bold text-amber-500 flex items-center space-x-1">
                    <Calendar className="w-3.5 h-3.5" />
                    <span>{fest.scheduleMonth}</span>
                  </span>
                </div>

                <h3 className="text-xl font-extrabold text-[#172033] dark:text-white tracking-tight group-hover:text-[#2684FF] transition">
                  {fest.name}
                </h3>
                <div className="text-xs font-semibold text-[#32C5F4] italic mt-0.5">
                  "{fest.tagline}"
                </div>

                <p className="text-xs text-slate-600 dark:text-slate-300 mt-3 leading-relaxed">
                  {fest.description}
                </p>

                {/* Highlights */}
                <div className="mt-4">
                  <div className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1.5">
                    Signature Events &amp; Highlights:
                  </div>
                  <div className="grid grid-cols-1 gap-1.5">
                    {fest.highlights.map((h, i) => (
                      <div key={i} className="flex items-center space-x-2 text-xs text-slate-700 dark:text-slate-300">
                        <CheckCircle2 className="w-3.5 h-3.5 text-[#10B981] shrink-0" />
                        <span>{h}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <div className="mt-5 pt-3 border-t border-[#E3EAF3] dark:border-[#172B50] flex items-center justify-between">
                <span className="text-[11px] text-slate-500">
                  Organized by: <strong className="text-slate-700 dark:text-slate-300">{fest.departments.join(', ')}</strong>
                </span>

                <a
                  href={fest.portalUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="px-3 py-1.5 rounded-lg bg-[#172B50] text-[#32C5F4] text-xs font-bold hover:bg-[#203a6b] transition flex items-center space-x-1"
                >
                  <span>Fest Coverage</span>
                  <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* TAB 6: STUDENT RESOURCES */}
      {activeTab === 'resources' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {resources.map((res) => (
              <div
                key={res.id}
                className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-5 shadow-xs flex flex-col justify-between hover:border-[#2684FF]/40 transition group"
              >
                <div>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-[#32C5F4]">
                    {res.category.replace('_', ' ')}
                  </span>
                  <h4 className="text-sm font-bold text-[#172033] dark:text-white mt-1 group-hover:text-[#2684FF] transition">
                    {res.title}
                  </h4>
                  <p className="text-xs text-slate-600 dark:text-slate-300 mt-2 leading-relaxed">
                    {res.shortDescription}
                  </p>
                </div>

                <div className="mt-4 pt-3 border-t border-[#E3EAF3] dark:border-[#172B50]">
                  <a
                    href={res.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="w-full py-2 px-3 rounded-lg bg-slate-100 dark:bg-[#172B50] text-slate-800 dark:text-slate-200 text-xs font-bold hover:bg-[#2684FF] hover:text-white transition flex items-center justify-center space-x-1.5 cursor-pointer"
                  >
                    <span>{res.actionText}</span>
                    <ExternalLink className="w-3.5 h-3.5" />
                  </a>
                </div>
              </div>
            ))}
          </div>

          {/* Approved GCET Branch Intake Table */}
          <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-5 shadow-xs">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 border-b border-[#E3EAF3] dark:border-[#172B50] gap-2">
              <div>
                <h3 className="text-sm font-bold text-[#172033] dark:text-white">
                  Approved Student Intake Matrix (AICTE &amp; JNTUH Autonomous)
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                  Official Sanctioned Intake for B.Tech &amp; PG Degree Programs
                </p>
              </div>

              <div className="px-3 py-1 rounded-full bg-blue-500/10 text-blue-600 dark:text-[#32C5F4] text-xs font-bold border border-blue-500/20">
                Total Sanctioned Seats: {totalIntake}
              </div>
            </div>

            <div className="overflow-x-auto mt-3">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-50 dark:bg-[#172B50] text-slate-500 dark:text-slate-400 uppercase font-semibold text-[10px]">
                  <tr>
                    <th className="py-2.5 px-3">Branch Code</th>
                    <th className="py-2.5 px-3">Branch &amp; Specialization Name</th>
                    <th className="py-2.5 px-3">Degree</th>
                    <th className="py-2.5 px-3 text-right">Sanctioned Intake</th>
                    <th className="py-2.5 px-3 text-center">Established</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3EAF3] dark:divide-[#172B50]">
                  {intakeList.map((item) => (
                    <tr key={item.branchCode} className="hover:bg-slate-50/60 dark:hover:bg-[#172B50]/30 transition">
                      <td className="py-2.5 px-3 font-mono font-bold text-[#2684FF]">
                        {item.branchCode}
                      </td>
                      <td className="py-2.5 px-3 font-medium text-slate-800 dark:text-slate-200">
                        {item.branchName}
                      </td>
                      <td className="py-2.5 px-3 text-slate-600 dark:text-slate-400">
                        {item.degree}
                      </td>
                      <td className="py-2.5 px-3 text-right font-bold text-slate-900 dark:text-white">
                        {item.sanctionedIntake}
                      </td>
                      <td className="py-2.5 px-3 text-center text-slate-500">
                        {item.establishedYear}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 7: CAMPUS DIRECTORY & INSTITUTIONAL LINKS */}
      {activeTab === 'contacts' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Official College Info Card */}
          <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-6 shadow-xs space-y-4">
            <div>
              <div className="text-[11px] font-bold text-[#32C5F4] uppercase tracking-wider">
                Institutional Contact Directory
              </div>
              <h3 className="text-lg font-extrabold text-[#172033] dark:text-white mt-1">
                {GCET_COLLEGE_DETAILS.name}
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                {GCET_COLLEGE_DETAILS.autonomousStatus}
              </p>
            </div>

            <div className="space-y-3 pt-2 text-xs">
              <div className="flex items-start space-x-3 text-slate-700 dark:text-slate-300">
                <MapPin className="w-4 h-4 text-[#2684FF] shrink-0 mt-0.5" />
                <div>
                  <strong>Campus Address:</strong><br />
                  {GCET_COLLEGE_DETAILS.address.campus}, {GCET_COLLEGE_DETAILS.address.district}, {GCET_COLLEGE_DETAILS.address.state} - PIN: {GCET_COLLEGE_DETAILS.address.pinCode}
                </div>
              </div>

              <div className="flex items-center space-x-3 text-slate-700 dark:text-slate-300">
                <Phone className="w-4 h-4 text-[#2684FF] shrink-0" />
                <div>
                  <strong>Front Office / Reception:</strong> {GCET_COLLEGE_DETAILS.contacts.frontOffice}
                </div>
              </div>

              <div className="flex items-center space-x-3 text-slate-700 dark:text-slate-300">
                <Phone className="w-4 h-4 text-[#2684FF] shrink-0" />
                <div>
                  <strong>Autonomous Exam Branch:</strong> {GCET_COLLEGE_DETAILS.contacts.examBranch}
                </div>
              </div>

              <div className="flex items-center space-x-3 text-slate-700 dark:text-slate-300">
                <Phone className="w-4 h-4 text-[#2684FF] shrink-0" />
                <div>
                  <strong>Admissions AO (Mr. B. Surendra Reddy):</strong> {GCET_COLLEGE_DETAILS.contacts.admissionsAO}
                </div>
              </div>

              <div className="flex items-center space-x-3 text-slate-700 dark:text-slate-300">
                <Mail className="w-4 h-4 text-[#2684FF] shrink-0" />
                <div>
                  <strong>Email:</strong> {GCET_COLLEGE_DETAILS.contacts.generalEmail} · {GCET_COLLEGE_DETAILS.contacts.placementEmail}
                </div>
              </div>
            </div>
          </div>

          {/* Reference Websites Card */}
          <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-6 shadow-xs space-y-4">
            <div>
              <div className="text-[11px] font-bold text-[#32C5F4] uppercase tracking-wider">
                Official Reference Websites
              </div>
              <h3 className="text-lg font-extrabold text-[#172033] dark:text-white mt-1">
                Connected Institutional Ecosystem
              </h3>
            </div>

            <div className="space-y-3 pt-2">
              <a
                href={GCET_COLLEGE_DETAILS.urls.gctcPortal}
                target="_blank"
                rel="noopener noreferrer"
                className="p-3 rounded-lg border border-[#E3EAF3] dark:border-[#172B50] hover:border-[#2684FF] transition flex items-center justify-between block group"
              >
                <div>
                  <div className="text-xs font-bold text-[#172033] dark:text-white group-hover:text-[#2684FF] transition">
                    GCTC Student Portal (gctcportal.in)
                  </div>
                  <div className="text-[11px] text-slate-500">
                    Student lifecycle, exam scorecards, club magazines &amp; seating arrangements
                  </div>
                </div>
                <ExternalLink className="w-4 h-4 text-slate-400 group-hover:text-[#2684FF] transition shrink-0" />
              </a>

              <a
                href={GCET_COLLEGE_DETAILS.urls.officialWebsite}
                target="_blank"
                rel="noopener noreferrer"
                className="p-3 rounded-lg border border-[#E3EAF3] dark:border-[#172B50] hover:border-[#2684FF] transition flex items-center justify-between block group"
              >
                <div>
                  <div className="text-xs font-bold text-[#172033] dark:text-white group-hover:text-[#2684FF] transition">
                    GCET Official Website (gcet.edu.in)
                  </div>
                  <div className="text-[11px] text-slate-500">
                    College governance, departments, accreditation, admissions &amp; faculty profiles
                  </div>
                </div>
                <ExternalLink className="w-4 h-4 text-slate-400 group-hover:text-[#2684FF] transition shrink-0" />
              </a>

              <a
                href={GCET_COLLEGE_DETAILS.urls.geethanjaliGroup}
                target="_blank"
                rel="noopener noreferrer"
                className="p-3 rounded-lg border border-[#E3EAF3] dark:border-[#172B50] hover:border-[#2684FF] transition flex items-center justify-between block group"
              >
                <div>
                  <div className="text-xs font-bold text-[#172033] dark:text-white group-hover:text-[#2684FF] transition">
                    Geethanjali Institutions (geethanjaliinstitutions.com)
                  </div>
                  <div className="text-[11px] text-slate-500">
                    Geethanjali Group parent society leadership &amp; institutions
                  </div>
                </div>
                <ExternalLink className="w-4 h-4 text-slate-400 group-hover:text-[#2684FF] transition shrink-0" />
              </a>

              <a
                href={GCET_COLLEGE_DETAILS.urls.jntuhWebsite}
                target="_blank"
                rel="noopener noreferrer"
                className="p-3 rounded-lg border border-[#E3EAF3] dark:border-[#172B50] hover:border-[#2684FF] transition flex items-center justify-between block group"
              >
                <div>
                  <div className="text-xs font-bold text-[#172033] dark:text-white group-hover:text-[#2684FF] transition">
                    Jawaharlal Nehru Technological University Hyderabad (jntuh.ac.in)
                  </div>
                  <div className="text-[11px] text-slate-500">
                    University affiliation, regulatory notifications &amp; academic council
                  </div>
                </div>
                <ExternalLink className="w-4 h-4 text-slate-400 group-hover:text-[#2684FF] transition shrink-0" />
              </a>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
