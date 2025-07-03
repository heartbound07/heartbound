"use client"

import { useNavigate, useLocation } from "react-router-dom"
import { useAuth } from "@/contexts/auth"
import "@/assets/sidebar.css"
import "@/assets/styles/fonts.css"
import "@/assets/z-index-system.css"
import { MdDashboard, MdAdminPanelSettings } from "react-icons/md"
import { IoSettingsSharp } from "react-icons/io5"
import { FaCoins, FaTrophy, FaShoppingCart, FaBoxOpen } from "react-icons/fa"
import { useState, useRef, useEffect } from "react"
import { ChevronRight, Menu, LogOut, Users, Star } from "lucide-react"
import { ProfilePreview } from "@/components/ui/profile/ProfilePreview"
import ReactDOM from "react-dom"
// Valorant logo import removed due to maintenance
// import valorantLogo from "@/assets/images/valorant-logo.png"

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

  // Add this to detect if we're on the Valorant page - disabled during maintenance
  const isValorantPage = () => {
    // Always return false since Valorant is under maintenance
    return false;
    // return location.pathname.includes("/valorant")
  }

  // Use the detected page to override the theme if needed
  const effectiveTheme = isValorantPage() ? "default" : theme

  const [gamesExpanded, setGamesExpanded] = useState(() => {
    // Don't auto-expand since Valorant is disabled
    return false;
    // Auto-expand if we're on a game page
    // return location.pathname.includes("/valorant")
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

  // Define games submenu items - Valorant removed due to maintenance
  const gameItems: Array<{
    path: string;
    label: string;
    logo: string;
    id: string;
  }> = [
    // Valorant temporarily removed for maintenance
    // {
    //   path: "/valorant",
    //   label: "VALORANT",
    //   logo: valorantLogo,
    //   id: "valorant",
    // },
    // More games will be added here in the future
  ]

  // Check if current user is an admin
  const isAdmin = hasRole("ADMIN")

  // Add admin panel to nav items if the user is an admin
  const navItems = [
    {
      path: "/discover",
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


  // Determine if we're on the discover page
  const isDiscoverPage = location.pathname === "/discover" || location.pathname === "/discover/"


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
          equippedBadgeIds={profile?.equippedBadgeId ? [profile.equippedBadgeId] : []}
          badgeMap={profile?.equippedBadgeId && profile?.badgeUrl ? { [profile.equippedBadgeId]: profile.badgeUrl } : {}}
          badgeNames={profile?.equippedBadgeId && profile?.badgeName ? { [profile.equippedBadgeId]: profile.badgeName } : {}}
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
      <aside
        className={`sidebar ${isCollapsed ? "collapsed" : "expanded"} ${isMobileOpen ? "mobile-open" : ""} theme-${effectiveTheme}`}
        aria-label="Main navigation"
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

          {!isCollapsed && (
            <h1
              className="sidebar-brand"
              onClick={() => navigate("/dashboard")}
            >
              heartbound
            </h1>
          )}
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
                item.path === "/discover"
                  ? isDiscoverPage
                  : location.pathname === item.path

              return (
                <div key={item.path} className="nav-group">
                  <div className={`nav-item ${isActive ? "active" : ""} ${isCollapsed ? "collapsed" : ""}`}>
                    <button
                      onClick={() => {
                        if (isCollapsed) {
                          setIsCollapsed(false)
                          if (item.hasSubmenu) {
                            setGamesExpanded(true)
                          }
                        } else {
                          navigate(item.path)
                        }
                      }}
                      className="nav-item-main"
                      aria-current={isActive ? "page" : undefined}
                    >
                      <div className="nav-icon">{item.icon}</div>

                      {!isCollapsed && (
                        <span className="nav-label">
                          {item.label}
                        </span>
                      )}
                    </button>

                    {item.hasSubmenu && !isCollapsed && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          setGamesExpanded(!gamesExpanded)
                        }}
                        className="nav-item-chevron"
                        aria-label={gamesExpanded ? "Collapse submenu" : "Expand submenu"}
                      >
                        <ChevronRight size={12} className={`submenu-arrow ${gamesExpanded ? "expanded" : ""}`} />
                      </button>
                    )}
                  </div>

                  {/* Submenu */}
                  {item.hasSubmenu && !isCollapsed && (
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
            {!isCollapsed && (
              <button
                onClick={() => navigate("/settings")}
                className={`footer-item ${isSettingsPage ? "active" : ""}`}
                aria-current={isSettingsPage ? "page" : undefined}
              >
                <IoSettingsSharp size={16} />
                <span>Settings</span>
              </button>
            )}

            <button onClick={handleLogout} className={`footer-item logout ${isCollapsed ? "collapsed" : ""}`}>
              <LogOut size={16} />
              {!isCollapsed && (
                <span>
                  Logout
                </span>
              )}
            </button>
          </div>
        </div>
      </aside>

      {/* Mobile Toggle */}
      <button onClick={toggleMobileSidebar} className="mobile-toggle" aria-label="Toggle mobile menu">
        <Menu size={18} />
      </button>
    </>
  )
}
