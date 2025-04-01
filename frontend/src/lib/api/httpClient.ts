import axios, { AxiosRequestConfig, AxiosError} from 'axios';
import { AUTH_STORAGE_KEY, AUTH_ENDPOINTS } from '../../contexts/auth/constants';
import { tokenStorage } from '../../contexts/auth/tokenStorage';

const baseURL = import.meta.env.VITE_API_URL;

const httpClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Flag to prevent multiple concurrent refresh requests
let isRefreshing = false;
// Queue of requests to be retried after token refresh
let refreshSubscribers: ((token: string) => void)[] = [];

// Function to add failed requests to the retry queue
function subscribeToTokenRefresh(callback: (token: string) => void) {
  refreshSubscribers.push(callback);
}

// Function to retry all queued requests with the new token
function onTokenRefreshed(newToken: string) {
  refreshSubscribers.forEach(callback => callback(newToken));
  refreshSubscribers = [];
}

// Function to refresh the access token
async function refreshAuthToken() {
  if (isRefreshing) {
    return new Promise<string>((resolve) => {
      subscribeToTokenRefresh(resolve);
    });
  }

  isRefreshing = true;
  try {
    // Get stored auth data for user info
    const authDataString = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!authDataString) {
      throw new Error('No auth data found');
    }

    // Get tokens from memory instead of localStorage
    const tokens = tokenStorage.getTokens();
    if (!tokens?.refreshToken) {
      throw new Error('No refresh token available');
    }

    const response = await axios.post(AUTH_ENDPOINTS.REFRESH, { refreshToken: tokens.refreshToken });
    if (response.data) {
      // Parse user from localStorage
      const parsedAuthData = JSON.parse(authDataString);
      const user = parsedAuthData.user;
      
      // Store new tokens in memory
      tokenStorage.setTokens(response.data);
      
      // Update localStorage without tokens
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ user }));
      
      const newToken = response.data.accessToken;
      onTokenRefreshed(newToken);
      isRefreshing = false;
      return newToken;
    } else {
      throw new Error('Failed to refresh token');
    }
  } catch (error) {
    isRefreshing = false;
    throw error;
  }
}

// Request interceptor to attach the JWT token from localStorage if available
httpClient.interceptors.request.use(
  (config) => {
    const tokens = tokenStorage.getTokens();
    if (tokens?.accessToken) {
      config.headers.Authorization = `Bearer ${tokens.accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle token refresh on 401 errors
httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };
    
    // Only handle 401 errors that haven't been retried yet
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        // Try to refresh the token
        const newToken = await refreshAuthToken();
        
        // Retry the original request with the new token
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
        }
        
        return axios(originalRequest);
      } catch (refreshError) {
        // If refresh fails, propagate the error
        // The application should handle this by redirecting to login
        console.error('Token refresh failed:', refreshError);
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);

export default httpClient;
