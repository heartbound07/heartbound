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
  // Determine if color is valid (starts with #)
  const isValidStartColor = color && color.startsWith('#');
  const isValidEndColor = endColor && endColor.startsWith('#');
  const displayColor = isValidStartColor ? color : fallbackColor;

  const style: React.CSSProperties = {
    fontWeight: 600,
    fontFamily: '"gg sans", sans-serif',
  };

  if (isValidStartColor && isValidEndColor) {
    // Apply gradient with proper cross-browser support
    style.background = `linear-gradient(135deg, ${color}, ${endColor})`;
    style.backgroundClip = 'text';
    style.WebkitBackgroundClip = 'text';
    style.color = 'transparent';
    style.WebkitTextFillColor = 'transparent';
    style.display = 'inline-block'; // Required for background-clip: text to work properly
    style.filter = 'drop-shadow(0 1px 2px rgba(0,0,0,0.5))';
    // Prevent text selection issues with gradient
    style.userSelect = 'none';
    style.WebkitUserSelect = 'none';
  } else {
    style.color = displayColor;
    style.textShadow = '0 1px 2px rgba(0,0,0,0.4)';
  }
  
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
        {/* Username with preview color - wrapped in container to prevent gradient bleeding */}
        <div className="relative">
          <span 
            className={`font-medium ${textSizes[size]}`} 
            style={style}
          >
            {username}
          </span>
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

export default NameplatePreview;
