import React, { useState, useRef, useEffect, useMemo } from "react";
import { motion } from "framer-motion"
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/profile/avatar";
import { Button } from "@/components/ui/profile/button";
import { UserIcon, ChevronDown, ChevronUp } from "lucide-react";
import { useNavigate } from "react-router-dom";

interface ProfilePreviewProps {
  bannerColor: string;
  bannerUrl?: string;
  name?: string;
  about?: string;
  pronouns?: string;
  user?: { avatar?: string; username?: string } | null;
  onClick?: () => void;
  showEditButton?: boolean;
  equippedBadgeIds?: string[];
  badgeMap?: Record<string, string>;
  badgeNames?: Record<string, string>;
}

export function ProfilePreview({ 
  bannerColor, 
  bannerUrl,
  name, 
  about, 
  pronouns,
  user, 
  onClick,
  showEditButton = true,
  equippedBadgeIds = [],
  badgeMap = {},
  badgeNames = {}
}: ProfilePreviewProps) {
  const navigate = useNavigate();
  const [isExpanded, setIsExpanded] = useState(false);
  const [hasOverflow, setHasOverflow] = useState(false);
  const bioRef = useRef<HTMLParagraphElement>(null);
  const [hoveredBadgeId, setHoveredBadgeId] = useState<string | null>(null);
  
  // Add debug logging on component mount and when props change
  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') {
      console.debug('ProfilePreview badgeData:', { 
        equippedBadgeIds, 
        badgeMapKeys: Object.keys(badgeMap),
        badgeMap 
      });
      
      // Check for missing badge URLs
      const missingBadges = equippedBadgeIds.filter(id => !badgeMap[id]);
      if (missingBadges.length > 0) {
        console.warn(`Missing badge URLs for ${missingBadges.length} badge(s):`, missingBadges);
      }
    }
  }, [equippedBadgeIds, badgeMap]);
  
  // Limit the number of badges to display before showing "+X more"
  const MAX_VISIBLE_BADGES = 5;
  
  // Add useMemo for badge processing
  const { visibleBadges, extraBadgesCount } = useMemo(() => {
    const visible = equippedBadgeIds.slice(0, MAX_VISIBLE_BADGES);
    const extra = Math.max(0, equippedBadgeIds.length - MAX_VISIBLE_BADGES);
    return { visibleBadges: visible, extraBadgesCount: extra };
  }, [equippedBadgeIds, MAX_VISIBLE_BADGES]);
  
  // Detect if text overflows 2 lines
  useEffect(() => {
    const checkOverflow = () => {
      if (bioRef.current && about) {
        const element = bioRef.current;
        // Line height is approximately 1.5em for text-xs
        const lineHeight = parseFloat(getComputedStyle(element).fontSize) * 1.5;
        // If element height > 2 lines, it's overflowing
        setHasOverflow(element.scrollHeight > lineHeight * 2);
      } else {
        setHasOverflow(false);
      }
    };
    
    checkOverflow();
    // Recheck on window resize
    window.addEventListener('resize', checkOverflow);
    return () => window.removeEventListener('resize', checkOverflow);
  }, [about]);
  
  const handleEditClick = (e: React.MouseEvent) => {
    e.stopPropagation(); // Prevent the click from bubbling up
    navigate('/dashboard/profile');
  };

  const toggleBioExpansion = (e: React.MouseEvent) => {
    e.stopPropagation(); // Prevent clicking the toggle from triggering the parent onClick
    setIsExpanded(!isExpanded);
  };

  // Use a trusted image URL or default to placeholder
  const safeImageUrl = (url: string | undefined) => {
    if (!url) return '/images/placeholder-badge.png';
    
    try {
      const parsedUrl = new URL(url);
      // Only allow images from trusted domains 
      if (['your-cdn.com', 'res.cloudinary.com'].includes(parsedUrl.hostname)) {
        return url;
      }
      return '/images/placeholder-badge.png';
    } catch (e) {
      return '/images/placeholder-badge.png';
    }
  };

  return (
    <div className="w-[300px]" onClick={onClick}>
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="overflow-hidden rounded-xl border border-white/10 bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] shadow-lg"
      >
        <div 
          className={`relative h-32 ${!bannerUrl && bannerColor.startsWith('bg-') ? bannerColor : ''}`}
          style={
            bannerUrl 
              ? { backgroundImage: `url(${bannerUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' } 
              : !bannerColor.startsWith('bg-') 
                ? { backgroundColor: bannerColor } 
                : {}
          }
        >
          <div className="absolute bottom-0 left-4 translate-y-1/2">
            <div className="relative">
              <Avatar className="h-20 w-20 border-4 border-white/10 shadow-xl">
                <AvatarImage src={user?.avatar || "/placeholder.svg"} />
                <AvatarFallback>{user?.username ? user.username.charAt(0).toUpperCase() : "P"}</AvatarFallback>
              </Avatar>
            </div>
          </div>
          
          {showEditButton && (
            <div className="absolute top-3 right-3">
              <Button 
                size="sm" 
                className="bg-white/20 hover:bg-white/30 text-white flex items-center gap-1 px-2 py-1 text-xs"
                onClick={handleEditClick}
              >
                <UserIcon size={14} />
                Edit
              </Button>
            </div>
          )}
        </div>
        <div className="p-4 pt-12">
          {/* Display name with badges next to it */}
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold truncate">{name || "Display Name"}</h2>
            
            {/* Badge display - relocated to appear next to display name */}
            {equippedBadgeIds && equippedBadgeIds.length > 0 && (
              <div className="flex flex-row items-center gap-1 transition-all relative">
                {visibleBadges.map(badgeId => {
                  const badgeUrl = badgeMap[badgeId];
                  // Use the badge name from the badgeNames map if available, fallback to a generic name
                  const badgeName = badgeNames[badgeId] || "Badge";
                  
                  return (
                    <div key={badgeId} className="relative">
                      <button 
                        type="button"
                        className="badge-wrapper p-0 m-0 border-0 bg-transparent cursor-pointer focus:outline-none"
                        onMouseEnter={() => setHoveredBadgeId(badgeId)}
                        onMouseLeave={() => setHoveredBadgeId(null)}
                        aria-label={`Badge: ${badgeName}`}
                      >
                        <img 
                          src={safeImageUrl(badgeUrl)} 
                          alt={`Badge`}
                          className="w-5 h-5 rounded-full object-cover"
                        />
                      </button>
                      
                      {/* Custom tooltip implementation */}
                      {hoveredBadgeId === badgeId && (
                        <div 
                          className="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 px-3 py-1.5 bg-gray-800 text-white text-sm font-semibold rounded-md shadow-md border border-gray-700 z-[9999] whitespace-nowrap"
                        >
                          {badgeName}
                          {/* Tooltip arrow */}
                          <div className="absolute top-full left-1/2 transform -translate-x-1/2 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-gray-800"></div>
                        </div>
                      )}
                    </div>
                  );
                })}
                
                {extraBadgesCount > 0 && (
                  <div className="relative">
                    <button 
                      type="button"
                      className="h-5 w-5 p-0 m-0 border-0 rounded-full bg-white/20 text-white flex items-center justify-center text-xs font-bold hover:bg-white/30 transition-colors cursor-pointer focus:outline-none"
                      onMouseEnter={() => setHoveredBadgeId('extra')}
                      onMouseLeave={() => setHoveredBadgeId(null)}
                      aria-label="More badges"
                    >
                      +{extraBadgesCount}
                    </button>
                    
                    {/* Custom tooltip for extra badges */}
                    {hoveredBadgeId === 'extra' && (
                      <div 
                        className="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 px-3 py-1.5 bg-gray-800 text-white text-sm font-semibold rounded-md shadow-md border border-gray-700 z-[9999] whitespace-nowrap"
                      >
                        {extraBadgesCount} more badge(s)
                        {/* Tooltip arrow */}
                        <div className="absolute top-full left-1/2 transform -translate-x-1/2 w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-gray-800"></div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
          
          {/* Username and pronouns on a row below - without badges now */}
          <div className="flex items-center gap-2 mt-1 mb-3">
            <span className="text-xs text-white/80 truncate">{user?.username || "Guest"}</span>
            {pronouns && <span className="text-xs text-white/60 truncate">• {pronouns}</span>}
          </div>
          
          <div className="text-white/80 w-full">
            {about && (
              <>
                <div 
                  className={`relative ${isExpanded ? '' : 'max-h-10'} overflow-hidden transition-all duration-300`}
                >
                  <p 
                    ref={bioRef}
                    className={`text-xs whitespace-normal break-words ${!isExpanded ? 'line-clamp-2' : ''}`}
                  >
                    {about}
                  </p>
                </div>
                
                {hasOverflow && (
                  <button 
                    onClick={toggleBioExpansion}
                    className="flex items-center text-xs mt-1 text-white/60 hover:text-white/90 transition-colors"
                  >
                    {isExpanded ? (
                      <>
                        <span>Show less</span>
                        <ChevronUp size={14} className="ml-1" />
                      </>
                    ) : (
                      <>
                        <span>Show more</span>
                        <ChevronDown size={14} className="ml-1" />
                      </>
                    )}
                  </button>
                )}
              </>
            )}
          </div>
        </div>
      </motion.div>
    </div>
  );
}