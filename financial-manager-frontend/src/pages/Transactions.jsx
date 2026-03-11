import React, { useState, useEffect } from 'react';
import { Wallet } from 'lucide-react';
import TransactionsForm from '../components/TransactionsForm';
import TransactionHistory from '../components/TransactionHistory';
import { fetchTransactions, createTransaction, deleteTransaction } from '../api';

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

  const handleAddTransaction = async (newTransaction) => {
    try {
      const created = await createTransaction(newTransaction);
      setTransactions((prev) => [created, ...prev].sort((a, b) => new Date(b.date) - new Date(a.date)));
    } catch (err) {
      console.error('Failed to add transaction', err);
      alert(`Error: ${err.message}`);
    }
  };

  const handleDeleteTransaction = async (id) => {
    if (!window.confirm('Cancellare questa transazione?')) return;
    try {
      await deleteTransaction(id);
      setTransactions((prev) => prev.filter((tx) => tx.id !== id));
    } catch (err) {
      console.error('Failed to delete transaction', err);
      alert(`Error: ${err.message}`);
    }
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
      <header className="dashboard-header" style={{ marginBottom: '2rem' }}>
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
      </header>

      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))', 
        gap: '2rem',
        alignItems: 'start'
      }}>
        <section>
          <TransactionsForm onAddTransaction={handleAddTransaction} />
        </section>

        <section style={{ minWidth: 0 }}>
          <TransactionHistory
            transactions={transactions}
            loading={loading}
            onDelete={handleDeleteTransaction}
          />
        </section>
      </div>
    </div>
  );
};

export default Transactions;
