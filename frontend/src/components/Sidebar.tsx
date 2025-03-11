import { useNavigate, useLocation } from "react-router-dom"
import { useAuth } from "@/contexts/auth"
import "@/assets/sidebar.css"
import "@/assets/styles/fonts.css"
import { MdDashboard, MdAdminPanelSettings } from "react-icons/md"
import { IoSettingsSharp } from "react-icons/io5"
import { useState, useRef, useEffect } from "react"
import { ChevronDown, ChevronRight, Menu } from "lucide-react"
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
 * @param {function} props.onCollapseChange - Callback function to communicate collapse state changes
 */
interface DashboardNavigationProps {
  theme?: string;
  onCollapseChange?: (collapsed: boolean) => void;
}

export function DashboardNavigation({ theme = 'default', onCollapseChange }: DashboardNavigationProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, profile, hasRole } = useAuth()
  const [gamesExpanded, setGamesExpanded] = useState(() => {
    // Auto-expand if we're on a game page
    return location.pathname.includes('/dashboard/valorant')
  })
  const [showProfilePreview, setShowProfilePreview] = useState(false)
  const profileSectionRef = useRef<HTMLDivElement>(null)
  const profilePreviewRef = useRef<HTMLDivElement>(null)
  const [popupPosition, setPopupPosition] = useState({ top: 0, left: 0 })
  
  // Add sidebar collapse state
  const [isCollapsed, setIsCollapsed] = useState(false)
  
  // Detect window size for responsive behavior
  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth < 768) {
        setIsCollapsed(true)
      }
    }
    
    // Set initial state based on window size
    handleResize()
    
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

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

  // Check if current user is an admin
  const isAdmin = hasRole('ADMIN');

  // Determine if we're on the admin page
  const isAdminPage = location.pathname === '/dashboard/admin';

  // Add admin panel to nav items if the user is an admin
  const navItems = [
    { 
      path: "/dashboard", 
      label: "Discover", 
      icon: <MdDashboard size={20} />,
      hasSubmenu: true
    },
    // Only show admin panel option if user has ADMIN role
    ...(isAdmin ? [
      {
        path: "/dashboard/admin",
        label: "Admin Panel",
        icon: <MdAdminPanelSettings size={20} />,
        hasSubmenu: false
      }
    ] : [])
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
    : "bg-gradient-to-b from-[#0F1923]/90 to-[#1F2731]/90" // Match Valorant colors

  // Determine if we're on the main dashboard page
  const isMainDashboard = location.pathname === '/dashboard' || location.pathname === '/dashboard/'
  
  // Determine if we're on a specific game page
  const onGamePage = gameItems.some(game => location.pathname.includes(game.id))

  // Check if we're on the profile page
  const isProfilePage = location.pathname === '/dashboard/profile'
  
  // Check if we're on the settings page
  const isSettingsPage = location.pathname === '/dashboard/settings'

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
        className="fixed z-[9999]"
        style={{ 
          top: `${popupPosition.top}px`, 
          left: `${popupPosition.left}px` 
        }}
      >
        <ProfilePreview
          bannerColor={profile?.bannerColor || "bg-white/10"}
          bannerUrl={profile?.bannerUrl}
          name={profile?.displayName || user?.username}
          about={profile?.about}
          pronouns={profile?.pronouns}
          user={user}
          onClick={() => {
            navigate('/dashboard/profile');
            setShowProfilePreview(false);
          }}
        />
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

  // Add effect to communicate collapse state changes
  useEffect(() => {
    if (onCollapseChange) {
      onCollapseChange(isCollapsed);
    }
  }, [isCollapsed, onCollapseChange]);

  return (
    <aside className={`dashboard-nav h-full flex flex-col ${sidebarBackground} backdrop-blur-md border-r border-white/10 shadow-xl ${isCollapsed ? 'collapsed' : 'expanded'}`}>
      {/* Brand Header with Toggle Button - Updated for proper centering in collapsed state */}
      <div className={`px-4 py-4 border-b border-white/10 flex items-center ${isCollapsed ? 'justify-center' : 'justify-between'}`}>
        <button 
          onClick={() => setIsCollapsed(!isCollapsed)} 
          className="toggle-sidebar-btn text-white/80 hover:text-white p-2 rounded-md hover:bg-white/10 transition-colors"
          aria-label={isCollapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          <Menu size={20} />
        </button>
        
        {!isCollapsed && (
          <>
            <h1 
              className="brand-text text-center text-white text-xl font-bold"
              style={{ fontFamily: "Grandstander, cursive" }}
            >
              heartbound
            </h1>
            
            {/* Empty div to balance the flex layout */}
            <div className="w-8"></div>
          </>
        )}
      </div>
      
      {/* User Profile Section - Avatar only when collapsed */}
      <div 
        ref={profileSectionRef}
        className={`relative px-4 py-4 cursor-pointer transition-all duration-200 
          ${isProfilePage ? 'bg-white/5' : 'hover:bg-white/5'}`}
        onClick={handleProfileClick}
      >
        <div className={`flex ${isCollapsed ? 'flex-col items-center' : 'items-center justify-center'} ${isCollapsed ? '' : 'gap-3'}`}>
          <div className="relative">
            <img
              src={user?.avatar || "/default-avatar.png"}
              alt={user?.username || "User"}
              className={`rounded-full object-cover ring-2 ring-primary/50 ring-offset-2 ring-offset-slate-900/50 ${isCollapsed ? 'w-10 h-10' : 'w-12 h-12'}`}
            />
            <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 rounded-full border-2 border-slate-800"></div>
          </div>
          
          {/* User info - only shown when expanded */}
          {!isCollapsed && (
            <div className="flex flex-col items-center">
              {/* Display name on top */}
              <span className="text-white font-medium text-sm truncate text-center max-w-full">
                {profile?.displayName || user?.username || "User"}
              </span>
              
              {/* Username and pronouns on a row below */}
              <div className="flex items-center justify-center gap-1 mt-0.5">
                <span className="text-white/70 text-xs truncate text-center">
                  {user?.username || "Guest"}
                </span>
                {profile?.pronouns && (
                  <span className="text-white/60 text-xs truncate">
                    • {profile.pronouns}
                  </span>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Render the profile preview portal */}
      <ProfilePreviewPortal />

      {/* Navigation menu items - Icons only when collapsed */}
      <nav className="flex-1 px-4 py-4">
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
                        // Always navigate to dashboard when main button is clicked
                        if (!isMainDashboard) {
                          navigate('/dashboard')
                        } else {
                          // If already on dashboard, also toggle dropdown
                          setGamesExpanded(!gamesExpanded)
                        }
                      } else {
                        navigate(item.path)
                      }
                    }}
                    className={`w-full flex ${isCollapsed ? 'items-center justify-center' : 'items-center justify-center'} ${isCollapsed ? 'gap-1' : 'gap-2'} px-3 py-3 rounded-lg text-sm font-medium transition-all duration-200 group
                      ${
                        isActive
                          ? "bg-primary/20 text-white shadow-md"
                          : "text-slate-300 hover:bg-white/5 hover:text-white"
                      }`}
                    aria-current={isActive ? "page" : undefined}
                  >
                    <span
                      className={`transition-transform duration-200 ${isCollapsed ? '' : ''} ${isActive ? "text-primary" : "text-slate-400 group-hover:text-slate-200"}`}
                    >
                      {item.icon}
                    </span>
                    
                    {/* Only show label when not collapsed */}
                    {!isCollapsed && (
                      <span className="sidebar-label">{item.label}</span>
                    )}
                    
                    {item.hasSubmenu && !isMainDashboard && !isCollapsed && (
                      <div 
                        onClick={(e) => {
                          e.stopPropagation(); // Prevent parent button click
                          setGamesExpanded(!gamesExpanded);
                        }}
                        className="absolute right-2 p-2 rounded-md hover:bg-white/10 cursor-pointer"
                      >
                        <ChevronRight 
                          size={16} 
                          className={`text-slate-400 transition-transform ${
                            gamesExpanded ? "animate-rotate-down" : "animate-rotate-up"
                          }`} 
                        />
                      </div>
                    )}
                    
                    {isActive && !item.hasSubmenu && !isCollapsed && <div className="absolute right-2 w-1.5 h-5 bg-primary rounded-full"></div>}
                  </button>
                  
                  {/* Games submenu - unchanged */}
                  {item.hasSubmenu && !isMainDashboard && !isCollapsed && (
                    <div className={gamesExpanded ? "animate-slideDown" : "animate-slideUp"}>
                      <ul className="mt-1 pl-8 space-y-1">
                        {gameItems.map((game) => {
                          const isGameActive = location.pathname.includes(game.path)
                          return (
                            <li key={game.path}>
                              <button
                                onClick={() => navigate(game.path)}
                                className={`w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200
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
                                {isGameActive && <div className="absolute right-2 w-1 h-4 bg-primary rounded-full"></div>}
                              </button>
                            </li>
                          )
                        })}
                      </ul>
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      </nav>

      {/* Settings Footer - Icon only when collapsed */}
      <div className="mt-auto px-4 pb-6 border-t border-white/10 pt-4">
        <button
          onClick={() => navigate('/dashboard/settings')}
          className={`w-full flex ${isCollapsed ? 'items-center justify-center' : 'items-center justify-center'} gap-2 px-3 py-3 rounded-lg text-sm font-medium transition-all duration-200 group
            ${
              isSettingsPage
                ? "bg-primary/20 text-white shadow-md"
                : "text-slate-300 hover:bg-white/5 hover:text-white"
            }`}
          aria-current={isSettingsPage ? "page" : undefined}
        >
          <span
            className={`transition-transform duration-200 ${isSettingsPage ? "text-primary" : "text-slate-400 group-hover:text-slate-200"}`}
          >
            <IoSettingsSharp size={20} />
          </span>
          
          {/* Only show label when not collapsed */}
          {!isCollapsed && (
            <span className="sidebar-label">Settings</span>
          )}
          
          {isSettingsPage && !isCollapsed && <div className="absolute right-2 w-1.5 h-5 bg-primary rounded-full"></div>}
        </button>
      </div>
    </aside>
  )
}

