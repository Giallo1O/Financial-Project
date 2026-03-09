import React, { useState, useEffect } from 'react';
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
    if (!window.confirm('Delete this transaction?')) return;
    try {
      await deleteTransaction(id);
      setTransactions((prev) => prev.filter((tx) => tx.id !== id));
    } catch (err) {
      console.error('Failed to delete transaction', err);
      alert(`Error: ${err.message}`);
    }
  };

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto' }}>
      <header style={{ marginBottom: '2rem' }}>
        <h1 style={{ marginBottom: '0.5rem' }}>Transactions</h1>
        <p className="text-muted">Manage your income and expenses.</p>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr)', gap: '2rem' }}>
        <section>
          <TransactionsForm onAddTransaction={handleAddTransaction} />
        </section>

        <section>
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
