import * as XLSX from 'xlsx';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';

/**
 * Esporta un array di transazioni in formato Excel (.xlsx)
 */
export function exportTransactionsToExcel(transactions, fileName = 'transazioni_finmanager.xlsx') {
  if (!transactions || transactions.length === 0) {
    alert('Nessuna transazione disponibile per l\'esportazione.');
    return;
  }

  // Mappa i dati in una struttura ordinata
  const rows = transactions.map((t) => {
    const isIncome = t.type === 'entrata';
    const amountVal = Number(t.amount);
    return {
      Data: t.date ? new Date(t.date).toLocaleDateString('it-IT') : '',
      Descrizione: t.description || 'Senza descrizione',
      Categoria: t.category || 'Non categorizzata',
      Tipo: isIncome ? 'Entrata' : 'Uscita',
      'Importo (€)': isIncome ? amountVal : -amountVal,
    };
  });

  // Calcola subtotali
  const totalIncomes = transactions
    .filter(t => t.type === 'entrata')
    .reduce((sum, t) => sum + Number(t.amount), 0);
  const totalExpenses = transactions
    .filter(t => t.type === 'uscita')
    .reduce((sum, t) => sum + Number(t.amount), 0);
  const netBalance = totalIncomes - totalExpenses;

  // Aggiungi riga separatore e totali
  rows.push({});
  rows.push({
    Data: 'TOTALI',
    Descrizione: `Totale Entrate: €${totalIncomes.toFixed(2)} | Totale Uscite: €${totalExpenses.toFixed(2)}`,
    Categoria: '',
    Tipo: 'Saldo Netto',
    'Importo (€)': Number(netBalance.toFixed(2)),
  });

  const worksheet = XLSX.utils.json_to_sheet(rows);

  // Imposta larghezza colonne automatica
  worksheet['!cols'] = [
    { wch: 14 }, // Data
    { wch: 35 }, // Descrizione
    { wch: 22 }, // Categoria
    { wch: 14 }, // Tipo
    { wch: 16 }, // Importo
  ];

  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Transazioni');
  XLSX.writeFile(workbook, fileName);
}

/**
 * Esporta un array di transazioni in formato PDF (.pdf)
 */
export function exportTransactionsToPdf(transactions, fileName = 'report_transazioni_finmanager.pdf') {
  if (!transactions || transactions.length === 0) {
    alert('Nessuna transazione disponibile per l\'esportazione.');
    return;
  }

  const doc = new jsPDF();
  const pageWidth = doc.internal.pageSize.getWidth();

  // Intestazione
  doc.setFontSize(20);
  doc.setTextColor(30, 25, 22);
  doc.text('FinManager - Estratto Conto', 14, 20);

  doc.setFontSize(10);
  doc.setTextColor(120, 115, 110);
  const dataStampa = new Date().toLocaleDateString('it-IT', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
  doc.text(`Generato il: ${dataStampa}`, 14, 27);

  // Calcolo statistiche
  const totalIncomes = transactions
    .filter(t => t.type === 'entrata')
    .reduce((sum, t) => sum + Number(t.amount), 0);
  const totalExpenses = transactions
    .filter(t => t.type === 'uscita')
    .reduce((sum, t) => sum + Number(t.amount), 0);
  const netBalance = totalIncomes - totalExpenses;

  // Box di Riepilogo
  doc.setDrawColor(200, 180, 160);
  doc.setFillColor(248, 246, 242);
  doc.roundedRect(14, 32, pageWidth - 28, 22, 3, 3, 'FD');

  doc.setFontSize(10);
  doc.setTextColor(80, 75, 70);
  doc.text('Entrate Totali:', 20, 41);
  doc.setTextColor(16, 185, 129); // green
  doc.text(`+€${totalIncomes.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`, 20, 48);

  doc.setTextColor(80, 75, 70);
  doc.text('Uscite Totali:', 85, 41);
  doc.setTextColor(239, 68, 68); // red
  doc.text(`-€${totalExpenses.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`, 85, 48);

  doc.setTextColor(80, 75, 70);
  doc.text('Saldo Netto:', 150, 41);
  if (netBalance >= 0) {
    doc.setTextColor(16, 185, 129);
    doc.text(`+€${netBalance.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`, 150, 48);
  } else {
    doc.setTextColor(239, 68, 68);
    doc.text(`-€${Math.abs(netBalance).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`, 150, 48);
  }

  // Tabella delle transazioni
  const tableData = transactions.map(t => {
    const isIncome = t.type === 'entrata';
    const formattedAmount = `${isIncome ? '+' : '-'} €${Math.abs(Number(t.amount)).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;
    return [
      t.date ? new Date(t.date).toLocaleDateString('it-IT') : '',
      t.description || 'Senza descrizione',
      t.category || 'Non categorizzata',
      isIncome ? 'Entrata' : 'Uscita',
      formattedAmount
    ];
  });

  autoTable(doc, {
    startY: 60,
    head: [['Data', 'Descrizione', 'Categoria', 'Tipo', 'Importo']],
    body: tableData,
    theme: 'grid',
    headStyles: {
      fillColor: [158, 85, 69], // terracotta accent
      textColor: [255, 255, 255],
      fontStyle: 'bold'
    },
    styles: {
      fontSize: 9,
      cellPadding: 3
    },
    columnStyles: {
      0: { cellWidth: 26 },
      1: { cellWidth: 'auto' },
      2: { cellWidth: 40 },
      3: { cellWidth: 22 },
      4: { cellWidth: 32, halign: 'right', fontStyle: 'bold' }
    },
    didParseCell: function(data) {
      if (data.section === 'body' && data.column.index === 4) {
        const text = data.cell.raw || '';
        if (text.startsWith('+')) {
          data.cell.styles.textColor = [16, 185, 129];
        } else if (text.startsWith('-')) {
          data.cell.styles.textColor = [220, 38, 38];
        }
      }
    }
  });

  doc.save(fileName);
}
