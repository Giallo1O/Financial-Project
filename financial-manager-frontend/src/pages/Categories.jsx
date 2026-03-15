import React, { useState, useEffect } from 'react';
import { Tag, Plus, Palette, Type, Check, X } from 'lucide-react';
import { fetchCategories, createCategory, deleteCategory } from '../api';
import '../components/Components.css'; // Corrected path


const Categories = () => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [color, setColor] = useState('#6366f1');
  const [icon, setIcon] = useState('tag');
  const [isExpense, setIsExpense] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  useEffect(() => {
    loadCategories();
  }, []);

  const loadCategories = async () => {
    try {
      const data = await fetchCategories();
      setCategories(data);
    } catch (err) {
      console.error('Failed to load categories', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name) return;

    setIsSubmitting(true);
    setMessage({ type: '', text: '' });

    try {
      await createCategory({ name, color, icon, expense: isExpense });
      setMessage({ type: 'success', text: 'Category created successfully!' });
      setName('');
      loadCategories(); // Refresh list
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteCategory = async (id) => {
    if (!window.confirm('Are you sure you want to delete this category? All associated summaries will be removed from the dashboard.')) return;

    try {
      await deleteCategory(id);
      setMessage({ type: 'success', text: 'Category deleted successfully!' });
      loadCategories(); // Refresh list
    } catch (err) {
      setMessage({ type: 'error', text: `Failed to delete category: ${err.message}` });
    }
  };

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto' }}>
      <header style={{ marginBottom: '2rem' }}>
        <h1 style={{ marginBottom: '0.5rem' }}>Category Management</h1>
        <p className="text-muted">Manage available categories for transactions (Superuser Only).</p>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 350px', gap: '2rem' }}>
        {/* Categories List */}
        <section className="glass-panel" style={{ padding: '1.5rem' }}>
          <h3 className="section-title" style={{ marginBottom: '1.5rem' }}>Existing Categories</h3>
          {loading ? (
            <p className="text-muted">Loading categories...</p>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '1rem' }}>
              {categories.map(cat => (
                <div 
                  key={cat.id} 
                  className="category-item" 
                  style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: '0.75rem', 
                    padding: '0.75rem', 
                    borderRadius: 'var(--radius-lg)',
                    backgroundColor: 'rgba(255,255,255,0.05)',
                    border: `1px solid ${cat.color}44`
                  }}
                >
                  <div style={{ 
                    width: '10px', 
                    height: '10px', 
                    borderRadius: '50%', 
                    backgroundColor: cat.color,
                    boxShadow: `0 0 8px ${cat.color}`
                  }} />
                  <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 }}>
                    <span style={{ fontWeight: '500', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{cat.name}</span>
                    <span style={{ fontSize: '0.7rem', color: cat.expense ? 'var(--danger)' : 'var(--success)', opacity: 0.8 }}>
                      {cat.expense ? 'Uscita' : 'Entrata'}
                    </span>
                  </div>
                  <button
                    onClick={() => handleDeleteCategory(cat.id)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--text-muted)',
                      cursor: 'pointer',
                      padding: '4px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      borderRadius: '4px',
                      transition: 'all 0.2s'
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.backgroundColor = 'rgba(239, 68, 68, 0.1)';
                      e.currentTarget.style.color = 'var(--danger)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.backgroundColor = 'transparent';
                      e.currentTarget.style.color = 'var(--text-muted)';
                    }}
                    title="Delete Category"
                  >
                    <X size={14} />
                  </button>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Add Category Form */}
        <section>
          <div className="glass-panel form-card" style={{ padding: '1.5rem' }}>
            <h3 className="section-title" style={{ marginBottom: '1.5rem' }}>Add New Category</h3>
            
            {message.text && (
              <div className={`status-message ${message.type}`} style={{ marginBottom: '1rem', padding: '0.75rem', borderRadius: '8px', fontSize: '0.875rem', backgroundColor: message.type === 'success' ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)', color: message.type === 'success' ? 'var(--success)' : 'var(--danger)' }}>
                {message.text}
              </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div className="form-group">
                <label>Category Name</label>
                <div className="input-wrapper">
                  <Type size={18} className="input-icon" />
                  <input
                    type="text"
                    placeholder="e.g. Travel, Health..."
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="form-group">
                <label>Color</label>
                <div className="input-wrapper">
                  <Palette size={18} className="input-icon" />
                  <input
                    type="color"
                    value={color}
                    onChange={(e) => setColor(e.target.value)}
                    style={{ height: '40px', padding: '2px', cursor: 'pointer' }}
                  />
                </div>
              </div>

              <div className="form-group">
                <label>Tipo Categoria</label>
                <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.25rem' }}>
                  <button
                    type="button"
                    onClick={() => setIsExpense(true)}
                    className={`btn ${isExpense ? 'btn-danger' : ''}`}
                    style={{ flex: 1, fontSize: '0.8rem', padding: '0.5rem', opacity: isExpense ? 1 : 0.4 }}
                  >
                    Uscita
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsExpense(false)}
                    className={`btn ${!isExpense ? 'btn-success' : ''}`}
                    style={{ flex: 1, fontSize: '0.8rem', padding: '0.5rem', opacity: !isExpense ? 1 : 0.4 }}
                  >
                    Entrata
                  </button>
                </div>
              </div>

              <div className="form-group">
                <label>Icon Identifier (string)</label>
                <div className="input-wrapper">
                  <Tag size={18} className="input-icon" />
                  <input
                    type="text"
                    placeholder="e.g. briefcase, shopping-cart..."
                    value={icon}
                    onChange={(e) => setIcon(e.target.value)}
                  />
                </div>
              </div>

              <button
                type="submit"
                className="btn btn-primary"
                disabled={isSubmitting}
                style={{ marginTop: '0.5rem', width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
              >
                {isSubmitting ? 'Creating...' : (
                  <>
                    <Plus size={18} />
                    <span>Create Category</span>
                  </>
                )}
              </button>
            </form>
          </div>
        </section>
      </div>
    </div>
  );
};

export default Categories;
