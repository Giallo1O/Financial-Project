import React, { useState, useEffect } from 'react';
import { LayoutDashboard } from 'lucide-react';
import AccountSummary from '../components/AccountSummary';
import CategoryPieChart from '../components/CategoryPieChart';
import { fetchTransactions, fetchCategories } from '../api';

const Dashboard = () => {
  const [transactions, setTransactions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        const now = new Date();
        const currentMonth = now.getMonth();
        const currentYear = now.getFullYear();

        const [txs, cats] = await Promise.all([
          fetchTransactions(),
          fetchCategories(),
        ]);

        const currentMonthTxs = txs.filter((tx) => {
          const txDate = new Date(tx.date);
          return txDate.getMonth() === currentMonth && txDate.getFullYear() === currentYear;
        });

        setCategories(cats);
        // Sort descending
        setTransactions(currentMonthTxs.sort((a, b) => new Date(b.date) - new Date(a.date)));
      } catch (err) {
        console.error('Unexpected error loading dashboard data', err);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  // Helper to aggregate stats by category
  const getCategoryData = (type) => {
    const filteredTxs = transactions.filter(t => t.type === type);
    const totals = {};

    filteredTxs.forEach(tx => {
      if (!totals[tx.categoryId]) {
        totals[tx.categoryId] = 0;
      }
      totals[tx.categoryId] += tx.amount;
    });

    return Object.keys(totals).map(catId => {
      const category = categories.find(c => c.id === catId);
      return {
        name: category ? category.name : 'Unknown',
        value: totals[catId],
        color: category ? category.color : '#cbd5e1'
      };
    }).sort((a, b) => b.value - a.value);
  };

  const expenseData = getCategoryData('uscita');
  const incomeData = getCategoryData('entrata');

  return (
    <div className="dashboard-layout" style={{ maxWidth: '1200px', margin: '0 auto' }}>
      <header className="dashboard-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{
            width: '50px', height: '50px',
            background: 'var(--accent-gradient)',
            borderRadius: 'var(--radius-md)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <LayoutDashboard size={24} color="white" />
          </div>
          <div>
            <h1 style={{ fontSize: '1.8rem', marginBottom: '0.2rem' }}>Portfolio Overview</h1>
            <p className="text-muted text-sm">Welcome back. Here's your summary for this month.</p>
          </div>
        </div>
      </header>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '4rem', color: 'var(--text-muted)' }}>
          <div
            className="spinner"
            style={{
              margin: '0 auto', marginBottom: '1rem',
              width: '40px', height: '40px',
              border: '3px solid var(--border-glass)',
              borderTopColor: 'var(--primary)',
              borderRadius: '50%',
              animation: 'spin 1s linear infinite',
            }}
          />
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
          <h2>Loading portfolio data...</h2>
        </div>
      ) : (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '1.5rem',
          }}
        >
          {/* Top — cash flow summary (Full Width) */}
          <section>
            <AccountSummary transactions={transactions} />
          </section>

          {/* Bottom — charts (Two Columns) */}
          <section
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))',
              gap: '1.5rem',
            }}
          >
            <CategoryPieChart 
              data={incomeData} 
              title="Entrate per Categoria" 
              subtitle="Riepilogo delle entrate mensili"
              totalColor="var(--success)"
            />
            <CategoryPieChart 
              data={expenseData} 
              title="Spese per Categoria" 
              subtitle="Riepilogo delle uscite mensili"
              totalColor="var(--danger)"
            />
          </section>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
