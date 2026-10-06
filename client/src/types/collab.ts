export type ItemStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

export interface BoardItem {
  id: string;
  roomId: string;
  title: string;
  description: string;
  status: ItemStatus;
  color: string;
  version: number;
  clientTimestamp: number;
  serverTimestamp: number;
  lastModifiedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface Room {
  id: string;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface PresenceUser {
  sessionId: string;
  username: string;
  color: string;
  joinedAt: number;
  currentEditingItemId: string | null;
}

export interface ConflictRecord {
  id: string;
  roomId: string;
  itemId: string;
  itemTitle: string;
  winningUser: string;
  rejectedUser: string;
  winningVersion: number;
  rejectedVersion: number;
  winningTimestamp: number;
  rejectedTimestamp: number;
  resolutionReason: string;
  createdAt: string;
}

export interface ConflictReport {
  recordId: string;
  roomId: string;
  itemId: string;
  itemTitle: string;
  winningUser: string;
  rejectedUser: string;
  winningVersion: number;
  rejectedVersion: number;
  winningTimestamp: number;
  rejectedTimestamp: number;
  currentWinnerItem: BoardItem;
  resolutionReason: string;
}

export interface RoomSyncPayload {
  room: Room;
  items: BoardItem[];
  activeUsers: PresenceUser[];
  recentConflicts: ConflictRecord[];
  serverTime: number;
}

export type CollabAction =
  | 'ITEM_CREATED'
  | 'ITEM_UPDATED'
  | 'ITEM_DELETED'
  | 'ITEM_STATUS_CHANGED'
  | 'CONFLICT_DETECTED'
  | 'PRESENCE_SYNC'
  | 'PRESENCE_TYPING'
  | 'ROOM_SYNC'
  | 'ERROR';

export interface CollabMessage {
  action: CollabAction;
  roomId: string;
  sender: string;
  payload: any;
  timestamp: number;
}
