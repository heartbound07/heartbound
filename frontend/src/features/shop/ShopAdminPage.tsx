import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';

interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  requiredRole: string | null;
  isActive: boolean;
  isDeleting?: boolean;
}

interface ShopFormData {
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  requiredRole: string | null;
}

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

export function ShopAdminPage() {
  const { } = useAuth();
  const [items, setItems] = useState<ShopItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingItem, setEditingItem] = useState<ShopItem | null>(null);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [submitting, setSubmitting] = useState(false);
  
  const [formData, setFormData] = useState<ShopFormData>({
    name: '',
    description: '',
    price: 0,
    category: '',
    imageUrl: '',
    requiredRole: null
  });
  
  // Available categories (these could also come from an API)
  const categories = ['BANNER', 'PROFILE_EFFECT', 'BADGE', 'OTHER'];
  
  // Available roles for role-restricted items
  const roles = ['ADMIN', 'MODERATOR', 'MONARCH', 'PREMIUM'];
  
  useEffect(() => {
    fetchShopItems();
  }, []);
  
  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  const fetchShopItems = async () => {
    try {
      setLoading(true);
      const response = await httpClient.get('/shop/admin/items');
      setItems(response.data);
    } catch (error) {
      console.error('Error fetching shop items:', error);
      showToast('Failed to load shop items', 'error');
    } finally {
      setLoading(false);
    }
  };
  
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    
    // Handle numeric conversion for price
    if (name === 'price') {
      setFormData({ ...formData, [name]: parseInt(value) || 0 });
    } else {
      setFormData({ ...formData, [name]: value });
    }
  };
  
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    // Prevent duplicate submissions
    if (submitting) return;
    
    setSubmitting(true);
    
    try {
      if (editingItem) {
        // Update existing item
        await httpClient.put(`/shop/admin/items/${editingItem.id}`, formData);
        showToast('Item updated successfully', 'success');
      } else {
        // Create new item
        await httpClient.post('/shop/admin/items', formData);
        showToast('Item created successfully', 'success');
      }
      
      // Reset form and refresh items
      resetForm();
      fetchShopItems();
    } catch (error) {
      console.error('Error saving shop item:', error);
      showToast('Failed to save shop item', 'error');
    } finally {
      setSubmitting(false);
    }
  };
  
  const handleEdit = (item: ShopItem) => {
    setEditingItem(item);
    setFormData({
      name: item.name,
      description: item.description,
      price: item.price,
      category: item.category,
      imageUrl: item.imageUrl,
      requiredRole: item.requiredRole
    });
  };
  
  const handleDelete = async (itemId: string) => {
    // Prevent duplicate operations
    if (items.find(item => item.id === itemId)?.isDeleting) {
      return;
    }
    
    // Add confirmation dialog
    if (!confirm('Are you sure you want to permanently delete this item? This cannot be undone.')) {
      return;
    }
    
    try {
      // Mark as deleting in UI
      setItems(prev => prev.map(item => 
        item.id === itemId ? { ...item, isDeleting: true } : item
      ));
      
      // Call API
      await httpClient.delete(`/shop/admin/items/${itemId}`);
      
      // Remove from UI immediately
      setItems(prev => prev.filter(item => item.id !== itemId));
      showToast('Item deleted successfully', 'success');
    } catch (error) {
      console.error('Error deleting item:', error);
      
      // Reset the deleting state
      setItems(prev => prev.map(item => 
        item.id === itemId ? { ...item, isDeleting: false } : item
      ));
      
      showToast('Failed to delete item', 'error');
    }
  };
  
  const resetForm = () => {
    setEditingItem(null);
    setFormData({
      name: '',
      description: '',
      price: 0,
      category: '',
      imageUrl: '',
      requiredRole: null
    });
  };
  
  if (loading) {
    return <div className="flex justify-center p-8">Loading shop items...</div>;
  }

  return (
    <div className="container mx-auto p-6">
      {/* Toast notifications */}
      <div className="fixed top-4 right-4 z-50 space-y-2">
        {toasts.map(toast => (
          <Toast
            key={toast.id}
            message={toast.message}
            type={toast.type}
            onClose={() => removeToast(toast.id)}
          />
        ))}
      </div>
      
      <h1 className="text-2xl font-bold mb-6 text-white">Shop Management</h1>
      
      {/* Form section */}
      <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-6 mb-8">
        <h2 className="text-xl font-semibold mb-4 text-white">
          {editingItem ? 'Edit Item' : 'Create New Item'}
        </h2>
        
        <form onSubmit={handleSubmit}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Name
              </label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleInputChange}
                required
                className="w-full bg-slate-700/50 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>
            
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Price (Credits)
              </label>
              <input
                type="number"
                name="price"
                value={formData.price}
                onChange={handleInputChange}
                required
                min="0"
                className="w-full bg-slate-700/50 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>
            
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Category
              </label>
              <select
                name="category"
                value={formData.category}
                onChange={handleInputChange}
                required
                className="w-full bg-slate-700/50 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">Select Category</option>
                {categories.map(category => (
                  <option key={category} value={category}>{category}</option>
                ))}
              </select>
            </div>
            
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Required Role (Optional)
              </label>
              <select
                name="requiredRole"
                value={formData.requiredRole || ''}
                onChange={handleInputChange}
                className="w-full bg-slate-700/50 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">None (Available to Everyone)</option>
                {roles.map(role => (
                  <option key={role} value={role}>{role}</option>
                ))}
              </select>
            </div>
            
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Image URL
              </label>
              <input
                type="text"
                name="imageUrl"
                value={formData.imageUrl}
                onChange={handleInputChange}
                className="w-full bg-slate-700/50 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>
            
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Description
              </label>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleInputChange}
                rows={3}
                className="w-full bg-slate-700/50 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
              ></textarea>
            </div>
          </div>
          
          <div className="flex justify-end mt-6 space-x-3">
            {editingItem && (
              <button
                type="button"
                onClick={resetForm}
                className="px-4 py-2 border border-slate-600 rounded bg-slate-700 text-white hover:bg-slate-600 transition-colors"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              className="px-4 py-2 bg-primary hover:bg-primary/90 text-white rounded transition-colors"
            >
              {editingItem ? 'Update Item' : 'Create Item'}
            </button>
          </div>
        </form>
      </div>
      
      {/* Items table */}
      <div className="bg-slate-800/50 border border-slate-700 rounded-lg overflow-hidden">
        <table className="min-w-full">
          <thead>
            <tr className="bg-slate-700/50">
              <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Item</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Category</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Price</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Status</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-700">
            {items.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-4 text-center text-slate-400">
                  No items found. Create your first shop item above.
                </td>
              </tr>
            ) : (
              items.map(item => (
                <tr key={item.id} className="hover:bg-slate-700/30">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      {item.imageUrl && (
                        <img 
                          src={item.imageUrl} 
                          alt={item.name} 
                          className="h-10 w-10 rounded-full mr-3 bg-slate-700 object-cover"
                        />
                      )}
                      <div className="text-sm font-medium text-white">{item.name}</div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-300">{item.category}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-300">{item.price}</td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                      item.isActive ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                    }`}>
                      {item.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <button
                      onClick={() => handleEdit(item)}
                      className="text-indigo-400 hover:text-indigo-300 mr-3"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => handleDelete(item.id)}
                      disabled={item.isDeleting}
                      className={`text-red-500 hover:text-red-700 ${item.isDeleting ? 'opacity-50 cursor-not-allowed' : ''}`}
                    >
                      {item.isDeleting ? 'Deleting...' : 'Delete'}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default ShopAdminPage; 