import React, { useState, useEffect, useMemo } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  LineChart, Line, AreaChart, Area, Cell, PieChart, Pie
} from 'recharts';
import {
  ChevronLeft, ChevronRight, TrendingUp, Tag, Calendar,
  ArrowUpRight, ArrowDownLeft, Filter, Info, BarChart3
} from 'lucide-react';
import { fetchTransactions, fetchCategories } from '../api';

const Comparisons = () => {
  const [allTransactions, setAllTransactions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('mom'); // 'mom', 'trends', 'tags'

  // MoM State
  const [monthA, setMonthA] = useState(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [monthB, setMonthB] = useState(new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1));

  // Trends State
  const [trendCategory, setTrendCategory] = useState('all');
  const [trendMonths, setTrendMonths] = useState(6);

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        // Fetch all transactions for trends/comparisons
        const [txs, cats] = await Promise.all([
          fetchTransactions(), // Fetches all if no params
          fetchCategories()
        ]);
        setAllTransactions(txs);
        setCategories(cats);
      } catch (err) {
        console.error('Failed to load comparison data', err);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, []);

  // --- Helper Functions ---
  const formatMonth = (date) => date.toLocaleDateString('it-IT', { month: 'short', year: '2-digit' });
  const getMonthKey = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;

  const getTransactionsForMonth = (date) => {
    const month = date.getMonth() + 1;
    const year = date.getFullYear();
    return allTransactions.filter(tx => {
      const txDate = new Date(tx.date);
      return txDate.getMonth() + 1 === month && txDate.getFullYear() === year;
    });
  };

  // --- MoM Analysis ---
  const momData = useMemo(() => {
    const txsA = getTransactionsForMonth(monthA);
    const txsB = getTransactionsForMonth(monthB);

    const stats = (txs) => {
      return txs.reduce((acc, tx) => {
        if (tx.type === 'uscita') acc.expense += tx.amount;
        else acc.income += tx.amount;
        return acc;
      }, { income: 0, expense: 0 });
    };

    const sA = stats(txsA);
    const sB = stats(txsB);

    return [
      { name: 'Entrate', [formatMonth(monthB)]: sB.income, [formatMonth(monthA)]: sA.income },
      { name: 'Uscite', [formatMonth(monthB)]: sB.expense, [formatMonth(monthA)]: sA.expense },
      { name: 'Risparmio', [formatMonth(monthB)]: sB.income - sB.expense, [formatMonth(monthA)]: sA.income - sA.expense }
    ];
  }, [allTransactions, monthA, monthB]);

  // --- Trend Analysis ---
  const trendData = useMemo(() => {
    const months = [];
    for (let i = trendMonths - 1; i >= 0; i--) {
      const d = new Date();
      d.setMonth(d.getMonth() - i);
      months.push({
        key: getMonthKey(d),
        label: formatMonth(d),
        income: 0,
        expense: 0
      });
    }

    allTransactions.forEach(tx => {
      const txDate = new Date(tx.date);
      const key = getMonthKey(txDate);
      const monthObj = months.find(m => m.key === key);

      if (monthObj) {
        if (trendCategory === 'all' || tx.categoryId === trendCategory) {
          if (tx.type === 'uscita') monthObj.expense += tx.amount;
          else monthObj.income += tx.amount;
        }
      }
    });

    return months;
  }, [allTransactions, trendCategory, trendMonths]);

  // --- Tag Analysis ---
  const tagData = useMemo(() => {
    // Current month tags
    const currentMonthTxs = getTransactionsForMonth(new Date());
    const tagMap = {};

    currentMonthTxs.forEach(tx => {
      if (tx.type === 'uscita' && tx.categoryTags && tx.categoryTags.length > 0) {
        tx.categoryTags.forEach(tag => {
          tagMap[tag] = (tagMap[tag] || 0) + tx.amount;
        });
      } else if (tx.type === 'uscita') {
        tagMap['Senza Tag'] = (tagMap['Senza Tag'] || 0) + tx.amount;
      }
    });

    return Object.entries(tagMap)
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value);
  }, [allTransactions]);

  if (loading) return <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>Caricamento dati...</div>;

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', animation: 'fadeIn 0.5s ease-out' }}>
      <header className="dashboard-header" style={{ marginBottom: '2rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{
            width: '50px', height: '50px',
            background: 'var(--accent-gradient)',
            borderRadius: 'var(--radius-md)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'var(--shadow-glow)'
          }}>
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Confronti</h1>
            <p className="text-muted text-sm">Analizza i tuoi dati finanziari attraverso diversi periodi e tag.</p>
          </div>
        </div>
      </header>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '2rem', background: 'rgba(255,255,255,0.03)', padding: '0.4rem', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border-glass)' }}>
        <button
          onClick={() => setActiveTab('mom')}
          className={`btn ${activeTab === 'mom' ? 'btn-primary' : 'btn-glass'}`}
          style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
        >
          <Calendar size={18} /> Confronto Mesi
        </button>
        <button
          onClick={() => setActiveTab('trends')}
          className={`btn ${activeTab === 'trends' ? 'btn-primary' : 'btn-glass'}`}
          style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
        >
          <TrendingUp size={18} /> Trend Temporali
        </button>
        <button
          onClick={() => setActiveTab('tags')}
          className={`btn ${activeTab === 'tags' ? 'btn-primary' : 'btn-glass'}`}
          style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
        >
          <Tag size={18} /> Analisi Tag
        </button>
      </div>

      {/* MoM View */}
      {activeTab === 'mom' && (
        <div className="glass-panel" style={{ padding: '2rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
            <h3 style={{ margin: 0 }}>Paragone tra due mesi</h3>
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <input
                type="month"
                value={getMonthKey(monthB)}
                onChange={(e) => setMonthB(new Date(e.target.value))}
                className="input-field" style={{ width: 'auto', padding: '0.5rem' }}
              />
              <span className="text-muted">vs</span>
              <input
                type="month"
                value={getMonthKey(monthA)}
                onChange={(e) => setMonthA(new Date(e.target.value))}
                className="input-field" style={{ width: 'auto', padding: '0.5rem' }}
              />
            </div>
          </div>

          <div style={{ height: '400px', width: '100%' }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={momData}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                <XAxis dataKey="name" stroke="var(--text-muted)" />
                <YAxis stroke="var(--text-muted)" tickFormatter={(v) => `€${v}`} />
                <Tooltip
                  contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-glass)', borderRadius: '8px' }}
                  formatter={(v) => `€${v.toLocaleString('it-IT')}`}
                />
                <Legend />
                <Bar dataKey={formatMonth(monthB)} fill="var(--text-muted)" radius={[4, 4, 0, 0]} />
                <Bar dataKey={formatMonth(monthA)} fill="var(--accent-primary)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Trends View */}
      {activeTab === 'trends' && (
        <div className="glass-panel" style={{ padding: '2rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
            <h3 style={{ margin: 0 }}>Trend degli ultimi {trendMonths} mesi</h3>
            <div style={{ display: 'flex', gap: '1rem' }}>
              <select
                value={trendCategory}
                onChange={(e) => setTrendCategory(e.target.value)}
                className="input-field" style={{ width: 'auto' }}
              >
                <option value="all">Tutte le categorie</option>
                {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
          </div>

          <div style={{ height: '400px', width: '100%' }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trendData}>
                <defs>
                  <linearGradient id="colorExpense" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--danger)" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="var(--danger)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                <XAxis dataKey="label" stroke="var(--text-muted)" />
                <YAxis stroke="var(--text-muted)" tickFormatter={(v) => `€${v}`} />
                <Tooltip
                  contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-glass)', borderRadius: '8px' }}
                  formatter={(v) => `€${v.toLocaleString('it-IT')}`}
                />
                <Area type="monotone" dataKey="expense" name="Uscite" stroke="var(--danger)" fillOpacity={1} fill="url(#colorExpense)" />
                {trendCategory === 'all' && <Area type="monotone" dataKey="income" name="Entrate" stroke="var(--success)" fill="transparent" strokeDasharray="5 5" />}
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Tags View */}
      {activeTab === 'tags' && (
        <div className="glass-panel" style={{ padding: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
            <h3 style={{ margin: 0 }}>Analisi per Tag (Mese Corrente)</h3>
            <Info size={16} className="text-muted" title="Mostra come sono distribuite le uscite tra i vari tag associati alle categorie." />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 300px', gap: '2rem', alignItems: 'center' }}>
            <div style={{ height: '400px' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={tagData}
                    cx="50%"
                    cy="50%"
                    innerRadius={80}
                    outerRadius={140}
                    paddingAngle={5}
                    dataKey="value"
                  >
                    {tagData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={`hsl(${index * 45}, 70%, 60%)`} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-glass)', borderRadius: '8px' }}
                    formatter={(v) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <h4 className="text-muted" style={{ fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '0.1em' }}>Distribuzione Spese</h4>
              {tagData.map((item, index) => (
                <div key={item.name} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem', borderRadius: '8px', background: 'rgba(255,255,255,0.03)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <div style={{ width: '12px', height: '12px', borderRadius: '3px', background: `hsl(${index * 45}, 70%, 60%)` }}></div>
                    <span>{item.name}</span>
                  </div>
                  <span style={{ fontWeight: 600 }}>€{item.value.toLocaleString('it-IT')}</span>
                </div>
              ))}
              {tagData.length === 0 && <p className="text-muted">Nessuna spesa tracciata questo mese.</p>}
            </div>
          </div>
        </div>
      )}

      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .input-field {
          background: rgba(255,255,255,0.05);
          border: 1px solid var(--border-glass);
          color: white;
          border-radius: 8px;
          outline: none;
        }
        .input-field:focus {
          border-color: var(--accent-primary);
        }
      `}</style>
    </div>
  );
};

export default Comparisons;
