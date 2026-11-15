import React, { useState, useMemo } from 'react';
import {
  FileCheck2,
  Search,
  Filter,
  Download,
  Clock,
  CheckCircle2,
  XCircle,
  AlertCircle,
  ArrowRight,
  User,
  Building2,
  Calendar,
  X,
  ChevronRight,
} from 'lucide-react';
import {
  Application,
  PlacementDrive,
  Student,
  User as UserType,
  ApplicationStage,
  APPLICATION_STAGE_LABELS,
  STAGE_ORDER,
} from '../types';
import { api } from '../services/api';
import { ConfirmationModal } from '../components/ConfirmationModal';

interface ApplicationsPageProps {
  currentUser: UserType | null;
  applications: Application[];
  drives: PlacementDrive[];
  students: Student[];
  initialDriveId?: string;
  onRefreshData: () => Promise<void>;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

export const ApplicationsPage: React.FC<ApplicationsPageProps> = ({
  currentUser,
  applications,
  drives,
  initialDriveId,
  onRefreshData,
  onShowToast,
}) => {
  const [selectedDriveId, setSelectedDriveId] = useState<string>(initialDriveId || 'ALL');
  const [selectedStage, setSelectedStage] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Timeline drawer state
  const [timelineApp, setTimelineApp] = useState<Application | null>(null);

  // Stage update modal
  const [updatingApp, setUpdatingApp] = useState<Application | null>(null);
  const [targetStage, setTargetStage] = useState<ApplicationStage>('SHORTLISTED');
  const [stageNotes, setStageNotes] = useState('');
  const [offerLpa, setOfferLpa] = useState<number>(7.0);

  // Rejection modal
  const [rejectingApp, setRejectingApp] = useState<Application | null>(null);
  const [rejectionReason, setRejectionReason] = useState('');

  // Withdrawal confirmation
  const [withdrawingApp, setWithdrawingApp] = useState<Application | null>(null);

  const canManageStages = currentUser?.role === 'ADMIN' || currentUser?.role === 'PLACEMENT_OFFICER';

  // Filtered applications
  const filteredApps = useMemo(() => {
    return applications.filter((app) => {
      // If student role, only show student's own applications
      if (currentUser?.role === 'STUDENT' && currentUser.studentRollNumber) {
        if (app.studentRollNumber.toLowerCase() !== currentUser.studentRollNumber.toLowerCase()) {
          return false;
        }
      }

      const matchDrive = selectedDriveId === 'ALL' || app.driveId === selectedDriveId;
      const matchStage = selectedStage === 'ALL' || app.currentStage === selectedStage;
      const q = searchQuery.toLowerCase().trim();
      const matchSearch =
        !q ||
        app.studentName.toLowerCase().includes(q) ||
        app.studentRollNumber.toLowerCase().includes(q) ||
        app.companyName.toLowerCase().includes(q) ||
        app.jobRole.toLowerCase().includes(q);

      return matchDrive && matchStage && matchSearch;
    });
  }, [applications, selectedDriveId, selectedStage, searchQuery, currentUser]);

  const handleOpenUpdateStage = (app: Application) => {
    setUpdatingApp(app);
    // Suggest next stage in the logical sequence
    const currentIndex = STAGE_ORDER.indexOf(app.currentStage);
    const nextStage = currentIndex >= 0 && currentIndex < STAGE_ORDER.length - 1
      ? STAGE_ORDER[currentIndex + 1]
      : 'SELECTED';
    setTargetStage(nextStage);
    setStageNotes('');
    setOfferLpa(app.finalOfferLpa || 7.5);
  };

  const handleSaveStageUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!updatingApp) return;

