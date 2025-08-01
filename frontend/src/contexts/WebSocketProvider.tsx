import {
  createContext,
  useState,
  useEffect,
  ReactNode,
  useCallback,
  useMemo,
  useRef,
} from 'react';
import webSocketService from '../config/WebSocketService';
import { useAuth } from '@/contexts/auth/useAuth';
import { useTokenManagement } from '../hooks/useTokenManagement';
import {
  WebSocketContextValue,
  WebSocketConnectionStatus,
  WebSocketError,
  SubscriptionInfo,
  RetryConfig,
  RetryState,
  QueuedMessage,
  MessagePriority,
  DeliveryMode,
  QueueStatistics,
} from './types/websocket';
import { MessageQueue, defaultQueueConfig } from '../utils/MessageQueue';

interface WebSocketProviderProps {
  children: ReactNode;
}

// Default retry configuration
const defaultRetryConfig: RetryConfig = {
  maxRetries: 5,
  baseDelay: 1000,
  maxDelay: 30000,
  jitterPercent: 25,
  backoffMultiplier: 2,
  
  authRetryConfig: {
    maxRetries: 3, // Fewer retries for auth failures
    baseDelay: 2000, // Longer delay for auth issues
  },
  
  networkRetryConfig: {
    maxRetries: 8, // More retries for network issues
    baseDelay: 500, // Shorter initial delay for network
  },
};

// Initial retry state
const initialRetryState: RetryState = {
  attempt: 0,
  maxRetries: defaultRetryConfig.maxRetries,
  lastError: null,
  errorType: 'unknown',
  nextRetryAt: null,
  isRetryable: true,
  consecutiveAuthFailures: 0,
  consecutiveNetworkFailures: 0,
};

export const WebSocketContext = createContext<WebSocketContextValue | undefined>(undefined);

// Singleton MessageQueue instance to persist across React re-renders
let globalMessageQueue: MessageQueue | null = null;

const getOrCreateMessageQueue = (): MessageQueue => {
  if (!globalMessageQueue) {
    globalMessageQueue = new MessageQueue(defaultQueueConfig);
  }
  return globalMessageQueue;
};

