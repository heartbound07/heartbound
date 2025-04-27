import { useState } from "react"
import "@/assets/dashboard.css"
import { 
  IoPersonOutline, 
  IoColorPaletteOutline, 
  IoNotificationsOutline,
  IoShieldOutline,
  IoHardwareChipOutline,
  IoSaveOutline 
} from "react-icons/io5"
import { Toaster } from "react-hot-toast"
import { Loader2 } from "lucide-react"

// For future implementation 
// import { useAuth } from "@/contexts/auth"

/**
 * SettingsPage Component
 * 
 * A modern, organized settings interface with categorized sections using
 * glassmorphism and responsive layout, matching the dashboard aesthetic.
 */
export function SettingsPage() {
  const [activeTab, setActiveTab] = useState("appearance")
  const [isSaving, setIsSaving] = useState(false)
  
  // Mock save function - To be implemented later
  const handleSave = () => {
    setIsSaving(true)
    // Simulate API call delay
    setTimeout(() => setIsSaving(false), 1000)
  }
  
  return (
    <div className="min-h-screen p-6 text-white">
      <Toaster position="top-right" />
      
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold mb-8">Settings</h1>
        
        <div className="flex flex-col lg:flex-row gap-8">
          {/* Settings Categories Navigation */}
          <div className="w-full lg:w-1/4">
            <div className="bg-white/15 backdrop-blur-xl rounded-xl border border-white/20 shadow-lg p-4 sticky top-4">
              <nav className="space-y-1">
                {settingsTabs.map(tab => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg text-left transition-all duration-200
                      ${activeTab === tab.id
                        ? "bg-primary/20 text-white"
                        : "text-white/70 hover:bg-white/10 hover:text-white"
                      }`}
                  >
                    <span className={activeTab === tab.id ? "text-primary" : "text-white/60"}>
                      {tab.icon}
                    </span>
                    <span className="font-medium">{tab.label}</span>
                  </button>
                ))}
              </nav>
            </div>
          </div>
          
          {/* Settings Content Area */}
          <div className="w-full lg:w-3/4 space-y-8">
            {/* Appearance Settings Section */}
            {activeTab === "appearance" && (
              <SettingsCard title="Appearance">
                <div className="space-y-6">
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Theme</div>
                      <div className="text-white/60 text-sm">Choose your preferred theme</div>
                    </div>
                    <div className="flex gap-2">
                      <div className="w-10 h-10 rounded-md bg-gradient-to-b from-[#6B5BE6] to-[#8878f0]"></div>
                      <div className="w-10 h-10 rounded-md bg-gradient-to-b from-[#0F1923] to-[#1F2731]"></div>
                    </div>
                  </div>
                  
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Text Size</div>
                      <div className="text-white/60 text-sm">Adjust the size of text</div>
                    </div>
                    <div className="w-40 h-6 bg-white/10 rounded-full"></div>
                  </div>
                  
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Motion Effects</div>
                      <div className="text-white/60 text-sm">Toggle animation effects</div>
                    </div>
                    <div className="w-12 h-6 bg-white/10 rounded-full"></div>
                  </div>
                </div>
              </SettingsCard>
            )}
            
            {/* Notifications Settings Section */}
            {activeTab === "notifications" && (
              <SettingsCard title="Notification Preferences">
                <div className="space-y-6">
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Game Invites</div>
                      <div className="text-white/60 text-sm">Receive notifications for game invites</div>
                    </div>
                    <div className="w-12 h-6 bg-white/10 rounded-full"></div>
                  </div>
                  
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Friend Requests</div>
                      <div className="text-white/60 text-sm">Notifications for new friend requests</div>
                    </div>
                    <div className="w-12 h-6 bg-white/10 rounded-full"></div>
                  </div>
                  
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Email Notifications</div>
                      <div className="text-white/60 text-sm">Receive important updates via email</div>
                    </div>
                    <div className="w-12 h-6 bg-white/10 rounded-full"></div>
                  </div>
                </div>
              </SettingsCard>
            )}
            
            {/* Privacy Settings Section */}
            {activeTab === "privacy" && (
              <SettingsCard title="Privacy & Security">
                <div className="space-y-6">
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Profile Visibility</div>
                      <div className="text-white/60 text-sm">Control who can see your profile</div>
                    </div>
                    <div className="w-32 h-10 bg-white/10 rounded-md"></div>
                  </div>
                  
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Two-Factor Authentication</div>
                      <div className="text-white/60 text-sm">Enable 2FA for extra security</div>
                    </div>
                    <div className="w-12 h-6 bg-white/10 rounded-full"></div>
                  </div>
                  
                  <div className="h-16 bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Data Collection</div>
                      <div className="text-white/60 text-sm">Manage data sharing preferences</div>
                    </div>
                    <div className="w-12 h-6 bg-white/10 rounded-full"></div>
                  </div>
                </div>
              </SettingsCard>
            )}
            
            {/* Devices Settings Section */}
            {activeTab === "devices" && (
              <SettingsCard title="Devices & Sessions">
                <div className="space-y-6">
                  <div className="bg-white/10 rounded-lg backdrop-blur-sm p-4">
                    <div className="flex justify-between items-center mb-2">
                      <div>
                        <div className="text-sm font-medium">Current Session</div>
                        <div className="text-white/60 text-xs">Windows • Chrome • Last active now</div>
                      </div>
                      <div className="px-2 py-1 text-xs bg-green-500/20 text-green-300 rounded-full">Active</div>
                    </div>
                  </div>
                  
                  <div className="bg-white/10 rounded-lg backdrop-blur-sm p-4">
                    <div className="flex justify-between items-center mb-2">
                      <div>
                        <div className="text-sm font-medium">Mobile Device</div>
                        <div className="text-white/60 text-xs">iOS • Safari • Last active 2 days ago</div>
                      </div>
                      <div className="w-20 h-8 bg-white/10 rounded-md"></div>
                    </div>
                  </div>
                  
                  <div className="h-12 flex justify-center items-center">
                    <div className="w-48 h-10 bg-white/10 rounded-md flex items-center justify-center">
                      <span className="text-white/60 text-sm">Log Out All Devices</span>
                    </div>
                  </div>
                </div>
              </SettingsCard>
            )}
            
            {/* Save Button */}
            <div className="flex justify-end mt-6">
              <button
                onClick={handleSave}
                disabled={isSaving}
                className="flex items-center gap-2 px-6 py-3 rounded-lg bg-primary hover:bg-primary/90 text-white font-medium transition-colors disabled:opacity-70"
              >
                {isSaving ? (
                  <>
                    <Loader2 className="h-5 w-5 animate-spin" />
                    <span>Saving...</span>
                  </>
                ) : (
                  <>
                    <IoSaveOutline size={20} />
                    <span>Save Settings</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// Settings card component for consistent styling
function SettingsCard({ title, children }: { title: string, children: React.ReactNode }) {
  return (
    <div className="bg-white/15 backdrop-blur-xl rounded-xl border border-white/20 shadow-lg p-6">
      <h2 className="text-xl font-semibold mb-6">{title}</h2>
      {children}
    </div>
  )
}

// Settings tabs data
const settingsTabs = [
  {
    id: "appearance",
    label: "Appearance",
    icon: <IoColorPaletteOutline size={20} />
  },
  {
    id: "notifications",
    label: "Notifications",
    icon: <IoNotificationsOutline size={20} />
  },
  {
    id: "privacy",
    label: "Privacy & Security",
    icon: <IoShieldOutline size={20} />
  },
  {
    id: "devices",
    label: "Devices & Sessions",
    icon: <IoHardwareChipOutline size={20} />
  }
]
