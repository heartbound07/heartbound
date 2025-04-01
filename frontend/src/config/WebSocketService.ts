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

  constructor() {
    // Initialize the STOMP client with configuration options.
    this.client = new Client({
      // Make sure this matches the endpoint registered in WebSocketConfig ("/ws")
      // DO NOT CHANGE FROM ("/api/ws") to ("/ws")! Backend Serverlet is set to /api
      webSocketFactory: () => new SockJS('http://localhost:8080/api/ws'),
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
        this.retryCount = 0;
        // If we have a callback stored, subscribe to the party topic
        if (this.callback) {
          this.subscribe('/topic/party', this.callback);
        }
      },
      onWebSocketError: (evt: Event) => {
        console.error('[WebSocket] Error occurred:', evt);
        this.connected = false;
      },
      onWebSocketClose: (evt: CloseEvent) => {
        console.error(
          `[WebSocket] Connection closed (Code: ${evt.code}, Reason: ${evt.reason}). Auto-reconnect is enabled; attempting to reconnect...`
        );
        this.connected = false;
      },
      onStompError: async (frame) => {
        console.error('[STOMP] Broker reported error:', frame.headers['message']);
        console.error('[STOMP] Error details:', frame.body);

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
          }
        }
      },
    });
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

    // Get token from memory, not localStorage
    const tokens = tokenStorage.getTokens();
    if (!tokens?.accessToken) {
      console.warn('[WebSocket] No authentication token available. Delaying connection.');
      // We'll retry in 1 second if we're within our retry limit
      if (this.retryCount < this.maxRetries) {
        this.retryCount++;
        setTimeout(() => this.connect(callback), 1000);
      } else {
        console.error('[WebSocket] Max retries reached. Could not establish connection without token.');
      }
      return;
    }

    // Update the connection headers with the current token
    this.client.connectHeaders = {
      Authorization: "Bearer " + tokens.accessToken,
    };
    
    this.lastUsedToken = tokens.accessToken;

    console.info('[WebSocket] Connecting with token...');
    this.connecting = true;

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
    }
  }

  /**
   * Subscribe to a topic.
   * @param topic The topic to subscribe to.
   * @param callback Function to call when a message is received.
   * @returns The subscription object.
   */
  subscribe(topic: string, callback: (message: any) => void): StompSubscription | null {
    if (!this.client.active) {
      console.error(`[STOMP] Cannot subscribe to ${topic}: Client not connected`);
      return null;
    }
    const subscription = this.client.subscribe(topic, (message: IMessage) => {
      try {
        const body = JSON.parse(message.body);
        callback(body);
      } catch (error) {
        console.error(`[STOMP] Error parsing message from topic ${topic}:`, error);
      }
    });
    this.subscriptions.set(topic, subscription);
    return subscription;
  }

  /**
   * Disconnects the WebSocket connection and clears all subscriptions.
   */
  disconnect() {
    if (this.client && this.client.active) {
      this.client.deactivate();
      this.subscriptions.clear();
      this.connected = false;
      this.connecting = false;
    }
  }

  /**
   * Publishes a message to the specified destination.
   * @param destination - The endpoint destination (e.g. "/app/party/update").
   * @param body - The message payload.
   */
  send(destination: string, body: any) {
    try {
      if (!this.client.active) {
        console.error('[STOMP] Cannot send message: Client not connected');
        return;
      }
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
    } catch (error) {
      console.error('[STOMP] Error sending message:', error);
    }
  }

  // Helper method to refresh the access token.
  private async refreshAccessToken(): Promise<string | null> {
    const tokens = tokenStorage.getTokens();
    if (!tokens?.refreshToken) {
      console.error("[WebSocket] No refresh token found in memory.");
      return null;
    }
    
    try {
      const response = await axios.post(AUTH_ENDPOINTS.REFRESH, {
        refreshToken: tokens.refreshToken
      });
      
      if (response.data && response.data.accessToken) {
        // Update tokens in memory
        tokenStorage.setTokens(response.data);
        
        console.info("[WebSocket] Token refresh successful");
        return response.data.accessToken;
      }
      
      console.warn("[WebSocket] Token refresh response missing access token");
      return null;
    } catch (error) {
      console.error("[WebSocket] Error refreshing token:", error);
      // Add more specific error handling
      if (axios.isAxiosError(error) && error.response) {
        console.error("[WebSocket] Server returned error:", error.response.status, error.response.data);
      }
      return null;
    }
  }

  /**
   * Reconnects using the latest token from localStorage.
   * This can be called after token refresh to ensure WebSocket uses the updated token.
   */
  public reconnectWithFreshToken(): void {
    const tokens = tokenStorage.getTokens();
    if (!tokens?.accessToken) {
      console.warn('[WebSocket] No valid token available for reconnection');
      return;
    }
    
    console.log('[WebSocket] Reconnecting with fresh token');
    
    if (this.client) {
      // Track if we're currently connecting to prevent multiple attempts
      if (!this.connecting) {
        // Store the current token being used for connection
        this.lastUsedToken = tokens.accessToken;
        
        // Update the connection headers with the new token
        this.client.connectHeaders = {
          Authorization: "Bearer " + tokens.accessToken,
        };
        
        // If already active, deactivate first
        if (this.client.active) {
          this.connecting = true;
          this.retryCount = 0;
          
          console.log('[WebSocket] Deactivating current connection before reconnecting');
          
          // Deactivate then reactivate after a short delay
          this.client.deactivate().then(() => {
            // Small delay to ensure clean disconnect
            setTimeout(() => {
              console.log('[WebSocket] Reactivating with new token');
              this.client.activate();
            }, 500);
          }).catch(err => {
            console.error('[WebSocket] Error during reconnection:', err);
            this.connecting = false;
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
    }
  }
}

export default new WebSocketService();
