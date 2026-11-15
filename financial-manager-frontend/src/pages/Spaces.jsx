import React, { useState, useEffect, useMemo } from 'react';
import {
  Layers, ArrowUpRight, ArrowDownLeft, Users, User, ArrowLeft,
  TrendingUp, Calendar, Search, Filter, ShoppingCart, Home, Zap,
  Tv, Plane, Car, Fuel, CreditCard, Wallet, Sparkles, AlertCircle
} from 'lucide-react';
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip, CartesianGrid
} from 'recharts';
import { fetchSpaces, fetchSpaceDetail } from '../api';
import { eur } from '../utils/format';
import { format, parseISO } from 'date-fns';
import { it } from 'date-fns/locale/it';
import '../components/Components.css';

// Dynamic icon mapper
const getSpaceIcon = (iconName, size = 22) => {
  const props = { size };
  switch ((iconName || '').toLowerCase()) {
    case 'shopping-cart': return <ShoppingCart {...props} />;
    case 'home': return <Home {...props} />;
    case 'zap': return <Zap {...props} />;
    case 'cat': return <span style={{ fontSize: size }}>🐱</span>;
    case 'tv': return <Tv {...props} />;
    case 'plane': return <Plane {...props} />;
    case 'car': return <Car {...props} />;
    case 'fuel': return <Fuel {...props} />;
    case 'credit-card': return <CreditCard {...props} />;
    default: return <Wallet {...props} />;
  }
};

