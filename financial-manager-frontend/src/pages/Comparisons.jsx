import React, { useState, useEffect, useMemo } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  LineChart, Line, AreaChart, Area, Cell, PieChart, Pie
} from 'recharts';
import {
  ChevronLeft, ChevronRight, TrendingUp, Tag, Calendar,
  ArrowUpRight, ArrowDownLeft, Filter, Info, BarChart3, Layers, CheckCircle, AlertTriangle
} from 'lucide-react';
import { fetchTransactions, fetchCategories } from '../api';

const Comparisons = () => {
  const [allTransactions, setAllTransactions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('mom'); // 'mom', 'trends', 'tags', 'nature'

  // MoM State
  const [monthA, setMonthA] = useState(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [monthB, setMonthB] = useState(new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1));

  // Trends State
  const [trendCategory, setTrendCategory] = useState('all');
  const [trendMonths] = useState(6);

  // Fisse vs Variabili State
  const [natureMonth, setNatureMonth] = useState(new Date(new Date().getFullYear(), new Date().getMonth(), 1));


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

  // --- Spese Fisse vs Variabili & 50/30/20 Rule ---
  const natureData = useMemo(() => {
    const txs = getTransactionsForMonth(natureMonth);
    const totalIncome = txs.filter(t => t.type === 'entrata').reduce((s, t) => s + t.amount, 0);
    const expenses = txs.filter(t => t.type === 'uscita');
    const fixedExpenses = expenses.filter(t => t.isFixed).reduce((s, t) => s + t.amount, 0);
    const variableExpenses = expenses.filter(t => !t.isFixed).reduce((s, t) => s + t.amount, 0);
    const totalExpenses = fixedExpenses + variableExpenses;
    const savings = Math.max(0, totalIncome - totalExpenses);

    const fixedPct = totalExpenses > 0 ? ((fixedExpenses / totalExpenses) * 100).toFixed(1) : '0';
    const variablePct = totalExpenses > 0 ? ((variableExpenses / totalExpenses) * 100).toFixed(1) : '0';

    // 50/30/20 Rule against total income (if income > 0)
    const ruleNeedsPct = totalIncome > 0 ? Math.min(100, Math.round((fixedExpenses / totalIncome) * 100)) : 0;
    const ruleWantsPct = totalIncome > 0 ? Math.min(100, Math.round((variableExpenses / totalIncome) * 100)) : 0;
    const ruleSavingsPct = totalIncome > 0 ? Math.min(100, Math.round((savings / totalIncome) * 100)) : 0;

    const pieData = [
      { name: 'Fisse (Necessità)', value: fixedExpenses, color: '#9e5545' },
      { name: 'Variabili (Desideri)', value: variableExpenses, color: '#c5a028' }
    ].filter(d => d.value > 0);

    return {
      totalIncome,
      fixedExpenses,
      variableExpenses,
      totalExpenses,
      savings,
      fixedPct,
      variablePct,
      ruleNeedsPct,
      ruleWantsPct,
      ruleSavingsPct,
      pieData,
      fixedExpensesList: expenses.filter(t => t.isFixed),
      variableExpensesList: expenses.filter(t => !t.isFixed)
    };
  }, [allTransactions, natureMonth]);

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
            <BarChart3 size={24} color="white" />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Confronti</h1>
            <p className="text-muted text-sm">Analizza i tuoi dati finanziari attraverso diversi periodi e tag.</p>
          </div>
        </div>
      </header>

      {/* Tabs */}
      <div className="tabs-scroll" style={{ display: 'flex', gap: '0.5rem', marginBottom: '2rem', background: 'rgba(255,255,255,0.03)', padding: '0.4rem', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border-glass)' }}>
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
        <button
          onClick={() => setActiveTab('nature')}
          className={`btn ${activeTab === 'nature' ? 'btn-primary' : 'btn-glass'}`}
          style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
        >
          <Layers size={18} /> Fisse vs Variabili (50/30/20)
        </button>
      </div>

      {/* MoM View */}
      {activeTab === 'mom' && (
        <div className="glass-panel" style={{ padding: '2rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '2rem' }}>
            <h3 style={{ margin: 0 }}>Paragone tra due mesi</h3>
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <input
                type="month"
                value={getMonthKey(monthB)}
                onChange={(e) => setMonthB(new Date(e.target.value))}
                className="input-month" style={{ width: 'auto' }}
              />
              <span className="text-muted">vs</span>
              <input
                type="month"
                value={getMonthKey(monthA)}
                onChange={(e) => setMonthA(new Date(e.target.value))}
                className="input-month" style={{ width: 'auto' }}
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
                  contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-glass)', borderRadius: '12px', boxShadow: 'var(--shadow-glass)' }}
                  itemStyle={{ color: 'var(--text-primary)' }}
                  cursor={{ fill: 'rgba(158, 85, 69, 0.15)' }}
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
                className="custom-select" style={{ width: 'auto' }}
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
                  contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-glass)', borderRadius: '12px', boxShadow: 'var(--shadow-glass)' }}
                  itemStyle={{ color: 'var(--text-primary)' }}
                  cursor={{ stroke: 'var(--accent-primary)', strokeWidth: 1, strokeDasharray: '5 5' }}
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

          <div className="stack-mobile" style={{ display: 'grid', gridTemplateColumns: '1fr 300px', gap: '2rem', alignItems: 'center' }}>
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

      {/* Nature View (Spese Fisse vs Variabili & 50/30/20) */}
      {activeTab === 'nature' && (
        <div className="glass-panel" style={{ padding: '2rem' }}>
          {/* Header con Month Picker */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem', flexWrap: 'wrap', gap: '1rem' }}>
            <div>
              <h3 style={{ margin: 0 }}>Spese Fisse vs Variabili</h3>
              <p className="text-muted text-sm">Analizza la sostenibilità delle tue uscite e la regola del budget 50/30/20.</p>
            </div>
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              <button
                className="btn btn-glass"
                style={{ padding: '0.4rem 0.6rem' }}
                onClick={() => setNatureMonth(new Date(natureMonth.getFullYear(), natureMonth.getMonth() - 1, 1))}
              >
                <ChevronLeft size={16} />
              </button>
              <input
                type="month"
                value={getMonthKey(natureMonth)}
                onChange={(e) => setNatureMonth(new Date(e.target.value))}
                className="input-month"
                style={{ width: 'auto' }}
              />
              <button
                className="btn btn-glass"
                style={{ padding: '0.4rem 0.6rem' }}
                onClick={() => setNatureMonth(new Date(natureMonth.getFullYear(), natureMonth.getMonth() + 1, 1))}
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </div>

          {/* 3 Metric Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 220px), 1fr))', gap: '1rem', marginBottom: '2rem' }}>
            <div style={{ padding: '1.25rem', borderRadius: 'var(--radius-md)', background: 'rgba(158, 85, 69, 0.12)', border: '1px solid rgba(158, 85, 69, 0.3)' }}>
              <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Spese Fisse (Necessità)</span>
              <h3 style={{ margin: '0.5rem 0', color: '#ff9b82', fontSize: '1.6rem' }}>
                €{natureData.fixedExpenses.toLocaleString('it-IT', { minimumFractionDigits: 2 })}
              </h3>
              <span className="text-sm" style={{ color: 'var(--text-muted)' }}>
                {natureData.fixedPct}% del totale uscite
              </span>
            </div>

            <div style={{ padding: '1.25rem', borderRadius: 'var(--radius-md)', background: 'rgba(197, 160, 40, 0.12)', border: '1px solid rgba(197, 160, 40, 0.3)' }}>
              <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Spese Variabili (Desideri)</span>
              <h3 style={{ margin: '0.5rem 0', color: '#facc15', fontSize: '1.6rem' }}>
                €{natureData.variableExpenses.toLocaleString('it-IT', { minimumFractionDigits: 2 })}
              </h3>
              <span className="text-sm" style={{ color: 'var(--text-muted)' }}>
                {natureData.variablePct}% del totale uscite
              </span>
            </div>

            <div style={{ padding: '1.25rem', borderRadius: 'var(--radius-md)', background: 'rgba(16, 185, 129, 0.12)', border: '1px solid rgba(16, 185, 129, 0.3)' }}>
              <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Entrate / Risparmio Netto</span>
              <h3 style={{ margin: '0.5rem 0', color: '#34d399', fontSize: '1.6rem' }}>
                €{natureData.savings.toLocaleString('it-IT', { minimumFractionDigits: 2 })}
              </h3>
              <span className="text-sm" style={{ color: 'var(--text-muted)' }}>
                su €{natureData.totalIncome.toLocaleString('it-IT', { minimumFractionDigits: 2 })} entrate
              </span>
            </div>
          </div>

          {/* Regola 50/30/20 Card */}
          <div style={{ padding: '1.5rem', borderRadius: 'var(--radius-lg)', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-glass)', marginBottom: '2rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Layers size={20} color="var(--accent-secondary)" />
                <h4 style={{ margin: 0 }}>Analisi Regola 50 / 30 / 20</h4>
              </div>
              {natureData.totalIncome > 0 ? (
                natureData.ruleNeedsPct <= 50 ? (
                  <span style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', color: 'var(--success)', background: 'rgba(16,185,129,0.1)', padding: '0.3rem 0.75rem', borderRadius: '12px' }}>
                    <CheckCircle size={14} /> Ottimo bilanciamento spese fisse
                  </span>
                ) : (
                  <span style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', color: 'var(--danger)', background: 'rgba(239,68,68,0.1)', padding: '0.3rem 0.75rem', borderRadius: '12px' }}>
                    <AlertTriangle size={14} /> Le spese fisse superano il 50%
                  </span>
                )
              ) : null}
            </div>

            <p className="text-muted text-sm" style={{ marginBottom: '1.5rem' }}>
              La regola finanziaria 50/30/20 suggerisce di destinare fino al <strong>50%</strong> delle entrate a necessità (fisse), il <strong>30%</strong> a desideri personali (variabili) e almeno il <strong>20%</strong> al risparmio.
            </p>

            {/* Barre visuali 50/30/20 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginBottom: '0.35rem' }}>
                  <span>Necessità / Fisse ({natureData.ruleNeedsPct}% su target ≤50%)</span>
                  <span style={{ fontWeight: 600 }}>€{natureData.fixedExpenses.toLocaleString('it-IT')}</span>
                </div>
                <div style={{ width: '100%', height: '8px', background: 'rgba(255,255,255,0.06)', borderRadius: '4px', overflow: 'hidden' }}>
                  <div style={{
                    width: `${Math.min(100, natureData.ruleNeedsPct)}%`,
                    height: '100%',
                    background: natureData.ruleNeedsPct <= 50 ? 'var(--accent-primary)' : 'var(--danger)',
                    borderRadius: '4px',
                    transition: 'width 0.4s ease'
                  }} />
                </div>
              </div>

              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginBottom: '0.35rem' }}>
                  <span>Desideri / Variabili ({natureData.ruleWantsPct}% su target ≤30%)</span>
                  <span style={{ fontWeight: 600 }}>€{natureData.variableExpenses.toLocaleString('it-IT')}</span>
                </div>
                <div style={{ width: '100%', height: '8px', background: 'rgba(255,255,255,0.06)', borderRadius: '4px', overflow: 'hidden' }}>
                  <div style={{
                    width: `${Math.min(100, natureData.ruleWantsPct)}%`,
                    height: '100%',
                    background: natureData.ruleWantsPct <= 30 ? 'var(--accent-secondary)' : 'var(--warning)',
                    borderRadius: '4px',
                    transition: 'width 0.4s ease'
                  }} />
                </div>
              </div>

              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginBottom: '0.35rem' }}>
                  <span>Risparmio Netto ({natureData.ruleSavingsPct}% su target ≥20%)</span>
                  <span style={{ fontWeight: 600 }}>€{natureData.savings.toLocaleString('it-IT')}</span>
                </div>
                <div style={{ width: '100%', height: '8px', background: 'rgba(255,255,255,0.06)', borderRadius: '4px', overflow: 'hidden' }}>
                  <div style={{
                    width: `${Math.min(100, natureData.ruleSavingsPct)}%`,
                    height: '100%',
                    background: 'var(--success)',
                    borderRadius: '4px',
                    transition: 'width 0.4s ease'
                  }} />
                </div>
              </div>
            </div>
          </div>

          {/* Grafico a Torta & Ripartizione */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))', gap: '2rem', alignItems: 'center' }}>
            <div style={{ height: '260px' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={natureData.pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={95}
                    paddingAngle={4}
                    dataKey="value"
                  >
                    {natureData.pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ backgroundColor: 'var(--bg-secondary)', borderColor: 'var(--border-glass)', borderRadius: '8px' }}
                    formatter={(v) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`}
                  />
                  <Legend verticalAlign="bottom" height={36} />
                </PieChart>
              </ResponsiveContainer>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <h4 className="text-muted" style={{ fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '0.1em' }}>
                Dettaglio Spese Fisse
              </h4>
              {natureData.fixedExpensesList.length > 0 ? (
                natureData.fixedExpensesList.slice(0, 5).map((tx, idx) => (
                  <div key={tx.id || idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.6rem 0.75rem', borderRadius: '8px', background: 'rgba(255,255,255,0.03)' }}>
                    <div>
                      <span style={{ fontWeight: 500, fontSize: '0.9rem' }}>{tx.description || tx.category}</span>
                      <span style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-muted)' }}>{tx.category}</span>
                    </div>
                    <span style={{ fontWeight: 600, color: '#ff9b82' }}>€{tx.amount.toLocaleString('it-IT')}</span>
                  </div>
                ))
              ) : (
                <p className="text-muted text-sm">Nessuna spesa fissa registrata questo mese. Puoi contrassegnare le categorie come 'Fisse' nella sezione Categorie.</p>
              )}
            </div>
          </div>
        </div>
      )}

      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .input-month {
          background: rgba(255,255,255,0.05);
          border: 1px solid var(--border-glass);
          color: white;
          border-radius: 8px;
          outline: none;
          padding: 0.5rem;
        }
        .input-month:focus {
          border-color: var(--accent-primary);
        }
      `}</style>
    </div>
  );
};

export default Comparisons;
