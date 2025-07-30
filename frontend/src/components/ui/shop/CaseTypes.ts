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
    fishingRodMultiplier?: number;
    gradientEndColor?: string;
    fishingRodPartType?: string;
  };
  rollValue: number;
  rolledAt: string;
  alreadyOwned: boolean;
  compensationAwarded?: boolean;
  compensatedCredits?: number;
  compensatedXp?: number;
}

export interface CaseItemDTO {
  id: string;
  caseId: string;
  containedItem: {
    id: string;
    name: string;
    description: string;
    price: number;
    category: string;
    imageUrl: string;
    thumbnailUrl?: string;
    rarity: string;
    owned: boolean;
    fishingRodMultiplier?: number;
    gradientEndColor?: string;
    fishingRodPartType?: string;
  };
  dropRate: number;
}

export interface CaseContents {
  caseId: string;
  caseName: string;
  items: CaseItemDTO[];
  totalDropRate: number;
  itemCount: number;
}

export type AnimationState = 'idle' | 'loading' | 'rolling' | 'decelerating' | 'revealing' | 'reward'; 