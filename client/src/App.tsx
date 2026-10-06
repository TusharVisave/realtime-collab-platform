import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Navbar } from './components/Navbar';
import { PresenceBar } from './components/PresenceBar';
import { Board } from './components/Board';
import { TaskModal } from './components/TaskModal';
import { ConflictDrawer } from './components/ConflictDrawer';
import { ActivityFeed } from './components/ActivityFeed';
import { socketService, type ConnectionStatus } from './services/collabSocket';
import type {
  BoardItem,
  Room,
  PresenceUser,
  ConflictRecord,
  ConflictReport,
  CollabMessage,
  ItemStatus,
  RoomSyncPayload,
} from './types/collab';

const RANDOM_NAMES = ['Aria', 'Caleb', 'Devon', 'Elena', 'Kiran', 'Maya', 'Nico', 'Sora', 'Zara'];
const PALETTE = ['#3b82f6', '#10b981', '#8b5cf6', '#f59e0b', '#ec4899', '#06b6d4', '#f97316'];

export const App: React.FC = () => {
  // User identity
  const [username, setUsername] = useState(() => {
    const saved = localStorage.getItem('collab_username');
    if (saved) return saved;
    const picked = RANDOM_NAMES[Math.floor(Math.random() * RANDOM_NAMES.length)] + '-' + Math.floor(Math.random() * 89 + 10);
    localStorage.setItem('collab_username', picked);
    return picked;
  });

  const [userColor] = useState(() => {
    return PALETTE[Math.floor(Math.random() * PALETTE.length)];
  });

  // Rooms and navigation
  const [rooms, setRooms] = useState<Room[]>([
    { id: 'engineering-sync', name: 'Engineering Core Sync', description: 'Distributed systems, WebSocket concurrency & LWW consensus', createdAt: '', updatedAt: '' },
    { id: 'design-review', name: 'Product & Architecture Review', description: 'UI Glassmorphism, Presence HUD, and Realtime Experience', createdAt: '', updatedAt: '' },
  ]);
  const [currentRoomId, setCurrentRoomId] = useState<string>('engineering-sync');
  const [currentRoom, setCurrentRoom] = useState<Room | null>(null);

  // Real-time State
  const [status, setStatus] = useState<ConnectionStatus>('CONNECTING');
  const [items, setItems] = useState<BoardItem[]>([]);
  const [activeUsers, setActiveUsers] = useState<PresenceUser[]>([]);
  const [conflicts, setConflicts] = useState<ConflictRecord[]>([]);
  const [activeConflict, setActiveConflict] = useState<ConflictReport | null>(null);
  const [activeTypingUser, setActiveTypingUser] = useState<{ username: string; itemId: string } | null>(null);
  const [events, setEvents] = useState<CollabMessage[]>([]);

  // Modals & Panels
  const [isTaskModalOpen, setIsTaskModalOpen] = useState(false);
  const [modalDefaultStatus, setModalDefaultStatus] = useState<ItemStatus>('TODO');
  const [editingItem, setEditingItem] = useState<BoardItem | null>(null);
  const [isConflictDrawerOpen, setIsConflictDrawerOpen] = useState(false);
  const [isActivityFeedOpen, setIsActivityFeedOpen] = useState(false);

  // References
  const currentRoomIdRef = useRef(currentRoomId);
  currentRoomIdRef.current = currentRoomId;

  // Handle incoming STOMP messages
  const handleIncomingMessage = useCallback((msg: CollabMessage) => {
    // Append to live activity events
    setEvents((prev) => [msg, ...prev.slice(0, 49)]);

    switch (msg.action) {
      case 'ROOM_SYNC': {
        const syncPayload = msg.payload as RoomSyncPayload;
        if (syncPayload) {
          if (syncPayload.room) setCurrentRoom(syncPayload.room);
          if (syncPayload.items) setItems(syncPayload.items);
          if (syncPayload.activeUsers) setActiveUsers(syncPayload.activeUsers);
          if (syncPayload.recentConflicts) setConflicts(syncPayload.recentConflicts);
        }
        break;
      }

      case 'ITEM_CREATED': {
        const newItem = msg.payload as BoardItem;
        if (newItem) {
          setItems((prev) => {
            const exists = prev.some((i) => i.id === newItem.id);
            if (exists) return prev.map((i) => (i.id === newItem.id ? newItem : i));
            return [...prev, newItem];
          });
        }
        break;
      }

      case 'ITEM_UPDATED': {
        const updatedItem = msg.payload as BoardItem;
        if (updatedItem) {
          setItems((prev) => prev.map((i) => (i.id === updatedItem.id ? updatedItem : i)));
        }
        break;
      }

      case 'ITEM_STATUS_CHANGED': {
        const updatedItem = msg.payload as BoardItem;
        if (updatedItem) {
          setItems((prev) => prev.map((i) => (i.id === updatedItem.id ? updatedItem : i)));
        }
        break;
      }

      case 'ITEM_DELETED': {
        const deletedId = msg.payload?.itemId;
        if (deletedId) {
          setItems((prev) => prev.filter((i) => i.id !== deletedId));
        }
        break;
      }

      case 'PRESENCE_SYNC': {
        const users = msg.payload as PresenceUser[];
        if (Array.isArray(users)) {
          setActiveUsers(users);
        }
        break;
      }

      case 'PRESENCE_TYPING': {
        const { username: typingUser, itemId } = msg.payload || {};
        if (typingUser && typingUser !== username) {
          if (itemId) {
            setActiveTypingUser({ username: typingUser, itemId });
          } else {
            setActiveTypingUser(null);
          }
        }
        break;
      }

      case 'CONFLICT_DETECTED': {
        const report = msg.payload as ConflictReport;
        if (report) {
          setActiveConflict(report);

          // Prepend to room conflicts
          const record: ConflictRecord = {
            id: report.recordId || String(Date.now()),
            roomId: report.roomId,
            itemId: report.itemId,
            itemTitle: report.itemTitle,
            winningUser: report.winningUser,
            rejectedUser: report.rejectedUser,
            winningVersion: report.winningVersion,
            rejectedVersion: report.rejectedVersion,
            winningTimestamp: report.winningTimestamp,
            rejectedTimestamp: report.rejectedTimestamp,
            resolutionReason: report.resolutionReason,
            createdAt: new Date().toISOString(),
          };
          setConflicts((prev) => [record, ...prev]);

          // Reconcile winner item in local items state
          if (report.currentWinnerItem) {
            setItems((prev) =>
              prev.map((i) => (i.id === report.currentWinnerItem.id ? report.currentWinnerItem : i))
            );
          }
        }
        break;
      }

      default:
        break;
    }
  }, [username]);

  // Initial connect & Room switching
  useEffect(() => {
    // Fetch rooms list from REST
    fetch('http://localhost:8080/api/rooms')
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setRooms(data);
          const found = data.find((r: Room) => r.id === currentRoomId);
          if (found) setCurrentRoom(found);
        }
      })
      .catch(() => {
        // use fallback initial rooms
      });

    // Establish WebSocket Connection
    socketService.connect(currentRoomId, username, userColor, {
      onMessage: handleIncomingMessage,
      onStatusChange: (newStatus) => setStatus(newStatus),
    });

    return () => {
      socketService.disconnect();
    };
  }, [currentRoomId, username, userColor, handleIncomingMessage]);

  const handleSelectRoom = (roomId: string) => {
    setCurrentRoomId(roomId);
    socketService.switchRoom(roomId, username, userColor);
  };

  const handleCreateRoom = (id: string, name: string) => {
    const newRoom: Room = {
      id,
      name,
      description: 'Collaborative space created by ' + username,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    setRooms((prev) => [...prev, newRoom]);
    setCurrentRoomId(id);
    socketService.switchRoom(id, username, userColor);
  };

  const handleUpdateUsername = (newName: string) => {
    setUsername(newName);
    localStorage.setItem('collab_username', newName);
  };

  // Disconnect / Reconnect Simulators
  const handleSimulateDisconnect = () => {
    socketService.simulateDisconnect();
  };

  const handleSimulateReconnect = () => {
    socketService.connect(currentRoomId, username, userColor, {
      onMessage: handleIncomingMessage,
      onStatusChange: (newStatus) => setStatus(newStatus),
    });
  };

  // Card Operations
  const handleOpenCreateModal = (defaultStatus: ItemStatus) => {
    setEditingItem(null);
    setModalDefaultStatus(defaultStatus);
    setIsTaskModalOpen(true);
  };

  const handleEditItem = (item: BoardItem) => {
    setEditingItem(item);
    setIsTaskModalOpen(true);
  };

  const handleDeleteItem = (itemId: string) => {
    socketService.deleteItem(currentRoomId, itemId, username);
  };

  const handleStatusChange = (itemId: string, newStatus: ItemStatus) => {
    socketService.updateStatus(currentRoomId, itemId, newStatus, username);
  };

  const handleSaveModal = (data: {
    id?: string;
    title: string;
    description: string;
    status: ItemStatus;
    color: string;
    baseVersion?: number;
    simulatedTimestamp?: number;
  }) => {
    if (data.id) {
      // Edit with baseVersion and optional simulated timestamp (for LWW testing)
      socketService.editItem(
        currentRoomId,
        data.id,
        data.title,
        data.description,
        data.status,
        data.color,
        data.baseVersion ?? 1,
        username,
        data.simulatedTimestamp
      );
    } else {
      // Create new
      socketService.createItem(
        currentRoomId,
        data.title,
        data.description,
        data.status,
        data.color,
        username
      );
    }
  };

  const handleTypingStatusChange = (isTyping: boolean) => {
    if (editingItem) {
      socketService.reportTyping(currentRoomId, isTyping ? editingItem.id : null, username);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* Top Navbar */}
      <Navbar
        rooms={rooms}
        currentRoomId={currentRoomId}
        onSelectRoom={handleSelectRoom}
        onCreateRoom={handleCreateRoom}
        status={status}
        username={username}
        userColor={userColor}
        onUpdateUsername={handleUpdateUsername}
        onSimulateDisconnect={handleSimulateDisconnect}
        onSimulateReconnect={handleSimulateReconnect}
        conflictCount={conflicts.length}
        onToggleConflictDrawer={() => setIsConflictDrawerOpen((prev) => !prev)}
      />

      {/* Room Presence & Live HUD */}
      <PresenceBar
        room={currentRoom}
        activeUsers={activeUsers}
        activeTypingUser={activeTypingUser}
        itemsCount={items.length}
      />

      {/* Main Board Kanban */}
      <main style={{ flex: 1 }}>
        <Board
          items={items}
          onOpenCreateModal={handleOpenCreateModal}
          onEditItem={handleEditItem}
          onDeleteItem={handleDeleteItem}
          onStatusChange={handleStatusChange}
          activeTypingUser={activeTypingUser}
        />
      </main>

      {/* Live Event Stream Ticker */}
      <ActivityFeed
        events={events}
        isOpen={isActivityFeedOpen}
        onToggle={() => setIsActivityFeedOpen((prev) => !prev)}
      />

      {/* Modal for Creating / Editing Task */}
      <TaskModal
        isOpen={isTaskModalOpen}
        onClose={() => setIsTaskModalOpen(false)}
        onSave={handleSaveModal}
        initialItem={editingItem}
        defaultStatus={modalDefaultStatus}
        onTypingStatusChange={handleTypingStatusChange}
      />

      {/* Conflict Audit Drawer */}
      <ConflictDrawer
        isOpen={isConflictDrawerOpen}
        onClose={() => setIsConflictDrawerOpen(false)}
        conflicts={conflicts}
        activeConflict={activeConflict}
        onDismissActiveConflict={() => setActiveConflict(null)}
      />
    </div>
  );
};

export default App;
