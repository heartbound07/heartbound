import React, { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ProfilePreview } from "@/components/ui/profile/ProfilePreview";
import { Loader2, X } from "lucide-react";
import { type UserProfileDTO } from "@/config/userService";

interface Position {
  x: number;
  y: number;
}

interface UserProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  userProfile: UserProfileDTO | null;
  position?: Position | null; // Position for contextual positioning
  containerRef?: React.RefObject<HTMLElement>; // Added container reference prop
}

export function UserProfileModal({ isOpen, onClose, userProfile, position }: UserProfileModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const [modalPosition, setModalPosition] = useState<Position | null>(null);
  
  // Calculate optimal position when modal opens or position changes
  useEffect(() => {
    if (isOpen && position && modalRef.current) {
      const modalRect = modalRef.current.getBoundingClientRect();
      const modalWidth = modalRect.width > 0 ? modalRect.width : 320; // Fallback width
      const modalHeight = modalRect.height > 0 ? modalRect.height : 450; // Fallback height
      const margin = 12; // Small margin from the triggering element
      const viewportMargin = 20; // Minimum margin from viewport edges
      
      // Get viewport dimensions
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;
      
      // Always position relative to the viewport, not the container
      // Step 1: Determine horizontal positioning strategy
      let x = position.x + margin; // Default: position to the right
      
      // Check if we're overflowing right edge of viewport
      if (x + modalWidth + viewportMargin > viewportWidth) {
        // Not enough space to the right, try to position left
        const leftPosition = position.x - modalWidth - margin;
        
        if (leftPosition >= viewportMargin) {
          // There's enough space to the left
          x = leftPosition;
        } else {
          // Center horizontally if there's not enough space on either side
          x = Math.max(viewportMargin, Math.min(position.x - (modalWidth / 2), viewportWidth - modalWidth - viewportMargin));
        }
      }
      
      // Step 2: Determine vertical positioning strategy
      let y = position.y;
      
      // Check if we're overflowing bottom edge of viewport
      if (y + modalHeight + viewportMargin > viewportHeight) {
        // Not enough space below, move it up as needed
        y = Math.max(viewportMargin, viewportHeight - modalHeight - viewportMargin);
      }
      
      setModalPosition({ x, y });
    }
  }, [isOpen, position, userProfile]);

  // Handle closing when clicking outside
  useEffect(() => {
    const handleOutsideClick = (event: MouseEvent) => {
      if (modalRef.current && !modalRef.current.contains(event.target as Node)) {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener("mousedown", handleOutsideClick);
    }

    return () => {
      document.removeEventListener("mousedown", handleOutsideClick);
    };
  }, [isOpen, onClose]);

  // Handle Escape key to close modal
  useEffect(() => {
    const handleEscKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener("keydown", handleEscKey);
    }

    return () => {
      document.removeEventListener("keydown", handleEscKey);
    };
  }, [isOpen, onClose]);

  // Handle focus trap - focus the close button instead of container
  useEffect(() => {
    if (isOpen && userProfile && modalRef.current) {
      const closeButton = modalRef.current.querySelector('button[aria-label="Close profile preview"]') as HTMLButtonElement;
      if (closeButton) {
        closeButton.focus();
      }
    }
  }, [isOpen, userProfile]);

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          className="fixed inset-0 pointer-events-none"
          style={{ zIndex: 1600 }}
        >
          <motion.div
            ref={modalRef}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            transition={{ duration: 0.2 }}
            className="absolute pointer-events-auto shadow-2xl user-profile-modal-container"
            style={{
              left: modalPosition ? `${modalPosition.x}px` : '50%',
              top: modalPosition ? `${modalPosition.y}px` : '50%',
              transform: modalPosition ? 'none' : 'translate(-50%, -50%)'
            }}
          >
            {userProfile ? (
              <>
                <motion.button
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.8 }}
                  transition={{ delay: 0.1, duration: 0.2 }}
                  onClick={onClose}
                  className="absolute top-2 right-2 z-10 flex items-center justify-center w-8 h-8 bg-black/40 rounded-full text-white hover:bg-black/60 transition-colors"
                  aria-label="Close profile preview"
                >
                  <X size={16} />
                </motion.button>
                
                <ProfilePreview
                  bannerColor={userProfile.bannerColor || "bg-primary"}
                  bannerUrl={userProfile.bannerUrl}
                  name={userProfile.displayName || userProfile.username}
                  about={userProfile.about}
                  pronouns={userProfile.pronouns}
                  user={{ avatar: userProfile.avatar, username: userProfile.username }}
                  equippedBadgeIds={userProfile.equippedBadgeId ? [userProfile.equippedBadgeId] : []}
                  badgeMap={userProfile.equippedBadgeId && userProfile.badgeUrl ? { [userProfile.equippedBadgeId]: userProfile.badgeUrl } : {}}
                  badgeNames={userProfile.equippedBadgeId && userProfile.badgeName ? { [userProfile.equippedBadgeId]: userProfile.badgeName } : {}}
                  showEditButton={false}
                />
              </>
            ) : (
              // Loading state with fixed dimensions matching ProfilePreview
              <div className="w-80 h-[450px] flex items-center justify-center bg-slate-900/80 backdrop-blur-sm border border-slate-700 rounded-lg text-white">
                <Loader2 className="w-8 h-8 animate-spin text-slate-400" />
              </div>
            )}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
