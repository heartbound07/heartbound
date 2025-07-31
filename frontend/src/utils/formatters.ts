export const formatDisplayText = (text: string | undefined, defaultText: string = "N/A"): string => {
  if (!text) return defaultText;
  
  // Handle region values with underscores (e.g., "NA_EAST" to "NA East")
  if (text.includes("_")) {
    return text.split("_").map(part => 
      part.charAt(0) + part.slice(1).toLowerCase()
    ).join(" ");
  }
  
  // Default case: capitalize first letter
  return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase();
}

export const formatBooleanText = (value: boolean | undefined): string => {
  if (value === undefined || value === null) return "N/A";
  return value ? "Yes" : "No";
}

export const formatVoiceTime = (minutes: number | undefined): string => {
  if (minutes === undefined || minutes === null || minutes < 0) {
    return "0m";
  }
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return `${hours}h ${remainingMinutes}m`;
};

// Add category mapping for special cases
export const categoryDisplayMapping: Record<string, string> = {
    'USER_COLOR': 'Nameplate',
    'LISTING': 'Listing Color',
    'ACCENT': 'Profile Accent',
    'BADGE': 'Badge',
    'CASE': 'Case',
    'FISHING_ROD': 'Fishing Rod',
    'FISHING_ROD_PART': 'Fishing Rod Part'
};

// Format category for display with custom mappings
export const formatCategoryDisplay = (category: string): string => {
    return categoryDisplayMapping[category] || formatDisplayText(category);
}; 