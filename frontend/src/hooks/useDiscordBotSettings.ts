import { useState, useEffect } from "react"
import { getDiscordBotSettings, type DiscordBotSettingsDTO } from "@/config/discordBotService"

export const useDiscordBotSettings = () => {
  const [botSettings, setBotSettings] = useState<DiscordBotSettingsDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        setLoading(true)
        const settings = await getDiscordBotSettings()
        setBotSettings(settings)
      } catch (err: any) {
        setError(err.message || "Failed to fetch Discord bot settings")
      } finally {
        setLoading(false)
      }
    }

    fetchSettings()
  }, [])

  return { botSettings, loading, error }
} 