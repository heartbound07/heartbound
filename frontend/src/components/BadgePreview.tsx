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
    sm: 'text-sm',
    md: 'text-lg',
    lg: 'text-xl'
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
            // Gradient text using CSS mask approach
            <span 
              className={`font-medium ${textSizes[size]} gradient-text-container`}
              style={{
                fontFamily: '"gg sans", sans-serif',
                fontWeight: 600,
                background: `linear-gradient(to right, ${nameplateColor}, ${nameplateEndColor})`,
                WebkitBackgroundClip: 'text',
                backgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                color: 'transparent',
                display: 'inline-block',
              }}
            >
              {username}
            </span>
          ) : (
            // Single color text
            <span 
              className={`font-medium ${textSizes[size]}`}
              style={{
                fontFamily: '"gg sans", sans-serif',
                fontWeight: 600,
                color: nameplateColor || '#ffffff',
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