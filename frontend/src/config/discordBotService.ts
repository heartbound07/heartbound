import httpClient from "@/lib/api/httpClient"

export interface DiscordBotSettingsDTO {
  activityEnabled: boolean
  creditsToAward: number
  messageThreshold: number
  timeWindowMinutes: number
  cooldownSeconds: number
  minMessageLength: number
  levelingEnabled: boolean
  xpToAward: number
  baseXp: number
  levelMultiplier: number
  levelExponent: number
  levelFactor: number
  creditsPerLevel: number
  level5RoleId: string
  level15RoleId: string
  level30RoleId: string
  level40RoleId: string
  level50RoleId: string
  level70RoleId: string
  level100RoleId: string
  starterRoleId: string
  roleMultipliers: string
  roleMultipliersEnabled: boolean
  inactivityChannelId: string
  countingGameEnabled: boolean
  countingChannelId: string
  countingTimeoutRoleId: string
  creditsPerCount: number
  countingLives: number
  autoSlowmodeEnabled: boolean
  slowmodeChannelIds: string
  activityThreshold: number
  slowmodeTimeWindow: number
  slowmodeDuration: number
  slowmodeCooldown: number
  creditDropEnabled: boolean;
  creditDropChannelId: string;
  creditDropMinAmount: number;
  creditDropMaxAmount: number;
  partDropEnabled: boolean;
  partDropChannelId: string;
  partDropChance: number;
  age15RoleId: string
  age16To17RoleId: string
  age18PlusRoleId: string
  genderSheHerRoleId: string
  genderHeHimRoleId: string
  genderAskRoleId: string
  rankIronRoleId: string
  rankBronzeRoleId: string
  rankSilverRoleId: string
  rankGoldRoleId: string
  rankPlatinumRoleId: string
  rankDiamondRoleId: string
  ageRolesThumbnailUrl: string
  genderRolesThumbnailUrl: string
  rankRolesThumbnailUrl: string
  regionNaRoleId: string
  regionEuRoleId: string
  regionSaRoleId: string
  regionApRoleId: string
  regionOceRoleId: string
  regionRolesThumbnailUrl: string
  fishingMinCatches: number
  fishingMaxCatches: number
  fishingDefaultMaxCatches: number
  fishingCooldownHours: number
  fishingLimitWarningThreshold: number
  fishingPenaltyCredits: number
}

export const getDiscordBotSettings = async (): Promise<DiscordBotSettingsDTO> => {
  const response = await httpClient.get<DiscordBotSettingsDTO>("/admin/discord/settings")
  return response.data
} 