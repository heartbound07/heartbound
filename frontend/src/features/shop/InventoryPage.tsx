import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import { motion, AnimatePresence } from 'framer-motion';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import '@/assets/Inventory.css';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import { formatDisplayText } from '@/utils/formatters';
import { Skeleton } from '@/components/ui/SkeletonUI';

interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  owned: boolean;
  equipped?: boolean;
  rarity: string;
}

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

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

// Inventory Item Skeleton Component
const InventoryItemSkeleton = () => {
  return (
    <div className="inventory-item-card">
      {/* Image skeleton */}
      <div className="inventory-item-image">
        <Skeleton 
          width="100%" 
          height="100%" 
          theme="dashboard"
        />
      </div>
      
      {/* Content skeleton */}
      <div className="inventory-item-content">
        <div className="flex justify-between items-center mb-2">
          <Skeleton 
            width="60%" 
            height="20px" 
            theme="dashboard"
            className="mb-2"
          />
          <Skeleton 
            width="20%" 
            height="16px" 
            theme="dashboard"
            className="rounded"
          />
        </div>
        
        <Skeleton 
          width="100%" 
          height="40px" 
          theme="dashboard"
          className="mb-3"
        />
        
        <div className="flex justify-between items-center">
          <Skeleton 
            width="40%" 
            height="14px" 
            theme="dashboard"
          />
          <Skeleton 
            width="30%" 
            height="28px" 
            theme="dashboard"
            className="rounded"
          />
        </div>
      </div>
    </div>
  );
};

export function InventoryPage() {
  const { user, updateUserProfile } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<ShopItem[]>([]);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);
  
  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
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
  
  useEffect(() => {
    fetchInventory();
  }, [selectedCategory]);
  
  const handleEquipItem = async (itemId: string) => {
    if (actionInProgress) return;
    
    setActionInProgress(itemId);
    try {
      const response = await httpClient.post(`/shop/equip/${itemId}`);
      showToast('Item equipped successfully!', 'success');
      
      // Update local user profile with the updated profile from response
      if (response.data) {
        await updateUserProfile({
          displayName: response.data.displayName || user?.username || '',
          pronouns: response.data.pronouns || '',
          about: response.data.about || '',
          bannerColor: response.data.bannerColor || ''
        });
      }
      
      // Refresh inventory to show updated equipped status
      await fetchInventory();
    } catch (error: any) {
      console.error('Error equipping item:', error);
      showToast(
        error.response?.data?.message || 'Failed to equip item', 
        'error'
      );
    } finally {
      setActionInProgress(null);
    }
  };
  
  const handleUnequipItem = async (category: string) => {
    if (actionInProgress) return;
    
    setActionInProgress(category);
    try {
      const response = await httpClient.post(`/shop/unequip/${category}`);
      showToast('Item unequipped successfully!', 'success');
      
      // Update local user profile with the updated profile from response
      if (response.data) {
        await updateUserProfile({
          displayName: response.data.displayName || user?.username || '',
          pronouns: response.data.pronouns || '',
          about: response.data.about || '',
          bannerColor: response.data.bannerColor || ''
        });
      }
      
      // Refresh inventory to show updated equipped status
      await fetchInventory();
    } catch (error: any) {
      console.error('Error unequipping item:', error);
      showToast(
        error.response?.data?.message || 'Failed to unequip item', 
        'error'
      );
    } finally {
      setActionInProgress(null);
    }
  };
  
  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-8">
        <h1 className="inventory-title">My Inventory</h1>
        <p className="inventory-subtitle">
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
            <div className="inventory-categories">
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
          </div>
        )}
        
        {/* Inventory items */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2 }}
        >
          <h2 className="text-2xl font-bold text-white mb-4">
            {selectedCategory ? `${formatCategoryDisplay(selectedCategory)} Items` : 'All Items'}
          </h2>
          
          {loading ? (
            <div className="inventory-grid">
              {Array.from({ length: 6 }).map((_, index) => (
                <motion.div
                  key={index}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ duration: 0.3, delay: 0.1 * index }}
                >
                  <InventoryItemSkeleton />
                </motion.div>
              ))}
            </div>
          ) : items.length === 0 ? (
            <div className="empty-inventory">
              <div className="empty-inventory-message">
                {selectedCategory ? 
                  `You don't have any items in the ${selectedCategory} category yet.` : 
                  "Your inventory is empty."
                }
              </div>
              <button 
                onClick={() => window.location.href = '/dashboard/shop'}
                className="visit-shop-button"
              >
                Visit Shop
              </button>
            </div>
          ) : (
            <div className="inventory-grid">
              <AnimatePresence mode="popLayout">
                {items.map((item) => {
                  const rarityColor = getRarityColor(item.rarity);
                  
                  return (
                    <motion.div
                      key={item.id}
                      layout
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, scale: 0.95 }}
                      whileHover={{ y: -5 }}
                      className="inventory-item-card"
                      style={{ 
                        borderColor: item.equipped ? 'var(--color-primary, #0088cc)' : rarityColor,
                        borderWidth: '1px'
                      }}
                    >
                      {/* Item image */}
                      <div className="inventory-item-image">
                        {item.imageUrl ? (
                          <img 
                            src={item.imageUrl} 
                            alt={item.name}
                            className="h-full w-full object-cover" 
                          />
                        ) : (
                          <span className="text-slate-400">No Image</span>
                        )}
                        
                        {/* Equipped badge */}
                        {item.equipped && (
                          <div className="equipped-badge">
                            Equipped
                          </div>
                        )}
                        
                        {/* Category badge (in place of rarity badge) */}
                      </div>
                      
                      <div className="inventory-item-content">
                        <div className="flex justify-between items-center mb-2">
                          <div className="flex items-center">
                            <h3 className="font-medium text-white mr-2">{item.name}</h3>
                            <div 
                              className="px-2 py-0.5 rounded text-xs font-semibold"
                              style={getRarityBadgeStyle(item.rarity)}
                            >
                              {getRarityLabel(item.rarity)}
                            </div>
                          </div>
                        </div>
                        
                        {item.description && (
                          <p className="text-slate-300 text-sm mb-3">{item.description}</p>
                        )}
                        
                        <div className="flex justify-between items-center">
                          {/* Always show Purchased badge regardless of price */}
                          <div className="px-2 py-1 bg-green-600/20 text-green-300 rounded text-xs flex items-center">
                            Purchased
                          </div>
                          
                          {/* Equip/Unequip button */}
                          {item.equipped ? (
                            <button
                              onClick={() => handleUnequipItem(item.category)}
                              disabled={actionInProgress !== null}
                              className={`item-action-button unequip-button h-8 flex items-center ${
                                actionInProgress === item.category ? 'opacity-50 cursor-not-allowed' : ''
                              }`}
                            >
                              {actionInProgress === item.category ? 'Processing...' : 'Unequip'}
                            </button>
                          ) : (
                            <button
                              onClick={() => handleEquipItem(item.id)}
                              disabled={actionInProgress !== null}
                              className={`item-action-button equip-button h-8 flex items-center ${
                                actionInProgress === item.id ? 'opacity-50 cursor-not-allowed' : ''
                              }`}
                            >
                              {actionInProgress === item.id ? 'Processing...' : 'Equip'}
                            </button>
                          )}
                        </div>
                      </div>
                    </motion.div>
                  );
                })}
              </AnimatePresence>
            </div>
          )}
        </motion.div>
      </div>
    </div>
  );
}


export default InventoryPage;
