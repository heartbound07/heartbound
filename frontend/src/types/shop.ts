import { Role } from '@/contexts/auth/types';

export interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  thumbnailUrl?: string;
  requiredRole: Role | null;
  owned: boolean;
  rarity: string;
  isCase?: boolean;
  caseContentsCount?: number;
  isFeatured?: boolean;
  isDaily?: boolean;
  fishingRodMultiplier?: number;
  fishingRodPartType?: string;
  gradientEndColor?: string;
  maxCopies?: number;
  copiesSold?: number;
  maxDurability?: number;
}

export interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

export interface UserProfile {
  id: string;
  username: string;
  avatar: string;
  displayName: string;
  pronouns: string;
  about: string;
  bannerColor: string;
  bannerUrl: string;
  banned: boolean;
  roles: Role[];
  credits: number;
  level: number;
  experience: number;
  xpForNextLevel: number;
  messageCount: number;
  fishCaughtCount: number;
  messagesToday: number;
  messagesThisWeek: number;
  messagesThisTwoWeeks: number;
  voiceRank: number;
  voiceTimeMinutesToday: number;
  voiceTimeMinutesThisWeek: number;
  voiceTimeMinutesThisTwoWeeks: number;
  voiceTimeMinutesTotal: number;
  equippedUserColorId: string | null;
  equippedListingId: string | null;
  equippedAccentId: string | null;
  equippedBadgeId: string | null;
  badgeUrl: string | null;
  badgeName: string | null;
  nameplateColor: string | null;
  dailyStreak: number;
  lastDailyClaim: string | null;
  selectedAgeRoleId: string | null;
  selectedGenderRoleId: string | null;
  selectedRankRoleId: string | null;
  selectedRegionRoleId: string | null;
  fishingLimitCooldownUntil: string | null;
}

export interface PurchaseResponse {
  userProfile: UserProfile;
  purchasedItem: ShopItem;
}

export interface ShopLayoutResponse {
  featuredItems: ShopItem[];
  dailyItems: ShopItem[];
} 