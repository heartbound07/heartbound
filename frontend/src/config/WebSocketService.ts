import { Client, IMessage } from '@stomp/stompjs';
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

  constructor() {
    this.client = new Client({
      // Make sure this matches the endpoint registered in WebSocketConfig ("/ws")
      // DO NOT CHANGE FROM ("/api/ws") to ("/ws")! Backend Serverlet is set to /api
      webSocketFactory: () => new SockJS('http://localhost:8080/api/ws'),
      // Built-in auto-reconnect (in milliseconds). Adjust as needed.
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg: string) => {
        // Uncomment the line below to enable detailed debugging logs:
        // console.log('[STOMP DEBUG]', msg);
      },
      // Use our utility function to set the proper Authorization header.
      connectHeaders: {
        Authorization: "Bearer " + getAccessToken(),
      },
      // Enhanced error handling callbacks:
      onWebSocketError: (evt: Event) => {
        console.error('[WebSocket] Error occurred:', evt);
      },
      onWebSocketClose: (evt: CloseEvent) => {
        console.error(
          `[WebSocket] Connection closed (Code: ${evt.code}, Reason: ${evt.reason}). ` +
          'Auto-reconnect is enabled; attempting to reconnect...'
        );
      },
      // Enhanced error handling for STOMP errors.
      onStompError: async (frame) => {
        console.error('[STOMP] Broker reported error:', frame.headers['message']);
        console.error('[STOMP] Error details:', frame.body);

        if (frame.body && frame.body.includes("Invalid JWT token")) {
          try {
            // Retrieve the refresh token from the stored auth object.
            const authDataString = localStorage.getItem('heartbound_auth');
            let refreshToken = '';
            if (authDataString) {
              const authData = JSON.parse(authDataString);
              refreshToken = authData.tokens?.refreshToken || '';
            }
            if (!refreshToken) {
              throw new Error('No refresh token available');
            }

            // Call the refresh endpoint.
            const response = await axios.post('http://localhost:8080/api/auth/refresh', {
              refreshToken,
            });
            const newAccessToken = response.data.accessToken;

            // Update the stored auth object with the new access token.
            if (authDataString) {
              const authData = JSON.parse(authDataString);
              authData.tokens.accessToken = newAccessToken;
              localStorage.setItem('heartbound_auth', JSON.stringify(authData));
            } else {
              // Alternatively, if no authData is found, you might store it directly.
              localStorage.setItem('accessToken', newAccessToken);
            }
            console.info("[WebSocket] Received new access token.");

            // Update the connection headers with the new token.
            this.client.connectHeaders = {
              Authorization: "Bearer " + newAccessToken,
            };

            // Force a disconnect then reactivate to use the new token.
            this.client.deactivate();
            this.client.activate();
          } catch (refreshError) {
            console.error("[WebSocket] Failed to refresh token:", refreshError);
          }
        }
      },
    });
  }

  /**
   * Establishes a connection to the WebSocket broker and subscribes to a topic.
   * @param callback - Function to be called with the parsed message payload on each received message.
   */
  connect(callback: (message: any) => void) {
    this.client.onConnect = () => {
      console.info('[STOMP] Connected to WebSocket broker');
      // Subscribe to the topic for party updates.
      this.client.subscribe('/topic/party', (message: IMessage) => {
        try {
          const body = JSON.parse(message.body);
          callback(body);
        } catch (error) {
          console.error('[STOMP] Error parsing message from WebSocket:', error);
        }
      });
    };

    // Ensure the onStompError handler remains set.
    this.client.onStompError = this.client.onStompError;

    // Activate the client to establish the WebSocket connection.
    this.client.activate();
  }

  /**
   * Disconnects the WebSocket connection.
   */
  disconnect() {
    if (this.client && this.client.active) {
      this.client.deactivate();
    }
  }

  /**
   * Publishes a message to a specific destination.
   * @param destination - The endpoint destination (e.g., "/app/party/update").
   * @param body - The message payload to send.
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
