import React, { useState } from 'react';
import { useWebSocket } from '@/hooks/useWebSocket';
import { MessagePriority, DeliveryMode } from '@/contexts/types/websocket';

/**
 * Demo component showcasing the new WebSocket message queuing system
 * This demonstrates:
 * - Priority-based message queuing
 * - TTL and persistence options
 * - Queue statistics and management
 * - Reliable message delivery
 */
export const MessageQueueDemo: React.FC = () => {
  const {
    isConnected,
    connectionStatus,
    sendMessage,
    queueStats,
    clearQueue,
    pauseQueue,
    resumeQueue,
    getQueueSize,
  } = useWebSocket();

  const [messageBody, setMessageBody] = useState('Hello from queue!');
  const [destination, setDestination] = useState('/app/party/update');
  const [selectedPriority, setSelectedPriority] = useState<MessagePriority>(MessagePriority.NORMAL);
  const [isPersistent, setIsPersistent] = useState(false);
  const [customTTL, setCustomTTL] = useState(30000);

  const handleSendMessage = async () => {
    try {
      const messageId = await sendMessage(destination, {
        message: messageBody,
        timestamp: new Date().toISOString(),
        demo: true,
      }, {
        priority: selectedPriority,
        persistent: isPersistent,
        ttl: customTTL,
        deliveryMode: DeliveryMode.AT_LEAST_ONCE,
        maxRetries: selectedPriority === MessagePriority.CRITICAL ? 5 : 3,
      });

      console.log(`Message ${messageId} queued successfully`);
    } catch (error) {
      console.error('Failed to queue message:', error);
    }
  };

  const handleSendBatch = async () => {
    const priorities = [
      MessagePriority.LOW,
      MessagePriority.NORMAL,
      MessagePriority.HIGH,
      MessagePriority.CRITICAL,
    ];

    for (let i = 0; i < 4; i++) {
      await sendMessage(`/app/test/batch/${i}`, {
        batchId: Date.now(),
        messageIndex: i,
        priority: priorities[i],
        timestamp: new Date().toISOString(),
      }, {
        priority: priorities[i],
        persistent: i >= 2, // Make HIGH and CRITICAL persistent
        ttl: 60000,
      });
    }

    console.log('Batch of 4 messages queued with different priorities');
  };

  const getPriorityLabel = (priority: MessagePriority): string => {
    switch (priority) {
      case MessagePriority.CRITICAL: return 'Critical';
      case MessagePriority.HIGH: return 'High';
      case MessagePriority.NORMAL: return 'Normal';
      case MessagePriority.LOW: return 'Low';
      default: return 'Unknown';
    }
  };

  const getStatusColor = (status: string): string => {
    switch (status) {
      case 'connected': return 'text-green-600';
      case 'connecting': return 'text-yellow-600';
      case 'reconnecting': return 'text-orange-600';
      case 'error': return 'text-red-600';
      default: return 'text-gray-600';
    }
  };

  return (
    <div className="p-6 max-w-4xl mx-auto space-y-6">
      <div className="bg-white rounded-lg shadow-md p-6">
        <h2 className="text-2xl font-bold mb-4">WebSocket Message Queue Demo</h2>
        
        {/* Connection Status */}
        <div className="mb-6">
          <h3 className="text-lg font-semibold mb-2">Connection Status</h3>
          <div className="flex items-center space-x-4">
            <span className={`font-medium ${getStatusColor(connectionStatus)}`}>
              {connectionStatus.toUpperCase()}
            </span>
            {isConnected && <span className="text-green-600">✓ Ready to send messages</span>}
          </div>
        </div>

        {/* Queue Statistics */}
        <div className="mb-6">
          <h3 className="text-lg font-semibold mb-2">Queue Statistics</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="bg-blue-50 p-3 rounded">
              <div className="text-2xl font-bold text-blue-600">{queueStats.totalMessages}</div>
              <div className="text-sm text-blue-800">Total Messages</div>
            </div>
            <div className="bg-yellow-50 p-3 rounded">
              <div className="text-2xl font-bold text-yellow-600">{queueStats.pendingMessages}</div>
              <div className="text-sm text-yellow-800">Pending</div>
            </div>
            <div className="bg-green-50 p-3 rounded">
              <div className="text-2xl font-bold text-green-600">{queueStats.deliveredMessages}</div>
              <div className="text-sm text-green-800">Delivered</div>
            </div>
            <div className="bg-red-50 p-3 rounded">
              <div className="text-2xl font-bold text-red-600">{queueStats.failedMessages}</div>
              <div className="text-sm text-red-800">Failed</div>
            </div>
          </div>
          <div className="mt-2 text-sm text-gray-600">
            Current queue size: {getQueueSize()} messages
          </div>
        </div>

        {/* Message Configuration */}
        <div className="mb-6">
          <h3 className="text-lg font-semibold mb-2">Send Message</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Destination</label>
              <input
                type="text"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="/app/party/update"
              />
            </div>
            
            <div>
              <label className="block text-sm font-medium mb-1">Message Body</label>
              <input
                type="text"
                value={messageBody}
                onChange={(e) => setMessageBody(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Hello from queue!"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Priority</label>
                <select
                  value={selectedPriority}
                  onChange={(e) => setSelectedPriority(Number(e.target.value) as MessagePriority)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value={MessagePriority.CRITICAL}>Critical</option>
                  <option value={MessagePriority.HIGH}>High</option>
                  <option value={MessagePriority.NORMAL}>Normal</option>
                  <option value={MessagePriority.LOW}>Low</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">TTL (seconds)</label>
                <input
                  type="number"
                  value={customTTL / 1000}
                  onChange={(e) => setCustomTTL(Number(e.target.value) * 1000)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  min="1"
                  max="300"
                />
              </div>

              <div className="flex items-center">
                <label className="flex items-center">
                  <input
                    type="checkbox"
                    checked={isPersistent}
                    onChange={(e) => setIsPersistent(e.target.checked)}
                    className="mr-2"
                  />
                  <span className="text-sm font-medium">Persistent</span>
                </label>
              </div>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-wrap gap-3">
          <button
            onClick={handleSendMessage}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            Send Message ({getPriorityLabel(selectedPriority)})
          </button>
          
          <button
            onClick={handleSendBatch}
            className="px-4 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500"
          >
            Send Priority Batch
          </button>
          
          <button
            onClick={pauseQueue}
            className="px-4 py-2 bg-yellow-600 text-white rounded-md hover:bg-yellow-700 focus:outline-none focus:ring-2 focus:ring-yellow-500"
          >
            Pause Queue
          </button>
          
          <button
            onClick={resumeQueue}
            className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500"
          >
            Resume Queue
          </button>
          
          <button
            onClick={clearQueue}
            className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500"
          >
            Clear Queue
          </button>
          
          <button
            onClick={() => window.dispatchEvent(new Event('online'))}
            className="px-4 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            Force Reconnect
          </button>
        </div>

        {/* Usage Instructions */}
        <div className="mt-6 p-4 bg-gray-50 rounded-md">
          <h4 className="font-semibold mb-2">How to Test:</h4>
          <ol className="list-decimal list-inside space-y-1 text-sm text-gray-700">
            <li>Disconnect your internet to see messages queue up</li>
            <li>Send messages with different priorities while offline</li>
            <li>Reconnect to see priority-based delivery (Critical → High → Normal → Low)</li>
            <li>Use persistent messages to survive page refreshes</li>
            <li>Monitor the queue statistics for delivery tracking</li>
          </ol>
        </div>
      </div>
    </div>
  );
};

export default MessageQueueDemo; 