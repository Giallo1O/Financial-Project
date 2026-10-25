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
import AccountSelect from '../components/AccountSelect';
import DatePicker, { registerLocale } from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import { format, parseISO } from 'date-fns';
import { it } from 'date-fns/locale/it';
import './ImportTransactions.css';

registerLocale('it', it);

const STEPS = [
  { id: 1, label: 'Carica', icon: Upload },
  { id: 2, label: 'Controlla', icon: List },
  { id: 3, label: 'Conferma', icon: CheckCircle2 },
];

const ImportTransactions = () => {
  const [currentStep, setCurrentStep] = useState(1);
  const [file, setFile] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState({ text: '', type: '' });
  const [accountId, setAccountId] = useState('');
  const [learn, setLearn] = useState(true); // ricorda le categorie scelte per i prossimi import
  
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
      setMessage({ text: 'Seleziona un file CSV valido.', type: 'error' });
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
      setMessage({ text: 'Trascina un file CSV valido.', type: 'error' });
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
        suggestedCategoryId: t.categoryNote ? t.categoryId : null, // categoria proposta da una regola
        type: t.type || 'USCITA',
        transactionDate: t.transactionDate ? parseISO(t.transactionDate) : new Date()
      })));
      setCurrentStep(2);
      setMessage(data.internalTransfers > 0
        ? { text: `${data.internalTransfers} spostamenti tra i tuoi conti/Spaces non vengono importati: non sono né spese né entrate.`, type: 'success' }
        : { text: '', type: '' });
    } catch (error) {
      console.error('Upload Error:', error);
      setMessage({ text: 'Non riesco a leggere il CSV. Controlla il formato del file.', type: 'error' });
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

  const duplicateCount = transactions.filter(t => t.duplicate).length;

  const handleRemoveDuplicates = () => {
    setTransactions(prev => prev.filter(t => !t.duplicate));
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
      // i campi usati solo dalla UI non vanno inviati al backend
      // eslint-disable-next-line no-unused-vars
      const payload = transactions.map(({ tempId, duplicate, duplicateNote, categoryNote, suggestedCategoryId, transactionDate, ...rest }) => ({
        ...rest,
        transactionDate: format(transactionDate, 'yyyy-MM-dd'),
        type: rest.type.toUpperCase()
      }));
      
      const res = await bulkImportTransactions(payload, learn, accountId);

      if (res.ok) {
        const { imported, skipped } = await res.json();
        setMessage({
          text: `Importate ${imported} transazioni` + (skipped ? `, ${skipped} saltate perché già presenti.` : '.'),
          type: 'success'
        });
        setTimeout(() => navigate('/transactions'), skipped ? 3000 : 1500);
      } else {
        setMessage({ text: 'Importazione non riuscita. Riprova.', type: 'error' });
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
          <h1 className="text-gradient">Importa movimenti</h1>
          <p className="text-muted">Carica l'estratto conto della banca in formato CSV</p>
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
              <h3>{file ? file.name : 'Clicca o trascina qui il file CSV'}</h3>
              <p className="text-muted">Dimensione massima: 5 MB</p>
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
              {isLoading ? 'Analisi in corso...' : 'Analizza file'}
              {!isLoading && <ChevronRight size={18} />}
            </button>
          </div>
        )}

        {/* Step 2: Review */}
        {currentStep === 2 && (
          <div className="fade-in">
            <div className="preview-actions">
              <h3>Controlla e correggi ({transactions.length} righe)</h3>
              <div style={{ display: 'flex', gap: '1rem' }}>
                {duplicateCount > 0 && (
                  <button className="btn btn-glass" onClick={handleRemoveDuplicates}>
                    Rimuovi {duplicateCount} già present{duplicateCount === 1 ? 'e' : 'i'}
                  </button>
                )}
                <button className="btn btn-glass" onClick={() => setCurrentStep(1)}>
                  Indietro
                </button>
                <button className="btn btn-primary" onClick={handleNextToConfirm}>
                  Avanti <ArrowRight size={18} />
                </button>
              </div>
            </div>

            <div className="glass-panel scrolltable-container">
              <table className="import-preview-table">
                <thead>
                  <tr>
                    <th>Descrizione</th>
                    <th style={{ width: '120px' }}>Importo</th>
                    <th style={{ width: '130px' }}>Tipo</th>
                    <th style={{ width: '150px' }}>Data</th>
                    <th style={{ width: '180px' }}>Categoria</th>
                    <th style={{ width: '50px' }}></th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map(t => (
                    <tr key={t.tempId} style={t.duplicate ? { opacity: 0.55 } : undefined}>
                      <td>
                        {t.duplicate && (
                          <span title="Verrà saltato al salvataggio" style={{ fontSize: '0.75rem', color: 'var(--danger)' }}>
                            {t.duplicateNote || 'Già presente'}
                          </span>
                        )}
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
                          <option value="USCITA">Uscita</option>
                          <option value="ENTRATA">Entrata</option>
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
                          <option value="">-- Senza categoria --</option>
                          {categories.map(c => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                          ))}
                        </select>
                        {t.categoryNote && t.categoryId === t.suggestedCategoryId && (
                          <span title="Categoria suggerita da una tua regola" style={{ fontSize: '0.75rem', color: 'var(--accent-secondary)' }}>
                            {t.categoryNote}
                          </span>
                        )}
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
            <h2>Pronto per importare</h2>
            <p className="text-muted">Controlla il riepilogo prima di salvare</p>

            <div className="summary-stats">
              <div className="stat-item">
                <span className="stat-value">{transactions.length}</span>
                <span className="stat-label">Movimenti</span>
              </div>
              <div className="stat-item">
                <span className="stat-value" style={{ color: 'var(--accent-secondary)' }}>
                  {transactions.filter(t => t.type === 'ENTRATA').length}
                </span>
                <span className="stat-label">Entrate</span>
              </div>
              <div className="stat-item">
                <span className="stat-value" style={{ color: 'var(--danger)' }}>
                  {transactions.filter(t => t.type === 'USCITA').length}
                </span>
                <span className="stat-label">Uscite</span>
              </div>
            </div>

            <div style={{ maxWidth: 320, margin: '1rem auto' }}>
              <AccountSelect value={accountId} onChange={setAccountId} label="Su quale conto sono questi movimenti?" />
            </div>

            <label style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', justifyContent: 'center', margin: '1rem 0', cursor: 'pointer' }}>
              <input type="checkbox" checked={learn} onChange={(e) => setLearn(e.target.checked)}
                style={{ width: 'auto', margin: 0, padding: 0 }} />
              <span>
                Ricorda le categorie scelte per i prossimi import
                <span className="text-muted text-sm"> ({transactions.filter(t => t.categoryId).length} righe con categoria)</span>
              </span>
            </label>

            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', marginTop: '1rem' }}>
              <button className="btn btn-glass" onClick={() => setCurrentStep(2)}>
                Torna al controllo
              </button>
              <button className="btn btn-primary" onClick={handleFinalConfirm} disabled={isLoading} style={{ padding: '0.75rem 3rem' }}>
                {isLoading ? 'Salvataggio...' : 'Conferma e salva tutto'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ImportTransactions;
