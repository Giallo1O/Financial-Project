import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LayoutDashboard } from 'lucide-react';
import AccountSummary from '../components/AccountSummary';
import CategoryPieChart from '../components/CategoryPieChart';
import { fetchTransactions, fetchDashboardStats, fetchCategories } from '../api';

const Dashboard = () => {
  const navigate = useNavigate();
  const [transactions, setTransactions] = useState([]);
  const [categoryStats, setCategoryStats] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [txs, dashboardStats, categories] = await Promise.all([
          fetchTransactions(),
          fetchDashboardStats(),
          fetchCategories(),
        ]);

        // Filter transactions for only the CURRENT MONTH
        const now = new Date();
        const currentMonth = now.getMonth();
        const currentYear = now.getFullYear();

        const currentMonthTxs = txs.filter((tx) => {
          const txDate = new Date(tx.date);
          return txDate.getMonth() === currentMonth && txDate.getFullYear() === currentYear;
        });

        // Sort descending
        setTransactions(currentMonthTxs.sort((a, b) => new Date(b.date) - new Date(a.date)));
        // Build id→name map for category pie chart
        const categoryMap = Object.fromEntries(categories.map(c => [c.id, c.name]));
        const mappedStats = dashboardStats.map(stat => ({
          ...stat,
          categoryId: categoryMap[stat.categoryId] || stat.categoryId || '—'
        }));

        setCategoryStats(mappedStats);
      } catch (err) {
        // Unexpected error loading dashboard data (e.g. backend down)
        console.error('Unexpected error loading dashboard data', err);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

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
          <p>Connecting to backend services</p>
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: '1.5rem',
            alignItems: 'start',
          }}
        >
          {/* Left — cash flow summary */}
          <section>
            <AccountSummary transactions={transactions} />
          </section>

          {/* Right — categories breakdown */}
          <section>
            <CategoryPieChart categories={categoryStats} />
          </section>
        </div>
      )}

    </div>
  );
};

export default Dashboard;
