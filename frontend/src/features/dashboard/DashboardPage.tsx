import { useAuth } from '@/contexts/auth';

export function DashboardPage() {
  const { user } = useAuth();

  return (
    <div className="dashboard-container">
      <h1>Welcome, {user?.username}!</h1>
      <p>This is your protected dashboard.</p>
    </div>
  );
} 