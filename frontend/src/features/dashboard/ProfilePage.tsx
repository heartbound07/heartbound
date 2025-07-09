"use client"

import { useState, useEffect } from "react"
import { Save, Loader2, Palette, Check } from "lucide-react"
import toast, { Toaster } from 'react-hot-toast'
import { HexColorPicker } from "react-colorful"
import "@/assets/profile.css"
import { useTheme } from "@/contexts/ThemeContext"

// Import Radix UI Popover component
import * as Popover from '@radix-ui/react-popover'

import { Button } from "@/components/ui/profile/button"
import { Input } from "@/components/ui/profile/input"
import { Label } from "@/components/ui/profile/label"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/profile/tooltip"
import { useAuth } from "@/contexts/auth"
import { ProfilePreview } from "@/components/ui/profile/ProfilePreview"
import { UpdateProfileDTO } from "@/config/userService"
import { AvatarUpload } from "@/components/ui/profile/AvatarUpload"
import { BannerUpload } from "@/components/ui/profile/BannerUpload"

// Security components
import { SecureInput } from "@/components/ui/SecureInput"
import { useSanitizedContent, useInputValidation } from "@/hooks/useSanitizedContent"

// Helper to convert Tailwind bg classes to hex colors
const tailwindBgToHex = (bgClass: string) => {
  const colorMap: Record<string, string> = {
    "bg-white/10": "#ffffff1a",
    "bg-blue-600": "#2563eb",
    "bg-purple-600": "#9333ea",
    "bg-rose-600": "#e11d48",
    "bg-emerald-600": "#059669"
  };
  
  return colorMap[bgClass] || bgClass;
}

// Add proper type definitions for the component props
interface ColorPickerPopoverProps {
  color: string;
  onChange: (color: string) => void;
  presetColors?: string[];
}

