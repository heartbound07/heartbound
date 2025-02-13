import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthGuard } from '@/components/AuthGuard';
import { LoginPage } from '@/features/auth/LoginPage';
import { AuthErrorPage } from '@/features/auth/AuthErrorPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { DiscordCallback } from '@/features/auth/DiscordCallback';

export function AppRoutes() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/discord/callback" element={<DiscordCallback />} />
      <Route path="/auth/error" element={<AuthErrorPage />} />

      {/* Protected routes */}
      <Route path="/dashboard" element={
        <AuthGuard>
          <DashboardPage />
        </AuthGuard>
      } />

      {/* Default redirect */}
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
} 