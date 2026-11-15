import React, { useState, useMemo } from 'react';
import {
  Building2,
  Search,
  Plus,
  ExternalLink,
  Mail,
  Phone,
  Briefcase,
  MapPin,
  Edit2,
  Trash2,
  X,
} from 'lucide-react';
import { Company, User, PlacementDrive } from '../types';
import { api } from '../services/api';
import { ConfirmationModal } from '../components/ConfirmationModal';

interface CompaniesPageProps {
  currentUser: User | null;
  companies: Company[];
  drives: PlacementDrive[];
  onSelectDrive?: (driveId: string) => void;
  onNavigateToDrives: () => void;
  onRefreshData: () => Promise<void>;
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

export const CompaniesPage: React.FC<CompaniesPageProps> = ({
  currentUser,
  companies,
  drives,
  onNavigateToDrives,
  onRefreshData,
  onShowToast,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTier, setSelectedTier] = useState<string>('ALL');

  // Form states
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingCompany, setEditingCompany] = useState<Company | null>(null);
  const [deleteCandidate, setDeleteCandidate] = useState<Company | null>(null);

  const [formData, setFormData] = useState({
    name: '',
    industry: '',
    website: '',
    location: 'Hyderabad, Telangana',
    description: '',
    hrName: '',
    hrEmail: '',
    hrPhone: '',
    tier: 'Standard IT' as Company['tier'],
  });

  const canManage = currentUser?.role === 'ADMIN' || currentUser?.role === 'PLACEMENT_OFFICER';

  const filteredCompanies = useMemo(() => {
    return companies.filter((c) => {
      const q = searchQuery.toLowerCase().trim();
      const matchSearch = !q || c.name.toLowerCase().includes(q) || c.industry.toLowerCase().includes(q) || c.location.toLowerCase().includes(q);
      const matchTier = selectedTier === 'ALL' || c.tier === selectedTier;
      return matchSearch && matchTier;
    });
  }, [companies, searchQuery, selectedTier]);

  const handleOpenAdd = () => {
    setEditingCompany(null);
    setFormData({
      name: '',
      industry: 'Information Technology',
      website: 'https://',
      location: 'Hyderabad, Telangana',
      description: '',
      hrName: '',
      hrEmail: '',
      hrPhone: '',
      tier: 'Standard IT',
    });
    setIsFormOpen(true);
  };

  const handleOpenEdit = (comp: Company) => {
    setEditingCompany(comp);
    setFormData({
      name: comp.name,
      industry: comp.industry,
      website: comp.website,
      location: comp.location,
      description: comp.description,
      hrName: comp.hrName,
      hrEmail: comp.hrEmail,
      hrPhone: comp.hrPhone,
      tier: comp.tier,
    });
    setIsFormOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim()) return;

