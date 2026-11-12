import React from 'react';
import { AlertTriangle, Info, RefreshCw, Pencil } from 'lucide-react';

export const YabahColors = {
  Navy: '#101B2C',
  Ivory: '#F8F7F3',
  White: '#FFFFFF',
  Jade: '#0DAA9B',
  Gold: '#E8B65A',
  Slate: '#627186',
  NavyRaised: '#18263B',
  NavyHigh: '#213149',
  JadeDeep: '#078A7E',
  Danger: '#C2453D',
  DangerDark: '#FF8A80',
};

export type BadgeTone = 'Demo' | 'Warn' | 'Danger' | 'Info' | 'Success';

/** Original YABAH mark from ic_yabah_mark.xml */
export const YabahMark: React.FC<{ size?: number; className?: string }> = ({
  size = 36,
  className = '',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 108 108"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
    aria-hidden="true"
  >
    <g transform="translate(24, 23) scale(2.5)">
      <path
        fill="#0DAA9B"
        d="M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13C19,5.13 15.87,2 12,2z"
      />
      <path
        fill="none"
        stroke="#E8B65A"
        strokeWidth="1.7"
        strokeLinecap="round"
        d="M8.4,12.7C9.9,11.9 10.2,10.6 11.6,9.6S14.6,8.4 15.6,5.9"
      />
      <circle cx="15.6" cy="5.9" r="0.95" fill="#F8F7F3" />
    </g>
  </svg>
);

/** Car icon from ic_car.xml */
export const CarIcon: React.FC<{ size?: number; className?: string }> = ({
  size = 24,
  className = '',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="currentColor"
    className={className}
    aria-hidden="true"
  >
    <path d="M18.92,6.01C18.72,5.42 18.16,5 17.5,5h-11c-0.66,0 -1.21,0.42 -1.42,1.01L3,12v8c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-1h12v1c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-8l-2.08,-5.99zM6.5,16c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,13 6.5,13s1.5,0.67 1.5,1.5S7.33,16 6.5,16zM17.5,16c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5zM5,11l1.5,-4.5h11L19,11H5z" />
  </svg>
);

/** My Location icon from ic_my_location.xml */
export const MyLocationIcon: React.FC<{ size?: number; className?: string }> = ({
  size = 22,
  className = '',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="currentColor"
    className={className}
    aria-hidden="true"
  >
    <path d="M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM20.94,11c-0.46,-4.17 -3.77,-7.48 -7.94,-7.94V1h-2v2.06C6.83,3.52 3.52,6.83 3.06,11H1v2h2.06c0.46,4.17 3.77,7.48 7.94,7.94V23h2v-2.06c4.17,-0.46 7.48,-3.77 7.94,-7.94H23v-2h-2.06zM12,19c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z" />
  </svg>
);

export const YabahPrimaryButton: React.FC<{
  text: string;
  onClick: () => void;
  enabled?: boolean;
  loading?: boolean;
  testId?: string;
  className?: string;
}> = ({ text, onClick, enabled = true, loading = false, testId, className = '' }) => {
  const isDisabled = !enabled || loading;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={isDisabled}
      data-testid={testId}
      className={`w-full min-h-[56px] rounded-[16px] px-5 py-3 font-semibold text-[15px] leading-[22px] flex items-center justify-center gap-3 transition-all ${
        isDisabled
          ? 'bg-[#E2E0D9] dark:bg-[#2A3B55] text-[#627186] dark:text-[#A9B4C4] cursor-not-allowed'
          : 'bg-[#0DAA9B] hover:bg-[#078A7E] active:scale-[0.99] text-white cursor-pointer shadow-sm'
      } ${className}`}
    >
      {loading && (
        <span className="w-5 h-5 border-2 border-current border-t-transparent rounded-full animate-spin" />
      )}
      <span className="truncate">{text}</span>
    </button>
  );
};

export const YabahSecondaryButton: React.FC<{
  text: string;
  onClick: () => void;
  enabled?: boolean;
  testId?: string;
  className?: string;
}> = ({ text, onClick, enabled = true, testId, className = '' }) => (
  <button
    type="button"
    onClick={onClick}
    disabled={!enabled}
    data-testid={testId}
    className={`w-full min-h-[52px] rounded-[16px] px-5 py-2.5 font-semibold text-[15px] leading-[22px] border transition-all flex items-center justify-center ${
      !enabled
        ? 'border-[#E2E0D9] dark:border-[#2A3B55] text-[#627186] opacity-50 cursor-not-allowed'
        : 'border-[#B9C0CB] dark:border-[#51617A] text-[#101B2C] dark:text-[#F8F7F3] bg-transparent hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer'
    } ${className}`}
  >
    <span className="truncate">{text}</span>
  </button>
);

export const StatusBadge: React.FC<{
  text: string;
  tone: BadgeTone;
  testId?: string;
  className?: string;
}> = ({ text, tone, testId, className = '' }) => {
  const toneClasses: Record<BadgeTone, string> = {
    Demo: 'bg-[#E8B65A] text-[#101B2C]',
    Warn: 'bg-[#FBEFD7] text-[#6B4A0E] dark:bg-[#3A2F17] dark:text-[#F3D9A4]',
    Danger: 'bg-[#FBE3E1] text-[#7A201A] dark:bg-[#3D1E1E] dark:text-[#FFC9C4]',
    Info: 'bg-[#F0EEE8] text-[#627186] dark:bg-[#213149] dark:text-[#A9B4C4]',
    Success: 'bg-[#D3F1ED] text-[#00413B] dark:bg-[#0A4A44] dark:text-[#B9EDE6]',
  };
  return (
    <span
      data-testid={testId}
      className={`inline-flex items-center rounded-full px-2.5 py-1 text-[13px] leading-[18px] font-medium whitespace-nowrap ${toneClasses[tone]} ${className}`}
    >
      {text}
    </span>
  );
};

export const DemoChip: React.FC<{
  label: string;
  testId?: string;
}> = ({ label, testId }) => (
  <span
    data-testid={testId}
    aria-label={label}
    className="inline-flex items-center rounded-full bg-[#E8B65A] text-[#101B2C] px-3 py-1.5 text-[13px] leading-[18px] font-semibold shadow-sm"
  >
    {label === 'DEMO' ? label : `DEMO · ${label}`}
  </span>
);

export const MessageCard: React.FC<{
  message: string;
  tone: BadgeTone;
  actionLabel?: string;
  onAction?: () => void;
  testId?: string;
}> = ({ message, tone, actionLabel, onAction, testId }) => {
  const toneClasses: Record<BadgeTone, string> = {
    Danger: 'bg-[#FBE3E1] text-[#7A201A] dark:bg-[#3D1E1E] dark:text-[#FFC9C4]',
    Warn: 'bg-[#FBEFD7] text-[#6B4A0E] dark:bg-[#3A2F17] dark:text-[#F3D9A4]',
    Demo: 'bg-[#FBEFD7] text-[#6B4A0E] dark:bg-[#3A2F17] dark:text-[#F3D9A4]',
    Success: 'bg-[#D3F1ED] text-[#00413B] dark:bg-[#0A4A44] dark:text-[#B9EDE6]',
    Info: 'bg-[#F0EEE8] text-[#627186] dark:bg-[#213149] dark:text-[#A9B4C4]',
  };
  const isWarning = tone === 'Danger' || tone === 'Warn';
  return (
    <div
      role="status"
      aria-live="polite"
      data-testid={testId}
      className={`w-full rounded-[12px] px-3 py-2.5 flex items-center gap-2.5 ${toneClasses[tone]}`}
    >
      {isWarning ? (
        <AlertTriangle className="w-[18px] h-[18px] shrink-0" />
      ) : (
        <Info className="w-[18px] h-[18px] shrink-0" />
      )}
      <span className="text-[14px] leading-[22px] flex-1">{message}</span>
      {actionLabel && onAction && (
        <button
          type="button"
          onClick={onAction}
          className="min-h-[36px] px-2.5 py-1 rounded-lg flex items-center gap-1 text-[13px] font-medium hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer shrink-0"
        >
          <RefreshCw className="w-4 h-4" />
          <span>{actionLabel}</span>
        </button>
      )}
    </div>
  );
};

export const LocationRow: React.FC<{
  label: string;
  value: string | null;
  placeholder: string;
  dotColor: string;
  active: boolean;
  onClick?: () => void;
  showEdit?: boolean;
  editLabel?: string;
  testId?: string;
}> = ({
  label,
  value,
  placeholder,
  dotColor,
  active,
  onClick,
  showEdit,
  editLabel = 'Edit',
  testId,
}) => {
  const Tag = onClick ? 'button' : 'div';
  return (
    <Tag
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      data-testid={testId}
      className={`w-full min-h-[60px] rounded-[16px] px-4 py-2 flex items-center gap-3 text-start transition-colors bg-white dark:bg-[#18263B] ${
        active
          ? 'border-2 border-[#0DAA9B]'
          : 'border border-[#E2E0D9] dark:border-[#2A3B55]'
      } ${onClick ? 'cursor-pointer hover:bg-black/[0.02] dark:hover:bg-white/[0.02]' : ''}`}
    >
      <span
        className="w-3 h-3 rounded-full shrink-0"
        style={{ backgroundColor: dotColor }}
      />
      <div className="flex-1 min-w-0">
        <div className="text-[11px] leading-[16px] font-medium text-[#627186] dark:text-[#A9B4C4]">
          {label}
        </div>
        <div
          className={`text-[14px] leading-[20px] font-semibold truncate ${
            value
              ? 'text-[#101B2C] dark:text-[#F8F7F3]'
              : 'text-[#627186] dark:text-[#A9B4C4]'
          }`}
        >
          {value ?? placeholder}
        </div>
      </div>
      {showEdit && (
        <Pencil
          className="w-[18px] h-[18px] text-[#627186] dark:text-[#A9B4C4] shrink-0"
          aria-label={editLabel}
        />
      )}
    </Tag>
  );
};

export const KeyValueRow: React.FC<{
  label: string;
  value: string;
  emphasize?: boolean;
}> = ({ label, value, emphasize = false }) => (
  <div className="w-full py-0.5 flex items-center justify-between gap-4">
    <span className="text-[14px] leading-[22px] text-[#627186] dark:text-[#A9B4C4]">
      {label}
    </span>
    <span
      className={
        emphasize
          ? 'text-[16px] leading-[24px] font-semibold text-[#101B2C] dark:text-[#F8F7F3]'
          : 'text-[14px] leading-[22px] text-[#101B2C] dark:text-[#F8F7F3]'
      }
    >
      {value}
    </span>
  </div>
);

export const CenterPin: React.FC<{
  isPickup: boolean;
  lifted: boolean;
  description: string;
  topPadding: number;
  bottomPadding: number;
}> = ({ isPickup, lifted, description, topPadding, bottomPadding }) => {
  const color = isPickup ? '#0DAA9B' : '#E8B65A';
  return (
    <div
      className="pointer-events-none absolute inset-0 flex items-center justify-center"
      style={{
        paddingTop: `${topPadding}px`,
        paddingBottom: `${bottomPadding}px`,
      }}
    >
      <div className="relative flex items-center justify-center">
        {/* Ground shadow */}
        <div className="w-2 h-1 rounded-full bg-black/25" />
        {/* Pin SVG */}
        <svg
          width="34"
          height="46"
          viewBox="0 0 14 20"
          data-testid="center_pin"
          aria-label={description}
          role="img"
          className="absolute transition-transform duration-150"
          style={{
            transform: `translateY(${lifted ? -33 : -23}px)`,
          }}
        >
          <path
            d="M7,20 C7,20 0,12.2 0,7 C0,3.13 3.13,0 7,0 C10.87,0 14,3.13 14,7 C14,12.2 7,20 7,20 Z"
            fill={color}
            stroke="#FFFFFF"
            strokeWidth="1.2"
          />
          <circle cx="7" cy="7" r="2.6" fill="#FFFFFF" />
          <circle cx="7" cy="7" r="1.1" fill="#101B2C" />
        </svg>
      </div>
    </div>
  );
};
