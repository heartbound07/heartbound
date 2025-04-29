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
import { LeaderboardPage } from '@/features/dashboard/LeaderboardPage';
import { SettingsPage } from '@/features/settings/SettingsPage';
import { ShopPage } from '@/features/shop/ShopPage';
import { useEffect } from 'react';
import { ShopAdminPage } from '@/features/shop/ShopAdminPage';

// Admin route guard component
function AdminRoute({ children }: { children: React.ReactNode }) {
  const { hasRole } = useAuth();
  
  if (!hasRole('ADMIN')) {
    return <RouterNavigate to="/dashboard" replace />;
  }
  
  return <>{children}</>;
}

// Create a separate AdminShopRoute component for shop admin features
function AdminShopRoute({ children }: { children: React.ReactNode }) {
  const { hasRole, user } = useAuth();
  
  useEffect(() => {
    // Enhanced security logging
    console.log(`Admin shop access attempt by user: ${user?.id || 'unknown'}`);
  }, [user]);
  
  // Double security check
  if (!hasRole('ADMIN')) {
    console.warn('Unauthorized admin shop access attempt blocked');
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
      
      {/* Riot Games verification file route */}
      <Route path="/riot.txt" element={
        <pre style={{ fontFamily: 'monospace' }}>7afb92b0-5252-4000-9ba3-fab43c393d15</pre>
      } />

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
          <Route path="leaderboard" element={<LeaderboardPage />} />
          <Route path="settings" element={<SettingsPage />} />
          
          {/* Shop routes with proper protection */}
          <Route path="shop">
            <Route index element={<ShopPage />} />
            <Route 
              path="admin" 
              element={
                <AdminShopRoute>
                  <ShopAdminPage />
                </AdminShopRoute>
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