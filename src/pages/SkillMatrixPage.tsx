import React, { useState, useMemo } from 'react';
import {
  Grid3X3,
  Search,
  Plus,
  Filter,
  CheckCircle2,
  Users,
  ShieldCheck,
  Award,
  Layers,
  X,
  Edit2,
  Trash2,
} from 'lucide-react';
import {
  Skill,
  SkillCategory,
  Student,
  User,
  SkillProficiency,
  PROFICIENCY_LABELS,
} from '../types';
import { api } from '../services/api';

interface SkillMatrixPageProps {
  currentUser: User | null;
  skills: Skill[];
  students: Student[];
  onSelectStudentProfile: (studentId: string) => void;
  onRefreshData: () => Promise<void>;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

const CATEGORIES: SkillCategory[] = [
  'Programming Languages',
  'Data Structures & Algorithms',
  'Database Management',
  'Web Development',
  'AI & Machine Learning',
  'Cloud Computing',
  'Operating Systems & Networks',
  'Communication',
  'Aptitude',
  'Software Tools',
];

export const SkillMatrixPage: React.FC<SkillMatrixPageProps> = ({
  currentUser,
  skills,
  students,
  onSelectStudentProfile,
  onRefreshData,
  onShowToast,
}) => {
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [inspectingSkill, setInspectingSkill] = useState<Skill | null>(null);

  // Skill Add/Edit modal
  const [isSkillFormOpen, setIsSkillFormOpen] = useState(false);
  const [editingSkill, setEditingSkill] = useState<Skill | null>(null);
  const [skillForm, setSkillForm] = useState({
    name: '',
    category: 'Programming Languages' as SkillCategory,
    description: '',
  });

  const canManageSkills = currentUser?.role === 'ADMIN' || currentUser?.role === 'PLACEMENT_OFFICER';

  const filteredSkills = useMemo(() => {
    return skills.filter((s) => {
      const matchCat = selectedCategory === 'ALL' || s.category === selectedCategory;
      const q = searchQuery.toLowerCase().trim();
      const matchSearch = !q || s.name.toLowerCase().includes(q) || s.description.toLowerCase().includes(q);
      return matchCat && matchSearch;
    });
  }, [skills, selectedCategory, searchQuery]);

  // Students who possess the inspected skill
  const skilledStudents = useMemo(() => {
    if (!inspectingSkill) return [];
    return students
      .map((student) => {
        const studentSkill = student.skills.find((sk) => sk.skillId === inspectingSkill.id);
        if (!studentSkill) return null;
        return {
          student,
          proficiency: studentSkill.proficiency,
          verified: studentSkill.verifiedByFaculty,
        };
      })
      .filter(Boolean) as { student: Student; proficiency: SkillProficiency; verified: boolean }[];
  }, [inspectingSkill, students]);

  const handleOpenAdd = () => {
    setEditingSkill(null);
    setSkillForm({
      name: '',
      category: 'Programming Languages',
      description: '',
    });
    setIsSkillFormOpen(true);
  };

  const handleOpenEdit = (skill: Skill, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingSkill(skill);
    setSkillForm({
      name: skill.name,
      category: skill.category,
      description: skill.description,
    });
    setIsSkillFormOpen(true);
  };

  const handleDeleteSkill = async (skill: Skill, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await api.deleteSkill(skill.id);
      await onRefreshData();
      onShowToast('success', 'Skill Deleted', `Removed ${skill.name} from MySQL skills catalog.`);
      if (inspectingSkill?.id === skill.id) setInspectingSkill(null);
    } catch (err: any) {
      onShowToast('error', 'Cannot Delete', err.message);
    }
  };

