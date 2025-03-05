import React, { useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ProfilePreview } from "@/components/ui/profile/ProfilePreview";
import { X } from "lucide-react";
import { type UserProfileDTO } from "@/config/userService";

interface UserProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  userProfile: UserProfileDTO | null;
}

export function UserProfileModal({ isOpen, onClose, userProfile }: UserProfileModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);

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
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
        >
          <div
            ref={modalRef}
            className="relative"
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
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
