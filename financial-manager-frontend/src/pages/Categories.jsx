import React, { useState, useEffect } from 'react';
import { Tag, Plus, Palette, Type, Check, X, Pencil, RotateCcw } from 'lucide-react';
import { fetchCategories, createCategory, updateCategory, deleteCategory } from '../api';
import '../components/Components.css'; // Corrected path


const Categories = () => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [color, setColor] = useState('#6366f1');
  const [icon, setIcon] = useState('tag');
  const [isExpense, setIsExpense] = useState(true);
  const [budget, setBudget] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [editingCategory, setEditingCategory] = useState(null);

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
      if (editingCategory) {
        await updateCategory(editingCategory.id, { name, color, icon, expense: isExpense, budget: Number(budget) });
        setMessage({ type: 'success', text: 'Category updated successfully!' });
        setEditingCategory(null);
      } else {
        await createCategory({ name, color, icon, expense: isExpense, budget: Number(budget) });
        setMessage({ type: 'success', text: 'Category created successfully!' });
      }
      setName('');
      setColor('#6366f1');
      setIcon('tag');
      setIsExpense(true);
      setBudget('');
      loadCategories(); // Refresh list
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEdit = (category) => {
    setEditingCategory(category);
    setName(category.name);
    setColor(category.color || '#6366f1');
    setIcon(category.icon || 'tag');
    setIsExpense(category.expense);
    setBudget(category.budget || '');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleCancel = () => {
    setEditingCategory(null);
    setName('');
    setColor('#6366f1');
    setIcon('tag');
    setIsExpense(true);
    setBudget('');
    setMessage({ type: '', text: '' });
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
                      {cat.budget > 0 && ` • €${cat.budget}`}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '4px' }}>
                    <button
                      onClick={() => handleEdit(cat)}
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
                        e.currentTarget.style.backgroundColor = 'rgba(99, 102, 241, 0.1)';
                        e.currentTarget.style.color = 'var(--primary)';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.backgroundColor = 'transparent';
                        e.currentTarget.style.color = 'var(--text-muted)';
                      }}
                      title="Edit Category"
                    >
                      <Pencil size={14} />
                    </button>
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
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Add Category Form */}
        <section>
          <div className="glass-panel form-card" style={{ padding: '1.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
              <h3 className="section-title" style={{ marginBottom: 0 }}>
                {editingCategory ? 'Edit Category' : 'Add New Category'}
              </h3>
              {editingCategory && (
                <button 
                  onClick={handleCancel}
                  style={{ 
                    background: 'none', 
                    border: 'none', 
                    color: 'var(--text-muted)', 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: '4px', 
                    fontSize: '0.8rem',
                    cursor: 'pointer'
                  }}
                >
                  <RotateCcw size={14} />
                  Cancel
                </button>
              )}
            </div>
            
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
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: '0.5rem', marginTop: '0.5rem' }}>
                  {[
                    '#9e5545', '#c5a028', '#8b4513', '#cc7722', '#d2b48c', '#808000',
                    '#800000', '#ff7f50', '#f4c430', '#6366f1', '#10b981', '#ef4444'
                  ].map(c => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      style={{
                        width: '100%',
                        aspectRatio: '1',
                        borderRadius: '50%',
                        backgroundColor: c,
                        border: color === c ? '2px solid white' : '2px solid transparent',
                        boxShadow: color === c ? `0 0 10px ${c}` : 'none',
                        cursor: 'pointer',
                        transition: 'all 0.2s',
                        padding: 0
                      }}
                    />
                  ))}
                  <div style={{ position: 'relative', width: '100%', aspectRatio: '1' }}>
                    <Palette 
                      size={20} 
                      style={{ 
                        position: 'absolute', 
                        top: '50%', 
                        left: '50%', 
                        transform: 'translate(-50%, -50%)',
                        pointerEvents: 'none',
                        color: 'white',
                        opacity: 0.8
                      }} 
                    />
                    <input
                      type="color"
                      value={color}
                      onChange={(e) => setColor(e.target.value)}
                      style={{ 
                        width: '100%', 
                        height: '100%', 
                        padding: 0, 
                        border: 'none', 
                        borderRadius: '50%', 
                        cursor: 'pointer',
                        opacity: 0,
                        position: 'absolute'
                      }}
                    />
                    <div style={{
                      width: '100%',
                      height: '100%',
                      borderRadius: '50%',
                      border: '1px solid var(--border-glass)',
                      background: 'rgba(255,255,255,0.05)'
                    }} />
                  </div>
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
                <label>Budget Mensile (opzionale)</label>
                <div className="input-wrapper">
                  <span className="input-icon">€</span>
                  <input
                    type="number"
                    placeholder="0.00"
                    step="0.01"
                    value={budget}
                    onChange={(e) => setBudget(e.target.value)}
                  />
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
                className={`btn ${editingCategory ? 'btn-success' : 'btn-primary'}`}
                disabled={isSubmitting}
                style={{ marginTop: '0.5rem', width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
              >
                {isSubmitting ? (editingCategory ? 'Updating...' : 'Creating...') : (
                  <>
                    {editingCategory ? <Check size={18} /> : <Plus size={18} />}
                    <span>{editingCategory ? 'Save Changes' : 'Create Category'}</span>
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
