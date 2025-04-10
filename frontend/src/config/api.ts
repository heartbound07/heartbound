// API configuration for the application
export const API_CONFIG = {
  BASE_URL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  TIMEOUT: 15000, // 15 seconds
};

// Helper function to get full endpoint URL
export const getEndpoint = (path: string): string => {
  // Remove leading slash if present
  const cleanPath = path.startsWith('/') ? path.substring(1) : path;
  return `${API_CONFIG.BASE_URL}/${cleanPath}`;
}; 