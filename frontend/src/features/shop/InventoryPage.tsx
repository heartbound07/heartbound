import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import { motion } from 'framer-motion';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';

interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  owned: boolean;
}

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

export function InventoryPage() {
  const { } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<ShopItem[]>([]);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  
  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  useEffect(() => {
    const fetchInventory = async () => {
      setLoading(true);
      try {
        const response = await httpClient.get('/shop/inventory');
        
        if (response.data && response.data.items) {
          // Extract unique categories from items with proper type assertion
          const uniqueCategories = [...new Set(response.data.items.map((item: ShopItem) => item.category))] as string[];
          setCategories(uniqueCategories);
          
          // Filter items by category if a category is selected
          const filteredItems = selectedCategory 
            ? response.data.items.filter((item: ShopItem) => item.category === selectedCategory)
            : response.data.items;
            
          setItems(filteredItems);
        } else {
          setItems([]);
        }
      } catch (error) {
        console.error('Error fetching inventory:', error);
        showToast('Failed to load your inventory', 'error');
      } finally {
        setLoading(false);
      }
    };
    
    fetchInventory();
  }, [selectedCategory]);
  
  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white mb-2">My Inventory</h1>
        <p className="text-slate-400">
          View all items you've purchased from the shop.
        </p>
      </div>
      
      {toasts.map((toast) => (
        <Toast
          key={toast.id}
          message={toast.message}
          type={toast.type}
          onClose={() => removeToast(toast.id)}
        />
      ))}
      
      <div className="space-y-8">
        {/* Categories */}
        {categories.length > 0 && (
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
        )}
        
        {/* Inventory items */}
        <div>
          <h2 className="text-2xl font-bold text-white mb-4">
            {selectedCategory ? `${selectedCategory} Items` : 'All Items'}
          </h2>
          
          {loading ? (
            <div className="flex justify-center py-12">
              <div className="animate-spin h-8 w-8 border-4 border-primary border-t-transparent rounded-full"></div>
            </div>
          ) : items.length === 0 ? (
            <div className="text-center py-12 bg-slate-800/30 border border-slate-700 rounded-lg">
              <div className="text-slate-400 mb-2">
                {selectedCategory ? 
                  `You don't have any items in the ${selectedCategory} category yet.` : 
                  "Your inventory is empty."
                }
              </div>
              <button 
                onClick={() => window.location.href = '/dashboard/shop'}
                className="px-4 py-2 bg-primary/80 hover:bg-primary text-white rounded transition-colors"
              >
                Visit Shop
              </button>
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
                      {item.price > 0 && (
                        <div className="px-2 py-1 bg-green-600/20 text-green-300 rounded text-xs">
                          Purchased
                        </div>
                      )}
                    </div>
                    
                    {item.description && (
                      <p className="text-slate-300 text-sm mb-3">{item.description}</p>
                    )}
                    
                    <div className="text-xs text-slate-400 mt-2">
                      Category: {item.category}
                    </div>
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

export default InventoryPage;
