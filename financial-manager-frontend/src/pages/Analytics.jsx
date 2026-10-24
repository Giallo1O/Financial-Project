import React, { useState, useEffect, useMemo } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';
import { ChevronLeft, ChevronRight, BarChart3, Filter, ArrowUpRight, ArrowDownLeft } from 'lucide-react';
import { fetchTransactions } from '../api';
import DailyCategoryChart from '../components/DailyCategoryChart';
import { eur } from '../utils/format';

const Analytics = () => {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);

  // Date state for month selection
  const [currentDate, setCurrentDate] = useState(new Date());

  // Filter state
  const [showIncome, setShowIncome] = useState(true);
  const [showExpense, setShowExpense] = useState(true);

  // Tab state
  const [activeTab, setActiveTab] = useState('overview'); // 'overview' or 'daily'

  // Daily view type (expense or income) - synced with sidebar
  const [dailyViewType, setDailyViewType] = useState('expense'); 

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

  const monthYearStr = currentDate.toLocaleDateString('it-IT', { month: 'long', year: 'numeric' });

  // Transactions are already filtered by month from the backend
  // Just group by category
  const chartData = useMemo(() => {
    const categoryMap = {};
    transactions.forEach(tx => {
      const cat = tx.category || 'Non categorizzato';
      if (!categoryMap[cat]) {
        categoryMap[cat] = { name: cat, expense: 0, income: 0 };
      }
      if (tx.type === 'uscita') {
        categoryMap[cat].expense += Number(tx.amount);
      } else {
        categoryMap[cat].income += Number(tx.amount);
      }
    });

    return Object.values(categoryMap)
      .filter(item => {
        // Keep category if it has any data of the enabled types
        const hasVisibleData = (showIncome && item.income > 0) || (showExpense && item.expense > 0);
        return hasVisibleData;
      })
      .sort((a, b) => b.expense - a.expense || b.income - a.income);
  }, [transactions, showIncome, showExpense]);

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
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
            <h1 style={{ fontSize: '1.8rem', marginBottom: '0.2rem' }}>Analisi</h1>
            <p className="text-muted text-sm">Analizza le tue entrate e uscite per categoria.</p>
          </div>
        </div>

        {/* Month Selector Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', background: 'rgba(28,22,20,0.6)', padding: '0.5rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-glass)' }}>
          <button className="btn btn-glass" style={{ padding: '0.5rem' }} onClick={() => changeMonth(-1)}>
            <ChevronLeft size={20} />
          </button>
          <span style={{ minWidth: '150px', textAlign: 'center', fontWeight: '600', fontSize: '1.1rem', textTransform: 'capitalize' }}>
            {monthYearStr}
          </span>
          <button className="btn btn-glass" style={{ padding: '0.5rem' }} onClick={() => changeMonth(1)} disabled={currentDate >= new Date()}>
            <ChevronRight size={20} />
          </button>
        </div>
      </header>

      <div className="stack-mobile" style={{ display: 'grid', gridTemplateColumns: '250px 1fr', gap: '2rem', alignItems: 'start' }}>
        
        {/* Sidebar Filters */}
        <aside className="glass-panel" style={{ padding: '1.5rem', position: 'sticky', top: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem', color: 'var(--accent-secondary)' }}>
            <Filter size={18} />
            <h3 style={{ margin: 0, fontSize: '1.1rem' }}>Filtri</h3>
          </div>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {activeTab === 'overview' ? (
              <>
                <label style={{ 
                  display: 'flex', alignItems: 'center', gap: '0.75rem', cursor: 'pointer',
                  padding: '0.75rem', borderRadius: '8px', 
                  background: showIncome ? 'rgba(16, 185, 129, 0.1)' : 'transparent',
                  border: `1px solid ${showIncome ? 'var(--success)' : 'var(--border-glass)'}`,
                  transition: 'all 0.2s'
                }}>
                  <input 
                    type="checkbox" 
                    checked={showIncome} 
                    onChange={(e) => setShowIncome(e.target.checked)}
                    style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: 'var(--success)' }}
                  />
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <ArrowDownLeft size={16} color="var(--success)" />
                    <span style={{ fontWeight: 500, color: showIncome ? 'var(--text-primary)' : 'var(--text-muted)' }}>Entrate</span>
                  </div>
                </label>

                <label style={{ 
                  display: 'flex', alignItems: 'center', gap: '0.75rem', cursor: 'pointer',
                  padding: '0.75rem', borderRadius: '8px', 
                  background: showExpense ? 'rgba(239, 68, 68, 0.1)' : 'transparent',
                  border: `1px solid ${showExpense ? 'var(--danger)' : 'var(--border-glass)'}`,
                  transition: 'all 0.2s'
                }}>
                  <input 
                    type="checkbox" 
                    checked={showExpense} 
                    onChange={(e) => setShowExpense(e.target.checked)}
                    style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: 'var(--danger)' }}
                  />
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <ArrowUpRight size={16} color="var(--danger)" />
                    <span style={{ fontWeight: 500, color: showExpense ? 'var(--text-primary)' : 'var(--text-muted)' }}>Uscite</span>
                  </div>
                </label>
              </>
            ) : (
              <>
                <label 
                  onClick={() => setDailyViewType('income')}
                  style={{ 
                    display: 'flex', alignItems: 'center', gap: '0.75rem', cursor: 'pointer',
                    padding: '0.75rem', borderRadius: '8px', 
                    background: dailyViewType === 'income' ? 'rgba(16, 185, 129, 0.1)' : 'transparent',
                    border: `1px solid ${dailyViewType === 'income' ? 'var(--success)' : 'var(--border-glass)'}`,
                    transition: 'all 0.2s'
                  }}
                >
                  <input 
                    type="radio" 
                    name="dailyType"
                    checked={dailyViewType === 'income'} 
                    readOnly
                    style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: 'var(--success)' }}
                  />
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <ArrowDownLeft size={16} color="var(--success)" />
                    <span style={{ fontWeight: 500, color: dailyViewType === 'income' ? 'var(--text-primary)' : 'var(--text-muted)' }}>Solo Entrate</span>
                  </div>
                </label>

                <label 
                  onClick={() => setDailyViewType('expense')}
                  style={{ 
                    display: 'flex', alignItems: 'center', gap: '0.75rem', cursor: 'pointer',
                    padding: '0.75rem', borderRadius: '8px', 
                    background: dailyViewType === 'expense' ? 'rgba(239, 68, 68, 0.1)' : 'transparent',
                    border: `1px solid ${dailyViewType === 'expense' ? 'var(--danger)' : 'var(--border-glass)'}`,
                    transition: 'all 0.2s'
                  }}
                >
                  <input 
                    type="radio" 
                    name="dailyType"
                    checked={dailyViewType === 'expense'} 
                    readOnly
                    style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: 'var(--danger)' }}
                  />
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <ArrowUpRight size={16} color="var(--danger)" />
                    <span style={{ fontWeight: 500, color: dailyViewType === 'expense' ? 'var(--text-primary)' : 'var(--text-muted)' }}>Solo Uscite</span>
                  </div>
                </label>
              </>
            )}
          </div>

          <div style={{ marginTop: '2rem', padding: '1rem', borderTop: '1px solid var(--border-glass)', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            <p>Seleziona cosa mostrare nel grafico per confrontare meglio i flussi di cassa.</p>
          </div>
        </aside>

        {/* Main Content Area */}
        <main>
          {loading ? (
            <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
              <p className="text-muted">Caricamento dati...</p>
            </div>
          ) : (
            <div>
              {/* Tabs for switching views - ALWAYS VISIBLE if not loading */}
              <div style={{ display: 'flex', gap: '1rem', marginBottom: '2rem', borderBottom: '1px solid var(--border-glass)', paddingBottom: '1rem' }}>
                <button 
                  onClick={() => setActiveTab('overview')}
                  style={{
                    padding: '0.75rem 1.5rem',
                    background: activeTab === 'overview' ? 'var(--accent-primary)' : 'transparent',
                    color: activeTab === 'overview' ? 'white' : 'var(--text-muted)',
                    border: 'none',
                    borderRadius: 'var(--radius-md)',
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    boxShadow: activeTab === 'overview' ? 'var(--shadow-glow)' : 'none'
                  }}
                >
                  Vista Generale
                </button>
                <button 
                  onClick={() => setActiveTab('daily')}
                  style={{
                    padding: '0.75rem 1.5rem',
                    background: activeTab === 'daily' ? 'var(--accent-primary)' : 'transparent',
                    color: activeTab === 'daily' ? 'white' : 'var(--text-muted)',
                    border: 'none',
                    borderRadius: 'var(--radius-md)',
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    boxShadow: activeTab === 'daily' ? 'var(--shadow-glow)' : 'none'
                  }}
                >
                  Vista Giornaliera
                </button>
              </div>

              {activeTab === 'overview' ? (
                // OVERVIEW TAB CONTENT
                chartData.length === 0 ? (
                  <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
                    <h3 style={{ marginBottom: '0.5rem' }}>Nessun Dato Disponibile</h3>
                    <p className="text-muted">Non ci sono transazioni per {monthYearStr} che corrispondano ai filtri selezionati.</p>
                  </div>
                ) : (
                  <div className="glass-panel" style={{ padding: '2rem' }}>
                    <h3 className="section-title" style={{ marginBottom: '2rem', textAlign: 'center' }}>
                      Flusso di Cassa per Categoria
                    </h3>
                    <div style={{ height: '500px', width: '100%' }}>
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart
                          data={chartData}
                          margin={{ top: 20, right: 30, left: 20, bottom: 60 }}
                        >
                          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" vertical={false} />
                          <XAxis 
                            dataKey="name" 
                            tick={{fill: 'var(--text-muted)', fontSize: 12}} 
                            axisLine={{stroke: 'var(--border-glass)'}} 
                            angle={-45}
                            textAnchor="end"
                            interval={0}
                          />
                          <YAxis tick={{fill: 'var(--text-muted)'}} axisLine={{stroke: 'var(--border-glass)'}} tickFormatter={(value) => `€${value}`} />
                          <Tooltip 
                            cursor={{fill: 'rgba(197, 160, 40, 0.08)'}}
                            contentStyle={{ 
                              backgroundColor: 'var(--bg-secondary)', 
                              border: '1px solid var(--border-glass)',
                              borderRadius: 'var(--radius-md)',
                              color: 'white',
                              boxShadow: 'var(--shadow-glass)'
                            }}
                            formatter={(value) => [`${eur(value)}`, undefined]}
                          />
                          <Legend verticalAlign="top" height={36} />
                          {showIncome && <Bar dataKey="income" name="Entrate" fill="var(--success)" radius={[4, 4, 0, 0]} barSize={32} />}
                          {showExpense && <Bar dataKey="expense" name="Uscite" fill="var(--danger)" radius={[4, 4, 0, 0]} barSize={32} />}
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                )
              ) : (
                // DAILY TAB CONTENT
                <DailyCategoryChart 
                  transactions={transactions} 
                  currentDate={currentDate} 
                  viewType={dailyViewType}
                />
              )}
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export default Analytics;
