import React, { useState, useEffect } from 'react';
import { Tag, Plus, Palette, Type, Check } from 'lucide-react';
import { fetchCategories, createCategory } from '../api';
import '../components/Components.css'; // Corrected path


const Categories = () => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [color, setColor] = useState('#6366f1');
  const [icon, setIcon] = useState('tag');
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
      await createCategory({ name, color, icon });
      setMessage({ type: 'success', text: 'Category created successfully!' });
      setName('');
      loadCategories(); // Refresh list
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setIsSubmitting(false);
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
                  <span style={{ fontWeight: '500' }}>{cat.name}</span>
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
