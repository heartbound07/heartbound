import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';
import { AUTH_ENDPOINTS } from '@/contexts/auth/constants';
import { tokenStorage } from '../contexts/auth/tokenStorage';

class WebSocketService {
  private client: Client;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private callback: ((message: any) => void) | null = null;
  private connected: boolean = false;
  private connecting: boolean = false;
  private retryCount: number = 0;
  private maxRetries: number = 3;

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
      debug: (_msg: string) => {
        // Uncomment the next line to enable detailed logging:
        // console.log('[STOMP DEBUG]', _msg);
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
        if (frame.body?.includes("Invalid JWT token")) {
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
   * Reconnects the WebSocket using the latest token from storage.
   * This is typically called after a token refresh.
   */
  reconnectWithFreshToken(): void {
    console.info('[WebSocket] Attempting reconnect with fresh token...');
    const tokens = tokenStorage.getTokens();
    if (!tokens?.accessToken) {
      console.error('[WebSocket] Cannot reconnect: No access token found after refresh.');
      this.disconnect(); // Disconnect if no token is available
      return;
    }

    // Update headers for the next connection attempt
    this.client.connectHeaders = {
      Authorization: "Bearer " + tokens.accessToken,
    };

    // If the client is already active, deactivate first to force re-authentication
    if (this.client.active) {
      console.log('[WebSocket] Deactivating existing connection before reconnecting...');
      this.client.deactivate().then(() => {
        console.log('[WebSocket] Reactivating connection...');
        // Use a small delay if needed, or activate directly
        setTimeout(() => this.client.activate(), 100); 
      }).catch(err => {
        console.error('[WebSocket] Error during deactivation for reconnect:', err);
        // Still try to activate if deactivation failed uncleanly
        setTimeout(() => this.client.activate(), 100);
      });
    } else {
      // If not active, just activate
      console.log('[WebSocket] Activating connection...');
      this.client.activate();
    }
  }
}

const webSocketService = new WebSocketService();
export default webSocketService;
