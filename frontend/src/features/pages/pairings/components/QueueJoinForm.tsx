"use client"

import type React from "react"
import { useState, useCallback, useMemo, memo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/profile/input"
import { Label } from "@/components/ui/valorant/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Heart, Users, MapPin, Trophy, User } from "lucide-react"
import type { JoinQueueRequestDTO } from "@/config/pairingService"

// Import component-specific CSS
import "@/assets/QueueJoinForm.css"

// Constants for form options
const REGIONS = [
  { value: "NA_EAST", label: "NA East" },
  { value: "NA_WEST", label: "NA West" },
  { value: "NA_CENTRAL", label: "NA Central" },
  { value: "EU", label: "Europe" },
  { value: "AP", label: "Asia Pacific" },
  { value: "KR", label: "Korea" },
  { value: "LATAM", label: "Latin America" },
  { value: "BR", label: "Brazil" },
] as const

const RANKS = [
  { value: "IRON", label: "Iron" },
  { value: "BRONZE", label: "Bronze" },
  { value: "SILVER", label: "Silver" },
  { value: "GOLD", label: "Gold" },
  { value: "PLATINUM", label: "Platinum" },
  { value: "DIAMOND", label: "Diamond" },
  { value: "ASCENDANT", label: "Ascendant" },
  { value: "IMMORTAL", label: "Immortal" },
  { value: "RADIANT", label: "Radiant" },
] as const

const GENDERS = [
  { value: "MALE", label: "Male" },
  { value: "FEMALE", label: "Female" },
  { value: "NON_BINARY", label: "Non-Binary" },
  { value: "PREFER_NOT_TO_SAY", label: "Prefer not to say" },
] as const

interface QueueJoinFormProps {
  onJoinQueue: (data: JoinQueueRequestDTO) => Promise<void>
  loading: boolean
}

// Enhanced Queue Join Form with minimal modern design - Memoized for performance
export const QueueJoinForm = memo(({ onJoinQueue, loading }: QueueJoinFormProps) => {
  const [formData, setFormData] = useState({
    age: "",
    region: "",
    rank: "",
    gender: "",
  })

  // Secure input validation
  const validateAge = useCallback((age: string): boolean => {
    const ageNum = Number.parseInt(age)
    return !Number.isNaN(ageNum) && ageNum >= 15 && ageNum <= 100
  }, [])

  // Sanitize input to prevent XSS
  const sanitizeInput = useCallback((input: string): string => {
    return input.trim().replace(/[<>'"]/g, "")
  }, [])

  const updateFormField = useCallback(
    (field: keyof typeof formData, value: string) => {
      setFormData((prev) => ({
        ...prev,
        [field]: field === "age" ? value : sanitizeInput(value),
      }))
    },
    [sanitizeInput],
  )

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault()

      const ageNum = Number.parseInt(formData.age)
      if (!validateAge(formData.age)) {
        throw new Error("Age must be at least 15!")
      }

      if (!formData.region || !formData.rank || !formData.gender) {
        throw new Error("Please fill in all fields")
      }

      await onJoinQueue({
        userId: "",
        age: ageNum,
        region: formData.region as any,
        rank: formData.rank as any,
        gender: formData.gender as any,
      })

      // Reset form on successful submission
      setFormData({ age: "", region: "", rank: "", gender: "" })
    },
    [formData, onJoinQueue, validateAge],
  )

  const isFormValid = useMemo(
    () => validateAge(formData.age) && formData.region && formData.rank && formData.gender,
    [formData, validateAge],
  )

  return (
    <div className="queue-join-form-wrapper">
      <Card className="valorant-card">
        <CardHeader className="form-header">
          <CardTitle className="form-title">
            <div className="title-icon">
              <Heart className="h-6 w-6" />
            </div>
            <div className="title-content">
              <h2>Find Your Match</h2>
              <p>Connect with players in your region and skill level</p>
            </div>
          </CardTitle>
        </CardHeader>

        <CardContent className="form-content">
          <form onSubmit={handleSubmit} className="form-container">
            {/* Personal Information Section */}
            <div className="form-section">
              <div className="section-header">
                <User className="section-icon" aria-label="Personal Information" />
              </div>

              <div className="form-grid">
                <div className="form-field">
                  <Label htmlFor="age" className="field-label">
                    Age
                  </Label>
                  <div className="input-container">
                    <Input
                      id="age"
                      type="number"
                      placeholder="18"
                      value={formData.age}
                      onChange={(e) => updateFormField("age", e.target.value)}
                      className="form-input"
                      min="15"
                      max="100"
                      required
                      aria-describedby="age-error"
                    />
                  </div>
                </div>

                <div className="form-field">
                  <Label htmlFor="gender" className="field-label">
                    Gender
                  </Label>
                  <div className="select-container">
                    <Select
                      value={formData.gender}
                      onValueChange={(value) => updateFormField("gender", value)}
                      required
                    >
                      <SelectTrigger className="form-select">
                        <SelectValue placeholder="Select gender" />
                      </SelectTrigger>
                      <SelectContent className="select-content">
                        {GENDERS.map((g) => (
                          <SelectItem key={g.value} value={g.value} className="select-item">
                            {g.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              </div>
            </div>

            {/* Gaming Information Section */}
            <div className="form-section">
              <div className="section-header">
                <Trophy className="section-icon" aria-label="Gaming Profile" />
              </div>

              <div className="form-grid">
                <div className="form-field">
                  <Label htmlFor="region" className="field-label">
                    <MapPin className="label-icon" />
                    Region
                  </Label>
                  <div className="select-container">
                    <Select
                      value={formData.region}
                      onValueChange={(value) => updateFormField("region", value)}
                      required
                    >
                      <SelectTrigger className="form-select">
                        <SelectValue placeholder="Select your region" />
                      </SelectTrigger>
                      <SelectContent className="select-content">
                        {REGIONS.map((reg) => (
                          <SelectItem key={reg.value} value={reg.value} className="select-item">
                            {reg.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                <div className="form-field">
                  <Label htmlFor="rank" className="field-label">
                    <Trophy className="label-icon" />
                    VALORANT Rank
                  </Label>
                  <div className="select-container">
                    <Select value={formData.rank} onValueChange={(value) => updateFormField("rank", value)} required>
                      <SelectTrigger className="form-select">
                        <SelectValue placeholder="Select your rank" />
                      </SelectTrigger>
                      <SelectContent className="select-content">
                        {RANKS.map((r) => (
                          <SelectItem key={r.value} value={r.value} className="select-item">
                            {r.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              </div>
            </div>

            {/* Submit Button */}
            <div className="form-actions">
              <button type="submit" className="join-queue-button" disabled={loading || !isFormValid}>
                {loading ? (
                  <div className="button-loading">
                    <div className="loading-spinner" />
                    <span>Joining Queue...</span>
                  </div>
                ) : (
                  <div className="button-content">
                    <Users className="button-icon" />
                    <span>Join the Queue</span>
                  </div>
                )}
              </button>

              {!isFormValid && <p className="form-hint">Please fill in all fields to join the queue</p>}
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
})

QueueJoinForm.displayName = "QueueJoinForm"
