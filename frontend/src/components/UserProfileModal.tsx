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
  position?: Position | null; // New prop for positioning
}

export function UserProfileModal({ isOpen, onClose, userProfile, position }: UserProfileModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const [modalPosition, setModalPosition] = useState<Position | null>(null);

  // Calculate optimal position when modal opens or position changes
  useEffect(() => {
    if (isOpen && position && modalRef.current) {
      const modalWidth = 300; // Width of ProfilePreview component
      const modalHeight = 400; // Approximate height of ProfilePreview
      const padding = 16; // Padding from edge
      
      // Get viewport dimensions
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;
      
      // Calculate position to ensure modal stays in viewport
      let x = position.x;
      let y = position.y;
      
      // Adjust horizontal position if needed
      if (x + modalWidth + padding > viewportWidth) {
        x = x - modalWidth - padding; // Position to the left of the slot
      } else {
        x = x + padding; // Position to the right of the slot
      }
      
      // Adjust vertical position if needed
      if (y + modalHeight + padding > viewportHeight) {
        y = viewportHeight - modalHeight - padding; // Position above viewport bottom
      }
      
      // Ensure modal is not positioned above the top of viewport
      y = Math.max(padding, y);
      
      setModalPosition({ x, y });
    } else {
      setModalPosition(null);
    }
  }, [isOpen, position]);

  // Handle clicking outside to close
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (modalRef.current && !modalRef.current.contains(event.target as Node)) {
        onClose();
      }
    }

    // Handle escape key press
    function handleEscKey(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
      }
    }

    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("keydown", handleEscKey);
    }

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
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
