"use client"

import React from "react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { Zap, Trophy, Mic, MapPin, Plus, Users } from "lucide-react"

interface PlayerSlot {
  id: number
  filled: boolean
  number?: string
  avatar?: string
}

interface ListingProps {
  party: any // Ideally, replace `any` with a specific Party type
}

export default function Listing({ party }: ListingProps) {
  const [slots] = useState<PlayerSlot[]>([
    { id: 1, filled: true, number: "17", avatar: "/placeholder.svg" },
    { id: 2, filled: false },
  ])

  return (
    <div className="w-full max-w-2xl mx-auto bg-zinc-900 rounded-lg overflow-hidden shadow-lg">
      <div className="px-4 py-3 bg-gradient-to-r from-zinc-900 to-zinc-800 border-b border-zinc-700/50">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-zinc-400 hover:text-white hover:bg-zinc-700/50 transition-all duration-200"
                  >
                    <Zap className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>
                  <p>Quick Match</p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-zinc-400 hover:text-white hover:bg-zinc-700/50 transition-all duration-200"
                  >
                    <Trophy className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>
                  <p>Tournaments</p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-zinc-400 hover:text-white hover:bg-zinc-700/50 transition-all duration-200"
                  >
                    <Mic className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>
                  <p>Voice Chat</p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="flex items-center gap-1 bg-zinc-800/50 px-2 py-1 rounded-full hover:bg-zinc-700/50 transition-all duration-200 cursor-pointer">
                  <MapPin className="h-3 w-3 text-zinc-400" />
                  <span className="text-xs font-medium text-zinc-300">NA-WEST</span>
                </div>
              </TooltipTrigger>
              <TooltipContent>
                <p>Your current region</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
        <h1 className="text-sm font-semibold text-zinc-100 tracking-wide mt-2">Looking for players...</h1>
      </div>

      <div className="p-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center space-x-2">
            <Users className="h-4 w-4 text-zinc-400" />
            <span className="text-xs font-medium text-zinc-300">Players</span>
          </div>
          <span className="text-xs font-medium text-zinc-400">1/2</span>
        </div>
        <div className="flex space-x-2 mb-4">
          {slots.map((slot) => (
            <TooltipProvider key={slot.id}>
              <Tooltip>
                <TooltipTrigger asChild>
                  <div
                    className={`relative w-10 h-10 rounded-full ${
                      slot.filled
                        ? "bg-zinc-800 ring-1 ring-violet-500/50"
                        : "border border-dashed border-zinc-700 hover:border-zinc-500"
                    } transition-all duration-200 group cursor-pointer flex items-center justify-center`}
                  >
                    {slot.filled ? (
                      <>
                        <Avatar className="h-full w-full">
                          <AvatarImage src={slot.avatar} />
                          <AvatarFallback>P{slot.id}</AvatarFallback>
                        </Avatar>
                      </>
                    ) : (
                      <Plus className="h-4 w-4 text-zinc-600 group-hover:text-zinc-400 transition-colors duration-200" />
                    )}
                  </div>
                </TooltipTrigger>
                <TooltipContent>
                  <p>{slot.filled ? `Player ${slot.number}` : "Empty Slot"}</p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          ))}
        </div>
        <Button className="w-full bg-violet-600 hover:bg-violet-700 text-white py-2 text-xs font-semibold tracking-wide transition-all duration-200 ease-in-out transform hover:scale-[1.02] focus:outline-none focus:ring-2 focus:ring-violet-400 focus:ring-opacity-50">
          Join Game
        </Button>
      </div>
    </div>
  )
}

