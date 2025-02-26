"use client"
import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { X, Users, GamepadIcon, Mic, Calendar, Trophy } from "lucide-react"
import { Button } from "@/components/ui/valorant/groupcreatebutton"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Input } from "@/components/ui/profile/input"
import { motion } from "framer-motion"
import { createParty } from '@/contexts/valorant/partyService'

interface PostGroupModalProps {
  onClose: () => void;
}

export default function PostGroupModal({ onClose }: PostGroupModalProps) {
  // State for basic group information
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  
  // Set expiresIn default to 10 minutes
  const [expiresIn, setExpiresIn] = useState(10)
  
  // Default maxPlayers is derived from teamSize (duo by default)
  const [maxPlayers, setMaxPlayers] = useState(2)

  // State for party requirements
  const [reqRank, setReqRank] = useState('')
  const [reqRegion, setReqRegion] = useState('')
  const [voiceChat, setVoiceChat] = useState(false)

  // State for additional group creation fields
  const [matchType, setMatchType] = useState('casual')
  const [gameMode, setGameMode] = useState('unrated')
  const [teamSize, setTeamSize] = useState('duo')
  const [voicePreference, setVoicePreference] = useState('discord')
  const [ageRestriction, setAgeRestriction] = useState('any')

  // Import useNavigate to allow redirection after group creation
  const navigate = useNavigate()

  // Auto-update maxPlayers based on selected teamSize
  useEffect(() => {
    if (teamSize === 'duo') {
      setMaxPlayers(2)
    } else if (teamSize === 'trio') {
      setMaxPlayers(3)
    } else if (teamSize === 'five-stack') {
      setMaxPlayers(5)
    } else if (teamSize === 'ten-man') {
      setMaxPlayers(10)
    }
  }, [teamSize])

  // Dropdown configurations
  const dropdowns = [
    {
      field: 'matchType',
      icon: Trophy,
      defaultValue: 'casual',
      options: [
        { value: 'competitive', label: 'Competitive' },
        { value: 'casual', label: 'Casual' }
      ],
      setter: setMatchType,
    },
    {
      field: 'gameMode',
      icon: GamepadIcon,
      defaultValue: 'unrated',
      options: [
        { value: 'unrated', label: 'Unrated' },
        { value: 'spike-rush', label: 'Spike Rush' },
        { value: 'deathmatch', label: 'Deathmatch' },
        { value: 'swiftplay', label: 'Swiftplay' },
        { value: 'tdm', label: 'TDM' },
        { value: 'any', label: 'Any' }
      ],
      setter: setGameMode,
    },
    {
      field: 'teamSize',
      icon: Users,
      defaultValue: 'duo',
      options: [
        { value: 'duo', label: 'Duo' },
        { value: 'trio', label: 'Trio' },
        { value: 'five-stack', label: '5-Stack' },
        { value: 'ten-man', label: '10-man' }
      ],
      setter: setTeamSize,
    },
    {
      field: 'voicePreference',
      icon: Mic,
      defaultValue: 'discord',
      options: [
        { value: 'in-game', label: 'In Game' },
        { value: 'discord', label: 'Discord' },
        { value: 'no-voice', label: 'No voice' }
      ],
      setter: setVoicePreference,
    },
    {
      field: 'ageRestriction',
      icon: Calendar,
      defaultValue: 'any',
      options: [
        { value: 'any', label: 'Any' },
        { value: '15-plus', label: '15+' },
        { value: '18-plus', label: '18+' },
        { value: '21-plus', label: '21+' }
      ],
      setter: setAgeRestriction,
    }
  ]

  // Submission handler for posting the group
  const handlePostGroup = async () => {
    const payload = {
      game: "Valorant",
      title,
      description,
      requirements: {
        rank: reqRank,
        region: reqRegion,
        voiceChat,
      },
      expiresIn,    // will always be 10 minutes
      maxPlayers,   // auto-calculated based on teamSize
      matchType,
      gameMode,
      teamSize,
      voicePreference,
      ageRestriction,
    }

    try {
      const newParty = await createParty(payload)
      // Redirect to the party details page using the new party's id
      navigate(`/dashboard/valorant/${newParty.id}`)
    } catch (error) {
      console.error("Error posting group:", error)
      // Optionally, add UI error handling here
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
    >
      <motion.div
        initial={{ scale: 0.95, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        exit={{ scale: 0.95, opacity: 0 }}
        transition={{ duration: 0.2 }}
        className="relative w-full max-w-2xl overflow-hidden rounded-xl bg-gradient-to-b from-zinc-800/90 to-zinc-900/90 p-6 shadow-2xl ring-1 ring-white/10"
      >
        <Button
          onClick={onClose}
          className="absolute right-4 top-4 p-2"
          variant="ghost"
          size="icon"
        >
          <X className="h-5 w-5" />
          <span className="sr-only">Close</span>
        </Button>

        <div className="mb-6 flex items-center justify-center gap-3">
          <div className="rounded-xl bg-[#FF4655]/10 p-2">
            <GamepadIcon className="h-6 w-6 text-[#FF4655]" />
          </div>
          <h2 className="bg-gradient-to-r from-white to-white/80 bg-clip-text text-xl font-semibold text-transparent">
            Post your group
          </h2>
        </div>

        {/* Additional group settings via dropdowns */}
        <div className="mb-6 flex flex-wrap justify-center gap-3">
          {dropdowns.map((dropdown, index) => (
            <Select
              key={index}
              defaultValue={dropdown.defaultValue}
              onValueChange={(value: string) => dropdown.setter(value)}
            >
              <SelectTrigger className="h-10 w-[calc(20%-0.6rem)] min-w-[120px] border-0 bg-white/5 px-3 text-sm text-zinc-200 ring-1 ring-white/10 transition-colors hover:bg-white/10 focus:ring-2 focus:ring-[#FF4655]">
                <dropdown.icon className="mr-2 h-4 w-4 text-zinc-400" />
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="border-zinc-800 bg-zinc-900 text-zinc-200">
                {dropdown.options.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ))}
        </div>

        {/* Input fields for group details */}
        <div className="mb-4">
          <Input
            placeholder="Group Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="h-10 border-0 bg-white/5 text-zinc-200 placeholder:text-zinc-500 ring-1 ring-white/10 transition-colors focus-visible:ring-2 focus-visible:ring-[#FF4655]"
          />
        </div>
        <div className="mb-4">
          <Input
            placeholder="Group Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="h-10 border-0 bg-white/5 text-zinc-200 placeholder:text-zinc-500 ring-1 ring-white/10 transition-colors focus-visible:ring-2 focus-visible:ring-[#FF4655]"
          />
        </div>
        <div className="mb-4">
          <Input
            placeholder="Required Rank"
            value={reqRank}
            onChange={(e) => setReqRank(e.target.value)}
            className="h-10 border-0 bg-white/5 text-zinc-200 placeholder:text-zinc-500 ring-1 ring-white/10 transition-colors focus-visible:ring-2 focus-visible:ring-[#FF4655]"
          />
        </div>
        <div className="mb-4">
          <Input
            placeholder="Preferred Region"
            value={reqRegion}
            onChange={(e) => setReqRegion(e.target.value)}
            className="h-10 border-0 bg-white/5 text-zinc-200 placeholder:text-zinc-500 ring-1 ring-white/10 transition-colors focus-visible:ring-2 focus-visible:ring-[#FF4655]"
          />
        </div>
        <div className="mb-4">
          <label htmlFor="voiceChat" className="flex items-center space-x-3 cursor-pointer">
            <div className="relative">
              <input
                id="voiceChat"
                type="checkbox"
                checked={voiceChat}
                onChange={(e) => setVoiceChat(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-10 h-6 bg-gray-300 rounded-full transition-colors duration-200 peer-focus:ring-2 peer-focus:ring-[#FF4655] peer-checked:bg-[#FF4655]"></div>
              <div className="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow-md transition-transform duration-200 peer-checked:translate-x-4"></div>
            </div>
            <span className="text-sm font-medium text-white">Voice Chat Required?</span>
          </label>
        </div>
        
        {/* The expiresIn and maxPlayers inputs have been removed.
            Their values are now auto-set (expiresIn = 10, maxPlayers based on teamSize) */}
        
        {/* Submission Button */}
        <div className="mt-6">
          <Button onClick={handlePostGroup} className="w-full py-2">
            Post Group
          </Button>
        </div>
      </motion.div>
    </motion.div>
  )
}

