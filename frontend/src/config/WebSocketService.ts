import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_CONFIG } from './api';

// Derive WebSocket URL from API_CONFIG
const webSocketUrl = `${API_CONFIG.BASE_URL}/ws`;
console.log(`[WebSocket] Configured URL: ${webSocketUrl}`);

// Connection state enum for clear state management
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

// Configuration interface for externalized settings
export interface WebSocketConfig {
  baseUrl: string;
  heartbeatIncoming: number;
  heartbeatOutgoing: number;
  debug: boolean;
}

// Enhanced subscription result interface
export interface SubscriptionResult {
  success: boolean;
  subscription?: StompSubscription;
  error?: string;
}

class WebSocketService {
  private client: Client;
  private connectionState: ConnectionState = 'disconnected';
  private subscriptions: Map<string, StompSubscription> = new Map();

  constructor(config?: Partial<WebSocketConfig>) {
    // Default configuration
    const defaultConfig: WebSocketConfig = {
      baseUrl: API_CONFIG.BASE_URL,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: false
    };

    const finalConfig = { ...defaultConfig, ...config };

        // Initialize the STOMP client with simplified configuration
    const wsUrl = `${finalConfig.baseUrl}/ws`;
    console.info(`[WebSocket] Initializing STOMP client with URL: ${wsUrl}`);
    
    const clientConfig: any = {
      webSocketFactory: () => {
        console.info(`[WebSocket] Creating SockJS connection to: ${wsUrl}`);
        return new SockJS(wsUrl);
      },
      heartbeatIncoming: finalConfig.heartbeatIncoming,
      heartbeatOutgoing: finalConfig.heartbeatOutgoing,
      // Note: No reconnectDelay - reconnection is handled by WebSocketProvider
      connectHeaders: {},
    };
    
    // Only add debug function if debugging is enabled
    if (finalConfig.debug) {
      clientConfig.debug = (msg: string) => console.log('[STOMP]', msg);
    }
    
    this.client = new Client(clientConfig);
    
    console.info('[WebSocket] STOMP client initialized successfully');
  }

  /**
   * Connect to the WebSocket server with Promise-based API
   * @param accessToken - JWT access token for authentication
   * @returns Promise that resolves when connection is established
   */
  async connect(accessToken: string): Promise<void> {
    if (this.connectionState === 'connecting') {
      throw new Error('Connection already in progress');
    }
    
    if (this.connectionState === 'connected') {
      console.info('[WebSocket] Already connected');
      return; // Already connected
    }

    this.connectionState = 'connecting';
    console.info('[WebSocket] Connecting with token...');

    return new Promise((resolve, reject) => {
      // Add connection timeout
      const connectionTimeout = setTimeout(() => {
        console.error('[WebSocket] Connection timeout after 15 seconds');
        this.connectionState = 'error';
        reject(new Error('WebSocket connection timeout'));
      }, 15000);

      const cleanup = () => {
        clearTimeout(connectionTimeout);
      };

      // Set authentication headers
      this.client.connectHeaders = {
        Authorization: `Bearer ${accessToken}`
      };

      // Configure connection callbacks
      this.client.onConnect = () => {
        console.info('[WebSocket] Connected successfully');
        this.connectionState = 'connected';
        cleanup();
        resolve();
      };

      this.client.onStompError = (frame) => {
        console.error('[STOMP] Broker reported error:', frame.headers['message']);
        console.error('[STOMP] Error details:', frame.body);
        this.connectionState = 'error';
        cleanup();
        reject(new Error(frame.body || 'STOMP authentication error'));
      };

      this.client.onWebSocketError = (event) => {
        console.error('[WebSocket] WebSocket error occurred:', event);
        this.connectionState = 'error';
        cleanup();
        reject(new Error('WebSocket connection error'));
      };

      this.client.onWebSocketClose = (event) => {
        console.warn(`[WebSocket] Connection closed (Code: ${event.code}, Reason: ${event.reason})`);
        if (this.connectionState === 'connecting') {
          // Connection failed during initial connection attempt
          this.connectionState = 'error';
          cleanup();
          reject(new Error(`WebSocket connection closed during connection attempt (Code: ${event.code})`));
        } else {
          this.connectionState = 'disconnected';
          this.subscriptions.clear();
        }
      };

      // Activate the connection
      try {
        console.info('[WebSocket] Activating STOMP client...');
        this.client.activate();
        console.info('[WebSocket] STOMP client activation initiated');
      } catch (error) {
        console.error('[WebSocket] Error during client activation:', error);
        this.connectionState = 'error';
        cleanup();
        reject(new Error(`Failed to activate WebSocket client: ${error instanceof Error ? error.message : String(error)}`));
      }
    });
  }

