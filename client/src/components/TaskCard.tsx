import React from 'react';
import { ArrowLeft, ArrowRight, Edit2, Trash2 } from 'lucide-react';
import type { BoardItem, ItemStatus } from '../types/collab';

interface TaskCardProps {
  item: BoardItem;
  onEdit: (item: BoardItem) => void;
  onDelete: (itemId: string) => void;
  onStatusChange: (itemId: string, newStatus: ItemStatus) => void;
  isBeingEditedByPeer?: string; // username if another peer is typing on this card
}

export const TaskCard: React.FC<TaskCardProps> = ({
  item,
  onEdit,
  onDelete,
  onStatusChange,
  isBeingEditedByPeer,
}) => {
  const getNextStatus = (current: ItemStatus): ItemStatus | null => {
    if (current === 'TODO') return 'IN_PROGRESS';
    if (current === 'IN_PROGRESS') return 'DONE';
    return null;
  };

  const getPrevStatus = (current: ItemStatus): ItemStatus | null => {
    if (current === 'DONE') return 'IN_PROGRESS';
    if (current === 'IN_PROGRESS') return 'TODO';
    return null;
  };

  const nextStatus = getNextStatus(item.status);
  const prevStatus = getPrevStatus(item.status);

  return (
    <div
      className="glass-card"
      style={{
        padding: '16px',
        marginBottom: '12px',
        position: 'relative',
        borderLeft: `4px solid ${item.color || '#3b82f6'}`,
        borderColor: isBeingEditedByPeer ? '#38bdf8' : undefined,
        boxShadow: isBeingEditedByPeer ? '0 0 15px rgba(56, 189, 248, 0.4)' : undefined,
      }}
    >
      {/* Peer live editing indicator banner */}
      {isBeingEditedByPeer && (
        <div style={{
          position: 'absolute',
          top: '-10px',
          right: '12px',
          background: '#0284c7',
          color: '#fff',
          fontSize: '0.68rem',
          fontWeight: 700,
          padding: '2px 8px',
          borderRadius: '10px',
          boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
          display: 'flex',
          alignItems: 'center',
          gap: '4px',
        }}>
          <span style={{ width: '5px', height: '5px', borderRadius: '50%', background: '#fff' }} className="animate-pulse-dot" />
          {isBeingEditedByPeer} editing
        </div>
      )}

      {/* Header: Version badge & actions */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
        <span
          className="badge font-mono"
          style={{
            background: 'rgba(255, 255, 255, 0.07)',
            color: '#cbd5e1',
            fontSize: '0.7rem',
          }}
          title={`Optimistic lock version ${item.version}`}
        >
          v{item.version}
        </span>

        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <button
            onClick={() => onEdit(item)}
            className="btn btn-secondary"
            style={{ padding: '4px 6px', fontSize: '0.75rem' }}
            title="Edit task (inspect LWW conflict simulation)"
          >
            <Edit2 size={13} />
          </button>
          <button
            onClick={() => onDelete(item.id)}
            className="btn btn-danger"
            style={{ padding: '4px 6px', fontSize: '0.75rem' }}
            title="Delete task"
          >
            <Trash2 size={13} />
          </button>
        </div>
      </div>

      {/* Title */}
      <h3 style={{
        fontSize: '0.95rem',
        fontWeight: 600,
        color: '#f8fafc',
        marginBottom: '6px',
        lineHeight: 1.35,
      }}>
        {item.title}
      </h3>

      {/* Description */}
      {item.description && (
        <p style={{
          fontSize: '0.82rem',
          color: '#94a3b8',
          marginBottom: '14px',
          lineHeight: 1.4,
          whiteSpace: 'pre-wrap',
        }}>
          {item.description}
        </p>
      )}

      {/* Metadata: Author & Time */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        fontSize: '0.72rem',
        color: '#64748b',
        borderTop: '1px solid rgba(255, 255, 255, 0.06)',
        paddingTop: '10px',
        marginTop: '8px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
          <span style={{ color: '#94a3b8' }}>By:</span>
          <strong style={{ color: '#e2e8f0' }}>{item.lastModifiedBy}</strong>
        </div>

        {/* Move Left / Right status controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
          {prevStatus && (
            <button
              onClick={() => onStatusChange(item.id, prevStatus)}
              className="btn btn-secondary"
              style={{ padding: '3px 6px', fontSize: '0.7rem' }}
              title={`Move to ${prevStatus}`}
            >
              <ArrowLeft size={12} />
            </button>
          )}
          {nextStatus && (
            <button
              onClick={() => onStatusChange(item.id, nextStatus)}
              className="btn btn-primary"
              style={{ padding: '3px 6px', fontSize: '0.7rem' }}
              title={`Move to ${nextStatus}`}
            >
              <ArrowRight size={12} />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
