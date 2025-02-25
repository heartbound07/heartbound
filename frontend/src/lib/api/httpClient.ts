import axios from 'axios';

const baseURL = import.meta.env.VITE_API_URL;

const httpClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Request interceptor to attach the JWT token from localStorage if available
httpClient.interceptors.request.use(
  (config) => {
    const authDataString = localStorage.getItem('heartbound_auth');
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

// Response interceptor to handle errors globally
httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      console.error('Unauthorized - possibly invalid token');
      // Optionally, you might want to trigger a logout or token refresh here.
    }
    return Promise.reject(error);
  }
);

export default httpClient;
