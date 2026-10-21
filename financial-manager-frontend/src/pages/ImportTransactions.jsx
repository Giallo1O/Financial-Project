import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Upload, 
  FileText, 
  CheckCircle2, 
  ChevronRight, 
  Trash2, 
  AlertCircle,
  ArrowRight,
  PieChart,
  List
} from 'lucide-react';
import { fetchCategories, parseCsv, bulkImportTransactions } from '../api';
import DatePicker, { registerLocale } from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import { format, parseISO } from 'date-fns';
import { it } from 'date-fns/locale/it';
import './ImportTransactions.css';

registerLocale('it', it);

const STEPS = [
  { id: 1, label: 'Upload', icon: Upload },
  { id: 2, label: 'Review', icon: List },
  { id: 3, label: 'Confirm', icon: CheckCircle2 },
];

const ImportTransactions = () => {
  const [currentStep, setCurrentStep] = useState(1);
  const [file, setFile] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState({ text: '', type: '' });
  
  const fileInputRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    loadCategories();
  }, []);

  const loadCategories = async () => {
    try {
      const data = await fetchCategories();
      setCategories(data);
    } catch (error) {
      console.error('Failed to load categories', error);
    }
  };

  const handleFileChange = (e) => {
    const selectedFile = e.target.files[0];
    if (selectedFile && selectedFile.type === 'text/csv' || selectedFile.name.endsWith('.csv')) {
      setFile(selectedFile);
      setMessage({ text: '', type: '' });
    } else if (selectedFile) {
      setMessage({ text: 'Please select a valid CSV file.', type: 'error' });
    }
  };

  const onDragOver = (e) => {
    e.preventDefault();
  };

  const onDrop = (e) => {
    e.preventDefault();
    const droppedFile = e.dataTransfer.files[0];
    if (droppedFile && (droppedFile.type === 'text/csv' || droppedFile.name.endsWith('.csv'))) {
      setFile(droppedFile);
      setMessage({ text: '', type: '' });
    } else {
      setMessage({ text: 'Please drop a valid CSV file.', type: 'error' });
    }
  };

  const handleUploadAndParse = async () => {
    if (!file) return;

    setIsLoading(true);
    try {
      const data = await parseCsv(file);
      setTransactions(data.map(t => ({
        ...t, 
        tempId: crypto.randomUUID(),
        type: t.type || 'USCITA',
        transactionDate: t.transactionDate ? parseISO(t.transactionDate) : new Date()
      })));
      setCurrentStep(2);
      setMessage({ text: '', type: '' });
    } catch (error) {
      console.error('Upload Error:', error);
      setMessage({ text: 'Failed to parse CSV. Check the file format.', type: 'error' });
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

  const handleNextToConfirm = () => {
    if (transactions.length === 0) {
      setMessage({ text: 'No transactions to import.', type: 'error' });
      return;
    }
    setCurrentStep(3);
  };

  const handleFinalConfirm = async () => {
    setIsLoading(true);
    try {
      // tempId serve solo alla UI: non va inviato al backend
      // eslint-disable-next-line no-unused-vars
      const payload = transactions.map(({ tempId, transactionDate, ...rest }) => ({
        ...rest,
        transactionDate: format(transactionDate, 'yyyy-MM-dd'),
        type: rest.type.toUpperCase()
      }));
      
      const res = await bulkImportTransactions(payload);

      if (res.ok) {
        setMessage({ text: 'Success! Transactions imported.', type: 'success' });
        setTimeout(() => navigate('/transactions'), 1500);
      } else {
        setMessage({ text: 'Import failed. Please try again.', type: 'error' });
      }
    } catch (error) {
      setMessage({ text: 'Network error during import.', type: 'error' });
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="main-content fade-in">
      <div className="import-wizard">
        <div className="header-section">
          <h1 className="text-gradient">Import Transactions</h1>
          <p className="text-muted">Effortlessly bulk-upload your data using CSV files</p>
        </div>

        {/* Stepper indicator */}
        <div className="stepper-header">
          {STEPS.map((step) => {
            const Icon = step.icon;
            const isActive = currentStep === step.id;
            const isCompleted = currentStep > step.id;
            return (
              <div key={step.id} className={`step-item ${isActive ? 'active' : ''} ${isCompleted ? 'completed' : ''}`}>
                <div className="step-badge">
                  {isCompleted ? <CheckCircle2 size={18} /> : <Icon size={18} />}
                </div>
                <span className="step-label">{step.label}</span>
              </div>
            );
          })}
        </div>

        {message.text && (
          <div className={`glass-panel fade-in`} style={{ 
            padding: '1rem', 
            marginBottom: '1.5rem', 
            display: 'flex', 
            alignItems: 'center', 
            gap: '0.75rem',
            borderColor: message.type === 'error' ? 'var(--danger)' : 'var(--success)',
            background: message.type === 'error' ? 'rgba(239, 68, 68, 0.1)' : 'rgba(16, 185, 129, 0.1)'
          }}>
            {message.type === 'error' ? <AlertCircle size={20} color="var(--danger)" /> : <CheckCircle2 size={20} color="var(--success)" />}
            <span style={{ color: message.type === 'error' ? 'var(--danger)' : 'var(--success)' }}>{message.text}</span>
          </div>
        )}

        {/* Step 1: Upload */}
        {currentStep === 1 && (
          <div className="glass-panel dropzone-container fade-in"
               onDragOver={onDragOver}
               onDrop={onDrop}
               onClick={() => fileInputRef.current.click()}>
            <input 
              type="file" 
              ref={fileInputRef} 
              style={{ display: 'none' }} 
              accept=".csv" 
              onChange={handleFileChange} 
            />
            <div className="upload-icon">
              <Upload size={64} strokeWidth={1.5} />
            </div>
            <div>
              <h3>{file ? file.name : 'Click or drag CSV file here'}</h3>
              <p className="text-muted">Maximum file size: 5MB</p>
            </div>
            
            {file && (
              <div className="file-info">
                <FileText size={16} />
                <span>{file.name} ({(file.size / 1024).toFixed(1)} KB)</span>
              </div>
            )}

            <button 
              className="btn btn-primary" 
              disabled={!file || isLoading}
              onClick={(e) => {
                e.stopPropagation();
                handleUploadAndParse();
              }}
              style={{ marginTop: '1rem', padding: '0.75rem 3rem' }}
            >
              {isLoading ? 'Processing...' : 'Analyze Data'}
              {!isLoading && <ChevronRight size={18} />}
            </button>
          </div>
        )}

        {/* Step 2: Review */}
        {currentStep === 2 && (
          <div className="fade-in">
            <div className="preview-actions">
              <h3>Verify & Polish ({transactions.length} rows)</h3>
              <div style={{ display: 'flex', gap: '1rem' }}>
                <button className="btn btn-glass" onClick={() => setCurrentStep(1)}>
                  Back
                </button>
                <button className="btn btn-primary" onClick={handleNextToConfirm}>
                  Next Step <ArrowRight size={18} />
                </button>
              </div>
            </div>

            <div className="glass-panel scrolltable-container">
              <table className="import-preview-table">
                <thead>
                  <tr>
                    <th>Description</th>
                    <th style={{ width: '120px' }}>Amount</th>
                    <th style={{ width: '130px' }}>Type</th>
                    <th style={{ width: '150px' }}>Date</th>
                    <th style={{ width: '180px' }}>Category</th>
                    <th style={{ width: '50px' }}></th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map(t => (
                    <tr key={t.tempId}>
                      <td>
                        <input 
                          type="text" 
                          value={t.description || ''} 
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
                          <option value="USCITA">Expense</option>
                          <option value="ENTRATA">Income</option>
                        </select>
                      </td>
                      <td>
                        <DatePicker
                          selected={t.transactionDate}
                          onChange={(date) => handleRowChange(t.tempId, 'transactionDate', date)}
                          dateFormat="dd/MM/yyyy"
                          locale="it"
                          className="form-input"
                          required
                        />
                      </td>
                      <td>
                        <select 
                          value={t.categoryId || ''} 
                          onChange={(e) => handleRowChange(t.tempId, 'categoryId', e.target.value)}
                          className="form-select"
                        >
                          <option value="">-- No Category --</option>
                          {categories.map(c => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <button className="btn-icon" onClick={() => handleRemoveRow(t.tempId)} style={{ color: 'var(--danger)', background: 'none', border: 'none', cursor: 'pointer' }}>
                          <Trash2 size={18} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Step 3: Confirm */}
        {currentStep === 3 && (
          <div className="glass-panel summary-card fade-in">
            <CheckCircle2 size={64} color="var(--success)" style={{ marginBottom: '1.5rem', opacity: 0.8 }} />
            <h2>Ready to Import!</h2>
            <p className="text-muted">Please confirm the following data summary before final save</p>

            <div className="summary-stats">
              <div className="stat-item">
                <span className="stat-value">{transactions.length}</span>
                <span className="stat-label">Transactions</span>
              </div>
              <div className="stat-item">
                <span className="stat-value" style={{ color: 'var(--accent-secondary)' }}>
                  {transactions.filter(t => t.type === 'ENTRATA').length}
                </span>
                <span className="stat-label">Incomes</span>
              </div>
              <div className="stat-item">
                <span className="stat-value" style={{ color: 'var(--danger)' }}>
                  {transactions.filter(t => t.type === 'USCITA').length}
                </span>
                <span className="stat-label">Expenses</span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', marginTop: '1rem' }}>
              <button className="btn btn-glass" onClick={() => setCurrentStep(2)}>
                Back to Review
              </button>
              <button className="btn btn-primary" onClick={handleFinalConfirm} disabled={isLoading} style={{ padding: '0.75rem 3rem' }}>
                {isLoading ? 'Saving...' : 'Confirm & Save All'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ImportTransactions;
