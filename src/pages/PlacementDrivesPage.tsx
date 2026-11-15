import React, { useState, useMemo } from 'react';
import {
  Briefcase,
  Search,
  Plus,
  Filter,
  Calendar,
  CheckCircle2,
  Clock,
  Layers,
  Users,
  Edit2,
  Trash2,
  X,
  Sparkles,
} from 'lucide-react';
import {
  PlacementDrive,
  Company,
  Branch,
  Skill,
  Application,
  User,
  DriveStatus,
  SkillProficiency,
  PROFICIENCY_LABELS,
  DriveRequiredSkill,
} from '../types';
import { api } from '../services/api';
import { ConfirmationModal } from '../components/ConfirmationModal';

interface PlacementDrivesPageProps {
  currentUser: User | null;
  drives: PlacementDrive[];
  companies: Company[];
  skills: Skill[];
  applications?: Application[];
  onCheckEligibility: (driveId: string) => void;
  onViewApplications: (driveId: string) => void;
  onRefreshData: () => Promise<void>;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

const ALL_BRANCHES: Branch[] = [
  'CSE',
  'CSE - AIML',
  'CSE - DS',
  'CSE - Cyber Security',
  'IT',
  'ECE',
  'EEE',
  'MECH',
  'CIVIL',
];

export const PlacementDrivesPage: React.FC<PlacementDrivesPageProps> = ({
  currentUser,
  drives,
  companies,
  skills,
  applications = [],
  onCheckEligibility,
  onViewApplications,
  onRefreshData,
  onShowToast,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  // Modal state
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingDrive, setEditingDrive] = useState<PlacementDrive | null>(null);
  const [deleteCandidate, setDeleteCandidate] = useState<PlacementDrive | null>(null);

  // Form fields
  const [formData, setFormData] = useState({
    companyId: companies[0]?.id || '',
    jobRole: '',
    jobDescription: '',
    salaryPackageLpa: 8.0,
    eligibleBranches: ['CSE', 'IT'] as Branch[],
    minCgpa: 7.0,
    maxBacklogsAllowed: 0,
    targetGraduationYear: 2026,
    driveDate: '2026-11-25',
    applicationDeadline: '2026-11-20',
    availablePositions: 10,
    status: 'OPEN' as DriveStatus,
    selectionProcess: 'Online Test -> Technical Interview -> HR Round',
    requiredSkills: [] as DriveRequiredSkill[],
  });

  const canManage = currentUser?.role === 'ADMIN' || currentUser?.role === 'PLACEMENT_OFFICER';

  const filteredDrives = useMemo(() => {
    return drives.filter((d) => {
      const q = searchQuery.toLowerCase().trim();
      const matchSearch = !q || d.companyName.toLowerCase().includes(q) || d.jobRole.toLowerCase().includes(q);
      const matchStatus = statusFilter === 'ALL' || d.status === statusFilter;
      return matchSearch && matchStatus;
    });
  }, [drives, searchQuery, statusFilter]);

  const handleOpenAdd = () => {
    setEditingDrive(null);
    setFormData({
      companyId: companies[0]?.id || '',
      jobRole: '',
      jobDescription: '',
      salaryPackageLpa: 8.5,
      eligibleBranches: ['CSE', 'CSE - AIML', 'CSE - DS', 'IT'],
      minCgpa: 7.0,
      maxBacklogsAllowed: 0,
      targetGraduationYear: 2026,
      driveDate: new Date(Date.now() + 14 * 86400000).toISOString().split('T')[0],
      applicationDeadline: new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0],
      availablePositions: 15,
      status: 'OPEN',
      selectionProcess: 'Online Diagnostic -> System Coding -> Technical Panel -> HR',
      requiredSkills: skills.slice(0, 2).map((sk) => ({
        skillId: sk.id,
        minProficiency: 3 as SkillProficiency,
        isMandatory: true,
      })),
    });
    setIsFormOpen(true);
  };

  const handleOpenEdit = (drive: PlacementDrive) => {
    setEditingDrive(drive);
    setFormData({
      companyId: drive.companyId,
      jobRole: drive.jobRole,
      jobDescription: drive.jobDescription,
      salaryPackageLpa: drive.salaryPackageLpa,
      eligibleBranches: drive.eligibleBranches,
      minCgpa: drive.minCgpa,
      maxBacklogsAllowed: drive.maxBacklogsAllowed,
      targetGraduationYear: drive.targetGraduationYear,
      driveDate: drive.driveDate,
      applicationDeadline: drive.applicationDeadline,
      availablePositions: drive.availablePositions,
      status: drive.status,
      selectionProcess: drive.selectionProcess,
      requiredSkills: drive.requiredSkills,
    });
    setIsFormOpen(true);
  };

