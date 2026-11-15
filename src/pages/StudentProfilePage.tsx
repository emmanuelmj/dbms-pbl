import React, { useState, useMemo, useEffect } from 'react';
import {
  GraduationCap,
  Award,
  Briefcase,
  FileText,
  Plus,
  CheckCircle2,
  AlertCircle,
  ExternalLink,
  ShieldCheck,
  Star,
  Layers,
  Calendar,
  MapPin,
  Link2,
  Trophy,
  Code2,
  X,
} from 'lucide-react';
import {
  Student,
  User,
  Skill,
  SkillProficiency,
  PROFICIENCY_LABELS,
  StudentProject,
  Certification,
  Internship,
  SocialLinks,
  CustomLink,
  UnifiedUserProfile,
} from '../types';
import { db, computeLocalProfileCompletion } from '../services/db';
import { api, getStoredToken } from '../services/api';

interface StudentProfilePageProps {
  currentUser: User | null;
  students: Student[];
  skills: Skill[];
  initialStudentId?: string;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

export const StudentProfilePage: React.FC<StudentProfilePageProps> = ({
  currentUser,
  students,
  skills,
  initialStudentId,
  onShowToast,
}) => {
  // If logged in as student, enforce their student ID, otherwise allow selecting student
  const activeStudentId = useMemo(() => {
    if (currentUser?.role === 'STUDENT' && currentUser.studentRollNumber) {
      const self = students.find((s) => s.rollNumber.toLowerCase() === currentUser.studentRollNumber?.toLowerCase());
      if (self) return self.id;
    }
    return initialStudentId || students[0]?.id || '';
  }, [currentUser, students, initialStudentId]);

  const [selectedStudentId, setSelectedStudentId] = useState<string>(activeStudentId);

  useEffect(() => {
    if (initialStudentId && currentUser?.role !== 'STUDENT') {
      setSelectedStudentId(initialStudentId);
    }
  }, [initialStudentId, currentUser?.role]);

  const baseStudent = students.find((s) => s.id === selectedStudentId) || students[0];
  const [remoteProfile, setRemoteProfile] = useState<UnifiedUserProfile | null>(null);

  useEffect(() => {
    let active = true;
    setRemoteProfile(null);
    if (!baseStudent) return;

    const matchedUser = db.getUsers().find(
      (u) =>
        (u.studentRollNumber && u.studentRollNumber.toLowerCase() === baseStudent.rollNumber.toLowerCase()) ||
        u.username.toLowerCase() === baseStudent.rollNumber.toLowerCase() ||
        (baseStudent.email && u.email.toLowerCase() === baseStudent.email.toLowerCase())
    );
    if (matchedUser) {
      setRemoteProfile(db.getUnifiedProfile(matchedUser));
    }

    if (getStoredToken()) {
      api
        .getStudentProfile(baseStudent.rollNumber || baseStudent.id)
        .then((res) => {
          if (active && res && res.student) {
            setRemoteProfile(res as UnifiedUserProfile);
          }
        })
        .catch(() => {
          // Keep local cache silently
        });
    }
    return () => {
      active = false;
    };
  }, [baseStudent?.id, baseStudent?.rollNumber]);

  const currentStudent: Student | undefined = useMemo(() => {
    if (!baseStudent) return undefined;
    if (!remoteProfile?.student) return baseStudent;
    return {
      ...baseStudent,
      ...remoteProfile.student,
      skills: remoteProfile.student.skills?.length ? remoteProfile.student.skills : baseStudent.skills,
      projects: remoteProfile.student.projects?.length ? remoteProfile.student.projects : baseStudent.projects,
      certifications: remoteProfile.student.certifications?.length
        ? remoteProfile.student.certifications
        : baseStudent.certifications,
      internships: remoteProfile.student.internships?.length
        ? remoteProfile.student.internships
        : baseStudent.internships,
    };
  }, [baseStudent, remoteProfile]);

  const resolvedPhoto = remoteProfile?.user?.profilePhoto || currentStudent?.profilePhoto || '';
  const resolvedBio = remoteProfile?.user?.aboutMe || currentStudent?.aboutMe || '';
  const resolvedLocation = remoteProfile?.user?.location || currentStudent?.location || '';
  const resolvedSocialLinks: SocialLinks = remoteProfile?.socialLinks || currentStudent?.socialLinks || {};
  const resolvedCustomLinks: CustomLink[] = remoteProfile?.customLinks || currentStudent?.customLinks || [];

  // Modals for adding items
  const [isSkillModalOpen, setIsSkillModalOpen] = useState(false);
  const [isProjectModalOpen, setIsProjectModalOpen] = useState(false);
  const [isCertModalOpen, setIsCertModalOpen] = useState(false);

  // Skill form state
  const [selectedSkillId, setSelectedSkillId] = useState<string>(skills[0]?.id || '');
  const [skillProficiency, setSkillProficiency] = useState<SkillProficiency>(3);

  // Project form state
  const [projectForm, setProjectForm] = useState({
    title: '',
    description: '',
    technologies: '',
    link: '',
    duration: '',
  });

  // Cert form state
  const [certForm, setCertForm] = useState({
    name: '',
    issuingOrg: '',
    issueDate: '',
    credentialId: '',
  });

  // Calculate Profile Completeness %
  const completeness = useMemo(() => {
    if (!currentStudent) return 0;
    if (remoteProfile) {
      return computeLocalProfileCompletion(remoteProfile).percentage;
    }
    let score = 0;
    if (currentStudent.rollNumber && currentStudent.email && currentStudent.phone) score += 20;
    if (currentStudent.cgpa > 0 && currentStudent.tenthPercentage > 0) score += 20;
    if (currentStudent.skills.length >= 3) score += 25;
    else score += (currentStudent.skills.length / 3) * 25;
    if (currentStudent.projects.length > 0) score += 15;
    if (currentStudent.certifications.length > 0) score += 10;
    if (currentStudent.resumeUrl) score += 10;
    return Math.min(100, Math.round(score));
  }, [currentStudent, remoteProfile]);

  const nonEmptySocialButtons = useMemo(() => {
    const labels: { key: keyof SocialLinks; label: string }[] = [
      { key: 'linkedin', label: 'LinkedIn' },
      { key: 'github', label: 'GitHub' },
      { key: 'portfolio', label: 'Portfolio' },
      { key: 'leetcode', label: 'LeetCode' },
      { key: 'hackerrank', label: 'HackerRank' },
      { key: 'codechef', label: 'CodeChef' },
      { key: 'geeksforgeeks', label: 'GeeksforGeeks' },
      { key: 'kaggle', label: 'Kaggle' },
      { key: 'googleDeveloper', label: 'Google Dev' },
    ];
    return labels
      .map((item) => ({ label: item.label, url: resolvedSocialLinks[item.key]?.trim() || '' }))
      .filter((item) => Boolean(item.url));
  }, [resolvedSocialLinks]);

  if (!currentStudent) {
    return (
      <div className="p-8 text-center bg-white rounded-xl border border-slate-200">
        <p className="text-xs text-slate-500">No student profile found.</p>
      </div>
    );
  }

  const isSelf = currentUser?.role === 'STUDENT' && currentUser.studentRollNumber === currentStudent.rollNumber;
  const canEditProfile = isSelf || currentUser?.role === 'ADMIN' || currentUser?.role === 'PLACEMENT_OFFICER';
  const canVerifySkills = currentUser?.role === 'FACULTY' || currentUser?.role === 'ADMIN' || currentUser?.role === 'PLACEMENT_OFFICER';

  // Save / Update Student Skill
  const handleAddSkill = (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const existingSkillIndex = currentStudent.skills.findIndex((s) => s.skillId === selectedSkillId);
      const updatedSkills = [...currentStudent.skills];

      if (existingSkillIndex >= 0) {
        updatedSkills[existingSkillIndex] = {
          ...updatedSkills[existingSkillIndex],
          proficiency: skillProficiency,
          verifiedByFaculty: false, // reset verification on level update
        };
      } else {
        updatedSkills.push({
          skillId: selectedSkillId,
          proficiency: skillProficiency,
          verifiedByFaculty: false,
        });
      }

      const updatedStudent: Student = {
        ...currentStudent,
        skills: updatedSkills,
      };

      db.saveStudent(updatedStudent, currentUser?.fullName || 'User');
      onShowToast('success', 'Skill Updated', `Skill proficiency saved.`);
      setIsSkillModalOpen(false);
    } catch (err: any) {
      onShowToast('error', 'Failed to save skill', err.message);
    }
  };