    try {
      await api.advanceApplicationStage(updatingApp.id, {
        nextStage: targetStage,
        notes: stageNotes || `Advanced to ${APPLICATION_STAGE_LABELS[targetStage]}`,
        finalOfferLpa: ['OFFERED', 'JOINED', 'SELECTED'].includes(targetStage) ? offerLpa : undefined,
      });
      await onRefreshData();
      onShowToast(
        'success',
        'Recruitment Stage Updated',
        `${updatingApp.studentName} advanced to ${APPLICATION_STAGE_LABELS[targetStage]} in MySQL`
      );
      setUpdatingApp(null);
    } catch (err: any) {
      onShowToast('error', 'Update Failed', err.message);
    }
  };

  const handleConfirmReject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rejectingApp) return;
    if (!rejectionReason.trim()) {
      onShowToast('error', 'Reason Mandatory', 'Please record a specific technical or interview feedback reason.');
      return;
    }

    try {
      await api.advanceApplicationStage(rejectingApp.id, {
        nextStage: 'REJECTED',
        notes: `Rejection reason: ${rejectionReason}`,
        rejectionReason,
      });
      await onRefreshData();
      onShowToast('info', 'Application Status Updated', 'Candidate marked as Rejected in MySQL.');
      setRejectingApp(null);
      setRejectionReason('');
    } catch (err: any) {
      onShowToast('error', 'Update Failed', err.message);
    }
  };

  const handleConfirmWithdraw = async () => {
    if (!withdrawingApp) return;
    try {
      await api.withdrawApplication(withdrawingApp.id);
      await onRefreshData();
      onShowToast('info', 'Application Withdrawn', 'Your application has been withdrawn in MySQL.');
      setWithdrawingApp(null);
    } catch (err: any) {
      onShowToast('error', 'Withdrawal Failed', err.message);
      setWithdrawingApp(null);
    }
  };

  // CSV Export
  const exportCsv = () => {
    const headers = [
      'Application ID',
      'Student Roll No',
      'Student Name',
      'Branch',
      'CGPA',
      'Company Name',
      'Job Role',
      'Current Stage',
      'Applied Date',
      'Offer Package LPA',
    ];

    const rows = filteredApps.map((a) => [
      a.id,
      a.studentRollNumber,
      `"${a.studentName}"`,
      a.studentBranch,
      a.studentCgpa.toFixed(2),
      `"${a.companyName}"`,
      `"${a.jobRole}"`,
      APPLICATION_STAGE_LABELS[a.currentStage],
      a.appliedAt.split('T')[0],
      a.finalOfferLpa || '',
    ]);

    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute(
      'download',
      `GCET_Placement_Applications_${new Date().toISOString().split('T')[0]}.csv`
    );
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    onShowToast('info', 'Export Complete', `Exported ${filteredApps.length} applications.`);
  };

  return (
    <div className="space-y-5">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Placement Applications &amp; Recruitment Pipeline</h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            10-stage recruitment journey tracking from submission and shortlisting to technical interviews and offer letters.
          </p>
        </div>

        <button
          onClick={exportCsv}
          className="px-3.5 py-2 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-200 text-xs font-medium transition cursor-pointer flex items-center space-x-1.5 self-start sm:self-auto shadow-xs"
        >
          <Download className="w-3.5 h-3.5 text-slate-500 dark:text-slate-400" />
          <span>Export Pipeline CSV</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs flex flex-col md:flex-row items-stretch md:items-center gap-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search candidate name, roll number, or company..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
          />
        </div>

        <div className="flex items-center space-x-2">
          <span className="text-xs text-slate-500 dark:text-slate-400 shrink-0">Drive:</span>
          <select
            value={selectedDriveId}
            onChange={(e) => setSelectedDriveId(e.target.value)}
            className="px-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200 max-w-[220px]"
          >
            <option value="ALL">All Drives ({drives.length})</option>
            {drives.map((d) => (
              <option key={d.id} value={d.id}>
                {d.companyName} ({d.jobRole})
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center space-x-2">
          <span className="text-xs text-slate-500 dark:text-slate-400 shrink-0">Stage:</span>
          <select
            value={selectedStage}
            onChange={(e) => setSelectedStage(e.target.value)}
            className="px-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
          >
            <option value="ALL">All 10 Stages</option>
            {STAGE_ORDER.map((stage) => (
              <option key={stage} value={stage}>
                {APPLICATION_STAGE_LABELS[stage]}
              </option>
            ))}
            <option value="REJECTED">Rejected</option>
            <option value="WITHDRAWN">Withdrawn</option>
          </select>
        </div>
      </div>

      {/* Applications Data Table */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-50 dark:bg-slate-950/80 text-slate-600 dark:text-slate-300 font-semibold border-b border-slate-200 dark:border-slate-800 select-none">
              <tr>
                <th className="py-3 px-4">Candidate</th>
                <th className="py-3 px-4">Branch &amp; CGPA</th>
                <th className="py-3 px-4">Company &amp; Role</th>
                <th className="py-3 px-4">Current Stage</th>
                <th className="py-3 px-4">Applied On</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {filteredApps.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-xs text-slate-400">
                    No placement applications recorded for the selected criteria.
                  </td>
                </tr>
              ) : (
                filteredApps.map((app) => {
                  const isPlaced = app.currentStage === 'JOINED' || app.currentStage === 'OFFERED';
                  const isRejected = app.currentStage === 'REJECTED';
                  const isWithdrawn = app.currentStage === 'WITHDRAWN';

                  return (
                    <tr key={app.id} className="hover:bg-slate-50/80 dark:hover:bg-slate-800/50 transition">
                      <td className="py-3 px-4">
                        <div className="font-semibold text-slate-900 dark:text-slate-100">{app.studentName}</div>
                        <div className="text-[11px] font-mono text-cyan-700 dark:text-cyan-400">{app.studentRollNumber}</div>
                      </td>

                      <td className="py-3 px-4 text-slate-700 dark:text-slate-300">
                        <div className="font-medium">{app.studentBranch}</div>
                        <div className="text-[11px] text-slate-500 dark:text-slate-400">CGPA: {app.studentCgpa.toFixed(2)}</div>
                      </td>

                      <td className="py-3 px-4">
                        <div className="font-semibold text-slate-900 dark:text-slate-100">{app.companyName}</div>
                        <div className="text-[11px] text-slate-500 dark:text-slate-400">{app.jobRole}</div>
                      </td>

                      <td className="py-3 px-4">
                        <span className={`text-[10px] font-semibold px-2 py-0.5 rounded ${
                          isPlaced
                            ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
                            : isRejected
                            ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-800 dark:text-rose-300 border border-rose-200 dark:border-rose-800'
                            : isWithdrawn
                            ? 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400'
                            : 'bg-cyan-50 dark:bg-cyan-950/40 text-cyan-800 dark:text-cyan-300 border border-cyan-200 dark:border-cyan-800'
                        }`}>
                          {APPLICATION_STAGE_LABELS[app.currentStage]}
                          {app.finalOfferLpa ? ` (${app.finalOfferLpa} LPA)` : ''}
                        </span>
                      </td>

                      <td className="py-3 px-4 text-slate-500 dark:text-slate-400 text-[11px]">
                        {app.appliedAt.split('T')[0]}
                      </td>

                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end space-x-1">
                          <button
                            onClick={() => setTimelineApp(app)}
                            className="px-2.5 py-1 rounded-md border border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 font-medium transition cursor-pointer"
                            title="View Status Timeline"
                          >
                            Timeline ({app.stageHistory.length})
                          </button>

                          {canManageStages && !['REJECTED', 'WITHDRAWN', 'JOINED'].includes(app.currentStage) && (
                            <>
                              <button
                                onClick={() => handleOpenUpdateStage(app)}
                                className="px-2.5 py-1 rounded-md bg-cyan-600 hover:bg-cyan-500 text-white font-semibold transition cursor-pointer shadow-xs"
                                title="Advance Stage"
                              >
                                Advance
                              </button>
                              <button
                                onClick={() => {
                                  setRejectingApp(app);
                                  setRejectionReason('');
                                }}
                                className="px-2 py-1 rounded-md hover:bg-rose-50 dark:hover:bg-rose-950/40 text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 font-medium transition cursor-pointer"
                                title="Reject Application"
                              >
                                Reject
                              </button>
                            </>
                          )}

                          {currentUser?.role === 'STUDENT' && !['SELECTED', 'OFFERED', 'JOINED', 'WITHDRAWN'].includes(app.currentStage) && (
                            <button
                              onClick={() => setWithdrawingApp(app)}
                              className="px-2.5 py-1 rounded-md hover:bg-rose-50 dark:hover:bg-rose-950/40 text-rose-600 dark:text-rose-400 font-medium transition cursor-pointer"
                              title="Withdraw Application"
                            >
                              Withdraw
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <div className="p-3 bg-slate-50 dark:bg-slate-950/80 border-t border-slate-200 dark:border-slate-800 flex justify-between items-center text-xs text-slate-500 dark:text-slate-400">
          <span>Showing {filteredApps.length} applications</span>
          <span>Official Recruitment Ledger</span>
        </div>
      </div>

      {/* Timeline Modal */}
      {timelineApp && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-lg w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <div>
                <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">
                  Placement Journey Timeline
                </h3>
                <div className="text-[11px] text-slate-500 dark:text-slate-400">
                  {timelineApp.studentName} ({timelineApp.studentRollNumber}) · {timelineApp.companyName}
                </div>
              </div>
              <button onClick={() => setTimelineApp(null)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="mt-4 space-y-4 max-h-[400px] overflow-y-auto pl-2">
              {timelineApp.stageHistory.map((step, idx) => (
                <div key={idx} className="relative flex items-start space-x-3 text-xs">
                  {idx !== timelineApp.stageHistory.length - 1 && (
                    <div className="absolute left-[9px] top-4 w-0.5 h-full bg-slate-200 dark:bg-slate-700" />
                  )}
                  <div className="w-5 h-5 rounded-full bg-cyan-600 text-white flex items-center justify-center font-bold text-[10px] shrink-0 z-10">
                    {idx + 1}
                  </div>
                  <div className="flex-1 pb-4">
                    <div className="flex justify-between items-baseline">
                      <span className="font-bold text-slate-900 dark:text-slate-100">
                        {APPLICATION_STAGE_LABELS[step.stage]}
                      </span>
                      <span className="text-[10px] text-slate-400 font-mono">
                        {new Date(step.timestamp).toLocaleDateString()}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                      Updated by: {step.updatedBy}
                    </div>
                    {step.notes && (
                      <div className="mt-1 p-2 rounded bg-slate-50 dark:bg-slate-950/70 border border-slate-200/60 dark:border-slate-800 text-[11px] text-slate-700 dark:text-slate-300">
                        {step.notes}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>

            <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end">
              <button
                onClick={() => setTimelineApp(null)}
                className="px-4 py-1.5 rounded-lg bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-200 text-xs font-semibold cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Advance Stage Modal */}
      {updatingApp && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-md w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">Advance Recruitment Stage</h3>
              <button onClick={() => setUpdatingApp(null)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSaveStageUpdate} className="mt-4 space-y-3 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Target Stage *</label>
                <select
                  value={targetStage}
                  onChange={(e) => setTargetStage(e.target.value as ApplicationStage)}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-medium"
                >
                  {STAGE_ORDER.map((stage) => (
                    <option key={stage} value={stage}>
                      {APPLICATION_STAGE_LABELS[stage]}
                    </option>
                  ))}
                </select>
              </div>

              {['SELECTED', 'OFFERED', 'JOINED'].includes(targetStage) && (
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Offered Salary Package (LPA) *
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    min="1"
                    max="100"
                    required
                    value={offerLpa}
                    onChange={(e) => setOfferLpa(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              )}

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Evaluation &amp; Panel Notes</label>
                <textarea
                  rows={3}
                  placeholder="Record assessment score, interviewer comments, or panel feedback..."
                  value={stageNotes}
                  onChange={(e) => setStageNotes(e.target.value)}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setUpdatingApp(null)}
                  className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold cursor-pointer shadow-xs"
                >
                  Confirm Stage Transition
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {rejectingApp && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-md w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-sm font-bold text-rose-700 dark:text-rose-400">Record Application Rejection</h3>
              <button onClick={() => setRejectingApp(null)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleConfirmReject} className="mt-4 space-y-3 text-xs">
              <p className="text-slate-600 dark:text-slate-300 leading-relaxed">
                You are marking <strong>{rejectingApp.studentName}</strong> as rejected for <strong>{rejectingApp.companyName}</strong>. You must state the feedback or cutoff failure reason.
              </p>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Rejection Reason *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="e.g. Failed Technical Round 2 DSA graph questions; did not meet sectional coding cutoff..."
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-rose-500"
                />
              </div>

              <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setRejectingApp(null)}
                  className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white font-semibold cursor-pointer shadow-xs"
                >
                  Confirm Rejection
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Withdrawal Confirmation */}
      <ConfirmationModal
        isOpen={!!withdrawingApp}
        title="Withdraw Placement Application"
        message={`Are you sure you want to withdraw your application for ${withdrawingApp?.companyName} (${withdrawingApp?.jobRole})?`}
        confirmLabel="Withdraw Application"
        isDestructive={true}
        onConfirm={handleConfirmWithdraw}
        onCancel={() => setWithdrawingApp(null)}
      />

    </div>
  );
};
