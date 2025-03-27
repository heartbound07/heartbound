"use client"
import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { X, Users, GamepadIcon, Mic, Calendar, Trophy, AlertCircle, Globe, Shield } from "lucide-react"
import { Button } from "@/components/ui/valorant/groupcreatebutton"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { motion, AnimatePresence } from "framer-motion"
import { createParty, type Rank, type Region } from '@/contexts/valorant/partyService'
import toast from 'react-hot-toast'

interface PostGroupModalProps {
  onClose: () => void;
  onPartyCreated?: (newParty: any) => void;
}

export default function PostGroupModal({ onClose, onPartyCreated }: PostGroupModalProps) {
  // State for basic group information
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  
  // Validation state
  const [titleError, setTitleError] = useState<string | null>(null)
  const [descriptionError, setDescriptionError] = useState<string | null>(null)
  
  // Add state to track if description should be shown
  const [showDescription, setShowDescription] = useState(false)
  
  // Set expiresIn default to 10 minutes
  const [expiresIn, setExpiresIn] = useState(10)
  
  // Default maxPlayers is derived from teamSize (duo by default)
  const [maxPlayers, setMaxPlayers] = useState(2)

  // State for party requirements
  const [reqRank, setReqRank] = useState<'IRON' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND' | 'ASCENDANT' | 'IMMORTAL' | 'RADIANT'>('IRON')
  const [reqRegion, setReqRegion] = useState<'NA_EAST' | 'NA_WEST' | 'NA_CENTRAL' | 'LATAM' | 'BR' | 'EU' | 'KR' | 'AP'>('NA_EAST')
  const [voiceChat, setVoiceChat] = useState(false)

  // State for additional group creation fields
  const [matchType, setMatchType] = useState('casual')
  const [gameMode, setGameMode] = useState('unrated')
  const [teamSize, setTeamSize] = useState('duo')
  const [voicePreference, setVoicePreference] = useState('discord')
  const [ageRestriction, setAgeRestriction] = useState('any')

  // Import useNavigate to allow redirection after group creation
  const navigate = useNavigate()

  // Validation helper function for character count
  const countCharacters = (text: string): number => {
    return text.length;
  }

  // Title validation
  const validateTitle = (value: string): boolean => {
    setTitleError(null);
    
    if (!value.trim()) {
      setTitleError('Title is required');
      return false;
    }
    
    const charCount = countCharacters(value);
    if (charCount > 50) {
      setTitleError('Title cannot exceed 50 characters');
      return false;
    }
    
    return true;
  }

  // Description validation
  const validateDescription = (value: string): boolean => {
    setDescriptionError(null);
    
    if (!value.trim()) {
      setDescriptionError('Description is required');
      return false;
    }
    
    const charCount = countCharacters(value);
    if (charCount > 100) {
      setDescriptionError('Description cannot exceed 100 characters');
      return false;
    }
    
    return true;
  }

  // Handle title change with validation
  const handleTitleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newTitle = e.target.value;
    setTitle(newTitle);
    validateTitle(newTitle);
    
    // Show description field when user starts typing in title
    // Hide it when the title becomes empty
    if (newTitle.trim() && !showDescription) {
      setShowDescription(true);
    } else if (!newTitle.trim() && showDescription) {
      setShowDescription(false);
    }
  }

  // Handle description change with validation
  const handleDescriptionChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newDescription = e.target.value;
    setDescription(newDescription);
    validateDescription(newDescription);
  }

  // Effect to update gameMode when matchType changes to competitive
  useEffect(() => {
    if (matchType === 'competitive') {
      setGameMode('any');
    }
  }, [matchType]);

  // Auto-update maxPlayers based on selected teamSize
  useEffect(() => {
    if (teamSize === 'duo') {
      setMaxPlayers(2)
    } else if (teamSize === 'trio') {
      setMaxPlayers(3)
    } else if (teamSize === '4-stack') {
      setMaxPlayers(4)
    } else if (teamSize === 'five-stack') {
      setMaxPlayers(5)
    } else if (teamSize === 'ten-man') {
      setMaxPlayers(10)
    }
  }, [teamSize])

  // Dropdown configurations for game settings
  const gameSettingsDropdowns = [
    {
      field: 'matchType',
      icon: Trophy,
      label: 'Match Type',
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
      label: 'Game Mode',
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
      label: 'Team Size',
      defaultValue: 'duo',
      options: [
        { value: 'duo', label: 'Duo' },
        { value: 'trio', label: 'Trio' },
        { value: '4-stack', label: '4-Stack' },
        { value: 'five-stack', label: '5-Stack' },
        { value: 'ten-man', label: '10-man' }
      ],
      setter: setTeamSize,
    },
  ]
  
  // Dropdown configurations for preferences
  const preferencesDropdowns = [
    {
      field: 'voicePreference',
      icon: Mic,
      label: 'Voice Preference',
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
      label: 'Age Restriction',
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
    // Run validations
    const isTitleValid = validateTitle(title);
    const isDescriptionValid = validateDescription(description);

    // If validation fails, show toast and prevent submission
    if (!isTitleValid || !isDescriptionValid) {
      toast.error("Please fix the validation errors before submitting");
      return;
    }

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
      // Call onPartyCreated if provided
      onPartyCreated && onPartyCreated(newParty)
      // Show success toast
      toast.success("Party created successfully!");
      // Redirect to the party details page using the new party's id
      navigate(`/dashboard/valorant/${newParty.id}`)
    } catch (error: any) {
      console.error("Error posting group:", error)
      // Display error message using toast
      toast.error(error.message || "An unexpected error occurred");
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
        className="relative w-full max-w-2xl overflow-hidden rounded-xl bg-gradient-to-b from-[#1F2731] to-[#0F1923] p-6 shadow-2xl ring-1 ring-white/10"
      >
        <Button
          onClick={onClose}
          className="absolute right-4 top-4 p-2 bg-transparent hover:bg-[#FF4655]/10 hover:text-[#FF4655] text-white/80 z-50"
          variant="ghost"
          size="icon"
        >
          <X className="h-5 w-5" />
          <span className="sr-only">Close</span>
        </Button>

        <div className="mb-6 flex items-center justify-center gap-3">
          <div className="rounded-xl bg-[#FF4655]/10 p-2.5 shadow-md shadow-[#FF4655]/5">
            <GamepadIcon className="h-6 w-6 text-[#FF4655]" />
          </div>
          <h2 className="bg-gradient-to-r from-white to-white/80 bg-clip-text text-xl font-semibold text-transparent tracking-wide">
            Create Your Party
          </h2>
        </div>

        <div className="space-y-6">
          {/* Basic Information Section */}
          <div className="space-y-4">            
            <div className="space-y-4">
              {/* Title Input */}
              <div>
                <label htmlFor="groupTitle" className="block text-sm font-medium text-zinc-200 mb-1.5 pl-1">
                  Title
                </label>
                <input
                  id="groupTitle"
                  type="text"
                  value={title}
                  onChange={handleTitleChange}
                  placeholder="Give your party a title"
                  autoComplete="off"
                  className={`w-full bg-[#1F2731]/80 rounded-md border-0 px-3 py-2.5 text-sm text-zinc-200 focus:outline-none focus:ring-2 focus:ring-[#FF4655]/50 transition-all duration-200 shadow-sm ${
                    titleError ? "ring-1 ring-red-500/50" : "ring-1 ring-white/10"
                  }`}
                />
                <div className="flex justify-between mt-1.5 px-1">
                  <AnimatePresence>
                    {titleError ? (
                      <motion.div
                        initial={{ opacity: 0, y: -5 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -5 }}
                        className="flex items-center text-[#FF4655] text-xs"
                      >
                        <AlertCircle className="h-3 w-3 mr-1" />
                        <span>{titleError}</span>
                      </motion.div>
                    ) : (
                      <div></div>
                    )}
                  </AnimatePresence>
                  <motion.div
                    animate={{
                      color: countCharacters(title) > 45 ? "#FF4655" : "#8B97A4"
                    }}
                    className="text-xs"
                  >
                    {countCharacters(title)}/50
                  </motion.div>
                </div>
              </div>

              {/* Description Input - Only show when title has content */}
              <AnimatePresence>
                {showDescription && (
                  <motion.div
                    initial={{ opacity: 0, height: 0, y: -10 }}
                    animate={{ opacity: 1, height: "auto", y: 0 }}
                    exit={{ opacity: 0, height: 0, y: -10 }}
                    transition={{ duration: 0.3, ease: "easeOut" }}
                  >
                    <label htmlFor="groupDescription" className="block text-sm font-medium text-zinc-200 mb-1.5 pl-1">
                      Description
                    </label>
                    <textarea
                      id="groupDescription"
                      value={description}
                      onChange={handleDescriptionChange}
                      placeholder="What kind of players are you looking for?"
                      rows={3}
                      autoComplete="off"
                      className={`w-full bg-[#1F2731]/80 rounded-md border-0 px-3 py-2.5 text-sm text-zinc-200 focus:outline-none focus:ring-2 focus:ring-[#FF4655]/50 transition-all duration-200 shadow-sm ${
                        descriptionError ? "ring-1 ring-red-500/50" : "ring-1 ring-white/10"
                      }`}
                    />
                    <div className="flex justify-between mt-1.5 px-1">
                      <AnimatePresence>
                        {descriptionError ? (
                          <motion.div
                            initial={{ opacity: 0, y: -5 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -5 }}
                            className="flex items-center text-[#FF4655] text-xs"
                          >
                            <AlertCircle className="h-3 w-3 mr-1" />
                            <span>{descriptionError}</span>
                          </motion.div>
                        ) : (
                          <div></div>
                        )}
                      </AnimatePresence>
                      <motion.div
                        animate={{
                          color: countCharacters(description) > 90 ? "#FF4655" : "#8B97A4"
                        }}
                        className="text-xs"
                      >
                        {countCharacters(description)}/100
                      </motion.div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>

          {/* Game Settings Section */}
          <div className="space-y-4">
            <div className="flex items-center gap-2.5 pl-1">
              <span className="h-1.5 w-1.5 rounded-full bg-[#FF4655]"></span>
              <h3 className="text-sm font-medium text-white/90">Game Settings</h3>
            </div>
            
            <div className="grid grid-cols-3 gap-3">
              {matchType === 'competitive' ? (
                // Competitive layout (2 columns centered)
                <motion.div 
                  className="col-span-3 grid grid-cols-2 gap-3"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ duration: 0.3, ease: "easeOut" }}
                >
                  {/* Match Type column */}
                  <motion.div 
                    layout
                    className="space-y-1.5"
                    initial={{ x: -16 }}
                    animate={{ x: 0 }}
                    transition={{ duration: 0.3, ease: "easeOut" }}
                  >
                    <label className="text-xs text-zinc-400 pl-1 flex items-center gap-1.5">
                      <Trophy className="h-3.5 w-3.5 text-[#8B97A4]" />
                      <span>Match Type</span>
                    </label>
                    <Select
                      value={matchType}
                      onValueChange={(value: string) => setMatchType(value)}
                    >
                      <SelectTrigger className="h-10 border-0 bg-[#1F2731]/80 px-3 text-sm text-zinc-200 ring-1 ring-white/10 transition-colors hover:bg-[#1F2731] focus:ring-2 focus:ring-[#FF4655]/50 shadow-sm">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent className="border-zinc-800 bg-[#1F2731] text-zinc-200">
                        <SelectItem value="competitive" className="focus:bg-[#FF4655]/10">Competitive</SelectItem>
                        <SelectItem value="casual" className="focus:bg-[#FF4655]/10">Casual</SelectItem>
                      </SelectContent>
                    </Select>
                  </motion.div>

                  {/* Team Size column */}
                  <motion.div 
                    layout
                    className="space-y-1.5"
                    initial={{ x: 16 }}
                    animate={{ x: 0 }}
                    transition={{ duration: 0.3, ease: "easeOut" }}
                  >
                    <label className="text-xs text-zinc-400 pl-1 flex items-center gap-1.5">
                      <Users className="h-3.5 w-3.5 text-[#8B97A4]" />
                      <span>Team Size</span>
                    </label>
                    <Select
                      value={teamSize}
                      onValueChange={(value: string) => setTeamSize(value)}
                    >
                      <SelectTrigger className="h-10 border-0 bg-[#1F2731]/80 px-3 text-sm text-zinc-200 ring-1 ring-white/10 transition-colors hover:bg-[#1F2731] focus:ring-2 focus:ring-[#FF4655]/50 shadow-sm">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent className="border-zinc-800 bg-[#1F2731] text-zinc-200">
                        <SelectItem value="duo" className="focus:bg-[#FF4655]/10">Duo</SelectItem>
                        <SelectItem value="trio" className="focus:bg-[#FF4655]/10">Trio</SelectItem>
                        <SelectItem value="4-stack" className="focus:bg-[#FF4655]/10">4-Stack</SelectItem>
                        <SelectItem value="five-stack" className="focus:bg-[#FF4655]/10">5-Stack</SelectItem>
                      </SelectContent>
                    </Select>
                  </motion.div>
                </motion.div>
              ) : (
                // Casual layout (all 3 columns)
                <>
                  {gameSettingsDropdowns.map((dropdown, index) => (
                    <motion.div 
                      key={index} 
                      className="space-y-1.5"
                      layout
                      initial={{ opacity: index === 1 ? 0 : 1, scale: index === 1 ? 0.95 : 1 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: index === 1 ? 0 : 1, scale: index === 1 ? 0.95 : 1 }}
                      transition={{ duration: 0.3, ease: "easeOut" }}
                    >
                      <label className="text-xs text-zinc-400 pl-1 flex items-center gap-1.5">
                        <dropdown.icon className="h-3.5 w-3.5 text-[#8B97A4]" />
                        <span>{dropdown.label}</span>
                      </label>
                      <Select
                        value={index === 0 ? matchType : index === 1 ? gameMode : teamSize}
                        onValueChange={(value: string) => dropdown.setter(value)}
                      >
                        <SelectTrigger className="h-10 border-0 bg-[#1F2731]/80 px-3 text-sm text-zinc-200 ring-1 ring-white/10 transition-colors hover:bg-[#1F2731] focus:ring-2 focus:ring-[#FF4655]/50 shadow-sm">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent className="border-zinc-800 bg-[#1F2731] text-zinc-200">
                          {dropdown.options
                            .filter(option => 
                              !(dropdown.field === 'gameMode' && matchType === 'casual' && option.value === 'any')
                            )
                            .filter(option => 
                              !(dropdown.field === 'teamSize' && matchType === 'competitive' && option.value === 'ten-man')
                            )
                            .map((option) => (
                              <SelectItem key={option.value} value={option.value} className="focus:bg-[#FF4655]/10">
                                {option.label}
                              </SelectItem>
                            ))
                          }
                        </SelectContent>
                      </Select>
                    </motion.div>
                  ))}
                </>
              )}
            </div>
          </div>

          {/* Preferences Section */}
          <div className="space-y-4">
            <div className="flex items-center gap-2.5 pl-1">
              <span className="h-1.5 w-1.5 rounded-full bg-[#FF4655]"></span>
              <h3 className="text-sm font-medium text-white/90">Preferences</h3>
            </div>
            
            <div className="grid grid-cols-2 gap-3">
              {preferencesDropdowns.map((dropdown, index) => (
                <div key={index} className="space-y-1.5">
                  <label className="text-xs text-zinc-400 pl-1 flex items-center gap-1.5">
                    <dropdown.icon className="h-3.5 w-3.5 text-[#8B97A4]" />
                    <span>{dropdown.label}</span>
                  </label>
                  <Select
                    defaultValue={dropdown.defaultValue}
                    onValueChange={(value: string) => dropdown.setter(value)}
                  >
                    <SelectTrigger className="h-10 border-0 bg-[#1F2731]/80 px-3 text-sm text-zinc-200 ring-1 ring-white/10 transition-colors hover:bg-[#1F2731] focus:ring-2 focus:ring-[#FF4655]/50 shadow-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="border-zinc-800 bg-[#1F2731] text-zinc-200">
                      {dropdown.options.map((option) => (
                        <SelectItem key={option.value} value={option.value} className="focus:bg-[#FF4655]/10">
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              ))}
            </div>
          </div>

          {/* Requirements Section */}
          <div className="space-y-4">
            <div className="flex items-center gap-2.5 pl-1">
              <span className="h-1.5 w-1.5 rounded-full bg-[#FF4655]"></span>
              <h3 className="text-sm font-medium text-white/90">Requirements</h3>
            </div>
            
            <div className="grid grid-cols-2 gap-4">
              {/* Rank Selector */}
              <div className="space-y-1.5">
                <label className="text-xs text-zinc-400 pl-1 flex items-center gap-1.5">
                  <Shield className="h-3.5 w-3.5 text-[#8B97A4]" />
                  <span>Minimum Rank</span>
                </label>
                <Select
                  value={reqRank}
                  onValueChange={(value: string) => setReqRank(value as Rank)}
                >
                  <SelectTrigger className="h-10 border-0 bg-[#1F2731]/80 px-3 text-sm text-zinc-200 ring-1 ring-white/10 transition-colors hover:bg-[#1F2731] focus:ring-2 focus:ring-[#FF4655]/50 shadow-sm">
                    <SelectValue placeholder="Select Rank" />
                  </SelectTrigger>
                  <SelectContent className="border-zinc-800 bg-[#1F2731] text-zinc-200">
                    <SelectItem value="IRON" className="focus:bg-[#FF4655]/10">Iron</SelectItem>
                    <SelectItem value="BRONZE" className="focus:bg-[#FF4655]/10">Bronze</SelectItem>
                    <SelectItem value="SILVER" className="focus:bg-[#FF4655]/10">Silver</SelectItem>
                    <SelectItem value="GOLD" className="focus:bg-[#FF4655]/10">Gold</SelectItem>
                    <SelectItem value="PLATINUM" className="focus:bg-[#FF4655]/10">Platinum</SelectItem>
                    <SelectItem value="DIAMOND" className="focus:bg-[#FF4655]/10">Diamond</SelectItem>
                    <SelectItem value="ASCENDANT" className="focus:bg-[#FF4655]/10">Ascendant</SelectItem>
                    <SelectItem value="IMMORTAL" className="focus:bg-[#FF4655]/10">Immortal</SelectItem>
                    <SelectItem value="RADIANT" className="focus:bg-[#FF4655]/10">Radiant</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {/* Region Selector */}
              <div className="space-y-1.5">
                <label className="text-xs text-zinc-400 pl-1 flex items-center gap-1.5">
                  <Globe className="h-3.5 w-3.5 text-[#8B97A4]" />
                  <span>Region</span>
                </label>
                <Select
                  value={reqRegion}
                  onValueChange={(value: string) => setReqRegion(value as Region)}
                >
                  <SelectTrigger className="h-10 border-0 bg-[#1F2731]/80 px-3 text-sm text-zinc-200 ring-1 ring-white/10 transition-colors hover:bg-[#1F2731] focus:ring-2 focus:ring-[#FF4655]/50 shadow-sm">
                    <SelectValue placeholder="Select Region" />
                  </SelectTrigger>
                  <SelectContent className="border-zinc-800 bg-[#1F2731] text-zinc-200">
                    <SelectItem value="NA_EAST" className="focus:bg-[#FF4655]/10">NA East</SelectItem>
                    <SelectItem value="NA_WEST" className="focus:bg-[#FF4655]/10">NA West</SelectItem>
                    <SelectItem value="NA_CENTRAL" className="focus:bg-[#FF4655]/10">NA Central</SelectItem>
                    <SelectItem value="LATAM" className="focus:bg-[#FF4655]/10">LATAM</SelectItem>
                    <SelectItem value="BR" className="focus:bg-[#FF4655]/10">BR</SelectItem>
                    <SelectItem value="EU" className="focus:bg-[#FF4655]/10">EU</SelectItem>
                    <SelectItem value="KR" className="focus:bg-[#FF4655]/10">KR</SelectItem>
                    <SelectItem value="AP" className="focus:bg-[#FF4655]/10">AP</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            
            {/* Voice Chat Toggle */}
            <div className="pt-1">
              <label htmlFor="voiceChat" className="flex items-center gap-3 cursor-pointer group p-1.5 rounded-md hover:bg-white/5 transition-colors">
                <div className="relative shrink-0">
                  <input
                    id="voiceChat"
                    type="checkbox"
                    checked={voiceChat}
                    onChange={(e) => setVoiceChat(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-10 h-6 rounded-full transition-colors duration-200 
                    bg-[#1F2731] ring-1 ring-white/10
                    peer-focus:ring-2 peer-focus:ring-[#FF4655]/50 
                    peer-checked:bg-[#FF4655] peer-checked:ring-0
                    shadow-inner"
                  ></div>
                  <div className="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow-md
                    transition-transform duration-200 
                    peer-checked:translate-x-4 peer-checked:bg-white
                    peer-focus:ring-1 peer-focus:ring-white/20 
                    flex items-center justify-center overflow-hidden"
                  >
                    <Mic className={`h-3 w-3 ${voiceChat ? 'text-[#FF4655]/0' : 'text-[#1F2731]/30'} transition-colors`} />
                  </div>
                </div>
                <div>
                  <span className="text-sm font-medium text-white group-hover:text-white/90 transition-colors">Voice Chat Required</span>
                  <p className="text-xs text-[#8B97A4] group-hover:text-[#8B97A4]/90 transition-colors">Only players with microphones can join</p>
                </div>
              </label>
            </div>
          </div>

          {/* Submission Button */}
          <div className="pt-2">
            <Button 
              onClick={handlePostGroup} 
              className="w-full py-2.5 bg-[#FF4655] hover:bg-[#FF4655]/90 text-white font-medium tracking-wide
                transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98]
                shadow-md shadow-[#FF4655]/10 hover:shadow-lg hover:shadow-[#FF4655]/20
                focus:outline-none focus:ring-2 focus:ring-[#FF4655]/50 focus:ring-offset-1 focus:ring-offset-[#1F2731]
                rounded-md"
            >
              Create Party
            </Button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  )
}