    try {
      if (editingCompany) {
        await api.updateCompany(editingCompany.id, formData);
        await onRefreshData();
        onShowToast('success', 'Company Updated', `Updated MySQL record for ${formData.name}`);
      } else {
        await api.createCompany(formData);
        await onRefreshData();
        onShowToast('success', 'Company Registered', `Saved partner company ${formData.name} in MySQL`);
      }
      setIsFormOpen(false);
    } catch (err: any) {
      onShowToast('error', 'Error Saving Company', err.message);
    }
  };

  const confirmDelete = async () => {
    if (!deleteCandidate) return;
    try {
      await api.deleteCompany(deleteCandidate.id);
      await onRefreshData();
      onShowToast('success', 'Company Removed', `Removed ${deleteCandidate.name} from MySQL directory.`);
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
          <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Campus Recruitment Partners</h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Partner corporate organizations participating in GCET placement drives.
          </p>
        </div>

        {canManage && (
          <button
            onClick={handleOpenAdd}
            className="px-3.5 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition cursor-pointer flex items-center space-x-1.5 shadow-xs"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Partner Company</span>
          </button>
        )}
      </div>

      {/* Filter and Search */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-xs flex flex-col md:flex-row items-stretch md:items-center gap-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search company by name, industry or location..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
          />
        </div>

        <div className="flex items-center space-x-2">
          <span className="text-xs text-slate-500 dark:text-slate-400 shrink-0">Hiring Tier:</span>
          <select
            value={selectedTier}
            onChange={(e) => setSelectedTier(e.target.value)}
            className="px-3 py-1.5 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
          >
            <option value="ALL">All Tiers</option>
            <option value="Tier 1 (Dream)">Tier 1 (Dream)</option>
            <option value="Tier 2 (Super Dream)">Tier 2 (Super Dream)</option>
            <option value="Core">Core Engineering</option>
            <option value="Standard IT">Standard IT Services</option>
          </select>
        </div>
      </div>

      {/* Companies Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredCompanies.length === 0 ? (
          <div className="col-span-3 p-12 text-center bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 text-xs text-slate-400">
            No recruitment partners found matching the filter criteria.
          </div>
        ) : (
          filteredCompanies.map((comp) => {
            const companyDrives = drives.filter((d) => d.companyId === comp.id);
            const activeDrivesCount = companyDrives.filter((d) => d.status === 'OPEN').length;

            return (
              <div
                key={comp.id}
                className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs hover:border-slate-300 dark:hover:border-slate-700 transition flex flex-col justify-between text-xs space-y-4"
              >
                <div>
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-bold text-sm text-slate-900 dark:text-slate-100">{comp.name}</h3>
                      <div className="text-[11px] text-cyan-700 dark:text-cyan-400 font-medium mt-0.5">{comp.industry}</div>
                    </div>

                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700">
                      {comp.tier}
                    </span>
                  </div>

                  <p className="text-[11px] text-slate-600 dark:text-slate-300 mt-2.5 line-clamp-2 leading-relaxed">
                    {comp.description}
                  </p>

                  <div className="mt-3 space-y-1 text-[11px] text-slate-500 dark:text-slate-400">
                    <div className="flex items-center space-x-1.5">
                      <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      <span>{comp.location}</span>
                    </div>

                    {comp.hrEmail && (
                      <div className="flex items-center space-x-1.5">
                        <Mail className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                        <span className="truncate">{comp.hrEmail}</span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between">
                  <div>
                    <button
                      onClick={onNavigateToDrives}
                      className="text-xs font-semibold text-slate-800 dark:text-slate-200 hover:text-cyan-700 dark:hover:text-cyan-400 flex items-center space-x-1 cursor-pointer"
                    >
                      <Briefcase className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
                      <span>{companyDrives.length} Drives ({activeDrivesCount} Active)</span>
                    </button>
                  </div>

                  <div className="flex items-center space-x-1">
                    {comp.website && (
                      <a
                        href={comp.website}
                        target="_blank"
                        rel="noreferrer"
                        className="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 transition"
                        title="Open Website"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                      </a>
                    )}

                    {canManage && (
                      <>
                        <button
                          onClick={() => handleOpenEdit(comp)}
                          className="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 transition cursor-pointer"
                          title="Edit Company"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setDeleteCandidate(comp)}
                          className="p-1.5 rounded hover:bg-rose-50 dark:hover:bg-rose-950/40 text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 transition cursor-pointer"
                          title="Delete Company"
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

      {/* Add / Edit Company Modal */}
      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-xs p-4 animate-in fade-in duration-150">
          <div className="bg-white dark:bg-slate-900 rounded-xl max-w-lg w-full shadow-2xl border border-slate-200 dark:border-slate-800 p-5">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">
                {editingCompany ? `Edit Company: ${editingCompany.name}` : 'Register Partner Company'}
              </h3>
              <button onClick={() => setIsFormOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSave} className="mt-4 space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Company Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Amazon Web Services (AWS)"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Industry Domain *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Cloud Computing / SaaS"
                    value={formData.industry}
                    onChange={(e) => setFormData({ ...formData, industry: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Hiring Tier *</label>
                  <select
                    value={formData.tier}
                    onChange={(e) => setFormData({ ...formData, tier: e.target.value as Company['tier'] })}
                    className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 focus:outline-none focus:ring-1 focus:ring-cyan-500 text-slate-700 dark:text-slate-200"
                  >
                    <option value="Tier 1 (Dream)">Tier 1 (Dream)</option>
                    <option value="Tier 2 (Super Dream)">Tier 2 (Super Dream)</option>
                    <option value="Core">Core Engineering</option>
                    <option value="Standard IT">Standard IT</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Website URL</label>
                  <input
                    type="url"
                    placeholder="https://..."
                    value={formData.website}
                    onChange={(e) => setFormData({ ...formData, website: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Office Location</label>
                  <input
                    type="text"
                    placeholder="e.g. Hitec City, Hyderabad"
                    value={formData.location}
                    onChange={(e) => setFormData({ ...formData, location: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Company Description</label>
                <textarea
                  rows={2}
                  placeholder="Overview of hiring history, technologies, and work culture..."
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                />
              </div>

              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">HR Contact</label>
                  <input
                    type="text"
                    placeholder="Full Name"
                    value={formData.hrName}
                    onChange={(e) => setFormData({ ...formData, hrName: e.target.value })}
                    className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">HR Email</label>
                  <input
                    type="email"
                    placeholder="campus@company.com"
                    value={formData.hrEmail}
                    onChange={(e) => setFormData({ ...formData, hrEmail: e.target.value })}
                    className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 dark:text-slate-300 mb-1">HR Phone</label>
                  <input
                    type="text"
                    placeholder="+91..."
                    value={formData.hrPhone}
                    onChange={(e) => setFormData({ ...formData, hrPhone: e.target.value })}
                    className="w-full px-2.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-cyan-500"
                  />
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
                  {editingCompany ? 'Save Changes' : 'Register Company'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!deleteCandidate}
        title="Confirm Company Deletion"
        message={`Are you sure you want to delete ${deleteCandidate?.name}? (Will be rejected if any active placement drives exist for this company).`}
        confirmLabel="Delete Company"
        isDestructive={true}
        onConfirm={confirmDelete}
        onCancel={() => setDeleteCandidate(null)}
      />

    </div>
  );
};
