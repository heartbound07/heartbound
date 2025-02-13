import { Outlet } from 'react-router-dom';
import { DashboardNavigation } from './DashboardNavigation';

export function DashboardLayout() {
  return (
    <div className="dashboard-container">
      <DashboardNavigation />
      <main className="dashboard-content">
        <Outlet />
      </main>
    </div>
  );
}
