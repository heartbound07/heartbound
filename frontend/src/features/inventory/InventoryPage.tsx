import { useState, useEffect, useMemo } from 'react';
import { useAuth } from '@/contexts/auth';
import { motion } from 'framer-motion';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import '@/assets/Inventory.css';
import '@/assets/shoppage.css';
import { CasePreviewModal } from '@/components/ui/shop/CasePreviewModal';
import { CaseRollModal } from '@/components/ui/shop/CaseRollModal';
import { ItemPreview } from '@/components/ui/shop/ItemPreview';
import { ShopItem, ToastNotification, RollResult } from '@/types/inventory';
import { UserProfileDTO } from '@/config/userService';
import { InventoryFilters } from './components/InventoryFilters';
import { InventoryControls } from './components/InventoryControls';
import { InventoryGrid } from './components/InventoryGrid';
import { FishingRodPartsModal } from './components/FishingRodPartsModal';


export function InventoryPage() {
  const { user, profile, updateProfile } = useAuth();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<ShopItem[]>([]);
  const [fishingRodParts, setFishingRodParts] = useState<ShopItem[]>([]);
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
  
  const [partsModal, setPartsModal] = useState<{
    isOpen: boolean;
    rod: ShopItem | null;
  }>({
    isOpen: false,
    rod: null,
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

  const openPartsModal = (rod: ShopItem) => {
    setPartsModal({ isOpen: true, rod });
  };

  const closePartsModal = () => {
    setPartsModal({ isOpen: false, rod: null });
  };

  const handleEquipRodPart = async (rodId: string, partInstanceId: string) => {
    if (actionInProgress) return;
    setActionInProgress(`equip-part-${rodId}`);
    try {
      const response = await httpClient.post<UserProfileDTO>(`/inventory/rod/${rodId}/equip-part`, { partInstanceId });
      if (response.data) {
        updateProfile(response.data);
      }
      showToast('Fishing rod part equipped successfully!', 'success');
      await fetchInventory(); // Refresh inventory
      closePartsModal(); // Close the modal on success
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to equip fishing rod part';
      showToast(errorMessage, 'error');
    } finally {
      setActionInProgress(null);
    }
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
        const allItems: ShopItem[] = response.data.items;
        const displayableItems = allItems.filter(item => item.category !== 'FISHING_ROD_PART');
        const parts = allItems.filter(item => item.category === 'FISHING_ROD_PART');
        
        setFishingRodParts(parts);

        const uniqueCategories = [...new Set(displayableItems.map((item: ShopItem) => item.category))] as string[];
        setCategories(uniqueCategories);
        
        // Filter items by category if selected
        const categoryFiltered = selectedCategory 
          ? displayableItems.filter((item: ShopItem) => item.category === selectedCategory)
          : displayableItems;
        
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

  const availablePartsForModal = useMemo(() => {
    if (!partsModal.rod) return [];
    const equippedInstanceIds = new Set(
        Object.values(partsModal.rod.equippedParts || {}).map(p => p.instanceId)
    );
    return fishingRodParts.filter(p => !equippedInstanceIds.has(p.instanceId));
  }, [partsModal.rod, fishingRodParts]);
  
  const handleEquipItem = async (itemId: string, instanceId?: string) => {
    if (actionInProgress) return;
    
    setActionInProgress(instanceId || itemId);
    try {
      const endpoint = instanceId ? `/shop/equip/instance/${instanceId}` : `/shop/equip/${itemId}`;
      const response = await httpClient.post<UserProfileDTO>(endpoint);
      
      if (response.data) {
        updateProfile(response.data);
      }

      showToast('Item equipped successfully!', 'success');
      
      // Clear selection and refresh inventory to show updated equipped status
      setSelectedItems({});
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
      const response = await httpClient.post<UserProfileDTO>(`/shop/unequip/${category}`);

      if (response.data) {
        updateProfile(response.data);
      }
      showToast('Item unequipped successfully!', 'success');

      
      // Clear selection and refresh inventory to show updated equipped status
      setSelectedItems({});
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
      const response = await httpClient.post<UserProfileDTO>(`/shop/unequip/badge/${badgeId}`);
      
      if (response.data) {
        updateProfile(response.data);
      }
      
      showToast("Badge unequipped successfully!", "success");
      
      // Clear selection and refresh inventory
      setSelectedItems({});
      await fetchInventory();
      
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
      const response = await httpClient.post<UserProfileDTO>('/shop/equip/batch', {
        itemIds: itemIds
      });
      
      if (response.data) {
        updateProfile(response.data);
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
  
  // Handle unequipping multiple items in a single batch operation
  const handleUnequipMultipleItems = async (itemIds: string[]) => {
    if (actionInProgress !== null) return;

    if (!itemIds || itemIds.length === 0) {
      showToast("No items selected for unequipping", "error");
      return;
    }

    setActionInProgress("batch-unequip");

    try {
      const response = await httpClient.post<UserProfileDTO>('/shop/unequip/batch', {
        itemIds: itemIds
      });

      if (response.data) {
        updateProfile(response.data);
        const itemCount = itemIds.length;
        showToast(
          `Successfully unequipped ${itemCount} item${itemCount > 1 ? 's' : ''}!`,
          'success'
        );
        setSelectedItems({});
        await fetchInventory();
      }
    } catch (error: any) {
      console.error('Error batch unequipping items:', error);
      const errorMessage = error.response?.data?.message || 'Failed to unequip selected items';
      showToast(errorMessage, 'error');
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
      
      // Check for mixed selection (equipped and unequipped items)
      const selectionAsArray = Object.values(newSelected).filter(i => i !== null) as ShopItem[];
      const hasEquippedItems = selectionAsArray.some(i => i.equipped);
      const hasUnequippedItems = selectionAsArray.some(i => !i.equipped);

      // If selection is mixed, deselect all equipped items, keeping only the unequipped ones.
      if (hasEquippedItems && hasUnequippedItems) {
        const unequippedOnlySelection: typeof newSelected = {};
        Object.entries(newSelected).forEach(([key, value]) => {
          if (value && !value.equipped) {
            unequippedOnlySelection[key as keyof typeof newSelected] = value;
          }
        });
        return unequippedOnlySelection;
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
              onUnequipMultipleItems={handleUnequipMultipleItems}
            />
          </div>

          {/* Left Column - Inventory Grid with Fixed Height and Scroll */}
          <div className="inventory-left-column">
            <InventoryFilters
              categories={categories}
              selectedCategory={selectedCategory}
              onSelectCategory={setSelectedCategory}
            />
            
            <InventoryControls
              selectedCategory={selectedCategory}
              sortOrder={sortOrder}
              onSortChange={setSortOrder}
              itemCount={items.length}
              loading={loading}
            />
            
            <InventoryGrid
              loading={loading}
              items={items}
              selectedCategory={selectedCategory}
              user={profile || user}
              isItemSelected={isItemSelected}
              onSelectItem={handleSelectItem}
              onOpenPartsModal={openPartsModal}
            />
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

    <FishingRodPartsModal
        isOpen={partsModal.isOpen}
        onClose={closePartsModal}
        rod={partsModal.rod}
        parts={availablePartsForModal}
        onEquipPart={handleEquipRodPart}
    />
    </div>
  );
}

export default InventoryPage;
