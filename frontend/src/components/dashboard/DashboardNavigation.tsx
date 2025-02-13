import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';

export function DashboardNavigation() {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout, user } = useAuth();

  const navItems = [
    { path: '/dashboard', label: 'Overview' },
    { path: '/dashboard/profile', label: 'Profile' },
    { path: '/dashboard/settings', label: 'Settings' },
  ];

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <nav className="dashboard-nav">
      <div className="user-info">
        <img 
          src={user?.avatar || '/default-avatar.png'} 
          alt={user?.username} 
          className="avatar"
        />
        <span>{user?.username}</span>
      </div>
      
      <div className="nav-links">
        {navItems.map(item => (
          <button
            key={item.path}
            onClick={() => navigate(item.path)}
            className={`nav-item ${location.pathname === item.path ? 'active' : ''}`}
          >
            {item.label}
          </button>
        ))}
      </div>

      <button onClick={handleLogout} className="logout-button">
        Logout
      </button>
    </nav>
  );
} 