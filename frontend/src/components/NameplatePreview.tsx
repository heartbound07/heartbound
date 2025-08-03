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
    <div className={`flex items-center justify-center w-full ${className}`} style={{ background: 'transparent', overflow: 'hidden' }}>
      {/* User avatar */}
      <img 
        src={avatar} 
        alt={username}
        className={`${avatarSizes[size]} rounded-full mr-3 object-cover flex-shrink-0`} 
      />
      
      <div className="flex flex-col flex-1 min-w-0">
        {/* Username with preview color - isolated gradient container */}
        <div className="flex items-center">
          {isValidStartColor && isValidEndColor ? (
            // Gradient text with strict containment
            <span 
              className={`font-medium ${textSizes[size]}`}
              style={{
                fontFamily: '"gg sans", sans-serif',
                fontWeight: 600,
                background: `linear-gradient(135deg, ${color}, ${endColor})`,
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
                color: displayColor,
                textShadow: '0 1px 2px rgba(0,0,0,0.4)',
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

export default NameplatePreview;
