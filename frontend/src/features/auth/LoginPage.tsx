import { useAuth } from '@/contexts/auth';
import { CloudBackground } from '@/components/backgrounds/CloudBackground';
import { Navigation } from '@/components/ui/Navigation';
import { DiscordLoginButton } from '@/components/ui/DiscordLoginButton';

export function LoginPage() {
  const { startDiscordOAuth } = useAuth();

  // We no longer need a separate click handler since the DiscordLoginButton
  // integrates the styling and placeholder logic.
  return (
    <div className="min-h-screen bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] relative overflow-hidden">
      <Navigation className="font-grandstander" />
      <CloudBackground />
      <main className="relative z-10 flex flex-col items-center justify-center min-h-[calc(100vh-80px)] px-4 text-center">
        <h1 className="font-grandstander text-7xl font-bold text-white mb-8 tracking-wide">
          heartbound
        </h1>
        <p className="text-2xl text-white/90 mb-12">
          find your perfect duo!
        </p>
        
        <div className="bg-white/10 backdrop-blur-md rounded-2xl p-8 shadow-lg w-[340px] mx-auto">
          <DiscordLoginButton />
        </div>
      </main>
    </div>
  );
} 