import React from "react";
import { motion } from "framer-motion";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/profile/avatar";

interface ProfilePreviewProps {
  bannerColor: string;
  name?: string;
  about?: string;
  user?: { avatar?: string; username?: string } | null;
  onClick?: () => void;
}

export function ProfilePreview({ bannerColor, name, about, user, onClick }: ProfilePreviewProps) {
  return (
    <div className="w-full lg:w-1/2" onClick={onClick}>
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="sticky top-6 overflow-hidden rounded-2xl border border-white/10 bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] shadow-lg"
      >
        <div className={`relative h-32 ${bannerColor}`}>
          <div className="absolute bottom-0 left-4 translate-y-1/2">
            <div className="relative">
              <Avatar className="h-20 w-20 border-4 border-white/10 shadow-xl">
                <AvatarImage src={user?.avatar || "/placeholder.svg"} />
                <AvatarFallback>{user?.username ? user.username.charAt(0).toUpperCase() : "P"}</AvatarFallback>
              </Avatar>
            </div>
          </div>
        </div>
        <div className="p-4 pt-12">
          <div className="mb-4 flex items-center gap-2">
            <h2 className="text-xl font-bold">{name || "Display Name"}</h2>
            <span className="text-sm text-white/80">{user?.username || "Guest"}</span>
          </div>
          <p className="text-sm text-white/80">
            {about || "Your about me section will appear here..."}
          </p>
        </div>
      </motion.div>
    </div>
  );
}