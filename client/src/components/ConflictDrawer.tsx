import React from 'react';
import { X, ShieldAlert, Award, ArrowRight } from 'lucide-react';
import type { ConflictRecord, ConflictReport } from '../types/collab';

interface ConflictDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  conflicts: ConflictRecord[];
  activeConflict: ConflictReport | null;
  onDismissActiveConflict: () => void;
}

export const ConflictDrawer: React.FC<ConflictDrawerProps> = ({
  isOpen,
  onClose,
  conflicts,
  activeConflict,
  onDismissActiveConflict,
}) => {
  return (
    <>
      {/* Active Conflict Toast / Alert Banner */}
      {activeConflict && (
        <div style={{
          position: 'fixed',
          bottom: '24px',
          right: '24px',
          maxWidth: '460px',
          background: 'rgba(23, 16, 28, 0.95)',
          border: '1px solid rgba(244, 63, 94, 0.5)',
          borderRadius: '12px',
          padding: '16px',
          boxShadow: '0 8px 30px rgba(244, 63, 94, 0.25)',
          zIndex: 90,
          backdropFilter: 'blur(12px)',
        }} className="animate-conflict">
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#f43f5e', fontWeight: 700, fontSize: '0.9rem' }}>
              <ShieldAlert size={18} />
              <span>Concurrent Conflict Detected!</span>
            </div>
            <button
              onClick={onDismissActiveConflict}
              style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer' }}
            >
              <X size={16} />
            </button>
          </div>

          <p style={{ fontSize: '0.8rem', color: '#e2e8f0', marginTop: '6px', lineHeight: 1.4 }}>
            Item: <strong>"{activeConflict.itemTitle}"</strong>
          </p>

          <div style={{
            marginTop: '8px',
            background: 'rgba(244, 63, 94, 0.1)',
            padding: '8px 10px',
            borderRadius: '6px',
            fontSize: '0.75rem',
            color: '#fca5a5',
          }}>
            <div><strong>Resolution:</strong> {activeConflict.resolutionReason}</div>
            <div style={{ marginTop: '4px', display: 'flex', gap: '8px' }}>
              <span>Winner: <strong style={{ color: '#86efac' }}>{activeConflict.winningUser}</strong></span>
              <span>|</span>
              <span>Rejected: <strong style={{ color: '#fda4af' }}>{activeConflict.rejectedUser}</strong></span>
            </div>
          </div>
        </div>
      )}

      {/* Slide-over Conflict Drawer */}
      {isOpen && (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.5)',
          zIndex: 80,
          display: 'flex',
          justifyContent: 'flex-end',
        }}>
          <div
            className="glass-panel"
            style={{
              width: '450px',
              maxWidth: '90vw',
              height: '100%',
              borderRadius: '0',
              padding: '24px',
              overflowY: 'auto',
              borderLeft: '1px solid rgba(255, 255, 255, 0.1)',
              background: '#0d131f',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <ShieldAlert size={20} color="#f43f5e" />
                <h2 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f8fafc' }}>
                  LWW Conflict Audit History
                </h2>
              </div>
              <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer' }}>
                <X size={20} />
              </button>
            </div>

            <div style={{
              background: 'rgba(59, 130, 246, 0.08)',
              border: '1px solid rgba(59, 130, 246, 0.2)',
              borderRadius: '8px',
              padding: '10px 12px',
              fontSize: '0.75rem',
              color: '#93c5fd',
              marginBottom: '20px',
              lineHeight: 1.45,
            }}>
              <strong>Section 11 Architecture Note:</strong> Under Last-Write-Wins (LWW), the edit with the newer client timestamp wins. The losing user's concurrent modifications are overwritten rather than merged.
            </div>

            {conflicts.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '40px 0', color: '#64748b' }}>
                <Award size={32} style={{ margin: '0 auto 8px', opacity: 0.5 }} />
                <p style={{ fontSize: '0.85rem' }}>No conflicts recorded in this room yet.</p>
                <p style={{ fontSize: '0.75rem', marginTop: '4px' }}>
                  Try opening two browser tabs and editing the same task simultaneously!
                </p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {conflicts.map((c) => (
                  <div
                    key={c.id}
                    style={{
                      background: 'rgba(30, 41, 59, 0.6)',
                      border: '1px solid rgba(255, 255, 255, 0.08)',
                      borderRadius: '8px',
                      padding: '12px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '6px' }}>
                      <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#f1f5f9' }}>
                        {c.itemTitle || `Item #${c.itemId}`}
                      </span>
                      <span className="badge font-mono" style={{ background: 'rgba(244, 63, 94, 0.15)', color: '#f43f5e', fontSize: '0.68rem' }}>
                        v{c.winningVersion}
                      </span>
                    </div>

                    <div style={{ fontSize: '0.75rem', color: '#cbd5e1', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span style={{ color: '#10b981', fontWeight: 600 }}>{c.winningUser} (Winner)</span>
                      <ArrowRight size={12} color="#64748b" />
                      <span style={{ color: '#f43f5e', textDecoration: 'line-through' }}>{c.rejectedUser} (Overwritten)</span>
                    </div>

                    <div style={{
                      fontSize: '0.7rem',
                      color: '#94a3b8',
                      background: '#090d16',
                      padding: '6px 8px',
                      borderRadius: '4px',
                      fontFamily: 'monospace',
                    }}>
                      {c.resolutionReason}
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '8px', fontSize: '0.68rem', color: '#64748b' }}>
                      <span>Audit ID: {c.id.substring(0, 8)}</span>
                      <span>{new Date(c.createdAt).toLocaleTimeString()}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
};
