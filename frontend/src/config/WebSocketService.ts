import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
  private client: Client;

  constructor() {
    this.client = new Client({
      // Set up the WebSocket endpoint – ensure this matches the backend configuration.
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      reconnectDelay: 5000, // Attempts reconnection every 5000ms on disconnect.
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg: string) => {
        // Uncomment the line below to enable debugging logs.
        // console.log('[STOMP DEBUG]', msg);
      },
      // Optional: add connection headers here (e.g., JWT token) if needed:
      // connectionHeaders: { Authorization: "Bearer " + token },
    });
  }

  /**
   * Establishes a connection to the WebSocket broker and subscribes to a topic.
   * @param callback - Function to be called with the parsed message payload on each received message.
   */
  connect(callback: (message: any) => void) {
    this.client.onConnect = () => {
      console.log('Connected to WebSocket broker');
      // Subscribe to the topic for party updates.
      this.client.subscribe('/topic/party', (message: IMessage) => {
        try {
          const body = JSON.parse(message.body);
          callback(body);
        } catch (error) {
          console.error('Error parsing message from WebSocket:', error);
        }
      });
    };

    this.client.onStompError = (frame) => {
      console.error('Broker reported error:', frame.headers['message']);
      console.error('Error details:', frame.body);
    };

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
    this.client.publish({
      destination,
      body: JSON.stringify(body),
    });
  }
}

export default new WebSocketService();
