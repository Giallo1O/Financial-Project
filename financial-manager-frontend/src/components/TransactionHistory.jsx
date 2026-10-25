import React from 'react';
import { Trash2, Pencil, History, AlertCircle } from 'lucide-react';
import './Components.css';
import { eur } from '../utils/format';

const TransactionHistory = ({ transactions, loading, onDelete, onEdit, accountNames = {} }) => {
  if (loading) {
    return (
      <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
        <div className="spinner-container">
          <div className="spinner" />
        </div>
        <p className="text-muted" style={{ marginTop: '1rem' }}>Caricamento transazioni...</p>
      </div>
    );
  }

  if (!transactions || transactions.length === 0) {
    return (
      <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
        <AlertCircle size={40} className="text-muted" style={{ marginBottom: '1rem', opacity: 0.5 }} />
        <h3 style={{ marginBottom: '0.5rem' }}>Nessuna transazione</h3>
        <p className="text-muted">Non hai ancora registrato movimenti per questo periodo.</p>
      </div>
    );
  }

  return (
    <div className="glass-panel" style={{ padding: '1.5rem', overflow: 'hidden' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
        <History size={20} className="text-gradient-icon" />
        <h3 className="section-title" style={{ margin: 0, color: 'var(--text-primary)', textTransform: 'none', letterSpacing: 'normal', fontSize: '1.25rem' }}>
          Cronologia Recente
        </h3>
      </div>
      
      <div className="history-wrap">
        <table className="history-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--border-glass)', textAlign: 'left' }}>
              <th style={{ padding: '1rem', color: 'var(--text-muted)', fontWeight: 500, fontSize: '0.85rem' }}>DATA</th>
              <th style={{ padding: '1rem', color: 'var(--text-muted)', fontWeight: 500, fontSize: '0.85rem' }}>DESCRIZIONE</th>
              <th style={{ padding: '1rem', color: 'var(--text-muted)', fontWeight: 500, fontSize: '0.85rem' }}>CATEGORIA</th>
              <th style={{ padding: '1rem', color: 'var(--text-muted)', fontWeight: 500, fontSize: '0.85rem', textAlign: 'right' }}>IMPORTO</th>
              {onDelete && (
                <th style={{ padding: '1rem', color: 'var(--text-muted)', fontWeight: 500, fontSize: '0.85rem', textAlign: 'center' }}>AZIONI</th>
              )}
            </tr>
          </thead>
          <tbody>
            {transactions.map((tx) => {
              const isIncome = tx.type === 'entrata';
              return (
                <tr key={tx.id} className="history-row">
                  <td className="h-date" style={{ padding: '1rem', whiteSpace: 'nowrap' }}>
                    {new Date(tx.date).toLocaleDateString('it-IT')}
                  </td>
                  <td className="h-desc" style={{ padding: '1rem' }}>
                    <div style={{ fontWeight: 500 }}>{tx.description || 'Senza descrizione'}</div>
                    {accountNames[tx.accountId] && <div className="text-muted text-sm">{accountNames[tx.accountId]}</div>}
                  </td>
                  <td className="h-cat" style={{ padding: '1rem' }}>
                    <span className="category-badge">
                      {tx.category}
                    </span>
                  </td>
                  <td className="h-amount" style={{
                    padding: '1rem',
                    textAlign: 'right',
                    fontWeight: '700',
                    color: isIncome ? 'var(--success)' : 'var(--danger)',
                    whiteSpace: 'nowrap'
                  }}>
                    {isIncome ? '+' : '-'}{eur(Math.abs(tx.amount))}
                  </td>
                  {onDelete && (
                    <td className="h-actions" style={{ padding: '1rem', textAlign: 'center', whiteSpace: 'nowrap' }}>
                      {onEdit && (
                        <button onClick={() => onEdit(tx)} className="delete-btn edit-btn" title="Modifica movimento">
                          <Pencil size={16} />
                        </button>
                      )}
                      <button
                        onClick={() => onDelete(tx.id)}
                        className="delete-btn"
                        title="Elimina transazione"
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
    </div>
  );
};

export default TransactionHistory;