  // Faculty verify skill
  const handleVerifySkill = (skillId: string) => {
    try {
      db.verifyStudentSkill(
        currentStudent.id,
        skillId,
        currentUser?.id || 'usr-3',
        currentUser?.fullName || 'Faculty Member'
      );
      onShowToast('success', 'Skill Verified', `Verified proficiency level for ${currentStudent.fullName}`);
    } catch (err: any) {
      onShowToast('error', 'Verification Failed', err.message);
    }
  };

  // Add project
  const handleAddProject = (e: React.FormEvent) => {
    e.preventDefault();
    if (!projectForm.title.trim()) return;

    const newProj: StudentProject = {
      id: `proj-${Date.now()}`,
      title: projectForm.title,
      description: projectForm.description,
      technologies: projectForm.technologies.split(',').map((t) => t.trim()).filter(Boolean),
      link: projectForm.link || undefined,
      duration: projectForm.duration || undefined,
    };

    const updatedStudent: Student = {
      ...currentStudent,
      projects: [...currentStudent.projects, newProj],
    };

    db.saveStudent(updatedStudent, currentUser?.fullName || 'User');
    onShowToast('success', 'Project Added', `Added project: ${newProj.title}`);
    setIsProjectModalOpen(false);
    setProjectForm({ title: '', description: '', technologies: '', link: '', duration: '' });
  };

