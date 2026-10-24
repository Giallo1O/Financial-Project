// Unico formato degli importi in tutta l'app: €1.234,56 e -€710,99 (useGrouping 'always': senza, it-IT scrive 2000,00)
export const eur = (n) => {
  const v = Number(n || 0);
  const abs = Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: 'always' });
  return `${v < 0 ? '-' : ''}€${abs}`;
};
