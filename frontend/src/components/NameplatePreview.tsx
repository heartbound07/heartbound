import React from 'react';

interface NameplatePreviewProps {
  username?: string;
  avatar?: string;
  color: string;
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
  fallbackColor = '#ffffff',
  message = 'This is the color for the Nameplate!',
  className = '',
  size = 'md'
}) => {
  // Determine if color is valid (starts with #)
  const isValidColor = color && color.startsWith('#');
  const displayColor = isValidColor ? color : fallbackColor;
  
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
        <span 
          className={`font-medium ${textSizes[size]}`} 
          style={{ 
            color: displayColor,
            fontWeight: 600,
            fontFamily: '"gg sans", sans-serif'
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
