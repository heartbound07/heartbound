import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import { ImageUpload } from '@/components/ui/shop/ImageUpload';
import { getRarityColor } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import { 
  HiOutlineCheck, 
  HiOutlineCollection, 
  HiOutlineShoppingCart, 
  HiOutlineColorSwatch,
  HiOutlineTag,
  HiOutlineCash,
  HiOutlineExclamation,
  HiOutlineClock,
  HiOutlineCalendar,
  HiOutlineTrash,
  HiOutlinePencil,
  HiOutlineStar
} from 'react-icons/hi';

interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  requiredRole: string | null;
  expiresAt: string | null;
  active: boolean;
  expired: boolean;
  isDeleting?: boolean;
  discordRoleId?: string;
  rarity: string;
}

interface ShopFormData {
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  requiredRole: string | null;
  expiresAt: string | null;
  active: boolean;
  discordRoleId?: string;
  rarity: string;
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
    requiredRole: null,
    expiresAt: null,
    active: true,
    discordRoleId: '',
    rarity: 'COMMON'
  });
  
  // Available categories
  const categories = ['USER_COLOR', 'LISTING', 'ACCENT', 'BADGE'];
  
  // Available roles for role-restricted items
  const roles = ['ADMIN', 'MODERATOR', 'MONARCH'];
  
  // Add this after the roles array
  const rarities = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY'];
  
  useEffect(() => {
    fetchShopItems();
  }, []);
  
  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info', id?: string) => {
    const toastId = id || Math.random().toString(36).substring(2, 9);
    
    // Don't add duplicate toasts with the same message and type
    if (!toasts.some(toast => toast.message === message && toast.type === type)) {
      setToasts(prev => [...prev, { id: toastId, message, type }]);
    }
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  const fetchShopItems = async () => {
    try {
      setLoading(true);
      const response = await httpClient.get('/shop/admin/items');
      
      // Use functional update to ensure we're working with the latest state
      setItems(response.data);
      return response.data; // Return the data for potential further processing
    } catch (error) {
      console.error('Error fetching shop items:', error);
      if (!toasts.some(t => t.message === 'Failed to load shop items')) {
        showToast('Failed to load shop items', 'error');
      }
      return [];
    } finally {
      setLoading(false);
    }
  };
  
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    
    // Handle numeric conversion for price
    if (name === 'price') {
      setFormData({ ...formData, [name]: parseInt(value) || 0 });
    } else if (name === 'expiresAt') {
      // Handle empty value for expiresAt
      setFormData({ ...formData, [name]: value || null });
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
      const payload = {
        ...formData,
        ...(editingItem && { id: editingItem.id })
      };
      
      if (editingItem) {
        // Update existing item
        await httpClient.put(`/shop/admin/items/${editingItem.id}`, payload);
        
        // Only show toast if another with same message doesn't exist
        if (!toasts.some(t => t.message === 'Item updated successfully')) {
          showToast('Item updated successfully', 'success');
        }
      } else {
        // Create new item
        await httpClient.post('/shop/admin/items', payload);
        
        // Only show toast if another with same message doesn't exist
        if (!toasts.some(t => t.message === 'Item created successfully')) {
          showToast('Item created successfully', 'success');
        }
      }
      
      // Reset form and refresh items
      resetForm();
      await fetchShopItems(); // Use await to ensure completion before setting submitting to false
    } catch (error) {
      console.error('Error saving shop item:', error);
      showToast('Failed to save shop item', 'error');
    } finally {
      setSubmitting(false);
    }
  };
  
  const handleEdit = (item: ShopItem) => {
    console.log("Editing item with active state:", item.active);
    setEditingItem(item);
    setFormData({
      name: item.name,
      description: item.description || '',
      price: item.price,
      category: item.category,
      imageUrl: item.imageUrl || '',
      requiredRole: item.requiredRole,
      expiresAt: item.expiresAt,
      active: item.active,
      discordRoleId: item.discordRoleId || '',
      rarity: item.rarity || 'COMMON'
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
    setFormData({
      name: '',
      description: '',
      price: 0,
      category: '',
      imageUrl: '',
      requiredRole: null,
      expiresAt: null,
      active: true,
      discordRoleId: '',
      rarity: 'COMMON'
    });
    setEditingItem(null);
  };
  
  const handleCheckboxChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, checked } = e.target;
    console.log(`Checkbox ${name} changed to: ${checked}`);
    setFormData(prev => ({ ...prev, [name]: checked }));
  };
  
  // Add function to handle image upload
  const handleImageUpload = (url: string) => {
    setFormData({ ...formData, imageUrl: url });
  };
  
  // Add function to handle image removal
  const handleImageRemove = () => {
    setFormData({ ...formData, imageUrl: '' });
  };
  
  if (loading) {
    return (
      <div className="container mx-auto p-6">
        <div className="flex flex-col items-center justify-center h-96">
          <div className="w-12 h-12 border-4 border-primary border-t-transparent rounded-full animate-spin mb-4"></div>
          <p className="text-slate-300">Loading shop items...</p>
        </div>
      </div>
    );
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
      
      <div className="flex items-center mb-6">
        <HiOutlineShoppingCart className="text-primary mr-3" size={26} />
        <h1 className="text-2xl font-bold text-white">Shop Management</h1>
      </div>
      
      {/* Form section */}
      <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
        <h2 className="text-xl font-semibold mb-6 text-white flex items-center">
          <HiOutlineTag className="text-primary mr-2" size={20} />
          {editingItem ? 'Edit Item' : 'Create New Item'}
        </h2>
        
        <form onSubmit={handleSubmit}>
          <div className="space-y-6">
            {/* Basic Information */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
              <h3 className="text-md font-medium text-slate-200 mb-4 flex items-center">
                <HiOutlineCollection className="mr-2 text-primary" size={18} />
                Basic Information
              </h3>
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
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
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  />
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    Price (Credits)
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                      <HiOutlineCash className="text-slate-400" size={16} />
                    </div>
                    <input
                      type="number"
                      name="price"
                      value={formData.price}
                      onChange={handleInputChange}
                      required
                      min="0"
                      className="w-full bg-slate-800 border border-slate-700 rounded-md pl-9 pr-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                    />
                  </div>
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
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
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
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  >
                    <option value="">None (Available to Everyone)</option>
                    {roles.map(role => (
                      <option key={role} value={role}>{role}</option>
                    ))}
                  </select>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    Rarity
                  </label>
                  <select
                    id="rarity"
                    name="rarity"
                    value={formData.rarity}
                    onChange={handleInputChange}
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  >
                    {rarities.map(rarity => (
                      <option key={rarity} value={rarity}>
                        {rarity.charAt(0) + rarity.slice(1).toLowerCase()}
                      </option>
                    ))}
                  </select>
                  <p className="text-xs text-slate-400 mt-1">
                    The rarity level affects the item's border color and badge in the shop.
                  </p>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    <div className="flex items-center">
                      <HiOutlineCalendar className="mr-1.5 text-slate-400" size={16} />
                      Expires At (Optional)
                    </div>
                  </label>
                  <input
                    type="datetime-local"
                    name="expiresAt"
                    value={formData.expiresAt || ''}
                    onChange={handleInputChange}
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  />
                  <p className="text-xs text-slate-400 mt-1">
                    Leave empty for items that never expire
                  </p>
                </div>
              </div>
              
              <div className="mt-4">
                <label className="flex items-center space-x-2 text-sm font-medium text-slate-300">
                  <input
                    type="checkbox"
                    name="active"
                    checked={formData.active}
                    onChange={handleCheckboxChange}
                    className="w-4 h-4 rounded text-primary focus:ring-primary bg-slate-800 border-slate-600"
                  />
                  <span>Active</span>
                </label>
                <p className="text-xs text-slate-400 mt-1 ml-6">
                  {editingItem?.expired ? 
                    "This item has expired. You can reactivate it by setting a new expiration date or removing the expiration." : 
                    "Inactive items won't be visible in the shop."
                  }
                </p>
              </div>
            </div>
            
            {/* Image & Appearance */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
              <h3 className="text-md font-medium text-slate-200 mb-4 flex items-center">
                <HiOutlineColorSwatch className="mr-2 text-primary" size={18} />
                Item Appearance
              </h3>
              
              {formData.category === 'USER_COLOR' ? (
                <div className="space-y-5">
                  <div>
                    <label htmlFor="colorPicker" className="block text-sm font-medium text-slate-300 mb-1">
                      Nameplate Color
                    </label>
                    <div className="flex items-center space-x-3">
                      <input
                        id="colorPicker"
                        type="color"
                        value={formData.imageUrl && formData.imageUrl.startsWith('#') ? formData.imageUrl : '#ffffff'}
                        onChange={(e) => setFormData({...formData, imageUrl: e.target.value})}
                        className="h-10 w-14 p-1 bg-slate-800 border border-slate-700 rounded cursor-pointer"
                      />
                      <input
                        type="text"
                        value={formData.imageUrl || ''}
                        onChange={(e) => setFormData({...formData, imageUrl: e.target.value})}
                        className="flex-1 px-3 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary"
                        placeholder="#RRGGBB Hex Color"
                      />
                      <div 
                        className="h-10 w-10 rounded border border-slate-600"
                        style={{ backgroundColor: formData.imageUrl || '#ffffff' }}
                      ></div>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">
                      Choose a color for this nameplate. This will be displayed in the user's name in Discord.
                    </p>
                  </div>

                  <div>
                    <label htmlFor="discordRoleId" className="block text-sm font-medium text-slate-300 mb-1">
                      Discord Role ID
                    </label>
                    <input
                      id="discordRoleId"
                      type="text"
                      value={formData.discordRoleId || ''}
                      onChange={(e) => setFormData({...formData, discordRoleId: e.target.value})}
                      className="block w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary"
                      placeholder="Discord role ID for USER_COLOR items"
                    />
                    <p className="mt-1 text-xs text-slate-400">
                      Enter the Discord role ID to be granted when this color is equipped. Leave empty for no role.
                    </p>
                  </div>
                  
                  {/* Preview section */}
                  <div className="mt-4 p-5 bg-slate-900 rounded-md border border-slate-700">
                    <h4 className="text-sm font-medium text-slate-300 mb-3 flex items-center">
                      <HiOutlineExclamation className="mr-1.5 text-yellow-400" size={16} />
                      Preview
                    </h4>
                    <NameplatePreview
                      username="Username"
                      color={formData.imageUrl}
                      message="This is how the color will appear"
                      size="md"
                      className="bg-slate-800/80 rounded-md"
                    />
                  </div>
                </div>
              ) : (
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-2">
                    Item Image
                  </label>
                  <ImageUpload 
                    currentImageUrl={formData.imageUrl}
                    onUpload={handleImageUpload}
                    onRemove={handleImageRemove}
                    showRemoveButton={!!formData.imageUrl}
                    className="mb-2"
                  />
                  <p className="text-xs text-slate-400 mt-2">
                    Click to upload an image for this shop item. Supported formats: JPG, PNG, GIF, WebP (max 5MB)
                  </p>
                </div>
              )}
            </div>
            
            {/* Description */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
              <h3 className="text-md font-medium text-slate-200 mb-4">
                Description
              </h3>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleInputChange}
                rows={3}
                className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                placeholder="Enter item description..."
              ></textarea>
            </div>
          </div>
          
          <div className="flex justify-end mt-6 space-x-3">
            {editingItem && (
              <button
                type="button"
                onClick={resetForm}
                className="px-4 py-2 border border-slate-600 rounded-md bg-slate-800 text-white hover:bg-slate-700 transition-colors flex items-center"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={submitting}
              className={`px-5 py-2 bg-primary hover:bg-primary/90 text-white rounded-md transition-all duration-300 flex items-center ${
                submitting ? 'opacity-70 cursor-not-allowed' : 'transform hover:-translate-y-0.5 hover:shadow-lg hover:shadow-primary/20'
              }`}
            >
              {submitting ? (
                <>
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"></div>
                  {editingItem ? 'Updating...' : 'Creating...'}
                </>
              ) : (
                <>
                  <HiOutlineCheck className="mr-2" size={18} />
                  {editingItem ? 'Update Item' : 'Create Item'}
                </>
              )}
            </button>
          </div>
        </form>
      </div>
      
      {/* Items table */}
      <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 overflow-hidden transition-all duration-300 hover:shadow-xl">
        <div className="p-5 border-b border-slate-700">
          <h2 className="text-xl font-semibold text-white flex items-center">
            <HiOutlineCollection className="text-primary mr-2" size={20} />
            Shop Items
          </h2>
        </div>
        
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-700">
            <thead>
              <tr className="bg-slate-800/70">
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Item</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Category</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Price</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Rarity</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Status</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700/70 bg-slate-800/20">
              {items.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-slate-400">
                    <div className="flex flex-col items-center">
                      <HiOutlineExclamation className="text-slate-500 mb-2" size={24} />
                      <p>No items found. Create your first shop item above.</p>
                    </div>
                  </td>
                </tr>
              ) : (
                items.map(item => (
                  <tr key={item.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center">
                        {item.category === 'USER_COLOR' ? (
                          <div 
                            className="h-10 w-10 rounded-full mr-3 flex items-center justify-center"
                            style={{ 
                              backgroundColor: item.imageUrl || '#ffffff',
                              border: `2px solid ${getRarityColor(item.rarity)}`
                            }}
                          >
                            <HiOutlineColorSwatch className="text-white text-opacity-80" size={16} />
                          </div>
                        ) : (
                          item.imageUrl ? (
                            <div className="h-10 w-10 rounded-full overflow-hidden mr-3" style={{ 
                              border: `2px solid ${getRarityColor(item.rarity)}`
                            }}>
                              <img 
                                src={item.imageUrl} 
                                alt={item.name} 
                                className="h-full w-full object-cover"
                              />
                            </div>
                          ) : (
                            <div className="h-10 w-10 rounded-full mr-3 bg-slate-700 flex items-center justify-center"
                              style={{ border: `2px solid ${getRarityColor(item.rarity)}` }}
                            >
                              <HiOutlineCollection className="text-slate-400" size={16} />
                            </div>
                          )
                        )}
                        <div className="text-sm font-medium text-white">{item.name}</div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-300">{item.category}</td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center text-sm text-yellow-400">
                        <HiOutlineCash className="mr-1" size={14} />
                        <span>{item.price}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span 
                        className="px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full"
                        style={{
                          backgroundColor: getRarityColor(item.rarity) + '20',
                          color: getRarityColor(item.rarity),
                          border: `1px solid ${getRarityColor(item.rarity)}`
                        }}
                      >
                        <HiOutlineStar className="mr-1" size={14} />
                        {item.rarity.charAt(0) + item.rarity.slice(1).toLowerCase()}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {item.expired ? (
                        <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-red-900/30 text-red-400 border border-red-700">
                          <HiOutlineClock className="mr-1" size={14} />
                          Expired
                        </span>
                      ) : item.active ? (
                        <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-green-900/30 text-green-400 border border-green-700">
                          <HiOutlineCheck className="mr-1" size={14} />
                          Active
                        </span>
                      ) : (
                        <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-slate-700/50 text-slate-400 border border-slate-600">
                          Inactive
                        </span>
                      )}
                      {item.expiresAt && !item.expired && (
                        <div className="text-xs text-slate-400 mt-1 flex items-center">
                          <HiOutlineClock className="mr-1" size={12} />
                          Expires: {new Date(item.expiresAt).toLocaleString()}
                        </div>
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                      <div className="flex items-center space-x-3">
                        <button
                          onClick={() => handleEdit(item)}
                          className="text-primary hover:text-primary/80 flex items-center"
                        >
                          <HiOutlinePencil className="mr-1" size={16} />
                          Edit
                        </button>
                        <button
                          onClick={() => handleDelete(item.id)}
                          disabled={item.isDeleting}
                          className={`text-red-500 hover:text-red-400 flex items-center ${item.isDeleting ? 'opacity-50 cursor-not-allowed' : ''}`}
                        >
                          {item.isDeleting ? (
                            <>
                              <div className="w-3 h-3 border-2 border-red-500 border-t-transparent rounded-full animate-spin mr-1"></div>
                              Deleting...
                            </>
                          ) : (
                            <>
                              <HiOutlineTrash className="mr-1" size={16} />
                              Delete
                            </>
                          )}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default ShopAdminPage; 