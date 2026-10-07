import React, { useState, useEffect } from 'react';
import { X, Cpu } from 'lucide-react';
import type { BoardItem, ItemStatus } from '../types/collab';

interface TaskModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (data: {
    id?: string;
    title: string;
    description: string;
    status: ItemStatus;
    color: string;
    baseVersion?: number;
    simulatedTimestamp?: number;
  }) => void;
  initialItem?: BoardItem | null;
  defaultStatus?: ItemStatus;
  onTypingStatusChange?: (isTyping: boolean) => void;
}

const PALETTE = ['#3b82f6', '#10b981', '#8b5cf6', '#f59e0b', '#ec4899', '#06b6d4', '#f97316'];

export const TaskModal: React.FC<TaskModalProps> = ({
  isOpen,
  onClose,
  onSave,
  initialItem,
  defaultStatus = 'TODO',
  onTypingStatusChange,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<ItemStatus>(defaultStatus);
  const [color, setColor] = useState(PALETTE[0]);

  // Concurrency Simulation Toggles
  const [simulateStaleVersion, setSimulateStaleVersion] = useState(false);
  const [staleVersionValue, setStaleVersionValue] = useState(1);
  const [simulateDelayedTimestamp, setSimulateDelayedTimestamp] = useState(false);

  useEffect(() => {
    if (initialItem) {
      setTitle(initialItem.title);
      setDescription(initialItem.description || '');
      setStatus(initialItem.status);
      setColor(initialItem.color || PALETTE[0]);
      setStaleVersionValue(Math.max(1, (initialItem.version || 1) - 1));
      onTypingStatusChange?.(true);
    } else {
      setTitle('');
      setDescription('');
      setStatus(defaultStatus);
      setColor(PALETTE[Math.floor(Math.random() * PALETTE.length)]);
    }

    return () => {
      onTypingStatusChange?.(false);
    };
  }, [initialItem, defaultStatus, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;

    let baseVersion = initialItem ? initialItem.version : undefined;
    if (initialItem && simulateStaleVersion) {
      baseVersion = staleVersionValue;
    }

    let simulatedTimestamp: number | undefined = undefined;
    if (simulateDelayedTimestamp) {
      // 30 seconds into the past to trigger LWW rejection against newer writes
      simulatedTimestamp = Date.now() - 30000;
    }

    onSave({
      id: initialItem?.id,
      title: title.trim(),
      description: description.trim(),
      status,
      color,
      baseVersion,
      simulatedTimestamp,
    });

    onClose();
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(0, 0, 0, 0.75)',
      backdropFilter: 'blur(8px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 100,
    }}>
      <div className="glass-panel" style={{ width: '520px', maxWidth: '92vw', padding: '24px', position: 'relative' }}>
        {/* Close Button */}
        <button
          onClick={onClose}
          style={{
            position: 'absolute',
            top: '16px',
            right: '16px',
            background: 'none',
            border: 'none',
            color: '#94a3b8',
            cursor: 'pointer',
          }}
        >
          <X size={20} />
        </button>

        <h2 style={{ fontSize: '1.2rem', fontWeight: 700, marginBottom: '6px', color: '#f8fafc' }}>
          {initialItem ? 'Edit Collaborative Task' : 'Create New Task'}
        </h2>
        <p style={{ fontSize: '0.8rem', color: '#94a3b8', marginBottom: '18px' }}>
          {initialItem
            ? `Editing entity [${initialItem.id}]. Changes are broadcasted over STOMP and reconciled with LWW.`
            : 'Add a new task card to the shared live board.'}
        </p>

        <form onSubmit={handleSubmit}>
          {/* Title */}
          <div style={{ marginBottom: '14px' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '6px' }}>
              Title
            </label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Implement STOMP Room Broadcast"
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: '8px',
                background: '#0f172a',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                color: '#fff',
                fontSize: '0.9rem',
                outline: 'none',
              }}
              autoFocus
            />
          </div>

          {/* Description */}
          <div style={{ marginBottom: '14px' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '6px' }}>
              Description
            </label>
            <textarea
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Detailed description, acceptance criteria, or architectural notes..."
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: '8px',
                background: '#0f172a',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                color: '#fff',
                fontSize: '0.85rem',
                outline: 'none',
                resize: 'vertical',
              }}
            />
          </div>

          {/* Status & Color Selection */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '18px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '6px' }}>
                Status Column
              </label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value as ItemStatus)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  borderRadius: '8px',
                  background: '#0f172a',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  color: '#fff',
                  fontSize: '0.85rem',
                  outline: 'none',
                }}
              >
                <option value="TODO">To Do</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="DONE">Done</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '6px' }}>
                Card Accent Color
              </label>
              <div style={{ display: 'flex', gap: '8px', alignItems: 'center', height: '38px' }}>
                {PALETTE.map((p) => (
                  <div
                    key={p}
                    onClick={() => setColor(p)}
                    style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: p,
                      cursor: 'pointer',
                      border: color === p ? '2px solid #fff' : '2px solid transparent',
                      transform: color === p ? 'scale(1.15)' : 'scale(1)',
                      transition: 'all 0.15s',
                    }}
                  />
                ))}
              </div>
            </div>
          </div>

          {/* Concurrency Laboratory Section (When editing an existing card) */}
          {initialItem && (
            <div style={{
              background: 'rgba(30, 41, 59, 0.5)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: '8px',
              padding: '12px',
              marginBottom: '18px',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.8rem', fontWeight: 600, color: '#38bdf8', marginBottom: '8px' }}>
                <Cpu size={14} /> Concurrency & Conflict Simulator
              </div>

              <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginBottom: '8px' }}>
                Current persisted base version: <strong style={{ color: '#fff' }}>v{initialItem.version}</strong>
              </div>

              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.78rem', color: '#e2e8f0', cursor: 'pointer', marginBottom: '6px' }}>
                <input
                  type="checkbox"
                  checked={simulateStaleVersion}
                  onChange={(e) => setSimulateStaleVersion(e.target.checked)}
                />
                <span>Simulate Stale Base Version (test concurrent write collision)</span>
              </label>

              {simulateStaleVersion && (
                <div style={{ marginLeft: '22px', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '0.72rem', color: '#94a3b8' }}>Send baseVersion:</span>
                  <input
                    type="number"
                    min={1}
                    value={staleVersionValue}
                    onChange={(e) => setStaleVersionValue(parseInt(e.target.value) || 1)}
                    style={{ width: '60px', background: '#0f172a', border: '1px solid #64748b', color: '#fff', borderRadius: '4px', padding: '2px 6px', fontSize: '0.75rem' }}
                  />
                </div>
              )}

              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.78rem', color: '#e2e8f0', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={simulateDelayedTimestamp}
                  onChange={(e) => setSimulateDelayedTimestamp(e.target.checked)}
                />
                <span>Simulate Outdated Clock (t - 30s) to trigger <strong>LWW Rejection</strong></span>
              </label>
            </div>
          )}

          {/* Form Actions */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
            <button type="button" onClick={onClose} className="btn btn-secondary">
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              {initialItem ? 'Commit Update' : 'Create Task'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
