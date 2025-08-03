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
    sm: { fontSize: '14px', height: '20px' },
    md: { fontSize: '18px', height: '28px' },
    lg: { fontSize: '20px', height: '32px' }
  };
  
  const messageSizes = {
    sm: 'text-xs',
    md: 'text-xs',
    lg: 'text-sm'
  };

  // Generate unique ID for gradient definition
  const gradientId = `nameplate-gradient-${Math.random().toString(36).substr(2, 9)}`;
  
  return (
    <div className={`flex items-center justify-center w-full ${className}`}>
      {/* User avatar */}
      <img 
        src={avatar} 
        alt={username}
        className={`${avatarSizes[size]} rounded-full mr-3 object-cover`} 
      />
      
      <div className="flex flex-col">
        {/* Username with preview color */}
        {isValidStartColor && isValidEndColor ? (
          // Robust gradient text using SVG
          <div style={{ height: textSizes[size].height, display: 'flex', alignItems: 'center' }}>
            <svg
              width="auto"
              height={textSizes[size].height}
              style={{ overflow: 'visible' }}
            >
              <defs>
                <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stopColor={color} />
                  <stop offset="100%" stopColor={endColor} />
                </linearGradient>
              </defs>
              <text
                x="0"
                y="70%"
                fill={`url(#${gradientId})`}
                fontSize={textSizes[size].fontSize}
                fontFamily='"gg sans", sans-serif'
                fontWeight="600"
                style={{ filter: 'drop-shadow(0 1px 2px rgba(0,0,0,0.5))' }}
              >
                {username}
              </text>
            </svg>
          </div>
        ) : (
          // Single color text
          <span 
            className={`font-medium`}
            style={{
              fontFamily: '"gg sans", sans-serif',
              fontWeight: 600,
              fontSize: textSizes[size].fontSize,
              color: displayColor,
              textShadow: '0 1px 2px rgba(0,0,0,0.4)',
              height: textSizes[size].height,
              display: 'flex',
              alignItems: 'center'
            }}
          >
            {username}
          </span>
        )}
        
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
