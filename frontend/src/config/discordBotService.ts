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
  fishingMaxCatches: number
  fishingCooldownHours: number
  fishingLimitWarningThreshold: number
  fishingPenaltyCredits: number
  [key: string]: string | number | boolean | undefined
}

export const getDiscordBotSettings = async (): Promise<DiscordBotSettingsDTO> => {
  const response = await httpClient.get<DiscordBotSettingsDTO>("/discord/api/bot-settings")
  return response.data
} 