import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';
import { AUTH_ENDPOINTS, AUTH_STORAGE_KEY } from '@/contexts/auth/constants';
import { tokenStorage } from '../contexts/auth/tokenStorage';

// Utility function to extract the current access token from localStorage.
// This will be called whenever we need the token, not just once during initialization
function getAccessToken(): string {
  // First try to get token from memory storage
  const memoryTokens = tokenStorage.getTokens();
  if (memoryTokens?.accessToken) {
    return memoryTokens.accessToken;
  }
  
  // Fallback to localStorage auth status check (though this won't have the actual token)
  if (localStorage.getItem('heartbound_auth_status') === 'true') {
    console.warn('[WebSocket] Auth status exists but no token in memory - needs refresh');
  }
  
  return '';
}

// Connection states to provide better visibility into the WebSocket lifecycle
export enum ConnectionState {
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  RECONNECTING = 'RECONNECTING',
  ERROR = 'ERROR'
}

class WebSocketService {
  private client: Client;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private callback: ((message: any) => void) | null = null;
  private connected: boolean = false;
  private connecting: boolean = false;
  private retryCount: number = 0;
  private maxRetries: number = 3;
  private lastUsedToken: string | null = null;
  private reconnectDebounceTimer: NodeJS.Timeout | null = null;
  private connectionState: ConnectionState = ConnectionState.DISCONNECTED;
  private stateChangeCallback: ((state: ConnectionState, error?: string) => void) | null = null;

