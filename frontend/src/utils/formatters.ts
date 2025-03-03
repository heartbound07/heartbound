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