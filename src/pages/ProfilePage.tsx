import React, { useState, useEffect, useRef } from 'react';
import {
  User as UserIcon,
  Mail,
  Phone,
  MapPin,
  Briefcase,
  GraduationCap,
  Code2,
  FolderGit2,
  Award,
  Link2,
  Globe,
  ExternalLink,
  Edit3,
  Save,
  X,
  Plus,
  Trash2,
  Upload,
  Lock,
  CheckCircle2,
  BookOpen,
  Building2,
  ShieldCheck,
  AlertCircle,
  Loader2,
  Calendar,
  Sparkles,
} from 'lucide-react';
import {
  User,
  Student,
  Skill,
  UnifiedUserProfile,
  SocialLinks,
  CustomLink,
  StudentProject,
  Internship,
  Certification,
  AchievementItem,
  PublicationItem,
  PROFICIENCY_LABELS,
  SkillProficiency,
} from '../types';
import { api, getStoredToken } from '../services/api';
import { db, computeLocalProfileCompletion } from '../services/db';
import { authService } from '../services/auth';

interface ProfilePageProps {
  currentUser: User;
  skills: Skill[];
  onUserUpdated: (updatedUser: User) => void;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

type ProfileTab =
  | 'personal'
  | 'academic'
  | 'skills'
  | 'projects'
  | 'experience'
  | 'certifications'
  | 'social'
  | 'expertise'
  | 'publications'
  | 'company';

const SOCIAL_PLATFORMS: { key: keyof SocialLinks; label: string; placeholder: string }[] = [
  { key: 'linkedin', label: 'LinkedIn Profile', placeholder: 'https://www.linkedin.com/in/username' },
  { key: 'github', label: 'GitHub Profile', placeholder: 'https://github.com/username' },
  { key: 'portfolio', label: 'Personal Portfolio Website', placeholder: 'https://yourportfolio.dev' },
  { key: 'leetcode', label: 'LeetCode Profile', placeholder: 'https://leetcode.com/u/username' },
  { key: 'hackerrank', label: 'HackerRank Profile', placeholder: 'https://www.hackerrank.com/profile/username' },
  { key: 'codechef', label: 'CodeChef Profile', placeholder: 'https://www.codechef.com/users/username' },
  { key: 'geeksforgeeks', label: 'GeeksforGeeks Profile', placeholder: 'https://www.geeksforgeeks.org/user/username' },
  { key: 'kaggle', label: 'Kaggle Profile', placeholder: 'https://www.kaggle.com/username' },
  { key: 'googleDeveloper', label: 'Google Developer Profile', placeholder: 'https://g.dev/username' },
];

function isValidHttpUrl(val: string): boolean {
  if (!val.trim()) return true;
  try {
    const parsed = new URL(val.trim());
    return (parsed.protocol === 'http:' || parsed.protocol === 'https:') && (parsed.hostname.includes('.') || parsed.hostname === 'localhost');
  } catch {
    return false;
  }
}

function getInitials(name: string): string {
  const parts = (name || 'U').trim().split(/\s+/);
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  return parts[0].slice(0, 2).toUpperCase();
}

async function processImageFile(file: File): Promise<string> {
  const allowed = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp'];
  if (!allowed.includes(file.type.toLowerCase())) {
    throw new Error('Only PNG, JPEG, or WEBP image files are allowed.');
  }
  if (file.size > 2 * 1024 * 1024) {
    throw new Error('Image size must be 2 MB or smaller.');
  }

  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Failed to read selected image file.'));
    reader.onload = () => {
      const dataUrl = reader.result as string;
      // Resize large images down cleanly to max 480px so DB & cache stay crisp and fast
      const img = new Image();
      img.onerror = () => resolve(dataUrl);
      img.onload = () => {
        const maxDim = 480;
        let { width, height } = img;
        if (width <= maxDim && height <= maxDim && file.size <= 350 * 1024) {
          resolve(dataUrl);
          return;
        }
        if (width > height && width > maxDim) {
          height = Math.round((height * maxDim) / width);
          width = maxDim;
        } else if (height >= width && height > maxDim) {
          width = Math.round((width * maxDim) / height);
          height = maxDim;
        }
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(dataUrl);
          return;
        }
        ctx.drawImage(img, 0, 0, width, height);
        const outputMime = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
        resolve(canvas.toDataURL(outputMime, 0.88));
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
  });
}

// Reusable Tag List Input component for Edit Mode + Clean Tags in View Mode
const TagListEditor: React.FC<{
  label: string;
  items: string[];
  isEditing: boolean;
  placeholder: string;
  onChange: (next: string[]) => void;
}> = ({ label, items, isEditing, placeholder, onChange }) => {
  const [inputVal, setInputVal] = useState('');

  const handleAdd = () => {
    const parts = inputVal
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s && !items.some((existing) => existing.toLowerCase() === s.toLowerCase()));
    if (parts.length > 0) {
      onChange([...items, ...parts]);
    }
    setInputVal('');
  };

  return (
    <div className="space-y-2">
      <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">{label}</label>
      {isEditing && (
        <div className="flex gap-2">
          <input
            type="text"
            value={inputVal}
            onChange={(e) => setInputVal(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleAdd();
              }
            }}
            placeholder={placeholder}
            className="flex-1 px-3 py-1.5 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-[#2684FF]"
          />
          <button
            type="button"
            onClick={handleAdd}
            className="px-3 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold whitespace-nowrap shrink-0 cursor-pointer flex items-center gap-1"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add</span>
          </button>
        </div>
      )}
      {items.length === 0 ? (
        <p className="text-xs text-slate-400 dark:text-slate-500 italic">Not specified yet</p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {items.map((item, idx) => (
            <span
              key={`${item}-${idx}`}
              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-200 border border-slate-200 dark:border-slate-700"
            >
              <span>{item}</span>
              {isEditing && (
                <button
                  type="button"
                  onClick={() => onChange(items.filter((_, i) => i !== idx))}
                  className="text-slate-400 hover:text-rose-500 cursor-pointer"
                  aria-label={`Remove ${item}`}
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </span>
          ))}
        </div>
      )}
    </div>
  );
};

