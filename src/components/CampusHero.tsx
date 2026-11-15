import React from 'react';
import { GCET_CAMPUS_BUILDING_PHOTO } from '../assets/campusImages';

export interface CampusHeroProps {
  /** Institution header badge text (default: 'GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY') */
  institutionBadge?: string;
  /** Accreditation tag (e.g. 'UGC AUTONOMOUS | NAAC A+ & NBA') */
  accreditationBadge?: string;
  /** Main hero title (supports JSX for cyan highlights) */
  title: React.ReactNode;
  /** Secondary subtitle below title */
  subtitle?: React.ReactNode;
  /** Institutional tagline in quotation marks */
  tagline?: string;
  /** Welcome / overview description */
  description: React.ReactNode;
  /** Left footer information badges, address or links */
  details?: React.ReactNode;
  /** Action buttons and controls displayed on the right on desktop, stacked on mobile */
  actions?: React.ReactNode;
  /** Optional custom object position for the campus photo (defaults to 'object-[center_35%]') */
  imagePosition?: string;
  /** Optional custom minimum height classes */
  minHeightClass?: string;
  /** Optional additional outer container styling */
  className?: string;
}

export const CampusHero: React.FC<CampusHeroProps> = ({
  institutionBadge = 'GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY',
  accreditationBadge = 'UGC AUTONOMOUS | NAAC A+ & NBA',
  title,
  subtitle,
  tagline,
  description,
  details,
  actions,
  imagePosition = 'object-[center_35%]',
  minHeightClass = 'min-h-[380px] md:min-h-[420px] lg:min-h-[440px]',
  className = '',
}) => {
  return (
    <div
      className={`relative w-full rounded-2xl sm:rounded-3xl overflow-hidden border border-slate-700/60 shadow-2xl bg-[#0a1226] ${minHeightClass} flex items-center select-none ${className}`}
    >
      {/* 1. Official GCET Campus Building Photograph as Full Background */}
      <div className="absolute inset-0 z-0">
        <img
          src={GCET_CAMPUS_BUILDING_PHOTO}
          alt="Geethanjali College of Engineering and Technology (GCET) Cheeryal Campus"
          className={`w-full h-full object-cover ${imagePosition} select-none block pointer-events-none`}
          loading="eager"
        />
      </div>

      {/* 2. Carefully Engineered Dark Navy Transparent Gradient Overlay */}
      <div
        className="absolute inset-0 z-10 bg-gradient-to-r from-[#070e20]/95 via-[#091533]/85 sm:via-[#091533]/75 to-[#0b1736]/40 pointer-events-none"
        aria-hidden="true"
      />
      <div
        className="absolute inset-0 z-10 bg-gradient-to-t from-[#070e20]/80 via-transparent to-[#070e20]/30 pointer-events-none"
        aria-hidden="true"
      />

      {/* 3. Unified Hero Content Container (Left: Content, Right: Action Controls) */}
      <div className="relative z-20 w-full p-6 sm:p-8 lg:p-10 flex flex-col lg:flex-row lg:items-center justify-between gap-8">
        
        {/* Left Side: Institutional Info, Identity & Highlights */}
        <div className="space-y-3.5 max-w-2xl text-white">
          
          {/* Eyebrow Institutional Badges */}
          {(institutionBadge || accreditationBadge) && (
            <div className="flex flex-wrap items-center gap-2 text-[11px] sm:text-xs font-bold uppercase tracking-wider">
              {institutionBadge && (
                <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full bg-blue-500/20 backdrop-blur-md border border-cyan-400/30 text-[#32C5F4] shadow-xs">
                  <span className="w-2 h-2 rounded-full bg-[#32C5F4] animate-pulse" />
                  <span className="tracking-widest">{institutionBadge}</span>
                </span>
              )}
              {accreditationBadge && (
                <span className="inline-flex items-center px-2.5 py-1 rounded-full bg-slate-900/60 backdrop-blur-md border border-white/15 text-slate-200 font-semibold text-[10px] sm:text-[11px]">
                  {accreditationBadge}
                </span>
              )}
            </div>
          )}

          {/* Main Title & Subtitle */}
          <div>
            <h1 className="text-2xl sm:text-3xl md:text-4xl font-black text-white tracking-tight leading-tight drop-shadow-sm">
              {title}
            </h1>
            {subtitle && (
              <div className="text-sm sm:text-base md:text-lg font-bold text-sky-200 mt-1 tracking-wide">
                {subtitle}
              </div>
            )}
          </div>

          {/* Institutional Tagline */}
          {tagline && (
            <div className="text-xs sm:text-sm font-semibold text-cyan-300 italic tracking-wide">
              "{tagline}"
            </div>
          )}

          {/* Description */}
          <div className="text-xs sm:text-sm text-slate-200 font-normal leading-relaxed max-w-xl text-shadow-sm">
            {description}
          </div>

          {/* Footer Details / Address / Secondary Badges */}
          {details && (
            <div className="pt-1 flex flex-wrap items-center gap-3 text-xs">
              {details}
            </div>
          )}
        </div>

        {/* Right Side: Action Controls Column */}
        {actions && (
          <div className="flex flex-col sm:flex-row lg:flex-col gap-3 shrink-0 lg:w-72">
            {actions}
          </div>
        )}
      </div>
    </div>
  );
};
