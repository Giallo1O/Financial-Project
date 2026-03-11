import React from 'react';
import { Trash2, History, AlertCircle } from 'lucide-react';
import './Components.css';

const TransactionHistory = ({ transactions, loading, onDelete }) => {
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
      
      <div style={{ overflowX: 'auto' }}>
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
                  <td style={{ padding: '1rem', whiteSpace: 'nowrap' }}>
                    {new Date(tx.date).toLocaleDateString('it-IT')}
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <div style={{ fontWeight: 500 }}>{tx.description || 'Senza descrizione'}</div>
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <span className="category-badge">
                      {tx.category}
                    </span>
                  </td>
                  <td style={{
                    padding: '1rem',
                    textAlign: 'right',
                    fontWeight: '700',
                    color: isIncome ? 'var(--success)' : 'var(--danger)',
                    whiteSpace: 'nowrap'
                  }}>
                    {isIncome ? '+' : '-'} €{Math.abs(tx.amount).toLocaleString('it-IT', { minimumFractionDigits: 2 })}
                  </td>
                  {onDelete && (
                    <td style={{ padding: '1rem', textAlign: 'center' }}>
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
