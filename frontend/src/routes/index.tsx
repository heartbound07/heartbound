import { Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { AuthGuard } from '@/components/AuthGuard';
import { WebSocketProvider } from '@/contexts/WebSocketProvider';
import PartyUpdatesProvider from '@/contexts/PartyUpdates';
import { DashboardLayout } from '@/components/dashboard/DashboardLayout';
import { PairingsPageLayout } from '@/features/pages/PairingsPageLayout';
// Valorant imports commented out due to maintenance
// import { ValorantPageLayout } from '@/components/valorant/ValorantPageLayout';
// import ValorantPage from '@/features/valorant/ValorantPage';
// import ValorantPartyDetails from '@/features/valorant/ValorantPartyDetails';
import { LoginPage } from '@/features/auth/LoginPage';
import { AuthErrorPage } from '@/features/auth/AuthErrorPage';
import { BannedPage } from '@/features/auth/BannedPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { DiscoverPage } from '@/features/dashboard/DiscoverPage';
import { ProfilePage } from '@/features/dashboard/ProfilePage';
import { AdminPanel } from '@/features/dashboard/admin/AdminPanel';
import { UserManagement } from '@/features/dashboard/admin/usermanage/UserManagement';
import { DiscordBotSettings } from '@/features/dashboard/admin/discordbotsettings/DiscordBotSettings';
import { SystemStats } from '@/features/dashboard/admin/SystemStats';
import { AuditPanel } from '@/features/dashboard/admin/AuditPanel';
import { DiscordCallback } from '@/features/auth/DiscordCallback';
import { useAuth } from '@/contexts/auth';
import { Navigate as RouterNavigate } from 'react-router-dom';
import { LeaderboardPage } from '@/features/dashboard/LeaderboardPage';
import { SettingsPage } from '@/features/settings/SettingsPage';
import { ShopPage } from '@/features/shop/ShopPage';
import { useEffect } from 'react';
import { ShopAdminPage } from '@/features/shop/ShopAdminPage';
import { InventoryPage } from '@/features/shop/InventoryPage';
import { PairingsPage } from '@/features/pages/PairingsPage';
import QueueUpdatesProvider from '@/contexts/QueueUpdates';
import PairingUpdatesProvider from '@/contexts/PairingUpdates';
import { QueueConfigProvider } from '@/contexts/QueueConfigUpdates';
import { AdminQueueStatsProvider } from '@/contexts/AdminQueueStatsProvider';
import { MessageQueueDemo } from '@/examples/MessageQueueDemo';
import { NotFoundPage } from '@/features/NotFoundPage';

// Admin route guard component
function AdminRoute({ children }: { children: React.ReactNode }) {
  const { hasRole } = useAuth();
  
  if (!hasRole('ADMIN')) {
    return <RouterNavigate to="/profile" replace />;
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
    return <RouterNavigate to="/profile" replace />;
  }
  
  return <>{children}</>;
}

// Create a maintenance route guard component for Valorant
function ValorantMaintenanceGuard() {
  // Always redirect to dashboard when trying to access Valorant routes
  return <RouterNavigate to="/dashboard" replace />;
}

function ProtectedRoutes() {
  return (
    <AuthGuard>
      <WebSocketProvider>
        <PartyUpdatesProvider>
          <QueueUpdatesProvider>
            <QueueConfigProvider>
              <PairingUpdatesProvider>
                <AdminQueueStatsProvider>
                  <Outlet />
                </AdminQueueStatsProvider>
              </PairingUpdatesProvider>
            </QueueConfigProvider>
          </QueueUpdatesProvider>
        </PartyUpdatesProvider>
      </WebSocketProvider>
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
      <Route path="/banned" element={<BannedPage />} />
      
      {/* Riot Games verification file route */}
      <Route path="/riot.txt" element={ 
        <pre style={{ fontFamily: 'monospace' }}>7afb92b0-5252-4000-9ba3-fab43c393d15</pre>
      } />

      {/* Protected routes */}
      <Route element={<ProtectedRoutes />}>
        
        {/* Dashboard (main page) */}
        <Route path="/dashboard" element={<DashboardLayout />}>
          <Route index element={<DashboardPage />} />
        </Route>
        
        {/* Discover page */}
        <Route path="/discover" element={<DashboardLayout />}>
          <Route index element={<DiscoverPage />} />
        </Route>
        
        {/* Profile page */}
        <Route path="/profile" element={<DashboardLayout />}>
          <Route index element={<ProfilePage />} />
        </Route>
        
        {/* Admin routes - protected with AdminRoute component */}
        <Route path="/admin" element={<DashboardLayout />}>
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
          <Route 
            path="discord-settings"
            element={
              <AdminRoute>
                <DiscordBotSettings />
              </AdminRoute>
            } 
          />
          <Route 
            path="system-stats"
            element={
              <AdminRoute>
                <SystemStats />
              </AdminRoute>
            } 
          />
          <Route 
            path="audit"
            element={
              <AdminRoute>
                <AuditPanel />
              </AdminRoute>
            } 
          />
          {/* Message Queue Demo - Admin only */}
          <Route 
            path="message-queue-demo"
            element={
              <AdminRoute>
                <MessageQueueDemo />
              </AdminRoute>
            } 
          />
        </Route>
        
        {/* Leaderboard page */}
        <Route path="/leaderboard" element={<DashboardLayout />}>
          <Route index element={<LeaderboardPage />} />
        </Route>
        
        {/* Settings page */}
        <Route path="/settings" element={<DashboardLayout />}>
          <Route index element={<SettingsPage />} />
        </Route>
        
        {/* Shop routes with proper protection */}
        <Route path="/shop" element={<DashboardLayout />}>
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
        
        {/* Inventory page */}
        <Route path="/inventory" element={<DashboardLayout />}>
          <Route index element={<InventoryPage />} />
        </Route>
        
        {/* Pairings page */}
        <Route path="/pairings" element={<PairingsPageLayout />}>
          <Route index element={<PairingsPage />} />
        </Route>
        
        {/* Valorant routes - currently under maintenance */}
        <Route path="/valorant" element={<ValorantMaintenanceGuard />}>
          <Route index element={<div />} />
          <Route path=":partyId" element={<div />} />
          <Route path="*" element={<div />} />
        </Route>

      </Route>

      {/* Default redirect - routes to dashboard */}
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      
      {/* Catch-all route for any non-existent pages */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
} 