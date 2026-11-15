import React, { useState } from 'react';
import {
  ShieldCheck,
  UserCheck,
  Building2,
  GraduationCap,
  ExternalLink,
  Award,
  ChevronRight,
  BookOpen,
  ArrowRight,
  Sparkles,
  Phone,
  Mail,
  MapPin,
  CheckCircle2,
  Lock,
  Compass,
  Users,
  Target,
  Briefcase,
  TrendingUp,
  BarChart3,
  Layers,
  FileCheck2,
  Search,
  Check,
} from 'lucide-react';
import { GCET_CAMPUS_BUILDING_PHOTO, GCET_COLLEGE_DETAILS, GCET_OFFICIAL_LOGO } from '../assets/campusImages';
import { CampusHero } from '../components/CampusHero';

export type AuthPortalCategory = 'ADMIN' | 'FACULTY' | 'COMPANIES' | 'STUDENTS';
export type AuthPortalSubView = 'LOGIN' | 'REGISTER';

interface WelcomePageProps {
  onOpenPortal: (category: AuthPortalCategory, subView: AuthPortalSubView) => void;
  theme?: 'light' | 'dark';
}

export const WelcomePage: React.FC<WelcomePageProps> = ({ onOpenPortal }) => {
  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'PLACEMENT_CELL' | 'ACCREDITATIONS'>('OVERVIEW');

  return (
    <div className="min-h-screen bg-white text-slate-800 flex flex-col font-sans selection:bg-[#2684FF]/20 selection:text-[#0f1d3d]">
      
      {/* 1. TOP INSTITUTIONAL ACCREDITATION STRIP (Crisp & Clean) */}
      <div className="bg-slate-100/90 text-slate-700 text-xs py-2 px-4 border-b border-slate-200">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2 text-center sm:text-left">
          <div className="flex items-center gap-2 flex-wrap justify-center">
            <span className="inline-flex items-center gap-1 font-bold text-amber-800 bg-amber-100/80 px-2.5 py-0.5 rounded-full border border-amber-300 text-[11px]">
              <Award className="w-3.5 h-3.5 text-amber-700" /> UGC AUTONOMOUS
            </span>
            <span className="font-semibold text-slate-800">NAAC 'A+' Grade</span>
            <span className="text-slate-400">·</span>
            <span className="text-slate-600">NBA Accredited Branches (CSE, ECE, EEE, MECH)</span>
            <span className="text-slate-400">·</span>
            <span className="text-slate-600">Affiliated to JNTU Hyderabad</span>
          </div>
          <div className="flex items-center gap-4 text-[11px] text-slate-600">
            <span className="flex items-center gap-1">
              <MapPin className="w-3 h-3 text-[#2684FF]" /> Cheeryal, Keesara, Telangana - 501301
            </span>
            <a
              href="https://gcet.edu.in"
              target="_blank"
              rel="noreferrer"
              className="hover:text-[#2684FF] font-medium flex items-center gap-0.5 transition-colors"
            >
              gcet.edu.in <ExternalLink className="w-2.5 h-2.5" />
            </a>
          </div>
        </div>
      </div>

      {/* 2. PUBLIC HEADER (Bright, Clean, Modern University Branding) */}
      <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-slate-200 shadow-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3.5 flex items-center justify-between">
          <div className="flex items-center gap-3 sm:gap-4">
            {/* Official GCET Logo */}
            <div className="w-12 h-12 rounded-full bg-white p-0.5 border border-slate-200 shadow-sm flex items-center justify-center shrink-0 overflow-hidden">
              <img
                src={GCET_OFFICIAL_LOGO}
                alt="Geethanjali College of Engineering and Technology (GCET) Official Logo"
                className="w-full h-full object-contain"
              />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-base sm:text-lg text-[#0f1d3d] tracking-tight">
                  GCET <span className="text-[#2684FF]">PLACEMENT HUB</span>
                </span>
                <span className="hidden md:inline-flex text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-blue-50 text-[#2684FF] border border-blue-200">
                  Institutional Portal
                </span>
              </div>
              <p className="text-xs text-slate-500 font-medium line-clamp-1">
                Geethanjali College of Engineering and Technology · Hyderabad
              </p>
            </div>
          </div>

          {/* Clean Action Buttons */}
          <div className="flex items-center gap-2 sm:gap-3">
            <button
              onClick={() => onOpenPortal('STUDENTS', 'LOGIN')}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold text-slate-700 bg-slate-100 hover:bg-slate-200 border border-slate-200 transition-all cursor-pointer"
            >
              <GraduationCap className="w-3.5 h-3.5 text-[#2684FF]" />
              <span>Student Sign In</span>
            </button>
            <button
              onClick={() => onOpenPortal('ADMIN', 'LOGIN')}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold text-white bg-[#2684FF] hover:bg-blue-600 shadow-sm shadow-blue-500/20 transition-all cursor-pointer"
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Portal Access</span>
            </button>
          </div>
        </div>
      </header>

      {/* 3. UNIFIED CAMPUS HERO SECTION (Photograph as Full Background + Content & Portal Actions Inside) */}
      <section className="bg-gradient-to-b from-sky-50/60 via-white to-blue-50/20 py-6 sm:py-10 px-4 sm:px-6 lg:px-8 border-b border-slate-200">
        <div className="max-w-7xl mx-auto">
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
            description="Welcome to GCET Placement Hub, a centralized platform connecting students, faculty and recruiting companies through student profiles, skill development, placement drives and career opportunities."
            details={
              <>
                <span className="flex items-center space-x-1.5 text-slate-200 font-medium bg-black/40 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/15 shadow-xs">
                  <MapPin className="w-3.5 h-3.5 text-[#32C5F4]" />
                  <span>Cheeryal, Keesara, Telangana - 501301</span>
                </span>
                <a
                  href="https://www.gctcportal.in/"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-blue-500/20 hover:bg-blue-500/30 backdrop-blur-md border border-cyan-400/40 text-[#32C5F4] hover:text-white font-bold transition-all cursor-pointer shadow-xs"
                >
                  <span>Official GCTC Academic Portal</span>
                  <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </>
            }
            actions={
              <>
                {/* Primary Student Sign In */}
                <button
                  onClick={() => onOpenPortal('STUDENTS', 'LOGIN')}
                  className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-[#2684FF] to-blue-600 hover:from-blue-500 hover:to-blue-700 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-blue-600/30 transition-all cursor-pointer border border-blue-400/40 active:scale-[0.98]"
                >
                  <GraduationCap className="w-4 h-4 text-white" />
                  <span>Student Sign In</span>
                </button>

                {/* Secondary Portal Access / Select Portal */}
                <button
                  onClick={() => {
                    const el = document.getElementById('account-selection-section');
                    el?.scrollIntoView({ behavior: 'smooth' });
                  }}
                  className="w-full py-3 px-4 rounded-xl bg-slate-900/70 hover:bg-slate-800/80 backdrop-blur-md text-white font-semibold text-xs border border-white/20 hover:border-cyan-400/50 flex items-center justify-center gap-2 shadow-md transition-all cursor-pointer active:scale-[0.98]"
                >
                  <ShieldCheck className="w-4 h-4 text-[#32C5F4]" />
                  <span>Portal Access &amp; Role Gateways</span>
                  <ArrowRight className="w-3.5 h-3.5 text-slate-300" />
                </button>

                {/* Direct Register Link */}
                <button
                  onClick={() => onOpenPortal('STUDENTS', 'REGISTER')}
                  className="w-full py-2.5 px-4 rounded-xl bg-white/10 hover:bg-white/20 backdrop-blur-md text-cyan-200 hover:text-white font-semibold text-xs border border-white/15 transition-all cursor-pointer flex items-center justify-center gap-1.5"
                >
                  <span>New Student Registration</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>

                {/* Micro info strip */}
                <div className="pt-1 text-[11px] text-slate-300 text-center flex items-center justify-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                  <span>4 Portals: Admin · Faculty · Companies · Students</span>
                </div>
              </>
            }
          />
        </div>
      </section>

      {/* 4. ACCOUNT SELECTION SECTION (Bright, Clean, Modern 4-Column Cards) */}
      <section
        id="account-selection-section"
        className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16 w-full"
      >
        <div className="text-center max-w-2xl mx-auto mb-12">
          <span className="text-xs font-bold uppercase tracking-wider text-[#2684FF] bg-blue-50 px-3 py-1 rounded-full border border-blue-200">
            Account Selection Area
          </span>
          <h2 className="text-2xl sm:text-3xl font-black text-[#0f1d3d] mt-3">
            Choose Your Institutional Portal
          </h2>
          <p className="text-slate-600 text-sm mt-2">
            Secure, separate access gateways designed for College Leadership, Faculty Coordinators, Corporate Recruiters, and Engineering Students.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          
          {/* CARD 1: ADMIN */}
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg transition-all duration-300 p-6 flex flex-col justify-between group hover:border-blue-400">
            <div>
              <div className="w-12 h-12 rounded-xl bg-[#0f1d3d] text-white flex items-center justify-center mb-4 shadow-sm group-hover:scale-105 transition-transform">
                <ShieldCheck className="w-6 h-6 text-sky-400" />
              </div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-[#0f1d3d]">ADMIN</h3>
                <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-blue-50 text-[#2684FF] border border-blue-200">
                  Leadership
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Dedicated portal for the <strong>Principal</strong>, <strong>Chairman</strong>, <strong>Vice Chairman</strong>, and Placement Officers to oversee institutional policy, approvals, and recruitment drives.
              </p>

              <div className="mt-4 pt-3 border-t border-slate-100 space-y-1.5 text-[11px] text-slate-600">
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-[#2684FF]" /> Approves Faculty Registrations
                </div>
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-[#2684FF]" /> Approves Corporate Recruiters
                </div>
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-[#2684FF]" /> Oversees Campus Placements
                </div>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-100">
              <button
                onClick={() => onOpenPortal('ADMIN', 'LOGIN')}
                className="w-full py-2.5 px-3 rounded-xl bg-[#0f1d3d] hover:bg-slate-800 text-white text-xs font-bold flex items-center justify-center gap-1.5 shadow-sm transition-all cursor-pointer"
              >
                <Lock className="w-3.5 h-3.5 text-sky-400" />
                <span>Admin Sign In</span>
              </button>
            </div>
          </div>

          {/* CARD 2: FACULTY */}
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg transition-all duration-300 p-6 flex flex-col justify-between group hover:border-emerald-400">
            <div>
              <div className="w-12 h-12 rounded-xl bg-emerald-600 text-white flex items-center justify-center mb-4 shadow-sm group-hover:scale-105 transition-transform">
                <UserCheck className="w-6 h-6" />
              </div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-[#0f1d3d]">FACULTY</h3>
                <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">
                  Coordinators
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Departmental coordinators verify student registrations, validate technical skill assessments, and manage departmental student cohorts.
              </p>

              <div className="mt-4 pt-3 border-t border-slate-100 space-y-1.5 text-[11px] text-slate-600">
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Department Student Approval Gate
                </div>
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Verified Skill Matrix Endorsement
                </div>
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Department-Level Reports
                </div>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-100 flex flex-col gap-2">
              <button
                onClick={() => onOpenPortal('FACULTY', 'LOGIN')}
                className="w-full py-2 px-3 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold flex items-center justify-center gap-1.5 shadow-sm transition-all cursor-pointer"
              >
                <UserCheck className="w-3.5 h-3.5" />
                <span>Faculty Sign In</span>
              </button>
              <button
                onClick={() => onOpenPortal('FACULTY', 'REGISTER')}
                className="w-full py-1.5 px-3 rounded-xl bg-emerald-50 hover:bg-emerald-100 text-emerald-800 text-[11px] font-semibold border border-emerald-200 flex items-center justify-center gap-1 transition-all cursor-pointer"
              >
                <span>Faculty Registration</span>
                <ChevronRight className="w-3 h-3" />
              </button>
            </div>
          </div>

          {/* CARD 3: COMPANIES */}
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm hover:shadow-lg transition-all duration-300 p-6 flex flex-col justify-between group hover:border-purple-400">
            <div>
              <div className="w-12 h-12 rounded-xl bg-purple-600 text-white flex items-center justify-center mb-4 shadow-sm group-hover:scale-105 transition-transform">
                <Building2 className="w-6 h-6" />
              </div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-[#0f1d3d]">COMPANIES</h3>
                <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-purple-50 text-purple-700 border border-purple-200">
                  Recruiters
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Corporate recruiters post hiring drives, filter candidates by technical skill scores, and manage shortlisted engineering talent.
              </p>

              <div className="mt-4 pt-3 border-t border-slate-100 space-y-1.5 text-[11px] text-slate-600">
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-purple-600" /> Post Placement Drives
                </div>
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-purple-600" /> Filter by Verified Skill Scores
                </div>
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-purple-600" /> Multi-Stage Candidate Tracking
                </div>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-100 flex flex-col gap-2">
              <button
                onClick={() => onOpenPortal('COMPANIES', 'LOGIN')}
                className="w-full py-2 px-3 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold flex items-center justify-center gap-1.5 shadow-sm transition-all cursor-pointer"
              >
                <Building2 className="w-3.5 h-3.5" />
                <span>Recruiter Sign In</span>
              </button>
              <button
                onClick={() => onOpenPortal('COMPANIES', 'REGISTER')}
                className="w-full py-1.5 px-3 rounded-xl bg-purple-50 hover:bg-purple-100 text-purple-800 text-[11px] font-semibold border border-purple-200 flex items-center justify-center gap-1 transition-all cursor-pointer"
              >
                <span>Company Registration</span>
                <ChevronRight className="w-3 h-3" />
              </button>
            </div>
          </div>

          {/* CARD 4: STUDENTS */}
          <div className="bg-white rounded-2xl border-2 border-[#2684FF] shadow-md hover:shadow-xl transition-all duration-300 p-6 flex flex-col justify-between group relative overflow-hidden">
            <div className="absolute top-0 right-0 bg-[#2684FF] text-white text-[9px] font-extrabold uppercase px-3 py-1 rounded-bl-lg tracking-wider">
              Student Hub
            </div>
            <div>
              <div className="w-12 h-12 rounded-xl bg-[#2684FF] text-white flex items-center justify-center mb-4 shadow-sm group-hover:scale-105 transition-transform">
                <GraduationCap className="w-6 h-6" />
              </div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-[#0f1d3d]">STUDENTS</h3>
                <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-blue-50 text-[#2684FF] border border-blue-200">
                  Candidates
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                GCET engineering students register dynamically, build verified skill matrices, track eligibility, and apply to top tier recruitment drives.
              </p>

              <div className="mt-4 pt-3 border-t border-slate-100 space-y-1.5 text-[11px] text-slate-600">
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-[#2684FF]" /> 2-Field Direct Roll Number Login
                </div>
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-[#2684FF]" /> Real-time Eligibility Matching
                </div>
                <div className="flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-[#2684FF]" /> Department Faculty Approval Gate
                </div>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-100 flex flex-col gap-2">
              <button
                onClick={() => onOpenPortal('STUDENTS', 'LOGIN')}
                className="w-full py-2.5 px-3 rounded-xl bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-bold flex items-center justify-center gap-1.5 shadow-sm shadow-blue-500/20 transition-all cursor-pointer"
              >
                <GraduationCap className="w-4 h-4" />
                <span>Student Sign In</span>
              </button>
              <button
                onClick={() => onOpenPortal('STUDENTS', 'REGISTER')}
                className="w-full py-1.5 px-3 rounded-xl bg-blue-50 hover:bg-blue-100 text-[#2684FF] text-[11px] font-bold border border-blue-200 flex items-center justify-center gap-1 transition-all cursor-pointer"
              >
                <span>Student Registration</span>
                <ChevronRight className="w-3 h-3" />
              </button>
            </div>
          </div>

        </div>
      </section>

      {/* 5. APPLICATION FEATURES SECTION (Requirement 7: Clean Feature Cards) */}
      <section className="bg-slate-50/70 py-16 px-4 sm:px-6 lg:px-8 border-t border-slate-200">
        <div className="max-w-7xl mx-auto">
          
          <div className="text-center max-w-2xl mx-auto mb-12">
            <span className="text-xs font-bold uppercase tracking-wider text-[#2684FF] bg-blue-50 px-3 py-1 rounded-full border border-blue-200">
              Platform Modules
            </span>
            <h2 className="text-2xl sm:text-3xl font-black text-[#0f1d3d] mt-3">
              Comprehensive Placement Features
            </h2>
            <p className="text-slate-600 text-sm mt-2">
              Explore the core capabilities built into the GCET Placement Hub to streamline campus recruitment and skill readiness.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            
            {/* Feature 1 */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-blue-50 text-[#2684FF] flex items-center justify-center mb-3.5">
                <Users className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-[#0f1d3d]">Student Profile Management</h3>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Centralized academic profiles with verified CGPA, 10th &amp; Intermediate percentages, branch specialization, and resume links.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-cyan-50 text-cyan-600 flex items-center justify-center mb-3.5">
                <Target className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-[#0f1d3d]">Technical Skill Matrix</h3>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                12+ core engineering skills evaluated across Java, Python, SQL, and Data Structures with official faculty endorsement ratings.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center mb-3.5">
                <CheckCircle2 className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-[#0f1d3d]">Placement Eligibility Engine</h3>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Automated multi-criteria evaluation matching student qualifications against drive minimum CGPA, eligible branches, and backlog policies.
              </p>
            </div>

            {/* Feature 4 */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center mb-3.5">
                <Building2 className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-[#0f1d3d]">Company Management</h3>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Structured hiring partner directory categorized by Tier 1 (Dream), Tier 2 (Super Dream), and Core engineering domains.
              </p>
            </div>

            {/* Feature 5 */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center mb-3.5">
                <Briefcase className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-[#0f1d3d]">Placement Drive Management</h3>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Publish and manage campus drives, CTC salary packages, registration deadlines, online assessments, and interview round logistics.
              </p>
            </div>

            {/* Feature 6 */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-teal-50 text-teal-600 flex items-center justify-center mb-3.5">
                <UserCheck className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-[#0f1d3d]">Student Approval System</h3>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Role-gated departmental verification gate where authorized faculty coordinators review and approve student registrations before login.
              </p>
            </div>

            {/* Feature 7 */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center mb-3.5">
                <TrendingUp className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-[#0f1d3d]">Placement Tracking</h3>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Real-time tracking of candidate progression across application, test shortlisted, technical rounds, HR interview, and final offer letters.
              </p>
            </div>

            {/* Feature 8 */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center mb-3.5">
                <BarChart3 className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-[#0f1d3d]">Reports &amp; Analytics</h3>
              <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                Database-driven institutional reports, department-wise placement percentages, salary distribution charts, and exportable data.
              </p>
            </div>

          </div>

        </div>
      </section>

      {/* 6. ABOUT GCET & INSTITUTION DETAILS SECTION */}
      <section className="bg-white py-14 px-4 sm:px-6 lg:px-8 border-t border-slate-200">
        <div className="max-w-7xl mx-auto">
          <div className="flex flex-wrap items-center justify-center gap-2 mb-8">
            <button
              onClick={() => setActiveTab('OVERVIEW')}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                activeTab === 'OVERVIEW'
                  ? 'bg-[#0f1d3d] text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Institution Overview
            </button>
            <button
              onClick={() => setActiveTab('PLACEMENT_CELL')}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                activeTab === 'PLACEMENT_CELL'
                  ? 'bg-[#0f1d3d] text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              About Placement Cell
            </button>
            <button
              onClick={() => setActiveTab('ACCREDITATIONS')}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                activeTab === 'ACCREDITATIONS'
                  ? 'bg-[#0f1d3d] text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Accreditations &amp; Honors
            </button>
          </div>

          <div className="bg-slate-50 rounded-2xl border border-slate-200 p-6 sm:p-8">
            {activeTab === 'OVERVIEW' && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center">
                <div>
                  <h3 className="text-xl font-bold text-[#0f1d3d]">
                    Geethanjali College of Engineering and Technology
                  </h3>
                  <p className="text-xs font-semibold text-[#2684FF] mt-0.5">
                    Cheeryal (V), Keesara (M), Medchal-Malkajgiri District, Telangana - 501301
                  </p>
                  <p className="text-sm text-slate-600 mt-4 leading-relaxed">
                    Established in 2005 under the visionary leadership of the Teja Educational Society, Geethanjali College of Engineering and Technology stands as an autonomous pillar of academic and technical excellence. Spanning a verdant, state-of-the-art campus in Cheeryal, the institution imparts industry-aligned education in Computer Science, Artificial Intelligence, Data Science, Cyber Security, Electronics, Electrical, Mechanical, and Civil Engineering.
                  </p>
                  <div className="mt-6 flex flex-wrap gap-2 text-xs font-medium text-slate-700">
                    <span className="px-3 py-1 bg-white rounded-lg border border-slate-200 shadow-xs">
                      UGC Autonomous Since 2016
                    </span>
                    <span className="px-3 py-1 bg-white rounded-lg border border-slate-200 shadow-xs">
                      Approved by AICTE New Delhi
                    </span>
                    <span className="px-3 py-1 bg-white rounded-lg border border-slate-200 shadow-xs">
                      Affiliated to JNTU Hyderabad
                    </span>
                  </div>
                </div>

                <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-xs">
                  <h4 className="text-xs font-bold text-[#2684FF] uppercase tracking-wider mb-4">
                    Institutional Leadership
                  </h4>
                  <div className="space-y-3 text-xs">
                    <div className="flex items-center justify-between pb-2.5 border-b border-slate-100">
                      <div>
                        <span className="font-bold text-[#0f1d3d] block">Mr. G. R. Ravinder Reddy</span>
                        <span className="text-[11px] text-slate-400">Teja Educational Society</span>
                      </div>
                      <span className="text-xs font-bold text-slate-700 bg-slate-100 px-2.5 py-1 rounded-md">
                        Chairman
                      </span>
                    </div>
                    <div className="flex items-center justify-between pb-2.5 border-b border-slate-100">
                      <div>
                        <span className="font-bold text-[#0f1d3d] block">Mr. R. Harishchandra Reddy</span>
                        <span className="text-[11px] text-slate-400">Institutional Governance</span>
                      </div>
                      <span className="text-xs font-bold text-slate-700 bg-slate-100 px-2.5 py-1 rounded-md">
                        Vice Chairman
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="font-bold text-[#0f1d3d] block">Dr. K. Sagar</span>
                        <span className="text-[11px] text-slate-400">Executive Head of Institution</span>
                      </div>
                      <span className="text-xs font-bold text-[#2684FF] bg-blue-50 px-2.5 py-1 rounded-md border border-blue-200">
                        Principal
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'PLACEMENT_CELL' && (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-xs">
                  <div className="w-8 h-8 rounded-lg bg-blue-100 text-[#2684FF] flex items-center justify-center font-bold text-xs mb-3">
                    01
                  </div>
                  <h4 className="text-sm font-bold text-[#0f1d3d]">Industry Alignment</h4>
                  <p className="text-xs text-slate-600 mt-2 leading-relaxed">
                    Bridging academia and the modern tech ecosystem through corporate training modules, guest masterclasses, and hands-on hackathons.
                  </p>
                </div>

                <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-xs">
                  <div className="w-8 h-8 rounded-lg bg-cyan-100 text-cyan-700 flex items-center justify-center font-bold text-xs mb-3">
                    02
                  </div>
                  <h4 className="text-sm font-bold text-[#0f1d3d]">Skill Matrix Benchmarking</h4>
                  <p className="text-xs text-slate-600 mt-2 leading-relaxed">
                    Evaluating students across Programming, Data Structures, Cloud, and AI with faculty-verified endorsements rather than unverified ratings.
                  </p>
                </div>

                <div className="p-5 bg-white rounded-xl border border-slate-200 shadow-xs">
                  <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center font-bold text-xs mb-3">
                    03
                  </div>
                  <h4 className="text-sm font-bold text-[#0f1d3d]">Rigorous Approval Gates</h4>
                  <p className="text-xs text-slate-600 mt-2 leading-relaxed">
                    Strict role segregation ensures only verified students and authorized corporate recruiters participate in on-campus recruitment cycles.
                  </p>
                </div>
              </div>
            )}

            {activeTab === 'ACCREDITATIONS' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 text-center">
                <div className="p-5 rounded-xl border border-amber-200 bg-white shadow-xs">
                  <Award className="w-8 h-8 text-amber-600 mx-auto mb-2" />
                  <h4 className="text-sm font-bold text-amber-900">NAAC 'A+' Grade</h4>
                  <p className="text-xs text-slate-500 mt-1">National Assessment &amp; Accreditation Council</p>
                </div>
                <div className="p-5 rounded-xl border border-blue-200 bg-white shadow-xs">
                  <Award className="w-8 h-8 text-[#2684FF] mx-auto mb-2" />
                  <h4 className="text-sm font-bold text-blue-900">NBA Accredited</h4>
                  <p className="text-xs text-slate-500 mt-1">National Board of Accreditation for Core Branches</p>
                </div>
                <div className="p-5 rounded-xl border border-emerald-200 bg-white shadow-xs">
                  <Award className="w-8 h-8 text-emerald-600 mx-auto mb-2" />
                  <h4 className="text-sm font-bold text-emerald-900">UGC Autonomous</h4>
                  <p className="text-xs text-slate-500 mt-1">Autonomous Status Conferred by UGC New Delhi</p>
                </div>
                <div className="p-5 rounded-xl border border-purple-200 bg-white shadow-xs">
                  <Award className="w-8 h-8 text-purple-600 mx-auto mb-2" />
                  <h4 className="text-sm font-bold text-purple-900">JNTUH Affiliation</h4>
                  <p className="text-xs text-slate-500 mt-1">Permanent Affiliation to JNTU Hyderabad</p>
                </div>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* 7. PUBLIC FOOTER (Clean & Professional) */}
      <footer className="bg-[#0f1d3d] text-slate-300 text-xs py-12 px-4 sm:px-6 lg:px-8 border-t border-slate-800">
        <div className="max-w-7xl mx-auto grid grid-cols-1 md:grid-cols-4 gap-8 mb-8">
          
          <div className="md:col-span-2">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-10 h-10 rounded-full bg-white p-0.5 shadow-md flex items-center justify-center shrink-0 overflow-hidden">
                <img
                  src={GCET_OFFICIAL_LOGO}
                  alt="Geethanjali College of Engineering and Technology (GCET) Official Logo"
                  className="w-full h-full object-contain"
                />
              </div>
              <span className="font-extrabold text-white text-base tracking-tight">
                GCET PLACEMENT HUB
              </span>
            </div>
            <p className="text-xs text-slate-400 max-w-md leading-relaxed">
              Geethanjali College of Engineering and Technology. An Autonomous Institution committed to shaping industry-ready engineering professionals through rigorous academic curricula, skill centers, and seamless placement workflows.
            </p>
            <p className="text-[11px] text-cyan-300 mt-3 font-semibold">
              Tagline: "Your Skills. Your Opportunities. Your Future."
            </p>
          </div>

          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-3">
              Institutional Portals
            </h4>
            <ul className="space-y-2 text-xs">
              <li>
                <a
                  href="https://www.gctcportal.in/"
                  target="_blank"
                  rel="noreferrer"
                  className="hover:text-cyan-400 flex items-center gap-1 transition-colors text-slate-300"
                >
                  GCTC Student &amp; Faculty Portal <ExternalLink className="w-3 h-3" />
                </a>
              </li>
              <li>
                <a
                  href="https://gcet.edu.in/"
                  target="_blank"
                  rel="noreferrer"
                  className="hover:text-cyan-400 flex items-center gap-1 transition-colors text-slate-300"
                >
                  Official GCET Website <ExternalLink className="w-3 h-3" />
                </a>
              </li>
              <li>
                <a
                  href="https://gcet.edu.in/placements.html"
                  target="_blank"
                  rel="noreferrer"
                  className="hover:text-cyan-400 flex items-center gap-1 transition-colors text-slate-300"
                >
                  Training &amp; Placement Cell <ExternalLink className="w-3 h-3" />
                </a>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-3">
              Campus Contact
            </h4>
            <div className="space-y-1.5 text-xs text-slate-400">
              <p className="text-white font-medium">Geethanjali College of Engg. &amp; Tech.</p>
              <p>Sy. No. 33 &amp; 34, Cheeryal (V), Keesara (M)</p>
              <p>Medchal-Malkajgiri Dist., Telangana - 501301</p>
              <p className="pt-2 flex items-center gap-1.5 text-slate-300">
                <Phone className="w-3.5 h-3.5 text-cyan-400" /> +91 9182058187
              </p>
              <p className="flex items-center gap-1.5 text-slate-300">
                <Mail className="w-3.5 h-3.5 text-cyan-400" /> placements@gcet.edu.in
              </p>
            </div>
          </div>
        </div>

        <div className="max-w-7xl mx-auto pt-6 border-t border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-3 text-[11px] text-slate-400 text-center sm:text-left">
          <p>© {new Date().getFullYear()} Geethanjali College of Engineering and Technology. All rights reserved.</p>
          <p className="text-slate-400">GCET Placement Hub · Free Multi-User Institutional Platform</p>
        </div>
      </footer>
    </div>
  );
};
