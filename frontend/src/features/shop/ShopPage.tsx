import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import { FaCoins } from 'react-icons/fa';
import { motion } from 'framer-motion';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import { Role } from '@/contexts/auth/types';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';

interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  requiredRole: Role | null;
  owned: boolean;
}

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

export function ShopPage() {
  const { user, updateUserProfile } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<ShopItem[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [purchaseInProgress, setPurchaseInProgress] = useState(false);
  const [recentPurchases, setRecentPurchases] = useState<{[key: string]: number}>({});
  
  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  useEffect(() => {
    const fetchShopItems = async () => {
      setLoading(true);
      try {
        const response = await httpClient.get('/shop/items', {
          params: selectedCategory ? { category: selectedCategory } : {}
        });
        setItems(response.data);
      } catch (error) {
        console.error('Error fetching shop items:', error);
        showToast('Failed to load shop items', 'error');
      } finally {
        setLoading(false);
      }
    };
    
    fetchShopItems();
  }, [selectedCategory]);
  
  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const response = await httpClient.get('/shop/categories');
        const fetchedCategories = response.data as string[];
        setCategories(fetchedCategories);
      } catch (error) {
        console.error('Error fetching categories:', error);
        showToast('Failed to load categories', 'error');
      }
    };
    
    fetchCategories();
  }, []);
  
  const handlePurchase = async (itemId: string) => {
    if (purchaseInProgress) return;
    
    setPurchaseInProgress(true);
    try {
      await httpClient.post(`/shop/purchase/${itemId}`);
      showToast('Item purchased successfully!', 'success');
      
      const response = await httpClient.get('/shop/items', {
        params: selectedCategory ? { category: selectedCategory } : {}
      });
      setItems(response.data);
      
      // Update user profile to refresh credits
      try {
        await updateUserProfile({
          displayName: user?.username || '',
          pronouns: '', 
          about: '',
          bannerColor: ''
        });
      } catch (profileError) {
        console.error('Error updating profile:', profileError);
      }
    } catch (error: any) {
      console.error('Purchase error:', error);
      if (error.response?.status === 403) {
        showToast('You do not have permission to purchase this item', 'error');
      } else if (error.response?.status === 409) {
        showToast('You already own this item', 'error');
      } else if (error.response?.status === 400) {
        showToast('Insufficient credits to purchase this item', 'error');
      } else {
        showToast('Failed to purchase item: ' + (error.response?.data?.message || 'Unknown error'), 'error');
      }
    } finally {
      setPurchaseInProgress(false);
    }
  };
  
  // Get featured items (e.g., first 3 items or items marked as featured)
  const getFeaturedItems = () => {
    return items.slice(0, 3);
  };
  
  const featuredItems = getFeaturedItems();
  
  return (
    <div className="container mx-auto p-6">
      <div className="flex flex-col space-y-8">
        {/* Toast notifications */}
        <div className="fixed top-4 right-4 z-50 flex flex-col space-y-2">
          {toasts.map(toast => (
            <Toast
              key={toast.id}
              message={toast.message}
              type={toast.type}
              onClose={() => removeToast(toast.id)}
            />
          ))}
        </div>
      
        {/* Header with credits display */}
        <div className="flex justify-between items-center">
          <h1 className="text-3xl font-bold text-white">Shop</h1>
          <div className="flex items-center bg-slate-800/50 rounded-lg px-4 py-2 border border-slate-700">
            <FaCoins className="text-yellow-400 mr-2" size={20} />
            <span className="text-yellow-400 font-semibold text-lg">
              {user?.credits || 0}
            </span>
          </div>
        </div>
        
        {/* Featured items banner */}
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative w-full h-60 bg-gradient-to-r from-primary/20 to-primary/10 rounded-xl overflow-hidden"
        >
          <div className="absolute inset-0 flex flex-col justify-center p-8">
            <h2 className="text-2xl font-bold text-white mb-2">Featured Items</h2>
            <p className="text-white/80 max-w-md">
              Enhance your profile with exclusive items. New items added regularly!
            </p>
            
            <div className="mt-4 flex space-x-4">
              {featuredItems.map((item) => (
                <div key={item.id} className="flex items-center bg-slate-800/60 px-3 py-2 rounded-lg border border-slate-700">
                  <span className="text-white mr-2">{item.name}</span>
                  <div className="flex items-center">
                    <FaCoins className="text-yellow-400 mr-1" size={12} />
                    <span className="text-yellow-400 text-sm">{item.price}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </motion.div>
        
        {/* Categories */}
        <div>
          <h2 className="text-2xl font-bold text-white mb-4">Categories</h2>
          <div className="flex flex-wrap gap-2 mb-6">
            <button 
              onClick={() => setSelectedCategory(null)}
              className={`px-4 py-2 rounded-lg transition-colors ${
                selectedCategory === null 
                  ? 'bg-primary text-white' 
                  : 'bg-slate-800/50 text-white hover:bg-slate-700'
              }`}
            >
              All Items
            </button>
            
            {categories.map((category) => (
              <button 
                key={category}
                onClick={() => setSelectedCategory(category)}
                className={`px-4 py-2 rounded-lg transition-colors ${
                  selectedCategory === category 
                    ? 'bg-primary text-white' 
                    : 'bg-slate-800/50 text-white hover:bg-slate-700'
                }`}
              >
                {category}
              </button>
            ))}
          </div>
        </div>
        
        {/* Shop items */}
        <div>
          <h2 className="text-2xl font-bold text-white mb-4">
            {selectedCategory ? `${selectedCategory} Items` : 'All Items'}
          </h2>
          
          {items.length === 0 ? (
            <div className="text-center py-8 text-slate-400">
              No items available in this category.
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {items.map((item) => (
                <motion.div
                  key={item.id}
                  whileHover={{ y: -5 }}
                  className="bg-slate-800/30 border border-slate-700 rounded-lg overflow-hidden"
                >
                  {/* Item image */}
                  <div className="h-40 bg-slate-700/50 flex items-center justify-center">
                    {item.imageUrl ? (
                      <img 
                        src={item.imageUrl} 
                        alt={item.name}
                        className="h-full w-full object-cover" 
                      />
                    ) : (
                      <span className="text-slate-400">No Image</span>
                    )}
                  </div>
                  
                  <div className="p-4">
                    <div className="flex justify-between items-center mb-2">
                      <h3 className="font-medium text-white">{item.name}</h3>
                      <div className="flex items-center">
                        <FaCoins className="text-yellow-400 mr-1" size={14} />
                        <span className="text-yellow-400 font-medium">{item.price}</span>
                      </div>
                    </div>
                    
                    {item.description && (
                      <p className="text-slate-300 text-sm mb-3">{item.description}</p>
                    )}
                    
                    {item.owned ? (
                      <button 
                        disabled
                        className="w-full px-4 py-2 bg-green-600/30 text-green-300 rounded cursor-not-allowed"
                      >
                        Owned
                      </button>
                    ) : item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) ? (
                      <button 
                        disabled
                        className="w-full px-4 py-2 bg-amber-600/30 text-amber-300 rounded cursor-not-allowed"
                      >
                        Requires {item.requiredRole} Role
                      </button>
                    ) : (user?.credits ?? 0) < item.price ? (
                      <button 
                        disabled
                        className="w-full px-4 py-2 bg-red-600/30 text-red-300 rounded cursor-not-allowed"
                      >
                        Not Enough Credits
                      </button>
                    ) : (
                      <button 
                        onClick={() => handlePurchase(item.id)}
                        className="w-full px-4 py-2 bg-primary/80 hover:bg-primary text-white rounded transition-colors"
                      >
                        Purchase
                      </button>
                    )}
                  </div>
                </motion.div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default ShopPage;
