"use client"

import type React from "react"
import { useCallback, useMemo, memo, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Label } from "@/components/ui/valorant/label"
import { Heart, Users, User, Info, CheckCircle, XCircle } from "lucide-react"
import type { UserProfileDTO } from "@/config/userService"
import type { DiscordBotSettingsDTO } from "@/config/discordBotService"

import "@/assets/QueueJoinForm.css"

interface QueueJoinFormProps {
  onJoinQueue: () => Promise<void>
  loading: boolean
  userProfile: UserProfileDTO
  botSettings: DiscordBotSettingsDTO | null
}

const RoleDisplayItem: React.FC<{
  label: string
  value: string | null | undefined
  missingMessage: string
  imageUrl?: string
}> = ({ label, value, missingMessage, imageUrl }) => {
  const [imgError, setImgError] = useState(false)

  const handleImageError = () => {
    setImgError(true)
  }

  return (
    <div className="role-display-item">
      <div className="role-label-container">
        <Label className="role-label">{label}</Label>
        {value ? (
          <CheckCircle className="h-4 w-4 text-status-success" />
        ) : (
          <XCircle className="h-4 w-4 text-status-error" />
        )}
      </div>
      {value ? (
        <div className="role-value-content">
          {label === "Rank" && imageUrl && !imgError && (
            <img
              src={imageUrl}
              alt={`${value} Rank`}
              className="rank-image"
              onError={handleImageError}
            />
          )}
          <p className="role-value">{value}</p>
        </div>
      ) : (
        <p className="role-missing">{missingMessage}</p>
      )}
    </div>
  )
}

export const QueueJoinForm = memo(({ onJoinQueue, loading, userProfile, botSettings }: QueueJoinFormProps) => {
  const apiBaseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';
  
  const getRoleName = useCallback(
    (roleId: string | null | undefined) => {
      if (!roleId || !botSettings) return null

      const key = Object.keys(botSettings).find(k => botSettings[k as keyof DiscordBotSettingsDTO] === roleId)

      if (!key) return "Unknown Role"

      if (key.startsWith("age")) {
        if (key === "age15RoleId") return "15"
        if (key === "age16To17RoleId") return "16-17"
        if (key === "age18PlusRoleId") return "18+"
      }
      if (key.startsWith("gender")) {
        if (key === "genderSheHerRoleId") return "She/Her"
        if (key === "genderHeHimRoleId") return "He/Him"
        if (key === "genderAskRoleId") return "Ask"
      }
      if (key.startsWith("rank")) {
        return key.replace(/^rank/i, "").replace(/RoleId$/i, "")
      }
      if (key.startsWith("region")) {
        const region = key.replace("region", "").replace("RoleId", "").toUpperCase()
        if (region === "NA") return "North America"
        if (region === "EU") return "Europe"
        if (region === "SA") return "South America"
        if (region === "AP") return "Asia Pacific"
        if (region === "OCE") return "Oceania"
        return region
      }
      return "Selected"
    },
    [botSettings],
  )

  const {
    ageSelection,
    genderSelection,
    rankSelection,
    regionSelection,
    isFormValid,
  } = useMemo(() => {
    const selections = {
      ageSelection: getRoleName(userProfile.selectedAgeRoleId),
      genderSelection: getRoleName(userProfile.selectedGenderRoleId),
      rankSelection: getRoleName(userProfile.selectedRankRoleId),
      regionSelection: getRoleName(userProfile.selectedRegionRoleId),
    }
    return {
      ...selections,
      isFormValid: Object.values(selections).every(Boolean),
    }
  }, [userProfile, getRoleName])

  const levelRequirement = useMemo(() => {
    if (!botSettings || !userProfile?.selectedGenderRoleId || userProfile.level == null) {
      return { isApplicable: false, isMet: true }
    }

    const isMale = userProfile.selectedGenderRoleId === botSettings.genderHeHimRoleId
    if (!isMale) {
      return { isApplicable: false, isMet: true }
    }

    const meets = (userProfile.level ?? 1) >= 5
    return { isApplicable: true, isMet: meets }
  }, [userProfile, botSettings])

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault()
      if (!isFormValid) {
        throw new Error("Please select all required roles in Discord.")
      }
      await onJoinQueue()
    },
    [isFormValid, onJoinQueue],
  )

  return (
    <div className="queue-join-form-wrapper">
      <Card className="valorant-card">
        <CardHeader className="form-header">
          <CardTitle className="form-title">
            <div className="title-icon">
              <Heart className="h-5 w-5" />
            </div>
            <div className="title-content">
              <h2>Find Your Match</h2>
              <p>Your matchmaking preferences are based on your Discord roles.</p>
            </div>
          </CardTitle>
        </CardHeader>

        <CardContent className="form-content">
          <form onSubmit={handleSubmit} className="form-container">
            <div className="form-section">
              <div className="section-header">
                <User className="section-icon" />
                <h3>Your Roles</h3>
              </div>
              <div className="form-grid">
                <RoleDisplayItem label="Age" value={ageSelection} missingMessage="Select in #roles" />
                <RoleDisplayItem label="Gender" value={genderSelection} missingMessage="Select in #roles" />
                <RoleDisplayItem
                  label="Rank"
                  value={rankSelection}
                  missingMessage="Select in #roles"
                  imageUrl={rankSelection ? `${apiBaseUrl}/images/ranks/${rankSelection.toLowerCase()}.png` : undefined}
                />
                <RoleDisplayItem label="Region" value={regionSelection} missingMessage="Select in #roles" />
              </div>
            </div>

            {!isFormValid && (
              <div className="missing-roles-notice">
                <Info className="h-5 w-5 flex-shrink-0" />
                <p>You must select all required roles in the Discord server before you can join the queue.</p>
              </div>
            )}

            {levelRequirement.isApplicable && !levelRequirement.isMet && (
              <div className="missing-roles-notice">
                <Info className="h-5 w-5 flex-shrink-0" />
                <p>
                  Male users must be level 5 or higher to join the queue. Your current level is{" "}
                  {userProfile.level ?? 1}.
                </p>
              </div>
            )}

            <div className="form-actions">
              <button
                type="submit"
                className="join-queue-button"
                disabled={loading || !isFormValid || !levelRequirement.isMet}
              >
                {loading ? (
                  <div className="button-loading">
                    <div className="loading-spinner" />
                    <span>Joining...</span>
                  </div>
                ) : (
                  <div className="button-content">
                    <Users className="button-icon" />
                    <span>Join the Queue</span>
                  </div>
                )}
              </button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
})

QueueJoinForm.displayName = "QueueJoinForm"
