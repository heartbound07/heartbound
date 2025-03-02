import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';
import { AUTH_ENDPOINTS, AUTH_STORAGE_KEY } from '@/contexts/auth/constants';

// Utility function to extract the current access token from localStorage.
// This will be called whenever we need the token, not just once during initialization
function getAccessToken(): string {
  const authDataString = localStorage.getItem(AUTH_STORAGE_KEY);
  if (authDataString) {
    try {
      const authData = JSON.parse(authDataString);
      return authData.tokens?.accessToken || '';
    } catch (error) {
      console.error('Error parsing authentication data from localStorage:', error);
    }
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

        // Enhance token refresh handling: if the error indicates an invalid token, attempt to refresh.
        if (frame.body && frame.body.includes("Invalid JWT token")) {
          const newAccessToken = await this.refreshAccessToken();
          if (newAccessToken) {
            console.info("[WebSocket] Received new access token. Reconnecting...");
            // Update client headers and re-establish the connection.
            this.client.connectHeaders = {
              Authorization: "Bearer " + newAccessToken,
            };
            this.client.deactivate().then(() => {
              this.client.activate();
            });
          } else {
            console.error("[WebSocket] Failed to refresh token. Disconnecting WebSocket client.");
            this.client.deactivate();
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

    // If already connected or connecting, don't try again
    if (this.connected || this.connecting) {
      console.info('[WebSocket] Already connected or connecting');
      return;
    }

    // Get the current token before attempting to connect
    const token = getAccessToken();
    if (!token) {
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
      Authorization: "Bearer " + token,
    };

    console.info('[WebSocket] Connecting with token...');
    this.connecting = true;

    try {
      // Activate the client connection
      this.client.activate();
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
    const authDataString = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!authDataString) {
      console.error("[WebSocket] No authentication data stored.");
      return null;
    }
    
    try {
      // Use the AUTH_ENDPOINTS constant for consistency
      const response = await axios.post(AUTH_ENDPOINTS.REFRESH, {
        refreshToken: JSON.parse(authDataString).tokens?.refreshToken,
      });
      
      if (response.data && response.data.accessToken) {
        // Update stored tokens
        const authData = JSON.parse(authDataString);
        authData.tokens = response.data;
        localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authData));
        
        return response.data.accessToken;
      }
      return null;
    } catch (error) {
      console.error("[WebSocket] Error refreshing token:", error);
      return null;
    }
  }
}

export default new WebSocketService();
