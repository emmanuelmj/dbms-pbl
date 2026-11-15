import React, { useState, useMemo, useEffect } from 'react';
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Building2,
  GraduationCap,
  Sparkles,
  Layers,
  ArrowRight,
  Send,
} from 'lucide-react';
import { Student, PlacementDrive, User, Skill, Application, PROFICIENCY_LABELS } from '../types';
import { eligibilityService } from '../services/eligibilityService';
import { api } from '../services/api';

interface EligibilityCheckerPageProps {
  currentUser: User | null;
  students: Student[];
  drives: PlacementDrive[];
  skills: Skill[];
  applications: Application[];
  initialStudentId?: string;
  initialDriveId?: string;
  onNavigateToApplications: () => void;
  onRefreshData: () => Promise<void>;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

export const EligibilityCheckerPage: React.FC<EligibilityCheckerPageProps> = ({
  currentUser,
  students,
  drives,
  skills,
  applications,
  initialStudentId,
  initialDriveId,
  onNavigateToApplications,
  onRefreshData,
  onShowToast,
}) => {
  // If student role, lock student selection to self
  const defaultStudentId = useMemo(() => {
    if (currentUser?.role === 'STUDENT' && currentUser.studentRollNumber) {
      const self = students.find((s) => s.rollNumber.toLowerCase() === currentUser.studentRollNumber?.toLowerCase());
      if (self) return self.id;
    }
    return initialStudentId || students[0]?.id || '';
  }, [currentUser, students, initialStudentId]);

  const [selectedStudentId, setSelectedStudentId] = useState<string>(defaultStudentId);
  const [selectedDriveId, setSelectedDriveId] = useState<string>(initialDriveId || drives[0]?.id || '');

  useEffect(() => {
    if (initialStudentId) setSelectedStudentId(initialStudentId);
    else if (!selectedStudentId && defaultStudentId) setSelectedStudentId(defaultStudentId);
  }, [initialStudentId, defaultStudentId, selectedStudentId]);

  useEffect(() => {
    if (initialDriveId) setSelectedDriveId(initialDriveId);
    else if (!selectedDriveId && drives[0]?.id) setSelectedDriveId(drives[0].id);
  }, [initialDriveId, drives, selectedDriveId]);

  const student = students.find((s) => s.id === selectedStudentId) || students[0];
  const drive = drives.find((d) => d.id === selectedDriveId) || drives[0];

  const eligibility = useMemo(() => {
    if (!student || !drive) return null;
    return eligibilityService.checkEligibility(student, drive, skills);
  }, [student, drive, skills]);

  // Check if student already applied
  const existingApp = useMemo(() => {
    if (!student || !drive) return undefined;
    return applications.find((a) => a.studentId === student.id && a.driveId === drive.id);
  }, [student, drive, applications]);

  const handleApply = async () => {
    if (!student || !drive) return;
    try {
      await api.createApplication(drive.id, student.id);
      await onRefreshData();
      onShowToast(
        'success',
        'Application Submitted',
        `Saved application for ${drive.companyName} (${drive.jobRole}) in MySQL.`
      );
    } catch (err: any) {
      onShowToast('error', 'Application Failed', err.message);
    }
  };

  if (!student || !drive) {
    return (
      <div className="p-8 text-center bg-white rounded-xl border border-slate-200">
        <p className="text-xs text-slate-500">Please ensure student records and placement drives are configured.</p>
      </div>
    );
  }

  const { criteriaChecks } = eligibility!;

  return (
    <div className="space-y-6">
      
      {/* Header */}
      <div>
        <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Automated Placement Eligibility Engine</h1>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Deterministic evaluation of student academic records, backlogs, branch relevance, and skill matrix proficiencies against hiring cutoffs.
        </p>
      </div>

      {/* Selectors Bar */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Student Selector */}
        <div>
          <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center space-x-1.5">
            <GraduationCap className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
            <span>Select Student:</span>
          </label>
          <select
            value={selectedStudentId}
            disabled={currentUser?.role === 'STUDENT'}
            onChange={(e) => setSelectedStudentId(e.target.value)}
            className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono text-slate-800 dark:text-slate-100 disabled:bg-slate-100 dark:disabled:bg-slate-900"
          >
            {students.map((s) => (
              <option key={s.id} value={s.id}>
                {s.rollNumber} - {s.fullName} ({s.branch}, CGPA: {s.cgpa.toFixed(2)})
              </option>
            ))}
          </select>
          {currentUser?.role === 'STUDENT' && (
            <span className="text-[10px] text-slate-400 mt-0.5 block">
              Logged in as student ({student.rollNumber}).
            </span>
          )}
        </div>

        {/* Drive Selector */}
        <div>
          <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center space-x-1.5">
            <Building2 className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
            <span>Select Target Placement Drive:</span>
          </label>
          <select
            value={selectedDriveId}
            onChange={(e) => setSelectedDriveId(e.target.value)}
            className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-800 dark:text-slate-100 font-medium"
          >
            {drives.map((d) => (
              <option key={d.id} value={d.id}>
                {d.companyName} - {d.jobRole} ({d.salaryPackageLpa} LPA, Min CGPA: {d.minCgpa})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Primary Result Banner */}
      <div
        className={`rounded-2xl p-6 border shadow-xs transition ${
          eligibility?.isEligible
            ? 'bg-emerald-50/70 dark:bg-emerald-950/40 border-emerald-200 dark:border-emerald-800/80 text-emerald-950 dark:text-emerald-100'
            : 'bg-rose-50/70 dark:bg-rose-950/40 border-rose-200 dark:border-rose-800/80 text-rose-950 dark:text-rose-100'
        }`}
      >
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-start space-x-3.5">
            <div className={`p-2.5 rounded-full shrink-0 ${eligibility?.isEligible ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'}`}>
              {eligibility?.isEligible ? <CheckCircle2 className="w-6 h-6" /> : <XCircle className="w-6 h-6" />}
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className={`text-xs font-bold uppercase tracking-wider px-2 py-0.5 rounded ${
                  eligibility?.isEligible ? 'bg-emerald-200/80 dark:bg-emerald-900/60 text-emerald-900 dark:text-emerald-200' : 'bg-rose-200/80 dark:bg-rose-900/60 text-rose-900 dark:text-rose-200'
                }`}>
                  {eligibility?.isEligible ? 'ELIGIBLE TO APPLY' : 'CURRENTLY NOT ELIGIBLE'}
                </span>
                <span className="text-xs font-semibold">
                  Preparedness Score: {eligibility?.score}%
                </span>
              </div>

              <h2 className="text-lg font-bold mt-1">
                {student.fullName} ({student.rollNumber}) vs {drive.companyName}
              </h2>

              <p className="text-xs mt-1 leading-relaxed opacity-90 max-w-2xl">
                {eligibility?.isEligible
                  ? `Candidate satisfies all academic benchmarks (CGPA >= ${drive.minCgpa}), branch eligibility, zero active backlog bounds, and verified mandatory technical skills.`
                  : `Candidate does not meet ${eligibility?.reasons.length} mandatory requirement(s) for this position.`}
              </p>
            </div>
          </div>

          {/* Action button */}
          <div className="shrink-0 flex items-center space-x-2">
            {existingApp ? (
              <div className="px-4 py-2 rounded-lg bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-800 dark:text-slate-200 text-xs font-semibold shadow-xs">
                Already Applied (Stage: {existingApp.currentStage})
              </div>
            ) : eligibility?.isEligible ? (
              <button
                onClick={handleApply}
                className="px-5 py-2.5 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-bold transition cursor-pointer flex items-center space-x-2 shadow-sm"
              >
                <Send className="w-3.5 h-3.5" />
                <span>Submit Placement Application</span>
              </button>
            ) : (
              <button
                disabled
                className="px-4 py-2.5 rounded-lg bg-slate-300 dark:bg-slate-800 text-slate-500 dark:text-slate-400 text-xs font-medium cursor-not-allowed"
              >
                Application Locked
              </button>
            )}
          </div>
        </div>

        {/* Reasons list if ineligible */}
        {!eligibility?.isEligible && eligibility?.reasons && eligibility.reasons.length > 0 && (
          <div className="mt-4 pt-4 border-t border-rose-200/60 dark:border-rose-900/60">
            <span className="text-xs font-bold text-rose-900 dark:text-rose-200 block mb-1.5">
              Specific Criteria Failures:
            </span>
            <ul className="space-y-1 text-xs text-rose-800 dark:text-rose-300">
              {eligibility.reasons.map((r, i) => (
                <li key={i} className="flex items-center space-x-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-rose-600 dark:bg-rose-400 shrink-0" />
                  <span>{r}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* Criteria Verification Breakdown Table */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs">
        <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800">
          <h3 className="text-xs font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wider">
            Detailed Criteria Evaluation Matrix
          </h3>
        </div>

        <div className="divide-y divide-slate-100 dark:divide-slate-800 text-xs">
          
          {/* CGPA */}
          <div className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="space-y-0.5">
              <div className="font-semibold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
                <span>1. Cumulative Grade Point Average (CGPA)</span>
                {criteriaChecks.cgpa.satisfied ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                ) : (
                  <XCircle className="w-4 h-4 text-rose-600 dark:text-rose-400" />
                )}
              </div>
              <div className="text-[11px] text-slate-500 dark:text-slate-400">
                Minimum Cutoff: <strong>{criteriaChecks.cgpa.requiredVal.toFixed(2)}</strong> · Student CGPA: <strong>{criteriaChecks.cgpa.studentVal.toFixed(2)}</strong>
              </div>
            </div>
            <div className={`text-xs font-semibold ${criteriaChecks.cgpa.satisfied ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
              {criteriaChecks.cgpa.satisfied ? 'Requirement Satisfied' : 'Below Cutoff'}
            </div>
          </div>

          {/* Branch */}
          <div className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="space-y-0.5">
              <div className="font-semibold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
                <span>2. Degree &amp; Engineering Branch</span>
                {criteriaChecks.branch.satisfied ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                ) : (
                  <XCircle className="w-4 h-4 text-rose-600 dark:text-rose-400" />
                )}
              </div>
              <div className="text-[11px] text-slate-500 dark:text-slate-400">
                Permitted: {criteriaChecks.branch.allowedBranches.join(', ')} · Candidate: <strong>{criteriaChecks.branch.studentVal}</strong>
              </div>
            </div>
            <div className={`text-xs font-semibold ${criteriaChecks.branch.satisfied ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
              {criteriaChecks.branch.satisfied ? 'Eligible Branch' : 'Non-Permitted Branch'}
            </div>
          </div>

          {/* Graduation Year */}
          <div className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="space-y-0.5">
              <div className="font-semibold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
                <span>3. Target Passing Batch (Graduation Year)</span>
                {criteriaChecks.graduationYear.satisfied ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                ) : (
                  <XCircle className="w-4 h-4 text-rose-600 dark:text-rose-400" />
                )}
              </div>
              <div className="text-[11px] text-slate-500 dark:text-slate-400">
                Required Batch: <strong>{criteriaChecks.graduationYear.requiredVal}</strong> · Candidate: <strong>{criteriaChecks.graduationYear.studentVal}</strong>
              </div>
            </div>
            <div className={`text-xs font-semibold ${criteriaChecks.graduationYear.satisfied ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
              {criteriaChecks.graduationYear.satisfied ? 'Batch Matched' : 'Batch Discrepancy'}
            </div>
          </div>

          {/* Backlogs */}
          <div className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="space-y-0.5">
              <div className="font-semibold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
                <span>4. Standing Academic Backlogs</span>
                {criteriaChecks.backlogs.satisfied ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                ) : (
                  <XCircle className="w-4 h-4 text-rose-600 dark:text-rose-400" />
                )}
              </div>
              <div className="text-[11px] text-slate-500 dark:text-slate-400">
                Max Allowed: <strong>{criteriaChecks.backlogs.maxAllowed}</strong> · Active Backlogs: <strong>{criteriaChecks.backlogs.studentVal}</strong>
              </div>
            </div>
            <div className={`text-xs font-semibold ${criteriaChecks.backlogs.satisfied ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
              {criteriaChecks.backlogs.satisfied ? 'Within Limit' : 'Exceeds Backlog Cap'}
            </div>
          </div>

          {/* Technical Skills & Proficiency Breakdown */}
          <div className="p-4 space-y-3">
            <div className="flex items-center justify-between">
              <div className="font-semibold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
                <span>5. Required Technical Skills &amp; Proficiency Levels</span>
                {criteriaChecks.skills.satisfied ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                ) : (
                  <AlertTriangle className="w-4 h-4 text-amber-600 dark:text-amber-400" />
                )}
              </div>
              <span className="text-[11px] text-slate-500 dark:text-slate-400">
                {criteriaChecks.skills.satisfiedCount} / {criteriaChecks.skills.totalRequired} skills satisfied
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-2">
              {criteriaChecks.skills.details.map((detail) => (
                <div
                  key={detail.skillId}
                  className={`p-2.5 rounded-lg border text-[11px] flex items-center justify-between ${
                    detail.satisfied
                      ? 'bg-emerald-50/60 dark:bg-emerald-950/50 border-emerald-200 dark:border-emerald-800/60 text-emerald-950 dark:text-emerald-100'
                      : 'bg-rose-50/60 dark:bg-rose-950/50 border-rose-200 dark:border-rose-800/60 text-rose-950 dark:text-rose-100'
                  }`}
                >
                  <div>
                    <span className="font-semibold">{detail.skillName}</span>
                    <div className="text-[10px] opacity-80">
                      Required: Level {detail.requiredProficiency} ({PROFICIENCY_LABELS[detail.requiredProficiency]}) · Student: {detail.studentProficiency ? `Level ${detail.studentProficiency}` : 'None'}
                    </div>
                  </div>

                  <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                    detail.satisfied ? 'bg-emerald-200 dark:bg-emerald-900/80 text-emerald-900 dark:text-emerald-200' : 'bg-rose-200 dark:bg-rose-900/80 text-rose-900 dark:text-rose-200'
                  }`}>
                    {detail.satisfied ? 'Satisfied' : `Gap -${detail.gap}`}
                  </span>
                </div>
              ))}
            </div>
          </div>

        </div>
      </div>

    </div>
  );
};
