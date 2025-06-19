"use client"

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
import { ChevronRight, Menu, LogOut, Users, Star } from "lucide-react"
import { ProfilePreview } from "@/components/ui/profile/ProfilePreview"
import ReactDOM from "react-dom"
import valorantLogo from "@/assets/images/valorant-logo.png"
import { motion, AnimatePresence } from "framer-motion"

/**
 * DashboardNavigation
 *
 * A modern, minimal sidebar navigation with enhanced design,
 * clean typography, and smooth framer-motion animations.
 *
 * @param {Object} props - Component props
 * @param {string} props.theme - Optional theme variant: 'dashboard' or 'default'
 * @param {function} props.onCollapseChange - Callback function to communicate collapse state changes
 */
interface DashboardNavigationProps {
  theme?: string
  onCollapseChange?: (collapsed: boolean) => void
}

export function DashboardNavigation({ theme = "default", onCollapseChange }: DashboardNavigationProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, profile, hasRole, logout } = useAuth()

  // Add this to detect if we're on the Valorant page
  const isValorantPage = () => {
    return location.pathname.includes("/valorant")
  }

  // Use the detected page to override the theme if needed
  const effectiveTheme = isValorantPage() ? "default" : theme

  const [gamesExpanded, setGamesExpanded] = useState(() => {
    // Auto-expand if we're on a game page
    return location.pathname.includes("/valorant")
  })
  const [showProfilePreview, setShowProfilePreview] = useState(false)
  const profileSectionRef = useRef<HTMLDivElement>(null)
  const profilePreviewRef = useRef<HTMLDivElement>(null)
  const [popupPosition, setPopupPosition] = useState({ top: 0, left: 0 })

  // Add sidebar collapse state with localStorage persistence
  const [isCollapsed, setIsCollapsed] = useState(() => {
    // Initialize from localStorage if available, otherwise default to false
    const savedState = localStorage.getItem("sidebar-collapsed")
    return savedState ? JSON.parse(savedState) : false
  })

  // Add a new state for tracking mobile overlay visibility
  const [isMobileOpen, setIsMobileOpen] = useState(false)

  // Update localStorage and dispatch a custom event when isCollapsed changes
  useEffect(() => {
    localStorage.setItem("sidebar-collapsed", JSON.stringify(isCollapsed))

    // Dispatch a custom event that other components can listen for
    const event = new CustomEvent("sidebarStateChange", {
      detail: { collapsed: isCollapsed },
    })
    window.dispatchEvent(event)

    // Notify parent components about the change
    if (onCollapseChange) {
      onCollapseChange(isCollapsed)
    }
  }, [isCollapsed, onCollapseChange])

  // Update the resize handler to also close mobile sidebar when resizing
  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth < 768) {
        setIsCollapsed(true)
        setIsMobileOpen(false) // Close mobile sidebar when resizing
      }
    }

    // Set initial state based on window size
    handleResize()

    window.addEventListener("resize", handleResize)
    return () => window.removeEventListener("resize", handleResize)
  }, [])

  // Define games submenu items
  const gameItems = [
    {
      path: "/valorant",
      label: "VALORANT",
      logo: valorantLogo,
      id: "valorant",
    },
    // More games will be added here in the future
  ]

  // Check if current user is an admin
  const isAdmin = hasRole("ADMIN")

  // Add admin panel to nav items if the user is an admin
  const navItems = [
    {
      path: "/dashboard",
      label: "Discover",
      icon: <MdDashboard size={18} />,
      hasSubmenu: true,
    },
    {
      label: "Pairings",
      path: "/pairings",
      icon: <Users size={18} />,
      exact: true,
    },
    {
      path: "/leaderboard",
      label: "Leaderboard",
      icon: <FaTrophy size={18} />,
      hasSubmenu: false,
    },
    // Shop navigation item
    {
      path: "/shop",
      label: "Shop",
      icon: <FaShoppingCart size={18} />,
      hasSubmenu: false,
    },
    // Inventory navigation item
    {
      path: "/inventory",
      label: "Inventory",
      icon: <FaBoxOpen size={18} />,
      hasSubmenu: false,
    },
    // Only show admin panel option if user has ADMIN role
    ...(isAdmin
      ? [
          {
            path: "/admin",
            label: "Admin Panel",
            icon: <MdAdminPanelSettings size={18} />,
            hasSubmenu: false,
          },
        ]
      : []),
  ]

  // Update popup position whenever profile section or visibility changes
  useEffect(() => {
    if (profileSectionRef.current && showProfilePreview) {
      const rect = profileSectionRef.current.getBoundingClientRect()

      // Adjust position for mobile
      if (window.innerWidth < 768) {
        setPopupPosition({
          top: rect.bottom + 10, // Position below profile section on mobile
          left: window.innerWidth / 2 - 150, // Center horizontally
        })
      } else {
        // Desktop positioning (unchanged)
        setPopupPosition({
          top: rect.top,
          left: rect.right + 10,
        })
      }
    }
  }, [showProfilePreview, profileSectionRef.current])

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

    document.addEventListener("mousedown", handleClickOutside)
    return () => {
      document.removeEventListener("mousedown", handleClickOutside)
    }
  }, [showProfilePreview])

  // Determine if we're on the main dashboard page
  const isMainDashboard = location.pathname === "/dashboard" || location.pathname === "/dashboard/"

  // Determine if we're on a specific game page
  const onGamePage = gameItems.some((game) => location.pathname.includes(game.id))

  // Check if we're on the profile page
  const isProfilePage = location.pathname === "/profile"

  // Check if we're on the settings page
  const isSettingsPage = location.pathname === "/settings"

  const handleProfileClick = () => {
    // Don't show the profile preview if we're already on the profile page
    if (isProfilePage) {
      // If on profile page, just navigate there (no need for preview)
      navigate("/profile")
      return
    }

    // Otherwise toggle the preview as normal
    setShowProfilePreview(!showProfilePreview)
  }

  // Profile Preview Portal Component
  const ProfilePreviewPortal = () => {
    // Don't render the portal at all if we're on the profile page
    if (!showProfilePreview || isProfilePage) return null

    return ReactDOM.createPortal(
      <div
        ref={profilePreviewRef}
        className="fixed z-[1400]"
        style={{
          top: `${popupPosition.top}px`,
          left: `${popupPosition.left}px`,
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
            navigate("/profile")
            setShowProfilePreview(false)
          }}
          equippedBadgeIds={profile?.equippedBadgeIds || []}
          badgeMap={profile?.badgeUrls || {}}
          badgeNames={profile?.badgeNames || {}}
        />
      </div>,
      document.body,
    )
  }

  // Add this useEffect after other useEffects
  useEffect(() => {
    // Close the profile preview when on the profile page
    if (isProfilePage && showProfilePreview) {
      setShowProfilePreview(false)
    }
  }, [isProfilePage])

  // Updated function to handle logout without confirmation
  const handleLogout = async () => {
    try {
      await logout()
      navigate("/login")
    } catch (error) {
      console.error("Logout failed:", error)
      // Optionally, display an error message to the user here
    }
  }

  // Modify the toggleMobileSidebar function to ensure sidebar is expanded when opened on mobile
  const toggleMobileSidebar = () => {
    // If we're opening the sidebar on mobile, make sure it's expanded
    if (!isMobileOpen) {
      setIsMobileOpen(true)
      setIsCollapsed(false) // Force expanded state when opening on mobile
    } else {
      setIsMobileOpen(false)
    }
  }

  // Add mobile backdrop for overlay pattern
  const MobileBackdrop = () => {
    if (!isMobileOpen || window.innerWidth >= 768) return null

    return <div className="mobile-backdrop" onClick={() => setIsMobileOpen(false)} />
  }

  return (
    <>
      <MobileBackdrop />
      <motion.aside
        className={`sidebar ${isCollapsed ? "collapsed" : "expanded"} ${isMobileOpen ? "mobile-open" : ""} theme-${effectiveTheme}`}
        aria-label="Main navigation"
        animate={{
          width: isCollapsed ? 78 : 280,
        }}
        transition={{
          duration: 0.25,
          ease: [0.25, 0.1, 0.25, 1],
        }}
      >
        {/* Header */}
        <div className="sidebar-header">
          <button
            onClick={() => setIsCollapsed(!isCollapsed)}
            className="sidebar-toggle"
            aria-label={isCollapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            <Menu size={16} />
          </button>

          <AnimatePresence>
            {!isCollapsed && (
              <motion.h1
                className="sidebar-brand"
                onClick={() => navigate("/dashboard")}
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ duration: 0.15 }}
              >
                heartbound
              </motion.h1>
            )}
          </AnimatePresence>
        </div>

        {/* Profile Section */}
        <div
          ref={profileSectionRef}
          className={`sidebar-profile ${isProfilePage ? "active" : ""} ${isCollapsed ? "collapsed" : ""}`}
          onClick={handleProfileClick}
        >
          <div className="profile-avatar">
            <img src={user?.avatar || "/default-avatar.png"} alt={user?.username || "User"} className="avatar-image" />
            <div className="status-dot"></div>
          </div>

          {!isCollapsed && (
            <div className="profile-info">
              <div className="profile-name">{profile?.displayName || user?.username || "User"}</div>
              <div className="profile-meta">
                <span className="username">@{user?.username || "guest"}</span>
                {profile?.pronouns && <span className="pronouns">{profile.pronouns}</span>}
              </div>
            </div>
          )}

          <div className="profile-badges">
            <div className={`credits-badge ${isCollapsed ? "collapsed" : ""}`}>
              <FaCoins size={12} />
              {!isCollapsed && <span>{user?.credits || 0}</span>}
            </div>
            <div className={`level-badge ${isCollapsed ? "collapsed" : ""}`}>
              <Star size={12} />
              {!isCollapsed && (
                <span>
                  {user?.level || 0}
                  <span className="level-xp">({user?.experience || 0} XP)</span>
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Profile Preview Portal */}
        <ProfilePreviewPortal />

        {/* Navigation */}
        <nav className="sidebar-nav">
          <div className="nav-section">
            {navItems.map((item) => {
              const isActive =
                item.path === "/dashboard"
                  ? isMainDashboard || (item.hasSubmenu && onGamePage)
                  : location.pathname === item.path

              return (
                <div key={item.path} className="nav-group">
                  <button
                    onClick={() => {
                      if (isCollapsed && item.path === "/dashboard") {
                        setIsCollapsed(false)
                        setGamesExpanded(true)
                      } else if (item.path === "/dashboard" && gamesExpanded) {
                        setGamesExpanded(false)
                      } else if (item.hasSubmenu) {
                        setGamesExpanded(!gamesExpanded)
                      } else {
                        navigate(item.path)
                      }
                    }}
                    className={`nav-item ${isActive ? "active" : ""} ${isCollapsed ? "collapsed" : ""}`}
                    aria-current={isActive ? "page" : undefined}
                  >
                    <div className="nav-icon">{item.icon}</div>

                    <AnimatePresence>
                      {!isCollapsed && (
                        <motion.span
                          className="nav-label"
                          initial={{ opacity: 0, x: -10 }}
                          animate={{ opacity: 1, x: 0 }}
                          exit={{ opacity: 0, x: -10 }}
                          transition={{ duration: 0.15 }}
                        >
                          {item.label}
                        </motion.span>
                      )}
                    </AnimatePresence>

                    {item.hasSubmenu && !isMainDashboard && !isCollapsed && (
                      <ChevronRight size={12} className={`submenu-arrow ${gamesExpanded ? "expanded" : ""}`} />
                    )}
                  </button>

                  {/* Submenu */}
                  {item.hasSubmenu && !isMainDashboard && !isCollapsed && (
                    <div className={`submenu ${gamesExpanded ? "expanded" : ""}`}>
                      {gameItems.map((game) => {
                        const isGameActive = location.pathname.includes(game.path)
                        return (
                          <button
                            key={game.path}
                            onClick={() => navigate(game.path)}
                            className={`submenu-item ${isGameActive ? "active" : ""}`}
                          >
                            <img
                              src={game.logo || "/placeholder.svg"}
                              alt={`${game.label} logo`}
                              className="game-icon"
                            />
                            <span>{game.label}</span>
                          </button>
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </nav>

        {/* Footer */}
        <div className="sidebar-footer">
          <div className="footer-section">
            <AnimatePresence>
              {!isCollapsed && (
                <motion.button
                  onClick={() => navigate("/settings")}
                  className={`footer-item ${isSettingsPage ? "active" : ""}`}
                  aria-current={isSettingsPage ? "page" : undefined}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -10 }}
                  transition={{ duration: 0.15 }}
                >
                  <IoSettingsSharp size={16} />
                  <span>Settings</span>
                </motion.button>
              )}
            </AnimatePresence>

            <button onClick={handleLogout} className={`footer-item logout ${isCollapsed ? "collapsed" : ""}`}>
              <LogOut size={16} />
              <AnimatePresence>
                {!isCollapsed && (
                  <motion.span
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: -10 }}
                    transition={{ duration: 0.15 }}
                  >
                    Logout
                  </motion.span>
                )}
              </AnimatePresence>
            </button>
          </div>
        </div>
      </motion.aside>

      {/* Mobile Toggle */}
      <button onClick={toggleMobileSidebar} className="mobile-toggle" aria-label="Toggle mobile menu">
        <Menu size={18} />
      </button>
    </>
  )
}
