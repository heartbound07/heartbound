import { useState, useCallback } from 'react'
import { useAuth } from '@/contexts/auth'
import { CustomButton } from './CustomButton'
import { DiscordIcon } from './DiscordIcon'

export function DiscordLoginButton() {
  const { startDiscordOAuth } = useAuth()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleDiscordLogin = useCallback(async () => {
    try {
      setIsLoading(true)
      console.log('[Discord Login] Button clicked')
      await startDiscordOAuth()
      setIsLoading(false)
    } catch (err: any) {
      console.error('Discord login failed:', err)
      setError(err instanceof Error ? err.message : 'Discord authentication failed')
      setIsLoading(false)
    }
  }, [startDiscordOAuth])

  return (
    <div className="flex flex-col items-center w-full">
      {error && (
        <div className="mb-2 text-center text-red-400 text-sm">
          {error}
        </div>
      )}
      <CustomButton
        onClick={handleDiscordLogin}
        disabled={isLoading}
        className="w-full h-12 bg-[#5865F2] hover:bg-[#4752C4] text-white font-grandstander rounded-full text-base font-semibold shadow-md transition-transform duration-200 hover:scale-105 gap-3 relative"
      >
        <DiscordIcon className="w-6 h-6" />
        {isLoading ? (
          <>
            <span className="opacity-0">Continue with Discord</span>
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
            </div>
          </>
        ) : (
          "Continue with Discord"
        )}
      </CustomButton>
    </div>
  )
} 