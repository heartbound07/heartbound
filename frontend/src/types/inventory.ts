export interface ShopItem {
  id: string;
  instanceId?: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  thumbnailUrl?: string;
  owned: boolean;
  equipped?: boolean;
  rarity: string;
  isCase?: boolean;
  caseContentsCount?: number;
  quantity?: number;
  fishingRodMultiplier?: number;
  gradientEndColor?: string;
  maxCopies?: number;
  copiesSold?: number;
  durability?: number;
  maxDurability?: number;
  experience?: number;
  level?: number;
  xpForNextLevel?: number;
  fishingRodPartType?: string;
}

export interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

export interface RollResult {
  caseId: string;
  caseName: string;
  wonItem: {
    id: string;
    name: string;
    description: string;
    price: number;
    category: string;
    imageUrl: string;
    thumbnailUrl?: string;
    rarity: string;
    owned: boolean;
  };
  rollValue: number;
  rolledAt: string;
  alreadyOwned: boolean;
  compensationAwarded?: boolean;
  compensatedCredits?: number;
  compensatedXp?: number;
} 