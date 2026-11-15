import React, { useState, useMemo, useEffect } from 'react';
import {
  SplitSquareVertical,
  GraduationCap,
  Building2,
  CheckCircle2,
  AlertCircle,
  HelpCircle,
  ArrowRight,
  BookOpen,
  TrendingUp,
  Award,
} from 'lucide-react';
import { Student, PlacementDrive, User, Skill, PROFICIENCY_LABELS, SkillProficiency } from '../types';
import { skillGapService } from '../services/skillGapService';

interface SkillGapAnalysisPageProps {
  currentUser: User | null;
  students: Student[];
  drives: PlacementDrive[];
  skills: Skill[];
  initialStudentId?: string;
  initialDriveId?: string;
  onNavigateToSkillMatrix: () => void;
}

export const SkillGapAnalysisPage: React.FC<SkillGapAnalysisPageProps> = ({
  currentUser,
  students,
  drives,
  skills,
  initialStudentId,
  initialDriveId,
  onNavigateToSkillMatrix,
}) => {
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

  const analysis = useMemo(() => {
    if (!student || !drive) return null;
    return skillGapService.analyzeSkillGap(student, drive, skills);
  }, [student, drive, skills]);

  if (!student || !drive || !analysis) {
    return (
      <div className="p-8 text-center bg-white rounded-xl border border-slate-200">
        <p className="text-xs text-slate-500">Please select student and drive records to conduct skill gap evaluation.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      
      {/* Header */}
      <div>
        <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Skill Gap Analysis &amp; Learning Roadmap</h1>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Quantifies technical deficiencies between student skill profiles and target recruitment requisites to formulate targeted upskilling roadmaps.
        </p>
      </div>

      {/* Selectors Bar */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center space-x-1.5">
            <GraduationCap className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
            <span>Target Student:</span>
          </label>
          <select
            value={selectedStudentId}
            disabled={currentUser?.role === 'STUDENT'}
            onChange={(e) => setSelectedStudentId(e.target.value)}
            className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono text-slate-800 dark:text-slate-100 disabled:bg-slate-100 dark:disabled:bg-slate-900"
          >
            {students.map((s) => (
              <option key={s.id} value={s.id}>
                {s.rollNumber} - {s.fullName} ({s.branch})
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center space-x-1.5">
            <Building2 className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
            <span>Target Job Opportunity:</span>
          </label>
          <select
            value={selectedDriveId}
            onChange={(e) => setSelectedDriveId(e.target.value)}
            className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-800 dark:text-slate-100 font-medium"
          >
            {drives.map((d) => (
              <option key={d.id} value={d.id}>
                {d.companyName} - {d.jobRole} ({d.salaryPackageLpa} LPA)
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Summary Readiness Score Card */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div>
          <span className="text-xs font-semibold text-cyan-700 dark:text-cyan-400 uppercase tracking-wider">
            Technical Preparedness Index
          </span>
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100 mt-1">
            {analysis.readinessPercentage}% Technical Skill Match
          </h2>
          <p className="text-xs text-slate-600 dark:text-slate-300 mt-1 leading-relaxed max-w-xl">
            Candidate satisfies <strong>{analysis.skillsSatisfiedCount}</strong> of <strong>{analysis.totalRequiredSkills}</strong> required core skills. 
            {analysis.skillsMissingCount > 0 ? ` ${analysis.skillsMissingCount} skill(s) currently unattempted.` : ''}
          </p>
        </div>

        <div className="flex items-center space-x-3 shrink-0">
          <div className="text-center p-3 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800/80 min-w-[90px]">
            <div className="text-lg font-bold text-emerald-800 dark:text-emerald-400">{analysis.skillsSatisfiedCount}</div>
            <div className="text-[10px] text-emerald-700 dark:text-emerald-300 font-semibold">Fully Met</div>
          </div>

          <div className="text-center p-3 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800/80 min-w-[90px]">
            <div className="text-lg font-bold text-amber-800 dark:text-amber-400">{analysis.skillsPartialCount}</div>
            <div className="text-[10px] text-amber-700 dark:text-amber-300 font-semibold">Partial Gap</div>
          </div>

          <div className="text-center p-3 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800/80 min-w-[90px]">
            <div className="text-lg font-bold text-rose-800 dark:text-rose-400">{analysis.skillsMissingCount}</div>
            <div className="text-[10px] text-rose-700 dark:text-rose-300 font-semibold">Missing</div>
          </div>
        </div>
      </div>

      {/* Detailed Skill Comparison Cards */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs">
        <h3 className="text-xs font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wider pb-3 border-b border-slate-100 dark:border-slate-800">
          Proficiency Gap Evaluation &amp; Curriculum Prescriptions
        </h3>

        <div className="divide-y divide-slate-100 dark:divide-slate-800 mt-2">
          {analysis.skills.map((item) => {
            const isMet = item.status === 'MET';
            const isPartial = item.status === 'PARTIAL';

            return (
              <div key={item.skillId} className="py-4 space-y-2 text-xs">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div>
                    <div className="flex items-center space-x-2">
                      <span className="font-bold text-slate-900 dark:text-slate-100">{item.skillName}</span>
                      <span className={`text-[10px] font-semibold px-2 py-0.5 rounded ${
                        isMet
                          ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800'
                          : isPartial
                          ? 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800'
                          : 'bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border border-rose-200 dark:border-rose-800'
                      }`}>
                        {isMet ? 'Requirement Met' : isPartial ? `Partial Gap (-${item.gapLevel} Level)` : 'Completely Missing'}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">{item.category}</div>
                  </div>

                  <div className="flex items-center space-x-4 shrink-0 text-[11px]">
                    <div>
                      <span className="text-slate-400 dark:text-slate-500 block">Candidate Level</span>
                      <strong className="text-slate-800 dark:text-slate-200">
                        {item.studentProficiency > 0
                          ? `${item.studentProficiency}: ${PROFICIENCY_LABELS[item.studentProficiency as SkillProficiency]}`
                          : 'Not Listed (Level 0)'}
                      </strong>
                    </div>

                    <ArrowRight className="w-3.5 h-3.5 text-slate-300 dark:text-slate-600" />

                    <div>
                      <span className="text-slate-400 dark:text-slate-500 block">Required Level</span>
                      <strong className="text-cyan-800 dark:text-cyan-400">
                        {item.requiredProficiency}: {PROFICIENCY_LABELS[item.requiredProficiency]}
                      </strong>
                    </div>
                  </div>
                </div>

                {/* Specific Learning Recommendation */}
                <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-950/70 border border-slate-200/80 dark:border-slate-800 text-[11px] text-slate-700 dark:text-slate-300 flex items-start space-x-2.5">
                  <BookOpen className="w-4 h-4 text-cyan-600 dark:text-cyan-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-slate-900 dark:text-slate-100">Curriculum Action Plan: </span>
                    <span className="text-slate-700 dark:text-slate-300 leading-relaxed">{item.recommendation}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Strategic Roadmap Card */}
      <div className="bg-slate-900 rounded-xl p-5 text-white border border-slate-800 text-xs space-y-3">
        <div className="flex items-center space-x-2 text-cyan-400 font-bold uppercase tracking-wider text-[11px]">
          <TrendingUp className="w-4 h-4" />
          <span>Strategic Improvement Roadmap for {student.fullName}</span>
        </div>

        <div className="space-y-2">
          {analysis.actionPlan.map((step, idx) => (
            <div key={idx} className="flex items-start space-x-2.5 text-slate-200">
              <span className="w-5 h-5 rounded-full bg-cyan-950 border border-cyan-700/60 text-cyan-300 flex items-center justify-center font-mono font-bold text-[10px] shrink-0 mt-0.5">
                {idx + 1}
              </span>
              <p className="text-xs leading-relaxed">{step}</p>
            </div>
          ))}
        </div>
      </div>

    </div>
  );
};
