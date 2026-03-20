import React, { useState, useEffect, useMemo } from 'react';
import { 
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine, Cell, ComposedChart
} from 'recharts';
import { 
  ChevronLeft, ChevronRight, PiggyBank, AlertTriangle, CheckCircle, ArrowUpRight, TrendingUp, TrendingDown, Wallet, Calendar, Info
} from 'lucide-react';
import { fetchCategories, fetchTransactions } from '../api';

const Budgets = () => {
  const [categories, setCategories] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentDate, setCurrentDate] = useState(new Date());

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        const month = currentDate.getMonth() + 1;
        const year = currentDate.getFullYear();
        const [cats, txs] = await Promise.all([
          fetchCategories(),
          fetchTransactions({ month, year })
        ]);
        // Only show expense categories that have a budget set
        setCategories(cats.filter(c => c.expense && Number(c.budget) > 0));
        // Only consider expense transactions
        setTransactions(txs.filter(tx => tx.type === 'uscita'));
      } catch (err) {
        console.error('Failed to fetch budget data', err);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, [currentDate]);

  const budgetStats = useMemo(() => {
    const spendingMap = {};
    transactions.forEach(tx => {
      spendingMap[tx.categoryId] = (spendingMap[tx.categoryId] || 0) + Number(tx.amount);
    });

    const data = categories.map(cat => {
      const spent = spendingMap[cat.id] || 0;
      const budget = Number(cat.budget);
      const remaining = budget - spent;
      const percent = (spent / budget) * 100;
      
      let status = 'good';
      if (percent >= 100) status = 'danger';
      else if (percent >= 85) status = 'warning';

      return {
        ...cat,
        spent,
        budget,
        remaining,
        percent,
        status
      };
    }).sort((a, b) => b.percent - a.percent);

    const totalBudget = data.reduce((sum, item) => sum + item.budget, 0);
    const totalSpent = data.reduce((sum, item) => sum + item.spent, 0);
    const totalPercent = totalBudget > 0 ? (totalSpent / totalBudget) * 100 : 0;

    return {
      items: data,
      totalBudget,
      totalSpent,
      totalPercent
    };
  }, [categories, transactions]);

  const changeMonth = (offset) => {
    setCurrentDate(prev => {
      const d = new Date(prev);
      d.setMonth(d.getMonth() + offset);
      return d;
    });
  };

  const monthYearStr = currentDate.toLocaleDateString('it-IT', { month: 'long', year: 'numeric' });

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
        <div className="spinner"></div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', animation: 'fadeIn 0.5s ease-out' }}>
      {/* Header */}
      <header className="dashboard-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{ 
            width: '50px', height: '50px', 
            background: 'var(--accent-gradient)', 
            borderRadius: 'var(--radius-md)', 
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'var(--shadow-glow)'
          }}>
            <PiggyBank size={26} color="white" />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Budget Mensili</h1>
            <p className="text-muted text-sm">Monitora le tue spese rispetto ai limiti impostati.</p>
          </div>
        </div>

        {/* Month Selector */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', background: 'rgba(28,22,20,0.6)', padding: '0.5rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-glass)' }}>
          <button className="btn btn-glass" style={{ padding: '0.5rem' }} onClick={() => changeMonth(-1)}>
            <ChevronLeft size={20} />
          </button>
          <span style={{ minWidth: '150px', textAlign: 'center', fontWeight: '600', fontSize: '1.1rem', textTransform: 'capitalize' }}>
            {monthYearStr}
          </span>
          <button className="btn btn-glass" style={{ padding: '0.5rem' }} onClick={() => changeMonth(1)} disabled={currentDate >= new Date(new Date().getFullYear(), new Date().getMonth(), 1)}>
            <ChevronRight size={20} />
          </button>
        </div>
      </header>

      {/* Overview Card */}
      <div className="glass-panel" style={{ padding: '2rem', marginBottom: '2rem', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '2rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <span className="text-muted text-sm" style={{ textTransform: 'uppercase', letterSpacing: '0.1em' }}>Budget Totale</span>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5rem' }}>
            <h2 style={{ fontSize: '2.5rem', margin: 0 }}>€{budgetStats.totalBudget.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</h2>
          </div>
        </div>
        
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <span className="text-muted text-sm" style={{ textTransform: 'uppercase', letterSpacing: '0.1em' }}>Spesa Totale (Categorie a Budget)</span>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5rem' }}>
            <h2 style={{ fontSize: '2.5rem', margin: 0, color: budgetStats.totalPercent > 100 ? 'var(--danger)' : 'var(--text-primary)' }}>
              €{budgetStats.totalSpent.toLocaleString('it-IT', { minimumFractionDigits: 2 })}
            </h2>
            <span style={{ fontSize: '1.2rem', color: 'var(--text-muted)' }}>/ {budgetStats.totalPercent.toFixed(0)}%</span>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
           <div style={{ width: '100%', height: '12px', background: 'rgba(255,255,255,0.05)', borderRadius: '6px', overflow: 'hidden', border: '1px solid var(--border-glass)' }}>
              <div style={{ 
                width: `${Math.min(budgetStats.totalPercent, 100)}%`, 
                height: '100%', 
                background: budgetStats.totalPercent > 100 ? 'var(--danger)' : budgetStats.totalPercent > 85 ? 'var(--warning)' : 'var(--accent-gradient)',
                transition: 'width 1s cubic-bezier(0.34, 1.56, 0.64, 1)',
                boxShadow: '0 0 10px rgba(0,0,0,0.3)'
              }}></div>
           </div>
        </div>
      </div>

      {budgetStats.items.length === 0 ? (
        <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
          <Info size={48} className="text-muted" style={{ marginBottom: '1rem', opacity: 0.5 }} />
          <h3>Nessun Budget Configurato</h3>
          <p className="text-muted">Aggiungi dei budget alle tue categorie nella pagina <a href="/categories" style={{ color: 'var(--accent-secondary)', fontWeight: 600 }}>Categorie</a> per vederli qui.</p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(350px, 1fr))', gap: '1.5rem' }}>
          {budgetStats.items.map((item) => (
            <div key={item.id} className="glass-panel" style={{ 
              padding: '1.5rem', 
              transition: 'transform 0.2s, background-color 0.2s',
              cursor: 'default',
              borderLeft: `4px solid ${item.status === 'danger' ? 'var(--danger)' : item.status === 'warning' ? 'var(--warning)' : item.color || 'var(--accent-secondary)'}`
            }}
            onMouseEnter={e => e.currentTarget.style.backgroundColor = 'var(--bg-glass-hover)'}
            onMouseLeave={e => e.currentTarget.style.backgroundColor = 'var(--bg-glass)'}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
                <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                  <div style={{ 
                    width: '40px', height: '40px', 
                    borderRadius: '10px', 
                    background: `${item.color}22`, 
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    border: `1px solid ${item.color}44`
                  }}>
                    <Wallet size={20} style={{ color: item.color }} />
                  </div>
                  <div>
                    <h3 style={{ margin: 0, fontSize: '1.1rem' }}>{item.name}</h3>
                    <span className="text-muted text-sm">{item.status === 'danger' ? 'Limite Superato' : item.status === 'warning' ? 'Quasi al Limite' : 'In Regola'}</span>
                  </div>
                </div>
                {item.status === 'danger' ? (
                  <AlertTriangle size={20} color="var(--danger)" />
                ) : item.status === 'warning' ? (
                  <AlertTriangle size={20} color="var(--warning)" />
                ) : (
                  <CheckCircle size={20} color="var(--success)" />
                )}
              </div>

              <div style={{ marginBottom: '1.5rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                  <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Speso: <strong>€{item.spent.toLocaleString('it-IT')}</strong></span>
                  <span style={{ fontSize: '0.9rem', color: 'var(--text-muted)' }}>Budget: €{item.budget.toLocaleString('it-IT')}</span>
                </div>
                <div style={{ height: '30px', width: '100%', position: 'relative' }}>
                   <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={[{ spent: item.spent, budget: item.budget }]} layout="vertical" margin={{ top: 0, right: 0, left: 0, bottom: 0 }}>
                        <XAxis type="number" hide domain={[0, Math.max(item.budget, item.spent) * 1.1]} />
                        <YAxis type="category" dataKey="name" hide />
                        <Bar 
                          dataKey="spent" 
                          fill={item.status === 'danger' ? 'var(--danger)' : item.status === 'warning' ? 'var(--warning)' : item.color || 'var(--accent-secondary)'} 
                          radius={[0, 4, 4, 0]}
                          barSize={20}
                        />
                        <ReferenceLine x={item.budget} stroke="rgba(255,255,255,0.3)" strokeDasharray="3 3" label={{ position: 'top', value: 'Cap', fill: 'var(--text-muted)', fontSize: 10 }} />
                      </BarChart>
                   </ResponsiveContainer>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '1rem', borderTop: '1px solid rgba(255,255,255,0.05)' }}>
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <span className="text-muted text-xs" style={{ fontSize: '0.75rem', textTransform: 'uppercase' }}>Rimanente</span>
                  <span style={{ 
                    fontSize: '1.1rem', 
                    fontWeight: 600, 
                    color: item.remaining < 0 ? 'var(--danger)' : 'var(--success)' 
                  }}>
                    €{Math.abs(item.remaining).toLocaleString('it-IT')}
                    {item.remaining < 0 && ' (Extra)'}
                  </span>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: '1.4rem', fontWeight: 700, color: item.status === 'danger' ? 'var(--danger)' : 'var(--text-primary)' }}>
                    {item.percent.toFixed(0)}%
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .text-xs { font-size: 0.75rem; }
      `}</style>
    </div>
  );
};

export default Budgets;
