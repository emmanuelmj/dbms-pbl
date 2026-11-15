import React, { useState, useMemo } from 'react';
import {
  Users,
  Search,
  Plus,
  Filter,
  Download,
  Eye,
  Edit2,
  Trash2,
  ArrowUpDown,
  GraduationCap,
  X,
} from 'lucide-react';
import { Student, Branch, User } from '../types';
import { api } from '../services/api';
import { ConfirmationModal } from '../components/ConfirmationModal';

interface StudentsPageProps {
  currentUser: User | null;
  students: Student[];
  onSelectStudentProfile: (studentId: string) => void;
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

export const StudentsPage: React.FC<StudentsPageProps> = ({
  currentUser,
  students,
  onSelectStudentProfile,
  onRefreshData,
  onShowToast,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedBranch, setSelectedBranch] = useState<string>('ALL');
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');
  const [selectedGradYear, setSelectedGradYear] = useState<string>('ALL');
  const [sortField, setSortField] = useState<'rollNumber' | 'fullName' | 'cgpa'>('rollNumber');
  const [sortAsc, setSortAsc] = useState(true);

  // Modal states
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingStudent, setEditingStudent] = useState<Student | null>(null);
  const [deleteCandidate, setDeleteCandidate] = useState<Student | null>(null);

  // Form fields
  const [formData, setFormData] = useState({
    rollNumber: '',
    fullName: '',
    email: '',
    phone: '',
    branch: 'CSE' as Branch,
    cgpa: 8.0,
    tenthPercentage: 85.0,
    interOrDiplomaPercentage: 88.0,
    graduationYear: 2026,
    backlogs: 0,
  });

  const canManage = currentUser?.role === 'ADMIN' || currentUser?.role === 'PLACEMENT_OFFICER';

  const handleOpenAdd = () => {
    setEditingStudent(null);
    setFormData({
      rollNumber: '',
      fullName: '',
      email: '',
      phone: '',
      branch: 'CSE',
      cgpa: 7.5,
      tenthPercentage: 85.0,
      interOrDiplomaPercentage: 85.0,
      graduationYear: 2026,
      backlogs: 0,
    });
    setIsFormOpen(true);
  };

