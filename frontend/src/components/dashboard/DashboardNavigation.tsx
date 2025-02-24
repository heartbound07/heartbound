import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';
import '@/assets/dashboard.css';
import { MdDashboard } from 'react-icons/md';
import { FaUserCircle } from 'react-icons/fa';
import { IoSettingsSharp } from 'react-icons/io5';
import { FiLogOut } from 'react-icons/fi';

/**
 * DashboardNavigation
 *
 * The sidebar navigation has been updated to use a glassmorphic effect with a semi-transparent background,
 * modern hover animations, and clear active states.
 */
export function DashboardNavigation() {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout, user } = useAuth();

  const navItems = [
    { path: '/dashboard', label: 'Overview', icon: <MdDashboard size={20} /> },
    { path: '/dashboard/profile', label: 'Profile', icon: <FaUserCircle size={20} /> },
    { path: '/dashboard/settings', label: 'Settings', icon: <IoSettingsSharp size={20} /> },
  ];

  const handleLogout = async () => {
    await logout();
    sessionStorage.removeItem('hasSeenWelcome');
    navigate('/login');
  };

  return (
    <nav className="dashboard-nav flex flex-col h-full animate-slideRightIn">
      <div className="user-info flex items-center gap-3 mb-8 p-4">
        <img 
          src={user?.avatar || '/default-avatar.png'} 
          alt={user?.username} 
          className="avatar rounded-full w-12 h-12 object-cover"
        />
        <span className="text-white font-semibold">{user?.username}</span>
      </div>
      
      <div className="nav-links flex flex-col gap-2 mb-auto">
        {navItems.map(item => (
          <button
            key={item.path}
            onClick={() => navigate(item.path)}
            className={`nav-item ${location.pathname === item.path ? 'active' : ''}`}
          >
            <span className="mr-2">{item.icon}</span>
            {item.label}
          </button>
        ))}
      </div>

      <button onClick={handleLogout} className="logout-button flex items-center justify-center gap-2">
        <FiLogOut size={20} />
        Logout
      </button>
    </nav>
  );
} 