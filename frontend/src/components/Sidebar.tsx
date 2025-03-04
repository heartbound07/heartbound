import { useNavigate, useLocation } from "react-router-dom"
import { useAuth } from "@/contexts/auth"
import "@/assets/sidebar.css"
import "@/assets/styles/fonts.css"
import { MdDashboard } from "react-icons/md"
import { IoSettingsSharp } from "react-icons/io5"
import { useState, useRef, useEffect } from "react"
import { ChevronDown, ChevronRight } from "lucide-react"
import { ProfilePreview } from "@/components/ui/profile/ProfilePreview"
import ReactDOM from "react-dom"
import valorantLogo from '@/assets/images/valorant-logo.png'

/**
 * DashboardNavigation
 *
 * A modern sidebar navigation with glassmorphic effect, smooth transitions,
 * and an enhanced visual hierarchy for improved user experience.
 * 
 * @param {Object} props - Component props
 * @param {string} props.theme - Optional theme variant: 'dashboard' or 'default'
 */
export function DashboardNavigation({ theme = 'default' }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const [gamesExpanded, setGamesExpanded] = useState(() => {
    // Auto-expand if we're on a game page
    return location.pathname.includes('/dashboard/valorant')
  })
  const [showProfilePreview, setShowProfilePreview] = useState(false)
  const profileSectionRef = useRef<HTMLDivElement>(null)
  const profilePreviewRef = useRef<HTMLDivElement>(null)
  const [popupPosition, setPopupPosition] = useState({ top: 0, left: 0 })

  // Define games submenu items
  const gameItems = [
    { 
      path: "/dashboard/valorant", 
      label: "VALORANT", 
      logo: valorantLogo,
      id: "valorant"
    },
    // More games will be added here in the future
  ]

  const navItems = [
    { 
      path: "/dashboard", 
      label: "Discover", 
      icon: <MdDashboard size={20} />,
      hasSubmenu: true
    },
    { path: "/dashboard/settings", label: "Settings", icon: <IoSettingsSharp size={20} /> },
    // Profile tab removed as it's now integrated with the user profile section
  ]
  
  // Update popup position whenever profile section or visibility changes
  useEffect(() => {
    if (profileSectionRef.current && showProfilePreview) {
      const rect = profileSectionRef.current.getBoundingClientRect();
      setPopupPosition({
        top: rect.top,
        left: rect.right + 10 // 10px offset from the sidebar
      });
    }
  }, [showProfilePreview, profileSectionRef.current]);
  
  // Close the profile preview when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        showProfilePreview &&
        profilePreviewRef.current && 
        profileSectionRef.current &&
        !profilePreviewRef.current.contains(event.target as Node) &&
        !profileSectionRef.current.contains(event.target as Node)
      ) {
        setShowProfilePreview(false)
      }
    }
    
    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [showProfilePreview])
  
  // Set background color based on theme
  const sidebarBackground = theme === 'dashboard'
    ? "bg-gradient-to-b from-[#5b48e6]/90 to-[#7a67ed]/90" // Match dashboard gradient
    : "bg-gradient-to-b from-slate-800/90 to-slate-900/90"

  // Determine if we're on the main dashboard page
  const isMainDashboard = location.pathname === '/dashboard' || location.pathname === '/dashboard/'
  
  // Determine if we're on a specific game page
  const onGamePage = gameItems.some(game => location.pathname.includes(game.id))

  // Check if we're on the profile page
  const isProfilePage = location.pathname === '/dashboard/profile'

  const handleProfileClick = () => {
    // Don't show the profile preview if we're already on the profile page
    if (isProfilePage) {
      // If on profile page, just navigate there (no need for preview)
      navigate('/dashboard/profile');
      return;
    }
    
    // Otherwise toggle the preview as normal
    setShowProfilePreview(!showProfilePreview);
  }

  // Profile Preview Portal Component
  const ProfilePreviewPortal = () => {
    // Don't render the portal at all if we're on the profile page
    if (!showProfilePreview || isProfilePage) return null;
    
    return ReactDOM.createPortal(
      <div 
        ref={profilePreviewRef}
        style={{
          position: 'absolute',
          top: popupPosition.top,
          left: popupPosition.left,
          zIndex: 9999 // High z-index to ensure it's above everything
        }}
        className="profile-preview-portal"
      >
        <div className="relative">
          <ProfilePreview 
            bannerColor="bg-gradient-to-r from-purple-700 to-blue-500"
            name={user?.username || "User"}
            about="Click Edit Profile to customize your profile!"
            user={user}
            onClick={() => {
              setShowProfilePreview(false)
              navigate('/dashboard/profile')
            }}
          />
        </div>
      </div>,
      document.body
    );
  };

  // Add this useEffect after other useEffects
  useEffect(() => {
    // Close the profile preview when on the profile page
    if (isProfilePage && showProfilePreview) {
      setShowProfilePreview(false);
    }
  }, [isProfilePage]);

  return (
    <aside className={`dashboard-nav h-full flex flex-col ${sidebarBackground} backdrop-blur-md border-r border-white/10 shadow-xl`}>
      {/* Brand Header */}
      <div className="px-6 py-5 border-b border-white/10">
        <h1 
          className="text-center text-white text-xl font-bold"
          style={{ fontFamily: "Grandstander, cursive" }}
        >
          heartbound
        </h1>
      </div>
      
      {/* User Profile Section - Made clickable */}
      <div 
        ref={profileSectionRef}
        className={`relative px-6 py-8 border-b border-white/10 cursor-pointer transition-all duration-200 
          ${isProfilePage ? 'bg-white/5' : 'hover:bg-white/5'}`}
        onClick={handleProfileClick}
      >
        <div className="flex items-center gap-4">
          <div className="relative">
            <img
              src={user?.avatar || "/default-avatar.png"}
              alt={user?.username || "User"}
              className="w-12 h-12 rounded-full object-cover ring-2 ring-primary/50 ring-offset-2 ring-offset-slate-900/50"
            />
            <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 rounded-full border-2 border-slate-800"></div>
          </div>
          <div className="flex flex-col">
            <span className="text-white font-medium text-sm">{user?.username || "User"}</span>
            <span className="text-slate-400 text-xs">Online</span>
          </div>
        </div>
      </div>

      {/* Render the profile preview portal */}
      <ProfilePreviewPortal />

      {/* Navigation Links */}
      <nav className="flex-1 px-4 py-6">
        <ul className="space-y-2">
          {navItems.map((item) => {
            const isActive = item.path === '/dashboard' 
              ? (isMainDashboard || (item.hasSubmenu && onGamePage))
              : location.pathname === item.path
              
            return (
              <li key={item.path}>
                <div>
                  <button
                    onClick={() => {
                      if (item.hasSubmenu) {
                        // If we're on a game page and click Overview, go to dashboard
                        if (onGamePage) {
                          navigate('/dashboard')
                        } else {
                          // Otherwise toggle the dropdown
                          setGamesExpanded(!gamesExpanded)
                        }
                      } else {
                        navigate(item.path)
                      }
                    }}
                    className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 group
                      ${
                        isActive
                          ? "bg-primary/20 text-white shadow-md"
                          : "text-slate-300 hover:bg-white/5 hover:text-white"
                      }`}
                    aria-current={isActive ? "page" : undefined}
                  >
                    <span
                      className={`transition-transform duration-200 ${isActive ? "text-primary" : "text-slate-400 group-hover:text-slate-200"}`}
                    >
                      {item.icon}
                    </span>
                    <span>{item.label}</span>
                    
                    {item.hasSubmenu && !isMainDashboard && (
                      <span className="ml-auto">
                        {gamesExpanded ? 
                          <ChevronDown size={16} className="text-slate-400" /> : 
                          <ChevronRight size={16} className="text-slate-400" />
                        }
                      </span>
                    )}
                    
                    {isActive && !item.hasSubmenu && <div className="ml-auto w-1.5 h-5 bg-primary rounded-full"></div>}
                  </button>
                  
                  {/* Games submenu */}
                  {item.hasSubmenu && gamesExpanded && !isMainDashboard && (
                    <ul className="mt-1 pl-8 space-y-1">
                      {gameItems.map((game) => {
                        const isGameActive = location.pathname.includes(game.path)
                        return (
                          <li key={game.path}>
                            <button
                              onClick={() => navigate(game.path)}
                              className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200
                                ${
                                  isGameActive
                                    ? "bg-primary/10 text-white"
                                    : "text-slate-300 hover:bg-white/5 hover:text-white"
                                }`}
                            >
                              <img 
                                src={game.logo} 
                                alt={`${game.label} logo`} 
                                className="w-5 h-5 object-contain"
                              />
                              <span>{game.label}</span>
                              {isGameActive && <div className="ml-auto w-1 h-4 bg-primary rounded-full"></div>}
                            </button>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      </nav>
    </aside>
  )
}

