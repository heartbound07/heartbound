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
import React, { forwardRef } from 'react';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';

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
  rarity: string;
}

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

// Shop Item Card Component
const ShopItemCard = forwardRef(({ 
  item, 
  handlePurchase, 
  purchaseInProgress, 
  user,
  isRecentlyPurchased = false
}: { 
  item: ShopItem; 
  handlePurchase: (id: string) => void; 
  purchaseInProgress: boolean;
  user: any;
  isRecentlyPurchased?: boolean;
}, ref) => {
  // Get rarity color for border
  const rarityColor = getRarityColor(item.rarity);
  
  // Add state to track insufficient credits message
  const [showInsufficientCredits, setShowInsufficientCredits] = useState(false);
  
  // Function to handle purchase attempt
  const handlePurchaseAttempt = () => {
    if ((user?.credits ?? 0) < item.price) {
      // Show insufficient credits message
      setShowInsufficientCredits(true);
      
      // Reset after 3 seconds
      setTimeout(() => {
        setShowInsufficientCredits(false);
      }, 3000);
    } else {
      // Proceed with purchase
      handlePurchase(item.id);
    }
  };
  
  return (
    <motion.div
      ref={ref as React.RefObject<HTMLDivElement>}
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -5 }}
      className={`shop-item-card ${isRecentlyPurchased ? 'shop-item-recent-purchase' : ''}`}
      style={{ borderColor: 'transparent' }}
    >
      {/* Item image or Discord preview for USER_COLOR */}
      {item.category === 'USER_COLOR' ? (
        <div className="shop-item-image discord-preview">
          <NameplatePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/default-avatar.png"}
            color={item.imageUrl}
            fallbackColor={rarityColor}
            message="This is the color for the Nameplate!"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          
          {/* Status badges - Removed "Owned" badge, keeping only role requirement badge */}
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          
          {/* Recent purchase effect */}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : (
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
          
          {/* Status badges - Removed "Owned" badge, keeping only role requirement badge */}
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          
          {/* Recent purchase effect */}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      )}
      
      <div className="shop-item-content">
        <div className="flex justify-between items-center mb-2">
          <div className="flex items-center">
            <h3 className="font-medium text-white text-lg mr-2">{item.name}</h3>
            <div 
              className="px-2 py-0.5 rounded text-xs font-semibold"
              style={getRarityBadgeStyle(item.rarity)}
            >
              {getRarityLabel(item.rarity)}
            </div>
          </div>
          <div className="flex items-center">
            <FaCoins className="text-yellow-400 mr-1" size={14} />
            <span className="text-yellow-400 font-medium">{item.price}</span>
          </div>
        </div>
        
        {item.description && (
          <p className="text-slate-300 text-sm mb-3 line-clamp-2">{item.description}</p>
        )}
        
        {/* Enhanced purchase button */}
        {item.owned ? (
          <button 
            disabled
            className="purchase-button purchase-button-owned"
          >
            <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
            Owned
          </button>
        ) : item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) ? (
          <button 
            disabled
            className="purchase-button purchase-button-required"
          >
            <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
            {item.requiredRole} Required
          </button>
        ) : (
          <button 
            onClick={handlePurchaseAttempt}
            disabled={purchaseInProgress || showInsufficientCredits}
            className={`purchase-button ${
              showInsufficientCredits 
                ? 'purchase-button-insufficient' 
                : 'purchase-button-active'
            } ${purchaseInProgress ? 'purchase-button-processing' : ''}`}
          >
            {purchaseInProgress ? (
              <>
                <svg className="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Processing...
              </>
            ) : showInsufficientCredits ? (
              <>
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Not Enough Credits
              </>
            ) : (
              <>
                <FaCoins className="mr-2" size={14} />
                Purchase
              </>
            )}
          </button>
        )}
      </div>
    </motion.div>
  );
});

