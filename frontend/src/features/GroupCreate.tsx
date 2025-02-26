"use client"
import { X, Users, GamepadIcon, Mic, Calendar, Trophy } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Input } from "@/components/ui/profile/input"
import { motion } from "framer-motion"
import React from "react"

interface PostGroupModalProps {
  onClose: () => void;
}

export default function PostGroupModal({ onClose }: PostGroupModalProps) {
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
        <button 
          onClick={onClose}
          className="absolute right-4 top-4 rounded-full p-2 text-zinc-400 transition-colors hover:bg-white/10 hover:text-white"
        >
          <X className="h-5 w-5" />
          <span className="sr-only">Close</span>
        </button>
  
        <div className="mb-6 flex items-center justify-center gap-3">
          <div className="rounded-xl bg-violet-500/10 p-2">
            <GamepadIcon className="h-6 w-6 text-violet-500" />
          </div>
          <h2 className="bg-gradient-to-r from-white to-white/80 bg-clip-text text-xl font-semibold text-transparent">
            Post your group
          </h2>
        </div>
  
        <div className="mb-6 flex flex-wrap justify-center gap-3">
          {[
            {
              icon: Trophy,
              value: "casual",
              options: [
                { value: "competitive", label: "Competitive" },
                { value: "casual", label: "Casual" }
              ]
            },
            {
              icon: GamepadIcon,
              value: "unrated",
              options: [
                { value: "unrated", label: "Unrated" },
                { value: "spike-rush", label: "Spike Rush" },
                { value: "deathmatch", label: "Deathmatch" },
                { value: "swiftplay", label: "Swiftplay" },
                { value: "tdm", label: "TDM" },
                { value: "any", label: "Any" }
              ]
            },
            {
              icon: Users,
              value: "duo",
              options: [
                { value: "duo", label: "Duo" },
                { value: "trio", label: "Trio" },
                { value: "five-stack", label: "5-Stack" },
                { value: "ten-man", label: "10-man" }
              ]
            },
            {
              icon: Mic,
              value: "discord",
              options: [
                { value: "in-game", label: "In Game" },
                { value: "discord", label: "Discord" },
                { value: "no-voice", label: "No voice" }
              ]
            },
            {
              icon: Calendar,
              value: "any",
              options: [
                { value: "any", label: "Any" },
                { value: "15-plus", label: "15+" },
                { value: "18-plus", label: "18+" },
                { value: "21-plus", label: "21+" }
              ]
            }
          ].map((dropdown, index) => (
            <Select key={index} defaultValue={dropdown.value}>
              <SelectTrigger className="h-10 w-[calc(20%-0.6rem)] min-w-[120px] border-0 bg-white/5 px-3 text-sm text-zinc-200 ring-1 ring-white/10 transition-colors hover:bg-white/10 focus:ring-2 focus:ring-violet-500">
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
  
        <div className="mb-6">
          <Input
            placeholder="What are you looking for?"
            className="h-10 border-0 bg-white/5 text-zinc-200 placeholder:text-zinc-500 ring-1 ring-white/10 transition-colors focus-visible:ring-2 focus-visible:ring-violet-500"
          />
        </div>
  
        <Button className="group relative w-full overflow-hidden rounded-lg bg-violet-600 px-4 py-2.5 text-sm font-medium text-white transition-all hover:bg-violet-500 active:scale-[0.99]">
          <motion.div
            className="absolute inset-0 bg-gradient-to-r from-violet-400/0 via-violet-400/40 to-violet-400/0"
            initial={{ x: "-100%" }}
            whileHover={{ x: "100%" }}
            transition={{ duration: 0.75, repeat: Number.POSITIVE_INFINITY, repeatDelay: 0.5 }}
          />
          Post Group
        </Button>
      </motion.div>
    </motion.div>
  )
}

