"use client"

import * as React from "react"
import { ChevronLeft, Users, Crown, LogOut } from "lucide-react"
import { Button } from "@/components/ui/valorant/buttonparty"
import { Badge } from "@/components/ui/valorant/badge"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { useAuth } from "@/contexts/auth/useAuth"
import { useNavigate, useParams } from "react-router-dom"
import { deleteParty, getParty } from "@/contexts/valorant/partyService"

export default function ValorantPartyDetails() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { partyId } = useParams<{ partyId: string }>()
  
  const [party, setParty] = React.useState<any>(null)

  React.useEffect(() => {
    if (partyId) {
      getParty(partyId)
        .then((data) => setParty(data))
        .catch((err) => console.error("Error fetching party:", err))
    }
  }, [partyId])

  const handleLeaveGroup = async () => {
    if (!partyId) {
      console.error("Party ID is not available")
      return
    }
    try {
      await deleteParty(partyId)
      navigate("/dashboard/valorant")
    } catch (error) {
      console.error("Error leaving group:", error)
    }
  }

  return (
    <TooltipProvider>
      <div className="min-h-screen bg-[#0F1923] text-white font-sans">
        {/* Background elements */}
        <div className="fixed inset-0 bg-[#0F1923] z-0">
          <div className="absolute inset-0 bg-gradient-to-br from-[#FF4655]/10 to-transparent opacity-50"></div>
          <div className="absolute top-0 left-0 w-full h-64 bg-gradient-to-b from-[#1F2731] to-transparent opacity-30"></div>
        </div>

        {/* Content */}
        <div className="relative z-10">
          {/* Header */}
          <div className="p-6">
            <div className="absolute top-4 left-4 z-20">
              <Button
                variant="ghost"
                className="bg-transparent text-white gap-2 hover:bg-white/10 transition-all duration-300 rounded-full"
                onClick={() => navigate("/dashboard/valorant")}
              >
                <ChevronLeft className="h-4 w-4" />
                <span className="font-medium">Back to list</span>
              </Button>
            </div>
          </div>

          {/* Main Content */}
          <div className="max-w-4xl mx-auto px-6">
            <div className="space-y-8">
              {/* Game Info with Dynamic Badges */}
              <div className="flex flex-wrap gap-3 items-center backdrop-blur-sm bg-white/5 p-4 rounded-2xl">
                <Badge
                  variant="secondary"
                  className="bg-white/10 hover:bg-white/20 transition-colors text-white px-4 py-1.5 text-sm rounded-full"
                >
                  {party?.matchType || "N/A"}
                </Badge>
                <Badge
                  variant="secondary"
                  className="bg-white/10 hover:bg-white/20 transition-colors text-white px-4 py-1.5 text-sm rounded-full"
                >
                  NA
                </Badge>
                <Badge
                  variant="secondary"
                  className="bg-white/10 hover:bg-white/20 transition-colors text-white px-4 py-1.5 text-sm rounded-full"
                >
                  {party?.teamSize || "N/A"}
                </Badge>
                <Badge
                  variant="secondary"
                  className="bg-white/10 hover:bg-white/20 transition-colors text-white px-4 py-1.5 text-sm rounded-full"
                >
                  {party?.voicePreference || "N/A"}
                </Badge>
                <Badge
                  variant="secondary"
                  className="bg-white/10 hover:bg-white/20 transition-colors text-white px-4 py-1.5 text-sm rounded-full"
                >
                  {party?.ageRestriction || "N/A"}
                </Badge>
                <div className="ml-auto flex gap-3">
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="secondary"
                        size="icon"
                        onClick={handleLeaveGroup}
                        className="bg-white/10 hover:bg-white/20 transition-all duration-300 rounded-full"
                      >
                        <LogOut className="h-4 w-4" />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Leave group</TooltipContent>
                  </Tooltip>
                </div>
              </div>

              {/* Player Slots Container */}
              <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-purple-900/50 to-blue-900/50 p-8 shadow-2xl">
                <div className="absolute inset-0 bg-zinc-950/70 backdrop-blur-sm" />
                <div className="relative">
                  <div className="flex items-center justify-between mb-6">
                    <h2 className="text-2xl font-semibold text-white/90">Party Members</h2>
                    <Badge
                      variant="secondary"
                      className="bg-white/10 text-white px-3 py-1 rounded-full flex items-center gap-2"
                    >
                      <Users className="h-4 w-4" />
                      <span>1 / {party?.maxPlayers || "?"}</span>
                    </Badge>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-6">
                    {/* Active Player Slot for Party Leader */}
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <div className="relative group">
                          <div className="absolute -inset-0.5 bg-gradient-to-r from-purple-600 to-blue-600 rounded-full opacity-75 group-hover:opacity-100 transition duration-300 blur" />
                          <div className="relative w-full aspect-square rounded-full border-2 border-white/20 p-1 bg-zinc-900">
                            <div className="w-full h-full rounded-full overflow-hidden">
                              <img
                                src={user?.avatar || "https://v0.dev/placeholder.svg?height=400&width=400"}
                                alt="Player avatar"
                                className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
                              />
                            </div>
                            <div className="absolute -top-1 -right-1">
                              <Crown className="h-5 w-5 text-yellow-500" />
                            </div>
                          </div>
                          <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-zinc-800/90 px-3 py-1 rounded-full text-sm font-medium shadow-lg">
                            {user?.username || "You"}
                          </div>
                        </div>
                      </TooltipTrigger>
                      <TooltipContent>Party Leader</TooltipContent>
                    </Tooltip>
                    {/* Empty Slots */}
                    {(party?.maxPlayers ? Array.from({ length: party.maxPlayers - 1 }) : []).map((_, i) => (
                      <Tooltip key={i}>
                        <TooltipTrigger asChild>
                          <div className="relative group cursor-pointer">
                            <div className="w-full aspect-square rounded-full border-2 border-purple-500/20 p-1 bg-zinc-800/50 transition-all duration-300 hover:border-purple-500/40 hover:bg-zinc-800/70">
                              <div className="w-full h-full rounded-full bg-zinc-700/30 flex items-center justify-center transition-all duration-300 group-hover:bg-zinc-700/50">
                                <div className="w-8 h-8 text-purple-500/40 group-hover:text-purple-500/60 transition-colors duration-300 flex items-center justify-center text-2xl font-light">
                                  +
                                </div>
                              </div>
                            </div>
                          </div>
                        </TooltipTrigger>
                        <TooltipContent>Click to invite player</TooltipContent>
                      </Tooltip>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}

