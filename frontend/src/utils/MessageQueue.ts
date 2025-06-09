import {
  QueuedMessage,
  MessagePriority,
  MessageQueueConfig,
  DeliveryMode,
} from '@/contexts/types/websocket';

const QUEUE_STORAGE_KEY = 'websocket_message_queue';

export const defaultQueueConfig: MessageQueueConfig = {
  maxQueueSize: 1000,
  defaultTTL: 30000,  // 30 seconds
  maxRetries: 3,
  persistenceEnabled: true,
  batchSize: 10,
  batchDelay: 100,
  
  priorityConfig: {
    [MessagePriority.CRITICAL]: { ttl: 60000, maxRetries: 5 },
    [MessagePriority.HIGH]: { ttl: 45000, maxRetries: 4 },
    [MessagePriority.NORMAL]: { ttl: 30000, maxRetries: 3 },
    [MessagePriority.LOW]: { ttl: 15000, maxRetries: 2 },
  },
};

export class MessageQueue {
  private queues: Map<MessagePriority, QueuedMessage[]> = new Map();
  private processing: boolean = false;
  private config: MessageQueueConfig;
  private paused: boolean = false;
  
  constructor(config: MessageQueueConfig = defaultQueueConfig) {
    this.config = config;
    
    // Initialize priority queues
    Object.values(MessagePriority).forEach(priority => {
      if (typeof priority === 'number') {
        this.queues.set(priority, []);
      }
    });
    
    console.log('[MessageQueue] Initialized with config:', config);
  }
  
  /**
   * Add a message to the queue with priority handling
   */
  enqueue(message: Omit<QueuedMessage, 'id' | 'timestamp' | 'retryCount' | 'lastAttempt'>): string {
    const queuedMessage: QueuedMessage = {
      ...message,
      id: crypto.randomUUID(),
      timestamp: Date.now(),
      retryCount: 0,
      lastAttempt: undefined,
    };
    
    console.log(`[MessageQueue] Enqueuing message ${queuedMessage.id} with priority ${queuedMessage.priority}`);
    
    // Check queue size limits
    if (this.getTotalSize() >= this.config.maxQueueSize) {
      console.warn('[MessageQueue] Queue size limit reached, evicting old messages');
      this.evictOldMessages();
    }
    
    const priorityQueue = this.queues.get(message.priority);
    if (priorityQueue) {
      priorityQueue.push(queuedMessage);
      this.sortQueue(message.priority);
    }
    
    // Persist if enabled and message is marked as persistent
    if (message.persistent && this.config.persistenceEnabled) {
      this.persistQueue();
    }
    
    console.log(`[MessageQueue] Message ${queuedMessage.id} enqueued successfully`);
    return queuedMessage.id;
  }
  
  /**
   * Remove and return the highest priority message
   */
  dequeue(): QueuedMessage | null {
    if (this.paused) {
      return null;
    }
    
    // Process by priority (CRITICAL first)
    for (const priority of [
      MessagePriority.CRITICAL,
      MessagePriority.HIGH,
      MessagePriority.NORMAL,
      MessagePriority.LOW
    ]) {
      const queue = this.queues.get(priority);
      if (queue && queue.length > 0) {
        // Check if first message is expired before dequeuing
        const message = queue[0];
        const now = Date.now();
        
        if (now - message.timestamp > message.ttl) {
          // Remove expired message
          queue.shift();
          console.log(`[MessageQueue] Message ${message.id} expired, discarding`);
          continue;
        }
        
        const dequeuedMessage = queue.shift();
        if (dequeuedMessage) {
          console.log(`[MessageQueue] Dequeued message ${dequeuedMessage.id} with priority ${priority}`);
          return dequeuedMessage;
        }
      }
    }
    
    return null;
  }
  
  /**
   * Re-queue a message for retry (used when send fails)
   */
  requeueForRetry(message: QueuedMessage): boolean {
    if (message.retryCount >= message.maxRetries) {
      console.error(`[MessageQueue] Message ${message.id} exceeded max retries (${message.maxRetries})`);
      return false;
    }
    
    message.retryCount++;
    message.lastAttempt = Date.now();
    
    const priorityQueue = this.queues.get(message.priority);
    if (priorityQueue) {
      priorityQueue.push(message);
      this.sortQueue(message.priority);
      console.log(`[MessageQueue] Message ${message.id} requeued for retry (${message.retryCount}/${message.maxRetries})`);
      return true;
    }
    
    return false;
  }
  
  /**
   * Sort queue by timestamp (FIFO within priority)
   */
  private sortQueue(priority: MessagePriority): void {
    const queue = this.queues.get(priority);
    if (queue) {
      queue.sort((a, b) => a.timestamp - b.timestamp);
    }
  }
  
