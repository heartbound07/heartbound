import { useState, useEffect, forwardRef } from 'react';
import { useAuth } from '@/contexts/auth';
import { motion, AnimatePresence } from 'framer-motion';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import { FaInfoCircle } from 'react-icons/fa';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import '@/assets/Inventory.css';
import '@/assets/shoppage.css';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import { formatDisplayText } from '@/utils/formatters';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { CasePreviewModal } from '@/components/ui/shop/CasePreviewModal';
import { CaseRollModal } from '@/components/ui/shop/CaseRollModal';
import { ItemPreview } from '@/components/ui/shop/ItemPreview';
import { SafeText } from '@/components/SafeHtmlRenderer';
import { useSanitizedContent } from '@/hooks/useSanitizedContent';
import React from 'react';

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

// Inventory Item Card Component (based on ShopItemCard design)
const InventoryItemCard = forwardRef(({ 
  item, 
  handleEquip, 
  handleUnequip,
  handleUnequipBadge,
  handleOpenCase,
  actionInProgress, 
  user,
  isSelected = false,
  onSelect,
  onViewCaseContents
}: { 
  item: ShopItem; 
  handleEquip: (id: string) => void;
  handleUnequip: (category: string) => void;
  handleUnequipBadge: (badgeId: string) => void;
  handleOpenCase: (caseId: string, caseName: string) => void;
  actionInProgress: string | null;
  user: any;
  isSelected?: boolean;
  onSelect: (item: ShopItem) => void;
  onViewCaseContents?: (caseId: string, caseName: string) => void;
}, ref) => {
  // Get rarity color for border
  const rarityColor = getRarityColor(item.rarity);
  
  // Sanitize content for safe display
  const nameContent = useSanitizedContent(item.name, { maxLength: 100, stripHtml: true });
  const descriptionContent = useSanitizedContent(item.description, { maxLength: 500, stripHtml: true });
  
  const handleAction = () => {
    if (item.category === 'CASE') {
      handleOpenCase(item.id, nameContent.sanitized);
    } else if (item.category === 'BADGE') {
      if (item.equipped) {
        handleUnequipBadge(item.id);
      } else {
        handleEquip(item.id);
      }
    } else {
      if (item.equipped) {
        handleUnequip(item.category);
      } else {
        handleEquip(item.id);
      }
    }
  };

  const getActionButtonText = () => {
    if (item.category === 'CASE') {
      return (!item.quantity || item.quantity < 1) ? 'No Cases' : 'Open Case';
    }
    return item.equipped ? 'Unequip' : 'Equip';
  };

  const isActionDisabled = () => {
    if (item.category === 'CASE') {
      return !item.quantity || item.quantity < 1 || actionInProgress !== null;
    }
    return actionInProgress !== null;
  };
  
  return (
    <motion.div
      ref={ref as React.RefObject<HTMLDivElement>}
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -5 }}
      className={`shop-item-card inventory-item-card ${isSelected ? 'inventory-item-selected' : ''} ${item.equipped ? 'inventory-item-equipped' : ''}`}
      style={{ borderColor: isSelected ? 'var(--color-primary, #0088cc)' : (item.equipped ? 'var(--color-primary, #0088cc)' : 'transparent') }}
      onClick={() => onSelect(item)}
    >
      {/* Item image or Discord preview for USER_COLOR or BADGE preview */}
      {item.category === 'USER_COLOR' ? (
        <div className="shop-item-image inventory-item-image">
          <NameplatePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/default-avatar.png"}
            color={item.imageUrl}
            fallbackColor={rarityColor}
            message="Your nameplate color"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          
          {/* Equipped badge */}
          {item.equipped && (
            <div className="item-badge badge-equipped">
              Equipped
      </div>
          )}
        </div>
      ) : item.category === 'BADGE' ? (
        <div className="shop-item-image inventory-item-image">
          <BadgePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/default-avatar.png"}
            badgeUrl={item.thumbnailUrl || item.imageUrl}
            message="Your badge"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          
          {/* Equipped badge */}
          {item.equipped && (
            <div className="item-badge badge-equipped">
              Equipped
            </div>
          )}
        </div>
      ) : item.category === 'CASE' ? (
        <div className="shop-item-image inventory-item-image case-preview-container">
          {/* Case visual representation */}
          <div className="h-full w-full bg-gradient-to-br from-slate-700 to-slate-800 flex flex-col items-center justify-center relative overflow-hidden">
            {/* Case icon/visual */}
            <div className="relative z-10">
              {item.imageUrl ? (
                <img 
                  src={item.imageUrl} 
                  alt={nameContent.sanitized}
                  className="h-16 w-16 object-cover rounded-lg border-2"
                  style={{ borderColor: rarityColor }}
                />
              ) : (
                <div 
                  className="h-16 w-16 rounded-lg border-2 flex items-center justify-center"
                  style={{ 
                    borderColor: rarityColor,
                    backgroundColor: `${rarityColor}20`
                  }}
                >
                  <svg 
                    className="w-8 h-8"
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
            {item.category === 'CASE' && item.caseContentsCount && item.caseContentsCount > 0 && (
              <div className="mt-2 text-xs text-slate-300 font-medium">
                Contains {item.caseContentsCount} items
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
              className="absolute inset-0 opacity-20"
              style={{
                background: `radial-gradient(circle at center, ${rarityColor}40 0%, transparent 70%)`
              }}
          />
        </div>
        
          {/* Case-specific badges */}
          <div className="absolute top-2 left-2">
            <div 
              className="px-2 py-1 rounded text-xs font-semibold border"
              style={{
                backgroundColor: `${rarityColor}20`,
                color: rarityColor,
                borderColor: rarityColor
              }}
            >
              Case
            </div>
          </div>
          
          {/* Info icon in top right corner for cases */}
          {item.category === 'CASE' && item.caseContentsCount && item.caseContentsCount > 0 && onViewCaseContents && (
            <div className="absolute top-2 right-2">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onViewCaseContents(item.id, nameContent.sanitized);
                }}
                className="case-info-icon"
                title="View case contents and drop rates"
                aria-label="View case contents and drop rates"
              >
                <FaInfoCircle size={16} />
              </button>
            </div>
          )}
        </div>
      ) : (
        <div className="shop-item-image inventory-item-image">
          {item.imageUrl ? (
            <img 
              src={item.imageUrl} 
              alt={nameContent.sanitized}
              className="h-full w-full object-cover" 
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center bg-slate-800/50">
              <span className="text-slate-400">No Image</span>
            </div>
          )}
          
          {/* Equipped badge */}
          {item.equipped && (
            <div className="item-badge badge-equipped">
              Equipped
            </div>
          )}
        </div>
      )}
      
      <div className="shop-item-content">
        <div className="flex justify-between items-center mb-2">
          <div className="flex items-center">
            <SafeText 
              text={nameContent.sanitized}
              tag="h3"
              className="font-medium text-white text-lg mr-2"
              maxLength={100}
              showTooltip={true}
            />
            <div 
              className="px-2 py-0.5 rounded text-xs font-semibold"
              style={getRarityBadgeStyle(item.rarity)}
            >
              {getRarityLabel(item.rarity)}
            </div>
          </div>
        </div>
        
        {descriptionContent.sanitized && (
          <SafeText 
            text={descriptionContent.sanitized}
            tag="p"
            className="text-slate-300 text-sm mb-3 line-clamp-2"
            maxLength={200}
            showTooltip={true}
          />
        )}
        
        {/* Action button */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            handleAction();
          }}
          disabled={isActionDisabled()}
          className={`purchase-button ${
            item.category === 'CASE' ? 'purchase-button-active' :
            item.equipped ? 'purchase-button-owned' : 'purchase-button-active'
          } ${isActionDisabled() ? 'purchase-button-processing' : ''}`}
        >
          {actionInProgress !== null && (actionInProgress === item.id || actionInProgress === item.category) ? (
            <>
              <svg className="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              Processing...
            </>
          ) : (
            <>
              {item.category === 'CASE' ? (
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
              ) : item.equipped ? (
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              ) : (
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              )}
              {getActionButtonText()}
            </>
          )}
        </button>
        </div>
    </motion.div>
  );
});

