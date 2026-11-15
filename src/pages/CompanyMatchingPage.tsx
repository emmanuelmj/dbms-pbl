import React, { useState, useMemo, useEffect } from 'react';
import {
  Target,
  GraduationCap,
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  Building2,
  Filter,
  ArrowRight,
  Send,
  HelpCircle,
} from 'lucide-react';
import { Student, User, CompanyMatch, PlacementDrive, Company, Skill, Application } from '../types';
import { matchingEngine } from '../services/matchingEngine';
import { api } from '../services/api';

interface CompanyMatchingPageProps {
  currentUser: User | null;
  students: Student[];
  drives: PlacementDrive[];
  companies: Company[];
  skills: Skill[];
  applications?: Application[];
  initialStudentId?: string;
  onCheckEligibility: (driveId: string) => void;
  onRefreshData: () => Promise<void>;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

export const CompanyMatchingPage: React.FC<CompanyMatchingPageProps> = ({
  currentUser,
  students,
  drives,
  companies,
  skills,
  applications = [],
  initialStudentId,
  onCheckEligibility,
  onRefreshData,
  onShowToast,
}) => {
  const defaultStudentId = useMemo(() => {
    if (currentUser?.role === 'STUDENT' && currentUser.studentRollNumber) {
      const self = students.find((s) => s.rollNumber.toLowerCase() === currentUser.studentRollNumber?.toLowerCase());
      if (self) return self.id;
    }
    return initialStudentId || students[0]?.id || '';
  }, [currentUser, students, initialStudentId]);

  const [selectedStudentId, setSelectedStudentId] = useState<string>(defaultStudentId);
  useEffect(() => {
    if (initialStudentId) setSelectedStudentId(initialStudentId);
    else if (!selectedStudentId && defaultStudentId) setSelectedStudentId(defaultStudentId);
  }, [initialStudentId, defaultStudentId, selectedStudentId]);

  const [filterEligibleOnly, setFilterEligibleOnly] = useState(false);

  const student = students.find((s) => s.id === selectedStudentId) || students[0];

  const matches = useMemo(() => {
    if (!student) return [];
    return matchingEngine.findMatchesForStudent(student, drives, companies, skills);
  }, [student, drives, companies, skills]);

  const displayedMatches = useMemo(() => {
    if (filterEligibleOnly) {
      return matches.filter((m) => m.eligibility.isEligible);
    }
    return matches;
  }, [matches, filterEligibleOnly]);

  const handleApply = async (match: CompanyMatch) => {
    try {
      await api.createApplication(match.drive.id, student.id);
      await onRefreshData();
      onShowToast(
        'success',
        'Applied Successfully',
        `Saved application for ${match.drive.companyName} (${match.drive.jobRole}) in MySQL.`
      );
    } catch (err: any) {
      onShowToast('error', 'Cannot Apply', err.message);
    }
  };

  if (!student) {
    return (
      <div className="p-8 text-center bg-white rounded-xl border border-slate-200 text-xs text-slate-500">
        No students available to run matching calculations.
      </div>
    );
  }

  return (
    <div className="space-y-6">
      
      {/* Header */}
      <div>
        <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Intelligent Company Matching Engine</h1>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Multi-dimensional matching algorithm ranking placement opportunities by academic thresholds, branch fit, and technical proficiency overlap.
        </p>
      </div>

      {/* Selector & Filter Bar */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
        <div className="flex items-center space-x-3">
          <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 shrink-0 flex items-center space-x-1.5">
            <GraduationCap className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
            <span>Select Student Profile:</span>
          </label>
          <select
            value={selectedStudentId}
            disabled={currentUser?.role === 'STUDENT'}
            onChange={(e) => setSelectedStudentId(e.target.value)}
            className="px-3 py-1.5 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono text-slate-800 dark:text-slate-100 disabled:bg-slate-100 dark:disabled:bg-slate-900"
          >
            {students.map((s) => (
              <option key={s.id} value={s.id}>
                {s.rollNumber} - {s.fullName} ({s.branch}, CGPA: {s.cgpa.toFixed(2)})
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center space-x-2">
          <label className="flex items-center space-x-2 text-xs font-medium text-slate-700 dark:text-slate-300 cursor-pointer">
            <input
              type="checkbox"
              checked={filterEligibleOnly}
              onChange={(e) => setFilterEligibleOnly(e.target.checked)}
              className="rounded border-slate-300 dark:border-slate-700 text-cyan-600 focus:ring-cyan-500"
            />
            <span>Show 100% Eligible Opportunities Only</span>
          </label>
        </div>
      </div>

      {/* Matches List */}
      <div className="space-y-4">
        {displayedMatches.length === 0 ? (
          <div className="p-12 text-center bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 text-xs text-slate-400">
            No matching companies found for the selected filters.
          </div>
        ) : (
          displayedMatches.map((match) => {
            const isEligible = match.eligibility.isEligible;
            const existingApp = applications.find(
              (a) => a.studentId === student.id && a.driveId === match.drive.id && a.currentStage !== 'WITHDRAWN'
            );

            return (
              <div
                key={match.drive.id}
                className={`rounded-xl border p-5 shadow-xs transition text-xs space-y-3.5 ${
                  isEligible
                    ? 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 hover:border-cyan-400 dark:hover:border-cyan-500'
                    : 'bg-slate-50/40 dark:bg-slate-950/40 border-slate-200/80 dark:border-slate-800/80'
                }`}
              >
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                  <div>
                    <div className="flex items-center space-x-2.5">
                      <h3 className="font-bold text-sm text-slate-900 dark:text-slate-100">{match.company.name}</h3>
                      <span className="text-[10px] px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700">
                        {match.company.tier}
                      </span>
                      {isEligible ? (
                        <span className="text-[10px] font-semibold text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 px-2 py-0.5 rounded flex items-center space-x-1">
                          <CheckCircle2 className="w-3 h-3" />
                          <span>Eligible to Apply</span>
                        </span>
                      ) : (
                        <span className="text-[10px] font-semibold text-rose-700 dark:text-rose-400 bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800 px-2 py-0.5 rounded flex items-center space-x-1">
                          <AlertTriangle className="w-3 h-3" />
                          <span>Ineligible</span>
                        </span>
                      )}
                    </div>

                    <div className="text-xs font-semibold text-cyan-800 dark:text-cyan-400 mt-1">
                      {match.drive.jobRole} · <span className="text-slate-900 dark:text-slate-100 font-bold">{match.drive.salaryPackageLpa} LPA</span>
                    </div>

                    <p className="text-[11px] text-slate-600 dark:text-slate-300 mt-1.5 leading-relaxed max-w-3xl">
                      {match.explanation}
                    </p>
                  </div>

                  {/* Match Score Badge */}
                  <div className="flex flex-col items-end shrink-0">
                    <div className="text-right">
                      <span className="text-2xl font-bold text-slate-900 dark:text-slate-100">{match.matchScore}%</span>
                      <span className="text-[10px] text-slate-500 dark:text-slate-400 block">Overall Fit Score</span>
                    </div>
                  </div>
                </div>

                {/* Skills Overlap */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-3 border-t border-slate-100 dark:border-slate-800 text-[11px]">
                  <div>
                    <span className="font-semibold text-emerald-700 dark:text-emerald-400 block mb-1">
                      ✓ Matching Skills ({match.skillsMatched.length}):
                    </span>
                    {match.skillsMatched.length > 0 ? (
                      <div className="flex flex-wrap gap-1">
                        {match.skillsMatched.map((sk, idx) => (
                          <span key={idx} className="bg-emerald-50 dark:bg-emerald-950/50 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800 px-2 py-0.5 rounded text-[10px]">
                            {sk}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span className="text-slate-400">None yet</span>
                    )}
                  </div>

                  <div>
                    <span className="font-semibold text-rose-700 dark:text-rose-400 block mb-1">
                      ✗ Skills to Bridge ({match.skillsMissing.length}):
                    </span>
                    {match.skillsMissing.length > 0 ? (
                      <div className="flex flex-wrap gap-1">
                        {match.skillsMissing.map((sk, idx) => (
                          <span key={idx} className="bg-rose-50 dark:bg-rose-950/50 text-rose-800 dark:text-rose-300 border border-rose-200 dark:border-rose-800 px-2 py-0.5 rounded text-[10px]">
                            {sk}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span className="text-emerald-700 dark:text-emerald-400">All required skills satisfied!</span>
                    )}
                  </div>
                </div>

                {/* Card Actions */}
                <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between">
                  <div className="text-[11px] text-slate-500 dark:text-slate-400">
                    Cutoff: Min CGPA {match.drive.minCgpa} · Deadline: {match.drive.applicationDeadline}
                  </div>

                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => onCheckEligibility(match.drive.id)}
                      className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 font-medium transition cursor-pointer"
                    >
                      View Breakdown
                    </button>

                    {existingApp ? (
                      <span className="px-3 py-1.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold text-[11px]">
                        Applied ({existingApp.currentStage})
                      </span>
                    ) : isEligible ? (
                      <button
                        onClick={() => handleApply(match)}
                        className="px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-semibold transition cursor-pointer flex items-center space-x-1"
                      >
                        <Send className="w-3.5 h-3.5" />
                        <span>Apply Now</span>
                      </button>
                    ) : (
                      <button
                        disabled
                        className="px-3 py-1.5 rounded-lg bg-slate-200 dark:bg-slate-800 text-slate-400 dark:text-slate-500 font-medium cursor-not-allowed"
                      >
                        Ineligible
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

    </div>
  );
};