  constructor() {
    // Initialize the STOMP client with configuration options.
    this.client = new Client({
      // Make sure this matches the endpoint registered in WebSocketConfig ("/ws")
      // DO NOT CHANGE FROM ("/api/ws") to ("/ws")! Backend Serverlet is set to /api
      webSocketFactory: () => {
        const socket = new SockJS('http://localhost:8080/api/ws');
        // Add error handler to detect transport-level issues
        socket.onerror = (error) => {
          console.error('[WebSocket] Transport error:', error);
          this.updateConnectionState(ConnectionState.ERROR, "Transport-level connection error");
          
          // Check if we should attempt to refresh token on connection errors
          // (which might be caused by authentication issues)
          if (this.retryCount >= 1) {
            this.handlePossibleAuthError();
          }
        };
        return socket;
      },
      // Built-in auto-reconnect (in milliseconds). Adjust as needed.
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg: string) => {
        // Uncomment the next line to enable detailed logging:
        // console.log('[STOMP DEBUG]', msg);
      },
      // We'll set connectHeaders dynamically before connecting
      connectHeaders: {},
      onConnect: () => {
        console.info('[WebSocket] Connected successfully');
        this.connected = true;
        this.connecting = false;
        this.retryCount = 0; // Reset retry count on successful connection
        this.updateConnectionState(ConnectionState.CONNECTED);

        // Restore all active subscriptions after reconnection
        if (this.subscriptions.size > 0) {
          console.info('[WebSocket] Restoring subscriptions...');
          
          // Create a new map to avoid modifying while iterating
          const currentSubscriptions = new Map(this.subscriptions);
          
          // Clear subscriptions - they'll be invalid after reconnect
          this.subscriptions.clear();
          
          // Re-subscribe to all previous destinations
          currentSubscriptions.forEach((_, destination) => {
            if (this.callback) {
              this.subscribe(destination, this.callback);
            }
          });
        } else if (this.callback) {
          // If no subscriptions exist but we have a callback, subscribe to the default topic
          console.info('[WebSocket] Subscribing to default topic');
          this.subscribe('/topic/party', this.callback);
        }
      },
      onDisconnect: () => {
        console.info('[WebSocket] Disconnected');
        this.connected = false;
        this.connecting = false;
        this.updateConnectionState(ConnectionState.DISCONNECTED);
      },
      onStompError: async (frame) => {
        console.error('[WebSocket] STOMP protocol error');
        console.error('[STOMP] Error details:', frame.body);
        this.updateConnectionState(ConnectionState.ERROR, frame.body);

        // If the error indicates an invalid token, attempt to refresh.
        if (frame.body && frame.body.includes("Invalid JWT token")) {
          const newAccessToken = await this.refreshAccessToken();
          if (newAccessToken) {
            console.info("[WebSocket] Received new access token. Reconnecting...");
            this.reconnectWithFreshToken();
          } else {
            console.error("[WebSocket] Failed to refresh token. Disconnecting WebSocket client.");
            this.client.deactivate();
            this.connected = false;
            this.connecting = false;
            this.updateConnectionState(ConnectionState.ERROR, "Authentication failed - unable to refresh token");
          }
        }
      },
    });
  }

  /**
   * Updates the connection state and notifies any registered callbacks
   */
  private updateConnectionState(state: ConnectionState, error?: string): void {
    this.connectionState = state;
    if (this.stateChangeCallback) {
      this.stateChangeCallback(state, error);
    }
  }

  /**
   * Register a callback to be notified of connection state changes
   */
  public onConnectionStateChange(callback: (state: ConnectionState, error?: string) => void): void {
    this.stateChangeCallback = callback;
    // Immediately invoke with current state
    callback(this.connectionState);
  }

  /**
   * Handle potential authentication errors by attempting to refresh the token
   */
  private async handlePossibleAuthError(): Promise<void> {
    if (this.retryCount < this.maxRetries) {
      console.info("[WebSocket] Connection issues might be auth-related. Attempting token refresh...");
      const newAccessToken = await this.refreshAccessToken();
      
      if (newAccessToken) {
        console.info("[WebSocket] Refreshed token, reconnecting with new credentials");
        this.reconnectWithFreshToken();
      } else {
        this.updateConnectionState(ConnectionState.ERROR, "Authentication refresh failed");
      }
    } else {
      console.error("[WebSocket] Max retries reached. Giving up connection attempts.");
      this.updateConnectionState(ConnectionState.ERROR, "Max connection retries reached");
    }
  }

  /**
   * Connect to the WebSocket server and subscribe to the party topic.
   * This method ensures we have a token before attempting connection.
   * @param callback Function to call when a message is received.
   */
  connect(callback: (message: any) => void): void {
    // Store the callback for later use if we need to reconnect
    this.callback = callback;

    // If already connected, just subscribe again if needed
    if (this.connected) {
      console.info('[WebSocket] Already connected');
      this.subscribe('/topic/party', callback);
      return;
    }
    
    // If in the process of connecting, don't try again
    if (this.connecting) {
      console.info('[WebSocket] Connection already in progress');
      return;
    }
    
    this.connecting = true;
    this.updateConnectionState(ConnectionState.CONNECTING);
    
    // Get the most current token for initial connection
    const accessToken = getAccessToken();
    
    if (!accessToken) {
      console.warn('[WebSocket] No valid access token found. Connection likely to fail.');
      this.updateConnectionState(ConnectionState.ERROR, "No access token available for connection");
    } else {
      // Store this token for later comparison (to detect changes)
      this.lastUsedToken = accessToken;
    }
    
    // Update the connection headers with the current token
    this.client.connectHeaders = {
      Authorization: "Bearer " + accessToken
    };
    
    console.info('[WebSocket] Connecting...');

    try {
      // If the client is active but not connected, deactivate first
      if (this.client.active && !this.client.connected) {
        this.client.deactivate().then(() => {
          setTimeout(() => {
            this.client.activate();
          }, 500);
        });
      } else {
        // Activate the client connection
        this.client.activate();
      }
    } catch (error) {
      console.error('[WebSocket] Error activating client:', error);
      this.connecting = false;
      this.updateConnectionState(ConnectionState.ERROR, "Failed to activate connection");
    }
  }

  /**
   * Disconnect the client from the WebSocket server.
   */
  disconnect(): void {
    try {
      if (this.client && this.client.active) {
        console.info('[WebSocket] Disconnecting...');
        this.client.deactivate();
      }
      
      // Clear all subscriptions when disconnecting
      this.subscriptions.clear();
      this.connected = false;
      this.connecting = false;
      this.updateConnectionState(ConnectionState.DISCONNECTED);
    } catch (error) {
      console.error('[WebSocket] Error disconnecting:', error);
    }
  }

  /**
   * Subscribe to a specific destination.
   * @param destination The destination to subscribe to.
   * @param callback Function to call when a message is received.
   */
  subscribe(destination: string, callback: (message: any) => void): void {
    // If already subscribed, unsubscribe first
    if (this.subscriptions.has(destination)) {
      console.info(`[WebSocket] Already subscribed to ${destination}, refreshing subscription`);
      this.unsubscribe(destination);
    }

    if (!this.client.connected) {
      console.warn(`[WebSocket] Cannot subscribe to ${destination} - not connected`);
      
      // Store callback for later use and attempt to connect if not already connecting
      this.callback = callback;
      
      if (!this.connecting) {
        console.info('[WebSocket] Attempting to connect before subscribing');
        this.connect(callback);
      }
      return;
    }

    try {
      console.info(`[WebSocket] Subscribing to ${destination}`);
      const subscription = this.client.subscribe(destination, (message: IMessage) => {
        try {
          const parsedMessage = JSON.parse(message.body);
          callback(parsedMessage);
        } catch (error) {
          console.error(`[WebSocket] Error parsing message from ${destination}:`, error);
          console.error('Raw message:', message.body);
        }
      });
      
      this.subscriptions.set(destination, subscription);
    } catch (error) {
      console.error(`[WebSocket] Error subscribing to ${destination}:`, error);
      this.updateConnectionState(ConnectionState.ERROR, `Failed to subscribe to ${destination}`);
    }
  }

  /**
   * Unsubscribe from a specific destination.
   * @param destination The destination to unsubscribe from.
   */
  unsubscribe(destination: string): void {
    const subscription = this.subscriptions.get(destination);
    if (subscription) {
      try {
        subscription.unsubscribe();
        this.subscriptions.delete(destination);
        console.info(`[WebSocket] Unsubscribed from ${destination}`);
      } catch (error) {
        console.error(`[WebSocket] Error unsubscribing from ${destination}:`, error);
      }
    }
  }

  /**
   * Attempts to refresh the access token
   * @returns A Promise that resolves to the new access token, or null if refresh failed
   */
  private async refreshAccessToken(): Promise<string | null> {
    try {
      console.info('[WebSocket] Attempting to refresh access token');
      
      // Try to refresh using axios and the refresh token endpoint
      const storedRefreshToken = localStorage.getItem(AUTH_STORAGE_KEY);
      if (!storedRefreshToken) {
        console.error('[WebSocket] No refresh token found in storage');
        return null;
      }
      
      const parsedToken = JSON.parse(storedRefreshToken);
      const refreshToken = parsedToken?.refreshToken;
      
      if (!refreshToken) {
        console.error('[WebSocket] Parsed storage, but no refresh token found');
        return null;
      }
      
      // Call the token refresh endpoint
      const response = await axios.post(AUTH_ENDPOINTS.REFRESH, { refreshToken });
      
      if (response.data && response.data.accessToken) {
        // Update token in storage
        const newTokens = {
          ...parsedToken,
          accessToken: response.data.accessToken
        };
        
        localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(newTokens));
        
        // Also update in memory storage if it's being used
        tokenStorage.setTokens(newTokens);
        
        return response.data.accessToken;
      }
      
      return null;
    } catch (error) {
      console.error('[WebSocket] Error refreshing token:', error);
      return null;
    }
  }

  /**
   * Reconnect to the WebSocket server with the most recent token.
   * This uses debouncing to prevent multiple attempts in rapid succession.
   */
  public reconnectWithFreshToken(): void {
    const backoffDelay = Math.min(1000 * Math.pow(2, this.retryCount), 30000); // Exponential backoff with 30s max
    this.retryCount++;
    
    // Update state to indicate reconnection
    this.updateConnectionState(ConnectionState.RECONNECTING);
    
    // Clear any pending reconnect timer
    if (this.reconnectDebounceTimer) {
      clearTimeout(this.reconnectDebounceTimer);
    }
    
    console.log(`[WebSocket] Will attempt reconnection in ${backoffDelay}ms (attempt ${this.retryCount})`);
    
    this.reconnectDebounceTimer = setTimeout(() => {
      if (this.client) {
        // Track if we're currently connecting to prevent multiple attempts
        if (!this.connecting) {
          // Get the freshest tokens possible
          const tokens = {
            accessToken: getAccessToken()
          };
          
          if (!tokens.accessToken) {
            console.error('[WebSocket] Cannot reconnect - no access token available');
            this.updateConnectionState(ConnectionState.ERROR, "No token available for reconnection");
            return;
          }
          
          // Store the current token being used for connection
          this.lastUsedToken = tokens.accessToken;
          
          // Update the connection headers with the new token
          this.client.connectHeaders = {
            Authorization: "Bearer " + tokens.accessToken,
          };
          
          // If already active, deactivate first
          if (this.client.active) {
            console.log('[WebSocket] Deactivating before reconnect');
            this.client.deactivate().then(() => {
              setTimeout(() => {
                console.log('[WebSocket] Reactivating with new token');
                this.connecting = true;
                this.client.activate();
              }, 500);
            }).catch(err => {
              console.error('[WebSocket] Error during deactivation:', err);
              this.connecting = false;
              this.updateConnectionState(ConnectionState.ERROR, "Failed during reconnection deactivation");
            });
          } else {
            console.log('[WebSocket] Connection not active, performing fresh connection');
            
            // Create a default callback if none exists
            if (!this.callback) {
              this.callback = (message) => {
                console.log('[WebSocket] Received message with default callback:', message);
              };
              console.log('[WebSocket] Created default callback for reconnection');
            }
            
            // Connect with the saved callback
            this.connect(this.callback);
          }
        } else {
          console.log('[WebSocket] Connection attempt already in progress');
        }
      } else {
        console.warn('[WebSocket] STOMP client not initialized');
        this.updateConnectionState(ConnectionState.ERROR, "STOMP client not initialized");
      }
    }, backoffDelay);
  }

  /**
   * Check if the current token is different from the last used token
   * and reconnect if necessary.
   */
  public checkAndUpdateToken(): void {
    const currentToken = getAccessToken();
    if (currentToken && currentToken !== this.lastUsedToken) {
      console.info('[WebSocket] Token has changed, reconnecting with new token');
      this.lastUsedToken = currentToken;
      this.reconnectWithFreshToken();
    }
  }

  /**
   * Get the current connection state
   */
  public getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Check if the WebSocket is connected
   */
  public isConnected(): boolean {
    return this.connected;
  }
}

// Export singleton instance to be used throughout the application
const webSocketService = new WebSocketService();
export default webSocketService;
