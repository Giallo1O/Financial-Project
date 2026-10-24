import React, { useEffect, useState } from 'react';
import { fetchAccounts } from '../api';

const KEY = 'lastAccountId';

/** Menu dei conti; ricorda l'ultimo scelto. Se non hai ancora creato conti non mostra nulla. */
const AccountSelect = ({ value, onChange, label = 'Conto', className = 'form-input' }) => {
  const [accounts, setAccounts] = useState([]);

  useEffect(() => {
    fetchAccounts().then(({ accounts: list }) => {
      const open = list.filter(a => !a.archived);
      setAccounts(open);
      let last = null;
      try { last = localStorage.getItem(KEY); } catch { /* storage non disponibile */ }
      if (!value && last && open.some(a => a.id === last)) onChange(last);
    }).catch(() => {});
    // solo al primo caricamento: la scelta ricordata va applicata una volta
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (accounts.length === 0) return null;

  const pick = (id) => {
    try { localStorage.setItem(KEY, id); } catch { /* storage non disponibile */ }
    onChange(id);
  };

  return (
    <div className="input-group">
      <label>{label}</label>
      <select className={className} value={value || ''} onChange={(e) => pick(e.target.value)}>
        <option value="">Nessun conto</option>
        {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
      </select>
    </div>
  );
};

export default AccountSelect;
