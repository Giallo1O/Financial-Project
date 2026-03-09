import React from 'react';
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Tooltip,
  Legend,
} from 'recharts';
import { Tag } from 'lucide-react';

// Palette di colori vivaci per le categorie
const COLORS = [
  '#6366f1', // indigo
  '#ec4899', // pink
  '#f59e0b', // amber
  '#10b981', // emerald
  '#3b82f6', // blue
  '#ef4444', // red
  '#8b5cf6', // violet
  '#14b8a6', // teal
  '#f97316', // orange
  '#84cc16', // lime
];

const formatCurrency = (value) =>
  `€${Number(value).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

// Etichetta custom sulla fetta
const renderCustomLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent }) => {
  if (percent < 0.05) return null; // nascondi etichette < 5%
  const RADIAN = Math.PI / 180;
  const radius = innerRadius + (outerRadius - innerRadius) * 0.55;
  const x = cx + radius * Math.cos(-midAngle * RADIAN);
  const y = cy + radius * Math.sin(-midAngle * RADIAN);
  return (
    <text x={x} y={y} fill="white" textAnchor="middle" dominantBaseline="central" fontSize={12} fontWeight={600}>
      {`${(percent * 100).toFixed(0)}%`}
    </text>
  );
};

const CategoryPieChart = ({ categories = [] }) => {
  // Filtra categorie con importo > 0 e costruisci i dati per recharts
  const data = categories
    .filter((c) => Number(c.totalAmount) > 0)
    .map((c) => ({
      name: c.categoryId,            // può essere UUID o stringa leggibile (mock)
      value: Number(c.totalAmount),
      count: c.transactionCount,
    }));

  const total = data.reduce((acc, d) => acc + d.value, 0);

  const CustomTooltip = ({ active, payload }) => {
    if (active && payload && payload.length) {
      const item = payload[0];
      return (
        <div
          style={{
            backgroundColor: 'var(--bg-secondary)',
            border: '1px solid var(--border-glass)',
            borderRadius: 'var(--radius-md)',
            padding: '0.75rem 1rem',
            color: 'white',
            fontSize: '0.85rem',
            minWidth: '160px',
          }}
        >
          <p style={{ fontWeight: 700, marginBottom: '0.3rem', color: item.payload.fill }}>
            {item.name}
          </p>
          <p style={{ margin: '0.1rem 0' }}>
            Importo: <strong>{formatCurrency(item.value)}</strong>
          </p>
          <p style={{ margin: '0.1rem 0', color: 'var(--text-muted)' }}>
            Transazioni: {item.payload.count}
          </p>
          <p style={{ margin: '0.1rem 0', color: 'var(--text-muted)' }}>
            Quota: {total > 0 ? ((item.value / total) * 100).toFixed(1) : 0}%
          </p>
        </div>
      );
    }
    return null;
  };

  return (
    <div className="glass-panel" style={{ padding: '1.5rem', height: '100%' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.25rem' }}>
        <div
          style={{
            width: '36px',
            height: '36px',
            background: 'var(--accent-gradient)',
            borderRadius: 'var(--radius-sm)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Tag size={18} color="white" />
        </div>
        <div>
          <h3 className="section-title" style={{ marginBottom: '0.1rem' }}>
            Spese per Categoria
          </h3>
          <p className="text-muted text-sm">Distribuzione delle uscite per categoria</p>
        </div>
      </div>

      {data.length === 0 ? (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            height: '260px',
            color: 'var(--text-muted)',
            gap: '0.75rem',
          }}
        >
          <Tag size={40} opacity={0.3} />
          <p style={{ fontSize: '0.95rem' }}>Nessuna categoria trovata</p>
        </div>
      ) : (
        <>
          {/* Totale */}
          <div style={{ textAlign: 'center', marginBottom: '0.5rem' }}>
            <span className="text-muted text-sm">Totale uscite</span>
            <p
              style={{
                fontSize: '1.6rem',
                fontWeight: 700,
                color: 'var(--danger)',
                lineHeight: 1.2,
              }}
            >
              {formatCurrency(total)}
            </p>
          </div>

          {/* Grafico a torta */}
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                outerRadius={100}
                innerRadius={52}
                paddingAngle={3}
                dataKey="value"
                labelLine={false}
                label={renderCustomLabel}
                stroke="none"
              >
                {data.map((entry, index) => (
                  <Cell
                    key={`cell-${index}`}
                    fill={COLORS[index % COLORS.length]}
                  />
                ))}
              </Pie>
              <Tooltip content={<CustomTooltip />} />
              <Legend
                verticalAlign="bottom"
                height={40}
                formatter={(value) => (
                  <span style={{ color: 'var(--text-primary)', fontSize: '0.8rem' }}>
                    {value.length > 20 ? `${value.substring(0, 18)}…` : value}
                  </span>
                )}
              />
            </PieChart>
          </ResponsiveContainer>
        </>
      )}
    </div>
  );
};

export default CategoryPieChart;
