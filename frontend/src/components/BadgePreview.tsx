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

  // Check if we have valid gradient colors
  const isValidStartColor = nameplateColor && nameplateColor.startsWith('#');
  const isValidEndColor = nameplateEndColor && nameplateEndColor.startsWith('#');

  return (
    <div className={`flex items-center justify-center w-full p-4 rounded-lg ${className}`} style={{ background: 'transparent', overflow: 'hidden' }}>
      {/* User avatar */}
      <img 
        src={avatar} 
        alt={username}
        className={`${avatarSizes[size]} rounded-full mr-3 object-cover flex-shrink-0`} 
      />
      
      <div className="flex flex-col flex-1 min-w-0">
        {/* Username with badge next to it */}
        <div className="flex items-center gap-2">
          {/* Username with proper gradient handling - isolated container */}
          <div className="flex items-center flex-1 min-w-0">
            {isValidStartColor && isValidEndColor ? (
              // Gradient text with strict containment
              <span 
                className={`font-medium ${textSizes[size]}`}
                style={{
                  fontFamily: '"gg sans", sans-serif',
                  fontWeight: 600,
                  background: `linear-gradient(to right, ${nameplateColor}, ${nameplateEndColor})`,
                  WebkitBackgroundClip: 'text',
                  backgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                  color: 'transparent',
                  display: 'inline-block',
                  backgroundSize: '100% 100%',
                  backgroundRepeat: 'no-repeat',
                  isolation: 'isolate',
                  contain: 'style layout',
                  maxWidth: '100%',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap'
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
                  maxWidth: '100%',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap'
                }}
              >
                {username}
              </span>
            )}
          </div>
          
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
            style={{ 
              fontFamily: '"gg sans", sans-serif',
              maxWidth: '100%',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap'
            }}
          >
            {message}
          </span>
        )}
      </div>
    </div>
  );
};

export default BadgePreview; 