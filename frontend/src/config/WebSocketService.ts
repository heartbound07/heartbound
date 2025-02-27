import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
  private client: Client;

  constructor() {
    this.client = new Client({
      // Set up the WebSocket endpoint – ensure this matches the backend configuration.
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      // Built-in auto-reconnect (in milliseconds). This value can be adjusted or made dynamic for production.
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg: string) => {
        // Uncomment the line below to enable detailed debugging logs:
        // console.log('[STOMP DEBUG]', msg);
      },
      // Optional: add connection headers here (e.g., JWT token) if needed:
      // connectionHeaders: { Authorization: "Bearer " + token },
      
      // Enhanced error handling callbacks:
      onWebSocketError: (evt: Event) => {
        console.error('[WebSocket] Error occurred:', evt);
      },
      onWebSocketClose: (evt: CloseEvent) => {
        console.error(
          `[WebSocket] Connection closed (Code: ${evt.code}, Reason: ${evt.reason}). ` +
          'Auto-reconnect is enabled; attempting to reconnect...'
        );
      }
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

    this.client.onStompError = (frame) => {
      console.error('[STOMP] Broker reported error:', frame.headers['message']);
      console.error('[STOMP] Error details:', frame.body);
    };

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