  const handleSaveSkill = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!skillForm.name.trim()) return;

    try {
      if (editingSkill) {
        await api.updateSkill(editingSkill.id, {
          name: skillForm.name.trim(),
          category: skillForm.category,
          description: skillForm.description.trim(),
        });
        await onRefreshData();
        onShowToast('success', 'Skill Updated', `Saved changes to ${skillForm.name} in MySQL`);
      } else {
        await api.createSkill({
          name: skillForm.name.trim(),
          category: skillForm.category,
          description: skillForm.description.trim(),
        });
        await onRefreshData();
        onShowToast('success', 'Skill Created', `Added ${skillForm.name} to MySQL catalog.`);
      }
      setIsSkillFormOpen(false);
    } catch (err: any) {
      onShowToast('error', 'Error Saving Skill', err.message);
    }
  };

  return (
    <div className="space-y-5">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">GCET Technical Skill Matrix</h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Institutional proficiency repository mapping curriculum domains to industry hiring benchmarks.
          </p>
        </div>

        {canManageSkills && (
          <button
            onClick={handleOpenAdd}
            className="px-3.5 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition cursor-pointer flex items-center space-x-1.5 self-start sm:self-auto shadow-xs"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Skill to Catalog</span>
          </button>
        )}
      </div>

      {/* Filter Tabs & Search */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs space-y-3">
        <div className="flex flex-col md:flex-row items-stretch md:items-center gap-3">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              type="text"
              placeholder="Search skill (e.g. Java, MySQL, React, Docker)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
            />
          </div>

          <div className="flex items-center space-x-2">
            <Filter className="w-4 h-4 text-slate-400" />
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="px-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
            >
              <option value="ALL">All 10 Categories</option>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Category Pills Filter Bar */}
        <div className="flex flex-wrap gap-1 pt-1">
          <button
            onClick={() => setSelectedCategory('ALL')}
            className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition cursor-pointer ${
              selectedCategory === 'ALL'
                ? 'bg-slate-900 dark:bg-cyan-600 text-white font-semibold'
                : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
            }`}
          >
            All ({skills.length})
          </button>
          {CATEGORIES.map((cat) => {
            const count = skills.filter((s) => s.category === cat).length;
            const active = selectedCategory === cat;
            return (
              <button
                key={cat}
                onClick={() => setSelectedCategory(cat)}
                className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition cursor-pointer ${
                  active
                    ? 'bg-cyan-700 dark:bg-cyan-600 text-white font-semibold'
                    : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
                }`}
              >
                {cat} ({count})
              </button>
            );
          })}
        </div>
      </div>

      {/* Main Grid: Skills Cards & Student Distribution Drawer */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left 2 Cols: Skills Matrix Cards */}
        <div className="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-3">
          {filteredSkills.length === 0 ? (
            <div className="col-span-2 p-12 text-center bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 text-xs text-slate-400">
              No skills match your category or search query.
            </div>
          ) : (
            filteredSkills.map((skill) => {
              // Count students who have this skill
              const enrolledCount = students.filter((st) => st.skills.some((sk) => sk.skillId === skill.id)).length;
              const isSelected = inspectingSkill?.id === skill.id;

              return (
                <div
                  key={skill.id}
                  onClick={() => setInspectingSkill(skill)}
                  className={`p-4 rounded-xl border transition cursor-pointer flex flex-col justify-between text-xs ${
                    isSelected
                      ? 'bg-cyan-50/60 dark:bg-cyan-950/30 border-cyan-500 dark:border-cyan-500 shadow-xs ring-1 ring-cyan-500'
                      : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs'
                  }`}
                >
                  <div>
                    <div className="flex items-start justify-between">
                      <div>
                        <div className="font-bold text-slate-900 dark:text-slate-100">{skill.name}</div>
                        <div className="text-[10px] text-cyan-700 dark:text-cyan-400 font-medium mt-0.5">{skill.category}</div>
                      </div>

                      {canManageSkills && (
                        <div className="flex items-center space-x-1">
                          <button
                            onClick={(e) => handleOpenEdit(skill, e)}
                            className="p-1 rounded text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                            title="Edit Skill"
                          >
                            <Edit2 className="w-3 h-3" />
                          </button>
                          <button
                            onClick={(e) => handleDeleteSkill(skill, e)}
                            className="p-1 rounded text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 cursor-pointer"
                            title="Delete Skill"
                          >
                            <Trash2 className="w-3 h-3" />
                          </button>
                        </div>
                      )}
                    </div>

                    <p className="text-[11px] text-slate-600 dark:text-slate-300 mt-2 line-clamp-2 leading-relaxed">
                      {skill.description}
                    </p>
                  </div>

                  <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px]">
                    <span className="text-slate-500 dark:text-slate-400 flex items-center space-x-1">
                      <Users className="w-3.5 h-3.5 text-slate-400" />
                      <span>{enrolledCount} Students Qualified</span>
                    </span>

                    <span className="text-cyan-700 dark:text-cyan-400 font-semibold hover:underline">
                      {isSelected ? 'Inspecting' : 'View Students'}
                    </span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Right 1 Col: Student Inspector for Selected Skill */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs flex flex-col">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800">
            <div>
              <h2 className="text-sm font-bold text-slate-900 dark:text-slate-100">Talent Distribution</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                {inspectingSkill ? inspectingSkill.name : 'Select a skill to view qualified candidates'}
              </p>
            </div>
            {inspectingSkill && (
              <button
                onClick={() => setInspectingSkill(null)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1 rounded cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          <div className="flex-1 mt-4 space-y-3 overflow-y-auto max-h-[550px]">
            {!inspectingSkill ? (
              <div className="py-16 text-center text-xs text-slate-400">
                <Grid3X3 className="w-8 h-8 text-slate-300 dark:text-slate-600 mx-auto mb-2" />
                Click on any technical skill on the left to inspect students assessed in that domain.
              </div>
            ) : skilledStudents.length === 0 ? (
              <div className="py-12 text-center text-xs text-slate-400">
                No GCET students have recorded proficiency in {inspectingSkill.name} yet.
              </div>
            ) : (
              skilledStudents.map(({ student, proficiency, verified }) => (
                <div
                  key={student.id}
                  onClick={() => onSelectStudentProfile(student.id)}
                  className="p-3 rounded-lg border border-slate-100 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-950/70 hover:bg-slate-100/80 dark:hover:bg-slate-800/80 transition cursor-pointer flex items-center justify-between text-xs"
                >
                  <div>
                    <div className="font-semibold text-slate-900 dark:text-slate-100">{student.fullName}</div>
                    <div className="text-[11px] text-slate-500 dark:text-slate-400">
                      {student.rollNumber} · {student.branch} (CGPA {student.cgpa.toFixed(2)})
                    </div>
                  </div>

                  <div className="text-right">
                    <span className="font-semibold text-cyan-800 dark:text-cyan-300 text-xs block">
                      Level {proficiency}: {PROFICIENCY_LABELS[proficiency]}
                    </span>
                    <span className={`text-[10px] ${verified ? 'text-emerald-700 dark:text-emerald-400 font-medium' : 'text-slate-400'}`}>
                      {verified ? '✓ Faculty Verified' : 'Self-Assessed'}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

      </div>

      {/* Add / Edit Skill Modal */}
      {isSkillFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-md w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">
                {editingSkill ? `Edit Skill: ${editingSkill.name}` : 'Add New Skill to Matrix'}
              </h3>
              <button onClick={() => setIsSkillFormOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSaveSkill} className="mt-4 space-y-3 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Skill Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Go (Golang) Microservices"
                  value={skillForm.name}
                  onChange={(e) => setSkillForm({ ...skillForm, name: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Skill Category *</label>
                <select
                  value={skillForm.category}
                  onChange={(e) => setSkillForm({ ...skillForm, category: e.target.value as SkillCategory })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                >
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Description &amp; Topics</label>
                <textarea
                  rows={3}
                  placeholder="Core concepts, frameworks, and assessment expectations..."
                  value={skillForm.description}
                  onChange={(e) => setSkillForm({ ...skillForm, description: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsSkillFormOpen(false)}
                  className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold cursor-pointer shadow-xs"
                >
                  {editingSkill ? 'Update Skill' : 'Create Skill'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};
