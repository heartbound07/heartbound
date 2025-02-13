import { useAuth } from '@/contexts/auth';
import { CloudBackground } from '@/components/backgrounds/CloudBackground';
import { CustomButton } from '@/components/CustomButton';
import { Navigation } from '@/components/Navigation';

export function LoginPage() {
  const { startDiscordOAuth } = useAuth();

  const handleDiscordLogin = async () => {
    try {
      await startDiscordOAuth();
    } catch (error) {
      console.error('Discord login failed:', error);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] relative overflow-hidden">
      <CloudBackground />
      <Navigation />

      <main className="relative z-10 flex flex-col items-center justify-center min-h-[calc(100vh-80px)] px-4 text-center">
        <h1 className="font-grandstander text-7xl font-bold text-white mb-2 tracking-wide">
          heartbound
        </h1>
        <p className="text-2xl text-white/90 mb-12">
          find your perfect duo!
        </p>
        
        <div className="bg-white/10 backdrop-blur-md rounded-2xl p-8 shadow-lg w-[340px] mx-auto">
          <CustomButton
            onClick={handleDiscordLogin}
            className="w-full py-3 flex items-center justify-center gap-2 rounded-full backdrop-blur-sm"
          >
            Continue with Discord
          </CustomButton>
        </div>
      </main>
    </div>
  );
} 