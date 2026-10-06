import React, { useState } from 'react';
import { WifiOff, RefreshCw, ShieldAlert, Zap, Plus, Layers } from 'lucide-react';
import type { ConnectionStatus } from '../services/collabSocket';
import type { Room } from '../types/collab';

interface NavbarProps {
  rooms: Room[];
  currentRoomId: string;
  onSelectRoom: (roomId: string) => void;
  onCreateRoom: (id: string, name: string) => void;
  status: ConnectionStatus;
  username: string;
  userColor: string;
  onUpdateUsername: (newName: string) => void;
  onSimulateDisconnect: () => void;
  onSimulateReconnect: () => void;
  conflictCount: number;
  onToggleConflictDrawer: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  rooms,
  currentRoomId,
  onSelectRoom,
  onCreateRoom,
  status,
  username,
  userColor,
  onUpdateUsername,
  onSimulateDisconnect,
  onSimulateReconnect,
  conflictCount,
  onToggleConflictDrawer,
}) => {
  const [isEditingUser, setIsEditingUser] = useState(false);
  const [tempUsername, setTempUsername] = useState(username);
  const [showNewRoomModal, setShowNewRoomModal] = useState(false);
  const [newRoomName, setNewRoomName] = useState('');

  const handleSaveUser = () => {
    if (tempUsername.trim()) {
      onUpdateUsername(tempUsername.trim());
      setIsEditingUser(false);
    }
  };

  const handleCreateRoom = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRoomName.trim()) return;
    const id = newRoomName.toLowerCase().replace(/[^a-z0-9]/g, '-').replace(/-+/g, '-');
    onCreateRoom(id, newRoomName.trim());
    setNewRoomName('');
    setShowNewRoomModal(false);
  };

  return (
    <header style={{
      background: 'rgba(15, 23, 42, 0.85)',
      backdropFilter: 'blur(16px)',
      borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
      padding: '12px 24px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'sticky',
      top: 0,
      zIndex: 40,
    }}>
      {/* Brand & Room Selector */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            width: '38px',
            height: '38px',
            borderRadius: '10px',
            background: 'linear-gradient(135deg, #06b6d4 0%, #3b82f6 50%, #8b5cf6 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 0 16px rgba(59, 130, 246, 0.4)',
          }}>
            <Zap size={20} color="#fff" />
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '1rem', letterSpacing: '-0.02em', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span>Synapse Collab</span>
              <span className="badge" style={{ background: 'rgba(6, 182, 212, 0.15)', color: '#06b6d4', fontSize: '0.65rem' }}>
                STOMP / LWW
              </span>
            </div>
            <div style={{ fontSize: '0.72rem', color: '#64748b' }}>
              Flagship Distributed Real-time Engine
            </div>
          </div>
        </div>

        {/* Room Switcher */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Layers size={16} color="#94a3b8" />
          <select
            value={currentRoomId}
            onChange={(e) => onSelectRoom(e.target.value)}
            style={{
              background: 'rgba(30, 41, 59, 0.8)',
              color: '#f8fafc',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              borderRadius: '8px',
              padding: '6px 12px',
              fontSize: '0.85rem',
              fontWeight: 500,
              cursor: 'pointer',
              outline: 'none',
            }}
          >
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>

          <button
            onClick={() => setShowNewRoomModal(true)}
            className="btn btn-secondary"
            style={{ padding: '6px 10px', fontSize: '0.8rem' }}
            title="Create New Room"
          >
            <Plus size={14} /> New
          </button>
        </div>
      </div>

      {/* Right Controls: Simulator, Status, Presence, User */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
        {/* Connection State Badge */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          {status === 'CONNECTED' && (
            <span className="badge" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#10b981', border: '1px solid rgba(16, 185, 129, 0.3)' }}>
              <span style={{ width: '7px', height: '7px', borderRadius: '50%', background: '#10b981' }} className="animate-pulse-dot" />
              Connected
            </span>
          )}
          {status === 'CONNECTING' && (
            <span className="badge" style={{ background: 'rgba(59, 130, 246, 0.15)', color: '#3b82f6', border: '1px solid rgba(59, 130, 246, 0.3)' }}>
              <RefreshCw size={12} className="animate-spin" /> Connecting...
            </span>
          )}
          {status === 'RECONNECTING' && (
            <span className="badge" style={{ background: 'rgba(245, 158, 11, 0.15)', color: '#f59e0b', border: '1px solid rgba(245, 158, 11, 0.3)' }}>
              <RefreshCw size={12} /> Reconnecting...
            </span>
          )}
          {status === 'DISCONNECTED' && (
            <span className="badge" style={{ background: 'rgba(244, 63, 94, 0.15)', color: '#f43f5e', border: '1px solid rgba(244, 63, 94, 0.3)' }}>
              <WifiOff size={12} /> Offline (Simulated)
            </span>
          )}
        </div>

        {/* Reconnect / Catch-Up Simulator Button */}
        {status === 'CONNECTED' ? (
          <button
            onClick={onSimulateDisconnect}
            className="btn btn-warning"
            style={{ padding: '6px 12px', fontSize: '0.8rem' }}
            title="Simulate Wi-Fi drop to test offline mutation & reconnect catch-up"
          >
            <WifiOff size={14} /> Drop Socket
          </button>
        ) : (
          <button
            onClick={onSimulateReconnect}
            className="btn btn-primary"
            style={{ padding: '6px 12px', fontSize: '0.8rem' }}
            title="Reconnect & sync latest persisted DB snapshot"
          >
            <RefreshCw size={14} /> Reconnect & Catch Up
          </button>
        )}

        {/* Conflict Drawer Button */}
        <button
          onClick={onToggleConflictDrawer}
          className="btn btn-secondary"
          style={{
            padding: '6px 12px',
            fontSize: '0.8rem',
            borderColor: conflictCount > 0 ? 'rgba(244, 63, 94, 0.4)' : 'rgba(255, 255, 255, 0.1)',
            color: conflictCount > 0 ? '#f87171' : '#94a3b8',
          }}
        >
          <ShieldAlert size={14} />
          <span>LWW Audit</span>
          {conflictCount > 0 && (
            <span style={{
              background: '#f43f5e',
              color: '#fff',
              fontSize: '0.7rem',
              fontWeight: 700,
              padding: '1px 6px',
              borderRadius: '9999px',
            }}>
              {conflictCount}
            </span>
          )}
        </button>

        {/* User Identity Pill */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'rgba(30, 41, 59, 0.6)', padding: '4px 10px 4px 6px', borderRadius: '20px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
          <div style={{
            width: '24px',
            height: '24px',
            borderRadius: '50%',
            background: userColor,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 700,
            fontSize: '0.72rem',
          }}>
            {username.charAt(0).toUpperCase()}
          </div>
          {isEditingUser ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <input
                value={tempUsername}
                onChange={(e) => setTempUsername(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSaveUser()}
                style={{
                  background: '#0f172a',
                  color: '#fff',
                  border: '1px solid #3b82f6',
                  borderRadius: '4px',
                  padding: '2px 6px',
                  fontSize: '0.8rem',
                  width: '90px',
                }}
                autoFocus
              />
              <button onClick={handleSaveUser} style={{ background: '#3b82f6', border: 'none', color: '#fff', borderRadius: '4px', padding: '2px 6px', fontSize: '0.7rem', cursor: 'pointer' }}>OK</button>
            </div>
          ) : (
            <span
              onClick={() => setIsEditingUser(true)}
              style={{ fontSize: '0.82rem', fontWeight: 600, color: '#f1f5f9', cursor: 'pointer' }}
              title="Click to change your username"
            >
              {username}
            </span>
          )}
        </div>
      </div>

      {/* New Room Modal */}
      {showNewRoomModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.6)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
        }}>
          <div className="glass-panel" style={{ padding: '24px', width: '380px' }}>
            <h3 style={{ marginBottom: '14px', fontSize: '1.1rem' }}>Create Collaboration Room</h3>
            <form onSubmit={handleCreateRoom}>
              <input
                type="text"
                placeholder="e.g. Sprint-44-Retro"
                value={newRoomName}
                onChange={(e) => setNewRoomName(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  borderRadius: '8px',
                  background: '#0f172a',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  color: '#fff',
                  marginBottom: '16px',
                  outline: 'none',
                }}
                autoFocus
              />
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  type="button"
                  onClick={() => setShowNewRoomModal(false)}
                  className="btn btn-secondary"
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Create Room
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </header>
  );
};
