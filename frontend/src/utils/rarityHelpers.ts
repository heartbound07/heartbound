// Define rarity levels and their associated colors
export const RARITY_COLORS = {
  LEGENDARY: '#FFD700', // Gold/Yellow
  EPIC: '#A020F0',      // Purple
  RARE: '#007BFF',      // Blue
  UNCOMMON: '#28A745',  // Green
  COMMON: '#6c757d',    // Grey
};

// Helper function to get color based on rarity
export const getRarityColor = (rarity: string): string => {
  return RARITY_COLORS[rarity as keyof typeof RARITY_COLORS] || RARITY_COLORS.COMMON;
};

// Helper function to get rarity label (formatted for display)
export const getRarityLabel = (rarity: string): string => {
  return rarity ? rarity.charAt(0) + rarity.slice(1).toLowerCase() : 'Common';
};

// Helper function to get border style based on rarity
export const getRarityBorderStyle = (rarity: string): string => {
  const color = getRarityColor(rarity);
  return `border-2 border-[${color}]`;
};

// Helper function to get badge style based on rarity
export const getRarityBadgeStyle = (rarity: string): React.CSSProperties => {
  const color = getRarityColor(rarity);
  return {
    backgroundColor: `${color}20`, // 20% opacity
    color: color,
    border: `1px solid ${color}`,
  };
}; 