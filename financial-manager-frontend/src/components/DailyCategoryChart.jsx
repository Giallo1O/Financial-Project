import React, { useMemo, useState } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';

const DailyCategoryChart = ({ transactions, currentDate, viewType }) => {

  // JS Date is 0-indexed for month, but 1-indexed for dates
  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();

  // Get the number of days in the current month
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const chartData = useMemo(() => {
    // 1. Initialize data array for all days in the month
    const dailyData = Array.from({ length: daysInMonth }, (_, i) => {
      return {
        day: i + 1,
        dateStr: `${year}-${String(month + 1).padStart(2, '0')}-${String(i + 1).padStart(2, '0')}`,
        // Object to hold category amounts: { 'Food': 50, 'Transport': 20 }
      };
    });

    // 2. Aggregate transactions by day and category
    transactions.forEach(tx => {
      // Filter based on selected view (income vs expense)
      const txIsExpense = tx.type === 'uscita';
      if ((viewType === 'expense' && !txIsExpense) || (viewType === 'income' && txIsExpense)) {
        return; // Skip if it doesn't match the current view
      }

      // Parse date to find the day (date is in format YYYY-MM-DD)
      const dateParts = tx.date.split('-');
      if (dateParts.length !== 3) return;

      const dayIndex = parseInt(dateParts[2], 10) - 1; // 0-indexed array for days
      if (dayIndex >= 0 && dayIndex < daysInMonth) {
        const catName = tx.category || 'Non categorizzato';
        const amount = Number(tx.amount);

        if (!dailyData[dayIndex][catName]) {
          dailyData[dayIndex][catName] = 0;
        }
        dailyData[dayIndex][catName] += amount;
      }
    });

    return dailyData;
  }, [transactions, daysInMonth, viewType, month, year]);

  // Extract all unique categories to render Bars
  const categoryKeys = useMemo(() => {
    const keys = new Set();
    const colorMap = new Map(); // Category Name -> Color

    transactions.forEach(tx => {
       const txIsExpense = tx.type === 'uscita';
       // We only want to show keys for the current viewType
       if ((viewType === 'expense' && txIsExpense) || (viewType === 'income' && !txIsExpense)) {
         const catName = tx.category || 'Non categorizzato';
         keys.add(catName);
         if (!colorMap.has(catName)) {
           colorMap.set(catName, tx.categoryColor || '#6b7280');
         }
       }
    });

    return Array.from(keys).map(name => ({
      name,
      color: colorMap.get(name)
    }));
  }, [transactions, viewType]);


  return (
    <div className="glass-panel" style={{ padding: '2rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h3 className="section-title" style={{ margin: 0 }}>
          Flusso Giornaliero
        </h3>
      </div>

      {chartData.length === 0 || categoryKeys.length === 0 ? (
         <div style={{ height: '500px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
           <p className="text-muted">Nessun dato per la visualizzazione selezionata.</p>
         </div>
      ) : (
        <div style={{ height: '500px', width: '100%' }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={chartData}
              margin={{ top: 20, right: 30, left: 20, bottom: 20 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" vertical={false} />
              <XAxis 
                dataKey="day" 
                tick={{fill: 'var(--text-muted)', fontSize: 12}} 
                axisLine={{stroke: 'var(--border-glass)'}} 
                tickMargin={10}
              />
              <YAxis 
                tick={{fill: 'var(--text-muted)'}} 
                axisLine={{stroke: 'var(--border-glass)'}} 
                tickFormatter={(value) => `€${value}`} 
              />
              <Tooltip 
                cursor={{fill: 'rgba(255,255,255,0.05)'}}
                contentStyle={{ 
                  backgroundColor: 'var(--bg-secondary)', 
                  border: '1px solid var(--border-glass)',
                  borderRadius: 'var(--radius-md)',
                  color: 'white',
                  boxShadow: 'var(--shadow-glass)'
                }}
                formatter={(value, name) => [`€${value.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`, name]}
                labelFormatter={(label) => `Giorno ${label}`}
              />
              <Legend verticalAlign="top" height={36} />
              
              {/* Render a stacked Bar for each category */}
              {categoryKeys.map((cat) => (
                <Bar 
                  key={cat.name} 
                  dataKey={cat.name} 
                  name={cat.name} 
                  stackId="a" 
                  fill={cat.color} 
                />
              ))}
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
};

export default DailyCategoryChart;
