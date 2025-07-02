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
let refreshSubscribers: ((token: string | null, error?: Error) => void)[] = [];

// Function to add failed requests to the retry queue
function subscribeToTokenRefresh(callback: (token: string | null, error?: Error) => void) {
  refreshSubscribers.push(callback);
}

// Function to retry all queued requests with the new token
function onTokenRefreshed(newToken: string | null, error?: Error) {
  refreshSubscribers.forEach(callback => callback(newToken, error));
  refreshSubscribers = [];
}

// Function to refresh the access token
async function refreshAuthToken() {
  if (isRefreshing) {
    return new Promise<string>((resolve, reject) => {
      subscribeToTokenRefresh((token, error) => {
        if (error) reject(error);
        else if (token) resolve(token);
        else reject(new Error('Token refresh failed'));
      });
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

    console.log('httpClient: Refreshing token');
    const response = await axios.post(AUTH_ENDPOINTS.REFRESH, { refreshToken: tokens.refreshToken });
    
    if (response.data && response.data.accessToken) {
      // Parse user from localStorage
      const parsedAuthData = JSON.parse(authDataString);
      const user = parsedAuthData.user;
      
      // Store new tokens in memory
      tokenStorage.setTokens({
        accessToken: response.data.access_token || response.data.accessToken,
        refreshToken: response.data.refresh_token || response.data.refreshToken,
        tokenType: response.data.token_type || response.data.tokenType || 'bearer',
        expiresIn: response.data.expires_in || response.data.expiresIn || 3600,
        scope: response.data.scope || ''
      });
      
      // Update localStorage without tokens
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ user }));
      
      const newToken = response.data.access_token || response.data.accessToken;
      onTokenRefreshed(newToken);
      return newToken;
    } else {
      const error = new Error('Invalid token response format');
      onTokenRefreshed(null, error);
      throw error;
    }
  } catch (error) {
    console.error('Token refresh failed in httpClient:', error);
    
    // If it's a 401 error, clear tokens completely
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      console.log('Clearing tokens due to 401 during refresh');
      tokenStorage.clearTokens();
    }
    
    onTokenRefreshed(null, error instanceof Error ? error : new Error('Unknown error'));
    throw error;
  } finally {
    isRefreshing = false;
  }
}

// Request interceptor to attach the JWT token from localStorage if available
httpClient.interceptors.request.use(
  (config) => {
    const tokens = tokenStorage.getTokens();
    if (tokens?.accessToken) {
      config.headers.Authorization = `Bearer ${tokens.accessToken}`;
    }
    if (config.url?.startsWith('/api/')) {
      console.warn(`WARNING: Duplicate '/api' prefix detected in URL: ${config.url}`);
      // Optionally, automatically fix it:
      // config.url = config.url.replace(/^\/api\//, '/');
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle token refresh on 401 errors and CORS issues
httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };
    
    // Handle CORS errors specifically
    if (error.message === 'Network Error' || error.code === 'ERR_NETWORK') {
      console.error('CORS or Network Error detected:', {
        message: error.message,
        code: error.code,
        url: originalRequest?.url,
        method: originalRequest?.method
      });
      
      // Add user-friendly error information
      const corsError = new Error('Connection failed. Please check if the server is running and CORS is properly configured.');
      corsError.name = 'CORSError';
      return Promise.reject(corsError);
    }
    
    // Handle CORS preflight failures (405 Method Not Allowed on OPTIONS)
    if (error.response?.status === 405 && originalRequest?.method?.toUpperCase() === 'OPTIONS') {
      console.error('CORS Preflight failed:', {
        status: error.response.status,
        url: originalRequest.url,
        headers: error.response.headers
      });
      
      const preflightError = new Error('CORS preflight request failed. Server may not be configured for cross-origin requests.');
      preflightError.name = 'CORSPreflightError';
      return Promise.reject(preflightError);
    }
    
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
        console.error('Token refresh failed:', refreshError);
        return Promise.reject(refreshError);
      }
    }
    
    // Log other HTTP errors for debugging
    if (error.response) {
      console.error('HTTP Error:', {
        status: error.response.status,
        statusText: error.response.statusText,
        url: originalRequest?.url,
        method: originalRequest?.method,
        data: error.response.data
      });
    }
    
    return Promise.reject(error);
  }
);

export default httpClient;
