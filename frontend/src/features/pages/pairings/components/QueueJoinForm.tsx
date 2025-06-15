"use client"

import type React from "react"
import { useState, useCallback, useMemo, memo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/profile/input"
import { Label } from "@/components/ui/valorant/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Heart } from 'lucide-react'
import type { JoinQueueRequestDTO } from "@/config/pairingService"
import { Skeleton } from "@/components/ui/SkeletonUI"

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

// Enhanced Queue Join Form with better UX - Memoized for performance
export const QueueJoinForm = memo(({
  onJoinQueue,
  loading,
}: QueueJoinFormProps) => {
  const [formData, setFormData] = useState({
    age: "",
    region: "",
    rank: "",
    gender: ""
  })

  // Secure input validation
  const validateAge = useCallback((age: string): boolean => {
    const ageNum = Number.parseInt(age)
    return !Number.isNaN(ageNum) && ageNum >= 13 && ageNum <= 100
  }, [])

  // Sanitize input to prevent XSS
  const sanitizeInput = useCallback((input: string): string => {
    return input.trim().replace(/[<>'"]/g, '')
  }, [])

  const updateFormField = useCallback((field: keyof typeof formData, value: string) => {
    setFormData(prev => ({
      ...prev,
      [field]: field === 'age' ? value : sanitizeInput(value)
    }))
  }, [sanitizeInput])

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault()

      const ageNum = Number.parseInt(formData.age)
      if (!validateAge(formData.age)) {
        throw new Error("Please enter a valid age between 13 and 100")
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

  const isFormValid = useMemo(() => 
    validateAge(formData.age) && formData.region && formData.rank && formData.gender,
    [formData, validateAge]
  )

  return (
    <div>
      <Card className="valorant-card">
        <CardHeader className="pb-4">
          <CardTitle className="flex items-center gap-3 text-white text-xl">
            <div className="p-2 bg-primary/20 rounded-lg">
              <Heart className="h-5 w-5 text-primary" />
            </div>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="age" className="text-sm font-medium text-white">
                  Age
                </Label>
                <Input
                  id="age"
                  type="number"
                  placeholder="Enter your age"
                  value={formData.age}
                  onChange={(e) => updateFormField('age', e.target.value)}
                  className="bg-theme-container border-theme text-white placeholder:text-theme-tertiary focus:border-primary focus:ring-1 focus:ring-primary/20 theme-transition"
                  min="13"
                  max="100"
                  required
                  aria-describedby="age-error"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="gender" className="text-sm font-medium text-white">
                  Gender
                </Label>
                <Select value={formData.gender} onValueChange={(value) => updateFormField('gender', value)} required>
                  <SelectTrigger className="bg-theme-container border-theme text-white theme-transition">
                    <SelectValue placeholder="Select gender" />
                  </SelectTrigger>
                  <SelectContent className="bg-[#1F2731] border-theme theme-transition">
                    {GENDERS.map((g) => (
                      <SelectItem 
                        key={g.value} 
                        value={g.value}
                        className="text-white hover:bg-[#2A3441] hover:text-primary focus:bg-[#2A3441] focus:text-primary transition-all duration-200 ease-in-out cursor-pointer"
                      >
                        {g.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="region" className="text-white font-medium mb-2 block">
                  Region
                </Label>
                <Select value={formData.region} onValueChange={(value) => updateFormField('region', value)} required>
                  <SelectTrigger className="bg-theme-container border-theme text-white theme-transition">
                    <SelectValue placeholder="Select region" />
                  </SelectTrigger>
                  <SelectContent className="bg-[#1F2731] border-theme theme-transition">
                    {REGIONS.map((reg) => (
                      <SelectItem 
                        key={reg.value} 
                        value={reg.value}
                        className="text-white hover:bg-[#2A3441] hover:text-primary focus:bg-[#2A3441] focus:text-primary transition-all duration-200 ease-in-out cursor-pointer"
                      >
                        {reg.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label htmlFor="rank" className="text-white font-medium mb-2 block">
                  VALORANT Rank
                </Label>
                <Select value={formData.rank} onValueChange={(value) => updateFormField('rank', value)} required>
                  <SelectTrigger className="bg-theme-container border-theme text-white theme-transition">
                    <SelectValue placeholder="Select rank" />
                  </SelectTrigger>
                  <SelectContent className="bg-[#1F2731] border-theme theme-transition">
                    {RANKS.map((r) => (
                      <SelectItem 
                        key={r.value} 
                        value={r.value}
                        className="text-white hover:bg-[#2A3441] hover:text-primary focus:bg-[#2A3441] focus:text-primary transition-all duration-200 ease-in-out cursor-pointer"
                      >
                        {r.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="text-center">
              <button
                type="submit"
                className="join-queue-button"
                disabled={loading || !isFormValid}
              >
                {loading ? (
                  <Skeleton width="120px" height="20px" theme="valorant" className="mx-auto" />
                ) : (
                  <>
                    <Heart className="mr-2 h-5 w-5" />
                    Join the Queue
                  </>
                )}
              </button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
})

QueueJoinForm.displayName = 'QueueJoinForm' 