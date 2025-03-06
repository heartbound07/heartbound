import React, { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ProfilePreview } from "@/components/ui/profile/ProfilePreview";
import { X } from "lucide-react";
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

export function UserProfileModal({ isOpen, onClose, userProfile, position, containerRef }: UserProfileModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const [modalPosition, setModalPosition] = useState<Position | null>(null);
  
  // Calculate optimal position when modal opens or position changes
  useEffect(() => {
    if (isOpen && position && modalRef.current) {
      const modalWidth = 320; // Width of ProfilePreview component
      const modalHeight = 450; // Height estimate of ProfilePreview
      const margin = 12; // Small margin from the triggering element
      const viewportMargin = 20; // Minimum margin from viewport edges
      
      // Get viewport dimensions
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;
      
      // Check if we have a container to consider for positioning
      let containerRect: DOMRect | null = null;
      if (containerRef?.current) {
        containerRect = containerRef.current.getBoundingClientRect();
      }
      
      // Step 1: Determine horizontal positioning strategy (right or left of trigger)
      let x = position.x + margin; // Default: position to the right
      
      // Consider container boundaries if available
      const effectiveRightBoundary = containerRect 
        ? Math.min(containerRect.right - modalWidth - viewportMargin, viewportWidth - modalWidth - viewportMargin)
        : viewportWidth - modalWidth - viewportMargin;
      
      const rightOverflow = x > effectiveRightBoundary;
      
      if (rightOverflow) {
        // Not enough space to the right, try positioning to the left
        const leftPosition = position.x - modalWidth - margin;
        
        // Consider container left boundary if available
        const effectiveLeftBoundary = containerRect
          ? Math.max(containerRect.left + viewportMargin, viewportMargin)
          : viewportMargin;
        
        if (leftPosition >= effectiveLeftBoundary) {
          // There's enough space to the left
          x = leftPosition;
        } else {
          // Not enough space on either side, center horizontally relative to trigger
          // but staying within container/viewport bounds
          const minX = containerRect ? containerRect.left + viewportMargin : viewportMargin;
          const maxX = containerRect 
            ? containerRect.right - modalWidth - viewportMargin 
            : viewportWidth - modalWidth - viewportMargin;
          
          x = Math.max(minX, Math.min(position.x - (modalWidth / 2), maxX));
        }
      }
      
      // Step 2: Determine vertical positioning strategy
      // Try to align the top of modal with the trigger element's top position
      let y = position.y;
      
      // Consider container boundaries for vertical positioning
      const effectiveBottomBoundary = containerRect
        ? Math.min(containerRect.bottom - modalHeight - viewportMargin, viewportHeight - modalHeight - viewportMargin)
        : viewportHeight - modalHeight - viewportMargin;
      
      const bottomOverflow = y > effectiveBottomBoundary;
      
      if (bottomOverflow) {
        // Try to position it so the bottom aligns with the container/viewport bottom
        y = effectiveBottomBoundary;
        
        // Consider container top boundary for minimum y
        const effectiveTopBoundary = containerRect
          ? Math.max(containerRect.top + viewportMargin, viewportMargin)
          : viewportMargin;
        
        // If this would push it too high, ensure minimum top margin
        y = Math.max(effectiveTopBoundary, y);
      }
      
      setModalPosition({ x, y });
    } else {
      setModalPosition(null);
    }
  }, [isOpen, position, containerRef]);

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

  // Handle focus trap
  useEffect(() => {
    if (isOpen && modalRef.current) {
      modalRef.current.focus();
    }
  }, [isOpen]);

  if (!userProfile) return null;

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          className="fixed inset-0 z-50 pointer-events-none"
        >
          <motion.div
            ref={modalRef}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            transition={{ duration: 0.2 }}
            className="absolute pointer-events-auto"
            style={{
              left: modalPosition ? `${modalPosition.x}px` : '50%',
              top: modalPosition ? `${modalPosition.y}px` : '50%',
              transform: modalPosition ? 'none' : 'translate(-50%, -50%)'
            }}
            tabIndex={-1}
          >
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
              bannerColor={userProfile.bannerColor || "bg-white/10"}
              bannerUrl={userProfile.bannerUrl}
              name={userProfile.displayName || userProfile.username}
              about={userProfile.about}
              pronouns={userProfile.pronouns}
              user={{ avatar: userProfile.avatar, username: userProfile.username }}
              showEditButton={false}
            />
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
