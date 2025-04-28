import { useState } from "react"
import "@/assets/dashboard.css"
import { 
  IoPersonOutline, 
  IoColorPaletteOutline, 
  IoNotificationsOutline,
  IoShieldOutline,
  IoHardwareChipOutline,
  IoSaveOutline,
  IoLinkOutline  // Added for Connections tab
} from "react-icons/io5"
import { SiDiscord, SiRiotgames } from "react-icons/si" // Added for service icons
import { Toaster } from "react-hot-toast"
import { Loader2, ExternalLink } from "lucide-react"
import { useAuth } from "@/contexts/auth" // Added auth context import
import { useTheme } from "@/contexts/ThemeContext"
import { Switch } from "@/components/ui/valorant/switch"

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
  const { user, startDiscordOAuth } = useAuth()
  const { theme } = useTheme()
  
  return (
    <div className="min-h-screen p-6 text-white">
      <Toaster position="top-right" />
      
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold mb-8 text-center font-grandstander">Settings</h1>
        
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
                  <div className="bg-white/10 rounded-lg backdrop-blur-sm flex items-center px-4 py-4">
                    <div className="w-full">
                      <div className="text-sm font-medium">Theme</div>
                      <div className="text-white/60 text-sm">Choose your preferred theme</div>
                    </div>
                    <ThemeSelector />
                  </div>
                </div>
              </SettingsCard>
            )}
            
            {/* Notifications Settings Section */}
            {activeTab === "notifications" && (
              <SettingsCard title="Notifications">
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <div className="mb-4 p-4 rounded-full bg-white/10 text-white/70">
                    <IoNotificationsOutline size={36} />
                  </div>
                  <h3 className="text-xl font-medium text-white/90 mb-2">Coming Soon</h3>
                  <p className="text-white/50 max-w-md">
                    Notification preferences will be available in a future update. Stay tuned!
                  </p>
                </div>
              </SettingsCard>
            )}
            
            {/* Privacy Settings Section */}
            {activeTab === "privacy" && (
              <SettingsCard title="Privacy & Security">
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <div className="mb-4 p-4 rounded-full bg-white/10 text-white/70">
                    <IoShieldOutline size={36} />
                  </div>
                  <h3 className="text-xl font-medium text-white/90 mb-2">Coming Soon</h3>
                  <p className="text-white/50 max-w-md">
                    Privacy and security settings will be available in a future update.
                  </p>
                </div>
              </SettingsCard>
            )}
            
            {/* Devices Settings Section */}
            {activeTab === "devices" && (
              <SettingsCard title="Devices & Sessions">
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <div className="mb-4 p-4 rounded-full bg-white/10 text-white/70">
                    <IoHardwareChipOutline size={36} />
                  </div>
                  <h3 className="text-xl font-medium text-white/90 mb-2">Coming Soon</h3>
                  <p className="text-white/50 max-w-md">
                    Device management and session controls will be available in a future update.
                  </p>
                </div>
              </SettingsCard>
            )}
            
            {/* Connections Settings Section - New */}
            {activeTab === "connections" && (
              <SettingsCard title="Account Connections">
                <div className="space-y-6">
                  {/* Discord Connection */}
                  <div className="bg-white/10 rounded-lg backdrop-blur-sm p-4">
                    <div className="flex justify-between items-center">
                      <div className="flex items-center gap-3">
                        <SiDiscord size={24} className="text-[#5865F2]" />
                        <div>
                          <div className="text-sm font-medium">Discord</div>
                          <div className="text-white/60 text-xs">
                            {user?.username ? `Connected as ${user.username}` : 'Not connected'}
                          </div>
                        </div>
                      </div>
                      <button
                        onClick={() => startDiscordOAuth()}
                        className={`px-3 py-2 rounded-md text-sm font-medium transition-colors 
                          ${user?.username 
                            ? "bg-white/10 text-white/70 hover:bg-white/20" 
                            : "bg-[#5865F2] text-white hover:bg-[#4752C4]"}`}
                      >
                        {user?.username ? 'Reconnect' : 'Connect'}
                      </button>
                    </div>
                  </div>
                  
                  {/* Riot Connection */}
                  <div className="bg-white/10 rounded-lg backdrop-blur-sm p-4">
                    <div className="flex justify-between items-center">
                      <div className="flex items-center gap-3">
                        <SiRiotgames size={24} className="text-[#FF4655]" />
                        <div>
                          <div className="text-sm font-medium">Riot Games</div>
                          <div className="text-white/60 text-xs">Not connected</div>
                        </div>
                      </div>
                      <div className="relative">
                        <button
                          disabled
                          className="px-3 py-2 rounded-md bg-white/10 text-white/40 cursor-not-allowed"
                        >
                          Connect
                        </button>
                        <div className="absolute -top-2 -right-2 px-2 py-0.5 bg-primary/80 text-white text-xs rounded-full">
                          Coming Soon
                        </div>
                      </div>
                    </div>
                  </div>
                  
                  <div className="pt-2 pb-1">
                    <div className="flex items-center justify-center gap-1 text-white/40 text-xs">
                      <ExternalLink size={12} />
                      <span>Connections help secure your account and enable gameplay features</span>
                    </div>
                  </div>
                </div>
              </SettingsCard>
            )}
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
    id: "connections", // New tab for connections
    label: "Connections",
    icon: <IoLinkOutline size={20} />
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

function ThemeSelector() {
  const { theme, setTheme } = useTheme();
  
  return (
    <div className="flex gap-3">
      <button
        onClick={() => setTheme('default')}
        className={`w-14 h-14 rounded-md relative transition-all duration-200 ${
          theme === 'default' 
            ? 'ring-2 ring-primary ring-offset-2 ring-offset-gray-900 scale-110' 
            : 'opacity-70 hover:opacity-100'
        }`}
        aria-label="Default theme"
      >
        <div className="absolute inset-0 bg-gradient-to-b from-[#6B5BE6] to-[#8878f0] rounded-md" />
        {theme === 'default' && (
          <div className="absolute bottom-1 right-1 w-4 h-4 bg-primary rounded-full flex items-center justify-center">
            <div className="w-2 h-2 bg-white rounded-full"></div>
          </div>
        )}
      </button>
      
      <button
        onClick={() => setTheme('dark')}
        className={`w-14 h-14 rounded-md relative transition-all duration-200 ${
          theme === 'dark' 
            ? 'ring-2 ring-[#FF4655] ring-offset-2 ring-offset-gray-900 scale-110' 
            : 'opacity-70 hover:opacity-100'
        }`}
        aria-label="Dark theme"
      >
        <div className="absolute inset-0 bg-gradient-to-b from-[#0F1923] to-[#1F2731] rounded-md" />
        {theme === 'dark' && (
          <div className="absolute bottom-1 right-1 w-4 h-4 bg-[#FF4655] rounded-full flex items-center justify-center">
            <div className="w-2 h-2 bg-white rounded-full"></div>
          </div>
        )}
      </button>
    </div>
  );
}