  /**
   * Disconnect from the WebSocket server
   * @returns Promise that resolves when disconnection is complete
   */
  async disconnect(): Promise<void> {
    if (this.connectionState === 'disconnected') {
      console.info('[WebSocket] Already disconnected');
      return;
    }

    console.info('[WebSocket] Disconnecting...');

    return new Promise((resolve) => {
      // Configure disconnect callback
      this.client.onDisconnect = () => {
        console.info('[WebSocket] Disconnected successfully');
        this.connectionState = 'disconnected';
        this.subscriptions.clear();
        resolve();
      };

      // Deactivate the connection
      try {
        this.client.deactivate();
      } catch (error) {
        console.error('[WebSocket] Error during deactivation:', error);
        // Still mark as disconnected and resolve
        this.connectionState = 'disconnected';
        this.subscriptions.clear();
        resolve();
      }
    });
  }

  /**
   * Subscribe to a topic with enhanced error handling
   * @param topic - The topic to subscribe to
   * @param callback - Function to call when a message is received
   * @returns SubscriptionResult indicating success/failure with details
   */
  subscribe(topic: string, callback: (message: any) => void): SubscriptionResult {
    if (this.connectionState !== 'connected') {
      return {
        success: false,
        error: `Cannot subscribe to ${topic}: not connected (state: ${this.connectionState})`
      };
    }

    // Check for existing subscription
    if (this.subscriptions.has(topic)) {
      console.info(`[WebSocket] Already subscribed to ${topic}`);
      return {
        success: true,
        subscription: this.subscriptions.get(topic)!
      };
    }

    try {
      const subscription = this.client.subscribe(topic, (message: IMessage) => {
        try {
          const body = JSON.parse(message.body);
          callback(body);
        } catch (error) {
          console.error(`[WebSocket] Error parsing message from ${topic}:`, error);
        }
      });

      this.subscriptions.set(topic, subscription);
      console.info(`[WebSocket] Successfully subscribed to ${topic}`);

      return {
        success: true,
        subscription
      };
    } catch (error) {
      console.error(`[WebSocket] Error subscribing to ${topic}:`, error);
      return {
        success: false,
        error: `Failed to subscribe to ${topic}: ${error instanceof Error ? error.message : String(error)}`
      };
    }
  }

  /**
   * Unsubscribe from a topic
   * @param topic - The topic to unsubscribe from
   * @returns true if unsubscribed successfully, false if not subscribed
   */
  unsubscribe(topic: string): boolean {
    const subscription = this.subscriptions.get(topic);
    if (!subscription) {
      console.warn(`[WebSocket] Not subscribed to ${topic}, cannot unsubscribe`);
      return false;
    }

    try {
      subscription.unsubscribe();
      this.subscriptions.delete(topic);
      console.info(`[WebSocket] Successfully unsubscribed from ${topic}`);
      return true;
    } catch (error) {
      console.error(`[WebSocket] Error unsubscribing from ${topic}:`, error);
      return false;
    }
  }

  /**
   * Send a message to the specified destination
   * @param destination - The endpoint destination (e.g. "/app/party/update")
   * @param body - The message payload
   * @returns Promise that resolves when message is sent
   * @throws Error if not connected or send fails
   */
  async send(destination: string, body: any): Promise<void> {
    if (this.connectionState !== 'connected') {
      throw new Error(`Cannot send message: not connected (state: ${this.connectionState})`);
    }

    return new Promise((resolve, reject) => {
      try {
        this.client.publish({
          destination,
          body: JSON.stringify(body)
        });
        
        console.debug(`[WebSocket] Message sent to ${destination}`);
        resolve();
        
      } catch (error) {
        const errorMessage = `Failed to send message to ${destination}: ${error instanceof Error ? error.message : String(error)}`;
        console.error('[WebSocket]', errorMessage);
        reject(new Error(errorMessage));
      }
    });
  }

  /**
   * Get the current connection state
   * @returns Current connection state
   */
  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Check if the WebSocket is currently connected and active
   * @returns true if connected and client is active
   */
  isConnected(): boolean {
    return this.connectionState === 'connected' && this.client.active;
  }

  /**
   * Get list of active subscriptions
   * @returns Array of topic names currently subscribed to
   */
  getActiveSubscriptions(): string[] {
    return Array.from(this.subscriptions.keys());
  }

  /**
   * Get subscription count
   * @returns Number of active subscriptions
   */
  getSubscriptionCount(): number {
    return this.subscriptions.size;
  }
}

// Create singleton instance with default configuration
const webSocketService = new WebSocketService();
export default webSocketService;