  const handleOpenEdit = (student: Student) => {
    setEditingStudent(student);
    setFormData({
      rollNumber: student.rollNumber,
      fullName: student.fullName,
      email: student.email,
      phone: student.phone,
      branch: student.branch,
      cgpa: student.cgpa,
      tenthPercentage: student.tenthPercentage,
      interOrDiplomaPercentage: student.interOrDiplomaPercentage,
      graduationYear: student.graduationYear,
      backlogs: student.backlogs,
    });
    setIsFormOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (!formData.rollNumber.trim()) throw new Error('Roll Number is mandatory.');
      if (!formData.fullName.trim()) throw new Error('Full Name is mandatory.');
      if (!formData.email.trim()) throw new Error('Email address is mandatory.');
      if (formData.cgpa < 0 || formData.cgpa > 10) throw new Error('CGPA must be between 0.00 and 10.00.');

      if (editingStudent) {
        await api.updateStudent(editingStudent.id, formData);
        await onRefreshData();
        onShowToast('success', 'Student Updated', `Successfully updated MySQL profile for ${formData.fullName}`);
      } else {
        await api.createStudent(formData);
        await onRefreshData();
        onShowToast('success', 'Student Enrolled', `Saved new student ${formData.fullName} (${formData.rollNumber}) in MySQL`);
      }
      setIsFormOpen(false);
    } catch (err: any) {
      onShowToast('error', 'Error Saving Student', err.message);
    }
  };

  const confirmDelete = async () => {
    if (!deleteCandidate) return;
    try {
      await api.deleteStudent(deleteCandidate.id);
      await onRefreshData();
      onShowToast('success', 'Student Removed', `Deleted MySQL record for ${deleteCandidate.fullName}`);
      setDeleteCandidate(null);
    } catch (err: any) {
      onShowToast('error', 'Cannot Delete Student', err.message);
      setDeleteCandidate(null);
    }
  };

  // Filter & Search Logic
  const filteredStudents = useMemo(() => {
    return students.filter((s) => {
      const q = searchQuery.toLowerCase().trim();
      const matchSearch = !q || s.rollNumber.toLowerCase().includes(q) || s.fullName.toLowerCase().includes(q) || s.email.toLowerCase().includes(q);
      const matchBranch = selectedBranch === 'ALL' || s.branch === selectedBranch;
      const matchStatus = selectedStatus === 'ALL' || s.placementStatus === selectedStatus;
      const matchYear = selectedGradYear === 'ALL' || s.graduationYear.toString() === selectedGradYear;
      return matchSearch && matchBranch && matchStatus && matchYear;
    }).sort((a, b) => {
      let cmp = 0;
      if (sortField === 'rollNumber') cmp = a.rollNumber.localeCompare(b.rollNumber);
      else if (sortField === 'fullName') cmp = a.fullName.localeCompare(b.fullName);
      else if (sortField === 'cgpa') cmp = a.cgpa - b.cgpa;
      return sortAsc ? cmp : -cmp;
    });
  }, [students, searchQuery, selectedBranch, selectedStatus, selectedGradYear, sortField, sortAsc]);

  // CSV Export
  const exportCsv = () => {
    const headers = ['Roll Number', 'Full Name', 'Email', 'Phone', 'Branch', 'CGPA', 'Graduation Year', 'Backlogs', 'Placement Status', 'Placed Package LPA'];
    const rows = filteredStudents.map((s) => [
      s.rollNumber,
      `"${s.fullName}"`,
      s.email,
      s.phone || '',
      `"${s.branch}"`,
      s.cgpa.toFixed(2),
      s.graduationYear,
      s.backlogs,
      s.placementStatus,
      s.placedPackageLpa || '',
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `GCET_Students_Placement_Directory_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    onShowToast('info', 'Export Complete', `Exported ${filteredStudents.length} student records.`);
  };

  return (
    <div className="space-y-5">
      
      {/* Top Header & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Student Directory &amp; Academic Profiles</h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Manage student academic credentials, technical skills, and placement records.
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={exportCsv}
            className="px-3.5 py-2 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium transition cursor-pointer flex items-center space-x-1.5"
          >
            <Download className="w-3.5 h-3.5 text-slate-500 dark:text-slate-400" />
            <span>Export CSV</span>
          </button>

          {canManage && (
            <button
              onClick={handleOpenAdd}
              className="px-3.5 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition cursor-pointer flex items-center space-x-1.5 shadow-xs"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Enroll Student</span>
            </button>
          )}
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs flex flex-col md:flex-row items-stretch md:items-center gap-3">
        {/* Search */}
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search by Roll No (e.g. 22R11A0501), Name, or Email..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
          />
        </div>

        {/* Branch Filter */}
        <div className="flex items-center space-x-2">
          <span className="text-xs text-slate-500 dark:text-slate-400 shrink-0">Branch:</span>
          <select
            value={selectedBranch}
            onChange={(e) => setSelectedBranch(e.target.value)}
            className="px-2.5 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
          >
            <option value="ALL">All Branches</option>
            {ALL_BRANCHES.map((b) => (
              <option key={b} value={b}>{b}</option>
            ))}
          </select>
        </div>

        {/* Status Filter */}
        <div className="flex items-center space-x-2">
          <span className="text-xs text-slate-500 dark:text-slate-400 shrink-0">Status:</span>
          <select
            value={selectedStatus}
            onChange={(e) => setSelectedStatus(e.target.value)}
            className="px-2.5 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
          >
            <option value="ALL">All Statuses</option>
            <option value="UNPLACED">Unplaced</option>
            <option value="SHORTLISTED">Shortlisted</option>
            <option value="PLACED">Placed</option>
            <option value="OPTED_OUT">Opted Out</option>
          </select>
        </div>

        {/* Grad Year Filter */}
        <div className="flex items-center space-x-2">
          <span className="text-xs text-slate-500 dark:text-slate-400 shrink-0">Batch:</span>
          <select
            value={selectedGradYear}
            onChange={(e) => setSelectedGradYear(e.target.value)}
            className="px-2.5 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
          >
            <option value="ALL">All Batches</option>
            <option value="2025">2025</option>
            <option value="2026">2026</option>
            <option value="2027">2027</option>
          </select>
        </div>
      </div>

      {/* Students Data Table */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-50 dark:bg-slate-950/80 text-slate-600 dark:text-slate-300 font-semibold border-b border-slate-200 dark:border-slate-800 select-none">
              <tr>
                <th 
                  className="py-3 px-4 cursor-pointer hover:text-slate-900 dark:hover:text-white"
                  onClick={() => { setSortField('rollNumber'); setSortAsc(!sortAsc); }}
                >
                  <div className="flex items-center space-x-1">
                    <span>Roll Number</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th 
                  className="py-3 px-4 cursor-pointer hover:text-slate-900 dark:hover:text-white"
                  onClick={() => { setSortField('fullName'); setSortAsc(!sortAsc); }}
                >
                  <div className="flex items-center space-x-1">
                    <span>Student Name</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th className="py-3 px-4">Branch</th>
                <th 
                  className="py-3 px-4 cursor-pointer hover:text-slate-900 dark:hover:text-white"
                  onClick={() => { setSortField('cgpa'); setSortAsc(!sortAsc); }}
                >
                  <div className="flex items-center space-x-1">
                    <span>CGPA</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th className="py-3 px-4">Skills Verified</th>
                <th className="py-3 px-4">Placement Status</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {filteredStudents.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-10 text-center text-xs text-slate-400">
                    No student records match the selected query or filters.
                  </td>
                </tr>
              ) : (
                filteredStudents.map((s) => {
                  const verifiedSkills = s.skills.filter((sk) => sk.verifiedByFaculty).length;

                  return (
                    <tr key={s.id} className="hover:bg-slate-50/80 dark:hover:bg-slate-800/50 transition">
                      <td className="py-3 px-4 font-mono font-medium text-slate-800 dark:text-cyan-400">
                        {s.rollNumber}
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-semibold text-slate-900 dark:text-slate-100">{s.fullName}</div>
                        <div className="text-[11px] text-slate-500 dark:text-slate-400">{s.email}</div>
                      </td>
                      <td className="py-3 px-4 text-slate-700 dark:text-slate-300">
                        <span className="font-medium">{s.branch}</span>
                        <span className="text-slate-400 dark:text-slate-500 text-[11px]"> ({s.graduationYear})</span>
                      </td>
                      <td className="py-3 px-4 font-semibold text-slate-800 dark:text-slate-200">
                        {s.cgpa.toFixed(2)}
                        {s.backlogs > 0 && (
                          <span className="text-[10px] text-rose-600 dark:text-rose-400 block">
                            {s.backlogs} backlog{s.backlogs > 1 ? 's' : ''}
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-slate-600 dark:text-slate-400">
                        {verifiedSkills} / {s.skills.length} skills verified
                      </td>
                      <td className="py-3 px-4">
                        <span className={`text-[10px] font-semibold px-2 py-0.5 rounded ${
                          s.placementStatus === 'PLACED'
                            ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
                            : s.placementStatus === 'SHORTLISTED'
                            ? 'bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800'
                            : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400'
                        }`}>
                          {s.placementStatus}
                          {s.placedPackageLpa ? ` (${s.placedPackageLpa} LPA)` : ''}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end space-x-1">
                          <button
                            onClick={() => onSelectStudentProfile(s.id)}
                            className="p-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-600 dark:text-slate-400 hover:text-cyan-600 dark:hover:text-cyan-400 transition cursor-pointer"
                            title="View Full Profile & Skills"
                          >
                            <Eye className="w-3.5 h-3.5" />
                          </button>

                          {canManage && (
                            <>
                              <button
                                onClick={() => handleOpenEdit(s)}
                                className="p-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition cursor-pointer"
                                title="Edit Student Data"
                              >
                                <Edit2 className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => setDeleteCandidate(s)}
                                className="p-1.5 rounded-md hover:bg-rose-50 dark:hover:bg-rose-950/60 text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 transition cursor-pointer"
                                title="Delete Record"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </>
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

        <div className="p-3 bg-slate-50 dark:bg-slate-950 border-t border-slate-200 dark:border-slate-800 flex justify-between items-center text-xs text-slate-500 dark:text-slate-400">
          <span>Showing {filteredStudents.length} of {students.length} registered students</span>
          <span className="font-mono text-[11px]">Institution Code: GCET-22R</span>
        </div>
      </div>

      {/* Add / Edit Student Modal */}
      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-xl w-full shadow-2xl border border-slate-200 dark:border-slate-800 overflow-hidden">
            <div className="p-5 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between bg-slate-50 dark:bg-slate-950/80">
              <div className="flex items-center space-x-2">
                <GraduationCap className="w-4 h-4 text-cyan-600 dark:text-cyan-400" />
                <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">
                  {editingStudent ? `Edit Student: ${editingStudent.rollNumber}` : 'Enroll New Student'}
                </h3>
              </div>
              <button
                onClick={() => setIsFormOpen(false)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1 rounded-md"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSave} className="p-5 space-y-4 text-xs">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Roll Number (GCET Hall Ticket) *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. 22R11A0509"
                    value={formData.rollNumber}
                    onChange={(e) => setFormData({ ...formData, rollNumber: e.target.value.toUpperCase() })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 font-mono focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Full Name *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Student full legal name"
                    value={formData.fullName}
                    onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    College Email *
                  </label>
                  <input
                    type="email"
                    required
                    placeholder="student@gcet.edu.in"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Phone Number
                  </label>
                  <input
                    type="text"
                    placeholder="+91 98765 43210"
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Branch *
                  </label>
                  <select
                    value={formData.branch}
                    onChange={(e) => setFormData({ ...formData, branch: e.target.value as Branch })}
                    className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
                  >
                    {ALL_BRANCHES.map((b) => (
                      <option key={b} value={b}>{b}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Cumulative CGPA *
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    max="10"
                    required
                    value={formData.cgpa}
                    onChange={(e) => setFormData({ ...formData, cgpa: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Graduation Year *
                  </label>
                  <input
                    type="number"
                    min="2024"
                    max="2030"
                    required
                    value={formData.graduationYear}
                    onChange={(e) => setFormData({ ...formData, graduationYear: parseInt(e.target.value) || 2026 })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    10th (SSC) %
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    max="100"
                    value={formData.tenthPercentage}
                    onChange={(e) => setFormData({ ...formData, tenthPercentage: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Inter / Diploma %
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    max="100"
                    value={formData.interOrDiplomaPercentage}
                    onChange={(e) => setFormData({ ...formData, interOrDiplomaPercentage: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Active Backlogs
                  </label>
                  <input
                    type="number"
                    min="0"
                    max="20"
                    value={formData.backlogs}
                    onChange={(e) => setFormData({ ...formData, backlogs: parseInt(e.target.value) || 0 })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              </div>

              <div className="pt-4 border-t border-slate-100 dark:border-slate-800 flex items-center justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsFormOpen(false)}
                  className="px-3.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 transition cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold transition cursor-pointer"
                >
                  {editingStudent ? 'Save Changes' : 'Enroll Student'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!deleteCandidate}
        title="Confirm Student Deletion"
        message={`Are you sure you want to permanently delete ${deleteCandidate?.fullName} (${deleteCandidate?.rollNumber})? This will delete all associated skills and profile records.`}
        confirmLabel="Delete Permanently"
        isDestructive={true}
        onConfirm={confirmDelete}
        onCancel={() => setDeleteCandidate(null)}
      />

    </div>
  );
};
