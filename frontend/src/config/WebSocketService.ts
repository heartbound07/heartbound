import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';

// Utility function to extract the current access token from localStorage.
function getAccessToken(): string {
  const authDataString = localStorage.getItem('heartbound_auth');
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
      connectHeaders: {
        Authorization: "Bearer " + getAccessToken(),
      },
      onWebSocketError: (evt: Event) => {
        console.error('[WebSocket] Error occurred:', evt);
      },
      onWebSocketClose: (evt: CloseEvent) => {
        console.error(
          `[WebSocket] Connection closed (Code: ${evt.code}, Reason: ${evt.reason}). Auto-reconnect is enabled; attempting to reconnect...`
        );
      },
      onStompError: async (frame) => {
        console.error('[STOMP] Broker reported error:', frame.headers['message']);
        console.error('[STOMP] Error details:', frame.body);

        if (frame.body && frame.body.includes("Invalid JWT token")) {
          try {
            const authDataString = localStorage.getItem('heartbound_auth');
            let refreshToken = '';
            if (authDataString) {
              const authData = JSON.parse(authDataString);
              refreshToken = authData.tokens?.refreshToken || '';
            }
            if (!refreshToken) {
              throw new Error('No refresh token available');
            }

            // Call the token refresh endpoint.
            const response = await axios.post('http://localhost:8080/api/auth/refresh', {
              refreshToken,
            });
            const newAccessToken = response.data.accessToken;

            // Update the stored authentication data with the new access token.
            if (authDataString) {
              const authData = JSON.parse(authDataString);
              authData.tokens.accessToken = newAccessToken;
              localStorage.setItem('heartbound_auth', JSON.stringify(authData));
            } else {
              localStorage.setItem('accessToken', newAccessToken);
            }
            console.info("[WebSocket] Received new access token.");

            // Update the connection headers with the new token and reconnect.
            this.client.connectHeaders = {
              Authorization: "Bearer " + newAccessToken,
            };
            this.client.deactivate().then(() => {
              this.client.activate();
            });
          } catch (refreshError) {
            console.error("[WebSocket] Failed to refresh token:", refreshError);
          }
        }
      },
    });
  }

  /**
   * Establishes a connection to the WebSocket broker.
   * Optionally subscribes to the default party updates topic if a callback is provided.
   * @param callback - Function called on each received message from the default topic '/topic/party'.
   */
  connect(callback?: (message: any) => void) {
    this.client.onConnect = () => {
      console.info('[STOMP] Connected to WebSocket broker');
      if (callback) {
        // Subscribe to the default topic for party updates.
        const subscription = this.client.subscribe('/topic/party', (message: IMessage) => {
          try {
            const body = JSON.parse(message.body);
            callback(body);
          } catch (error) {
            console.error('[STOMP] Error parsing message from WebSocket:', error);
          }
        });
        this.subscriptions.set('/topic/party', subscription);
      }
    };

    // Activate the client to establish the connection.
    this.client.activate();
  }

  /**
   * Allows subscribing to an additional topic.
   * @param topic - The destination topic (e.g. "/topic/anotherTopic").
   * @param callback - Function called on each message from the specified topic.
   * @returns The subscription object.
   */
  subscribe(topic: string, callback: (message: any) => void): StompSubscription {
    if (!this.client.connected) {
      console.warn(`[WebSocket] Not connected. Cannot subscribe to ${topic} at this time.`);
      throw new Error("WebSocket client is not connected.");
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
    }
  }

  /**
   * Publishes a message to the specified destination.
   * @param destination - The endpoint destination (e.g. "/app/party/update").
   * @param body - The message payload.
   */
  send(destination: string, body: any) {
    try {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
    } catch (error) {
      console.error('[STOMP] Error sending message:', error);
    }
  }
}

export default new WebSocketService();