export const WebSocketProvider = ({ children }: WebSocketProviderProps) => {
  const { isAuthenticated, tokens, refreshToken } = useAuth();
  const { isTokenValid } = useTokenManagement();
  
  // Connection state
  const [connectionStatus, setConnectionStatus] = useState<WebSocketConnectionStatus>('disconnected');
  const [lastError, setLastError] = useState<WebSocketError | null>(null);
  
  // Enhanced retry state management
  const [retryState, setRetryState] = useState<RetryState>(initialRetryState);
  
  // Legacy retry attempt for backward compatibility
  const retryAttempt = retryState.attempt;
  const maxRetries = retryState.maxRetries;
  
  // Subscription management
  const subscriptionsRef = useRef<Map<string, SubscriptionInfo>>(new Map());
  const pendingSubscriptionsRef = useRef<Set<string>>(new Set());
  const isConnectingRef = useRef(false);
  const retryTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const connectionCheckTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const subscriptionTimeoutsRef = useRef<Map<string, NodeJS.Timeout>>(new Map());
  
  // Derived state
  const isConnected = connectionStatus === 'connected';
  
  // Use singleton MessageQueue instance
  const messageQueueRef = useRef<MessageQueue>(getOrCreateMessageQueue());
  const hasRestoredPersistenceRef = useRef(false);
  const [queueStats, setQueueStats] = useState<QueueStatistics>({
    totalMessages: 0,
    pendingMessages: 0,
    failedMessages: 0,
    deliveredMessages: 0,
  });
  
  const isProcessingQueueRef = useRef(false);
  const queueProcessingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  
  // Debug logging for connection status changes
  useEffect(() => {
    console.log(`[WebSocket] Connection status changed to: ${connectionStatus}, isConnected: ${isConnected}`);
    console.log(`[WebSocket] Retry state:`, retryState);
  }, [connectionStatus, isConnected, retryState]);

  // Initialize message queue and restore persistence on mount
  useEffect(() => {
    console.log('[WebSocket] Initializing message queue...');
    
    // Only restore persistence once to prevent multiple restores during React re-renders
    if (!hasRestoredPersistenceRef.current) {
      const restoredCount = messageQueueRef.current.restoreFromPersistence();
      if (restoredCount > 0) {
        setQueueStats(prev => ({
          ...prev,
          totalMessages: prev.totalMessages + restoredCount,
          pendingMessages: prev.pendingMessages + restoredCount,
        }));
        console.log(`[WebSocket] Restored ${restoredCount} messages from persistence`);
      }
      hasRestoredPersistenceRef.current = true;
    }
  }, []);

  // Error classification system
  const classifyError = useCallback((error: any): WebSocketError['type'] => {
    const message = error.message?.toLowerCase() || '';
    
    // Auth errors - including new WebSocket error types
    if (message.includes('websocket_auth_error') ||
        message.includes('token') || 
        message.includes('auth') || 
        message.includes('unauthorized') ||
        message.includes('forbidden') ||
        message.includes('invalid jwt') ||
        message.includes('authentication')) {
      return 'auth';
    }
    
    // Network errors
    if (message.includes('network') ||
        message.includes('connection') ||
        message.includes('timeout') ||
        message.includes('refused') ||
        message.includes('websocket') ||
        message.includes('websocket_stomp_error') ||
        error.code === 'NETWORK_ERROR') {
      return 'network';
    }
    
    // Server errors (HTTP 4xx, 5xx)
    if (error.status >= 400) {
      return 'server';
    }
    
    return 'unknown';
  }, []);

  // Enhanced retry decision logic with circuit breaker
  const shouldRetry = useCallback((error: WebSocketError, currentRetryState: RetryState): boolean => {
    // Circuit breaker: stop retrying after too many consecutive failures
    if (currentRetryState.consecutiveAuthFailures >= defaultRetryConfig.authRetryConfig.maxRetries) {
      console.log('[WebSocket] Circuit breaker: too many consecutive auth failures');
      return false;
    }
    
    if (currentRetryState.consecutiveNetworkFailures >= 10) {
      console.log('[WebSocket] Circuit breaker: too many consecutive network failures');
      return false;
    }
    
    // Error-specific retry logic
    switch (error.type) {
      case 'auth':
        return currentRetryState.attempt < defaultRetryConfig.authRetryConfig.maxRetries;
      case 'network':
        return currentRetryState.attempt < defaultRetryConfig.networkRetryConfig.maxRetries;
      case 'server':
        // Don't retry server errors (4xx, 5xx)
        console.log('[WebSocket] Not retrying server error');
        return false;
      default:
        return currentRetryState.attempt < defaultRetryConfig.maxRetries;
    }
  }, []);

  // Enhanced retry delay calculation with error-type-specific configuration
  const calculateRetryDelay = useCallback((
    attempt: number, 
    errorType: WebSocketError['type']
  ): number => {
    const config = errorType === 'auth' 
      ? defaultRetryConfig.authRetryConfig
      : errorType === 'network'
      ? defaultRetryConfig.networkRetryConfig
      : defaultRetryConfig;
      
    const baseDelay = config.baseDelay;
    const exponentialDelay = Math.min(
      baseDelay * Math.pow(defaultRetryConfig.backoffMultiplier, attempt), 
      defaultRetryConfig.maxDelay
    );
    
    const jitter = exponentialDelay * (defaultRetryConfig.jitterPercent / 100) * Math.random();
    return exponentialDelay + (Math.random() > 0.5 ? jitter : -jitter);
  }, []);

  // Create WebSocket error
  const createError = useCallback((
    type: WebSocketError['type'],
    message: string,
    isRecoverable: boolean = true
  ): WebSocketError => ({
    type,
    message,
    timestamp: Date.now(),
    isRecoverable,
  }), []);

  // Clear error state and reset retry state
  const clearError = useCallback(() => {
    setLastError(null);
    setRetryState(initialRetryState);
  }, []);

  // Reset retry state on successful connection
  const resetRetryState = useCallback(() => {
    setRetryState({
      attempt: 0,
      maxRetries: defaultRetryConfig.maxRetries,
      lastError: null,
      errorType: 'unknown',
      nextRetryAt: null,
      isRetryable: true,
      consecutiveAuthFailures: 0,
      consecutiveNetworkFailures: 0,
    });
  }, []);



  // Handle subscription when connection is established
  const processSubscriptions = useCallback(() => {
    const subscriptions = subscriptionsRef.current;
    const pendingTopics = Array.from(pendingSubscriptionsRef.current);
    
    console.log(`[WebSocket] Processing ${pendingTopics.length} pending subscriptions:`, pendingTopics);
    
    pendingTopics.forEach(topic => {
      const subInfo = subscriptions.get(topic);
      if (subInfo && !subInfo.isActive) {
        console.log(`[WebSocket] Processing pending subscription: ${topic}`);
        const result = webSocketService.subscribe(topic, subInfo.callback);
        
        if (result.success && result.subscription) {
          // Update the subscription info with the unsubscribe function
          subscriptions.set(topic, {
            ...subInfo,
            unsubscribe: () => {
              result.subscription!.unsubscribe();
              console.log(`[WebSocket] Unsubscribed from: ${topic}`);
            },
            isActive: true
          });
          pendingSubscriptionsRef.current.delete(topic);
          console.log(`[WebSocket] Successfully subscribed to: ${topic}`);
        } else {
          console.error(`[WebSocket] Failed to subscribe to ${topic}: ${result.error}`);
          setLastError(createError('server', result.error || `Failed to subscribe to ${topic}`, true));
        }
      }
    });
  }, [createError]);

  // Centralized retry orchestration
  const scheduleRetry = useCallback((error: WebSocketError) => {
    setRetryState(prevState => {
      const newState = {
        ...prevState,
        attempt: prevState.attempt + 1,
        lastError: error,
        errorType: error.type,
        consecutiveAuthFailures: error.type === 'auth' ? prevState.consecutiveAuthFailures + 1 : prevState.consecutiveAuthFailures,
        consecutiveNetworkFailures: error.type === 'network' ? prevState.consecutiveNetworkFailures + 1 : prevState.consecutiveNetworkFailures,
      };
      
      if (!shouldRetry(error, newState)) {
        newState.isRetryable = false;
        newState.nextRetryAt = null;
        setConnectionStatus('error');
        console.log(`[WebSocket] Retry exhausted for ${error.type} error after ${newState.attempt} attempts`);
        return newState;
      }
      
      const delay = calculateRetryDelay(newState.attempt, error.type);
      newState.nextRetryAt = Date.now() + delay;
      
      console.log(`[WebSocket] Scheduling retry ${newState.attempt}/${newState.maxRetries} in ${Math.round(delay / 1000)}s for ${error.type} error`);
      
      setConnectionStatus('reconnecting');
      
      // Clear any existing retry timeout
      if (retryTimeoutRef.current) {
        clearTimeout(retryTimeoutRef.current);
      }
      
      retryTimeoutRef.current = setTimeout(() => {
        if (error.type === 'auth') {
          // Handle auth retry inline to avoid circular dependency
          console.log('[WebSocket] Attempting auth retry...');
          refreshToken().then((newToken: string | undefined) => {
            if (newToken) {
              console.log('[WebSocket] Token refresh successful, attempting connection...');
              setRetryState(prev => ({
                ...prev,
                consecutiveAuthFailures: 0,
                attempt: 0,
              }));
              establishConnection();
            } else {
              const authError = createError('auth', 'Token refresh returned null', false);
              scheduleRetry(authError);
            }
          }).catch((refreshError: any) => {
            console.error('[WebSocket] Token refresh failed:', refreshError);
            const authError = createError('auth', 'Token refresh failed', false);
            scheduleRetry(authError);
          });
        } else {
          establishConnection();
        }
      }, delay);
      
      return newState;
    });
  }, [shouldRetry, calculateRetryDelay, refreshToken, createError]);

  // Main connection establishment function with centralized error handling
  const establishConnection = useCallback(async () => {
    if (isConnectingRef.current || !isAuthenticated || !tokens?.accessToken) {
      console.log('[WebSocket] Connection prerequisites not met:', {
        isConnecting: isConnectingRef.current,
        isAuthenticated,
        hasAccessToken: !!tokens?.accessToken
      });
      return;
    }

    // Validate token before attempting connection
    if (!isTokenValid(tokens.accessToken)) {
      console.log('[WebSocket] Access token is invalid or expired, skipping connection');
      const authError = createError('auth', 'Access token is invalid or expired', true);
      setLastError(authError);
      scheduleRetry(authError);
      return;
    }

    // Check if retries are exhausted
    if (!retryState.isRetryable) {
      console.log('[WebSocket] Retries exhausted, not attempting connection');
      return;
    }

    isConnectingRef.current = true;
    setConnectionStatus('connecting');
    clearError();

    try {
      console.log(`[WebSocket] Establishing connection (attempt ${retryState.attempt + 1})`);
      
      // Use Promise-based connect API
      await webSocketService.connect(tokens.accessToken);
      
      console.log('[WebSocket] Connection established successfully');
      setConnectionStatus('connected');
      
      // Reset retry state on successful connection
      resetRetryState();
      isConnectingRef.current = false;
      
      // Process any pending subscriptions with a small delay to ensure connection is fully ready
      setTimeout(() => {
        processSubscriptions();
        // Process any queued messages after subscriptions are ready
        setTimeout(() => {
          console.log('[WebSocket] Checking for queued messages to process...');
          const queueSize = messageQueueRef.current.getTotalSize();
          if (queueSize > 0) {
            console.log(`[WebSocket] Found ${queueSize} queued messages, processing...`);
            processMessageQueue();
          } else {
            console.log('[WebSocket] No queued messages to process');
          }
        }, 50);
      }, 100);

    } catch (error: any) {
      console.error('[WebSocket] Connection error:', error);
      isConnectingRef.current = false;
      
      // Classify error type
      const errorType = classifyError(error);
      const wsError = createError(errorType, error.message || 'Connection failed', errorType !== 'server');
      
      setLastError(wsError);
      
      // Use centralized retry logic
      scheduleRetry(wsError);
    }
  }, [
    isAuthenticated, 
    tokens?.accessToken, 
    retryState.isRetryable, 
    retryState.attempt,
    scheduleRetry,
    createError,
    clearError,
    processSubscriptions,
    resetRetryState,
    classifyError,
    isTokenValid
  ]);

  // Manual reconnect function
  const reconnect = useCallback(() => {
    console.log('[WebSocket] Manual reconnect requested');
    setRetryState(initialRetryState);
    setConnectionStatus('disconnected');
    clearError();
    
    // Clear any pending timeouts
    if (retryTimeoutRef.current) {
      clearTimeout(retryTimeoutRef.current);
      retryTimeoutRef.current = null;
    }
    if (connectionCheckTimeoutRef.current) {
      clearTimeout(connectionCheckTimeoutRef.current);
      connectionCheckTimeoutRef.current = null;
    }
    
    isConnectingRef.current = false;
    establishConnection();
  }, [establishConnection, clearError]);

  // Message sending with queuing
  const sendMessage = useCallback((
    destination: string,
    body: any,
    options: Partial<Pick<QueuedMessage, 'priority' | 'ttl' | 'persistent' | 'deliveryMode' | 'maxRetries'>> = {}
  ): Promise<string> => {
    const messageOptions = {
      priority: MessagePriority.NORMAL,
      ttl: defaultQueueConfig.defaultTTL,
      persistent: false,
      deliveryMode: DeliveryMode.AT_LEAST_ONCE,
      maxRetries: defaultQueueConfig.maxRetries,
      ...options,
    };
    
    const messageId = messageQueueRef.current.enqueue({
      destination,
      body,
      ...messageOptions,
    });
    
    // Update queue stats
    setQueueStats(prev => ({
      ...prev,
      totalMessages: prev.totalMessages + 1,
      pendingMessages: prev.pendingMessages + 1,
    }));
    
    console.log(`[WebSocket] Message ${messageId} enqueued for ${destination}`);
    
    // Process queue if connected and online
    const wsConnected = webSocketService.isConnected();
    if (wsConnected && navigator.onLine) {
      processMessageQueue();
    }
    
    return Promise.resolve(messageId);
  }, []);

  // Process message queue when connection is available
  const processMessageQueue = useCallback(async () => {
    const wsConnected = webSocketService.isConnected();
    const isOnline = navigator.onLine;
    
    if (isProcessingQueueRef.current || !wsConnected || !isOnline) {
      console.log(`[WebSocket] Skipping queue processing - wsConnected: ${wsConnected}, online: ${isOnline}, processing: ${isProcessingQueueRef.current}`);
      return;
    }
    
    isProcessingQueueRef.current = true;
    console.log('[WebSocket] Processing message queue...');
    
    const batchSize = defaultQueueConfig.batchSize;
    let processedCount = 0;
    
    while (processedCount < batchSize) {
      const message = messageQueueRef.current.dequeue();
      if (!message) break;
      
      // Check if message has expired
      const now = Date.now();
      if (now - message.timestamp > message.ttl) {
        console.log(`[WebSocket] Message ${message.id} expired, discarding`);
        setQueueStats(prev => ({
          ...prev,
          pendingMessages: prev.pendingMessages - 1,
          failedMessages: prev.failedMessages + 1,
        }));
        processedCount++;
        continue;
      }
      
      try {
        // Double-check connection before sending
        if (!webSocketService.isConnected() || !navigator.onLine) {
          console.log(`[WebSocket] Connection lost during processing, re-queuing message ${message.id}`);
          messageQueueRef.current.requeueForRetry(message);
          break;
        }
        
        // Attempt to send message
        await webSocketService.send(message.destination, message.body);
        
        console.log(`[WebSocket] Message ${message.id} sent successfully`);
        setQueueStats(prev => ({
          ...prev,
          pendingMessages: prev.pendingMessages - 1,
          deliveredMessages: prev.deliveredMessages + 1,
        }));
        
        processedCount++;
        
      } catch (error) {
        console.error(`[WebSocket] Failed to send message ${message.id}:`, error);
        
        // Retry logic
        const requeued = messageQueueRef.current.requeueForRetry(message);
        
        if (requeued) {
          console.log(`[WebSocket] Message ${message.id} re-queued for retry (${message.retryCount}/${message.maxRetries})`);
        } else {
          console.error(`[WebSocket] Message ${message.id} failed after ${message.maxRetries} retries`);
          setQueueStats(prev => ({
            ...prev,
            pendingMessages: prev.pendingMessages - 1,
            failedMessages: prev.failedMessages + 1,
          }));
        }
        
        break; // Stop processing on error to avoid overwhelming the connection
      }
    }
    
    isProcessingQueueRef.current = false;
    
    // Schedule next batch if there are more messages
    if (messageQueueRef.current.getTotalSize() > 0) {
      queueProcessingTimeoutRef.current = setTimeout(() => {
        processMessageQueue();
      }, defaultQueueConfig.batchDelay);
    }
  }, []);

  // Queue management functions
  const clearQueue = useCallback(() => {
    messageQueueRef.current.clear();
    setQueueStats({
      totalMessages: 0,
      pendingMessages: 0,
      failedMessages: 0,
      deliveredMessages: 0,
    });
    console.log('[WebSocket] Message queue cleared');
  }, []);

  const pauseQueue = useCallback(() => {
    messageQueueRef.current.pause();
    console.log('[WebSocket] Message queue paused');
  }, []);

  const resumeQueue = useCallback(() => {
    messageQueueRef.current.resume();
    const wsConnected = webSocketService.isConnected();
    if (wsConnected && navigator.onLine && messageQueueRef.current.getTotalSize() > 0) {
      processMessageQueue();
    }
    console.log('[WebSocket] Message queue resumed');
  }, [processMessageQueue]);

  const getQueueSize = useCallback(() => {
    return messageQueueRef.current.getTotalSize();
  }, []);

  // Subscription function with reference counting
  const subscribe = useCallback(function <T>(topic: string, callback: (message: T) => void): (() => void) {
    console.log(`[WebSocket] Subscribing to: ${topic}`);
    const subscriptions = subscriptionsRef.current;
    
    const existingSubscription = subscriptions.get(topic);
    if (existingSubscription) {
      // Clear any pending unsubscribe timeout for this topic
      const existingTimeout = subscriptionTimeoutsRef.current.get(topic);
      if (existingTimeout) {
        clearTimeout(existingTimeout);
        subscriptionTimeoutsRef.current.delete(topic);
        console.log(`[WebSocket] Cancelled pending unsubscribe for ${topic}`);
      }
      
      // Increment reference count for existing subscription
      existingSubscription.refCount += 1;
      console.log(`[WebSocket] Incremented ref count for ${topic}: ${existingSubscription.refCount}`);
      
      // Return unsubscribe function that decrements ref count
      return () => {
        const subscription = subscriptions.get(topic);
        if (subscription) {
          subscription.refCount -= 1;
          console.log(`[WebSocket] Decremented ref count for ${topic}: ${subscription.refCount}`);
          
          if (subscription.refCount <= 0) {
            // Actually unsubscribe when no more references
            if (subscription.unsubscribe) {
              subscription.unsubscribe();
            }
            subscriptions.delete(topic);
            pendingSubscriptionsRef.current.delete(topic);
            console.log(`[WebSocket] Fully unsubscribed from: ${topic}`);
          }
        }
      };
    }

    // Create new subscription info
    const subscriptionInfo: SubscriptionInfo = {
      callback: callback as (message: any) => void,
      unsubscribe: () => {}, // Will be set when actual subscription is created
      refCount: 1,
      isActive: false, // Will be set to true when actual subscription is created
    };
    
    subscriptions.set(topic, subscriptionInfo);

    if (isConnected && webSocketService.isConnected()) {
      // Subscribe immediately if connected
      const result = webSocketService.subscribe(topic, subscriptionInfo.callback);
      if (result.success && result.subscription) {
        subscriptionInfo.unsubscribe = () => {
          result.subscription!.unsubscribe();
          console.log(`[WebSocket] Unsubscribed from: ${topic}`);
        };
        subscriptionInfo.isActive = true;
        console.log(`[WebSocket] Immediately subscribed to: ${topic}`);
      } else {
        console.error(`[WebSocket] Failed to subscribe to ${topic}: ${result.error}`);
        setLastError(createError('server', result.error || `Failed to subscribe to ${topic}`, true));
      }
    } else {
      // Queue for later subscription when connected
      pendingSubscriptionsRef.current.add(topic);
      console.log(`[WebSocket] Queued subscription for: ${topic}`);
    }

    // Return unsubscribe function with debouncing
    return () => {
      const subscription = subscriptions.get(topic);
      if (subscription) {
        subscription.refCount -= 1;
        console.log(`[WebSocket] Decremented ref count for ${topic}: ${subscription.refCount}`);
        
        if (subscription.refCount <= 0) {
          // Clear any existing timeout for this topic
          const existingTimeout = subscriptionTimeoutsRef.current.get(topic);
          if (existingTimeout) {
            clearTimeout(existingTimeout);
          }
          
          // Debounce the actual unsubscription to prevent rapid subscribe/unsubscribe cycles
          const timeoutId = setTimeout(() => {
            const currentSub = subscriptions.get(topic);
            if (currentSub && currentSub.refCount <= 0) {
              if (currentSub.unsubscribe) {
                currentSub.unsubscribe();
              }
              subscriptions.delete(topic);
              pendingSubscriptionsRef.current.delete(topic);
              subscriptionTimeoutsRef.current.delete(topic);
              console.log(`[WebSocket] Fully unsubscribed from: ${topic}`);
            }
          }, 250); // 250ms debounce
          
          subscriptionTimeoutsRef.current.set(topic, timeoutId);
        }
      }
    };
  }, [isConnected, createError]);

  // Effect to handle auth state changes
  useEffect(() => {
    if (!isAuthenticated || !tokens?.accessToken) {
      console.log('[WebSocket] Auth state changed - disconnecting');
      setConnectionStatus('disconnected');
      setRetryState(initialRetryState);
      clearError();
      
      // Clear timeouts
      if (retryTimeoutRef.current) {
        clearTimeout(retryTimeoutRef.current);
        retryTimeoutRef.current = null;
      }
      if (connectionCheckTimeoutRef.current) {
        clearTimeout(connectionCheckTimeoutRef.current);
        connectionCheckTimeoutRef.current = null;
      }
      
      // Clear all subscriptions
      subscriptionsRef.current.clear();
      pendingSubscriptionsRef.current.clear();
      
      isConnectingRef.current = false;
      webSocketService.disconnect();
      return;
    }

    // Only establish connection if not already connected/connecting and not already connected via WebSocketService
    if (connectionStatus === 'disconnected' && !isConnectingRef.current && !webSocketService.isConnected()) {
      // Add delay to ensure auth state is fully stabilized and tokens are properly synced
      const connectionTimer = setTimeout(() => {
        // Double-check token validity before establishing connection
        if (isAuthenticated && tokens?.accessToken && isTokenValid(tokens.accessToken)) {
          console.log('[WebSocket] Auth state stable, establishing connection...');
          establishConnection();
        } else {
          console.log('[WebSocket] Auth state not ready for connection:', {
            isAuthenticated,
            hasToken: !!tokens?.accessToken,
            isTokenValid: tokens?.accessToken ? isTokenValid(tokens.accessToken) : false
          });
        }
      }, 1000); // Increased delay to allow auth state to stabilize

      return () => {
        clearTimeout(connectionTimer);
      };
    }
  }, [isAuthenticated, tokens?.accessToken, connectionStatus, establishConnection, clearError, isTokenValid]);

  // Handle browser visibility changes (reconnect when tab becomes visible)
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (!document.hidden && isAuthenticated && connectionStatus === 'disconnected') {
        console.log('[WebSocket] Tab became visible, attempting reconnection');
        reconnect();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [isAuthenticated, connectionStatus, reconnect]);

  // Handle online/offline events
  useEffect(() => {
    const handleOnline = () => {
      console.log('[WebSocket] Network came online');
      if (isAuthenticated && (connectionStatus === 'error' || connectionStatus === 'disconnected')) {
        console.log('[WebSocket] Attempting reconnection');
        reconnect();
      } else if (connectionStatus === 'connected' && webSocketService.isConnected()) {
        // If already connected, just process any queued messages
        console.log('[WebSocket] Already connected, processing queued messages');
        if (messageQueueRef.current.getTotalSize() > 0) {
          processMessageQueue();
        }
      }
    };

    const handleOffline = () => {
      console.log('[WebSocket] Network went offline');
      setConnectionStatus('disconnected');
      setLastError(createError('network', 'Network connection lost', true));
      
      // Force disconnect WebSocket when network goes offline
      webSocketService.disconnect();
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, [isAuthenticated, connectionStatus, reconnect, createError]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (retryTimeoutRef.current) {
        clearTimeout(retryTimeoutRef.current);
      }
      if (connectionCheckTimeoutRef.current) {
        clearTimeout(connectionCheckTimeoutRef.current);
      }
      if (queueProcessingTimeoutRef.current) {
        clearTimeout(queueProcessingTimeoutRef.current);
      }
      
      // Clear all subscription timeouts
      subscriptionTimeoutsRef.current.forEach((timeout) => {
        clearTimeout(timeout);
      });
      subscriptionTimeoutsRef.current.clear();
      
      // Clean up all subscriptions immediately on unmount
      const subscriptions = subscriptionsRef.current;
      subscriptions.forEach((subInfo) => {
        if (subInfo.unsubscribe) {
          subInfo.unsubscribe();
        }
      });
      subscriptions.clear();
      pendingSubscriptionsRef.current.clear();
      
      // Note: disconnect() is now async, but we can't await in cleanup
      webSocketService.disconnect();
    };
  }, []);

  // Memoize context value to prevent unnecessary re-renders
  const contextValue = useMemo(() => ({
    isConnected,
    connectionStatus,
    lastError,
    retryAttempt,
    maxRetries,
    retryState,
    retryConfig: defaultRetryConfig,
    subscribe,
    sendMessage,
    queueStats,
    clearQueue,
    pauseQueue,
    resumeQueue,
    getQueueSize,
    reconnect,
    clearError,
  }), [
    isConnected,
    connectionStatus,
    lastError,
    retryAttempt,
    maxRetries,
    retryState,
    subscribe,
    sendMessage,
    queueStats,
    clearQueue,
    pauseQueue,
    resumeQueue,
    getQueueSize,
    reconnect,
    clearError,
  ]);

  return (
    <WebSocketContext.Provider value={contextValue}>
      {children}
    </WebSocketContext.Provider>
  );
};

export default WebSocketProvider; 