import { Client, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { CollabMessage, ItemStatus } from '../types/collab';

export type ConnectionStatus = 'CONNECTED' | 'CONNECTING' | 'DISCONNECTED' | 'RECONNECTING';

export interface CollabSocketListeners {
  onMessage: (message: CollabMessage) => void;
  onStatusChange: (status: ConnectionStatus) => void;
}

export class CollabSocketService {
  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private listeners: CollabSocketListeners | null = null;
  private isExplicitlyDisconnected = false;

  constructor() {}

  public connect(roomId: string, username: string, userColor: string, listeners: CollabSocketListeners) {
    this.listeners = listeners;
    this.isExplicitlyDisconnected = false;

    this.listeners.onStatusChange('CONNECTING');

    // Setup STOMP client over SockJS fallback endpoint
    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws-collab'),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {
        // debug logging disabled
      },
      onConnect: () => {
        if (this.isExplicitlyDisconnected) {
          this.disconnect();
          return;
        }

        this.listeners?.onStatusChange('CONNECTED');
        this.subscribeToRoom(roomId, username, userColor);
      },
      onDisconnect: () => {
        if (!this.isExplicitlyDisconnected) {
          this.listeners?.onStatusChange('DISCONNECTED');
        }
      },
      onStompError: (frame) => {
        console.error('Broker error:', frame.headers['message'], frame.body);
        this.listeners?.onStatusChange('DISCONNECTED');
      },
      onWebSocketClose: () => {
        if (!this.isExplicitlyDisconnected) {
          this.listeners?.onStatusChange('RECONNECTING');
        }
      },
    });

    this.client.activate();
  }

  private subscribeToRoom(roomId: string, username: string, color: string) {
    if (!this.client || !this.client.connected) return;

    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = null;
    }

    const topic = `/topic/rooms/${roomId}`;
    this.subscription = this.client.subscribe(topic, (stompMessage) => {
      try {
        const payload: CollabMessage = JSON.parse(stompMessage.body);
        this.listeners?.onMessage(payload);
      } catch (err) {
        console.error('Failed to parse incoming STOMP message:', err);
      }
    });

    // Announce join to populate active presence and request immediate snapshot
    this.client.publish({
      destination: `/app/room/${roomId}/join`,
      body: JSON.stringify({ username, color }),
    });
  }

  public switchRoom(newRoomId: string, username: string, color: string) {
    if (this.client && this.client.connected) {
      this.subscribeToRoom(newRoomId, username, color);
    }
  }

  public createItem(roomId: string, title: string, description: string, status: ItemStatus, color: string, sender: string) {
    if (!this.client || !this.client.connected) return;
    this.client.publish({
      destination: `/app/room/${roomId}/create`,
      body: JSON.stringify({ roomId, title, description, status, color, sender }),
    });
  }

  public editItem(
    roomId: string,
    id: string,
    title: string,
    description: string,
    status: ItemStatus,
    color: string,
    baseVersion: number,
    sender: string,
    simulatedClientTimestamp?: number
  ) {
    if (!this.client || !this.client.connected) return;
    const clientTimestamp = simulatedClientTimestamp ?? Date.now();
    this.client.publish({
      destination: `/app/room/${roomId}/edit`,
      body: JSON.stringify({
        id,
        roomId,
        title,
        description,
        status,
        color,
        baseVersion,
        clientTimestamp,
        sender,
      }),
    });
  }

  public updateStatus(roomId: string, itemId: string, status: ItemStatus, sender: string) {
    if (!this.client || !this.client.connected) return;
    this.client.publish({
      destination: `/app/room/${roomId}/status`,
      body: JSON.stringify({ roomId, itemId, status, sender }),
    });
  }

  public deleteItem(roomId: string, itemId: string, sender: string) {
    if (!this.client || !this.client.connected) return;
    this.client.publish({
      destination: `/app/room/${roomId}/delete`,
      body: JSON.stringify({ roomId, itemId, sender }),
    });
  }

  public reportTyping(roomId: string, itemId: string | null, username: string) {
    if (!this.client || !this.client.connected) return;
    this.client.publish({
      destination: `/app/room/${roomId}/typing`,
      body: JSON.stringify({ itemId, username }),
    });
  }

  public requestSync(roomId: string) {
    if (!this.client || !this.client.connected) return;
    this.client.publish({
      destination: `/app/room/${roomId}/sync`,
      body: JSON.stringify({ roomId }),
    });
  }

  /**
   * Explicitly disconnects the socket to simulate offline state
   */
  public simulateDisconnect() {
    this.isExplicitlyDisconnected = true;
    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = null;
    }
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.listeners?.onStatusChange('DISCONNECTED');
  }

  public disconnect() {
    this.simulateDisconnect();
  }
}

export const socketService = new CollabSocketService();