const Spaces = () => {
  const [spaces, setSpaces] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedSpaceId, setSelectedSpaceId] = useState(null);
  const [spaceDetail, setSpaceDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [filterType, setFilterType] = useState('ALL'); // ALL, SHARED, PERSONAL
  const [movementSearch, setMovementSearch] = useState('');
  const [authorFilter, setAuthorFilter] = useState('ALL'); // ALL, ANDREA, ROSSELLA, ESTERNO

  useEffect(() => {
    loadSpaces();
  }, []);

  const loadSpaces = async () => {
    try {
      setLoading(true);
      const data = await fetchSpaces();
      setSpaces(data || []);
    } catch (err) {
      console.error('Failed to load spaces', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectSpace = async (spaceId) => {
    setSelectedSpaceId(spaceId);
    setMovementSearch('');
    setAuthorFilter('ALL');
    try {
      setDetailLoading(true);
      const detail = await fetchSpaceDetail(spaceId);
      setSpaceDetail(detail);
    } catch (err) {
      console.error('Failed to load space detail', err);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleBackToList = () => {
    setSelectedSpaceId(null);
    setSpaceDetail(null);
  };

  // Filtered spaces
  const filteredSpaces = useMemo(() => {
    if (filterType === 'SHARED') return spaces.filter(s => s.shared);
    if (filterType === 'PERSONAL') return spaces.filter(s => !s.shared);
    return spaces;
  }, [spaces, filterType]);

  // Overall metrics across all spaces
  const totalBalanceAll = useMemo(() => {
    return spaces.reduce((acc, s) => acc + (s.currentBalance || 0), 0);
  }, [spaces]);

  const totalAndreaIn = useMemo(() => {
    return spaces.reduce((acc, s) => acc + (s.andreaIn || 0), 0);
  }, [spaces]);

  const totalRossellaIn = useMemo(() => {
    return spaces.reduce((acc, s) => acc + (s.rossellaIn || 0), 0);
  }, [spaces]);

  // Filtered movements inside detail view
  const filteredMovements = useMemo(() => {
    if (!spaceDetail || !spaceDetail.movements) return [];
    return spaceDetail.movements.filter(m => {
      const matchAuthor = authorFilter === 'ALL' || m.author === authorFilter;
      const searchLower = movementSearch.toLowerCase();
      const matchSearch = !movementSearch
        || (m.description && m.description.toLowerCase().includes(searchLower))
        || (m.reference && m.reference.toLowerCase().includes(searchLower))
        || (m.partnerName && m.partnerName.toLowerCase().includes(searchLower));
      return matchAuthor && matchSearch;
    });
  }, [spaceDetail, authorFilter, movementSearch]);

  // Formatted timeline data for Recharts
  const chartData = useMemo(() => {
    if (!spaceDetail || !spaceDetail.timeline) return [];
    return spaceDetail.timeline.map(pt => ({
      date: pt.date,
      displayDate: format(parseISO(pt.date), 'd MMM yyyy', { locale: it }),
      saldo: Number(pt.balance)
    }));
  }, [spaceDetail]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '5rem 0', color: 'var(--text-muted)' }}>
        <div className="spinner" style={{
          margin: '0 auto 1.5rem', width: '42px', height: '42px',
          border: '3px solid var(--border-glass)', borderTopColor: 'var(--primary)',
          borderRadius: '50%', animation: 'spin 1s linear infinite'
        }} />
        <p>Caricamento spazi in corso...</p>
      </div>
    );
  }

  // ─── DETAIL VIEW (Single Space) ───────────────────────────────────────────
  if (selectedSpaceId && spaceDetail) {
    const s = spaceDetail.space;
    const color = s.color || 'var(--primary)';

    return (
      <div className="fade-in" style={{ maxWidth: '1200px', margin: '0 auto', paddingBottom: '3rem' }}>
        <button
          onClick={handleBackToList}
          className="btn btn-secondary"
          style={{
            display: 'inline-flex', alignItems: 'center', gap: '0.5rem',
            marginBottom: '1.5rem', padding: '0.5rem 1rem'
          }}
        >
          <ArrowLeft size={18} />
          <span>Torna a tutti gli spazi</span>
        </button>

        {/* Space Header Card */}
        <div className="glass-panel" style={{ padding: '1.8rem', marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1.2rem' }}>
              <div style={{
                width: '60px', height: '60px', borderRadius: '16px',
                background: `linear-gradient(135deg, ${color}33, ${color}66)`,
                border: `1px solid ${color}88`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: 'white'
              }}>
                {getSpaceIcon(s.icon, 28)}
              </div>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem' }}>
                  <h1 style={{ fontSize: '1.8rem', margin: 0 }}>{s.name}</h1>
                  {s.shared ? (
                    <span style={{
                      display: 'inline-flex', alignItems: 'center', gap: '0.35rem',
                      fontSize: '0.75rem', padding: '0.25rem 0.65rem', borderRadius: '999px',
                      background: 'rgba(236, 72, 153, 0.15)', color: '#f472b6',
                      border: '1px solid rgba(236, 72, 153, 0.3)'
                    }}>
                      <Users size={13} />
                      Condiviso con Rossella
                    </span>
                  ) : (
                    <span style={{
                      display: 'inline-flex', alignItems: 'center', gap: '0.35rem',
                      fontSize: '0.75rem', padding: '0.25rem 0.65rem', borderRadius: '999px',
                      background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8',
                      border: '1px solid rgba(99, 102, 241, 0.3)'
                    }}>
                      <User size={13} />
                      Personale
                    </span>
                  )}
                </div>
                <p className="text-muted text-sm" style={{ marginTop: '0.2rem' }}>
                  Movimenti storici e andamento del saldo dello spazio.
                </p>
              </div>
            </div>

            <div style={{ textAlign: 'right' }}>
              <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Saldo Attuale
              </span>
              <h2 style={{ fontSize: '2.2rem', margin: '0.1rem 0 0', color: color }}>
                {eur(s.currentBalance)}
              </h2>
            </div>
          </div>

          {/* Quick Metrics Bar */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '1rem',
            marginTop: '1.8rem',
            paddingTop: '1.5rem',
            borderTop: '1px solid var(--border-glass)'
          }}>
            <div className="stat-box" style={{ background: 'rgba(255, 255, 255, 0.02)', padding: '0.8rem 1rem', borderRadius: '12px' }}>
              <span className="text-muted text-xs">Versamenti Andrea</span>
              <h4 style={{ margin: '0.2rem 0 0', color: '#818cf8' }}>{eur(spaceDetail.andreaIn)}</h4>
            </div>

            {s.shared && (
              <div className="stat-box" style={{ background: 'rgba(255, 255, 255, 0.02)', padding: '0.8rem 1rem', borderRadius: '12px' }}>
                <span className="text-muted text-xs">Versamenti Rossella</span>
                <h4 style={{ margin: '0.2rem 0 0', color: '#f472b6' }}>{eur(spaceDetail.rossellaIn)}</h4>
              </div>
            )}

            <div className="stat-box" style={{ background: 'rgba(255, 255, 255, 0.02)', padding: '0.8rem 1rem', borderRadius: '12px' }}>
              <span className="text-muted text-xs">Totale Uscite / Spese</span>
              <h4 style={{ margin: '0.2rem 0 0', color: 'var(--danger)' }}>{eur(spaceDetail.totalOut)}</h4>
            </div>

            <div className="stat-box" style={{ background: 'rgba(255, 255, 255, 0.02)', padding: '0.8rem 1rem', borderRadius: '12px' }}>
              <span className="text-muted text-xs">Numero Movimenti</span>
              <h4 style={{ margin: '0.2rem 0 0' }}>{spaceDetail.movements.length}</h4>
            </div>
          </div>
        </div>

        {/* Balance Progression Chart */}
        {chartData.length > 1 && (
          <div className="glass-panel" style={{ padding: '1.5rem', marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
              <TrendingUp size={20} color={color} />
              <h3 style={{ margin: 0, fontSize: '1.1rem' }}>Andamento del saldo nel tempo</h3>
            </div>
            <div style={{ width: '100%', height: '280px' }}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="spaceGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={color} stopOpacity={0.4} />
                      <stop offset="95%" stopColor={color} stopOpacity={0.0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                  <XAxis
                    dataKey="date"
                    tickFormatter={(str) => {
                      try { return format(parseISO(str), 'MMM yy', { locale: it }); }
                      catch { return str; }
                    }}
                    stroke="var(--text-muted)"
                    fontSize={12}
                  />
                  <YAxis
                    stroke="var(--text-muted)"
                    fontSize={12}
                    tickFormatter={(val) => `${val} €`}
                  />
                  <Tooltip
                    content={({ active, payload }) => {
                      if (active && payload && payload.length) {
                        const data = payload[0].payload;
                        return (
                          <div style={{
                            background: 'var(--bg-secondary)',
                            border: '1px solid var(--border-glass)',
                            borderRadius: '8px',
                            padding: '0.6rem 0.9rem',
                            color: 'white',
                            fontSize: '0.85rem'
                          }}>
                            <div style={{ color: 'var(--text-muted)', marginBottom: '0.2rem' }}>{data.displayDate}</div>
                            <div style={{ fontWeight: 'bold', color: color }}>Saldo: {eur(data.saldo)}</div>
                          </div>
                        );
                      }
                      return null;
                    }}
                  />
                  <Area
                    type="monotone"
                    dataKey="saldo"
                    stroke={color}
                    strokeWidth={2.5}
                    fillOpacity={1}
                    fill="url(#spaceGradient)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}

        {/* Movements Table */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            flexWrap: 'wrap', gap: '1rem', marginBottom: '1.2rem'
          }}>
            <h3 style={{ margin: 0, fontSize: '1.1rem' }}>Tutti i movimenti ({filteredMovements.length})</h3>

            <div style={{ display: 'flex', gap: '0.8rem', flexWrap: 'wrap', alignItems: 'center' }}>
              {/* Author Filter */}
              <div style={{ display: 'inline-flex', background: 'rgba(255,255,255,0.04)', borderRadius: '8px', padding: '0.2rem' }}>
                <button
                  className={`btn text-xs ${authorFilter === 'ALL' ? 'btn-primary' : 'btn-ghost'}`}
                  style={{ padding: '0.35rem 0.75rem', borderRadius: '6px' }}
                  onClick={() => setAuthorFilter('ALL')}
                >
                  Tutti
                </button>
                <button
                  className={`btn text-xs ${authorFilter === 'ANDREA' ? 'btn-primary' : 'btn-ghost'}`}
                  style={{ padding: '0.35rem 0.75rem', borderRadius: '6px' }}
                  onClick={() => setAuthorFilter('ANDREA')}
                >
                  Andrea
                </button>
                {s.shared && (
                  <button
                    className={`btn text-xs ${authorFilter === 'ROSSELLA' ? 'btn-primary' : 'btn-ghost'}`}
                    style={{ padding: '0.35rem 0.75rem', borderRadius: '6px' }}
                    onClick={() => setAuthorFilter('ROSSELLA')}
                  >
                    Rossella
                  </button>
                )}
              </div>

              {/* Search bar */}
              <div style={{ position: 'relative', width: '220px' }}>
                <Search size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                <input
                  type="text"
                  placeholder="Cerca negozio, nota..."
                  value={movementSearch}
                  onChange={(e) => setMovementSearch(e.target.value)}
                  className="input-field"
                  style={{ paddingLeft: '2.2rem', fontSize: '0.85rem', width: '100%' }}
                />
              </div>
            </div>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-glass)', textAlign: 'left', color: 'var(--text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>
                  <th style={{ padding: '0.75rem' }}>Data</th>
                  <th style={{ padding: '0.75rem' }}>Autore</th>
                  <th style={{ padding: '0.75rem' }}>Descrizione / Negozio</th>
                  <th style={{ padding: '0.75rem', textAlign: 'right' }}>Importo</th>
                  <th style={{ padding: '0.75rem', textAlign: 'right' }}>Saldo risultante</th>
                </tr>
              </thead>
              <tbody>
                {filteredMovements.length === 0 ? (
                  <tr>
                    <td colSpan={5} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                      Nessun movimento trovato.
                    </td>
                  </tr>
                ) : (
                  filteredMovements.map((m) => {
                    const isPositive = m.amount > 0;
                    return (
                      <tr key={m.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                        <td style={{ padding: '0.75rem', whiteSpace: 'nowrap', color: 'var(--text-muted)' }}>
                          {format(parseISO(m.date), 'dd/MM/yyyy')}
                        </td>
                        <td style={{ padding: '0.75rem', whiteSpace: 'nowrap' }}>
                          {m.author === 'ROSSELLA' ? (
                            <span style={{
                              padding: '0.2rem 0.55rem', borderRadius: '6px', fontSize: '0.75rem',
                              background: 'rgba(236, 72, 153, 0.15)', color: '#f472b6', fontWeight: 500
                            }}>
                              Rossella
                            </span>
                          ) : m.author === 'ANDREA' ? (
                            <span style={{
                              padding: '0.2rem 0.55rem', borderRadius: '6px', fontSize: '0.75rem',
                              background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8', fontWeight: 500
                            }}>
                              Andrea
                            </span>
                          ) : (
                            <span style={{
                              padding: '0.2rem 0.55rem', borderRadius: '6px', fontSize: '0.75rem',
                              background: 'rgba(148, 163, 184, 0.15)', color: '#94a3b8', fontWeight: 500
                            }}>
                              Esterno
                            </span>
                          )}
                        </td>
                        <td style={{ padding: '0.75rem' }}>
                          <div style={{ fontWeight: 500 }}>{m.description}</div>
                          {m.reference && m.reference !== m.description && (
                            <div className="text-muted text-xs" style={{ marginTop: '0.1rem' }}>Rif: {m.reference}</div>
                          )}
                        </td>
                        <td style={{
                          padding: '0.75rem', textAlign: 'right', whiteSpace: 'nowrap',
                          fontWeight: 'bold',
                          color: isPositive ? 'var(--success)' : 'var(--danger)'
                        }}>
                          {isPositive ? `+${eur(m.amount)}` : `-${eur(Math.abs(m.amount))}`}
                        </td>
                        <td style={{ padding: '0.75rem', textAlign: 'right', whiteSpace: 'nowrap', color: 'var(--text-muted)' }}>
                          {m.balanceAfter != null ? eur(m.balanceAfter) : '-'}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  }

  // ─── MAIN VIEW (Spaces Cards Grid) ────────────────────────────────────────
  return (
    <div className="fade-in" style={{ maxWidth: '1200px', margin: '0 auto', paddingBottom: '3rem' }}>
      {/* Header */}
      <header className="dashboard-header" style={{ marginBottom: '2rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{
            width: '50px', height: '50px',
            background: 'var(--accent-gradient)',
            borderRadius: 'var(--radius-md)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'var(--shadow-glow)'
          }}>
            <Layers size={24} color="white" />
          </div>
          <div>
            <h1 style={{ fontSize: '1.8rem', marginBottom: '0.2rem' }}>I tuoi Spazi N26</h1>
            <p className="text-muted text-sm">
              Gestione degli spazi di spesa, affitto, bollette e salvadanai con andamento e contributi.
            </p>
          </div>
        </div>
      </header>

      {/* Overview Metric Bar */}
      <div className="glass-panel" style={{
        padding: '1.5rem', marginBottom: '2rem',
        display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.5rem'
      }}>
        <div style={{ borderRight: '1px solid var(--border-glass)', paddingRight: '1rem' }}>
          <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Totale nei tuoi Spazi
          </span>
          <h2 style={{ fontSize: '2rem', margin: '0.2rem 0 0', color: 'var(--success)' }}>
            {eur(totalBalanceAll)}
          </h2>
          <p className="text-muted text-xs" style={{ marginTop: '0.2rem' }}>
            Somma di tutti i saldi attuali dei tuoi spazi
          </p>
        </div>

        <div style={{ borderRight: '1px solid var(--border-glass)', paddingRight: '1rem' }}>
          <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Quota Versata da Te
          </span>
          <h3 style={{ fontSize: '1.5rem', margin: '0.2rem 0 0', color: '#818cf8' }}>
            {eur(totalAndreaIn)}
          </h3>
          <p className="text-muted text-xs" style={{ marginTop: '0.2rem' }}>
            Totale trasferimenti dai tuoi conti
          </p>
        </div>

        <div>
          <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Quota Versata da Rossella
          </span>
          <h3 style={{ fontSize: '1.5rem', margin: '0.2rem 0 0', color: '#f472b6' }}>
            {eur(totalRossellaIn)}
          </h3>
          <p className="text-muted text-xs" style={{ marginTop: '0.2rem' }}>
            Totale versamenti di Rossella negli spazi comuni
          </p>
        </div>
      </div>

      {/* Filter Tabs */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div style={{ display: 'inline-flex', background: 'rgba(255,255,255,0.03)', borderRadius: '10px', padding: '0.25rem' }}>
          <button
            className={`btn ${filterType === 'ALL' ? 'btn-primary' : 'btn-ghost'}`}
            style={{ padding: '0.45rem 1rem', fontSize: '0.85rem', borderRadius: '8px' }}
            onClick={() => setFilterType('ALL')}
          >
            Tutti ({spaces.length})
          </button>
          <button
            className={`btn ${filterType === 'SHARED' ? 'btn-primary' : 'btn-ghost'}`}
            style={{ padding: '0.45rem 1rem', fontSize: '0.85rem', borderRadius: '8px' }}
            onClick={() => setFilterType('SHARED')}
          >
            Condivisi ({spaces.filter(s => s.shared).length})
          </button>
          <button
            className={`btn ${filterType === 'PERSONAL' ? 'btn-primary' : 'btn-ghost'}`}
            style={{ padding: '0.45rem 1rem', fontSize: '0.85rem', borderRadius: '8px' }}
            onClick={() => setFilterType('PERSONAL')}
          >
            Personali ({spaces.filter(s => !s.shared).length})
          </button>
        </div>

        <span className="text-muted text-xs">
          Clicca su uno spazio per vedere il grafico e la lista movimenti
        </span>
      </div>

      {/* Spaces Cards Grid */}
      {filteredSpaces.length === 0 ? (
        <div className="glass-panel" style={{ textAlign: 'center', padding: '4rem', color: 'var(--text-muted)' }}>
          <AlertCircle size={40} style={{ margin: '0 auto 1rem', opacity: 0.5 }} />
          <h3>Nessuno spazio trovato</h3>
          <p className="text-sm">Importa il tuo estratto conto N26 per creare automaticamente gli spazi.</p>
        </div>
      ) : (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: '1.5rem'
        }}>
          {filteredSpaces.map((s) => {
            const color = s.color || 'var(--primary)';
            return (
              <div
                key={s.id}
                onClick={() => handleSelectSpace(s.id)}
                className="glass-panel"
                style={{
                  padding: '1.5rem',
                  borderRadius: '16px',
                  cursor: 'pointer',
                  transition: 'transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease',
                  border: '1px solid var(--border-glass)',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  position: 'relative',
                  overflow: 'hidden'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'translateY(-3px)';
                  e.currentTarget.style.borderColor = `${color}88`;
                  e.currentTarget.style.boxShadow = `0 10px 25px -5px ${color}22`;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.borderColor = 'var(--border-glass)';
                  e.currentTarget.style.boxShadow = 'none';
                }}
              >
                {/* Header of Card */}
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.2rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem' }}>
                      <div style={{
                        width: '46px', height: '46px', borderRadius: '12px',
                        background: `linear-gradient(135deg, ${color}33, ${color}66)`,
                        border: `1px solid ${color}88`,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: 'white'
                      }}>
                        {getSpaceIcon(s.icon, 22)}
                      </div>
                      <div>
                        <h3 style={{ fontSize: '1.15rem', margin: 0 }}>{s.name}</h3>
                        <span className="text-muted text-xs">{s.movementCount} movimenti</span>
                      </div>
                    </div>

                    {s.shared ? (
                      <span style={{
                        display: 'inline-flex', alignItems: 'center', gap: '0.25rem',
                        fontSize: '0.7rem', padding: '0.2rem 0.5rem', borderRadius: '999px',
                        background: 'rgba(236, 72, 153, 0.15)', color: '#f472b6',
                        border: '1px solid rgba(236, 72, 153, 0.3)'
                      }}>
                        <Users size={12} />
                        Rossella
                      </span>
                    ) : (
                      <span style={{
                        display: 'inline-flex', alignItems: 'center', gap: '0.25rem',
                        fontSize: '0.7rem', padding: '0.2rem 0.5rem', borderRadius: '999px',
                        background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8',
                        border: '1px solid rgba(99, 102, 241, 0.3)'
                      }}>
                        <User size={12} />
                        Personale
                      </span>
                    )}
                  </div>

                  {/* Balance Display */}
                  <div style={{ margin: '1rem 0' }}>
                    <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Saldo Disponibile
                    </span>
                    <h2 style={{ fontSize: '1.9rem', margin: '0.1rem 0 0', color: color }}>
                      {eur(s.currentBalance)}
                    </h2>
                  </div>
                </div>

                {/* Footer breakdown */}
                <div style={{
                  paddingTop: '1rem',
                  borderTop: '1px solid rgba(255,255,255,0.05)',
                  fontSize: '0.8rem',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}>
                  <div style={{ display: 'flex', gap: '0.8rem' }}>
                    <span style={{ color: '#818cf8' }}>
                      Tu: <strong>{eur(s.andreaIn)}</strong>
                    </span>
                    {s.shared && (
                      <span style={{ color: '#f472b6' }}>
                        Rossella: <strong>{eur(s.rossellaIn)}</strong>
                      </span>
                    )}
                  </div>

                  <span style={{ color: color, fontSize: '0.8rem', fontWeight: 500, display: 'inline-flex', alignItems: 'center', gap: '0.2rem' }}>
                    Dettagli →
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default Spaces;
