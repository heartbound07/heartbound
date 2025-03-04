import { useNavigate, useLocation } from "react-router-dom"
import { useAuth } from "@/contexts/auth"
import "@/assets/sidebar.css"
import { MdDashboard } from "react-icons/md"
import { FaUserCircle } from "react-icons/fa"
import { IoSettingsSharp } from "react-icons/io5"
import { FiLogOut } from "react-icons/fi"

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
  const { logout, user } = useAuth()

  const navItems = [
    { path: "/dashboard", label: "Overview", icon: <MdDashboard size={20} /> },
    { path: "/dashboard/profile", label: "Profile", icon: <FaUserCircle size={20} /> },
    { path: "/dashboard/settings", label: "Settings", icon: <IoSettingsSharp size={20} /> },
  ]

  const handleLogout = async () => {
    await logout()
    sessionStorage.removeItem("hasSeenWelcome")
    navigate("/login")
  }

  // Set background color based on theme
  const sidebarBackground = theme === 'dashboard'
    ? "bg-gradient-to-b from-[#5b48e6]/90 to-[#7a67ed]/90" // Match dashboard gradient
    : "bg-gradient-to-b from-slate-800/90 to-slate-900/90"

  return (
    <aside className={`dashboard-nav h-full flex flex-col ${sidebarBackground} backdrop-blur-md border-r border-white/10 shadow-xl`}>
      {/* User Profile Section */}
      <div className="px-6 py-8 border-b border-white/10">
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

      {/* Navigation Links */}
      <nav className="flex-1 px-4 py-6">
        <ul className="space-y-2">
          {navItems.map((item) => {
            const isActive = location.pathname === item.path
            return (
              <li key={item.path}>
                <button
                  onClick={() => navigate(item.path)}
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
                  {isActive && <div className="ml-auto w-1.5 h-5 bg-primary rounded-full"></div>}
                </button>
              </li>
            )
          })}
        </ul>
      </nav>

      {/* Footer with Logout */}
      <div className="p-4 mt-auto border-t border-white/10">
        <button
          onClick={handleLogout}
          className="w-full flex items-center justify-center gap-2 px-4 py-3 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-red-300 hover:text-red-200 transition-all duration-200 text-sm font-medium"
        >
          <FiLogOut size={18} />
          <span>Logout</span>
        </button>
      </div>
    </aside>
  )
}

