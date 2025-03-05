"use client"

import { useState, useEffect } from "react"
import { Save, Loader2 } from "lucide-react"
import toast, { Toaster } from 'react-hot-toast'

import { Button } from "@/components/ui/profile/button"
import { Input } from "@/components/ui/profile/input"
import { Label } from "@/components/ui/profile/label"
import { Textarea } from "@/components/ui/profile/textarea"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/profile/tooltip"
import { useAuth } from "@/contexts/auth"
import { ProfilePreview } from "@/components/ui/profile/ProfilePreview"
import { UpdateProfileDTO } from "@/config/userService"
import { AvatarUpload } from "@/components/ui/profile/AvatarUpload"
import { BannerUpload } from "@/components/ui/profile/BannerUpload"

export function ProfilePage() {
  const { user, profile, updateUserProfile, isLoading, hasRole } = useAuth()
  
  const [about, setAbout] = useState("")
  const [name, setName] = useState("")
  const [pronouns, setPronouns] = useState("")
  const [bannerColor, setBannerColor] = useState("bg-white/10")
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [avatarUrl, setAvatarUrl] = useState<string>("")
  const [bannerUrl, setBannerUrl] = useState<string>("")
  const [isUsingCustomAvatar, setIsUsingCustomAvatar] = useState(false)
  const [discordAvatar, setDiscordAvatar] = useState<string>("")
  const [useDiscordAvatar, setUseDiscordAvatar] = useState(false)
  
  // Initialize form with profile data if available
  useEffect(() => {
    if (profile) {
      setName(profile.displayName || "")
      setPronouns(profile.pronouns || "")
      setAbout(profile.about || "")
      setBannerColor(profile.bannerColor || "bg-white/10")
      setBannerUrl(profile.bannerUrl || "")
    }
    if (user) {
      setAvatarUrl(user.avatar || "")
      // Store the original Discord avatar URL if it contains discordapp.com
      if (user.avatar && user.avatar.includes('cdn.discordapp.com')) {
        setDiscordAvatar(user.avatar)
        setIsUsingCustomAvatar(false)
      } else if (user.avatar) {
        setIsUsingCustomAvatar(true)
      }
      setUseDiscordAvatar(false)
    }
  }, [profile, user])
  
  const handleAvatarUpload = (url: string) => {
    setAvatarUrl(url)
    setIsUsingCustomAvatar(true)
    setUseDiscordAvatar(false)
    toast.success("Avatar uploaded successfully! Don't forget to save your profile.")
  }
  
  const handleRemoveAvatar = () => {
    // Even if we have stored Discord avatar, just set avatarUrl to empty string
    // to signal to backend we want to use Discord avatar
    setAvatarUrl("");
    setIsUsingCustomAvatar(false);
    setUseDiscordAvatar(true);
    toast.success("Avatar removed. Your Discord avatar will be used instead. Don't forget to save your profile.");
  }
  
  const handleBannerUpload = (url: string) => {
    setBannerUrl(url)
    toast.success("Banner uploaded successfully! Don't forget to save your profile.")
  }
  
  const handleSaveProfile = async () => {
    try {
      const profileUpdate: UpdateProfileDTO = {
        displayName: name,
        pronouns: pronouns,
        about: about,
        bannerColor: bannerColor,
        avatar: avatarUrl,
        bannerUrl: bannerUrl
      }
      
      await updateUserProfile(profileUpdate)
      setSaveMessage("Profile updated successfully!")
      toast.success("Profile updated successfully!")
    } catch (error) {
      console.error("Error saving profile:", error)
      setSaveMessage("Error updating profile. Please try again.")
      toast.error("Error updating profile")
    }
  }

  return (
    <TooltipProvider>
      <div className="flex min-h-screen flex-col gap-8 bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] p-6 text-white lg:flex-row">
        <Toaster position="top-right" />
        
        {/* Settings Panel */}
        <div className="flex w-full flex-col gap-8 lg:w-1/2">
          <div className="rounded-2xl border border-white/10 bg-white/10 p-8 backdrop-blur-md shadow-lg">
            <h2 className="mb-6 text-lg font-medium text-white">Profile</h2>

            <div className="space-y-6">
              <div className="space-y-2">
                <Label htmlFor="display-name" className="text-xs font-medium text-white/80">
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
                <Label htmlFor="pronouns" className="text-xs font-medium text-white/80">
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
                <Label className="text-xs font-medium text-white/80">AVATAR</Label>
                <AvatarUpload 
                  currentAvatarUrl={avatarUrl}
                  onUpload={handleAvatarUpload}
                  onRemove={handleRemoveAvatar}
                  showRemoveButton={isUsingCustomAvatar}
                />
                <p className="text-xs text-white/60">
                  Click on your avatar to upload a new image. Maximum size: 5MB.
                </p>
              </div>

              <div className="space-y-3">
                <Label className="text-xs font-medium text-white/80">PROFILE BANNER</Label>
                
                {hasRole('MONARCH') ? (
                  <BannerUpload
                    currentBannerUrl={bannerUrl}
                    bannerColor={bannerColor}
                    onUpload={handleBannerUpload}
                  />
                ) : (
                  <div className="relative h-32 w-full overflow-hidden rounded-lg border border-white/10">
                    {bannerUrl ? (
                      <img src={bannerUrl} alt="Banner" className="h-full w-full object-cover" />
                    ) : (
                      <div className={`h-full w-full ${bannerColor}`} />
                    )}
                    
                    <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/60 text-white">
                      <div className="text-center px-4">
                        <p className="text-sm font-medium mb-1">Banner upload is available for Premium users only</p>
                        <p className="text-xs text-white/70">Upgrade your account to customize your profile banner</p>
                      </div>
                    </div>
                  </div>
                )}
                
                <p className="text-xs text-white/60">
                  {hasRole('MONARCH') 
                    ? "Click on the banner to upload a new image. Maximum size: 5MB."
                    : "Banner customization is a premium feature for Monarch users."}
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="about" className="text-xs font-medium text-white/80">
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
                  <span className="text-xs text-white/60">{about.length}/200</span>
                </div>
              </div>

              <div className="space-y-3">
                <Label className="text-xs font-medium text-white/80">BANNER COLOR</Label>
                <div className="flex gap-2">
                  {["bg-white/10", "bg-blue-600", "bg-purple-600", "bg-rose-600", "bg-emerald-600"].map((color) => (
                    <Tooltip key={color}>
                      <TooltipTrigger>
                        <Button
                          onClick={() => setBannerColor(color)}
                          variant="ghost"
                          size="sm"
                          className={`h-8 w-8 rounded-full border-2 border-white/20 transition-transform hover:scale-110 ${color} ${bannerColor === color ? 'ring-2 ring-white' : ''}`}
                        >
                          <span className="sr-only">
                            {color.replace("bg-", "").replace("/10", "")}
                          </span>
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>
                        {color.replace("bg-", "").replace("/10", "")}
                      </TooltipContent>
                    </Tooltip>
                  ))}
                </div>
              </div>
              
              <div className="flex justify-end">
                <Button 
                  onClick={handleSaveProfile}
                  disabled={isLoading}
                  className="bg-white/20 hover:bg-white/30 text-white flex items-center gap-2"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Saving...
                    </>
                  ) : (
                    <>
                      <Save className="h-4 w-4" />
                      Save Profile
                    </>
                  )}
                </Button>
              </div>
              
              {saveMessage && (
                <div className="mt-4 p-3 rounded-md bg-white/20 text-white text-sm">
                  {saveMessage}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Preview Panel */}
        <ProfilePreview 
          bannerColor={bannerColor}
          bannerUrl={bannerUrl}
          name={name || (user?.username || "")}
          about={about}
          pronouns={pronouns}
          user={{ ...user, avatar: avatarUrl }}
          showEditButton={false}
          onClick={() => {
            // For example, navigate to the user's detailed profile page.
          }}
        />
      </div>
    </TooltipProvider>
  )
}