// Add a display name for better debugging
InventoryItemCard.displayName = 'InventoryItemCard';

// Skeleton loader for inventory items
const InventoryItemSkeleton = () => {
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

export function InventoryPage() {
  const { user, profile } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<ShopItem[]>([]);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);
  const [sortOrder, setSortOrder] = useState<'default' | 'rarity-asc' | 'rarity-desc'>('default');
  const [selectedItems, setSelectedItems] = useState<{
    nameplate?: ShopItem | null;
    badge?: ShopItem | null;
    [key: string]: ShopItem | null | undefined;
  }>({});
  
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
      await httpClient.post(`/shop/equip/${itemId}`);
      showToast('Item equipped successfully!', 'success');
      
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
      await httpClient.post(`/shop/unequip/${category}`);
      showToast('Item unequipped successfully!', 'success');

      
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
      await httpClient.post(`/shop/unequip/badge/${badgeId}`);
      
      // Update equipped status in the items state
      const updatedItems = items.map(item => {
        if (item.id === badgeId && item.category === 'BADGE') {
          return { ...item, equipped: false };
        }
        return item;
      });
      setItems(updatedItems);
      
      showToast("Badge unequipped successfully!", "success");
    } catch (error) {
      console.error("Failed to unequip badge:", error);
      showToast("Failed to unequip badge", "error");
    } finally {
      setActionInProgress(null);
    }
  };
  
  // Handle equipping multiple items in a single batch operation
  const handleEquipMultipleItems = async (itemIds: string[]) => {
    if (actionInProgress !== null) return;
    
    if (!itemIds || itemIds.length === 0) {
      showToast("No items selected for equipping", "error");
      return;
    }
    
    // Check if multiple badges are being equipped (client-side validation)
    const selectedItemsArray = Object.values(selectedItems).filter(item => item !== null) as ShopItem[];
    const badgeItems = selectedItemsArray.filter(item => item.category === 'BADGE');
    
    if (badgeItems.length > 1) {
      showToast("Only one badge can be equipped at a time. Please select only one badge.", "error");
      return;
    }
    
    setActionInProgress("batch-equip");
    
    try {
      const response = await httpClient.post('/shop/equip/batch', {
        itemIds: itemIds
      });
      
      if (response.data) {
        // The shop batch equip endpoint already updates the user profile internally
        // No need for additional profile update call that causes rate limiting
        
        // Show success message with count
        const itemCount = itemIds.length;
        showToast(
          `Successfully equipped ${itemCount} item${itemCount > 1 ? 's' : ''}!`, 
          'success'
        );
        
        // Clear selected items after successful batch equip
        setSelectedItems({});
        
        // Refresh inventory to show updated equipped status
        await fetchInventory();
      }
    } catch (error: any) {
      console.error('Error batch equipping items:', error);
      const errorMessage = error.response?.data?.message || 'Failed to equip selected items';
      
      // Special handling for batch-specific errors
      if (errorMessage.includes('one badge can be equipped')) {
        showToast('Only one badge can be equipped at a time. Please select only one badge.', 'error');
      } else if (errorMessage.includes('Cannot equip more than')) {
        showToast('Too many items selected. Please select fewer items to equip at once.', 'info');
      } else {
        showToast(errorMessage, 'error');
      }
    } finally {
      setActionInProgress(null);
    }
  };
  
  // Handle item selection for preview
  const handleSelectItem = (item: ShopItem) => {
    setSelectedItems(prev => {
      const newSelected = { ...prev };
      
      if (item.category === 'USER_COLOR') {
        newSelected.nameplate = newSelected.nameplate?.id === item.id ? null : item;
      } else if (item.category === 'BADGE') {
        // For badges, only allow one selection at a time - replace any existing badge selection
        newSelected.badge = newSelected.badge?.id === item.id ? null : item;
      } else {
        // For other categories, use the category as the key
        const categoryKey = item.category.toLowerCase();
        newSelected[categoryKey] = newSelected[categoryKey]?.id === item.id ? null : item;
      }
      
      return newSelected;
    });
  };
  
  // Check if an item is selected
  const isItemSelected = (item: ShopItem) => {
    if (item.category === 'USER_COLOR') {
      return selectedItems.nameplate?.id === item.id;
    } else if (item.category === 'BADGE') {
      return selectedItems.badge?.id === item.id;
    } else {
      const categoryKey = item.category.toLowerCase();
      return selectedItems[categoryKey]?.id === item.id;
    }
  };
  
  return (
    <div className="bg-theme-gradient min-h-screen">
      <div className="container mx-auto px-4 py-8 inventory-page-container">
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
        
        {/* Page Header */}
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8 text-center"
        >
          <h1 className="shop-title inventory-title">My Inventory</h1>
          <p className="text-slate-300 text-lg">
            View and manage all items you've purchased from the shop.
          </p>
        </motion.div>

        {/* Two-Column Layout */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2 }}
          className="inventory-layout-container"
        >
          {/* Right Column - Item Preview Panel (moved before left column for mobile UX) */}
          <div className="inventory-right-column">
            <ItemPreview
              selectedItems={selectedItems}
              user={profile || user}
              onEquip={handleEquipItem}
              onUnequip={handleUnequipItem}
              onUnequipBadge={handleUnequipBadge}
              onOpenCase={openCaseRoll}
              onViewCaseContents={openCasePreview}
              actionInProgress={actionInProgress}
              onEquipMultipleItems={handleEquipMultipleItems}
            />
          </div>

          {/* Left Column - Inventory Grid with Fixed Height and Scroll */}
          <div className="inventory-left-column">
            {/* Categories Filter */}
        <motion.div 
          className="inventory-categories"
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.2 }}
        >
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
        
            {/* Controls Row */}
            <div className="inventory-controls">
              <h2 className="text-xl font-bold text-white">
              {selectedCategory ? `${formatCategoryDisplay(selectedCategory)} Items` : 'All Items'}
            </h2>
            
              {/* Sort controls */}
            {!loading && items.length > 0 && (
                <div className="sort-control-container">
                <span className="text-sm text-slate-300 mr-2">Sort by:</span>
                  <select
                  value={sortOrder}
                  onChange={(e) => setSortOrder(e.target.value as 'default' | 'rarity-asc' | 'rarity-desc')}
                  className="inventory-sort-dropdown"
                >
                  <option value="default">Default (Equipped first)</option>
                  <option value="rarity-desc">Legendary to Common</option>
                  <option value="rarity-asc">Common to Legendary</option>
                  </select>
                </div>
            )}
          </div>
          
            {/* Scrollable Inventory Grid */}
            <div className="inventory-grid-container">
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
                  <div className="empty-inventory-icon">
                    <svg className="w-16 h-16 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                    </svg>
                  </div>
                  <h3 className="empty-inventory-title">
                {selectedCategory ? 
                      `No ${formatCategoryDisplay(selectedCategory)} items` : 
                      "Your inventory is empty"
                    }
                  </h3>
                  <p className="empty-inventory-message">
                    {selectedCategory ? 
                      `You don't have any items in the ${formatCategoryDisplay(selectedCategory)} category yet.` : 
                      "Start shopping to collect amazing items!"
                    }
                  </p>
              <motion.button 
                onClick={() => window.location.href = '/shop'}
                className="visit-shop-button"
                    whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                transition={{ type: "spring", stiffness: 400, damping: 17 }}
              >
                Visit Shop
              </motion.button>
            </motion.div>
          ) : (
                <div className="inventory-grid">
                  <AnimatePresence mode="popLayout">
                    {items.map((item) => (
                      <InventoryItemCard
                          key={item.id}
                        item={item}
                        handleEquip={handleEquipItem}
                        handleUnequip={handleUnequipItem}
                        handleUnequipBadge={handleUnequipBadge}
                        handleOpenCase={openCaseRoll}
                        actionInProgress={actionInProgress}
                        user={profile || user}
                        isSelected={isItemSelected(item)}
                        onSelect={handleSelectItem}
                        onViewCaseContents={openCasePreview}
                      />
                    ))}
                  </AnimatePresence>
                                      </div>
                                    )}
                                  </div>
                                    </div>
                        </motion.div>
    </div>
    
    {/* Case Preview Modal */}
    <CasePreviewModal
      isOpen={casePreviewModal.isOpen}
      onClose={closeCasePreview}
      caseId={casePreviewModal.caseId}
      caseName={casePreviewModal.caseName}
      user={profile || user}
    />
    
    {/* Case Roll Modal */}
    <CaseRollModal
      isOpen={caseRollModal.isOpen}
      onClose={closeCaseRoll}
      caseId={caseRollModal.caseId}
      caseName={caseRollModal.caseName}
      onRollComplete={handleRollComplete}
      user={profile || user}
    />
    </div>
  );
}

export default InventoryPage;
