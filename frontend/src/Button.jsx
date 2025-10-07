import React from 'react';

/**
 * Button component with multiple variants, sizes, and states
 * 
 * @param {Object} props
 * @param {string} props.variant - Button variant: 'primary', 'secondary', 'success', 'danger', 'warning', 'ghost'
 * @param {string} props.size - Button size: 'sm', 'md', 'lg'
 * @param {boolean} props.disabled - Whether the button is disabled
 * @param {boolean} props.loading - Whether the button is in loading state
 * @param {boolean} props.fullWidth - Whether the button should take full width
 * @param {React.ReactNode} props.icon - Icon to display before text
 * @param {React.ReactNode} props.iconRight - Icon to display after text
 * @param {React.ReactNode} props.children - Button content
 * @param {string} props.className - Additional CSS classes
 * @param {Function} props.onClick - Click handler
 * @param {string} props.type - Button type: 'button', 'submit', 'reset'
 */
export const Button = ({
  variant = 'primary',
  size = 'md',
  disabled = false,
  loading = false,
  fullWidth = false,
  icon = null,
  iconRight = null,
  children,
  className = '',
  onClick,
  type = 'button',
  ...props
}) => {
  // Base styles that apply to all buttons
  const baseStyles = 'font-medium rounded-lg transition-all duration-200 inline-flex items-center justify-center gap-2 border focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-slate-900';

  // Size variants
  const sizeStyles = {
    sm: 'px-3 py-1.5 text-xs',
    md: 'px-4 py-2 text-sm',
    lg: 'px-6 py-3 text-base',
  };

  // Variant styles
  const variantStyles = {
    primary: disabled
      ? 'bg-zinc-700/40 text-zinc-500 border-zinc-600/40 cursor-not-allowed'
      : 'bg-gradient-to-r from-zinc-800 to-zinc-700 text-zinc-100 border-zinc-600/50 shadow-lg shadow-zinc-900/20 hover:from-zinc-700 hover:to-zinc-600 hover:border-zinc-500/70 hover:shadow-xl hover:shadow-zinc-800/30 active:from-zinc-600 active:to-zinc-500 focus:ring-zinc-500/50',
    
    secondary: disabled
      ? 'bg-zinc-700/40 text-zinc-500 border-zinc-600/40 cursor-not-allowed'
      : 'bg-gradient-to-r from-zinc-900/60 to-zinc-800/60 text-zinc-300 border-zinc-700/40 shadow-md shadow-zinc-950/20 hover:from-zinc-800/80 hover:to-zinc-700/80 hover:text-zinc-200 hover:border-zinc-600/50 hover:shadow-lg hover:shadow-zinc-900/30 active:from-zinc-800 active:to-zinc-700 focus:ring-zinc-500/50',
    
    success: disabled
      ? 'bg-slate-700/40 text-slate-500 border-slate-600/40 cursor-not-allowed'
      : 'bg-gradient-to-r from-green-500/20 to-emerald-500/20 text-green-300 border-green-500/30 shadow-lg shadow-green-500/10 hover:from-green-500/30 hover:to-emerald-500/30 hover:border-green-400/50 hover:shadow-xl hover:shadow-green-500/20 active:from-green-500/40 active:to-emerald-500/40 focus:ring-green-500/50',
    
    danger: disabled
      ? 'bg-slate-700/40 text-slate-500 border-slate-600/40 cursor-not-allowed'
      : 'bg-gradient-to-r from-red-500/20 to-rose-500/20 text-red-300 border-red-500/30 shadow-lg shadow-red-500/10 hover:from-red-500/30 hover:to-rose-500/30 hover:border-red-400/50 hover:shadow-xl hover:shadow-red-500/20 active:from-red-500/40 active:to-rose-500/40 focus:ring-red-500/50',
    
    warning: disabled
      ? 'bg-slate-700/40 text-slate-500 border-slate-600/40 cursor-not-allowed'
      : 'bg-gradient-to-r from-orange-500/20 to-yellow-500/20 text-orange-300 border-orange-500/30 shadow-lg shadow-orange-500/10 hover:from-orange-500/30 hover:to-yellow-500/30 hover:border-orange-400/50 hover:shadow-xl hover:shadow-orange-500/20 active:from-orange-500/40 active:to-yellow-500/40 focus:ring-orange-500/50',
    
    ghost: disabled
      ? 'bg-transparent text-slate-500 border-transparent cursor-not-allowed'
      : 'bg-transparent text-cyan-300 border-transparent hover:bg-cyan-500/10 hover:text-cyan-200 active:bg-cyan-500/20 focus:ring-cyan-500/30',
  };

  // Loading spinner component
  const LoadingSpinner = () => (
    <svg 
      className="animate-spin h-4 w-4" 
      xmlns="http://www.w3.org/2000/svg" 
      fill="none" 
      viewBox="0 0 24 24"
    >
      <circle 
        className="opacity-25" 
        cx="12" 
        cy="12" 
        r="10" 
        stroke="currentColor" 
        strokeWidth="4"
      />
      <path 
        className="opacity-75" 
        fill="currentColor" 
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
      />
    </svg>
  );

  // Combine all styles
  const buttonClasses = [
    baseStyles,
    sizeStyles[size],
    variantStyles[variant],
    fullWidth ? 'w-full' : '',
    (disabled || loading) ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const handleClick = (e) => {
    if (disabled || loading) {
      e.preventDefault();
      return;
    }
    if (onClick) {
      onClick(e);
    }
  };

  return (
    <button
      type={type}
      className={buttonClasses}
      onClick={handleClick}
      disabled={disabled || loading}
      {...props}
    >
      {loading && <LoadingSpinner />}
      {!loading && icon && <span className="flex-shrink-0">{icon}</span>}
      {children && <span>{children}</span>}
      {!loading && iconRight && <span className="flex-shrink-0">{iconRight}</span>}
    </button>
  );
};

// Icon components for common use cases
export const PlayIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
);

export const StopIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
);

export const PlusIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
  </svg>
);

export const CheckIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
  </svg>
);

export const XIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
  </svg>
);

export const RefreshIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
  </svg>
);

export const SettingsIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
  </svg>
);

export const TrashIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
  </svg>
);

export const EditIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
  </svg>
);

export const DownloadIcon = () => (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
  </svg>
);

export default Button;

