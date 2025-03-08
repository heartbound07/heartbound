import { Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { AuthGuard } from '@/components/AuthGuard';
import PartyUpdatesProvider from '@/contexts/PartyUpdates';
import { DashboardLayout } from '@/components/dashboard/DashboardLayout';
import { ValorantPageLayout } from '@/components/valorant/ValorantPageLayout';
import { LoginPage } from '@/features/auth/LoginPage';
import { AuthErrorPage } from '@/features/auth/AuthErrorPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { ProfilePage } from '@/features/dashboard/ProfilePage';
import { AdminPanel } from '@/features/dashboard/admin/AdminPanel';
import { UserManagement } from '@/features/dashboard/admin/UserManagement';
import ValorantPage from '@/features/valorant/ValorantPage';
import ValorantPartyDetails from '@/features/valorant/ValorantPartyDetails';
import { DiscordCallback } from '@/features/auth/DiscordCallback';
import { useAuth } from '@/contexts/auth';
import { Navigate as RouterNavigate } from 'react-router-dom';

// Admin route guard component
function AdminRoute({ children }: { children: React.ReactNode }) {
  const { hasRole } = useAuth();
  
  if (!hasRole('ADMIN')) {
    return <RouterNavigate to="/dashboard" replace />;
  }
  
  return <>{children}</>;
}

function ProtectedRoutes() {
  return (
    <AuthGuard>
      <PartyUpdatesProvider>
        <Outlet />
      </PartyUpdatesProvider>
    </AuthGuard>
  );
}

export function AppRoutes() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/discord/callback" element={<DiscordCallback />} />
      <Route path="/auth/error" element={<AuthErrorPage />} />

      {/* Protected routes */}
      <Route element={<ProtectedRoutes />}>
        <Route path="/dashboard" element={<DashboardLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="profile" element={<ProfilePage />} />
          
          {/* Admin routes - protected with AdminRoute component */}
          <Route path="admin">
            <Route 
              index
              element={
                <AdminRoute>
                  <AdminPanel />
                </AdminRoute>
              } 
            />
            <Route 
              path="users"
              element={
                <AdminRoute>
                  <UserManagement />
                </AdminRoute>
              } 
            />
          </Route>
        </Route>
        <Route path="/dashboard/valorant" element={<ValorantPageLayout />}>
          <Route index element={<ValorantPage />} />
          <Route path=":partyId" element={<ValorantPartyDetails />} />
        </Route>
      </Route>

      {/* Default redirect - now routes to /login */}
      <Route path="/" element={<Navigate to="/login" replace />} />
    </Routes>
  );
} 