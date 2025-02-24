import React, { useState } from "react";
import { motion } from "framer-motion";
import { useAuth } from "@/contexts/auth";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/profile/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/profile/input";
import { Label } from "@/components/ui/profile/label";
import { Textarea } from "@/components/ui/profile/textarea";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/profile/tooltip";
import "@/assets/profile.css";

// Dummy icons (or import from lucide-react if available)
function CameraIcon() {
  return (
    <svg className="icon" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path d="M5 8h14M12 1v22" />
    </svg>
  );
}
function UploadIcon() {
  return (
    <svg className="icon" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1" />
    </svg>
  );
}
function PlusIcon() {
  return (
    <svg className="icon" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path d="M12 4v16M4 12h16" />
    </svg>
  );
}

export function ProfilePage() {
  const { user, error: authError } = useAuth();

  // Local state for the profile settings
  const [name, setName] = useState(user?.username || "");
  const [pronouns, setPronouns] = useState("");
  const [about, setAbout] = useState("");
  const [bannerColor, setBannerColor] = useState("bg-black");
  const [avatarHover, setAvatarHover] = useState(false);
  const [bannerHover, setBannerHover] = useState(false);

  const bannerColors = [
    "bg-black",
    "bg-blue-600",
    "bg-purple-600",
    "bg-rose-600",
    "bg-emerald-600",
  ];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    // Implement your API call to update profile with
    // { name, pronouns, about, bannerColor } (plus avatar/banner changes if applicable)
  };

  return (
    <TooltipProvider>
      <div className="profile-container">
        {/* Settings Panel */}
        <form
          onSubmit={handleSubmit}
          className="settings-panel bg-black/60 rounded-2xl shadow-lg backdrop-blur-lg p-8"
        >
          <div className="settings-card">
            <h2 className="settings-title text-2xl font-semibold mb-4">Profile Settings</h2>
            <div className="settings-form space-y-6">
              <div className="form-group">
                <Label htmlFor="display-name" className="form-label uppercase tracking-wide">
                  Display Name
                </Label>
                <Input
                  id="display-name"
                  className="form-input"
                  placeholder="Enter your display name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>
              <div className="form-group">
                <Label htmlFor="pronouns" className="form-label uppercase tracking-wide">
                  Pronouns
                </Label>
                <Input
                  id="pronouns"
                  className="form-input"
                  placeholder="Add your pronouns"
                  value={pronouns}
                  onChange={(e) => setPronouns(e.target.value)}
                />
              </div>
              <div className="avatar-group">
                <Label className="form-label uppercase tracking-wide">Avatar</Label>
                <div
                  className="avatar-wrapper relative group"
                  onMouseEnter={() => setAvatarHover(true)}
                  onMouseLeave={() => setAvatarHover(false)}
                >
                  <Avatar className="avatar transition-transform duration-200 ease-in-out group-hover:scale-105">
                    <AvatarImage src={user?.avatar || "/default-avatar.png"} />
                    <AvatarFallback>{name.charAt(0) || "P"}</AvatarFallback>
                  </Avatar>
                  <div className={`avatar-overlay ${avatarHover ? "opacity-100" : "opacity-0"} transition-opacity duration-200`}>
                    <CameraIcon />
                  </div>
                </div>
              </div>
              <div className="banner-group">
                <Label className="form-label uppercase tracking-wide">Profile Banner</Label>
                <div
                  className="banner-wrapper relative group"
                  onMouseEnter={() => setBannerHover(true)}
                  onMouseLeave={() => setBannerHover(false)}
                >
                  <div className={`banner ${bannerColor} rounded-lg`} />
                  <div className={`banner-overlay ${bannerHover ? "opacity-100" : "opacity-0"} transition-opacity duration-200`}>
                    <UploadIcon />
                  </div>
                </div>
              </div>
              <div className="form-group">
                <Label htmlFor="about" className="form-label uppercase tracking-wide">
                  About Me
                </Label>
                <Textarea
                  id="about"
                  value={about}
                  onChange={(e) => setAbout(e.target.value)}
                  className="form-textarea"
                  placeholder="Tell us about yourself..."
                  maxLength={200}
                />
                <div className="character-count text-right">
                  <span>{about.length}/200</span>
                </div>
              </div>
              <div className="form-group">
                <Label className="form-label uppercase tracking-wide">Banner Color</Label>
                <div className="banner-colors flex gap-3">
                  {bannerColors.map((color) => (
                    <Tooltip key={color}>
                      <TooltipTrigger>
                        <div
                          className={`color-swatch ${color} ${bannerColor === color ? "selected" : ""}`}
                          onClick={() => setBannerColor(color)}
                        />
                      </TooltipTrigger>
                      <TooltipContent>
                        {color.replace("bg-", "").replace("-600", "")}
                      </TooltipContent>
                    </Tooltip>
                  ))}
                </div>
              </div>
            </div>
          </div>
          <Button type="submit" className="save-button mt-8 w-full py-3">
            Save Changes
          </Button>
        </form>

        {/* Preview Panel */}
        <div className="preview-panel">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="preview-card bg-gradient-to-tr from-zinc-800 to-zinc-900 rounded-2xl shadow-xl overflow-hidden"
          >
            <div className="preview-banner relative">
              <Button size="sm" variant="secondary" className="status-button absolute right-4 top-4 gap-2">
                <PlusIcon />
                Add Status
              </Button>
              <div className="preview-avatar-wrapper absolute bottom-0 left-4 translate-y-1/2">
                <Avatar className="preview-avatar border-4 border-zinc-900 shadow-xl">
                  <AvatarImage src={user?.avatar || "/default-avatar.png"} />
                  <AvatarFallback>{name.charAt(0) || "P"}</AvatarFallback>
                </Avatar>
              </div>
            </div>
            <div className="preview-content p-6 pt-16">
              <div className="preview-header flex items-center gap-3 mb-4">
                <h2 className="preview-name text-2xl font-bold">{name || "Display Name"}</h2>
                <span className="preview-role text-sm text-zinc-400">prospect</span>
              </div>
              <p className="preview-about text-base text-zinc-300">
                {about || "Your about me section will appear here..."}
              </p>
              <Button className="example-button mt-6 w-full">Example Button</Button>
            </div>
          </motion.div>
        </div>
      </div>
    </TooltipProvider>
  );
} 