// Import QueueStatsDTO type for admin stats

export type WebSocketConnectionStatus = 
  | 'disconnected' 
  | 'connecting' 
  | 'connected' 
  | 'error' 
  | 'reconnecting';

export interface WebSocketError {
  type: 'network' | 'auth' | 'server' | 'unknown';
  message: string;
  timestamp: number;
  isRecoverable: boolean;
}

// Enhanced retry configuration interfaces
export interface RetryConfig {
  maxRetries: number;
  baseDelay: number;
  maxDelay: number;
  jitterPercent: number;
  backoffMultiplier: number;
  
  // Specific configs for different error types
  authRetryConfig: {
    maxRetries: number;
    baseDelay: number;
  };
  
  networkRetryConfig: {
    maxRetries: number;
    baseDelay: number;
  };
}

// Enhanced retry state management
export interface RetryState {
  attempt: number;
  maxRetries: number;
  lastError: WebSocketError | null;
  errorType: 'network' | 'auth' | 'server' | 'unknown';
  nextRetryAt: number | null;
  isRetryable: boolean;
  consecutiveAuthFailures: number;
  consecutiveNetworkFailures: number;
}

// Message Queue Types
export enum MessagePriority {
  CRITICAL = 0,    // User actions (join party, send message)
  HIGH = 1,        // Real-time updates (status changes)
  NORMAL = 2,      // General messages
  LOW = 3,         // Analytics, non-essential data
}

export enum DeliveryMode {
  AT_LEAST_ONCE = 'at-least-once',  // Guaranteed delivery, possible duplicates
  AT_MOST_ONCE = 'at-most-once',    // No duplicates, possible message loss
  EXACTLY_ONCE = 'exactly-once',    // Guaranteed single delivery (requires server support)
}

export interface QueuedMessage {
  id: string;                    // Unique message identifier
  destination: string;           // WebSocket destination
  body: any;                     // Message payload
  timestamp: number;             // Creation timestamp
  ttl: number;                   // Time to live (milliseconds)
  priority: MessagePriority;     // Message priority level
  retryCount: number;            // Number of retry attempts
  maxRetries: number;            // Maximum retry attempts
  lastAttempt?: number;          // Timestamp of last send attempt
  persistent: boolean;           // Should survive page refresh
  deliveryMode: DeliveryMode;    // At-least-once, at-most-once, exactly-once
}

export interface MessageQueueConfig {
  maxQueueSize: number;          // Maximum messages in queue
  defaultTTL: number;            // Default message TTL (30 seconds)
  maxRetries: number;            // Default max retries (3)
  persistenceEnabled: boolean;   // Enable localStorage persistence
  batchSize: number;             // Messages to send per batch
  batchDelay: number;            // Delay between batches (ms)
  
  priorityConfig: {
    [MessagePriority.CRITICAL]: { ttl: number; maxRetries: number; };
    [MessagePriority.HIGH]: { ttl: number; maxRetries: number; };
    [MessagePriority.NORMAL]: { ttl: number; maxRetries: number; };
    [MessagePriority.LOW]: { ttl: number; maxRetries: number; };
  };
}

export interface QueueStatistics {
  totalMessages: number;
  pendingMessages: number;
  failedMessages: number;
  deliveredMessages: number;
}

export interface WebSocketContextValue {
  // Connection state
  isConnected: boolean;
  connectionStatus: WebSocketConnectionStatus;
  lastError: WebSocketError | null;
  retryAttempt: number;
  maxRetries: number;
  
  // Enhanced retry state
  retryState: RetryState;
  retryConfig: RetryConfig;
  
  // Subscription management  
  subscribe: <T>(topic: string, callback: (message: T) => void) => () => void;
  
  // Message queuing
  sendMessage: (
    destination: string,
    body: any,
    options?: Partial<Pick<QueuedMessage, 'priority' | 'ttl' | 'persistent' | 'deliveryMode' | 'maxRetries'>>
  ) => Promise<string>;
  
  // Queue statistics
  queueStats: QueueStatistics;
  
  // Queue management
  clearQueue: () => void;
  pauseQueue: () => void;
  resumeQueue: () => void;
  getQueueSize: () => number;
  
  // Manual controls
  reconnect: () => void;
  clearError: () => void;
}

export interface SubscriptionInfo {
  callback: (message: any) => void;
  unsubscribe: () => void;
  refCount: number;
  isActive: boolean;
}

// Message types from existing providers
export interface LFGPartyEvent {
  eventType: string;
  party?: any;
  minimalParty?: {
    id: string;
    userId: string;
    status: string;
    participants: string[];
    joinRequests: string[];
    invitedUsers: string[];
  };
  message: string;
  targetUserId?: string;
}

export interface PairingUpdateEvent {
  eventType: 'MATCH_FOUND' | 'PAIRING_ENDED' | 'ACTIVITY_UPDATE';
  pairing?: any; // PairingDTO
  message: string;
  timestamp: string;
  isInitiator?: boolean; // For PAIRING_ENDED events - true if user initiated breakup
}

export interface QueueUpdateEvent {
  totalQueueSize: number;
  timestamp?: string;
}

export interface QueueConfigUpdateEvent {
  queueEnabled: boolean;
  maxWaitTime: number;
  timestamp?: string;
}

// Utility type for extracting message type from topic
export type TopicMessageMap = {
  '/topic/party': LFGPartyEvent;
  '/topic/queue': QueueUpdateEvent;
  '/topic/queue/config': QueueConfigUpdateEvent;
  '/topic/admin/queue-stats': any; // QueueStatsDTO was removed, so using 'any' for now
} & {
  [key: `/user/${string}/topic/pairings`]: PairingUpdateEvent;
};

// Helper type for topic inference
export type GetMessageType<T extends string> = T extends keyof TopicMessageMap 
  ? TopicMessageMap[T]
  : T extends `/user/${string}/topic/pairings`
  ? PairingUpdateEvent
  : any; 