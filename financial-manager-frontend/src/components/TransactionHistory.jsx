import React, { useState, useEffect } from 'react';
import { Trash2 } from 'lucide-react';
import './Components.css';

const TransactionHistory = ({ transactions, loading, onDelete }) => {
  if (loading) {
    return (
      <div className="glass-panel" style={{ padding: '2rem', textAlign: 'center' }}>
        <p className="text-muted">Loading transactions...</p>
      </div>
    );
  }

  if (!transactions || transactions.length === 0) {
    return (
      <div className="glass-panel" style={{ padding: '2rem', textAlign: 'center' }}>
        <p className="text-muted">No transactions found.</p>
      </div>
    );
  }

  return (
    <div className="glass-panel" style={{ padding: '1.5rem', overflowX: 'auto' }}>
      <h3 className="section-title" style={{ marginBottom: '1rem' }}>Recent History</h3>
      <table className="history-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ borderBottom: '1px solid var(--border-glass)', textAlign: 'left' }}>
            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)' }}>Date</th>
            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)' }}>Description</th>
            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)' }}>Category</th>
            <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', textAlign: 'right' }}>Amount</th>
            {onDelete && (
              <th style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', textAlign: 'center' }}>Action</th>
            )}
          </tr>
        </thead>
        <tbody>
          {transactions.map((tx) => {
            const isIncome = tx.type === 'entrata';
            return (
              <tr key={tx.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <td style={{ padding: '1rem' }}>{new Date(tx.date).toLocaleDateString()}</td>
                <td style={{ padding: '1rem' }}>{tx.description || '-'}</td>
                <td style={{ padding: '1rem' }}>
                  <span style={{
                    padding: '0.25rem 0.75rem',
                    borderRadius: 'var(--radius-full)',
                    backgroundColor: 'rgba(255,255,255,0.1)',
                    fontSize: '0.875rem'
                  }}>
                    {tx.category}
                  </span>
                </td>
                <td style={{
                  padding: '1rem',
                  textAlign: 'right',
                  fontWeight: '600',
                  color: isIncome ? 'var(--success)' : 'var(--danger)'
                }}>
                  {isIncome ? '+' : '-'}€{Math.abs(tx.amount).toLocaleString('it-IT', { minimumFractionDigits: 2 })}
                </td>
                {onDelete && (
                  <td style={{ padding: '1rem', textAlign: 'center' }}>
                    <button
                      onClick={() => onDelete(tx.id)}
                      title="Delete transaction"
                      style={{
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        color: 'var(--danger)',
                        opacity: 0.7,
                        transition: 'opacity 0.2s',
                        padding: '0.25rem',
                        borderRadius: '4px',
                        display: 'inline-flex',
                        alignItems: 'center',
                      }}
                      onMouseEnter={e => e.currentTarget.style.opacity = '1'}
                      onMouseLeave={e => e.currentTarget.style.opacity = '0.7'}
                    >
                      <Trash2 size={16} />
                    </button>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default TransactionHistory;
