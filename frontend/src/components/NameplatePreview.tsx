import React from 'react';

interface NameplatePreviewProps {
  username?: string;
  avatar?: string;
  color: string;
  endColor?: string;
  fallbackColor?: string;
  message?: string;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
}

/**
 * A reusable component that displays a Discord-style nameplate preview
 * showing how a color would appear on a username.
 */
export const NameplatePreview: React.FC<NameplatePreviewProps> = ({
  username = 'Username',
  avatar = '/images/default-avatar.png',
  color,
  endColor,
  fallbackColor = '#ffffff',
  message = 'This is the color for the Nameplate!',
  className = '',
  size = 'md'
}) => {
  // Helper function to validate hex colors - same as ItemPreview.tsx
  const isValidHexColor = (color: string | null | undefined): boolean => {
    return color ? color.startsWith('#') && /^#[0-9A-F]{6}$/i.test(color) : false;
  };

  // Determine if color is valid using strict validation
  const isValidStartColor = isValidHexColor(color);
  const isValidEndColor = isValidHexColor(endColor);
  const displayColor = isValidStartColor ? color : fallbackColor;

  // Set sizes based on the size prop
  const avatarSizes = {
    sm: 'h-8 w-8',
    md: 'h-10 w-10',
    lg: 'h-12 w-12'
  };
  
  const textSizes = {
    sm: 'text-sm',
    md: 'text-lg',
    lg: 'text-xl'
  };
  
  const messageSizes = {
    sm: 'text-xs',
    md: 'text-xs',
    lg: 'text-sm'
  };

  // Prepare gradient styles using CSS custom properties for better control
  const gradientStyle: React.CSSProperties = isValidStartColor && isValidEndColor ? {
    '--text-gradient': `linear-gradient(135deg, ${color}, ${endColor})`,
    '--fallback-color': displayColor,
  } as React.CSSProperties : {
    color: displayColor,
    textShadow: '0 1px 2px rgba(0,0,0,0.4)',
  };
  
  return (
    <div className={`flex items-center justify-center w-full ${className}`}>
      {/* User avatar */}
      <img 
        src={avatar} 
        alt={username}
        className={`${avatarSizes[size]} rounded-full mr-3 object-cover`} 
      />
      
      <div className="flex flex-col">
        {/* Username with robust gradient handling */}
        <span 
          className={`font-medium ${textSizes[size]} ${isValidStartColor && isValidEndColor ? 'robust-gradient-text' : ''}`}
          style={{
            fontFamily: '"gg sans", sans-serif',
            fontWeight: 600,
            ...gradientStyle,
          }}
        >
          {username}
        </span>
        
        {message && (
          <span 
            className={`text-slate-300 ${messageSizes[size]} mt-1`}
            style={{ fontFamily: '"gg sans", sans-serif' }}
          >
            {message}
          </span>
        )}
      </div>
    </div>
  );
};

export default NameplatePreview;
