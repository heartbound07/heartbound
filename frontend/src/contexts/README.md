# Unified WebSocket System

This document describes the consolidated WebSocket system that replaces the fragmented context providers with a single, unified solution.

## Overview

The unified WebSocket system consists of:
- **WebSocketProvider**: Single connection manager for all WebSocket subscriptions
- **Specialized hooks**: Domain-specific hooks for different subscription types
- **Backward-compatible providers**: Existing providers that now use the unified system internally

## Key Benefits

### Before (Fragmented System)
- 4 separate context providers each managing their own connections
- Race conditions during auth state changes
- Inconsistent retry strategies
- Performance issues from uncoordinated re-renders
- Multiple competing connection managers

### After (Unified System)
- ✅ Single WebSocket connection for all subscriptions
- ✅ Consistent error handling and retry logic
- ✅ Exponential backoff with jitter for all subscriptions
- ✅ Optimized re-render performance
- ✅ Centralized connection state management
- ✅ Backward compatibility with existing code

## Architecture

```
WebSocketProvider (Single connection + subscription management)
├── useWebSocket (Core hook)
├── usePartyUpdates (Party-specific logic)
├── usePairingUpdates (Pairing-specific logic)
├── useQueueUpdates (Queue-specific logic)
└── useQueueConfig (Queue config logic)
```

## Usage

### Option 1: Direct Hook Usage (Recommended for new code)

```typescript
import { useWebSocket } from '@/contexts/hooks/useWebSocket';
import { usePartyUpdates } from '@/contexts/hooks/usePartyUpdates';

function MyComponent() {
  // Use core WebSocket hook
  const { isConnected, connectionStatus } = useWebSocket();
  
  // Use specialized domain hook
  const { update, clearUpdate } = usePartyUpdates();
  
  return (
    <div>
      <div>Status: {connectionStatus}</div>
      {update && <div>Party Update: {update.eventType}</div>}
    </div>
  );
}
```

### Option 2: Generic Subscription (For custom topics)

```typescript
import { useWebSocket } from '@/contexts/hooks/useWebSocket';

function CustomComponent() {
  const { subscribe } = useWebSocket();
  const [customData, setCustomData] = useState(null);
  
  useEffect(() => {
    const unsubscribe = subscribe<MyMessageType>('/topic/custom', (message) => {
      setCustomData(message);
    });
    
    return unsubscribe; // Cleanup subscription
  }, [subscribe]);
  
  return <div>{/* render custom data */}</div>;
}
```

### Option 3: Existing Context API (Backward compatible)

```typescript
import { usePartyUpdates } from '@/contexts/PartyUpdates';

function ExistingComponent() {
  // Existing code continues to work unchanged
  const { update, clearUpdate } = usePartyUpdates();
  
  return <div>{/* existing component logic */}</div>;
}
```

## Connection Management

### Connection States
- `disconnected`: No connection
- `connecting`: Attempting to connect
- `connected`: Successfully connected
- `error`: Connection failed
- `reconnecting`: Retrying connection

### Automatic Reconnection
- Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (max)
- Jitter: ±25% randomization to prevent thundering herd
- Max retries: 5 attempts before stopping
- Manual retry: `reconnect()` function available

### Network Awareness
- Automatically reconnects when network comes online
- Reconnects when browser tab becomes visible after being hidden
- Handles browser online/offline events

## Error Handling

### Error Types
```typescript
interface WebSocketError {
  type: 'network' | 'auth' | 'server' | 'unknown';
  message: string;
  timestamp: number;
  isRecoverable: boolean;
}
```

### Error Recovery
- **Network errors**: Automatic retry with exponential backoff
- **Auth errors**: Forces re-authentication, no automatic retry
- **Server errors**: Automatic retry for recoverable errors
- **Unknown errors**: Treated as recoverable, automatic retry

## Subscription Management

### Reference Counting
The system uses reference counting to efficiently manage subscriptions:
- Multiple components can subscribe to the same topic
- Only one actual WebSocket subscription is created
- Subscription is removed when last component unsubscribes

### Lazy Subscription
- Subscriptions are only created when components actually mount
- Subscriptions are queued if WebSocket is not connected
- Queued subscriptions are processed when connection is established

## Performance Optimizations

### Prevented Re-render Storms
- Proper memoization with `useMemo` and `useCallback`
- Optimized dependency arrays
- Batched state updates

### Memory Management
- Automatic cleanup of subscriptions
- Timeout management
- Proper unsubscription in component unmount

## Security Features

### JWT Token Integration
- Automatic token refresh handling
- Secure token storage integration
- Connection reestablishment after token refresh

### Authentication State Synchronization
- Automatic disconnection when user logs out
- Reconnection when user logs in
- Token expiration handling

## Migration Guide

### For New Components
Use the specialized hooks directly:
```typescript
import { usePartyUpdates } from '@/contexts/hooks/usePartyUpdates';
```

### For Existing Components
No changes required - existing context API continues to work:
```typescript
import { usePartyUpdates } from '@/contexts/PartyUpdates'; // Still works
```

### For Custom Subscriptions
Use the core hook:
```typescript
import { useWebSocket } from '@/contexts/hooks/useWebSocket';
```

## Troubleshooting

### Connection Issues
1. Check browser console for WebSocket errors
2. Verify JWT token is valid
3. Check network connectivity
4. Look for authentication errors

### Subscription Issues
1. Ensure component is wrapped in `WebSocketProvider`
2. Check that user is authenticated for user-specific topics
3. Verify topic name matches backend configuration

### Performance Issues
1. Check for unnecessary re-renders in components
2. Ensure proper cleanup of subscriptions
3. Verify components are properly memoized

## Backend Integration

### Supported Topics
- `/topic/party` - LFG party updates (public)
- `/topic/queue` - Queue size updates (public)
- `/topic/queue/config` - Admin queue configuration (public)
- `/user/{userId}/topic/pairings` - User-specific matchmaking (private)

### Message Types
All message types are strongly typed in `frontend/src/contexts/types/websocket.ts`

## Testing

### Connection Status UI
The unified system provides consistent connection status that can be displayed in the UI:
```typescript
const { connectionStatus, isConnected, lastError } = useWebSocket();
```

### Manual Testing
- Use browser dev tools to simulate network issues
- Test offline/online scenarios
- Verify reconnection after token refresh
- Check subscription cleanup on component unmount

## Future Enhancements

### Planned Features
- Connection pooling for multiple WebSocket endpoints
- Message queuing during disconnection
- Subscription persistence across sessions
- Enhanced offline support

### Extensibility
The system is designed to be easily extended:
- Add new specialized hooks for new domains
- Extend error types and handling
- Add new connection strategies
- Implement additional retry policies 