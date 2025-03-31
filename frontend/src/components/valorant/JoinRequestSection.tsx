"use client"

import * as React from "react"
import { Check, Loader2, X as XIcon } from "lucide-react"
import { Button } from "@/components/ui/valorant/buttonparty"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { type UserProfileDTO } from "@/config/userService"
import { motion } from "framer-motion"

interface JoinRequestSectionProps {
  isUserLeader: boolean;
  joinRequests: string[];
  joinRequestProfiles: Record<string, UserProfileDTO>;
  placeholderAvatar: string;
  isProcessingRequest: boolean;
  handleAcceptJoinRequest: (userId: string) => void;
  handleRejectJoinRequest: (userId: string) => void;
  onProfileView?: (userId: string, event: React.MouseEvent) => void;
}

export function JoinRequestSection({
  isUserLeader,
  joinRequests,
  joinRequestProfiles,
  placeholderAvatar,
  isProcessingRequest,
  handleAcceptJoinRequest,
  handleRejectJoinRequest,
  onProfileView
}: JoinRequestSectionProps) {
  if (!isUserLeader || !joinRequests?.length) {
    return null;
  }

  return (
    <div className="mt-4">
      <h4 className="text-sm font-medium mb-3 text-[#FF4655] flex items-center">
        <span className="mr-2 h-1.5 w-1.5 rounded-full bg-[#FF4655]"></span>
        Join Requests ({joinRequests.length})
      </h4>
      <div className="space-y-2.5">
        {joinRequests.map(userId => (
          <motion.div 
            key={userId} 
            initial={{ opacity: 0, y: 5 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -5 }}
            transition={{ duration: 0.2 }}
            className="flex items-center justify-between bg-[#1A242E] p-3 rounded-lg border border-[#384957]/30 shadow-sm hover:shadow-md hover:border-[#384957]/50 transition-all duration-200"
          >
            <div 
              className="flex items-center gap-3 cursor-pointer" 
              onClick={(event) => onProfileView && onProfileView(userId, event)}
            >
              <Avatar className="h-8 w-8 ring-2 ring-[#FF4655]/20 ring-offset-1 ring-offset-[#1A242E]">
                <AvatarImage 
                  src={joinRequestProfiles[userId]?.avatar || placeholderAvatar} 
                  alt={joinRequestProfiles[userId]?.username || "User"} 
                  className="object-cover"
                />
                <AvatarFallback className="bg-[#273645] text-[#8B97A4]">
                  {joinRequestProfiles[userId]?.username?.charAt(0) || "U"}
                </AvatarFallback>
              </Avatar>
              <div>
                <span className="text-sm font-medium text-white">
                  {joinRequestProfiles[userId]?.username || "Unknown User"}
                </span>
                <p className="text-xs text-[#8B97A4]">Wants to join your party</p>
              </div>
            </div>
            <div className="flex gap-2">
              <Button 
                onClick={() => handleAcceptJoinRequest(userId)}
                className="bg-green-500/10 hover:bg-green-500 text-green-400 hover:text-white px-3 py-1 h-8 rounded-md border border-green-500/20 transition-all duration-200 shadow-md hover:shadow-green-500/10 text-xs font-medium"
                aria-label="Accept join request"
                title="Accept"
                disabled={isProcessingRequest}
              >
                {isProcessingRequest ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
                ) : (
                  <Check className="h-3.5 w-3.5 mr-1" />
                )}
                Accept
              </Button>
              <Button 
                onClick={() => handleRejectJoinRequest(userId)}
                className="bg-red-500/10 hover:bg-red-500 text-red-400 hover:text-white px-3 py-1 h-8 rounded-md border border-red-500/20 transition-all duration-200 shadow-md hover:shadow-red-500/10 text-xs font-medium"
                aria-label="Reject join request"
                title="Reject"
                disabled={isProcessingRequest}
              >
                {isProcessingRequest ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
                ) : (
                  <XIcon className="h-3.5 w-3.5 mr-1" />
                )}
                Decline
              </Button>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
}
