import React from 'react';
import { Plus, ListTodo, PlayCircle, CheckCircle2 } from 'lucide-react';
import type { BoardItem, ItemStatus } from '../types/collab';
import { TaskCard } from './TaskCard';

interface BoardProps {
  items: BoardItem[];
  onOpenCreateModal: (defaultStatus: ItemStatus) => void;
  onEditItem: (item: BoardItem) => void;
  onDeleteItem: (itemId: string) => void;
  onStatusChange: (itemId: string, newStatus: ItemStatus) => void;
  activeTypingUser: { username: string; itemId: string } | null;
}

const COLUMNS: { status: ItemStatus; title: string; color: string; icon: React.ReactNode }[] = [
  {
    status: 'TODO',
    title: 'To Do',
    color: '#8b5cf6',
    icon: <ListTodo size={17} color="#a78bfa" />,
  },
  {
    status: 'IN_PROGRESS',
    title: 'In Progress',
    color: '#06b6d4',
    icon: <PlayCircle size={17} color="#22d3ee" />,
  },
  {
    status: 'DONE',
    title: 'Done',
    color: '#10b981',
    icon: <CheckCircle2 size={17} color="#34d399" />,
  },
];

export const Board: React.FC<BoardProps> = ({
  items,
  onOpenCreateModal,
  onEditItem,
  onDeleteItem,
  onStatusChange,
  activeTypingUser,
}) => {
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(3, 1fr)',
      gap: '20px',
      padding: '24px',
      maxWidth: '1600px',
      margin: '0 auto',
      alignItems: 'start',
    }}>
      {COLUMNS.map((col) => {
        const columnItems = items.filter((item) => item.status === col.status);

        return (
          <div
            key={col.status}
            className="glass-panel"
            style={{
              padding: '16px',
              minHeight: '650px',
              display: 'flex',
              flexDirection: 'column',
              background: 'rgba(15, 23, 42, 0.55)',
            }}
          >
            {/* Column Header */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '16px',
              paddingBottom: '12px',
              borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {col.icon}
                <h2 style={{ fontSize: '0.95rem', fontWeight: 700, color: '#f8fafc' }}>
                  {col.title}
                </h2>
                <span className="badge font-mono" style={{ background: 'rgba(255, 255, 255, 0.08)', color: '#94a3b8' }}>
                  {columnItems.length}
                </span>
              </div>

              <button
                onClick={() => onOpenCreateModal(col.status)}
                className="btn btn-secondary"
                style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                title={`Add card to ${col.title}`}
              >
                <Plus size={14} /> Add
              </button>
            </div>

            {/* Card List */}
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              {columnItems.length === 0 ? (
                <div style={{
                  flex: 1,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  border: '1px dashed rgba(255, 255, 255, 0.1)',
                  borderRadius: '8px',
                  color: '#64748b',
                  fontSize: '0.8rem',
                  padding: '30px 16px',
                  textAlign: 'center',
                }}>
                  No tasks in {col.title}
                </div>
              ) : (
                columnItems.map((item) => {
                  const isPeerEditing = activeTypingUser?.itemId === item.id ? activeTypingUser.username : undefined;
                  return (
                    <TaskCard
                      key={item.id}
                      item={item}
                      onEdit={onEditItem}
                      onDelete={onDeleteItem}
                      onStatusChange={onStatusChange}
                      isBeingEditedByPeer={isPeerEditing}
                    />
                  );
                })
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};
