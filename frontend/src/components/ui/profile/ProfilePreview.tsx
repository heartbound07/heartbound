import React, { useState, useRef, useEffect } from "react";
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
}

export function ProfilePreview({ 
  bannerColor, 
  bannerUrl,
  name, 
  about, 
  pronouns,
  user, 
  onClick,
  showEditButton = true
}: ProfilePreviewProps) {
  const navigate = useNavigate();
  const [isExpanded, setIsExpanded] = useState(false);
  const [hasOverflow, setHasOverflow] = useState(false);
  const bioRef = useRef<HTMLParagraphElement>(null);
  
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
          <div className="mb-2 flex items-center gap-2">
            <h2 className="text-lg font-bold truncate max-w-[140px]">{name || "Display Name"}</h2>
            <span className="text-xs text-white/80 truncate max-w-[70px]">{user?.username || "Guest"}</span>
            {pronouns && <span className="text-xs text-white/60 truncate max-w-[60px]">• {pronouns}</span>}
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
                        <span>View full bio</span>
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