  // Add certification
  const handleAddCert = (e: React.FormEvent) => {
    e.preventDefault();
    if (!certForm.name.trim()) return;

    const newCert: Certification = {
      id: `cert-${Date.now()}`,
      name: certForm.name,
      issuingOrg: certForm.issuingOrg,
      issueDate: certForm.issueDate || new Date().toISOString().split('T')[0],
      credentialId: certForm.credentialId || undefined,
    };

    const updatedStudent: Student = {
      ...currentStudent,
      certifications: [...currentStudent.certifications, newCert],
    };

    db.saveStudent(updatedStudent, currentUser?.fullName || 'User');
    onShowToast('success', 'Certification Added', `Added certification: ${newCert.name}`);
    setIsCertModalOpen(false);
    setCertForm({ name: '', issuingOrg: '', issueDate: '', credentialId: '' });
  };

  return (
    <div className="space-y-6">
      
      {/* Selector if Admin/Officer/Faculty */}
      {currentUser?.role !== 'STUDENT' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">Select Student Profile:</span>
            <select
              value={selectedStudentId}
              onChange={(e) => setSelectedStudentId(e.target.value)}
              className="px-3 py-1.5 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono font-medium text-slate-800 dark:text-cyan-300"
            >
              {students.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.rollNumber} - {s.fullName} ({s.branch})
                </option>
              ))}
            </select>
          </div>
          <div className="text-[11px] text-slate-500 dark:text-slate-400">
            Viewing institutional record for Geethanjali College of Engineering and Technology
          </div>
        </div>
      )}

      {/* Main Student Header Card */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 p-6 shadow-xs">
        <div className="flex flex-col md:flex-row md:items-start justify-between gap-4">
          <div className="flex items-start space-x-4">
            {resolvedPhoto ? (
              <img
                src={resolvedPhoto}
                alt={currentStudent.fullName}
                className="w-16 h-16 rounded-xl object-cover border border-cyan-500/40 shadow-sm shrink-0"
              />
            ) : (
              <div className="w-16 h-16 rounded-xl bg-gradient-to-br from-slate-800 to-cyan-900 text-cyan-300 flex items-center justify-center font-bold text-xl border border-cyan-700/50 shadow-sm shrink-0">
                {currentStudent.fullName.charAt(0)}
              </div>
            )}
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">{currentStudent.fullName}</h1>
                <span className="font-mono text-xs font-semibold text-cyan-700 dark:text-cyan-300 bg-cyan-50 dark:bg-cyan-950/60 px-2 py-0.5 rounded border border-cyan-200 dark:border-cyan-800">
                  {currentStudent.rollNumber}
                </span>
                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded ${
                  currentStudent.placementStatus === 'PLACED'
                    ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
                    : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300'
                }`}>
                  {currentStudent.placementStatus}
                  {currentStudent.placedPackageLpa ? ` · ${currentStudent.placedPackageLpa} LPA` : ''}
                </span>
              </div>

              <div className="mt-1 text-xs text-slate-600 dark:text-slate-400 flex flex-wrap items-center gap-y-1 gap-x-2.5">
                <span>{currentStudent.branch} Engineering</span>
                {currentStudent.section && <span>· Sec {currentStudent.section}</span>}
                {currentStudent.currentSemester && <span>· Sem {currentStudent.currentSemester}</span>}
                <span>· Batch of {currentStudent.graduationYear}</span>
                <span>· {currentStudent.email}</span>
                <span>· {currentStudent.phone || '+91 Not provided'}</span>
                {resolvedLocation && (
                  <span className="inline-flex items-center gap-1">
                    · <MapPin className="w-3 h-3" /> {resolvedLocation}
                  </span>
                )}
              </div>

              {resolvedBio && (
                <p className="mt-2 text-xs text-slate-600 dark:text-slate-300 max-w-2xl leading-relaxed">
                  {resolvedBio}
                </p>
              )}

              {(nonEmptySocialButtons.length > 0 || resolvedCustomLinks.length > 0 || currentStudent.resumeUrl) && (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {nonEmptySocialButtons.map((item) => (
                    <a
                      key={item.label}
                      href={item.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 hover:bg-cyan-50 dark:hover:bg-cyan-950/50 text-[11px] font-semibold text-slate-700 dark:text-slate-200 border border-slate-200 dark:border-slate-700 transition"
                    >
                      <Link2 className="w-3 h-3 text-cyan-600 dark:text-cyan-400" />
                      <span>{item.label}</span>
                      <ExternalLink className="w-2.5 h-2.5 opacity-60" />
                    </a>
                  ))}
                  {resolvedCustomLinks
                    .filter((l) => l.title?.trim() && l.url?.trim())
                    .map((cl, idx) => (
                      <a
                        key={`${cl.title}-${idx}`}
                        href={cl.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 hover:bg-cyan-50 dark:hover:bg-cyan-950/50 text-[11px] font-semibold text-slate-700 dark:text-slate-200 border border-slate-200 dark:border-slate-700 transition"
                      >
                        <Link2 className="w-3 h-3 text-cyan-600 dark:text-cyan-400" />
                        <span>{cl.title}</span>
                        <ExternalLink className="w-2.5 h-2.5 opacity-60" />
                      </a>
                    ))}
                  {currentStudent.resumeUrl && (
                    <a
                      href={currentStudent.resumeUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-cyan-50 dark:bg-cyan-950/60 hover:bg-cyan-100 dark:hover:bg-cyan-900/60 text-[11px] font-semibold text-cyan-700 dark:text-cyan-300 border border-cyan-200 dark:border-cyan-800 transition"
                    >
                      <FileText className="w-3 h-3" />
                      <span>Resume</span>
                      <ExternalLink className="w-2.5 h-2.5" />
                    </a>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Completeness Gauge */}
          <div className="bg-slate-50 dark:bg-slate-950/80 border border-slate-200 dark:border-slate-800 rounded-xl p-3.5 min-w-[200px] shrink-0">
            <div className="flex justify-between items-center text-xs mb-1.5">
              <span className="font-semibold text-slate-700 dark:text-slate-300">Profile Readiness</span>
              <span className="font-bold text-cyan-700 dark:text-cyan-400">{completeness}%</span>
            </div>
            <div className="w-full bg-slate-200 dark:bg-slate-800 h-2 rounded-full overflow-hidden">
              <div
                className="bg-cyan-600 h-full rounded-full transition-all duration-300"
                style={{ width: `${completeness}%` }}
              />
            </div>
            <div className="text-[10px] text-slate-500 dark:text-slate-400 mt-1.5">
              {completeness === 100
                ? 'Profile 100% complete & drive ready.'
                : 'Complete skills & projects for 100% eligibility score.'}
            </div>
          </div>
        </div>

        {/* Academic Score Cards Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-6 pt-5 border-t border-slate-100 dark:border-slate-800 text-xs">
          <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-950/60 border border-slate-100 dark:border-slate-800">
            <span className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">B.Tech CGPA</span>
            <div className="text-lg font-bold text-slate-900 dark:text-slate-100 mt-0.5">{currentStudent.cgpa.toFixed(2)}</div>
            <span className="text-[10px] text-slate-400 dark:text-slate-500">Scale of 10.0</span>
          </div>

          <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-950/60 border border-slate-100 dark:border-slate-800">
            <span className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">10th Standard (SSC)</span>
            <div className="text-lg font-bold text-slate-900 dark:text-slate-100 mt-0.5">{currentStudent.tenthPercentage}%</div>
            <span className="text-[10px] text-slate-400 dark:text-slate-500">Board Aggregate</span>
          </div>

          <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-950/60 border border-slate-100 dark:border-slate-800">
            <span className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">Inter / Diploma</span>
            <div className="text-lg font-bold text-slate-900 dark:text-slate-100 mt-0.5">{currentStudent.interOrDiplomaPercentage}%</div>
            <span className="text-[10px] text-slate-400 dark:text-slate-500">Pre-University</span>
          </div>

          <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-950/60 border border-slate-100 dark:border-slate-800">
            <span className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">Active Backlogs</span>
            <div className={`text-lg font-bold mt-0.5 ${currentStudent.backlogs > 0 ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-700 dark:text-emerald-400'}`}>
              {currentStudent.backlogs}
            </div>
            <span className="text-[10px] text-slate-400 dark:text-slate-500">
              {currentStudent.backlogs === 0 ? 'Clear Academic Record' : 'Requires Clearance'}
            </span>
          </div>
        </div>
      </div>

      {/* Skills Matrix & Proficiency Section */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800">
          <div>
            <h2 className="text-sm font-bold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
              <Layers className="w-4 h-4 text-cyan-600 dark:text-cyan-400" />
              <span>Technical Skills &amp; Proficiency Matrix</span>
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Assessed on a 1 (Beginner) to 5 (Expert) scale with GCET faculty verification.
            </p>
          </div>

          {canEditProfile && (
            <button
              onClick={() => setIsSkillModalOpen(true)}
              className="px-3 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition cursor-pointer flex items-center space-x-1.5"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add / Update Skill</span>
            </button>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-4">
          {currentStudent.skills.length === 0 ? (
            <div className="col-span-2 py-6 text-center text-xs text-slate-400">
              No technical skills listed yet. Add your programming languages, tools, and frameworks.
            </div>
          ) : (
            currentStudent.skills.map((stSkill) => {
              const meta = skills.find((sk) => sk.id === stSkill.skillId);
              const skillName = meta ? meta.name : stSkill.skillId;
              const category = meta ? meta.category : 'General';

              return (
                <div
                  key={stSkill.skillId}
                  className="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/60 flex flex-col justify-between hover:bg-slate-50 dark:hover:bg-slate-950/90 transition"
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="font-semibold text-xs text-slate-900 dark:text-slate-100">{skillName}</div>
                      <div className="text-[10px] text-slate-500 dark:text-slate-400">{category}</div>
                    </div>

                    {stSkill.verifiedByFaculty ? (
                      <span className="flex items-center space-x-1 text-[10px] font-medium text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-950/60 border border-emerald-200 dark:border-emerald-800 px-2 py-0.5 rounded">
                        <CheckCircle2 className="w-3 h-3" />
                        <span>Faculty Verified</span>
                      </span>
                    ) : (
                      <span className="text-[10px] font-medium text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/60 border border-amber-200 dark:border-amber-800 px-2 py-0.5 rounded">
                        Self-Assessed
                      </span>
                    )}
                  </div>

                  <div className="mt-3">
                    <div className="flex justify-between items-center text-[11px] mb-1">
                      <span className="text-slate-600 dark:text-slate-300 font-medium">
                        Level {stSkill.proficiency}: {PROFICIENCY_LABELS[stSkill.proficiency]}
                      </span>
                      <div className="flex space-x-0.5">
                        {[1, 2, 3, 4, 5].map((lvl) => (
                          <div
                            key={lvl}
                            className={`w-3 h-1.5 rounded-sm ${
                              lvl <= stSkill.proficiency ? 'bg-cyan-600' : 'bg-slate-200 dark:bg-slate-700'
                            }`}
                          />
                        ))}
                      </div>
                    </div>
                  </div>

                  {canVerifySkills && !stSkill.verifiedByFaculty && (
                    <div className="mt-2 pt-2 border-t border-slate-200 dark:border-slate-800 flex justify-end">
                      <button
                        onClick={() => handleVerifySkill(stSkill.skillId)}
                        className="text-[11px] text-cyan-700 dark:text-cyan-400 hover:text-cyan-800 dark:hover:text-cyan-300 font-semibold flex items-center space-x-1 cursor-pointer"
                      >
                        <ShieldCheck className="w-3.5 h-3.5" />
                        <span>Verify This Assessment</span>
                      </button>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>

        {/* Categorized Skills & Career Preferences */}
        {((currentStudent.programmingLanguages?.length || 0) > 0 ||
          (currentStudent.frameworksTechnologies?.length || 0) > 0 ||
          (currentStudent.technicalSkills?.length || 0) > 0 ||
          (currentStudent.softSkills?.length || 0) > 0 ||
          (currentStudent.preferredJobRoles?.length || 0) > 0 ||
          (currentStudent.careerInterests?.length || 0) > 0) && (
          <div className="mt-5 pt-4 border-t border-slate-100 dark:border-slate-800 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 text-xs">
            {[
              { label: 'Programming Languages', items: currentStudent.programmingLanguages },
              { label: 'Frameworks & Technologies', items: currentStudent.frameworksTechnologies },
              { label: 'Technical Domains', items: currentStudent.technicalSkills },
              { label: 'Soft Skills', items: currentStudent.softSkills },
              { label: 'Preferred Job Roles', items: currentStudent.preferredJobRoles },
              { label: 'Career Interests', items: currentStudent.careerInterests },
            ]
              .filter((grp) => grp.items && grp.items.length > 0)
              .map((grp) => (
                <div key={grp.label} className="space-y-1.5">
                  <div className="text-[11px] font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                    <Code2 className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
                    <span>{grp.label}</span>
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {grp.items!.map((tag, i) => (
                      <span
                        key={`${tag}-${i}`}
                        className="px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 text-[11px] border border-slate-200 dark:border-slate-700"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
          </div>
        )}
      </div>

      {/* Projects and Certifications 2-Col Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        
        {/* Projects */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800">
            <h2 className="text-sm font-bold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
              <Briefcase className="w-4 h-4 text-cyan-600 dark:text-cyan-400" />
              <span>Technical Projects ({currentStudent.projects.length})</span>
            </h2>

            {canEditProfile && (
              <button
                onClick={() => setIsProjectModalOpen(true)}
                className="text-xs font-semibold text-cyan-600 dark:text-cyan-400 hover:text-cyan-700 flex items-center space-x-1 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Project</span>
              </button>
            )}
          </div>

          <div className="mt-3 space-y-3">
            {currentStudent.projects.length === 0 ? (
              <div className="py-6 text-center text-xs text-slate-400">
                No capstone or engineering projects added yet.
              </div>
            ) : (
              currentStudent.projects.map((proj) => (
                <div key={proj.id} className="p-3 rounded-lg border border-slate-100 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-950/60 text-xs">
                  <div className="flex items-start justify-between">
                    <span className="font-bold text-slate-900 dark:text-slate-100">{proj.title}</span>
                    {proj.duration && <span className="text-[10px] text-slate-500 dark:text-slate-400">{proj.duration}</span>}
                  </div>
                  <p className="mt-1 text-slate-600 dark:text-slate-300 leading-relaxed text-[11px]">{proj.description}</p>
                  
                  <div className="mt-2 flex flex-wrap gap-1">
                    {proj.technologies.map((t, idx) => (
                      <span key={idx} className="text-[10px] bg-slate-200/80 dark:bg-slate-800 text-slate-700 dark:text-slate-300 px-1.5 py-0.5 rounded">
                        {t}
                      </span>
                    ))}
                  </div>

                  {proj.link && (
                    <div className="mt-2">
                      <a
                        href={proj.link}
                        target="_blank"
                        rel="noreferrer"
                        className="text-[11px] text-cyan-600 dark:text-cyan-400 hover:underline flex items-center space-x-1"
                      >
                        <span>View Repository</span>
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
        </div>

        {/* Certifications & Internships */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800">
            <h2 className="text-sm font-bold text-slate-900 dark:text-slate-100 flex items-center space-x-2">
              <Award className="w-4 h-4 text-cyan-600 dark:text-cyan-400" />
              <span>Certifications &amp; Credentials</span>
            </h2>

            {canEditProfile && (
              <button
                onClick={() => setIsCertModalOpen(true)}
                className="text-xs font-semibold text-cyan-600 dark:text-cyan-400 hover:text-cyan-700 flex items-center space-x-1 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Certificate</span>
              </button>
            )}
          </div>

          <div className="mt-3 space-y-3">
            {currentStudent.certifications.length === 0 ? (
              <div className="py-6 text-center text-xs text-slate-400">
                No external certifications (Oracle, AWS, Coursera) recorded.
              </div>
            ) : (
              currentStudent.certifications.map((cert) => (
                <div key={cert.id} className="p-3 rounded-lg border border-slate-100 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-950/60 text-xs">
                  <div className="font-bold text-slate-900 dark:text-slate-100">{cert.name}</div>
                  <div className="text-[11px] text-slate-600 dark:text-slate-400 mt-0.5">
                    Issued by: <strong className="text-slate-800 dark:text-slate-200">{cert.issuingOrg}</strong> · {cert.issueDate}
                  </div>
                  {cert.credentialId && (
                    <div className="text-[10px] text-slate-500 dark:text-slate-400 font-mono mt-0.5">
                      Credential ID: {cert.credentialId}
                    </div>
                  )}
                  {cert.credentialUrl && (
                    <a
                      href={cert.credentialUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="mt-1 inline-flex items-center gap-1 text-[11px] text-cyan-600 dark:text-cyan-400 hover:underline"
                    >
                      <span>Verify Credential</span>
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  )}
                </div>
              ))
            )}
          </div>

          {/* Internships section */}
          <div className="mt-5 pt-4 border-t border-slate-100 dark:border-slate-800">
            <h3 className="text-xs font-bold text-slate-800 dark:text-slate-200 mb-2">Industrial Internships</h3>
            {currentStudent.internships.length === 0 ? (
              <p className="text-[11px] text-slate-400">No industry internships on file.</p>
            ) : (
              <div className="space-y-2">
                {currentStudent.internships.map((intern) => (
                  <div key={intern.id} className="p-2.5 rounded-lg border border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-950/60 text-xs">
                    <div className="font-semibold text-slate-900 dark:text-slate-100">{intern.companyName}</div>
                    <div className="text-[11px] text-cyan-700 dark:text-cyan-400">
                      {intern.role} · {intern.duration}
                      {intern.location ? ` · ${intern.location}` : ''}
                    </div>
                    <p className="text-[11px] text-slate-600 dark:text-slate-300 mt-1">{intern.description}</p>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Achievements, Workshops & Hackathons section */}
          {currentStudent.achievements && currentStudent.achievements.length > 0 && (
            <div className="mt-5 pt-4 border-t border-slate-100 dark:border-slate-800">
              <h3 className="text-xs font-bold text-slate-800 dark:text-slate-200 mb-2 flex items-center gap-1.5">
                <Trophy className="w-3.5 h-3.5 text-amber-500" />
                <span>Achievements, Workshops &amp; Hackathons</span>
              </h3>
              <div className="space-y-2">
                {currentStudent.achievements.map((ach) => (
                  <div key={ach.id} className="p-2.5 rounded-lg border border-slate-100 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-950/60 text-xs">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-slate-900 dark:text-slate-100">{ach.title}</span>
                      <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-amber-50 dark:bg-amber-950/50 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
                        {ach.category}
                      </span>
                    </div>
                    {(ach.organizer || ach.eventDate) && (
                      <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                        {[ach.organizer, ach.eventDate].filter(Boolean).join(' · ')}
                      </div>
                    )}
                    {ach.description && (
                      <p className="text-[11px] text-slate-600 dark:text-slate-300 mt-1">{ach.description}</p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

      </div>

      {/* Add Skill Modal */}
      {isSkillModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-md w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">Add or Update Skill Proficiency</h3>
              <button onClick={() => setIsSkillModalOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddSkill} className="mt-4 space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Select Skill *</label>
                <select
                  value={selectedSkillId}
                  onChange={(e) => setSelectedSkillId(e.target.value)}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                >
                  {skills.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.category})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Proficiency Level: {skillProficiency} - {PROFICIENCY_LABELS[skillProficiency]}
                </label>
                <input
                  type="range"
                  min="1"
                  max="5"
                  step="1"
                  value={skillProficiency}
                  onChange={(e) => setSkillProficiency(parseInt(e.target.value) as SkillProficiency)}
                  className="w-full accent-cyan-600 cursor-pointer"
                />
                <div className="flex justify-between text-[10px] text-slate-500 dark:text-slate-400 mt-1">
                  <span>1: Beginner</span>
                  <span>3: Intermediate</span>
                  <span>5: Expert</span>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsSkillModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold cursor-pointer shadow-xs"
                >
                  Save Skill
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Project Modal */}
      {isProjectModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-md w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">Add Technical Project</h3>
              <button onClick={() => setIsProjectModalOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddProject} className="mt-4 space-y-3 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Project Title *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Distributed Key-Value Store"
                  value={projectForm.title}
                  onChange={(e) => setProjectForm({ ...projectForm, title: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Description *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Describe architecture, algorithm, problem solved..."
                  value={projectForm.description}
                  onChange={(e) => setProjectForm({ ...projectForm, description: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Technologies Used (Comma-separated) *</label>
                <input
                  type="text"
                  required
                  placeholder="Java, Spring Boot, MySQL, Docker"
                  value={projectForm.technologies}
                  onChange={(e) => setProjectForm({ ...projectForm, technologies: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Repository URL</label>
                  <input
                    type="url"
                    placeholder="https://github.com/..."
                    value={projectForm.link}
                    onChange={(e) => setProjectForm({ ...projectForm, link: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Duration</label>
                  <input
                    type="text"
                    placeholder="e.g. 3 months"
                    value={projectForm.duration}
                    onChange={(e) => setProjectForm({ ...projectForm, duration: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsProjectModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold cursor-pointer shadow-xs"
                >
                  Save Project
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Certification Modal */}
      {isCertModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-md w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">Add Certification</h3>
              <button onClick={() => setIsCertModalOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddCert} className="mt-4 space-y-3 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Certificate Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. AWS Certified Cloud Practitioner"
                  value={certForm.name}
                  onChange={(e) => setCertForm({ ...certForm, name: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Issuing Organization *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Amazon Web Services, Oracle, Google"
                  value={certForm.issuingOrg}
                  onChange={(e) => setCertForm({ ...certForm, issuingOrg: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Issue Date</label>
                  <input
                    type="date"
                    value={certForm.issueDate}
                    onChange={(e) => setCertForm({ ...certForm, issueDate: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Credential ID</label>
                  <input
                    type="text"
                    placeholder="e.g. AWS-CCP-1092"
                    value={certForm.credentialId}
                    onChange={(e) => setCertForm({ ...certForm, credentialId: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500 font-mono"
                  />
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsCertModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white font-semibold cursor-pointer shadow-xs"
                >
                  Save Certificate
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};
