import axios, { AxiosRequestConfig, AxiosError} from 'axios';
import { AUTH_STORAGE_KEY, AUTH_ENDPOINTS } from '../../contexts/auth/constants';

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
    const authDataString = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!authDataString) {
      throw new Error('No auth data found');
    }

    const authData = JSON.parse(authDataString);
    const refreshToken = authData.tokens?.refreshToken;
    
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }

    const response = await axios.post(AUTH_ENDPOINTS.REFRESH, { refreshToken });
    const newTokens = response.data;
    
    if (!newTokens || !newTokens.accessToken) {
      throw new Error('Invalid refresh response');
    }

    // Update stored tokens
    authData.tokens = newTokens;
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authData));
    
    // Process queued requests
    onTokenRefreshed(newTokens.accessToken);
    
    return newTokens.accessToken;
  } catch (error) {
    // Clear auth data on refresh failure
    localStorage.removeItem(AUTH_STORAGE_KEY);
    refreshSubscribers = [];
    throw error;
  } finally {
    isRefreshing = false;
  }
}

// Request interceptor to attach the JWT token from localStorage if available
httpClient.interceptors.request.use(
  (config) => {
    const authDataString = localStorage.getItem(AUTH_STORAGE_KEY);
    if (authDataString) {
      try {
        const authData = JSON.parse(authDataString);
        const token = authData.tokens?.accessToken;
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
      } catch (error) {
        console.error('Error parsing auth data from localStorage:', error);
      }
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