// Add a display name for better debugging
ShopItemCard.displayName = 'ShopItemCard';

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
  const { user, profile, updateUserProfile } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<ShopItem[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [purchaseInProgress, setPurchaseInProgress] = useState(false);
  const [recentPurchases, setRecentPurchases] = useState<Record<string, number>>({});
  const [sortOrder, setSortOrder] = useState<'default' | 'rarity-asc' | 'rarity-desc' | 'price-asc' | 'price-desc'>('default');
  
  // Define rarity order for sorting
  const RARITY_ORDER: Record<string, number> = {
    'COMMON': 0,
    'UNCOMMON': 1,
    'RARE': 2,
    'EPIC': 3,
    'LEGENDARY': 4
  };
  
  // Minimum loading time in milliseconds
  const MIN_LOADING_TIME = 800;
  
  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  // Combined sort function for shop items
  const sortItems = (itemsToSort: ShopItem[]): ShopItem[] => {
    // Make a copy of the array to avoid mutating the original
    let result = [...itemsToSort];
    
    // Apply sorting based on selected option
    switch (sortOrder) {
      case 'price-asc': // Low to High
        return result.sort((a, b) => a.price - b.price);
        
      case 'price-desc': // High to Low
        return result.sort((a, b) => b.price - a.price);
        
      case 'rarity-asc': // Common to Legendary
        return result.sort((a, b) => {
          // Primary sort: not owned items first
          if (a.owned !== b.owned) {
            return a.owned ? 1 : -1;
          }
          // Secondary sort: by rarity
          const rarityA = RARITY_ORDER[a.rarity] || 0;
          const rarityB = RARITY_ORDER[b.rarity] || 0;
          return rarityA - rarityB;
        });
        
      case 'rarity-desc': // Legendary to Common
        return result.sort((a, b) => {
          // Primary sort: not owned items first
          if (a.owned !== b.owned) {
            return a.owned ? 1 : -1;
          }
          // Secondary sort: by rarity
          const rarityA = RARITY_ORDER[a.rarity] || 0;
          const rarityB = RARITY_ORDER[b.rarity] || 0;
          return rarityB - rarityA;
        });
        
      default: // Default sort (not owned first)
        return result.sort((a) => a.owned ? 1 : -1);
    }
  };
  
  useEffect(() => {
    const fetchShopItems = async () => {
      // Record the start time
      const startTime = Date.now();
      setLoading(true);
      
      try {
        const response = await httpClient.get('/shop/items', {
          params: selectedCategory ? { category: selectedCategory } : {}
        });
        
        // Apply sorting with all criteria
        setItems(sortItems(response.data));
        
      } catch (error) {
        console.error('Error fetching shop items:', error);
        showToast('Failed to load shop items', 'error');
      } finally {
        // Calculate elapsed time
        const elapsedTime = Date.now() - startTime;
        
        // If elapsed time is less than minimum loading time, wait before hiding loader
        if (elapsedTime < MIN_LOADING_TIME) {
          setTimeout(() => {
            setLoading(false);
          }, MIN_LOADING_TIME - elapsedTime);
        } else {
          setLoading(false);
        }
      }
    };
    
    fetchShopItems();
  }, [selectedCategory, sortOrder]);
  
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
      // Store the purchase response which contains updated user profile
      const purchaseResponse = await httpClient.post(`/shop/purchase/${itemId}`);
      showToast('Item purchased successfully!', 'success');
      
      // Mark recent purchase for animation
      setRecentPurchases(prev => ({...prev, [itemId]: Date.now()}));
      
      // Refresh shop items
      const response = await httpClient.get('/shop/items', {
        params: selectedCategory ? { category: selectedCategory } : {}
      });
      setItems(response.data);
      
      // Update user profile to refresh credits while preserving other profile data
      if (purchaseResponse.data) {
        const updatedProfile = purchaseResponse.data;
        await updateUserProfile({
          displayName: updatedProfile.displayName || profile?.displayName || user?.username || '',
          pronouns: updatedProfile.pronouns || profile?.pronouns || '',
          about: updatedProfile.about || profile?.about || '',
          bannerColor: updatedProfile.bannerColor || profile?.bannerColor || '',
          bannerUrl: updatedProfile.bannerUrl || profile?.bannerUrl || '',
          avatar: updatedProfile.avatar || user?.avatar || ''
        });
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
          <div className="flex flex-wrap items-center justify-between mb-4">
            <h2 className="text-2xl font-bold text-white">
              {selectedCategory 
                ? `${formatCategoryDisplay(selectedCategory)} Items` 
                : 'All Items'}
            </h2>
            
            {/* Single consolidated sort dropdown */}
            {!loading && items.length > 0 && (
              <motion.div 
                className="sort-control-container"
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3, delay: 0.4 }}
              >
                <span className="text-sm text-slate-300 mr-2">Sort by:</span>
                <motion.select
                  value={sortOrder}
                  onChange={(e) => setSortOrder(e.target.value as 'default' | 'rarity-asc' | 'rarity-desc' | 'price-asc' | 'price-desc')}
                  className="inventory-sort-dropdown"
                  whileHover={{ scale: 1.03 }}
                  whileTap={{ scale: 0.97 }}
                  transition={{ type: "spring", stiffness: 400, damping: 17 }}
                >
                  <option value="default">Default</option>
                  <option value="rarity-desc">Legendary to Common</option>
                  <option value="rarity-asc">Common to Legendary</option>
                  <option value="price-asc">Low to High</option>
                  <option value="price-desc">High to Low</option>
                </motion.select>
              </motion.div>
            )}
          </div>
          
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
                    isRecentlyPurchased={!!recentPurchases[item.id] && (Date.now() - recentPurchases[item.id] < 5000)}
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