// Color picker component with popover
const ColorPickerPopover = ({ 
  color, 
  onChange, 
  presetColors = ["#ffffff1a", "#2563eb", "#9333ea", "#e11d48", "#059669"] 
}: ColorPickerPopoverProps) => {
  const [hexColor, setHexColor] = useState(() => tailwindBgToHex(color));
  
  // Update component when parent color changes
  useEffect(() => {
    setHexColor(tailwindBgToHex(color));
  }, [color]);
  
  const handleColorChange = (newColor: string) => {
    setHexColor(newColor);
    onChange(newColor);
  };
  
  return (
    <Popover.Root>
      <div className="flex items-center gap-3 pl-4 h-12 rounded-md">
        <Popover.Trigger asChild>
          <div 
            className="h-10 w-10 rounded-full shadow-sm cursor-pointer transition-all duration-200 hover:scale-110"
            style={{ backgroundColor: hexColor }}
          />
        </Popover.Trigger>
        
        <Palette className="h-5 w-5 opacity-60" />
      </div>
      
      <Popover.Portal>
        <Popover.Content className="w-64 p-4 bg-black/90 border border-white/20 rounded-md shadow-lg z-[9999]" sideOffset={5}>
          <div className="space-y-4">
            <HexColorPicker color={hexColor} onChange={handleColorChange} className="w-full" />
            
            <div className="pt-2">
              <Label className="text-xs text-white/80 mb-2 block">Presets</Label>
              <div className="flex flex-wrap gap-2 justify-center">
                {presetColors.map((presetColor) => (
                  <Tooltip key={presetColor}>
                    <TooltipTrigger>
                      <Button
                        onClick={() => handleColorChange(presetColor)}
                        variant="ghost"
                        size="sm"
                        className="h-8 w-8 rounded-full p-0 transition-all hover:scale-110 hover:bg-white/5"
                        style={{ backgroundColor: presetColor }}
                      >
                        {hexColor === presetColor && (
                          <Check className="h-3 w-3 text-white" />
                        )}
                        <span className="sr-only">
                          {presetColor}
                        </span>
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>
                      {presetColor}
                    </TooltipContent>
                  </Tooltip>
                ))}
              </div>
            </div>
            
            <div className="flex items-center gap-2 pt-2">
              <Input 
                value={hexColor} 
                onChange={(e) => handleColorChange(e.target.value)}
                className="font-mono text-sm"
              />
              <Button 
                onClick={() => onChange(hexColor)} 
                size="sm"
                className="bg-white/20 hover:bg-white/30"
              >
                Apply
              </Button>
            </div>
          </div>
          <Popover.Arrow className="fill-white/20" />
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
};

export function ProfilePage() {
  const { user, profile, updateUserProfile, isLoading, hasRole } = useAuth()
  const { theme } = useTheme()
  const [about, setAbout] = useState("")
  const [name, setName] = useState("")
  const [pronouns, setPronouns] = useState("")
  const [bannerColor, setBannerColor] = useState("bg-white/10")
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [avatarUrl, setAvatarUrl] = useState<string>("")
  const [bannerUrl, setBannerUrl] = useState<string>("")
  const [isUsingCustomAvatar, setIsUsingCustomAvatar] = useState(false)
  
  // Security validation for inputs
  const { validate: validateDisplayName } = useInputValidation({
    maxLength: 50,
    minLength: 1,
    stripHtml: true,
    allowHtml: false
  });
  
  const { validate: validatePronouns } = useInputValidation({
    maxLength: 20,
    stripHtml: true,
    allowHtml: false
  });
  
  const { validate: validateAbout } = useInputValidation({
    maxLength: 200,
    stripHtml: true,
    allowHtml: false
  });
  
  // Sanitized content for preview
  const sanitizedName = useSanitizedContent(name, { maxLength: 50, stripHtml: true });
  const sanitizedPronouns = useSanitizedContent(pronouns, { maxLength: 20, stripHtml: true });
  const sanitizedAbout = useSanitizedContent(about, { maxLength: 200, stripHtml: true });
  
  // Initialize form with profile data if available
  useEffect(() => {
    if (import.meta.env.DEV) {
      console.log('[ProfilePage useEffect] Running. User from context:', JSON.stringify(user));
      console.log('[ProfilePage useEffect] Running. Profile from context:', JSON.stringify(profile));
    }
    if (profile) {
      setName(profile.displayName || "")
      setPronouns(profile.pronouns || "")
      setAbout(profile.about || "")
      setBannerColor(profile.bannerColor || "bg-white/10")
      setBannerUrl(profile.bannerUrl || "")
    }
    if (user) {
      // AVATAR FALLBACK FIX: Use the resolved avatar URL from auth context
      // The backend resolves "USE_DISCORD_AVATAR" to actual Discord URL in the DTO
      // and AuthProvider updates the user context with this resolved URL
      setAvatarUrl(user.avatar || "")
      
      if (import.meta.env.DEV) {
        console.log('[ProfilePage useEffect] Setting local avatarUrl based on context to:', user.avatar);
      }
      
      // Determine if using custom avatar or Discord avatar
      if (user.avatar === "USE_DISCORD_AVATAR" || (user.avatar && user.avatar.includes('cdn.discordapp.com')) || user.avatar === '/images/default-avatar.png') {
        setIsUsingCustomAvatar(false)
      } else if (user.avatar) {
        setIsUsingCustomAvatar(true)
      }
    }
  }, [profile, user])
  
  const handleAvatarUpload = (url: string) => {
    setAvatarUrl(url)
    setIsUsingCustomAvatar(true)
    toast.success("Avatar uploaded successfully! Don't forget to save your profile.")
  }
  
  const handleRemoveAvatar = () => {
    // Set avatarUrl to empty string to signal to backend we want to use Discord avatar
    setAvatarUrl("");
    setIsUsingCustomAvatar(false);
    toast.success("Avatar removed. Your Discord avatar will be used instead. Don't forget to save your profile.");
  }
  
  const handleBannerUpload = (url: string) => {
    setBannerUrl(url)
    toast.success("Banner uploaded successfully! Don't forget to save your profile.")
  }
  
  const handleRemoveBanner = () => {
    setBannerUrl("");
    toast.success("Banner removed. The default banner color will be used instead. Don't forget to save your profile.");
  }
  
  const handleColorChange = (color: string) => {
    setBannerColor(color);
  }
  
  // Client-side validation helper functions
  const isValidHexColor = (color: string): boolean => {
    // Check for hex color format (#RRGGBB or #RRGGBBAA)
    const hexPattern = /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$/;
    return hexPattern.test(color);
  }

  const isValidTailwindColor = (color: string): boolean => {
    // Check for Tailwind CSS background color classes
    const tailwindPattern = new RegExp('^(bg-[a-z]+-[0-9]+(/[0-9]+)?|bg-white/[0-9]+)$');
    return tailwindPattern.test(color);
  }

  const validateBannerColor = (color: string): boolean => {
    if (!color) return true; // Empty is valid
    return isValidHexColor(color) || isValidTailwindColor(color);
  }

  const isValidUrl = (url: string): boolean => {
    if (!url || url.trim() === '') return true; // Empty URLs are valid
    
    const lowercaseUrl = url.toLowerCase();
    // Check for dangerous protocols
    if (lowercaseUrl.startsWith("javascript:") || 
        lowercaseUrl.startsWith("data:") || 
        lowercaseUrl.startsWith("vbscript:") ||
        lowercaseUrl.startsWith("file:")) {
      return false;
    }
    
    // Must start with http:// or https://
    return lowercaseUrl.startsWith("http://") || lowercaseUrl.startsWith("https://");
  }

  const handleSaveProfile = async () => {
    try {
      // Enhanced client-side validation with security checks
      const nameValidation = validateDisplayName(name);
      if (!nameValidation.isValid) {
        toast.error(`Display name error: ${nameValidation.errors.join(', ')}`);
        return;
      }

      const pronounsValidation = validatePronouns(pronouns);
      if (!pronounsValidation.isValid) {
        toast.error(`Pronouns error: ${pronounsValidation.errors.join(', ')}`);
        return;
      }

      const aboutValidation = validateAbout(about);
      if (!aboutValidation.isValid) {
        toast.error(`About section error: ${aboutValidation.errors.join(', ')}`);
        return;
      }

      if (bannerColor && !validateBannerColor(bannerColor)) {
        toast.error("Banner color must be a valid hex color (e.g., #FF0000) or Tailwind class (e.g., bg-blue-600)");
        return;
      }

      // Validate URLs for security
      if (avatarUrl && !isValidUrl(avatarUrl)) {
        toast.error("Avatar URL is not valid or contains dangerous content");
        return;
      }

      if (bannerUrl && !isValidUrl(bannerUrl)) {
        toast.error("Banner URL is not valid or contains dangerous content");
        return;
      }

      const profileUpdate: UpdateProfileDTO = {
        displayName: sanitizedName.sanitized,
        pronouns: sanitizedPronouns.sanitized,
        about: sanitizedAbout.sanitized,
        bannerColor: bannerColor,
        avatar: avatarUrl,
        bannerUrl: bannerUrl
      }
      
      await updateUserProfile(profileUpdate)
      setSaveMessage("Profile updated successfully!")
      toast.success("Profile updated successfully!")
    } catch (error) {
      console.error("Error saving profile:", error)
      
      // Enhanced error handling for security validation
      if (error && typeof error === 'object' && 'response' in error) {
        const errorResponse = (error as any).response;
        if (errorResponse?.status === 400) {
          if (errorResponse?.data?.message?.includes('sanitization') || 
              errorResponse?.data?.message?.includes('security')) {
            toast.error(`Security validation failed: ${errorResponse.data.message}`);
          } else if (errorResponse?.data?.message) {
            toast.error(`Validation error: ${errorResponse.data.message}`);
          } else {
            toast.error("Invalid input detected. Please check your entries for any special characters or HTML content.");
          }
        } else {
          toast.error("Error updating profile. Please check your input and try again.");
        }
      } else {
        toast.error("Error updating profile. Please try again.");
      }
      
      setSaveMessage("Error updating profile. Please try again.")
    }
  }

  return (
    <TooltipProvider>
      <div className={`profile-container theme-${theme}`}>
        <Toaster position="top-right" />
        
        {/* Settings Panel */}
        <div className="flex w-full flex-col gap-8 lg:w-1/2 order-2 lg:order-1">
          <div className="rounded-2xl border border-white/10 bg-white/10 p-8 backdrop-blur-md shadow-lg">
            <h2 className="mb-6 text-lg font-medium text-white">Profile</h2>

            <div className="space-y-6">
              <div className="space-y-2">
                <Label htmlFor="display-name" className="text-xs font-medium text-white/80">
                  DISPLAY NAME
                </Label>
                <SecureInput
                  value={name}
                  onChange={setName}
                  placeholder="Enter your display name"
                  maxLength={50}
                  minLength={1}
                  required={true}
                  label=""
                  className="border-white/10 bg-white/5 text-sm transition-colors focus:border-white/20 focus:bg-white/10"
                  showCharacterCount={true}
                  realTimeValidation={true}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="pronouns" className="text-xs font-medium text-white/80">
                  PRONOUNS
                </Label>
                <SecureInput
                  value={pronouns}
                  onChange={setPronouns}
                  placeholder="Add your pronouns"
                  maxLength={20}
                  label=""
                  className="border-white/10 bg-white/5 text-sm transition-colors focus:border-white/20 focus:bg-white/10"
                  showCharacterCount={true}
                  realTimeValidation={true}
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
                  Click on your avatar to upload a new image. Maximum size: 5MB. Only secure image URLs are allowed.
                </p>
              </div>

              <div className="space-y-3">
                <Label className="text-xs font-medium text-white/80">PROFILE BANNER</Label>
                
                {hasRole('MONARCH') || hasRole('ADMIN') || hasRole('MODERATOR') ? (
                  <BannerUpload
                    currentBannerUrl={bannerUrl}
                    bannerColor={bannerColor}
                    onUpload={handleBannerUpload}
                    onRemove={handleRemoveBanner}
                    showRemoveButton={!!bannerUrl}
                  />
                ) : (
                  <div className="relative h-32 w-full overflow-hidden rounded-lg border border-white/10">
                    {bannerUrl ? (
                      <img src={bannerUrl} alt="Banner" className="h-full w-full object-cover" />
                    ) : (
                      <div 
                        className="h-full w-full"
                        style={{ backgroundColor: bannerColor.startsWith('bg-') ? undefined : bannerColor }}
                      >
                        {bannerColor.startsWith('bg-') && <div className={`h-full w-full ${bannerColor}`} />}
                      </div>
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
                  {hasRole('MONARCH') || hasRole('ADMIN') || hasRole('MODERATOR') 
                    ? "Click on the banner to upload a new image. Maximum size: 5MB. Only secure image URLs are allowed."
                    : ""}
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="about" className="text-xs font-medium text-white/80">
                  ABOUT ME
                </Label>
                <SecureInput
                  value={about}
                  onChange={setAbout}
                  placeholder="Tell us about yourself..."
                  maxLength={200}
                  type="textarea"
                  label=""
                  className="min-h-[120px] border-white/10 bg-white/5 text-sm transition-colors focus:border-white/20 focus:bg-white/10"
                  showCharacterCount={true}
                  realTimeValidation={true}
                />
              </div>

              <div className="space-y-3">
                <Label className="text-xs font-medium text-white/80">BANNER COLOR</Label>
                <ColorPickerPopover color={bannerColor} onChange={handleColorChange} />
                <p className="text-xs text-white/60">
                  Choose any color for your profile banner background.
                </p>
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
        <div className="order-1 lg:order-2">
          <ProfilePreview 
            bannerColor={bannerColor}
            bannerUrl={bannerUrl}
            name={sanitizedName.sanitized || (user?.username || "")}
            about={sanitizedAbout.sanitized}
            pronouns={sanitizedPronouns.sanitized}
            user={{ ...user, avatar: avatarUrl || (isUsingCustomAvatar ? "" : user?.avatar) }}
            showEditButton={false}
            onClick={() => {
              // For example, navigate to the user's detailed profile page.
            }}
            equippedBadgeIds={profile?.equippedBadgeId ? [profile.equippedBadgeId] : []}
            badgeMap={profile?.equippedBadgeId && profile?.badgeUrl ? { [profile.equippedBadgeId]: profile.badgeUrl } : {}}
            badgeNames={profile?.equippedBadgeId && profile?.badgeName ? { [profile.equippedBadgeId]: profile.badgeName } : {}}
          />
        </div>
      </div>
    </TooltipProvider>
  )
}