  const handleToggleBranch = (branch: Branch) => {
    if (formData.eligibleBranches.includes(branch)) {
      setFormData({
        ...formData,
        eligibleBranches: formData.eligibleBranches.filter((b) => b !== branch),
      });
    } else {
      setFormData({
        ...formData,
        eligibleBranches: [...formData.eligibleBranches, branch],
      });
    }
  };

  const handleAddRequiredSkill = (skillId: string) => {
    if (formData.requiredSkills.some((r) => r.skillId === skillId)) return;
    setFormData({
      ...formData,
      requiredSkills: [
        ...formData.requiredSkills,
        { skillId, minProficiency: 3, isMandatory: true },
      ],
    });
  };

  const handleRemoveRequiredSkill = (skillId: string) => {
    setFormData({
      ...formData,
      requiredSkills: formData.requiredSkills.filter((r) => r.skillId !== skillId),
    });
  };

  const handleUpdateSkillProficiency = (skillId: string, minProficiency: SkillProficiency) => {
    setFormData({
      ...formData,
      requiredSkills: formData.requiredSkills.map((r) =>
        r.skillId === skillId ? { ...r, minProficiency } : r
      ),
    });
  };

  const handleSaveDrive = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.jobRole.trim()) return;
    if (formData.eligibleBranches.length === 0) {
      onShowToast('error', 'Branch Required', 'Select at least one eligible engineering branch.');
      return;
    }

    const company = companies.find((c) => c.id === formData.companyId);
    const companyName = company ? company.name : 'Partner Organization';

    try {
      if (editingDrive) {
        await api.updateDrive(editingDrive.id, {
          ...formData,
          companyName,
        });
        await onRefreshData();
        onShowToast('success', 'Drive Updated', `Updated MySQL placement drive: ${formData.jobRole}`);
      } else {
        await api.createDrive({
          ...formData,
          companyName,
        });
        await onRefreshData();
        onShowToast('success', 'Drive Created', `Announced MySQL placement drive for ${companyName}`);
      }
      setIsFormOpen(false);
    } catch (err: any) {
      onShowToast('error', 'Error Saving Drive', err.message);
    }
  };

  const confirmDelete = async () => {
    if (!deleteCandidate) return;
    try {
      await api.deleteDrive(deleteCandidate.id);
      await onRefreshData();
      onShowToast('success', 'Drive Deleted', `Removed drive ${deleteCandidate.jobRole} from MySQL`);
      setDeleteCandidate(null);
    } catch (err: any) {
      onShowToast('error', 'Cannot Delete', err.message);
      setDeleteCandidate(null);
    }
  };

  return (
    <div className="space-y-5">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Placement Drives &amp; Campus Recruitment</h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Configure recruitment drives, skill requirements, CGPA criteria, and selection workflows.
          </p>
        </div>

        {canManage && (
          <button
            onClick={handleOpenAdd}
            className="px-3.5 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition cursor-pointer flex items-center space-x-1.5 shadow-xs"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Create Placement Drive</span>
          </button>
        )}
      </div>

      {/* Filter and Search */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs flex flex-col md:flex-row items-stretch md:items-center gap-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search drives by company name or role..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
          />
        </div>

        <div className="flex items-center space-x-2">
          <span className="text-xs text-slate-500 dark:text-slate-400 shrink-0">Status:</span>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
          >
            <option value="ALL">All Statuses</option>
            <option value="OPEN">Open for Applications</option>
            <option value="UPCOMING">Upcoming</option>
            <option value="PLANNED">Planned</option>
            <option value="CLOSED">Closed</option>
            <option value="COMPLETED">Completed</option>
          </select>
        </div>
      </div>

      {/* Drives Grid */}
      <div className="space-y-4">
        {filteredDrives.length === 0 ? (
          <div className="p-12 text-center bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 text-xs text-slate-400">
            No placement drives match your filter criteria.
          </div>
        ) : (
          filteredDrives.map((drive) => {
            const driveApplications = applications.filter(
              (a) => a.driveId === drive.id && a.currentStage !== 'WITHDRAWN'
            );

            return (
              <div
                key={drive.id}
                className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs hover:border-slate-300 dark:hover:border-slate-700 transition text-xs space-y-4"
              >
                <div className="flex flex-col md:flex-row md:items-start justify-between gap-3">
                  <div>
                    <div className="flex items-center space-x-2.5">
                      <h3 className="font-bold text-sm text-slate-900 dark:text-slate-100">{drive.companyName}</h3>
                      <span className={`text-[10px] font-semibold px-2 py-0.5 rounded ${
                        drive.status === 'OPEN'
                          ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800'
                          : drive.status === 'UPCOMING'
                          ? 'bg-blue-50 dark:bg-blue-950/40 text-blue-700 dark:text-blue-400 border border-blue-200 dark:border-blue-800'
                          : drive.status === 'COMPLETED'
                          ? 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700'
                          : 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800'
                      }`}>
                        {drive.status}
                      </span>
                    </div>

                    <div className="text-xs font-semibold text-cyan-800 dark:text-cyan-400 mt-1">
                      {drive.jobRole}
                    </div>

                    <p className="text-[11px] text-slate-600 dark:text-slate-300 mt-1.5 leading-relaxed max-w-3xl">
                      {drive.jobDescription}
                    </p>
                  </div>

                  <div className="flex flex-col items-end shrink-0 text-right">
                    <span className="text-base font-bold text-slate-900 dark:text-slate-100">
                      {drive.salaryPackageLpa} LPA
                    </span>
                    <span className="text-[11px] text-slate-500 dark:text-slate-400">
                      {drive.availablePositions} Vacanc{drive.availablePositions > 1 ? 'ies' : 'y'}
                    </span>
                  </div>
                </div>

                {/* Criteria Grid */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-3 border-t border-slate-100 dark:border-slate-800 text-[11px]">
                  <div>
                    <span className="text-slate-400 dark:text-slate-500 block">Min CGPA</span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">{drive.minCgpa.toFixed(2)}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 dark:text-slate-500 block">Eligible Branches</span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">{drive.eligibleBranches.join(', ')}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 dark:text-slate-500 block">Application Deadline</span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">{drive.applicationDeadline}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 dark:text-slate-500 block">Drive Date</span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">{drive.driveDate}</span>
                  </div>
                </div>

                {/* Required Skills Badges */}
                <div className="pt-2">
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-400 block mb-1.5">
                    Required Technical Proficiencies:
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {drive.requiredSkills.map((req) => {
                      const skillObj = skills.find((s) => s.id === req.skillId);
                      const name = skillObj ? skillObj.name : req.skillId;
                      return (
                        <span
                          key={req.skillId}
                          className="text-[11px] px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700 flex items-center space-x-1"
                        >
                          <span className="font-medium">{name}</span>
                          <span className="text-cyan-700 dark:text-cyan-400 font-semibold">(Level {req.minProficiency})</span>
                        </span>
                      );
                    })}
                  </div>
                </div>

                {/* Footer Actions */}
                <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between">
                  <div className="flex items-center space-x-3 text-[11px] text-slate-500 dark:text-slate-400">
                    <span>
                      Applicants: <strong className="text-slate-800 dark:text-slate-200">{driveApplications.length}</strong>
                    </span>
                    <span>·</span>
                    <span>Class of {drive.targetGraduationYear}</span>
                  </div>

                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => onCheckEligibility(drive.id)}
                      className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-200 font-medium transition cursor-pointer flex items-center space-x-1"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
                      <span>Check My Eligibility</span>
                    </button>

                    <button
                      onClick={() => onViewApplications(drive.id)}
                      className="px-3 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold transition cursor-pointer flex items-center space-x-1 shadow-xs"
                    >
                      <Users className="w-3.5 h-3.5" />
                      <span>Applications ({driveApplications.length})</span>
                    </button>

                    {canManage && (
                      <>
                        <button
                          onClick={() => handleOpenEdit(drive)}
                          className="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 transition cursor-pointer"
                          title="Edit Drive"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setDeleteCandidate(drive)}
                          className="p-1.5 rounded hover:bg-rose-50 dark:hover:bg-rose-950/40 text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 transition cursor-pointer"
                          title="Delete Drive"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Add / Edit Drive Modal */}
      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 overflow-y-auto animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-2xl w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5 my-8">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">
                {editingDrive ? `Edit Placement Drive: ${editingDrive.jobRole}` : 'Create Campus Placement Drive'}
              </h3>
              <button onClick={() => setIsFormOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSaveDrive} className="mt-4 space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Company *</label>
                  <select
                    value={formData.companyId}
                    onChange={(e) => setFormData({ ...formData, companyId: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  >
                    {companies.map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Job Role Title *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Cloud Engineer / SDE"
                    value={formData.jobRole}
                    onChange={(e) => setFormData({ ...formData, jobRole: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Job Description &amp; Responsibilities *</label>
                <textarea
                  rows={2}
                  required
                  placeholder="Key responsibilities, technical stacks involved..."
                  value={formData.jobDescription}
                  onChange={(e) => setFormData({ ...formData, jobDescription: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Package (LPA) *</label>
                  <input
                    type="number"
                    step="0.1"
                    min="1"
                    max="100"
                    required
                    value={formData.salaryPackageLpa}
                    onChange={(e) => setFormData({ ...formData, salaryPackageLpa: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Min CGPA Cutoff *</label>
                  <input
                    type="number"
                    step="0.05"
                    min="0"
                    max="10"
                    required
                    value={formData.minCgpa}
                    onChange={(e) => setFormData({ ...formData, minCgpa: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Vacancies</label>
                  <input
                    type="number"
                    min="1"
                    max="500"
                    value={formData.availablePositions}
                    onChange={(e) => setFormData({ ...formData, availablePositions: parseInt(e.target.value) || 1 })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              </div>

              {/* Eligible Branches Multi-Select */}
              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Eligible Branches (Select Applicable) *
                </label>
                <div className="flex flex-wrap gap-1.5 p-2 bg-slate-50 dark:bg-slate-950/80 rounded-lg border border-slate-200 dark:border-slate-800">
                  {ALL_BRANCHES.map((b) => {
                    const isSelected = formData.eligibleBranches.includes(b);
                    return (
                      <button
                        key={b}
                        type="button"
                        onClick={() => handleToggleBranch(b)}
                        className={`px-2.5 py-1 rounded text-[11px] font-medium transition cursor-pointer ${
                          isSelected
                            ? 'bg-cyan-700 text-white font-semibold'
                            : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 border border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                        }`}
                      >
                        {isSelected ? '✓ ' : ''}{b}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Required Skills & Proficiency Levels */}
              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Required Technical Skills &amp; Minimum Proficiency
                </label>
                <div className="space-y-2 p-3 bg-slate-50 dark:bg-slate-950/80 rounded-lg border border-slate-200 dark:border-slate-800">
                  <div className="flex items-center space-x-2">
                    <select
                      id="skill-select"
                      className="flex-1 px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                      defaultValue=""
                      onChange={(e) => {
                        if (e.target.value) {
                          handleAddRequiredSkill(e.target.value);
                          e.target.value = '';
                        }
                      }}
                    >
                      <option value="">+ Add required skill from catalog...</option>
                      {skills.map((s) => (
                        <option key={s.id} value={s.id}>{s.name} ({s.category})</option>
                      ))}
                    </select>
                  </div>

                  <div className="space-y-1.5 mt-2">
                    {formData.requiredSkills.map((req) => {
                      const sObj = skills.find((s) => s.id === req.skillId);
                      return (
                        <div key={req.skillId} className="flex items-center justify-between p-2 rounded bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800">
                          <span className="font-medium text-slate-800 dark:text-slate-200">{sObj?.name}</span>
                          <div className="flex items-center space-x-2">
                            <span className="text-[10px] text-slate-500 dark:text-slate-400">Min Level:</span>
                            <select
                              value={req.minProficiency}
                              onChange={(e) => handleUpdateSkillProficiency(req.skillId, parseInt(e.target.value) as SkillProficiency)}
                              className="px-2 py-0.5 rounded border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-[11px]"
                            >
                              {[1, 2, 3, 4, 5].map((lvl) => (
                                <option key={lvl} value={lvl}>{lvl}: {PROFICIENCY_LABELS[lvl as SkillProficiency]}</option>
                              ))}
                            </select>
                            <button
                              type="button"
                              onClick={() => handleRemoveRequiredSkill(req.skillId)}
                              className="text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 p-1 cursor-pointer"
                            >
                              <X className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Drive Date</label>
                  <input
                    type="date"
                    required
                    value={formData.driveDate}
                    onChange={(e) => setFormData({ ...formData, driveDate: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Deadline</label>
                  <input
                    type="date"
                    required
                    value={formData.applicationDeadline}
                    onChange={(e) => setFormData({ ...formData, applicationDeadline: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Drive Status</label>
                  <select
                    value={formData.status}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value as DriveStatus })}
                    className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  >
                    <option value="OPEN">OPEN</option>
                    <option value="UPCOMING">UPCOMING</option>
                    <option value="PLANNED">PLANNED</option>
                    <option value="CLOSED">CLOSED</option>
                    <option value="COMPLETED">COMPLETED</option>
                    <option value="CANCELLED">CANCELLED</option>
                  </select>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsFormOpen(false)}
                  className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold cursor-pointer shadow-xs"
                >
                  {editingDrive ? 'Save Drive' : 'Publish Drive'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!deleteCandidate}
        title="Confirm Drive Deletion"
        message={`Delete placement drive for ${deleteCandidate?.companyName} (${deleteCandidate?.jobRole})?`}
        confirmLabel="Delete Drive"
        isDestructive={true}
        onConfirm={confirmDelete}
        onCancel={() => setDeleteCandidate(null)}
      />

    </div>
  );
};
