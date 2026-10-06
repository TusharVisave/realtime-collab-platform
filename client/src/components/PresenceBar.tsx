import React from 'react';
import { Users, Edit3 } from 'lucide-react';
import type { PresenceUser, Room } from '../types/collab';

interface PresenceBarProps {
  room: Room | null;
  activeUsers: PresenceUser[];
  activeTypingUser: { username: string; itemId: string } | null;
  itemsCount: number;
}

export const PresenceBar: React.FC<PresenceBarProps> = ({
  room,
  activeUsers,
  activeTypingUser,
  itemsCount,
}) => {
  return (
    <div style={{
      padding: '16px 24px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      background: 'rgba(15, 23, 42, 0.4)',
      borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
    }}>
      {/* Room Details */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <h1 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f8fafc' }}>
            {room ? room.name : 'Loading Room...'}
          </h1>
          <span className="badge" style={{ background: 'rgba(59, 130, 246, 0.12)', color: '#60a5fa' }}>
            {itemsCount} {itemsCount === 1 ? 'Task' : 'Tasks'}
          </span>
        </div>
        <p style={{ fontSize: '0.82rem', color: '#94a3b8', marginTop: '3px' }}>
          {room?.description || 'Realtime multi-tenant collaborative space with isolated STOMP topics'}
        </p>
      </div>

      {/* Right: Active Presence & Live Typing HUD */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '18px' }}>
        {/* Live Typing Indicator */}
        {activeTypingUser && activeTypingUser.itemId && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            fontSize: '0.78rem',
            color: '#38bdf8',
            background: 'rgba(56, 189, 248, 0.1)',
            padding: '4px 10px',
            borderRadius: '12px',
            border: '1px solid rgba(56, 189, 248, 0.25)',
          }}>
            <Edit3 size={13} className="animate-bounce" />
            <span><strong>{activeTypingUser.username}</strong> is editing...</span>
          </div>
        )}

        {/* Presence Avatars */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.8rem', color: '#64748b', marginRight: '4px' }}>
            <Users size={15} />
            <span>{activeUsers.length} online</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', marginLeft: '-6px' }}>
            {activeUsers.slice(0, 6).map((u, i) => (
              <div
                key={u.sessionId || i}
                title={`${u.username} (${u.sessionId.substring(0, 8)})`}
                style={{
                  width: '30px',
                  height: '30px',
                  borderRadius: '50%',
                  background: u.color || '#3b82f6',
                  border: '2px solid #0f172a',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '0.75rem',
                  fontWeight: 700,
                  color: '#fff',
                  marginLeft: i > 0 ? '-8px' : '0',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.4)',
                  cursor: 'default',
                  transition: 'transform 0.2s',
                }}
              >
                {u.username.charAt(0).toUpperCase()}
              </div>
            ))}
            {activeUsers.length > 6 && (
              <div style={{
                width: '30px',
                height: '30px',
                borderRadius: '50%',
                background: '#334155',
                border: '2px solid #0f172a',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '0.7rem',
                color: '#cbd5e1',
                marginLeft: '-8px',
              }}>
                +{activeUsers.length - 6}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