  /**
   * Remove expired messages from all queues
   */
  private evictOldMessages(): void {
    const now = Date.now();
    let evictedCount = 0;
    
    this.queues.forEach((queue, priority) => {
      const initialSize = queue.length;
      // Remove expired messages
      const filteredQueue = queue.filter(msg => {
        const isExpired = now - msg.timestamp > msg.ttl;
        if (isExpired) evictedCount++;
        return !isExpired;
      });
      
      this.queues.set(priority, filteredQueue);
    });
    
    console.log(`[MessageQueue] Evicted ${evictedCount} expired messages`);
    
    // If still over limit, remove oldest messages from lowest priority queue
    while (this.getTotalSize() >= this.config.maxQueueSize) {
      let removed = false;
      
      // Remove from LOW priority first, then NORMAL, then HIGH (never remove CRITICAL)
      for (const priority of [MessagePriority.LOW, MessagePriority.NORMAL, MessagePriority.HIGH]) {
        const queue = this.queues.get(priority);
        if (queue && queue.length > 0) {
          const removedMessage = queue.shift();
          console.log(`[MessageQueue] Evicted message ${removedMessage?.id} due to queue size limit`);
          removed = true;
          break;
        }
      }
      
      if (!removed) break; // No more messages to remove
    }
  }
  
  /**
   * Get total number of messages across all priority queues
   */
  getTotalSize(): number {
    return Array.from(this.queues.values()).reduce((total, queue) => total + queue.length, 0);
  }
  
  /**
   * Get size of specific priority queue
   */
  getQueueSize(priority: MessagePriority): number {
    return this.queues.get(priority)?.length || 0;
  }
  
  /**
   * Clear all messages from the queue
   */
  clear(): void {
    this.queues.forEach(queue => queue.length = 0);
    console.log('[MessageQueue] All messages cleared');
    
    if (this.config.persistenceEnabled) {
      this.clearPersistedQueue();
    }
  }
  
  /**
   * Pause queue processing
   */
  pause(): void {
    this.paused = true;
    console.log('[MessageQueue] Queue processing paused');
  }
  
  /**
   * Resume queue processing
   */
  resume(): void {
    this.paused = false;
    console.log('[MessageQueue] Queue processing resumed');
  }
  
  /**
   * Check if queue is paused
   */
  isPaused(): boolean {
    return this.paused;
  }
  
  /**
   * Get queue statistics
   */
  getStatistics(): { totalMessages: number; messagesByPriority: Record<MessagePriority, number> } {
    const messagesByPriority: Record<MessagePriority, number> = {
      [MessagePriority.CRITICAL]: this.getQueueSize(MessagePriority.CRITICAL),
      [MessagePriority.HIGH]: this.getQueueSize(MessagePriority.HIGH),
      [MessagePriority.NORMAL]: this.getQueueSize(MessagePriority.NORMAL),
      [MessagePriority.LOW]: this.getQueueSize(MessagePriority.LOW),
    };
    
    return {
      totalMessages: this.getTotalSize(),
      messagesByPriority,
    };
  }
  
  /**
   * Persist queue to localStorage
   */
  private persistQueue(): void {
    if (!this.config.persistenceEnabled) return;
    
    try {
      const persistentMessages: Array<[MessagePriority, QueuedMessage[]]> = [];
      
      this.queues.forEach((queue, priority) => {
        const persistentQueueMessages = queue.filter(msg => msg.persistent);
        if (persistentQueueMessages.length > 0) {
          persistentMessages.push([priority, persistentQueueMessages]);
        }
      });
      
      if (persistentMessages.length > 0) {
        const queueData = {
          messages: persistentMessages,
          timestamp: Date.now(),
        };
        
        localStorage.setItem(QUEUE_STORAGE_KEY, JSON.stringify(queueData));
        console.log(`[MessageQueue] Persisted ${persistentMessages.length} priority queues`);
      }
    } catch (error) {
      console.error('[MessageQueue] Failed to persist queue:', error);
    }
  }
  
  /**
   * Restore queue from localStorage
   */
  restoreFromPersistence(): number {
    if (!this.config.persistenceEnabled) return 0;
    
    try {
      const stored = localStorage.getItem(QUEUE_STORAGE_KEY);
      if (!stored) return 0;
      
      const queueData = JSON.parse(stored);
      const now = Date.now();
      let restoredCount = 0;
      
      // Restore non-expired messages
      queueData.messages.forEach(([priority, messages]: [MessagePriority, QueuedMessage[]]) => {
        const validMessages = messages.filter(msg => {
          const isExpired = now - msg.timestamp > msg.ttl;
          if (!isExpired) restoredCount++;
          return !isExpired;
        });
        
        const currentQueue = this.queues.get(priority) || [];
        currentQueue.push(...validMessages);
        this.queues.set(priority, currentQueue);
        this.sortQueue(priority);
      });
      
      // Clear stored queue after restoration
      this.clearPersistedQueue();
      console.log(`[MessageQueue] Restored ${restoredCount} messages from persistence`);
      
      return restoredCount;
      
    } catch (error) {
      console.error('[MessageQueue] Failed to restore queue:', error);
      this.clearPersistedQueue();
      return 0;
    }
  }
  
  /**
   * Clear persisted queue data
   */
  private clearPersistedQueue(): void {
    try {
      localStorage.removeItem(QUEUE_STORAGE_KEY);
    } catch (error) {
      console.error('[MessageQueue] Failed to clear persisted queue:', error);
    }
  }
} 