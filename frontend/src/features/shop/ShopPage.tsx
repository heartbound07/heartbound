import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import { FaCoins } from 'react-icons/fa';
import { motion, AnimatePresence } from 'framer-motion';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import { Role } from '@/contexts/auth/types';
import { formatDisplayText } from '@/utils/formatters';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import '@/assets/shoppage.css';

// Add category mapping for special cases
const categoryDisplayMapping: Record<string, string> = {
  'USER_COLOR': 'Nameplate',
  'LISTING': 'Listing Color',
  'ACCENT': 'Profile Accent'
};

// Format category for display with custom mappings
const formatCategoryDisplay = (category: string): string => {
  return categoryDisplayMapping[category] || formatDisplayText(category);
};

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

// Shop Item Card Component
const ShopItemCard = ({ 
  item, 
  handlePurchase, 
  purchaseInProgress, 
  user 
}: { 
  item: ShopItem; 
  handlePurchase: (id: string) => void; 
  purchaseInProgress: boolean;
  user: any;
}) => {
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -5 }}
      className="shop-item-card"
    >
      {/* Item image */}
      <div className="shop-item-image">
        {item.imageUrl ? (
          <img 
            src={item.imageUrl} 
            alt={item.name}
            className="h-full w-full object-cover" 
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center bg-slate-800/50">
            <span className="text-slate-400">No Image</span>
          </div>
        )}
        
        {/* Status badges */}
        {item.owned && (
          <div className="item-badge badge-owned">
            Owned
          </div>
        )}
        {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
          <div className="item-badge badge-required">
            {item.requiredRole} Required
          </div>
        )}
      </div>
      
      <div className="shop-item-content">
        <div className="flex justify-between items-center mb-2">
          <h3 className="font-medium text-white text-lg">{item.name}</h3>
          <div className="flex items-center">
            <FaCoins className="text-yellow-400 mr-1" size={14} />
            <span className="text-yellow-400 font-medium">{item.price}</span>
          </div>
        </div>
        
        {item.description && (
          <p className="text-slate-300 text-sm mb-3 line-clamp-2">{item.description}</p>
        )}
        
        {item.owned ? (
          <button 
            disabled
            className="purchase-button purchase-button-disabled bg-green-600/30 text-green-300"
          >
            Owned
          </button>
        ) : item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) ? (
          <button 
            disabled
            className="purchase-button purchase-button-disabled bg-amber-600/30 text-amber-300"
          >
            Requires {item.requiredRole} Role
          </button>
        ) : (user?.credits ?? 0) < item.price ? (
          <button 
            disabled
            className="purchase-button purchase-button-disabled bg-red-600/30 text-red-300"
          >
            Not Enough Credits
          </button>
        ) : (
          <button 
            onClick={() => handlePurchase(item.id)}
            disabled={purchaseInProgress}
            className={`purchase-button purchase-button-active ${purchaseInProgress ? 'opacity-70' : ''}`}
          >
            {purchaseInProgress ? 'Processing...' : 'Purchase'}
          </button>
        )}
      </div>
    </motion.div>
  );
};

// Skeleton loader for shop items
const ShopItemSkeleton = () => {
  return (
    <div className="shop-item-card">
      <div className="shop-item-image skeleton"></div>
      <div className="p-4 space-y-2">
        <div className="h-6 w-2/3 skeleton rounded"></div>
        <div className="h-4 w-full skeleton rounded"></div>
        <div className="h-8 w-full skeleton rounded mt-4"></div>
      </div>
    </div>
  );
};

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
      
      // Mark recent purchase for animation
      setRecentPurchases(prev => ({...prev, [itemId]: Date.now()}));
      
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
  
  return (
    <div className="container mx-auto px-4 py-8 shop-container">
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
      
        {/* Centered Shop title with custom font to match Leaderboard */}
        <motion.div className="section-header mb-6 text-center">
          <motion.h1 
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ delay: 0.3, type: "spring" }}
            className="shop-title"
          >
            Shop
          </motion.h1>
          
          {/* Centered credit balance */}
          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.5 }}
            className="credit-balance-container"
          >
            <div className="credit-balance">
              <FaCoins className="credit-balance-icon" size={20} />
              <span className="credit-balance-amount">
                {user?.credits || 0}
              </span>
            </div>
          </motion.div>
        </motion.div>
        
        {/* Categories */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.1 }}
        >
          <h2 className="text-2xl font-bold text-white mb-4">Categories</h2>
          <div className="category-filters">
            <button 
              onClick={() => setSelectedCategory(null)}
              className={`category-button ${
                selectedCategory === null 
                  ? 'category-button-active' 
                  : 'category-button-inactive'
              }`}
            >
              All Items
            </button>
            
            {categories.map((category) => (
              <button 
                key={category}
                onClick={() => setSelectedCategory(category)}
                className={`category-button ${
                  selectedCategory === category 
                    ? 'category-button-active' 
                    : 'category-button-inactive'
                }`}
              >
                {formatCategoryDisplay(category)}
              </button>
            ))}
          </div>
        </motion.div>
        
        {/* Shop items */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2 }}
        >
          <h2 className="text-2xl font-bold text-white mb-4">
            {selectedCategory 
              ? `${formatCategoryDisplay(selectedCategory)} Items` 
              : 'All Items'}
          </h2>
          
          {loading ? (
            // Display skeleton loaders while loading
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 shop-item-grid">
              {Array.from({ length: 6 }).map((_, index) => (
                <ShopItemSkeleton key={index} />
              ))}
            </div>
          ) : items.length === 0 ? (
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="text-center py-12 rounded-lg border border-slate-700/50 bg-slate-800/20"
            >
              <div className="text-slate-400 mb-2">
                No items available in this category.
              </div>
              <button 
                onClick={() => setSelectedCategory(null)}
                className="px-4 py-2 bg-primary/80 hover:bg-primary text-white rounded-lg transition-colors"
              >
                View All Items
              </button>
            </motion.div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 shop-item-grid">
              <AnimatePresence mode="popLayout">
                {items.map((item) => (
                  <ShopItemCard
                    key={item.id}
                    item={item}
                    handlePurchase={handlePurchase}
                    purchaseInProgress={purchaseInProgress}
                    user={user}
                  />
                ))}
              </AnimatePresence>
            </div>
          )}
        </motion.div>
      </div>
    </div>
  );
}

export default ShopPage;