export const ProfilePage: React.FC<ProfilePageProps> = ({
  currentUser,
  skills,
  onUserUpdated,
  onShowToast,
}) => {
  const [profile, setProfile] = useState<UnifiedUserProfile>(() => db.getUnifiedProfile(currentUser));
  const [draft, setDraft] = useState<UnifiedUserProfile>(() => db.getUnifiedProfile(currentUser));
  const [activeTab, setActiveTab] = useState<ProfileTab>('personal');
  const [isEditing, setIsEditing] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [dataSource, setDataSource] = useState<'MYSQL' | 'LOCAL'>('LOCAL');

  const avatarInputRef = useRef<HTMLInputElement>(null);
  const logoInputRef = useRef<HTMLInputElement>(null);

  // Load authoritative profile from MySQL on mount or user switch
  useEffect(() => {
    let mounted = true;
    const loadProfile = async () => {
      setIsLoading(true);
      setIsEditing(false);
      setErrorMessage(null);
      setActiveTab('personal');

      const localFallback = db.getUnifiedProfile(currentUser);
      setProfile(localFallback);
      setDraft(JSON.parse(JSON.stringify(localFallback)));

      if (getStoredToken()) {
        try {
          const remote = await api.getMyProfile();
          if (mounted && remote && remote.user) {
            const unified: UnifiedUserProfile = {
              user: { ...currentUser, ...remote.user },
              socialLinks: remote.socialLinks || {},
              customLinks: remote.customLinks || [],
              student: remote.student || null,
              faculty: remote.faculty || null,
              company: remote.company || null,
              completionPercentage: remote.completionPercentage ?? 0,
              missingSections: remote.missingSections || [],
              isOwnProfile: true,
            };
            db.saveUnifiedProfile(currentUser, unified);
            setProfile(unified);
            setDraft(JSON.parse(JSON.stringify(unified)));
            setDataSource('MYSQL');
          }
        } catch (err: any) {
          if (mounted) {
            setDataSource('LOCAL');
            if (err.status !== 503 && err.status !== 401) {
              console.warn('[ProfilePage] Using local profile cache:', err.message);
            }
          }
        }
      }
      if (mounted) setIsLoading(false);
    };

    loadProfile();
    return () => {
      mounted = false;
    };
  }, [currentUser.id, currentUser.username]);

  const role = profile.user.role;

  // Define role-appropriate tabs
  const tabs: { id: ProfileTab; label: string; icon: React.ElementType }[] = (() => {
    if (role === 'STUDENT') {
      return [
        { id: 'personal', label: 'Personal', icon: UserIcon },
        { id: 'academic', label: 'Academic', icon: GraduationCap },
        { id: 'skills', label: 'Skills', icon: Code2 },
        { id: 'projects', label: 'Projects', icon: FolderGit2 },
        { id: 'experience', label: 'Experience', icon: Briefcase },
        { id: 'certifications', label: 'Certifications', icon: Award },
        { id: 'social', label: 'Social Links', icon: Link2 },
      ];
    }
    if (role === 'FACULTY') {
      return [
        { id: 'personal', label: 'Personal & Bio', icon: UserIcon },
        { id: 'expertise', label: 'Expertise & Teaching', icon: BookOpen },
        { id: 'publications', label: 'Publications', icon: Sparkles },
        { id: 'certifications', label: 'Certifications', icon: Award },
        { id: 'social', label: 'Social Links', icon: Link2 },
      ];
    }
    if (role === 'COMPANY') {
      return [
        { id: 'personal', label: 'Recruiter & Contact', icon: UserIcon },
        { id: 'company', label: 'Company Details', icon: Building2 },
        { id: 'social', label: 'Social & Web Links', icon: Link2 },
      ];
    }
    return [
      { id: 'personal', label: 'Personal & Role', icon: ShieldCheck },
      { id: 'social', label: 'Social Links', icon: Link2 },
    ];
  })();

  const activeData = isEditing ? draft : profile;
  const liveCompletion = computeLocalProfileCompletion(activeData);

  const handleStartEdit = () => {
    setDraft(JSON.parse(JSON.stringify(profile)));
    setErrorMessage(null);
    setIsEditing(true);
  };

  const handleCancelEdit = () => {
    setDraft(JSON.parse(JSON.stringify(profile)));
    setErrorMessage(null);
    setIsEditing(false);
  };

  const handlePhotoSelect = async (e: React.ChangeEvent<HTMLInputElement>, target: 'avatar' | 'company_logo') => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    try {
      const dataUrl = await processImageFile(file);
      if (target === 'company_logo') {
        setDraft((prev) => ({
          ...prev,
          company: { ...(prev.company || {}), companyLogo: dataUrl },
        }));
      } else {
        setDraft((prev) => ({
          ...prev,
          user: { ...prev.user, profilePhoto: dataUrl },
        }));
      }

      // If not currently in full edit mode, persist photo immediately
      if (!isEditing) {
        if (getStoredToken()) {
          try {
            await api.uploadProfilePhoto(dataUrl, target);
          } catch (err: any) {
            if (err.status !== 503) throw err;
          }
        }
        const updatedProfile = {
          ...profile,
          user: target === 'avatar' ? { ...profile.user, profilePhoto: dataUrl } : profile.user,
          company: target === 'company_logo' ? { ...(profile.company || {}), companyLogo: dataUrl } : profile.company,
        };
        const savedLocal = db.saveUnifiedProfile(currentUser, updatedProfile);
        setProfile(savedLocal);
        setDraft(JSON.parse(JSON.stringify(savedLocal)));
        authService.setCurrentUser(savedLocal.user);
        onUserUpdated(savedLocal.user);
        onShowToast('success', target === 'company_logo' ? 'Company Logo Updated' : 'Profile Photo Updated', 'Your image has been saved securely.');
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Unable to process image.');
      onShowToast('error', 'Image Upload Failed', err.message);
    }
  };

  const handleRemovePhoto = async (target: 'avatar' | 'company_logo') => {
    if (isEditing) {
      if (target === 'company_logo') {
        setDraft((prev) => ({ ...prev, company: { ...(prev.company || {}), companyLogo: '' } }));
      } else {
        setDraft((prev) => ({ ...prev, user: { ...prev.user, profilePhoto: '' } }));
      }
      return;
    }

    try {
      if (getStoredToken()) {
        try {
          await api.removeProfilePhoto(target);
        } catch (err: any) {
          if (err.status !== 503) throw err;
        }
      }
      const updated = {
        ...profile,
        user: target === 'avatar' ? { ...profile.user, profilePhoto: '' } : profile.user,
        company: target === 'company_logo' ? { ...(profile.company || {}), companyLogo: '' } : profile.company,
      };
      const savedLocal = db.saveUnifiedProfile(currentUser, updated);
      setProfile(savedLocal);
      setDraft(JSON.parse(JSON.stringify(savedLocal)));
      authService.setCurrentUser(savedLocal.user);
      onUserUpdated(savedLocal.user);
      onShowToast('info', 'Photo Removed');
    } catch (err: any) {
      onShowToast('error', 'Remove Failed', err.message);
    }
  };

  const validateDraft = (d: UnifiedUserProfile): string | null => {
    if (!d.user.fullName || !d.user.fullName.trim()) return 'Full Name is required.';
    if (!d.user.email || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(d.user.email.trim())) {
      return 'Please enter a valid email address.';
    }
    if (d.user.phone && !/^[+\d\s\-().]{7,20}$/.test(d.user.phone.trim())) {
      return 'Phone number must be 7–20 characters and contain only valid digits/symbols.';
    }

    for (const plat of SOCIAL_PLATFORMS) {
      const val = d.socialLinks?.[plat.key] || '';
      if (val.trim() && !isValidHttpUrl(val)) {
        return `${plat.label} must be a valid URL starting with https:// or http://.`;
      }
    }

    for (const cl of d.customLinks || []) {
      if (!cl.title.trim() && !cl.url.trim()) continue;
      if (!cl.title.trim()) return 'Each custom link must include a title.';
      if (!cl.url.trim() || !isValidHttpUrl(cl.url)) {
        return `Custom link "${cl.title}" must have a valid https:// or http:// URL.`;
      }
    }

    if (role === 'STUDENT' && d.student) {
      if (d.student.cgpa < 0 || d.student.cgpa > 10 || isNaN(d.student.cgpa)) {
        return 'CGPA must be between 0.00 and 10.00.';
      }
      if (d.student.backlogs < 0 || isNaN(d.student.backlogs)) {
        return 'Backlogs cannot be negative.';
      }
      if (d.student.resumeUrl && !isValidHttpUrl(d.student.resumeUrl)) {
        return 'Resume link must be a valid https:// or http:// URL.';
      }
      for (const p of d.student.projects || []) {
        if (!p.title.trim()) return 'Every project entry must include a project title.';
        if (p.link && !isValidHttpUrl(p.link)) return `Project "${p.title}" URL must start with https:// or http://.`;
      }
      for (const c of d.student.certifications || []) {
        if (!c.name.trim() || !c.issuingOrg.trim()) return 'Every certification requires a name and issuing organization.';
        if (c.credentialUrl && !isValidHttpUrl(c.credentialUrl)) return `Certification "${c.name}" URL must start with https:// or http://.`;
      }
      for (const a of d.student.achievements || []) {
        if (!a.title.trim()) return 'Every achievement/workshop entry must include a title.';
        if (a.credentialUrl && !isValidHttpUrl(a.credentialUrl)) return `Achievement "${a.title}" URL must start with https:// or http://.`;
      }
    }

    if (role === 'FACULTY' && d.faculty) {
      for (const pub of d.faculty.publications || []) {
        if (!pub.title.trim()) return 'Every publication must include a title.';
        if (pub.url && !isValidHttpUrl(pub.url)) return `Publication "${pub.title}" DOI/URL must start with https:// or http://.`;
      }
      for (const c of d.faculty.certifications || []) {
        if (!c.name.trim() || !c.issuingOrg.trim()) return 'Every certification requires a name and issuing organization.';
        if (c.credentialUrl && !isValidHttpUrl(c.credentialUrl)) return `Certification "${c.name}" URL must start with https:// or http://.`;
      }
    }

    if (role === 'COMPANY' && d.company) {
      if (!d.company.name?.trim()) return 'Company Name is required.';
      if (d.company.website && !isValidHttpUrl(d.company.website)) return 'Company Website must start with https:// or http://.';
      if (d.company.linkedinUrl && !isValidHttpUrl(d.company.linkedinUrl)) return 'Company LinkedIn URL must start with https:// or http://.';
    }

    return null;
  };

  const handleSaveChanges = async () => {
    const validationErr = validateDraft(draft);
    if (validationErr) {
      setErrorMessage(validationErr);
      onShowToast('error', 'Validation Error', validationErr);
      return;
    }

    setIsSaving(true);
    setErrorMessage(null);

    try {
      const cleanedCustomLinks = (draft.customLinks || []).filter((l) => l.title.trim() && l.url.trim());
      const payload = {
        fullName: draft.user.fullName.trim(),
        email: draft.user.email.trim().toLowerCase(),
        phone: draft.user.phone?.trim() || '',
        designation: draft.user.designation?.trim() || '',
        location: draft.user.location?.trim() || '',
        address: draft.user.address?.trim() || '',
        aboutMe: draft.user.aboutMe?.trim() || '',
        profilePhoto: draft.user.profilePhoto || '',
        dob: draft.student?.dob || '',
        gender: draft.student?.gender || '',
        socialLinks: draft.socialLinks || {},
        customLinks: cleanedCustomLinks,
        student: role === 'STUDENT' ? draft.student : undefined,
        faculty: role === 'FACULTY' ? draft.faculty : undefined,
        company: role === 'COMPANY' ? draft.company : undefined,
      };

      let finalProfile: UnifiedUserProfile = {
        ...draft,
        customLinks: cleanedCustomLinks,
      };

      let savedToMysql = false;
      if (getStoredToken()) {
        try {
          const res = await api.updateMyProfile(payload);
          if (res?.profile) {
            finalProfile = {
              ...res.profile,
              user: { ...currentUser, ...res.profile.user },
            };
            savedToMysql = true;
            setDataSource('MYSQL');
          }
        } catch (err: any) {
          // Only fall back to local store if MySQL backend is standby (503) or unreachable; surface real 400/403/409 validation errors!
          if (err.status && err.status !== 503) {
            throw err;
          }
          setDataSource('LOCAL');
        }
      }

      const persisted = db.saveUnifiedProfile(currentUser, finalProfile);
      setProfile(persisted);
      setDraft(JSON.parse(JSON.stringify(persisted)));
      authService.setCurrentUser(persisted.user);
      onUserUpdated(persisted.user);
      setIsEditing(false);

      onShowToast(
        'success',
        'Profile Saved',
        savedToMysql
          ? 'Your profile changes have been permanently saved to MySQL.'
          : 'Your profile changes have been saved locally (MySQL standby).'
      );
    } catch (err: any) {
      const msg = err.message || 'Failed to save profile changes.';
      setErrorMessage(msg);
      onShowToast('error', 'Save Failed', msg);
    } finally {
      setIsSaving(false);
    }
  };

  // Helper to update draft user field
  const updateDraftUser = (patch: Partial<User>) => {
    setDraft((prev) => ({ ...prev, user: { ...prev.user, ...patch } }));
  };

  const updateDraftStudent = (patch: Partial<Student>) => {
    setDraft((prev) => ({
      ...prev,
      student: prev.student ? { ...prev.student, ...patch } : prev.student,
    }));
  };

  // Non-empty social links for Read-Only display
  const activeSocialLinks = SOCIAL_PLATFORMS.filter(
    (p) => Boolean(activeData.socialLinks?.[p.key] && String(activeData.socialLinks[p.key]).trim())
  );
  const activeCustomLinks = (activeData.customLinks || []).filter((l) => l.title.trim() && l.url.trim());

  const inputCls =
    'w-full px-3 py-2 text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-[#2684FF]';
  const readOnlyCls =
    'w-full px-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-100 dark:bg-slate-900/70 text-slate-500 dark:text-slate-400 cursor-not-allowed';

  if (isLoading && !profile.user.fullName) {
    return (
      <div className="flex items-center justify-center h-72 text-slate-500 dark:text-slate-400 text-sm gap-2">
        <Loader2 className="w-4 h-4 animate-spin text-[#2684FF]" />
        <span>Loading profile from gcet_placement_hub...</span>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Hidden file inputs for photo & logo uploads */}
      <input
        ref={avatarInputRef}
        type="file"
        accept="image/png,image/jpeg,image/webp"
        className="hidden"
        onChange={(e) => handlePhotoSelect(e, 'avatar')}
      />
      <input
        ref={logoInputRef}
        type="file"
        accept="image/png,image/jpeg,image/webp"
        className="hidden"
        onChange={(e) => handlePhotoSelect(e, 'company_logo')}
      />

      {/* Profile Overview Card */}
      <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs">
        <div className="h-24 bg-[#101A35] border-b border-[#172B50] px-6 flex items-center justify-between">
          <div className="text-xs text-slate-300">
            <span className="font-semibold text-[#32C5F4]">GCET Placement Hub</span>
            <span className="mx-2 text-slate-500">·</span>
            <span>{dataSource === 'MYSQL' ? 'MySQL Synced Profile' : 'Profile Management'}</span>
          </div>
          <div className="text-[11px] font-mono text-slate-300">
            @{activeData.user.username}
          </div>
        </div>

        <div className="px-6 pb-6">
          <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4 -mt-10">
            {/* Avatar & Identity */}
            <div className="flex flex-col sm:flex-row sm:items-end gap-4">
              <div className="relative shrink-0">
                <div className="w-24 h-24 rounded-2xl border-4 border-white dark:border-slate-900 bg-[#172B50] text-white flex items-center justify-center overflow-hidden shadow-md">
                  {activeData.user.profilePhoto ? (
                    <img
                      src={activeData.user.profilePhoto}
                      alt={activeData.user.fullName}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <span className="text-2xl font-bold tracking-tight text-[#32C5F4]">
                      {getInitials(activeData.user.fullName)}
                    </span>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => avatarInputRef.current?.click()}
                  className="absolute -bottom-1.5 -right-1.5 p-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white shadow-md cursor-pointer border-2 border-white dark:border-slate-900"
                  title="Upload or change profile photo (PNG, JPG, WEBP up to 2MB)"
                >
                  <Upload className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="pt-2 sm:pt-0 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h1 className="text-xl font-bold text-slate-900 dark:text-white tracking-tight">
                    {activeData.user.fullName}
                  </h1>
                  <span className="text-[11px] font-semibold px-2 py-0.5 rounded bg-[#2684FF]/10 text-[#2684FF] dark:text-[#32C5F4]">
                    {activeData.user.role.replace('_', ' ')}
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-600 dark:text-slate-400">
                  {activeData.user.designation && <span>{activeData.user.designation}</span>}
                  {activeData.user.department && (
                    <>
                      {activeData.user.designation && <span className="text-slate-300 dark:text-slate-600">·</span>}
                      <span>{activeData.user.department}</span>
                    </>
                  )}
                  {activeData.student?.rollNumber && (
                    <>
                      <span className="text-slate-300 dark:text-slate-600">·</span>
                      <span className="font-mono font-semibold text-slate-700 dark:text-slate-300">
                        {activeData.student.rollNumber}
                      </span>
                    </>
                  )}
                </div>
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex flex-wrap items-center gap-2 shrink-0">
              {activeData.user.profilePhoto && (
                <button
                  type="button"
                  onClick={() => handleRemovePhoto('avatar')}
                  className="px-3 py-2 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-600 dark:text-slate-300 text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer"
                >
                  Remove Photo
                </button>
              )}

              {!isEditing ? (
                <button
                  type="button"
                  onClick={handleStartEdit}
                  className="px-4 py-2 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 cursor-pointer shadow-xs"
                >
                  <Edit3 className="w-3.5 h-3.5" />
                  <span>Edit Profile</span>
                </button>
              ) : (
                <>
                  <button
                    type="button"
                    onClick={handleCancelEdit}
                    disabled={isSaving}
                    className="px-3.5 py-2 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-200 text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    <X className="w-3.5 h-3.5" />
                    <span>Cancel</span>
                  </button>
                  <button
                    type="button"
                    onClick={handleSaveChanges}
                    disabled={isSaving}
                    className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 cursor-pointer shadow-xs disabled:opacity-60"
                  >
                    {isSaving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
                    <span>{isSaving ? 'Saving...' : 'Save Changes'}</span>
                  </button>
                </>
              )}
            </div>
          </div>

          {/* Contact Strip, Short Bio & Completion Bar */}
          <div className="mt-5 pt-4 border-t border-slate-100 dark:border-slate-800 grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
            <div className="lg:col-span-8 space-y-3">
              <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-slate-600 dark:text-slate-300">
                <span className="inline-flex items-center gap-1.5">
                  <Mail className="w-3.5 h-3.5 text-slate-400" />
                  <span>{activeData.user.email}</span>
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <Phone className="w-3.5 h-3.5 text-slate-400" />
                  <span>{activeData.user.phone || 'Phone not added'}</span>
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 text-slate-400" />
                  <span>{activeData.user.location || 'Location not added'}</span>
                </span>
              </div>

              <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-300">
                {activeData.user.aboutMe?.trim()
                  ? activeData.user.aboutMe
                  : 'No professional introduction added yet. Click Edit Profile to write a short bio.'}
              </p>

              {/* Quick Non-Empty Social Buttons on Overview */}
              {(activeSocialLinks.length > 0 || activeCustomLinks.length > 0) && (
                <div className="flex flex-wrap items-center gap-2 pt-1">
                  {activeSocialLinks.map((plat) => (
                    <a
                      key={plat.key}
                      href={activeData.socialLinks[plat.key]}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/80 hover:border-[#2684FF] text-xs font-medium text-slate-700 dark:text-slate-200 hover:text-[#2684FF] dark:hover:text-[#32C5F4] transition"
                    >
                      <Globe className="w-3 h-3" />
                      <span>{plat.label.replace(' Profile', '')}</span>
                      <ExternalLink className="w-2.5 h-2.5 opacity-60" />
                    </a>
                  ))}
                  {activeCustomLinks.map((cl) => (
                    <a
                      key={cl.id}
                      href={cl.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/80 hover:border-[#2684FF] text-xs font-medium text-slate-700 dark:text-slate-200 hover:text-[#2684FF] dark:hover:text-[#32C5F4] transition"
                    >
                      <Link2 className="w-3 h-3" />
                      <span>{cl.title}</span>
                      <ExternalLink className="w-2.5 h-2.5 opacity-60" />
                    </a>
                  ))}
                </div>
              )}
            </div>

            {/* Profile Completion Meter */}
            <div className="lg:col-span-4 p-4 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-slate-700 dark:text-slate-200">Profile Completion</span>
                <span className="font-bold tabular-nums text-[#2684FF] dark:text-[#32C5F4]">
                  {liveCompletion.percentage}%
                </span>
              </div>
              <div className="h-2 rounded-full bg-slate-200 dark:bg-slate-800 overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-300 ${
                    liveCompletion.percentage >= 85
                      ? 'bg-emerald-500'
                      : liveCompletion.percentage >= 50
                      ? 'bg-[#2684FF]'
                      : 'bg-amber-500'
                  }`}
                  style={{ width: `${liveCompletion.percentage}%` }}
                />
              </div>
              {liveCompletion.missingSections.length > 0 ? (
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  Next to complete: {liveCompletion.missingSections.slice(0, 2).join(', ')}
                </p>
              ) : (
                <p className="text-[11px] text-emerald-600 dark:text-emerald-400 flex items-center gap-1 font-medium">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Profile 100% complete</span>
                </p>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Inline Error Banner */}
      {errorMessage && (
        <div className="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
          <button
            type="button"
            onClick={() => setErrorMessage(null)}
            className="text-rose-500 hover:text-rose-700 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Segmented Navigation Tabs */}
      <div className="flex items-center gap-1.5 overflow-x-auto border-b border-slate-200 dark:border-slate-800 pb-2">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const active = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 transition cursor-pointer ${
                active
                  ? 'bg-[#2684FF] text-white shadow-xs'
                  : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* TAB 1: PERSONAL INFORMATION (All Roles) */}
      {activeTab === 'personal' && (
        <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-5">
          <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
            <div>
              <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                {role === 'COMPANY' ? 'Recruiter & Account Contact Details' : 'Personal & Contact Information'}
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Core contact information and professional introduction linked to your GCET account.
              </p>
            </div>
            {isEditing && (
              <span className="text-[11px] font-medium text-[#2684FF] dark:text-[#32C5F4]">Editing Mode</span>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                {role === 'COMPANY' ? 'Recruiter Full Name *' : 'Full Name *'}
              </label>
              {isEditing ? (
                <input
                  type="text"
                  value={draft.user.fullName}
                  onChange={(e) => {
                    updateDraftUser({ fullName: e.target.value });
                    if (role === 'COMPANY' && draft.company) {
                      setDraft((prev) => ({
                        ...prev,
                        company: { ...(prev.company || {}), recruiterName: e.target.value },
                      }));
                    }
                  }}
                  className={inputCls}
                />
              ) : (
                <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                  {activeData.user.fullName}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center gap-1">
                <span>Username</span>
                <Lock className="w-3 h-3 text-slate-400" />
              </label>
              <input type="text" value={activeData.user.username} disabled className={readOnlyCls} />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                {role === 'FACULTY' || role === 'COMPANY' ? 'Official Email Address *' : 'Email Address *'}
              </label>
              {isEditing ? (
                <input
                  type="email"
                  value={draft.user.email}
                  onChange={(e) => updateDraftUser({ email: e.target.value })}
                  className={inputCls}
                />
              ) : (
                <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                  {activeData.user.email}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Phone Number
              </label>
              {isEditing ? (
                <input
                  type="text"
                  placeholder="+91 98480 12345"
                  value={draft.user.phone || ''}
                  onChange={(e) => updateDraftUser({ phone: e.target.value })}
                  className={inputCls}
                />
              ) : (
                <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                  {activeData.user.phone || 'Not added'}
                </div>
              )}
            </div>

            {role !== 'STUDENT' && (
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Designation
                </label>
                {isEditing ? (
                  <input
                    type="text"
                    placeholder="e.g. Associate Professor / Placement Coordinator"
                    value={draft.user.designation || ''}
                    onChange={(e) => {
                      updateDraftUser({ designation: e.target.value });
                      if (role === 'COMPANY') {
                        setDraft((prev) => ({
                          ...prev,
                          company: { ...(prev.company || {}), recruiterDesignation: e.target.value },
                        }));
                      }
                      if (role === 'FACULTY' && draft.faculty) {
                        setDraft((prev) => ({
                          ...prev,
                          faculty: prev.faculty ? { ...prev.faculty, designation: e.target.value } : prev.faculty,
                        }));
                      }
                    }}
                    className={inputCls}
                  />
                ) : (
                  <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                    {activeData.user.designation || 'Not specified'}
                  </div>
                )}
              </div>
            )}

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Location (City, State)
              </label>
              {isEditing ? (
                <input
                  type="text"
                  placeholder="Hyderabad, Telangana"
                  value={draft.user.location || ''}
                  onChange={(e) => updateDraftUser({ location: e.target.value })}
                  className={inputCls}
                />
              ) : (
                <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                  {activeData.user.location || 'Not specified'}
                </div>
              )}
            </div>

            {/* Verified Role & Department (Read-Only Institutional) */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center gap-1">
                <span>Role &amp; Department (Verified)</span>
                <Lock className="w-3 h-3 text-slate-400" />
              </label>
              <input
                type="text"
                value={`${activeData.user.role} · ${activeData.user.department || 'GCET'}`}
                disabled
                className={readOnlyCls}
              />
            </div>

            {role === 'FACULTY' && activeData.faculty && (
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center gap-1">
                  <span>Faculty / Employee ID (Institutional)</span>
                  <Lock className="w-3 h-3 text-slate-400" />
                </label>
                <input
                  type="text"
                  value={`${activeData.faculty.facultyId}${activeData.faculty.employeeId ? ` (${activeData.faculty.employeeId})` : ''}`}
                  disabled
                  className={readOnlyCls}
                />
              </div>
            )}

            {role === 'STUDENT' && activeData.student && (
              <>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Date of Birth
                  </label>
                  {isEditing ? (
                    <input
                      type="date"
                      value={draft.student?.dob || ''}
                      onChange={(e) => updateDraftStudent({ dob: e.target.value })}
                      className={inputCls}
                    />
                  ) : (
                    <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                      {activeData.student.dob || 'Not specified'}
                    </div>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Gender (Optional)
                  </label>
                  {isEditing ? (
                    <select
                      value={draft.student?.gender || ''}
                      onChange={(e) => updateDraftStudent({ gender: e.target.value })}
                      className={inputCls}
                    >
                      <option value="">Prefer not to say</option>
                      <option value="Male">Male</option>
                      <option value="Female">Female</option>
                      <option value="Other">Other</option>
                    </select>
                  ) : (
                    <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                      {activeData.student.gender || 'Not specified'}
                    </div>
                  )}
                </div>

                <div className="md:col-span-2">
                  <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                    Residential Address
                  </label>
                  {isEditing ? (
                    <input
                      type="text"
                      placeholder="Street, Area, City, PIN"
                      value={draft.user.address || draft.student?.address || ''}
                      onChange={(e) => {
                        updateDraftUser({ address: e.target.value });
                        updateDraftStudent({ address: e.target.value });
                      }}
                      className={inputCls}
                    />
                  ) : (
                    <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                      {activeData.user.address || activeData.student.address || 'Not specified'}
                    </div>
                  )}
                </div>
              </>
            )}

            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                About Me / Professional Introduction
              </label>
              {isEditing ? (
                <textarea
                  rows={4}
                  placeholder="Write a concise professional summary highlighting your background, strengths, and goals..."
                  value={draft.user.aboutMe || ''}
                  onChange={(e) => updateDraftUser({ aboutMe: e.target.value })}
                  className={inputCls}
                />
              ) : (
                <p className="text-xs text-slate-700 dark:text-slate-300 leading-relaxed py-1">
                  {activeData.user.aboutMe || 'No introduction provided yet.'}
                </p>
              )}
            </div>
          </div>
        </section>
      )}

      {/* TAB 2 (STUDENT): ACADEMIC INFORMATION */}
      {activeTab === 'academic' && role === 'STUDENT' && activeData.student && (
        <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-5">
          <div className="border-b border-slate-100 dark:border-slate-800 pb-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <h2 className="text-sm font-bold text-slate-900 dark:text-white">Academic Profile &amp; Verified Records</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Institutional identifiers (Roll Number, Verified Department, Approval &amp; Placement Status) are locked for integrity.
              </p>
            </div>
            <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 dark:text-emerald-400">
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>{activeData.student.approvalStatus || 'APPROVED'}</span>
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center gap-1">
                <span>Roll Number (Read-Only)</span>
                <Lock className="w-3 h-3 text-slate-400" />
              </label>
              <input type="text" value={activeData.student.rollNumber} disabled className={`${readOnlyCls} font-mono`} />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center gap-1">
                <span>Department (Verified)</span>
                <Lock className="w-3 h-3 text-slate-400" />
              </label>
              <input type="text" value={activeData.student.branch} disabled className={readOnlyCls} />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">Section</label>
              {isEditing ? (
                <select
                  value={draft.student?.section || 'A'}
                  onChange={(e) => updateDraftStudent({ section: e.target.value })}
                  className={inputCls}
                >
                  {['A', 'B', 'C', 'D', 'E'].map((sec) => (
                    <option key={sec} value={sec}>{sec}</option>
                  ))}
                </select>
              ) : (
                <div className="text-xs font-semibold text-slate-900 dark:text-white py-2">
                  Section {activeData.student.section || 'A'}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">Academic Year</label>
              {isEditing ? (
                <select
                  value={draft.student?.academicYear || 4}
                  onChange={(e) => updateDraftStudent({ academicYear: parseInt(e.target.value, 10) })}
                  className={inputCls}
                >
                  {[1, 2, 3, 4].map((yr) => (
                    <option key={yr} value={yr}>Year {yr}</option>
                  ))}
                </select>
              ) : (
                <div className="text-xs font-semibold text-slate-900 dark:text-white py-2">
                  Year {activeData.student.academicYear || 4}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">Current Semester</label>
              {isEditing ? (
                <select
                  value={draft.student?.currentSemester || 7}
                  onChange={(e) => updateDraftStudent({ currentSemester: parseInt(e.target.value, 10) })}
                  className={inputCls}
                >
                  {[1, 2, 3, 4, 5, 6, 7, 8].map((sem) => (
                    <option key={sem} value={sem}>Semester {sem}</option>
                  ))}
                </select>
              ) : (
                <div className="text-xs font-semibold text-slate-900 dark:text-white py-2">
                  Semester {activeData.student.currentSemester || 7}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">Graduation Year</label>
              {isEditing ? (
                <input
                  type="number"
                  min={2022}
                  max={2032}
                  value={draft.student?.graduationYear || 2026}
                  onChange={(e) => updateDraftStudent({ graduationYear: parseInt(e.target.value, 10) || 2026 })}
                  className={`${inputCls} tabular-nums`}
                />
              ) : (
                <div className="text-xs font-semibold text-slate-900 dark:text-white py-2 tabular-nums">
                  {activeData.student.graduationYear}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">CGPA (0.00 - 10.00)</label>
              {isEditing ? (
                <input
                  type="number"
                  step="0.01"
                  min={0}
                  max={10}
                  value={draft.student?.cgpa ?? 0}
                  onChange={(e) => updateDraftStudent({ cgpa: parseFloat(e.target.value) || 0 })}
                  className={`${inputCls} tabular-nums`}
                />
              ) : (
                <div className="text-xs font-bold text-[#2684FF] dark:text-[#32C5F4] py-2 tabular-nums">
                  {Number(activeData.student.cgpa || 0).toFixed(2)} / 10.00
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">Active Backlogs</label>
              {isEditing ? (
                <input
                  type="number"
                  min={0}
                  max={30}
                  value={draft.student?.backlogs ?? 0}
                  onChange={(e) => updateDraftStudent({ backlogs: Math.max(0, parseInt(e.target.value, 10) || 0) })}
                  className={`${inputCls} tabular-nums`}
                />
              ) : (
                <div className={`text-xs font-semibold py-2 tabular-nums ${activeData.student.backlogs > 0 ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-600 dark:text-emerald-400'}`}>
                  {activeData.student.backlogs}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center gap-1">
                <span>Placement Status (Protected)</span>
                <Lock className="w-3 h-3 text-slate-400" />
              </label>
              <input
                type="text"
                value={
                  activeData.student.placedPackageLpa
                    ? `${activeData.student.placementStatus} (${activeData.student.placedPackageLpa} LPA)`
                    : activeData.student.placementStatus
                }
                disabled
                className={readOnlyCls}
              />
            </div>

            <div className="sm:col-span-2 lg:col-span-3">
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Resume URL (Google Drive / Portfolio PDF)
              </label>
              {isEditing ? (
                <input
                  type="url"
                  placeholder="https://drive.google.com/file/d/..."
                  value={draft.student?.resumeUrl || ''}
                  onChange={(e) => updateDraftStudent({ resumeUrl: e.target.value })}
                  className={inputCls}
                />
              ) : activeData.student.resumeUrl ? (
                <a
                  href={activeData.student.resumeUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#2684FF] hover:underline py-1.5"
                >
                  <span>View Resume Document</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              ) : (
                <span className="text-xs text-slate-400 py-1.5 block">No resume link provided</span>
              )}
            </div>
          </div>
        </section>
      )}

      {/* TAB 3 (STUDENT): SKILLS & CAREER PREFERENCES */}
      {activeTab === 'skills' && role === 'STUDENT' && activeData.student && (
        <div className="space-y-5">
          {/* Verified Competency Matrix */}
          <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-4">
            <div className="border-b border-slate-100 dark:border-slate-800 pb-3">
              <h2 className="text-sm font-bold text-slate-900 dark:text-white">Faculty-Verified Skill Matrix</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Proficiency assessments (Levels 1–5) verified by GCET departmental faculty mentors.
              </p>
            </div>
            {activeData.student.skills && activeData.student.skills.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {activeData.student.skills.map((sk: any) => {
                  const catalogSkill = skills.find((c) => c.id === sk.skillId);
                  const skillName = sk.skillName || catalogSkill?.name || sk.skillId;
                  return (
                    <div
                      key={sk.skillId}
                      className="p-3 rounded-lg border border-slate-200 dark:border-slate-800 flex items-center justify-between gap-3"
                    >
                      <div>
                        <div className="text-xs font-bold text-slate-900 dark:text-white">{skillName}</div>
                        <div className="text-[11px] text-slate-500 dark:text-slate-400">
                          Level {sk.proficiency}/5 · {PROFICIENCY_LABELS[sk.proficiency as SkillProficiency] || 'Proficient'}
                        </div>
                      </div>
                      <span
                        className={`text-[11px] font-semibold ${
                          sk.verifiedByFaculty
                            ? 'text-emerald-600 dark:text-emerald-400'
                            : 'text-slate-400 dark:text-slate-500'
                        }`}
                      >
                        {sk.verifiedByFaculty ? 'Faculty Verified' : 'Self-Rated'}
                      </span>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                No verified competencies recorded in Skill Matrix yet.
              </p>
            )}
          </section>

          {/* Categorized Technical & Career Skills */}
          <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-5">
            <h2 className="text-sm font-bold text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-3">
              Skills, Technologies &amp; Career Preferences
            </h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <TagListEditor
                label="Technical Skills"
                items={activeData.student.technicalSkills || []}
                isEditing={isEditing}
                placeholder="e.g. System Design, REST APIs, DBMS (comma or Enter)"
                onChange={(next) => updateDraftStudent({ technicalSkills: next })}
              />
              <TagListEditor
                label="Programming Languages"
                items={activeData.student.programmingLanguages || []}
                isEditing={isEditing}
                placeholder="e.g. Java, Python, TypeScript, C++"
                onChange={(next) => updateDraftStudent({ programmingLanguages: next })}
              />
              <TagListEditor
                label="Frameworks & Technologies"
                items={activeData.student.frameworksTechnologies || []}
                isEditing={isEditing}
                placeholder="e.g. React, Spring Boot, Node.js, Docker, MySQL"
                onChange={(next) => updateDraftStudent({ frameworksTechnologies: next })}
              />
              <TagListEditor
                label="Soft Skills"
                items={activeData.student.softSkills || []}
                isEditing={isEditing}
                placeholder="e.g. Team Leadership, Public Speaking, Problem Solving"
                onChange={(next) => updateDraftStudent({ softSkills: next })}
              />
              <TagListEditor
                label="Career Interests"
                items={activeData.student.careerInterests || []}
                isEditing={isEditing}
                placeholder="e.g. Full-Stack Engineering, Cloud Architecture, Applied AI"
                onChange={(next) => updateDraftStudent({ careerInterests: next })}
              />
              <TagListEditor
                label="Preferred Job Roles"
                items={activeData.student.preferredJobRoles || []}
                isEditing={isEditing}
                placeholder="e.g. Software Development Engineer, Backend Developer"
                onChange={(next) => updateDraftStudent({ preferredJobRoles: next })}
              />
            </div>
          </section>
        </div>
      )}

      {/* TAB 4 (STUDENT): PROJECTS */}
      {activeTab === 'projects' && role === 'STUDENT' && activeData.student && (
        <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
            <div>
              <h2 className="text-sm font-bold text-slate-900 dark:text-white">Academic &amp; Engineering Projects</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Showcase software, hardware, or research projects with repository or live links.
              </p>
            </div>
            {isEditing && (
              <button
                type="button"
                onClick={() =>
                  updateDraftStudent({
                    projects: [
                      ...(draft.student?.projects || []),
                      {
                        id: `prj-${Date.now()}`,
                        title: '',
                        description: '',
                        technologies: [],
                        link: '',
                        duration: '',
                      },
                    ],
                  })
                }
                className="px-3 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold flex items-center gap-1 whitespace-nowrap shrink-0 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Project</span>
              </button>
            )}
          </div>

          {(!activeData.student.projects || activeData.student.projects.length === 0) ? (
            <p className="text-xs text-slate-500 dark:text-slate-400 py-4">
              No projects added yet.{!isEditing && ' Click Edit Profile to add your projects.'}
            </p>
          ) : (
            <div className="space-y-4">
              {activeData.student.projects.map((proj, idx) => (
                <div
                  key={proj.id || idx}
                  className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/50 space-y-3"
                >
                  {isEditing ? (
                    <>
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
                          Project #{idx + 1}
                        </span>
                        <button
                          type="button"
                          onClick={() =>
                            updateDraftStudent({
                              projects: draft.student!.projects.filter((_, i) => i !== idx),
                            })
                          }
                          className="text-xs text-rose-600 hover:underline flex items-center gap-1 cursor-pointer"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                          <span>Remove</span>
                        </button>
                      </div>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <input
                          type="text"
                          placeholder="Project Title *"
                          value={proj.title}
                          onChange={(e) => {
                            const next = [...draft.student!.projects];
                            next[idx] = { ...proj, title: e.target.value };
                            updateDraftStudent({ projects: next });
                          }}
                          className={inputCls}
                        />
                        <input
                          type="text"
                          placeholder="Duration (e.g. Jan 2026 - Apr 2026)"
                          value={proj.duration || ''}
                          onChange={(e) => {
                            const next = [...draft.student!.projects];
                            next[idx] = { ...proj, duration: e.target.value };
                            updateDraftStudent({ projects: next });
                          }}
                          className={inputCls}
                        />
                        <input
                          type="text"
                          placeholder="Technologies Used (comma-separated: React, MySQL, Java)"
                          value={(proj.technologies || []).join(', ')}
                          onChange={(e) => {
                            const next = [...draft.student!.projects];
                            next[idx] = {
                              ...proj,
                              technologies: e.target.value.split(',').map((s) => s.trim()).filter(Boolean),
                            };
                            updateDraftStudent({ projects: next });
                          }}
                          className={inputCls}
                        />
                        <input
                          type="url"
                          placeholder="Project / GitHub URL (https://...)"
                          value={proj.link || ''}
                          onChange={(e) => {
                            const next = [...draft.student!.projects];
                            next[idx] = { ...proj, link: e.target.value };
                            updateDraftStudent({ projects: next });
                          }}
                          className={inputCls}
                        />
                        <textarea
                          rows={2}
                          placeholder="Project description and key outcomes..."
                          value={proj.description}
                          onChange={(e) => {
                            const next = [...draft.student!.projects];
                            next[idx] = { ...proj, description: e.target.value };
                            updateDraftStudent({ projects: next });
                          }}
                          className={`${inputCls} md:col-span-2`}
                        />
                      </div>
                    </>
                  ) : (
                    <div className="space-y-1.5">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <h3 className="text-sm font-bold text-slate-900 dark:text-white">{proj.title}</h3>
                        <div className="flex items-center gap-3 text-xs">
                          {proj.duration && <span className="text-slate-500 dark:text-slate-400">{proj.duration}</span>}
                          {proj.link && (
                            <a
                              href={proj.link}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="inline-flex items-center gap-1 font-semibold text-[#2684FF] hover:underline"
                            >
                              <span>Project Link</span>
                              <ExternalLink className="w-3 h-3" />
                            </a>
                          )}
                        </div>
                      </div>
                      {proj.description && (
                        <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">{proj.description}</p>
                      )}
                      {proj.technologies && proj.technologies.length > 0 && (
                        <div className="text-[11px] font-mono text-slate-500 dark:text-slate-400 pt-1">
                          Stack: {proj.technologies.join(' · ')}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* TAB 5 (STUDENT): INTERNSHIPS, WORKSHOPS, HACKATHONS & ACHIEVEMENTS */}
      {activeTab === 'experience' && role === 'STUDENT' && activeData.student && (
        <div className="space-y-6">
          {/* Internships & Work Experience */}
          <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
              <div>
                <h2 className="text-sm font-bold text-slate-900 dark:text-white">Internships &amp; Work Experience</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Industry internships, industrial training, or part-time engineering roles.
                </p>
              </div>
              {isEditing && (
                <button
                  type="button"
                  onClick={() =>
                    updateDraftStudent({
                      internships: [
                        ...(draft.student?.internships || []),
                        { id: `int-${Date.now()}`, companyName: '', role: '', duration: '', location: '', description: '' },
                      ],
                    })
                  }
                  className="px-3 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold flex items-center gap-1 whitespace-nowrap shrink-0 cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add Internship</span>
                </button>
              )}
            </div>

            {(!activeData.student.internships || activeData.student.internships.length === 0) ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">No internships recorded yet.</p>
            ) : (
              <div className="space-y-3">
                {activeData.student.internships.map((intr, idx) => (
                  <div
                    key={intr.id || idx}
                    className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/50 space-y-3"
                  >
                    {isEditing ? (
                      <>
                        <div className="flex items-center justify-between">
                          <span className="text-[11px] font-bold uppercase text-slate-400">Experience #{idx + 1}</span>
                          <button
                            type="button"
                            onClick={() =>
                              updateDraftStudent({
                                internships: draft.student!.internships.filter((_, i) => i !== idx),
                              })
                            }
                            className="text-xs text-rose-600 hover:underline flex items-center gap-1 cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                            <span>Remove</span>
                          </button>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                          <input
                            type="text"
                            placeholder="Company / Organization *"
                            value={intr.companyName}
                            onChange={(e) => {
                              const next = [...draft.student!.internships];
                              next[idx] = { ...intr, companyName: e.target.value };
                              updateDraftStudent({ internships: next });
                            }}
                            className={inputCls}
                          />
                          <input
                            type="text"
                            placeholder="Role / Title *"
                            value={intr.role}
                            onChange={(e) => {
                              const next = [...draft.student!.internships];
                              next[idx] = { ...intr, role: e.target.value };
                              updateDraftStudent({ internships: next });
                            }}
                            className={inputCls}
                          />
                          <input
                            type="text"
                            placeholder="Duration (e.g. May 2025 - Jul 2025)"
                            value={intr.duration}
                            onChange={(e) => {
                              const next = [...draft.student!.internships];
                              next[idx] = { ...intr, duration: e.target.value };
                              updateDraftStudent({ internships: next });
                            }}
                            className={inputCls}
                          />
                          <input
                            type="text"
                            placeholder="Location (e.g. Hyderabad / Remote)"
                            value={intr.location || ''}
                            onChange={(e) => {
                              const next = [...draft.student!.internships];
                              next[idx] = { ...intr, location: e.target.value };
                              updateDraftStudent({ internships: next });
                            }}
                            className={inputCls}
                          />
                          <textarea
                            rows={2}
                            placeholder="Key responsibilities and deliverables..."
                            value={intr.description}
                            onChange={(e) => {
                              const next = [...draft.student!.internships];
                              next[idx] = { ...intr, description: e.target.value };
                              updateDraftStudent({ internships: next });
                            }}
                            className={`${inputCls} md:col-span-2`}
                          />
                        </div>
                      </>
                    ) : (
                      <div className="space-y-1">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <h3 className="text-sm font-bold text-slate-900 dark:text-white">
                            {intr.role} · <span className="text-[#2684FF] dark:text-[#32C5F4]">{intr.companyName}</span>
                          </h3>
                          <span className="text-xs text-slate-500 dark:text-slate-400">
                            {[intr.duration, intr.location].filter(Boolean).join(' · ')}
                          </span>
                        </div>
                        {intr.description && (
                          <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">{intr.description}</p>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Achievements, Awards, Workshops & Hackathons */}
          <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
              <div>
                <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                  Achievements, Awards, Workshops &amp; Hackathons
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Academic honors, coding contest rankings, technical workshops, and hackathon participations.
                </p>
              </div>
              {isEditing && (
                <button
                  type="button"
                  onClick={() =>
                    updateDraftStudent({
                      achievements: [
                        ...(draft.student?.achievements || []),
                        {
                          id: `ach-${Date.now()}`,
                          title: '',
                          category: 'HACKATHON',
                          organizer: '',
                          eventDate: '',
                          description: '',
                          credentialUrl: '',
                        },
                      ],
                    })
                  }
                  className="px-3 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold flex items-center gap-1 whitespace-nowrap shrink-0 cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add Entry</span>
                </button>
              )}
            </div>

            {(!activeData.student.achievements || activeData.student.achievements.length === 0) ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                No achievements, workshops, or hackathons added yet.
              </p>
            ) : (
              <div className="space-y-3">
                {activeData.student.achievements.map((ach, idx) => (
                  <div
                    key={ach.id || idx}
                    className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/50 space-y-3"
                  >
                    {isEditing ? (
                      <>
                        <div className="flex items-center justify-between">
                          <span className="text-[11px] font-bold uppercase text-slate-400">Entry #{idx + 1}</span>
                          <button
                            type="button"
                            onClick={() =>
                              updateDraftStudent({
                                achievements: (draft.student!.achievements || []).filter((_, i) => i !== idx),
                              })
                            }
                            className="text-xs text-rose-600 hover:underline flex items-center gap-1 cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                            <span>Remove</span>
                          </button>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                          <input
                            type="text"
                            placeholder="Title (e.g. Smart India Hackathon Finalist) *"
                            value={ach.title}
                            onChange={(e) => {
                              const next = [...(draft.student!.achievements || [])];
                              next[idx] = { ...ach, title: e.target.value };
                              updateDraftStudent({ achievements: next });
                            }}
                            className={inputCls}
                          />
                          <select
                            value={ach.category}
                            onChange={(e) => {
                              const next = [...(draft.student!.achievements || [])];
                              next[idx] = { ...ach, category: e.target.value as AchievementItem['category'] };
                              updateDraftStudent({ achievements: next });
                            }}
                            className={inputCls}
                          >
                            <option value="AWARD">Achievement / Award</option>
                            <option value="HACKATHON">Hackathon</option>
                            <option value="WORKSHOP">Workshop / Seminar</option>
                          </select>
                          <input
                            type="text"
                            placeholder="Organizer / Institution"
                            value={ach.organizer || ''}
                            onChange={(e) => {
                              const next = [...(draft.student!.achievements || [])];
                              next[idx] = { ...ach, organizer: e.target.value };
                              updateDraftStudent({ achievements: next });
                            }}
                            className={inputCls}
                          />
                          <input
                            type="text"
                            placeholder="Date (e.g. Oct 2025)"
                            value={ach.eventDate || ''}
                            onChange={(e) => {
                              const next = [...(draft.student!.achievements || [])];
                              next[idx] = { ...ach, eventDate: e.target.value };
                              updateDraftStudent({ achievements: next });
                            }}
                            className={inputCls}
                          />
                          <input
                            type="url"
                            placeholder="Certificate / Proof URL (https://...)"
                            value={ach.credentialUrl || ''}
                            onChange={(e) => {
                              const next = [...(draft.student!.achievements || [])];
                              next[idx] = { ...ach, credentialUrl: e.target.value };
                              updateDraftStudent({ achievements: next });
                            }}
                            className={`${inputCls} md:col-span-2`}
                          />
                          <textarea
                            rows={2}
                            placeholder="Details or outcome..."
                            value={ach.description || ''}
                            onChange={(e) => {
                              const next = [...(draft.student!.achievements || [])];
                              next[idx] = { ...ach, description: e.target.value };
                              updateDraftStudent({ achievements: next });
                            }}
                            className={`${inputCls} md:col-span-2`}
                          />
                        </div>
                      </>
                    ) : (
                      <div className="space-y-1">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="text-[11px] font-bold uppercase tracking-wide text-[#2684FF] dark:text-[#32C5F4]">
                              {ach.category}
                            </span>
                            <span className="text-slate-300 dark:text-slate-600">·</span>
                            <h3 className="text-sm font-bold text-slate-900 dark:text-white">{ach.title}</h3>
                          </div>
                          <div className="flex items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
                            <span>{[ach.organizer, ach.eventDate].filter(Boolean).join(' · ')}</span>
                            {ach.credentialUrl && (
                              <a
                                href={ach.credentialUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1 font-semibold text-[#2684FF] hover:underline"
                              >
                                <span>Verify</span>
                                <ExternalLink className="w-3 h-3" />
                              </a>
                            )}
                          </div>
                        </div>
                        {ach.description && (
                          <p className="text-xs text-slate-600 dark:text-slate-300">{ach.description}</p>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      )}

      {/* TAB 6 (STUDENT & FACULTY): CERTIFICATIONS */}
      {activeTab === 'certifications' && (role === 'STUDENT' || role === 'FACULTY') && (
        <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-4">
          {(() => {
            const certs: Certification[] =
              role === 'STUDENT'
                ? activeData.student?.certifications || []
                : activeData.faculty?.certifications || [];

            const updateCerts = (next: Certification[]) => {
              if (role === 'STUDENT') {
                updateDraftStudent({ certifications: next });
              } else {
                setDraft((prev) => ({
                  ...prev,
                  faculty: prev.faculty ? { ...prev.faculty, certifications: next } : prev.faculty,
                }));
              }
            };

            return (
              <>
                <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
                  <div>
                    <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                      Professional Certifications &amp; Credentials
                    </h2>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      Industry certifications, NPTEL / Coursera / AWS / Oracle credentials with verification URLs.
                    </p>
                  </div>
                  {isEditing && (
                    <button
                      type="button"
                      onClick={() =>
                        updateCerts([
                          ...certs,
                          { id: `crt-${Date.now()}`, name: '', issuingOrg: '', issueDate: '', credentialId: '', credentialUrl: '' },
                        ])
                      }
                      className="px-3 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold flex items-center gap-1 whitespace-nowrap shrink-0 cursor-pointer"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>Add Certification</span>
                    </button>
                  )}
                </div>

                {certs.length === 0 ? (
                  <p className="text-xs text-slate-500 dark:text-slate-400 py-3">
                    No certifications added yet.
                  </p>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {certs.map((cert, idx) => (
                      <div
                        key={cert.id || idx}
                        className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/50 space-y-2.5"
                      >
                        {isEditing ? (
                          <>
                            <div className="flex items-center justify-between">
                              <span className="text-[11px] font-bold uppercase text-slate-400">Cert #{idx + 1}</span>
                              <button
                                type="button"
                                onClick={() => updateCerts(certs.filter((_, i) => i !== idx))}
                                className="text-xs text-rose-600 hover:underline flex items-center gap-1 cursor-pointer"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                                <span>Remove</span>
                              </button>
                            </div>
                            <input
                              type="text"
                              placeholder="Certificate Name *"
                              value={cert.name}
                              onChange={(e) => {
                                const next = [...certs];
                                next[idx] = { ...cert, name: e.target.value };
                                updateCerts(next);
                              }}
                              className={inputCls}
                            />
                            <input
                              type="text"
                              placeholder="Issuing Organization *"
                              value={cert.issuingOrg}
                              onChange={(e) => {
                                const next = [...certs];
                                next[idx] = { ...cert, issuingOrg: e.target.value };
                                updateCerts(next);
                              }}
                              className={inputCls}
                            />
                            <div className="grid grid-cols-2 gap-2">
                              <input
                                type="text"
                                placeholder="Issue Date (e.g. Aug 2025)"
                                value={cert.issueDate}
                                onChange={(e) => {
                                  const next = [...certs];
                                  next[idx] = { ...cert, issueDate: e.target.value };
                                  updateCerts(next);
                                }}
                                className={inputCls}
                              />
                              <input
                                type="text"
                                placeholder="Credential ID"
                                value={cert.credentialId || ''}
                                onChange={(e) => {
                                  const next = [...certs];
                                  next[idx] = { ...cert, credentialId: e.target.value };
                                  updateCerts(next);
                                }}
                                className={inputCls}
                              />
                            </div>
                            <input
                              type="url"
                              placeholder="Credential Verification URL (https://...)"
                              value={cert.credentialUrl || ''}
                              onChange={(e) => {
                                const next = [...certs];
                                next[idx] = { ...cert, credentialUrl: e.target.value };
                                updateCerts(next);
                              }}
                              className={inputCls}
                            />
                          </>
                        ) : (
                          <div className="space-y-1">
                            <div className="text-sm font-bold text-slate-900 dark:text-white">{cert.name}</div>
                            <div className="text-xs text-slate-600 dark:text-slate-300">
                              {cert.issuingOrg}
                              {cert.issueDate ? ` · ${cert.issueDate}` : ''}
                            </div>
                            {cert.credentialId && (
                              <div className="text-[11px] font-mono text-slate-500">ID: {cert.credentialId}</div>
                            )}
                            {cert.credentialUrl && (
                              <a
                                href={cert.credentialUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1 text-xs font-semibold text-[#2684FF] hover:underline pt-1"
                              >
                                <span>Verify Credential</span>
                                <ExternalLink className="w-3 h-3" />
                              </a>
                            )}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </>
            );
          })()}
        </section>
      )}

      {/* FACULTY TAB: EXPERTISE & TEACHING */}
      {activeTab === 'expertise' && role === 'FACULTY' && activeData.faculty && (
        <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-5">
          <div className="border-b border-slate-100 dark:border-slate-800 pb-3">
            <h2 className="text-sm font-bold text-slate-900 dark:text-white">
              Academic Qualifications, Expertise &amp; Teaching Portfolio
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Verified institutional department ({activeData.faculty.department}) and approval records remain protected.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Academic Qualifications
              </label>
              {isEditing ? (
                <input
                  type="text"
                  placeholder="e.g. Ph.D (CSE), M.Tech (Software Engineering)"
                  value={draft.faculty?.qualifications || ''}
                  onChange={(e) =>
                    setDraft((prev) => ({
                      ...prev,
                      faculty: prev.faculty ? { ...prev.faculty, qualifications: e.target.value } : prev.faculty,
                    }))
                  }
                  className={inputCls}
                />
              ) : (
                <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                  {activeData.faculty.qualifications || 'Not specified'}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center gap-1">
                <span>Department (Institutional Verified)</span>
                <Lock className="w-3 h-3 text-slate-400" />
              </label>
              <input type="text" value={activeData.faculty.department} disabled className={readOnlyCls} />
            </div>

            <TagListEditor
              label="Areas of Expertise"
              items={activeData.faculty.areasOfExpertise || []}
              isEditing={isEditing}
              placeholder="e.g. Machine Learning, Cloud Computing, Algorithms"
              onChange={(next) =>
                setDraft((prev) => ({
                  ...prev,
                  faculty: prev.faculty ? { ...prev.faculty, areasOfExpertise: next } : prev.faculty,
                }))
              }
            />

            <TagListEditor
              label="Subjects Handled"
              items={activeData.faculty.subjectsHandled || []}
              isEditing={isEditing}
              placeholder="e.g. Data Structures, DBMS, Operating Systems"
              onChange={(next) =>
                setDraft((prev) => ({
                  ...prev,
                  faculty: prev.faculty ? { ...prev.faculty, subjectsHandled: next } : prev.faculty,
                }))
              }
            />

            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Research Interests
              </label>
              {isEditing ? (
                <textarea
                  rows={3}
                  placeholder="Describe ongoing research domains, funded projects, or mentoring focus..."
                  value={draft.faculty?.researchInterests || ''}
                  onChange={(e) =>
                    setDraft((prev) => ({
                      ...prev,
                      faculty: prev.faculty ? { ...prev.faculty, researchInterests: e.target.value } : prev.faculty,
                    }))
                  }
                  className={inputCls}
                />
              ) : (
                <p className="text-xs text-slate-700 dark:text-slate-300 leading-relaxed py-1">
                  {activeData.faculty.researchInterests || 'No research interests recorded yet.'}
                </p>
              )}
            </div>
          </div>
        </section>
      )}

      {/* FACULTY TAB: PUBLICATIONS */}
      {activeTab === 'publications' && role === 'FACULTY' && activeData.faculty && (
        <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
            <div>
              <h2 className="text-sm font-bold text-slate-900 dark:text-white">Research Publications</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                IEEE, Springer, Elsevier journals, conferences, and patents.
              </p>
            </div>
            {isEditing && (
              <button
                type="button"
                onClick={() =>
                  setDraft((prev) => ({
                    ...prev,
                    faculty: prev.faculty
                      ? {
                          ...prev.faculty,
                          publications: [
                            ...(prev.faculty.publications || []),
                            { id: `pub-${Date.now()}`, title: '', journalOrConference: '', year: '', authors: '', url: '' },
                          ],
                        }
                      : prev.faculty,
                  }))
                }
                className="px-3 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold flex items-center gap-1 whitespace-nowrap shrink-0 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Publication</span>
              </button>
            )}
          </div>

          {(!activeData.faculty.publications || activeData.faculty.publications.length === 0) ? (
            <p className="text-xs text-slate-500 dark:text-slate-400">No publications recorded yet.</p>
          ) : (
            <div className="space-y-3">
              {activeData.faculty.publications.map((pub: PublicationItem, idx: number) => (
                <div
                  key={pub.id || idx}
                  className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/50 space-y-2.5"
                >
                  {isEditing ? (
                    <>
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] font-bold uppercase text-slate-400">Publication #{idx + 1}</span>
                        <button
                          type="button"
                          onClick={() =>
                            setDraft((prev) => ({
                              ...prev,
                              faculty: prev.faculty
                                ? {
                                    ...prev.faculty,
                                    publications: prev.faculty.publications.filter((_, i) => i !== idx),
                                  }
                                : prev.faculty,
                            }))
                          }
                          className="text-xs text-rose-600 hover:underline flex items-center gap-1 cursor-pointer"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                          <span>Remove</span>
                        </button>
                      </div>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <input
                          type="text"
                          placeholder="Paper / Publication Title *"
                          value={pub.title}
                          onChange={(e) => {
                            const next = [...draft.faculty!.publications];
                            next[idx] = { ...pub, title: e.target.value };
                            setDraft((prev) => ({ ...prev, faculty: { ...prev.faculty!, publications: next } }));
                          }}
                          className={`${inputCls} md:col-span-2`}
                        />
                        <input
                          type="text"
                          placeholder="Journal or Conference Name"
                          value={pub.journalOrConference}
                          onChange={(e) => {
                            const next = [...draft.faculty!.publications];
                            next[idx] = { ...pub, journalOrConference: e.target.value };
                            setDraft((prev) => ({ ...prev, faculty: { ...prev.faculty!, publications: next } }));
                          }}
                          className={inputCls}
                        />
                        <input
                          type="text"
                          placeholder="Year (e.g. 2025)"
                          value={pub.year}
                          onChange={(e) => {
                            const next = [...draft.faculty!.publications];
                            next[idx] = { ...pub, year: e.target.value };
                            setDraft((prev) => ({ ...prev, faculty: { ...prev.faculty!, publications: next } }));
                          }}
                          className={inputCls}
                        />
                        <input
                          type="text"
                          placeholder="Authors"
                          value={pub.authors || ''}
                          onChange={(e) => {
                            const next = [...draft.faculty!.publications];
                            next[idx] = { ...pub, authors: e.target.value };
                            setDraft((prev) => ({ ...prev, faculty: { ...prev.faculty!, publications: next } }));
                          }}
                          className={inputCls}
                        />
                        <input
                          type="url"
                          placeholder="DOI / Paper URL (https://...)"
                          value={pub.url || ''}
                          onChange={(e) => {
                            const next = [...draft.faculty!.publications];
                            next[idx] = { ...pub, url: e.target.value };
                            setDraft((prev) => ({ ...prev, faculty: { ...prev.faculty!, publications: next } }));
                          }}
                          className={inputCls}
                        />
                      </div>
                    </>
                  ) : (
                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <h3 className="text-sm font-bold text-slate-900 dark:text-white">{pub.title}</h3>
                        {pub.year && <span className="text-xs font-mono text-slate-500">{pub.year}</span>}
                      </div>
                      <div className="text-xs text-slate-600 dark:text-slate-300">
                        {[pub.journalOrConference, pub.authors].filter(Boolean).join(' · ')}
                      </div>
                      {pub.url && (
                        <a
                          href={pub.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-xs font-semibold text-[#2684FF] hover:underline"
                        >
                          <span>Read Paper</span>
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* COMPANY / RECRUITER TAB: COMPANY DETAILS & LOGO */}
      {activeTab === 'company' && role === 'COMPANY' && activeData.company && (
        <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 dark:border-slate-800 pb-4">
            <div className="flex items-center gap-4">
              <div className="w-16 h-16 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-950 flex items-center justify-center overflow-hidden shrink-0">
                {activeData.company.companyLogo ? (
                  <img
                    src={activeData.company.companyLogo}
                    alt={activeData.company.name}
                    className="w-full h-full object-contain p-1"
                  />
                ) : (
                  <Building2 className="w-7 h-7 text-slate-400" />
                )}
              </div>
              <div>
                <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                  {activeData.company.name || 'Company Profile'}
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Corporate verification status ({activeData.company.approvalStatus || 'ACTIVE'}) is protected by College Administration.
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <button
                type="button"
                onClick={() => logoInputRef.current?.click()}
                className="px-3 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold flex items-center gap-1.5 cursor-pointer"
              >
                <Upload className="w-3.5 h-3.5" />
                <span>Upload Logo</span>
              </button>
              {activeData.company.companyLogo && (
                <button
                  type="button"
                  onClick={() => handleRemovePhoto('company_logo')}
                  className="px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-xs text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Remove
                </button>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">Company Name *</label>
              {isEditing ? (
                <input
                  type="text"
                  value={draft.company?.name || ''}
                  onChange={(e) =>
                    setDraft((prev) => ({
                      ...prev,
                      company: { ...(prev.company || {}), name: e.target.value },
                    }))
                  }
                  className={inputCls}
                />
              ) : (
                <div className="text-xs font-semibold text-slate-900 dark:text-white py-1.5">
                  {activeData.company.name}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">Industry *</label>
              {isEditing ? (
                <input
                  type="text"
                  value={draft.company?.industry || ''}
                  onChange={(e) =>
                    setDraft((prev) => ({
                      ...prev,
                      company: { ...(prev.company || {}), industry: e.target.value },
                    }))
                  }
                  className={inputCls}
                />
              ) : (
                <div className="text-xs font-medium text-slate-900 dark:text-slate-100 py-1.5">
                  {activeData.company.industry || 'Not specified'}
                </div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">Company Website</label>
              {isEditing ? (
                <input
                  type="url"
                  placeholder="https://www.company.com"
                  value={draft.company?.website || ''}
                  onChange={(e) =>
                    setDraft((prev) => ({
                      ...prev,
                      company: { ...(prev.company || {}), website: e.target.value },
                    }))
                  }
                  className={inputCls}
                />
              ) : activeData.company.website ? (
                <a
                  href={activeData.company.website}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs font-semibold text-[#2684FF] hover:underline py-1.5"
                >
                  <span>{activeData.company.website}</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              ) : (
                <div className="text-xs text-slate-400 py-1.5">Not specified</div>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1 flex items-center gap-1">
                <span>Verification &amp; Tier (Protected)</span>
                <Lock className="w-3 h-3 text-slate-400" />
              </label>
              <input
                type="text"
                value={`${activeData.company.approvalStatus || 'ACTIVE'} · ${activeData.company.tier || 'Standard IT'}`}
                disabled
                className={readOnlyCls}
              />
            </div>

            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Company Description
              </label>
              {isEditing ? (
                <textarea
                  rows={4}
                  placeholder="Describe company mission, engineering culture, and campus hiring focus..."
                  value={draft.company?.description || ''}
                  onChange={(e) =>
                    setDraft((prev) => ({
                      ...prev,
                      company: { ...(prev.company || {}), description: e.target.value },
                    }))
                  }
                  className={inputCls}
                />
              ) : (
                <p className="text-xs text-slate-700 dark:text-slate-300 leading-relaxed py-1">
                  {activeData.company.description || 'No description provided yet.'}
                </p>
              )}
            </div>
          </div>
        </section>
      )}

      {/* SOCIAL MEDIA & PROFESSIONAL LINKS TAB (All Roles) */}
      {activeTab === 'social' && (
        <div className="space-y-6">
          <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-5">
            <div className="border-b border-slate-100 dark:border-slate-800 pb-3">
              <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                Social Media &amp; Professional Coding Profiles
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {isEditing
                  ? 'Add valid http:// or https:// links. Empty fields are automatically hidden from public view.'
                  : 'Verified active links open directly in a new browser tab.'}
              </p>
            </div>

            {isEditing ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {SOCIAL_PLATFORMS.map((plat) => (
                  <div key={plat.key}>
                    <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                      {plat.label}
                    </label>
                    <div className="flex gap-2">
                      <input
                        type="url"
                        placeholder={plat.placeholder}
                        value={draft.socialLinks?.[plat.key] || ''}
                        onChange={(e) =>
                          setDraft((prev) => ({
                            ...prev,
                            socialLinks: { ...(prev.socialLinks || {}), [plat.key]: e.target.value },
                          }))
                        }
                        className={inputCls}
                      />
                      {draft.socialLinks?.[plat.key] && (
                        <button
                          type="button"
                          onClick={() =>
                            setDraft((prev) => ({
                              ...prev,
                              socialLinks: { ...(prev.socialLinks || {}), [plat.key]: '' },
                            }))
                          }
                          className="p-2 rounded-lg border border-slate-300 dark:border-slate-700 text-slate-400 hover:text-rose-500 cursor-pointer shrink-0"
                          title={`Clear ${plat.label}`}
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : activeSocialLinks.length === 0 ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                No social or coding profile links added yet. Click Edit Profile to connect LinkedIn, GitHub, LeetCode, or portfolio links.
              </p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {activeSocialLinks.map((plat) => (
                  <a
                    key={plat.key}
                    href={activeData.socialLinks[plat.key]}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 hover:border-[#2684FF] dark:hover:border-[#32C5F4] bg-slate-50/70 dark:bg-slate-950/60 flex items-center justify-between gap-3 group transition"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-bold text-slate-900 dark:text-white group-hover:text-[#2684FF] dark:group-hover:text-[#32C5F4]">
                        {plat.label}
                      </div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400 truncate">
                        {activeData.socialLinks[plat.key]}
                      </div>
                    </div>
                    <ExternalLink className="w-4 h-4 text-slate-400 group-hover:text-[#2684FF] shrink-0" />
                  </a>
                ))}
              </div>
            )}
          </section>

          {/* Custom Links */}
          <section className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
              <div>
                <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                  Other Professional &amp; Custom Links
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Add links to technical blogs, research profiles, Behance, StackOverflow, or YouTube channels.
                </p>
              </div>
              {isEditing && (
                <button
                  type="button"
                  onClick={() =>
                    setDraft((prev) => ({
                      ...prev,
                      customLinks: [...(prev.customLinks || []), { id: `lnk-${Date.now()}`, title: '', url: '' }],
                    }))
                  }
                  className="px-3 py-1.5 rounded-lg bg-[#2684FF] hover:bg-blue-600 text-white text-xs font-semibold flex items-center gap-1 whitespace-nowrap shrink-0 cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add Custom Link</span>
                </button>
              )}
            </div>

            {isEditing ? (
              (draft.customLinks || []).length === 0 ? (
                <p className="text-xs text-slate-400">No custom links added. Click "Add Custom Link" above.</p>
              ) : (
                <div className="space-y-2.5">
                  {draft.customLinks.map((cl: CustomLink, idx: number) => (
                    <div key={cl.id || idx} className="grid grid-cols-1 sm:grid-cols-12 gap-2 items-center">
                      <input
                        type="text"
                        placeholder="Title (e.g. Medium Blog)"
                        value={cl.title}
                        onChange={(e) => {
                          const next = [...draft.customLinks];
                          next[idx] = { ...cl, title: e.target.value };
                          setDraft((prev) => ({ ...prev, customLinks: next }));
                        }}
                        className={`${inputCls} sm:col-span-4`}
                      />
                      <input
                        type="url"
                        placeholder="https://..."
                        value={cl.url}
                        onChange={(e) => {
                          const next = [...draft.customLinks];
                          next[idx] = { ...cl, url: e.target.value };
                          setDraft((prev) => ({ ...prev, customLinks: next }));
                        }}
                        className={`${inputCls} sm:col-span-7`}
                      />
                      <button
                        type="button"
                        onClick={() =>
                          setDraft((prev) => ({
                            ...prev,
                            customLinks: prev.customLinks.filter((_, i) => i !== idx),
                          }))
                        }
                        className="p-2 rounded-lg border border-slate-300 dark:border-slate-700 text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/50 cursor-pointer flex justify-center sm:col-span-1"
                        title="Remove link"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))}
                </div>
              )
            ) : activeCustomLinks.length === 0 ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">No custom links added.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {activeCustomLinks.map((cl) => (
                  <a
                    key={cl.id}
                    href={cl.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 hover:border-[#2684FF] bg-slate-50/70 dark:bg-slate-950/60 flex items-center justify-between gap-3 group transition"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-bold text-slate-900 dark:text-white group-hover:text-[#2684FF]">
                        {cl.title}
                      </div>
                      <div className="text-[11px] text-slate-500 truncate">{cl.url}</div>
                    </div>
                    <ExternalLink className="w-4 h-4 text-slate-400 group-hover:text-[#2684FF] shrink-0" />
                  </a>
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
};
