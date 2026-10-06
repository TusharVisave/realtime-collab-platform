import React from 'react';
import { Activity, ShieldAlert, CheckCircle, UserPlus, Trash, Edit } from 'lucide-react';
import type { CollabMessage } from '../types/collab';

interface ActivityFeedProps {
  events: CollabMessage[];
  isOpen: boolean;
  onToggle: () => void;
}

export const ActivityFeed: React.FC<ActivityFeedProps> = ({ events, isOpen, onToggle }) => {
  const getActionIcon = (action: string) => {
    switch (action) {
      case 'ITEM_CREATED':
        return <CheckCircle size={13} color="#10b981" />;
      case 'ITEM_UPDATED':
      case 'ITEM_STATUS_CHANGED':
        return <Edit size={13} color="#3b82f6" />;
      case 'ITEM_DELETED':
        return <Trash size={13} color="#f43f5e" />;
      case 'CONFLICT_DETECTED':
        return <ShieldAlert size={13} color="#f43f5e" />;
      case 'PRESENCE_SYNC':
        return <UserPlus size={13} color="#8b5cf6" />;
      default:
        return <Activity size={13} color="#06b6d4" />;
    }
  };

  const formatEventText = (e: CollabMessage) => {
    switch (e.action) {
      case 'ITEM_CREATED':
        return `${e.sender} created "${e.payload?.title || 'item'}"`;
      case 'ITEM_UPDATED':
        return `${e.sender} updated "${e.payload?.title || 'item'}" (v${e.payload?.version})`;
      case 'ITEM_STATUS_CHANGED':
        return `${e.sender} changed status to ${e.payload?.status}`;
      case 'ITEM_DELETED':
        return `${e.sender} deleted item #${e.payload?.itemId}`;
      case 'CONFLICT_DETECTED':
        return `Conflict: ${e.payload?.winningUser} overwrote ${e.payload?.rejectedUser}`;
      case 'PRESENCE_SYNC':
        return `Presence sync: ${Array.isArray(e.payload) ? e.payload.length : 0} online`;
      case 'ROOM_SYNC':
        return `Synced room snapshot (${e.payload?.items?.length || 0} items)`;
      default:
        return `${e.action} by ${e.sender}`;
    }
  };

  return (
    <div style={{
      position: 'fixed',
      bottom: '16px',
      left: '24px',
      zIndex: 50,
    }}>
      {/* Toggle pill */}
      <button
        onClick={onToggle}
        className="btn btn-secondary"
        style={{
          padding: '6px 12px',
          fontSize: '0.78rem',
          borderRadius: '20px',
          background: 'rgba(15, 23, 42, 0.9)',
          boxShadow: '0 4px 12px rgba(0,0,0,0.4)',
        }}
      >
        <Activity size={14} color="#06b6d4" />
        <span>Live Stream</span>
        <span className="badge font-mono" style={{ background: 'rgba(6, 182, 212, 0.2)', color: '#22d3ee', fontSize: '0.68rem' }}>
          {events.length}
        </span>
      </button>

      {/* Expanded Feed Box */}
      {isOpen && (
        <div
          className="glass-panel"
          style={{
            position: 'absolute',
            bottom: '42px',
            left: '0',
            width: '380px',
            maxHeight: '320px',
            overflowY: 'auto',
            padding: '14px',
            background: 'rgba(15, 23, 42, 0.95)',
            boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#f8fafc' }}>Real-time STOMP Events</span>
            <span style={{ fontSize: '0.7rem', color: '#64748b' }}>Latest 25</span>
          </div>

          {events.length === 0 ? (
            <div style={{ fontSize: '0.75rem', color: '#64748b', textAlign: 'center', padding: '16px 0' }}>
              Waiting for events...
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {events.slice(0, 25).map((ev, i) => (
                <div
                  key={i}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '8px',
                    fontSize: '0.73rem',
                    color: ev.action === 'CONFLICT_DETECTED' ? '#fca5a5' : '#cbd5e1',
                    background: ev.action === 'CONFLICT_DETECTED' ? 'rgba(244, 63, 94, 0.1)' : 'rgba(30, 41, 59, 0.4)',
                    padding: '6px 8px',
                    borderRadius: '6px',
                  }}
                >
                  <div style={{ marginTop: '2px' }}>{getActionIcon(ev.action)}</div>
                  <div style={{ flex: 1, lineHeight: 1.3 }}>{formatEventText(ev)}</div>
                  <span style={{ fontSize: '0.65rem', color: '#64748b', whiteSpace: 'nowrap' }}>
                    {new Date(ev.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
