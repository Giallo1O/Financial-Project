import React, { useState, useEffect, useMemo } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';
import { ChevronLeft, ChevronRight, BarChart3 } from 'lucide-react';
import { fetchTransactions } from '../api';

const Analytics = () => {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);

  // Date state for month selection
  const [currentDate, setCurrentDate] = useState(new Date());

  // Re-fetch from backend every time the selected month changes
  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        const month = currentDate.getMonth() + 1; // JS months are 0-indexed
        const year = currentDate.getFullYear();
        const txs = await fetchTransactions({ month, year });
        setTransactions(txs);
      } catch (err) {
        console.error('Failed to fetch transactions', err);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, [currentDate]);


  const changeMonth = (offset) => {
    setCurrentDate((prev) => {
      const newDate = new Date(prev);
      newDate.setMonth(prev.getMonth() + offset);
      return newDate;
    });
  };

  const monthYearStr = currentDate.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

  // Transactions are already filtered by month from the backend
  // Just group by category
  const chartData = useMemo(() => {
    const categoryMap = {};
    transactions.forEach(tx => {
      const cat = tx.category || 'Uncategorized';
      if (!categoryMap[cat]) {
        categoryMap[cat] = { name: cat, expense: 0, income: 0 };
      }
      if (tx.type === 'uscita') {
        categoryMap[cat].expense += Number(tx.amount);
      } else {
        categoryMap[cat].income += Number(tx.amount);
      }
    });

    return Object.values(categoryMap).sort((a, b) => b.expense - a.expense || b.income - a.income);
  }, [transactions]);

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto' }}>
      <header className="dashboard-header" style={{ marginBottom: '2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border-glass)', paddingBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{ 
            width: '50px', height: '50px', 
            background: 'var(--accent-gradient)', 
            borderRadius: 'var(--radius-md)', 
            display: 'flex', alignItems: 'center', justifyContent: 'center' 
          }}>
            <BarChart3 size={24} color="white" />
          </div>
          <div>
            <h1 style={{ fontSize: '1.8rem', marginBottom: '0.2rem' }}>Analytics</h1>
            <p className="text-muted text-sm">Review your categorize incomes and expenses.</p>
          </div>
        </div>

        {/* Month Selector Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', background: 'rgba(15,23,42,0.6)', padding: '0.5rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-glass)' }}>
          <button className="btn btn-glass" style={{ padding: '0.5rem' }} onClick={() => changeMonth(-1)}>
            <ChevronLeft size={20} />
          </button>
          <span style={{ minWidth: '150px', textAlign: 'center', fontWeight: '600', fontSize: '1.1rem' }}>
            {monthYearStr}
          </span>
          <button className="btn btn-glass" style={{ padding: '0.5rem' }} onClick={() => changeMonth(1)} disabled={currentDate > new Date()}>
            <ChevronRight size={20} />
          </button>
        </div>
      </header>

      {loading ? (
        <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
          <p className="text-muted">Loading analytics data...</p>
        </div>
      ) : chartData.length === 0 ? (
        <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
          <h3 style={{ marginBottom: '0.5rem' }}>No Data Available</h3>
          <p className="text-muted">You have no transactions recorded for {monthYearStr}.</p>
        </div>
      ) : (
        <div className="glass-panel" style={{ padding: '2rem' }}>
          <h3 className="section-title" style={{ marginBottom: '2rem', textAlign: 'center' }}>
            Cash Flow by Category
          </h3>
          <div style={{ height: '400px', width: '100%' }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={chartData}
                margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" vertical={false} />
                <XAxis dataKey="name" tick={{fill: 'var(--text-muted)'}} axisLine={{stroke: 'var(--border-glass)'}} />
                <YAxis tick={{fill: 'var(--text-muted)'}} axisLine={{stroke: 'var(--border-glass)'}} tickFormatter={(value) => `$${value}`} />
                <Tooltip 
                  cursor={{fill: 'rgba(255,255,255,0.05)'}}
                  contentStyle={{ 
                    backgroundColor: 'var(--bg-secondary)', 
                    border: '1px solid var(--border-glass)',
                    borderRadius: 'var(--radius-md)',
                    color: 'white'
                  }}
                  formatter={(value) => [`$${value.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, undefined]}
                />
                <Legend wrapperStyle={{ paddingTop: '20px' }} />
                <Bar dataKey="income" name="Income" fill="var(--success)" radius={[4, 4, 0, 0]} barSize={40} />
                <Bar dataKey="expense" name="Expense" fill="var(--danger)" radius={[4, 4, 0, 0]} barSize={40} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
    </div>
  );
};

export default Analytics;
