import React from 'react';

interface BadgePreviewProps {
  username?: string;
  avatar?: string;
  badgeUrl: string;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
  message?: string;
}

/**
 * A reusable component that displays a preview of how a badge will appear 
 * next to a user's avatar and username.
 */
export const BadgePreview: React.FC<BadgePreviewProps> = ({
  username = 'Username',
  avatar = '/default-avatar.png',
  badgeUrl,
  className = '',
  size = 'md',
  message = 'This is how the badge will appear!'
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
  
  return (
    <div className={`flex items-center p-4 bg-slate-800 rounded-lg ${className}`}>
      {/* User avatar */}
      <img 
        src={avatar} 
        alt={username}
        className={`${avatarSizes[size]} rounded-full mr-3 object-cover`} 
      />
      
      <div className="flex flex-col">
        {/* Username with badge next to it */}
        <div className="flex items-center gap-2">
          <span 
            className={`font-medium text-white ${textSizes[size]}`} 
            style={{ 
              fontWeight: 600,
              fontFamily: '"gg sans", sans-serif'
            }}
          >
            {username}
          </span>
          
          {/* Badge icon */}
          <img 
            src={badgeUrl} 
            alt="Badge"
            className={`${badgeSizes[size]} rounded-full object-cover flex-shrink-0`}
            style={{
              border: '1px solid rgba(255, 255, 255, 0.2)',
              background: 'rgba(0, 0, 0, 0.1)'
            }}
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