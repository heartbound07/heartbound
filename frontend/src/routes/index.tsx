import { Routes, Route } from 'react-router-dom';
import { DiscordCallback } from '@/features/auth/DiscordCallback';

export function AppRoutes() {
  return (
    <Routes>
      {/* Add your other routes here */}
      <Route path="/auth/discord/callback" element={<DiscordCallback />} />
    </Routes>
  );
} 