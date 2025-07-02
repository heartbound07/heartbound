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
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import BadgeGallery from '@/components/ui/shop/BadgeGallery';
import { CasePreviewModal } from '@/components/ui/shop/CasePreviewModal';
import { CaseRollModal } from '@/components/ui/shop/CaseRollModal';

export interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  thumbnailUrl?: string;
  owned: boolean;
  equipped?: boolean;
  rarity: string;
  isCase?: boolean;
  caseContentsCount?: number;
  quantity?: number;
}

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface RollResult {
  caseId: string;
  caseName: string;
  wonItem: {
    id: string;
    name: string;
    description: string;
    price: number;
    category: string;
    imageUrl: string;
    thumbnailUrl?: string;
    rarity: string;
    owned: boolean;
  };
  rollValue: number;
  rolledAt: string;
  alreadyOwned: boolean;
  compensationAwarded?: boolean;
  compensatedCredits?: number;
  compensatedXp?: number;
}

// Add category mapping for special cases
const categoryDisplayMapping: Record<string, string> = {
  'USER_COLOR': 'Nameplate',
  'LISTING': 'Listing Color',
  'ACCENT': 'Profile Accent',
  'BADGE': 'Badge',
  'CASE': 'Case'
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
  const { user, updateUserProfile, profile } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<ShopItem[]>([]);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);
  // Update type to remove price-based options
  const [sortOrder, setSortOrder] = useState<'default' | 'rarity-asc' | 'rarity-desc'>('default');
  
  // Case preview modal state
  const [casePreviewModal, setCasePreviewModal] = useState<{
    isOpen: boolean;
    caseId: string;
    caseName: string;
  }>({
    isOpen: false,
    caseId: '',
    caseName: ''
  });
  
  // Case roll modal state
  const [caseRollModal, setCaseRollModal] = useState<{
    isOpen: boolean;
    caseId: string;
    caseName: string;
  }>({
    isOpen: false,
    caseId: '',
    caseName: ''
  });
  
  // Define rarity order for sorting
  const RARITY_ORDER: Record<string, number> = {
    'COMMON': 0,
    'UNCOMMON': 1,
    'RARE': 2,
    'EPIC': 3,
    'LEGENDARY': 4
  };
  
  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  // Case preview modal functions
  const openCasePreview = (caseId: string, caseName: string) => {
    setCasePreviewModal({
      isOpen: true,
      caseId,
      caseName
    });
  };
  
  const closeCasePreview = () => {
    setCasePreviewModal({
      isOpen: false,
      caseId: '',
      caseName: ''
    });
  };
  
  // Case roll modal functions
  const openCaseRoll = (caseId: string, caseName: string) => {
    setCaseRollModal({
      isOpen: true,
      caseId,
      caseName
    });
  };
  
  const closeCaseRoll = () => {
    setCaseRollModal({
      isOpen: false,
      caseId: '',
      caseName: ''
    });
  };
  
  const handleRollComplete = async (result: RollResult) => {
    // Show enhanced success toast with compensation info
    if (result.alreadyOwned && result.compensationAwarded) {
      showToast(
        `Congratulations! You won ${result.wonItem.name} (duplicate) and received ${result.compensatedCredits} credits + ${result.compensatedXp} XP as compensation!`, 
        'success'
      );
    } else if (result.alreadyOwned) {
      showToast(
        `Congratulations! You won ${result.wonItem.name} (already owned)!`, 
        'success'
      );
    } else {
      showToast(
        `Congratulations! You won ${result.wonItem.name}!`, 
        'success'
      );
    }
    
    // Refresh inventory to show updated items
    await fetchInventory();
  };
  
  // Simplified sort function without price sorting options
  const sortItems = (itemsToSort: ShopItem[]): ShopItem[] => {
    // Make a copy to avoid mutating the original
    let result = [...itemsToSort];
    
    // Apply sorting based on selected option
    switch (sortOrder) {
      case 'rarity-asc': // Common to Legendary
        return result.sort((a, b) => {
          // Primary sort: equipped items first
          if (a.equipped !== b.equipped) {
            return a.equipped ? -1 : 1;
          }
          // Secondary sort: by rarity
          const rarityA = RARITY_ORDER[a.rarity] || 0;
          const rarityB = RARITY_ORDER[b.rarity] || 0;
          return rarityA - rarityB;
        });
        
      case 'rarity-desc': // Legendary to Common
        return result.sort((a, b) => {
          // Primary sort: equipped items first
          if (a.equipped !== b.equipped) {
            return a.equipped ? -1 : 1;
          }
          // Secondary sort: by rarity
          const rarityA = RARITY_ORDER[a.rarity] || 0;
          const rarityB = RARITY_ORDER[b.rarity] || 0;
          return rarityB - rarityA;
        });
        
      default: // Default sorting (equipped first)
        return result.sort((a, b) => {
          return a.equipped && !b.equipped ? -1 : 
                 !a.equipped && b.equipped ? 1 : 0;
        });
    }
  };
  
  const fetchInventory = async () => {
    setLoading(true);
    try {
      const response = await httpClient.get('/shop/inventory');
      
      if (response.data && response.data.items) {
        // Extract unique categories
        const uniqueCategories = [...new Set(response.data.items.map((item: ShopItem) => item.category))] as string[];
        setCategories(uniqueCategories);
        
        // Filter items by category if selected
        const categoryFiltered = selectedCategory 
          ? response.data.items.filter((item: ShopItem) => item.category === selectedCategory)
          : response.data.items;
        
        // Apply sorting
        setItems(sortItems(categoryFiltered));
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
  
  // Modify useEffect for sorting when the sort order changes
  useEffect(() => {
    if (items.length > 0) {
      setItems(sortItems([...items]));
    }
  }, [sortOrder]);
  
  const handleEquipItem = async (itemId: string) => {
    if (actionInProgress) return;
    
    setActionInProgress(itemId);
    try {
      const response = await httpClient.post(`/shop/equip/${itemId}`);
      showToast('Item equipped successfully!', 'success');
      
      // Update local user profile with the updated profile from response
      if (response.data) {
        await updateUserProfile({
          displayName: response.data.displayName || profile?.displayName || user?.username || '',
          pronouns: response.data.pronouns || profile?.pronouns || '',
          about: response.data.about || profile?.about || '',
          bannerColor: response.data.bannerColor || profile?.bannerColor || '',
          bannerUrl: response.data.bannerUrl || profile?.bannerUrl || '',
          avatar: response.data.avatar || user?.avatar || ''
        });
      }
      
      // Refresh inventory to show updated equipped status
      await fetchInventory();
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to equip item';
      
      // Special handling for badge limit errors
      if (errorMessage.includes('Maximum number of badges')) {
        showToast('You\'ve reached the maximum number of equipped badges. Please unequip a badge first.', 'info');
      } else {
        showToast(errorMessage, 'error');
      }
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
          displayName: response.data.displayName || profile?.displayName || user?.username || '',
          pronouns: response.data.pronouns || profile?.pronouns || '',
          about: response.data.about || profile?.about || '',
          bannerColor: response.data.bannerColor || profile?.bannerColor || '',
          bannerUrl: response.data.bannerUrl || profile?.bannerUrl || '',
          avatar: response.data.avatar || user?.avatar || ''
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
  
  // Add a function to handle badge unequipping
  const handleUnequipBadge = async (badgeId: string) => {
    if (actionInProgress !== null) return;
    
    setActionInProgress(badgeId);
    
    try {
      const response = await httpClient.post(`/shop/unequip/badge/${badgeId}`);
      if (response.data) {
        // Update user profile
        updateUserProfile(response.data);
        
        // Update equipped status in the items state
        const updatedItems = items.map(item => {
          if (item.id === badgeId && item.category === 'BADGE') {
            return { ...item, equipped: false };
          }
          return item;
        });
        setItems(updatedItems);
        
        showToast("Badge unequipped successfully!", "success");
      }
    } catch (error) {
      console.error("Failed to unequip badge:", error);
      showToast("Failed to unequip badge", "error");
    } finally {
      setActionInProgress(null);
    }
  };
  
  // Filter badges from items
  const badgeItems = items.filter(item => item.category === 'BADGE');
  
  return (
    <div className="bg-theme-gradient min-h-screen">
      <div className="container mx-auto px-4 py-8">
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
      
      <div className="inventory-container">
        {/* Categories */}
        <motion.div 
          className="inventory-categories"
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.2 }}
        >
          {/* Add a horizontal scroll container for small screens */}
          <div className="inventory-categories-scroll-container">
            <motion.button
              key="all"
              onClick={() => setSelectedCategory(null)}
              className={`category-button ${
                selectedCategory === null ? 'category-button-active' : 'category-button-inactive'
              }`}
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
              transition={{ type: "spring", stiffness: 400, damping: 17 }}
            >
              All Items
            </motion.button>
            
            {categories.map((category) => (
              <motion.button
                key={category}
                onClick={() => setSelectedCategory(category)}
                className={`category-button ${
                  selectedCategory === category ? 'category-button-active' : 'category-button-inactive'
                }`}
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
                transition={{ type: "spring", stiffness: 400, damping: 17 }}
              >
                {formatCategoryDisplay(category)}
              </motion.button>
            ))}
          </div>
        </motion.div>
        
        {/* Inventory items */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2 }}
        >
          <div className="flex flex-wrap items-center justify-between mb-4">
            <h2 className="text-2xl font-bold text-white">
              {selectedCategory ? `${formatCategoryDisplay(selectedCategory)} Items` : 'All Items'}
            </h2>
            
            {/* Single consolidated sort dropdown with fewer options */}
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
                  onChange={(e) => setSortOrder(e.target.value as 'default' | 'rarity-asc' | 'rarity-desc')}
                  className="inventory-sort-dropdown"
                  whileHover={{ scale: 1.03 }}
                  whileTap={{ scale: 0.97 }}
                  transition={{ type: "spring", stiffness: 400, damping: 17 }}
                >
                  <option value="default">Default (Equipped first)</option>
                  <option value="rarity-desc">Legendary to Common</option>
                  <option value="rarity-asc">Common to Legendary</option>
                </motion.select>
              </motion.div>
            )}
          </div>
          
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
            <motion.div 
              className="empty-inventory"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.5, delay: 0.3 }}
            >
              <div className="empty-inventory-message">
                {selectedCategory ? 
                  `You don't have any items in the ${selectedCategory} category yet.` : 
                  "Your inventory is empty."
                }
              </div>
              <motion.button 
                onClick={() => window.location.href = '/shop'}
                className="visit-shop-button"
                whileHover={{ scale: 1.05, backgroundColor: 'rgba(var(--color-primary-rgb, 0, 136, 204), 0.9)' }}
                whileTap={{ scale: 0.95 }}
                transition={{ type: "spring", stiffness: 400, damping: 17 }}
              >
                Visit Shop
              </motion.button>
            </motion.div>
          ) : (
            <motion.div
              className="inventory-content"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.3 }}
            >
              {/* Render BadgeGallery when BADGE category is selected */}
              {selectedCategory === 'BADGE' ? (
                <BadgeGallery
                  badges={badgeItems}
                  onEquip={handleEquipItem}
                  onUnequip={handleUnequipBadge}
                  actionInProgress={actionInProgress}
                />
              ) : (
                // Existing grid view for non-badge categories or All
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
                          transition={{ 
                            type: "spring",
                            stiffness: 300, 
                            damping: 24,
                            duration: 0.4 
                          }}
                          whileHover={{ y: -5, transition: { duration: 0.2 } }}
                          className="inventory-item-card"
                        >
                          {/* Animated rarity glow effect */}
                          <motion.div
                            className={`rarity-glow ${item.equipped ? 'rarity-glow-equipped' : ''}`}
                            initial={{ opacity: 0 }}
                            animate={{ 
                              opacity: [0.4, 0.8, 0.4],
                              boxShadow: [
                                `0 0 5px 1px ${rarityColor}80`,
                                `0 0 10px 2px ${rarityColor}90`,
                                `0 0 5px 1px ${rarityColor}80`
                              ]
                            }}
                            transition={{
                              duration: 3,
                              repeat: Infinity,
                              repeatType: "reverse",
                              ease: "easeInOut"
                            }}
                          />
                          
                          {/* Item content with border */}
                          <div 
                            className="inventory-item-inner"
                            style={{ 
                              borderColor: item.equipped ? 'var(--color-primary, #0088cc)' : rarityColor
                            }}
                          >
                            {/* Item image, nameplate preview, badge preview, or case preview */}
                            <div className="inventory-item-image">
                              {item.category === 'USER_COLOR' ? (
                                <NameplatePreview
                                  username={user?.username || "Username"}
                                  avatar={user?.avatar || "/default-avatar.png"}
                                  color={item.imageUrl}
                                  fallbackColor={getRarityColor(item.rarity)}
                                  message="Your equipped nameplate color"
                                  className="h-full w-full"
                                  size="sm"
                                />
                              ) : item.category === 'BADGE' ? (
                                <BadgePreview
                                  username={user?.username || "Username"}
                                  avatar={user?.avatar || "/default-avatar.png"}
                                  badgeUrl={item.thumbnailUrl || item.imageUrl}
                                  message="Your badge"
                                  className="h-full w-full"
                                  size="sm"
                                />
                              ) : item.category === 'CASE' ? (
                                <div className="h-full w-full bg-gradient-to-br from-slate-700 to-slate-800 flex flex-col items-center justify-center relative overflow-hidden">
                                  {/* Case icon/visual */}
                                  <div className="relative z-10">
                                    {item.imageUrl ? (
                                      <img 
                                        src={item.imageUrl} 
                                        alt={item.name}
                                        className="h-12 w-12 object-cover rounded-lg border-2"
                                        style={{ borderColor: rarityColor }}
                                      />
                                    ) : (
                                      <div 
                                        className="h-12 w-12 rounded-lg border-2 flex items-center justify-center case-icon"
                                        style={{ 
                                          borderColor: rarityColor,
                                          backgroundColor: `${rarityColor}20`
                                        }}
                                      >
                                        <svg 
                                          className="w-6 h-6"
                                          style={{ color: rarityColor }}
                                          fill="none" 
                                          viewBox="0 0 24 24" 
                                          stroke="currentColor"
                                        >
                                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                                        </svg>
                                      </div>
                                    )}
                                  </div>
                                  
                                  {/* Case contents count */}
                                  {item.isCase && item.caseContentsCount && item.caseContentsCount > 0 && (
                                    <div className="mt-1 text-xs text-slate-300 font-medium">
                                      {item.caseContentsCount} items
                                    </div>
                                  )}
                                  
                                  {/* Case quantity display */}
                                  {item.category === 'CASE' && item.quantity && item.quantity > 1 && (
                                    <div className="mt-1 text-xs text-primary font-bold">
                                      x{item.quantity} Cases
                                    </div>
                                  )}
                                  
                                  {/* Mystical background effect */}
                                  <div 
                                    className="absolute inset-0 opacity-20 case-mystical-effect"
                                    style={{
                                      background: `radial-gradient(circle at center, ${rarityColor}40 0%, transparent 70%)`
                                    }}
                                  />
                                  
                                  {/* View Contents button overlay for cases */}
                                  {item.isCase && item.caseContentsCount && item.caseContentsCount > 0 && (
                                    <div className="absolute bottom-1 right-1">
                                      <button
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          openCasePreview(item.id, item.name);
                                        }}
                                        className="px-1 py-0.5 bg-primary/90 hover:bg-primary text-white text-xs rounded-md transition-colors flex items-center case-view-button"
                                      >
                                        <svg className="w-2.5 h-2.5 mr-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                                        </svg>
                                        View
                                      </button>
                                    </div>
                                  )}
                                </div>
                              ) : item.imageUrl ? (
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
                              
                              {/* Case-specific content information */}
                              {item.isCase && item.caseContentsCount && item.caseContentsCount > 0 && (
                                <div className="mb-3 p-2 bg-slate-800/30 border border-slate-700/50 rounded-md">
                                  <div className="flex items-center justify-between text-xs mb-2">
                                    <span className="text-slate-400">Case Contents:</span>
                                    <span className="text-slate-300 font-medium">{item.caseContentsCount} items</span>
                                  </div>
                                  {item.quantity && item.quantity > 1 && (
                                    <div className="flex items-center justify-between text-xs mb-2">
                                      <span className="text-slate-400">Quantity Owned:</span>
                                      <span className="text-primary font-medium">x{item.quantity}</span>
                                    </div>
                                  )}
                                  <button
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      openCasePreview(item.id, item.name);
                                    }}
                                    className="w-full py-1 text-xs text-primary hover:text-primary/80 transition-colors flex items-center justify-center"
                                  >
                                    <svg className="w-3 h-3 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                                    </svg>
                                    View Contents & Drop Rates
                                  </button>
                                </div>
                              )}
                              
                              <div className="flex justify-between items-center">
                                {/* Always show Purchased badge regardless of price */}
                                <div className="px-2 py-1 bg-green-600/20 text-green-300 rounded text-xs flex items-center">
                                  Purchased
                                </div>
                                
                                {/* Equip/Unequip button or Case actions */}
                                {item.category === 'CASE' ? (
                                  <button
                                    onClick={() => openCaseRoll(item.id, item.name)}
                                    disabled={actionInProgress !== null || !item.quantity || item.quantity < 1}
                                    className={`item-action-button equip-button h-8 flex items-center ${
                                      actionInProgress !== null || !item.quantity || item.quantity < 1 ? 'opacity-50 cursor-not-allowed' : ''
                                    }`}
                                  >
                                    {(!item.quantity || item.quantity < 1) ? 'No Cases' : 'Open Case'}
                                  </button>
                                ) : item.category === 'BADGE' ? (
                                  item.equipped ? (
                                    <button
                                      onClick={() => handleUnequipBadge(item.id)}
                                      disabled={actionInProgress !== null}
                                      className={`item-action-button unequip-button h-8 flex items-center ${
                                        actionInProgress === item.id ? 'opacity-50 cursor-not-allowed' : ''
                                      }`}
                                    >
                                      {actionInProgress === item.id ? 'Processing...' : 'Unequip'}
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
                                  )
                                ) : (
                                  item.equipped ? (
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
                                  )
                                )}
                              </div>
                            </div>
                          </div>
                        </motion.div>
                      );
                    })}
                  </AnimatePresence>
                </div>
              )}
            </motion.div>
          )}
        </motion.div>
      </div>
    </div>
    
    {/* Case Preview Modal */}
    <CasePreviewModal
      isOpen={casePreviewModal.isOpen}
      onClose={closeCasePreview}
      caseId={casePreviewModal.caseId}
      caseName={casePreviewModal.caseName}
      user={user}
    />
    
    {/* Case Roll Modal */}
    <CaseRollModal
      isOpen={caseRollModal.isOpen}
      onClose={closeCaseRoll}
      caseId={caseRollModal.caseId}
      caseName={caseRollModal.caseName}
      onRollComplete={handleRollComplete}
      user={user}
    />
    </div>
  );
}

export default InventoryPage;
