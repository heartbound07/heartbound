"use client"

import { useState } from "react"
import { motion } from "framer-motion"
import { Camera, Plus, Upload } from "lucide-react"

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/profile/avatar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/profile/input"
import { Label } from "@/components/ui/profile/label"
import { Textarea } from "@/components/ui/profile/textarea"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/profile/tooltip"
import { useAuth } from "@/contexts/auth"

export function ProfilePage() {
  const [avatarHover, setAvatarHover] = useState(false)
  const [bannerHover, setBannerHover] = useState(false)
  const [about, setAbout] = useState("")
  const [name, setName] = useState("p")
  const [pronouns, setPronouns] = useState("")
  const [bannerColor, setBannerColor] = useState("bg-black")
  const { user } = useAuth()

  return (
    <TooltipProvider>
      <div className="flex min-h-screen flex-col gap-8 bg-gradient-to-br from-zinc-900 via-zinc-900 to-zinc-800/50 p-6 text-white lg:flex-row">
        {/* Settings Panel */}
        <div className="flex w-full flex-col gap-8 lg:w-1/2">
          <div className="rounded-xl border border-white/5 bg-black/20 p-6 backdrop-blur-xl">
            <h2 className="mb-6 text-lg font-medium text-zinc-200">Profile Settings</h2>

            <div className="space-y-6">
              <div className="space-y-2">
                <Label htmlFor="display-name" className="text-xs font-medium text-zinc-400">
                  DISPLAY NAME
                </Label>
                <Input
                  id="display-name"
                  className="border-white/10 bg-white/5 text-sm transition-colors focus:border-white/20 focus:bg-white/10"
                  placeholder="Enter your display name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="pronouns" className="text-xs font-medium text-zinc-400">
                  PRONOUNS
                </Label>
                <Input
                  id="pronouns"
                  className="border-white/10 bg-white/5 text-sm transition-colors focus:border-white/20 focus:bg-white/10"
                  placeholder="Add your pronouns"
                  value={pronouns}
                  onChange={(e) => setPronouns(e.target.value)}
                />
              </div>

              <div className="space-y-3">
                <Label className="text-xs font-medium text-zinc-400">AVATAR</Label>
                <div
                  className="group relative h-24 w-24 cursor-pointer rounded-full"
                  onMouseEnter={() => setAvatarHover(true)}
                  onMouseLeave={() => setAvatarHover(false)}
                >
                  <Avatar className="h-24 w-24 border-2 border-white/10 transition-all duration-200 group-hover:border-white/20">
                    <AvatarImage src="/placeholder.svg" />
                    <AvatarFallback>P</AvatarFallback>
                  </Avatar>
                  <div
                    className={`absolute inset-0 flex items-center justify-center rounded-full bg-black/60 transition-opacity duration-200 ${
                      avatarHover ? "opacity-100" : "opacity-0"
                    }`}
                  >
                    <Camera className="h-6 w-6" />
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                <Label className="text-xs font-medium text-zinc-400">PROFILE BANNER</Label>
                <div
                  className="group relative h-32 cursor-pointer overflow-hidden rounded-lg border border-white/10 bg-white/5"
                  onMouseEnter={() => setBannerHover(true)}
                  onMouseLeave={() => setBannerHover(false)}
                >
                  <div
                    className={`absolute inset-0 flex items-center justify-center bg-black/60 transition-opacity duration-200 ${
                      bannerHover ? "opacity-100" : "opacity-0"
                    }`}
                  >
                    <Upload className="h-6 w-6" />
                  </div>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="about" className="text-xs font-medium text-zinc-400">
                  ABOUT ME
                </Label>
                <Textarea
                  id="about"
                  value={about}
                  onChange={(e) => setAbout(e.target.value)}
                  className="min-h-[120px] border-white/10 bg-white/5 text-sm transition-colors focus:border-white/20 focus:bg-white/10"
                  placeholder="Tell us about yourself..."
                  maxLength={200}
                />
                <div className="flex justify-end">
                  <span className="text-xs text-zinc-500">{about.length}/200</span>
                </div>
              </div>

              <div className="space-y-3">
                <Label className="text-xs font-medium text-zinc-400">BANNER COLOR</Label>
                <div className="flex gap-2">
                  {["bg-black", "bg-blue-600", "bg-purple-600", "bg-rose-600", "bg-emerald-600"].map((color) => (
                    <Tooltip key={color}>
                      <TooltipTrigger>
                        <div
                          className={`h-8 w-8 cursor-pointer rounded-full border-2 border-white/20 transition-transform hover:scale-110 ${color}`}
                          onClick={() => setBannerColor(color)}
                        />
                      </TooltipTrigger>
                      <TooltipContent>{color.replace("bg-", "").replace("-600", "")}</TooltipContent>
                    </Tooltip>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Preview Panel */}
        <div className="w-full lg:w-1/2">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="sticky top-6 overflow-hidden rounded-xl border border-white/10 bg-gradient-to-br from-zinc-900 to-zinc-800"
          >
            <div className={`relative h-32 ${bannerColor}`}>
              <Button
                size="sm"
                variant="secondary"
                className="absolute right-4 top-4 gap-2 bg-white/10 backdrop-blur-md transition-colors hover:bg-white/20"
              >
                <Plus className="h-4 w-4" />
                Add Status
              </Button>
              <div className="absolute bottom-0 left-4 translate-y-1/2">
                <div className="relative">
                  <Avatar className="h-20 w-20 border-4 border-zinc-900 shadow-xl">
                    <AvatarImage src={user?.avatar || "/placeholder.svg"} />
                    <AvatarFallback>{user?.username ? user.username.charAt(0).toUpperCase() : "P"}</AvatarFallback>
                  </Avatar>
                </div>
              </div>
            </div>
            <div className="p-4 pt-12">
              <div className="mb-4 flex items-center gap-2">
                <h2 className="text-xl font-bold">{name || "Display Name"}</h2>
                <span className="text-sm text-zinc-400">{user?.username || "Guest"}</span>
              </div>
              <p className="text-sm text-zinc-400">{about || "Your about me section will appear here..."}</p>
              <Button className="mt-6 w-full bg-white/10 backdrop-blur-md transition-colors hover:bg-white/20">
                Example Button
              </Button>
            </div>
          </motion.div>
        </div>
      </div>
    </TooltipProvider>
  )
}

