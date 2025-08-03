import React from 'react';

interface BadgePreviewProps {
  username?: string;
  avatar?: string;
  badgeUrl: string;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
  message?: string;
  nameplateColor?: string; // Add nameplate color support
  nameplateEndColor?: string; // Add nameplate gradient end color support
}

/**
 * A reusable component that displays a preview of how a badge will appear 
 * next to a user's avatar and username.
 */
export const BadgePreview: React.FC<BadgePreviewProps> = ({
  username = 'Username',
  avatar = '/images/default-avatar.png',
  badgeUrl,
  className = '',
  size = 'md',
  message = 'This is how the badge will appear!',
  nameplateColor, // Add nameplate color prop
  nameplateEndColor // Add nameplate end color prop
}) => {
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
  
  const badgeSizes = {
    sm: 'h-4 w-4',
    md: 'h-5 w-5',
    lg: 'h-6 w-6'
  };
  
  const messageSizes = {
    sm: 'text-xs',
    md: 'text-xs',
    lg: 'text-sm'
  };

  // Helper function to validate hex colors - same as ItemPreview.tsx
  const isValidHexColor = (color: string | null | undefined): boolean => {
    return color ? color.startsWith('#') && /^#[0-9A-F]{6}$/i.test(color) : false;
  };

  // Check if we have valid gradient colors using strict validation
  const isValidStartColor = isValidHexColor(nameplateColor);
  const isValidEndColor = isValidHexColor(nameplateEndColor);

  // Generate unique ID for gradient definition
  const gradientId = `badge-gradient-${Math.random().toString(36).substr(2, 9)}`;

  return (
    <div className={`flex items-center justify-center w-full p-4 rounded-lg ${className}`}>
      {/* User avatar */}
      <img 
        src={avatar} 
        alt={username}
        className={`${avatarSizes[size]} rounded-full mr-3 object-cover`} 
      />
      
      <div className="flex flex-col">
        {/* Username with badge next to it */}
        <div className="flex items-center gap-2">
          {/* Username with proper gradient handling */}
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
                    <stop offset="0%" stopColor={nameplateColor} />
                    <stop offset="100%" stopColor={nameplateEndColor} />
                  </linearGradient>
                </defs>
                <text
                  x="0"
                  y="50%"
                  dominantBaseline="middle"
                  fill={`url(#${gradientId})`}
                  fontSize={textSizes[size].fontSize}
                  fontFamily='"gg sans", sans-serif'
                  fontWeight="600"
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
                color: nameplateColor || '#ffffff',
                height: textSizes[size].height,
                display: 'flex',
                alignItems: 'center'
              }}
            >
              {username}
            </span>
          )}
          
          {/* Badge icon */}
          <img 
            src={badgeUrl} 
            alt="Badge"
            className={`${badgeSizes[size]} rounded-full object-cover flex-shrink-0`}
          />
        </div>
        
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

export default BadgePreview; 