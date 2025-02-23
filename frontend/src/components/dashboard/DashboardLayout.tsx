import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from './DashboardNavigation';

export function DashboardLayout() {
  return (
    <ProtectedRoute>
      <div className="flex min-h-screen bg-gradient-to-br from-[#6B5BE6] to-[#8878f0]">
        <DashboardNavigation />
        <main className="flex-1 p-8 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
