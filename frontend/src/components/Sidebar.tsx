import { useNavigate, useLocation } from "react-router-dom"
import { useAuth } from "@/contexts/auth"
import "@/assets/sidebar.css"
import "@/assets/styles/fonts.css"
import "@/assets/animations.css"
import "@/assets/z-index-system.css"
import { MdDashboard, MdAdminPanelSettings } from "react-icons/md"
import { IoSettingsSharp } from "react-icons/io5"
import { FaCoins, FaTrophy, FaShoppingCart, FaBoxOpen } from "react-icons/fa"
import { useState, useRef, useEffect } from "react"
import { ChevronRight, Menu, LogOut, Users } from "lucide-react"
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
  const { user, profile, hasRole, logout } = useAuth()
  
  // Add this to detect if we're on the Valorant page
  const isValorantPage = location.pathname.includes('/valorant')
  
  // Use the detected page to override the theme if needed
  const effectiveTheme = isValorantPage ? 'default' : theme
  
  const [gamesExpanded, setGamesExpanded] = useState(() => {
    // Auto-expand if we're on a game page
    return location.pathname.includes('/dashboard/valorant')
  })
  const [showProfilePreview, setShowProfilePreview] = useState(false)
  const profileSectionRef = useRef<HTMLDivElement>(null)
  const profilePreviewRef = useRef<HTMLDivElement>(null)
  const [popupPosition, setPopupPosition] = useState({ top: 0, left: 0 })
  
  // Add sidebar collapse state with localStorage persistence
  const [isCollapsed, setIsCollapsed] = useState(() => {
    // Initialize from localStorage if available, otherwise default to false
    const savedState = localStorage.getItem('sidebar-collapsed')
    return savedState ? JSON.parse(savedState) : false
  })
  
  // Add a new state for tracking mobile overlay visibility
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  
  // Update localStorage and dispatch a custom event when isCollapsed changes
  useEffect(() => {
    localStorage.setItem('sidebar-collapsed', JSON.stringify(isCollapsed))
    
    // Dispatch a custom event that other components can listen for
    const event = new CustomEvent('sidebarStateChange', { 
      detail: { collapsed: isCollapsed } 
    });
    window.dispatchEvent(event);
    
    // Notify parent components about the change
    if (onCollapseChange) {
      onCollapseChange(isCollapsed)
    }
  }, [isCollapsed, onCollapseChange])
  
  // Update the resize handler to also close mobile sidebar when resizing
  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth < 768) {
        setIsCollapsed(true);
        setIsMobileOpen(false); // Close mobile sidebar when resizing
      }
    }
    
    // Set initial state based on window size
    handleResize();
    
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

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
  
  // Determine if we're on the leaderboard page

  // Determine if we're on the shop page

  // Determine if we're on the inventory page

  // Add admin panel to nav items if the user is an admin
  const navItems = [
    { 
      path: "/dashboard", 
      label: "Discover", 
      icon: <MdDashboard size={20} />,
      hasSubmenu: true
    },
    {
      label: "Pairings",
      path: "/dashboard/pairings",
      icon: <Users size={20} />,
      exact: true
    },
    {
      path: "/dashboard/leaderboard",
      label: "Leaderboard",
      icon: <FaTrophy size={20} />,
      hasSubmenu: false
    },
    // Shop navigation item
    {
      path: "/dashboard/shop",
      label: "Shop",
      icon: <FaShoppingCart size={20} />,
      hasSubmenu: false
    },
    // Inventory navigation item
    {
      path: "/dashboard/inventory",
      label: "Inventory",
      icon: <FaBoxOpen size={20} />,
      hasSubmenu: false
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
      
      // Adjust position for mobile
      if (window.innerWidth < 768) {
        setPopupPosition({
          top: rect.bottom + 10, // Position below profile section on mobile
          left: window.innerWidth / 2 - 150 // Center horizontally
        });
      } else {
        // Desktop positioning (unchanged)
        setPopupPosition({
          top: rect.top,
          left: rect.right + 10
        });
      }
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
  const sidebarBackground = effectiveTheme === 'dashboard'
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
        className="fixed z-[1400]"
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
          equippedBadgeIds={profile?.equippedBadgeIds || []}
          badgeMap={profile?.badgeUrls || {}}
          badgeNames={profile?.badgeNames || {}}
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

  // Updated function to handle logout without confirmation
  const handleLogout = async () => {
    try {
      await logout();
      navigate('/login');
    } catch (error) {
      console.error('Logout failed:', error);
      // Optionally, display an error message to the user here
    }
  };

  // Modify the toggleMobileSidebar function to ensure sidebar is expanded when opened on mobile
  const toggleMobileSidebar = () => {
    // If we're opening the sidebar on mobile, make sure it's expanded
    if (!isMobileOpen) {
      setIsMobileOpen(true);
      setIsCollapsed(false); // Force expanded state when opening on mobile
    } else {
      setIsMobileOpen(false);
    }
  };

  // Add mobile backdrop for overlay pattern
  const MobileBackdrop = () => {
    if (!isMobileOpen || window.innerWidth >= 768) return null;
    
    return (
      <div 
        className="mobile-backdrop fixed inset-0 bg-black/50" 
        onClick={() => setIsMobileOpen(false)}
      />
    );
  };

  return (
    <>
      <MobileBackdrop />
      <aside
        className={`dashboard-nav theme-${effectiveTheme} ${isCollapsed ? 'collapsed' : ''} ${
          isMobileOpen ? 'mobile-open' : ''
        } ${sidebarBackground} flex flex-col`}
        aria-label="Main navigation"
      >
        {/* Brand Header with Toggle Button - Updated for mobile */}
        <div className={`brand-header px-4 py-4 border-b border-white/10 flex items-center ${isCollapsed ? 'justify-center' : 'justify-between'}`}>
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
                className="brand-text text-center text-white text-xl font-bold cursor-pointer"
                style={{ fontFamily: "Grandstander, cursive" }}
                onClick={() => navigate('/dashboard')}
              >
                heartbound
              </h1>
              
              {/* Empty div to balance the flex layout */}
              <div className="header-spacer w-8"></div>
            </>
          )}
        </div>
        
        {/* User Profile Section - Avatar only when collapsed */}
        <div 
          ref={profileSectionRef}
          className={`relative px-4 py-4 cursor-pointer transition-all duration-200 text-center
            ${isProfilePage ? 'bg-white/5' : 'hover:bg-white/5'}`}
          onClick={handleProfileClick}
        >
          <div className={`flex ${isCollapsed ? 'flex-col items-center' : 'flex-row items-center justify-center'} ${isCollapsed ? '' : 'gap-3'}`}>
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
              <div className="flex flex-col items-start">
                {/* Display name on top */}
                <span className="text-white font-medium text-sm truncate max-w-full">
                  {profile?.displayName || user?.username || "User"}
                </span>
                
                {/* Username and pronouns on a row below */}
                <div className="flex items-center gap-1 mt-0.5">
                  <span className="text-white/70 text-xs truncate">
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
          
          {/* Credits display with improved positioning and transitions */}
          <div className={`user-credits mt-3 ${isCollapsed ? 'mx-auto w-8 h-8 p-1' : 'mx-auto'} transition-all duration-200`}>
            <FaCoins className="user-credits-icon" size={isCollapsed ? 16 : 18} />
            {!isCollapsed && (
              <span className="user-credits-value transition-opacity duration-200">
                {user?.credits || 0}
              </span>
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
                        // If sidebar is collapsed and clicking on Discover, expand the sidebar and show games submenu
                        if (isCollapsed && item.path === "/dashboard") {
                          setIsCollapsed(false);
                          setGamesExpanded(true);
                        } else if (item.path === "/dashboard" && gamesExpanded) {
                          // If clicking on Dashboard while games are expanded, just collapse
                          setGamesExpanded(false);
                        } else if (item.hasSubmenu) {
                          // Toggle submenu for items with submenus
                          setGamesExpanded(!gamesExpanded);
                        } else {
                          // Navigate to the path for items without submenus
                          navigate(item.path);
                        }
                      }}
                      className={`w-full flex ${isCollapsed ? 'items-center justify-center' : 'items-center justify-start'} ${isCollapsed ? 'gap-1' : 'gap-2'} px-3 py-3 rounded-lg text-sm font-medium transition-all duration-200 group relative
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
                          className="absolute right-2 top-1/2 transform -translate-y-1/2 p-1 rounded-md cursor-pointer"
                        >
                          <ChevronRight 
                            size={16} 
                            className={`text-slate-400 transition-transform ${
                              gamesExpanded ? "animate-rotate-down" : "animate-rotate-up"
                            }`} 
                          />
                        </div>
                      )}
                      
                      {isActive && !item.hasSubmenu && !isCollapsed && <div className="absolute right-2 top-1/2 transform -translate-y-1/2 w-1.5 h-5 bg-primary rounded-full"></div>}
                    </button>
                    
                    {/* Games submenu - now left-aligned */}
                    {item.hasSubmenu && !isMainDashboard && !isCollapsed && (
                      <div className={gamesExpanded ? "animate-slideDown" : "animate-slideUp"}>
                        <ul className="mt-1 pl-8 space-y-1">
                          {gameItems.map((game) => {
                            const isGameActive = location.pathname.includes(game.path)
                            return (
                              <li key={game.path}>
                                <button
                                  onClick={() => navigate(game.path)}
                                  className={`w-full flex items-center justify-start gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200
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

        {/* Settings and Logout Footer - Updated */}
        <div className="mt-auto px-4 pb-6 border-t border-white/10 pt-4">
          {/* Container for buttons - Adjust justification based on collapsed state */}
          <div className={`flex items-center gap-2 ${isCollapsed ? 'justify-center' : 'justify-start'}`}>
            {/* Settings Button - Conditionally render only when expanded */}
            {!isCollapsed && (
              <button
                onClick={() => navigate('/dashboard/settings')}
                // Make settings button take available space when expanded
                className={`flex-1 flex items-center justify-start gap-2 px-3 py-3 rounded-lg text-sm font-medium transition-all duration-200 group
                  ${
                    isSettingsPage
                      ? "bg-primary/20 text-white shadow-md"
                      : "text-slate-300 hover:bg-white/5 hover:text-white"
                  }`}
                aria-current={isSettingsPage ? "page" : undefined}
                aria-label="Settings"
              >
                <span
                  className={`transition-transform duration-200 ${isSettingsPage ? "text-primary" : "text-slate-400 group-hover:text-slate-200"}`}
                >
                  <IoSettingsSharp size={20} />
                </span>
                {/* Label is always shown when button is visible (not collapsed) */}
                <span className="sidebar-label">Settings</span>
                {/* Active indicator */}
                {isSettingsPage && <div className="absolute right-2 w-1.5 h-5 bg-primary rounded-full"></div>}
              </button>
            )}

            {/* Logout Button - Icon only, consistent size */}
            <button
              onClick={handleLogout} // This now calls the updated function
              // Consistent size and centering, remove flex-1
              className={`w-10 h-10 flex items-center justify-center p-0 rounded-lg text-sm font-medium transition-all duration-200 group text-slate-300 hover:bg-red-500/10 hover:text-red-400`}
              aria-label="Logout"
            >
              <span className="text-slate-400 group-hover:text-red-400 transition-colors duration-200">
                <LogOut size={20} />
              </span>
              {/* Label removed - Icon only */}
            </button>
          </div>
        </div>
      </aside>
      
      {/* Add Mobile Toggle Button that's always visible on small screens */}
      <button
        onClick={toggleMobileSidebar}
        className="mobile-menu-toggle fixed top-4 left-4 z-[1001] p-2 rounded-md bg-primary/20 text-white hover:bg-primary/30 md:hidden"
        aria-label="Toggle mobile menu"
      >
        <Menu size={20} />
      </button>
    </>
  )
}

