import {
  createContext,
  useState,
  useEffect,
  ReactNode,
  useCallback,
  useMemo,
  useRef,
} from 'react';
import webSocketService, { ConnectionState, SubscriptionResult } from '../config/WebSocketService';
import { useAuth } from '@/contexts/auth/useAuth';
import {
  WebSocketContextValue,
  WebSocketConnectionStatus,
  WebSocketError,
  SubscriptionInfo,
} from './types/websocket';

interface WebSocketProviderProps {
  children: ReactNode;
}

export const WebSocketContext = createContext<WebSocketContextValue | undefined>(undefined);

export const WebSocketProvider = ({ children }: WebSocketProviderProps) => {
  const { isAuthenticated, tokens, refreshToken } = useAuth();
  
  // Connection state
  const [connectionStatus, setConnectionStatus] = useState<WebSocketConnectionStatus>('disconnected');
  const [lastError, setLastError] = useState<WebSocketError | null>(null);
  const [retryAttempt, setRetryAttempt] = useState(0);
  const maxRetries = 5;
  
  // Subscription management
  const subscriptionsRef = useRef<Map<string, SubscriptionInfo>>(new Map());
  const pendingSubscriptionsRef = useRef<Set<string>>(new Set());
  const isConnectingRef = useRef(false);
  const retryTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const connectionCheckTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const subscriptionTimeoutsRef = useRef<Map<string, NodeJS.Timeout>>(new Map());
  
  // Derived state
  const isConnected = connectionStatus === 'connected';
  
  // Debug logging for connection status changes
  useEffect(() => {
    console.log(`[WebSocket] Connection status changed to: ${connectionStatus}, isConnected: ${isConnected}`);
  }, [connectionStatus, isConnected]);

  // Exponential backoff with jitter calculation
  const calculateRetryDelay = useCallback((attempt: number): number => {
    const baseDelay = 1000; // 1 second
    const maxDelay = 30000; // 30 seconds
    const exponentialDelay = Math.min(baseDelay * Math.pow(2, attempt), maxDelay);
    const jitter = exponentialDelay * 0.25 * Math.random(); // ±25% jitter
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

  // Clear error state
  const clearError = useCallback(() => {
    setLastError(null);
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

  // Main connection establishment function
  const establishConnection = useCallback(async () => {
    if (isConnectingRef.current || !isAuthenticated || !tokens?.accessToken) {
      return;
    }

    // Don't attempt connection if we're at max retries
    if (retryAttempt >= maxRetries) {
      console.log('[WebSocket] Max retries reached, not attempting connection');
      return;
    }

    isConnectingRef.current = true;
    setConnectionStatus('connecting');
    clearError();

    try {
      console.log(`[WebSocket] Establishing connection (attempt ${retryAttempt + 1}/${maxRetries})`);
      
      // Use new Promise-based connect API
      await webSocketService.connect(tokens.accessToken);
      
      console.log('[WebSocket] Connection established, updating status to connected');
      setConnectionStatus('connected');
      setRetryAttempt(0);
      isConnectingRef.current = false;
      
      // Process any pending subscriptions with a small delay to ensure connection is fully ready
      setTimeout(() => {
        processSubscriptions();
      }, 100);

    } catch (error: any) {
      console.error('[WebSocket] Connection error:', error);
      isConnectingRef.current = false;
      
      const isAuthError = error.message?.includes('token') || error.message?.includes('auth') || error.message?.includes('Invalid JWT');
      
      if (isAuthError) {
        console.log('[WebSocket] Authentication error detected, attempting token refresh...');
        try {
          const newToken = await refreshToken();
          if (newToken) {
            console.log('[WebSocket] Token refresh successful, retrying connection...');
            // Reset retry count for fresh token attempt
            setRetryAttempt(0);
            isConnectingRef.current = false;
            // Retry immediately with new token
            setTimeout(() => establishConnection(), 100);
            return;
          } else {
            console.error('[WebSocket] Token refresh failed, marking as auth error');
            setConnectionStatus('error');
            setLastError(createError('auth', 'Authentication failed and token refresh unsuccessful', false));
            return;
          }
        } catch (refreshError) {
          console.error('[WebSocket] Token refresh failed:', refreshError);
          setConnectionStatus('error');
          setLastError(createError('auth', 'Authentication failed and token refresh unsuccessful', false));
          return;
        }
      }
      
      // Handle non-auth errors
      setConnectionStatus('error');
      setLastError(createError('network', error.message || 'Connection failed', true));
      
      // Schedule retry if within limits
      if (retryAttempt < maxRetries) {
        const delay = calculateRetryDelay(retryAttempt);
        console.log(`[WebSocket] Retrying connection in ${Math.round(delay / 1000)}s (attempt ${retryAttempt + 1}/${maxRetries})`);
        
        setConnectionStatus('reconnecting');
        setRetryAttempt(prev => prev + 1);
        
        retryTimeoutRef.current = setTimeout(() => {
          establishConnection();
        }, delay);
      }
    }
  }, [isAuthenticated, tokens?.accessToken, retryAttempt, maxRetries, calculateRetryDelay, createError, clearError, processSubscriptions, refreshToken]);

  // Manual reconnect function
  const reconnect = useCallback(() => {
    console.log('[WebSocket] Manual reconnect requested');
    setRetryAttempt(0);
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
      setRetryAttempt(0);
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
      // Small delay to ensure token is properly set
      const connectionTimer = setTimeout(() => {
        establishConnection();
      }, 300); // Reduced delay

      return () => {
        clearTimeout(connectionTimer);
      };
    }
  }, [isAuthenticated, tokens?.accessToken, connectionStatus, establishConnection, clearError]);

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
      if (isAuthenticated && (connectionStatus === 'error' || connectionStatus === 'disconnected')) {
        console.log('[WebSocket] Network came online, attempting reconnection');
        reconnect();
      }
    };

    const handleOffline = () => {
      console.log('[WebSocket] Network went offline');
      setConnectionStatus('disconnected');
      setLastError(createError('network', 'Network connection lost', true));
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
    subscribe,
    reconnect,
    clearError,
  }), [
    isConnected,
    connectionStatus,
    lastError,
    retryAttempt,
    maxRetries,
    subscribe,
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