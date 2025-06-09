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
  eventType: 'MATCH_FOUND' | 'PAIRING_ENDED' | 'NO_MATCH_FOUND' | 'QUEUE_REMOVED';
  pairing?: any; // PairingDTO
  message: string;
  timestamp: string;
  totalInQueue?: number;
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
} & {
  [key: `/user/${string}/topic/pairings`]: PairingUpdateEvent;
};

// Helper type for topic inference
export type GetMessageType<T extends string> = T extends keyof TopicMessageMap 
  ? TopicMessageMap[T]
  : T extends `/user/${string}/topic/pairings`
  ? PairingUpdateEvent
  : any; 