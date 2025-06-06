import { useContext } from 'react';
import { WebSocketContext } from '../contexts/WebSocketProvider';
import type { WebSocketContextValue } from '../contexts/types/websocket';

export function useWebSocket(): WebSocketContextValue {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error('useWebSocket must be used within a WebSocketProvider');
  }
  return context;
} 