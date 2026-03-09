import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const ImportTransactions = () => {
  const [file, setFile] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      const token = localStorage.getItem('token');
      // For simplicity, hardcoded default user if no token decoded. In real app use decoded token userId.
      const userId = 'default-user'; 
      const res = await fetch(`http://localhost:8081/api/categories/user/${userId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setCategories(data);
      }
    } catch (error) {
      console.error('Failed to load categories', error);
    }
  };

  const handleFileChange = (e) => {
    if (e.target.files.length > 0) {
      setFile(e.target.files[0]);
    }
  };

  const handleUpload = async () => {
    if (!file) return;

    setIsLoading(true);
    setMessage('');
    
    const formData = new FormData();
    formData.append('file', file);
    
    try {
      const res = await fetch('http://localhost:8081/api/transactions/parse-csv', {
        method: 'POST',
        // Optional auth if implemented: headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` },
        body: formData
      });

      if (res.ok) {
        const data = await res.json();
        setTransactions(data.map(t => ({...t, tempId: crypto.randomUUID()}))); // Add tempId to track edits easily
      } else {
        setMessage('Failed to parse CSV');
      }
    } catch (error) {
      console.error('Upload Error:', error);
      setMessage('Error connecting to server');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRowChange = (tempId, field, value) => {
    setTransactions(prev => prev.map(t => 
      t.tempId === tempId ? { ...t, [field]: value } : t
    ));
  };

  const handleRemoveRow = (tempId) => {
    setTransactions(prev => prev.filter(t => t.tempId !== tempId));
  };

  const handleConfirm = async () => {
    setIsLoading(true);
    try {
      // Remove tempId before sending to backend
      const payload = transactions.map(({ tempId, ...rest }) => rest);
      
      const res = await fetch('http://localhost:8081/api/transactions/bulk-import', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          // 'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        setMessage('Transactions imported successfully!');
        setTransactions([]);
        setFile(null);
        setTimeout(() => navigate('/transactions'), 2000); // Redirect after success
      } else {
        setMessage('Failed to import transactions');
      }
    } catch (error) {
      setMessage('Network error during import');
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="page-container">
      <h2>Import Transactions via CSV</h2>
      
      <div className="upload-container" style={{ display: 'flex', gap: '1rem', alignItems: 'center', marginBottom: '2rem' }}>
        <input type="file" accept=".csv" onChange={handleFileChange} className="form-input" />
        <button onClick={handleUpload} disabled={!file || isLoading} className="btn-primary">
          {isLoading ? 'Parsing...' : 'Upload & Parse'}
        </button>
      </div>

      {message && <div style={{ marginBottom: '1rem', color: message.includes('success') ? 'green' : 'red' }}>{message}</div>}

      {transactions.length > 0 && (
        <div className="import-preview">
          <h3>Preview & Edit Parsed Data</h3>
          <table className="transactions-table">
            <thead>
              <tr>
                <th>Description</th>
                <th>Amount</th>
                <th>Type</th>
                <th>Category (Optional)</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map(t => (
                <tr key={t.tempId}>
                  <td>
                    <input 
                      type="text" 
                      value={t.description} 
                      onChange={(e) => handleRowChange(t.tempId, 'description', e.target.value)} 
                      className="form-input"
                    />
                  </td>
                  <td>
                    <input 
                      type="number" 
                      step="0.01" 
                      value={t.amount} 
                      onChange={(e) => handleRowChange(t.tempId, 'amount', e.target.value)} 
                      className="form-input"
                    />
                  </td>
                  <td>
                    <select 
                      value={t.type} 
                      onChange={(e) => handleRowChange(t.tempId, 'type', e.target.value)}
                      className="form-select"
                    >
                      <option value="EXPENSE">Expense</option>
                      <option value="INCOME">Income</option>
                    </select>
                  </td>
                  <td>
                    <select 
                      value={t.categoryId || ''} 
                      onChange={(e) => handleRowChange(t.tempId, 'categoryId', e.target.value)}
                      className="form-select"
                    >
                      <option value="">-- None --</option>
                      {categories.map(c => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <button onClick={() => handleRemoveRow(t.tempId)} className="btn-alert" style={{ padding: '0.25rem 0.5rem' }}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div style={{ marginTop: '2rem', display: 'flex', justifyContent: 'flex-end' }}>
            <button onClick={handleConfirm} disabled={isLoading} className="btn-success" style={{ padding: '0.75rem 2rem', fontSize: '1.1rem' }}>
              {isLoading ? 'Saving...' : 'Confirm & Save All'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default ImportTransactions;
