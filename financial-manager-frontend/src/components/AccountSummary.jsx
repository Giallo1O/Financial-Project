import React from 'react';
import { 
  PieChart, 
  Pie, 
  Cell, 
  ResponsiveContainer, 
  Tooltip,
  Legend
} from 'recharts';
import { ArrowUpRight, ArrowDownRight, DollarSign } from 'lucide-react';
import './Components.css'; // Shared component styling

const AccountSummary = ({ transactions }) => {
  // Simple aggregation for UI
  const expenses = transactions.filter(t => t.type === 'uscita').reduce((acc, t) => acc + t.amount, 0);
  const incomes = transactions.filter(t => t.type === 'entrata').reduce((acc, t) => acc + t.amount, 0);
  const balance = incomes - expenses;

  const data = [
    { name: 'Income', value: incomes, color: 'var(--success)' },
    { name: 'Expenses', value: expenses, color: 'var(--danger)' }
  ];

  return (
    <div className="glass-panel summary-card">
      <div className="summary-header">
        <h3 className="section-title">Current Balance</h3>
        <div className="balance-display">
          <DollarSign size={32} className="balance-icon" />
          <h2 className={balance >= 0 ? 'text-success' : 'text-danger'}>
            ${balance.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </h2>
        </div>
      </div>

      <div className="summary-stats">
        <div className="stat-box income-box">
          <div className="stat-icon bg-success-muted">
            <ArrowUpRight size={20} className="text-success" />
          </div>
          <div>
            <p className="text-muted text-sm">Monthly Income</p>
            <h4>${incomes.toLocaleString('en-US', { minimumFractionDigits: 2 })}</h4>
          </div>
        </div>

        <div className="stat-box expense-box">
          <div className="stat-icon bg-danger-muted">
            <ArrowDownRight size={20} className="text-danger" />
          </div>
          <div>
            <p className="text-muted text-sm">Monthly Expenses</p>
            <h4>${expenses.toLocaleString('en-US', { minimumFractionDigits: 2 })}</h4>
          </div>
        </div>
      </div>

      {transactions.length > 0 && (
        <div className="chart-container">
          <h4 className="chart-title">Cash Flow Overview</h4>
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={80}
                paddingAngle={5}
                dataKey="value"
                stroke="none"
              >
                {data.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip 
                contentStyle={{ 
                  backgroundColor: 'var(--bg-secondary)', 
                  border: '1px solid var(--border-glass)',
                  borderRadius: 'var(--radius-md)',
                  color: 'white'
                }} 
              />
              <Legend verticalAlign="bottom" height={36}/>
            </PieChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
};

export default AccountSummary;
