import React, { useState, useEffect } from 'react';
import { Wallet, FileSpreadsheet, FileText } from 'lucide-react';
import TransactionsForm from '../components/TransactionsForm';
import TransactionHistory from '../components/TransactionHistory';
import { fetchTransactions, createTransaction, updateTransaction, deleteTransaction } from '../api';
import { exportTransactionsToExcel, exportTransactionsToPdf } from '../utils/exportUtils';

const Transactions = () => {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        const txs = await fetchTransactions();
        setTransactions(txs.sort((a, b) => new Date(b.date) - new Date(a.date)));
      } catch (err) {
        console.error('Failed to fetch transactions', err);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  const [editing, setEditing] = useState(null);

  const handleAddTransaction = async (data) => {
    try {
      const saved = editing ? await updateTransaction(editing.id, data) : await createTransaction(data);
      setTransactions((prev) => [saved, ...prev.filter((tx) => tx.id !== saved.id)]
        .sort((a, b) => new Date(b.date) - new Date(a.date)));
      setEditing(null);
    } catch (err) {
      console.error('Failed to save transaction', err);
      alert(`Errore: ${err.message}`);
    }
  };

  const handleEdit = (tx) => {
    setEditing(tx);
    document.querySelector('.form-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const handleDeleteTransaction = async (id) => {
    if (!window.confirm('Cancellare questa transazione?')) return;
    try {
      await deleteTransaction(id);
      setTransactions((prev) => prev.filter((tx) => tx.id !== id));
    } catch (err) {
      console.error('Failed to delete transaction', err);
      alert(`Errore: ${err.message}`);
    }
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
      <header className="dashboard-header" style={{ marginBottom: '2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{ 
            width: '50px', height: '50px', 
            background: 'var(--accent-gradient)', 
            borderRadius: 'var(--radius-md)', 
            display: 'flex', alignItems: 'center', justifyContent: 'center' 
          }}>
            <Wallet size={24} color="white" />
          </div>
          <div>
            <h1 style={{ fontSize: '1.8rem', marginBottom: '0.2rem' }}>Transazioni</h1>
            <p className="text-muted text-sm">Gestisci le tue entrate e uscite in modo semplice.</p>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <button
            onClick={() => exportTransactionsToExcel(transactions)}
            className="btn-export"
            title="Esporta in foglio Excel (.xlsx)"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.6rem 1rem',
              background: 'rgba(16, 185, 129, 0.15)',
              border: '1px solid rgba(16, 185, 129, 0.4)',
              borderRadius: 'var(--radius-md)',
              color: '#34d399',
              cursor: 'pointer',
              fontWeight: 500,
              fontSize: '0.875rem'
            }}
          >
            <FileSpreadsheet size={18} />
            <span>Esporta Excel</span>
          </button>

          <button
            onClick={() => exportTransactionsToPdf(transactions)}
            className="btn-export"
            title="Esporta estratto conto in PDF (.pdf)"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.6rem 1rem',
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid rgba(239, 68, 68, 0.4)',
              borderRadius: 'var(--radius-md)',
              color: '#f87171',
              cursor: 'pointer',
              fontWeight: 500,
              fontSize: '0.875rem'
            }}
          >
            <FileText size={18} />
            <span>Esporta PDF</span>
          </button>
        </div>
      </header>


      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 350px), 1fr))', 
        gap: '2rem',
        alignItems: 'start'
      }}>
        <section>
          <TransactionsForm key={editing?.id || 'new'} onAddTransaction={handleAddTransaction} editing={editing} onCancelEdit={() => setEditing(null)} />
        </section>

        <section style={{ minWidth: 0 }}>
          <TransactionHistory
            transactions={transactions}
            loading={loading}
            onDelete={handleDeleteTransaction}
            onEdit={handleEdit}
          />
        </section>
      </div>
    </div>
  );
};

export default Transactions;
