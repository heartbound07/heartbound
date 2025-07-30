import { useState, useEffect, useMemo } from 'react';
import { useAuth } from '@/contexts/auth';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import ShopItemsTable from '@/features/shop/components/ShopItemsTable';
import ShopFilters from '@/features/shop/components/ShopFilters';
import ItemFormModal from '@/features/shop/components/ItemFormModal';
import ItemOwnersModal from '@/features/shop/components/ItemOwnersModal';
import { 
  HiOutlineShoppingCart, 
  HiOutlinePlus
} from 'react-icons/hi';

interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  thumbnailUrl?: string;
  requiredRole: string | null;
  expiresAt: string | null;
  active: boolean;
  expired: boolean;
  isDeleting?: boolean;
  discordRoleId?: string;
  rarity: string;
  isFeatured: boolean;
  isDaily: boolean;
  fishingRodMultiplier?: number;
  gradientEndColor?: string;
  maxCopies?: number;
  copiesSold?: number;
  maxDurability?: number;
  fishingRodPartType?: string;
  durabilityIncrease?: number;
  bonusLootChance?: number;
  rarityChanceIncrease?: number;
  multiplierIncrease?: number;
  negationChance?: number;
  maxRepairs?: number;
}

interface ShopFormData {
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  thumbnailUrl?: string;
  requiredRole: string | null;
  expiresAt: string | null;
  active: boolean;
  discordRoleId?: string;
  rarity: string;
  isFeatured: boolean;
  isDaily: boolean;
  fishingRodMultiplier?: number;
  fishingRodPartType?: string;
  colorType: 'solid' | 'gradient';
  gradientEndColor?: string;
  maxCopies?: number;
  maxDurability?: number;
  durabilityIncrease?: number;
  bonusLootChance?: number;
  rarityChanceIncrease?: number;
  multiplierIncrease?: number;
  negationChance?: number;
  maxRepairs?: number;
}

interface CaseItemData {
  id?: string;
  containedItem: ShopItem;
  dropRate: number;
}

interface CaseContents {
  items: CaseItemData[];
  totalDropRate: number;
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
  
  // Case-specific state
  const [caseContents, setCaseContents] = useState<CaseContents>({ items: [], totalDropRate: 0 });
  const [availableItems, setAvailableItems] = useState<ShopItem[]>([]);
  const [loadingCaseContents, setLoadingCaseContents] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  
  // Owners modal state
  const [isOwnersModalOpen, setIsOwnersModalOpen] = useState(false);
  const [itemOwners, setItemOwners] = useState<any[]>([]);
  const [loadingOwners, setLoadingOwners] = useState(false);
  const [selectedItemName, setSelectedItemName] = useState('');

  const [filters, setFilters] = useState({
    search: '',
    category: '',
    rarity: '',
    status: '',
    isFeatured: false,
    isDaily: false,
  });
  
  const [formData, setFormData] = useState<ShopFormData>({
    name: '',
    description: '',
    price: 0,
    category: '',
    imageUrl: '',
    thumbnailUrl: '',
    requiredRole: null,
    expiresAt: null,
    active: true,
    discordRoleId: '',
    rarity: 'COMMON',
    isFeatured: false,
    isDaily: false,
    fishingRodMultiplier: 1.0,
    fishingRodPartType: '',
    colorType: 'solid',
    gradientEndColor: '',
    maxCopies: undefined,
    maxDurability: undefined,
    durabilityIncrease: undefined,
    bonusLootChance: undefined,
    rarityChanceIncrease: undefined,
    multiplierIncrease: undefined,
    negationChance: undefined,
    maxRepairs: undefined
  });
  
  // Available categories
  const categories = ['USER_COLOR', 'LISTING', 'ACCENT', 'BADGE', 'CASE', 'FISHING_ROD', 'FISHING_ROD_PART'];
  
  // Available roles for role-restricted items
  const roles = ['ADMIN', 'MODERATOR', 'MONARCH'];
  
  // Add this after the roles array
  const rarities = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY'];
  const fishingRodParts = ['ROD_SHAFT', 'REEL', 'FISHING_LINE', 'HOOK', 'GRIP'];

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
      
      // Also update available items for case management (exclude cases themselves)
      const nonCaseItems = response.data.filter((item: ShopItem) => item.category !== 'CASE');
      setAvailableItems(nonCaseItems);
      
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

    const handleViewOwners = async (itemId: string, itemName: string) => {
      setLoadingOwners(true);
      setSelectedItemName(itemName);
      setIsOwnersModalOpen(true);
      try {
        const response = await httpClient.get(`/users/admin/items/${itemId}/owners`);
        setItemOwners(response.data);
      } catch (error) {
        console.error('Error fetching item owners:', error);
        showToast('Failed to fetch item owners', 'error');
        setIsOwnersModalOpen(false);
      } finally {
        setLoadingOwners(false);
      }
    };

    const handleSearch = (query: string) => {
        setFilters(prev => ({ ...prev, search: query }));
    };

    const handleFilterChange = (filterType: string, value: string) => {
        setFilters(prev => ({ ...prev, [filterType]: value }));
    };

    const handleVisibilityChange = (filterType: string, checked: boolean) => {
        setFilters(prev => ({ ...prev, [filterType]: checked }));
    };

    const filteredItems = useMemo(() => {
        return items.filter(item => {
            if (filters.search && !item.name.toLowerCase().includes(filters.search.toLowerCase())) {
                return false;
            }
            if (filters.category && item.category !== filters.category) {
                return false;
            }
            if (filters.rarity && item.rarity !== filters.rarity) {
                return false;
            }
            if (filters.status) {
                if (filters.status === 'active' && !item.active) return false;
                if (filters.status === 'inactive' && item.active) return false;
                if (filters.status === 'expired' && !item.expired) return false;
            }
            if (filters.isFeatured && !item.isFeatured) {
                return false;
            }
            if (filters.isDaily && !item.isDaily) {
                return false;
            }
            return true;
        });
    }, [items, filters]);

    const handleCloseModal = () => {
        setIsModalOpen(false);
        resetForm();
    };

  // Case contents management functions
  const fetchCaseContents = async (caseId: string) => {
    try {
      setLoadingCaseContents(true);
      const response = await httpClient.get(`/shop/cases/${caseId}/contents`);
      const contents = response.data;
      
      setCaseContents({
        items: contents.items || [],
        totalDropRate: contents.totalDropRate || 0
      });
    } catch (error) {
      console.error('Error fetching case contents:', error);
      showToast('Failed to load case contents', 'error');
    } finally {
      setLoadingCaseContents(false);
    }
  };

  const addCaseItem = () => {
    if (availableItems.length === 0) {
      showToast('No items available to add to case', 'error');
      return;
    }
    
    // Find first available item not already in case
    const usedItemIds = caseContents.items.map(item => item.containedItem.id);
    const availableItem = availableItems.find(item => !usedItemIds.includes(item.id));
    
    if (!availableItem) {
      showToast('All available items are already in this case', 'error');
      return;
    }

    const newCaseItem: CaseItemData = {
      containedItem: availableItem,
      dropRate: 1
    };
    
    const newItems = [...caseContents.items, newCaseItem];
    const newTotalDropRate = newItems.reduce((sum, item) => sum + item.dropRate, 0);
    
    setCaseContents({
      items: newItems,
      totalDropRate: newTotalDropRate
    });
  };

  const removeCaseItem = (index: number) => {
    const newItems = caseContents.items.filter((_, i) => i !== index);
    const newTotalDropRate = newItems.reduce((sum, item) => sum + item.dropRate, 0);
    
    setCaseContents({
      items: newItems,
      totalDropRate: newTotalDropRate
    });
  };

  const updateCaseItemDropRate = (index: number, dropRate: number) => {
    const newItems = [...caseContents.items];
    newItems[index] = { ...newItems[index], dropRate };
    const newTotalDropRate = newItems.reduce((sum, item) => sum + item.dropRate, 0);
    
    setCaseContents({
      items: newItems,
      totalDropRate: newTotalDropRate
    });
  };

  const updateCaseItemSelection = (index: number, selectedItemId: string) => {
    const selectedItem = availableItems.find(item => item.id === selectedItemId);
    if (!selectedItem) return;
    
    const newItems = [...caseContents.items];
    newItems[index] = { ...newItems[index], containedItem: selectedItem };
    
    setCaseContents({
      items: newItems,
      totalDropRate: caseContents.totalDropRate
    });
  };

  const saveCaseContents = async () => {
    if (!editingItem || editingItem.category !== 'CASE') return;
    
    if (caseContents.items.length === 0) {
      showToast('Case must contain at least one item', 'error');
      return;
    }

    // Validate drop rates before saving
    for (const item of caseContents.items) {
      if (item.dropRate <= 0) {
        showToast(`Drop rate for "${item.containedItem.name}" must be a positive number.`, 'error');
        return;
      }
    }
    
    try {
      const payload = caseContents.items.map(item => ({
        containedItem: { id: item.containedItem.id },
        dropRate: item.dropRate
      }));
      
      await httpClient.post(`/shop/admin/cases/${editingItem.id}/contents`, payload);
      showToast('Case contents updated successfully', 'success');
    } catch (error) {
      console.error('Error saving case contents:', error);
      showToast('Failed to save case contents', 'error');
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    
    // Handle numeric conversion for price, maxCopies, and maxDurability
    if (name === 'price') {
      setFormData({ ...formData, [name]: parseInt(value, 10) || 0 });
    } else if (name === 'maxCopies' || name === 'maxDurability' || name === 'durabilityIncrease' || name === 'bonusLootChance' || name === 'rarityChanceIncrease' || name === 'multiplierIncrease' || name === 'negationChance' || name === 'maxRepairs') {
      const numValue = parseInt(value, 10);
      setFormData({ ...formData, [name]: isNaN(numValue) ? undefined : numValue });
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
      const dataToSend = {
        ...formData,
        expiresAt: formData.expiresAt ? new Date(formData.expiresAt).toISOString() : null,
        maxCopies: formData.maxCopies ? Number(formData.maxCopies) : null,
        maxDurability: formData.maxDurability ? Number(formData.maxDurability) : null,
        durabilityIncrease: formData.durabilityIncrease ? Number(formData.durabilityIncrease) : null,
        bonusLootChance: formData.bonusLootChance ? Number(formData.bonusLootChance) : null,
        rarityChanceIncrease: formData.rarityChanceIncrease ? Number(formData.rarityChanceIncrease) : null,
        multiplierIncrease: formData.multiplierIncrease ? Number(formData.multiplierIncrease) : null,
        negationChance: formData.negationChance ? Number(formData.negationChance) : null,
        maxRepairs: formData.maxRepairs ? Number(formData.maxRepairs) : null,
        fishingRodPartType: formData.fishingRodPartType || null,
      };
      
      if (editingItem) {
        // Update existing item
        await httpClient.put(`/shop/admin/items/${editingItem.id}`, dataToSend);
        
        // Only show toast if another with same message doesn't exist
        if (!toasts.some(t => t.message === 'Item updated successfully')) {
          showToast('Item updated successfully', 'success');
        }
      } else {
        // Create new item
        await httpClient.post('/shop/admin/items', dataToSend);
        
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
      thumbnailUrl: item.thumbnailUrl || '',
      requiredRole: item.requiredRole,
      expiresAt: item.expiresAt,
      active: item.active,
      discordRoleId: item.discordRoleId || '',
      rarity: item.rarity || 'COMMON',
      isFeatured: item.isFeatured,
      isDaily: item.isDaily,
      fishingRodMultiplier: item.fishingRodMultiplier || 1.0,
      fishingRodPartType: item.fishingRodPartType || '',
      colorType: item.gradientEndColor ? 'gradient' : 'solid',
      gradientEndColor: item.gradientEndColor || '',
      maxCopies: item.maxCopies,
      maxDurability: item.maxDurability,
      durabilityIncrease: item.durabilityIncrease,
      bonusLootChance: item.bonusLootChance,
      rarityChanceIncrease: item.rarityChanceIncrease,
      multiplierIncrease: item.multiplierIncrease,
      negationChance: item.negationChance,
      maxRepairs: item.maxRepairs
    });
    
    // Load case contents if this is a case
    if (item.category === 'CASE') {
      fetchCaseContents(item.id);
    } else {
      // Reset case contents for non-case items
      setCaseContents({ items: [], totalDropRate: 0 });
    }
    setIsModalOpen(true);
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
      thumbnailUrl: '',
      requiredRole: null,
      expiresAt: null,
      active: true,
      discordRoleId: '',
      rarity: 'COMMON',
      isFeatured: false,
      isDaily: false,
      fishingRodMultiplier: 1.0,
      fishingRodPartType: '',
      colorType: 'solid',
      gradientEndColor: '',
      maxCopies: undefined,
      maxDurability: undefined,
      durabilityIncrease: undefined,
      bonusLootChance: undefined,
      rarityChanceIncrease: undefined,
      multiplierIncrease: undefined,
      negationChance: undefined,
      maxRepairs: undefined
    });
    setEditingItem(null);
    setCaseContents({ items: [], totalDropRate: 0 });
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
      
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center">
            <HiOutlineShoppingCart className="text-primary mr-3" size={26} />
            <h1 className="text-2xl font-bold text-white">Shop Management</h1>
        </div>
        <button
          onClick={() => {
            resetForm();
            setIsModalOpen(true);
          }}
          className="px-5 py-2 bg-primary hover:bg-primary/90 text-white rounded-md transition-all duration-300 flex items-center transform hover:-translate-y-0.5 hover:shadow-lg hover:shadow-primary/20"
        >
          <HiOutlinePlus className="mr-2" size={18} />
          Create New Item
        </button>
      </div>
      
      {/* Filters section */}
      <ShopFilters 
        onSearch={handleSearch}
        onFilterChange={handleFilterChange}
        onVisibilityChange={handleVisibilityChange}
        categories={categories}
        rarities={rarities}
      />

      {/* Items table */}
      <ShopItemsTable 
        items={filteredItems}
        handleEdit={handleEdit}
        handleDelete={handleDelete}
        fetchShopItems={fetchShopItems}
        onViewOwners={handleViewOwners}
      />

      {/* Create/Edit Modal */}
      <ItemFormModal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        formData={formData}
        setFormData={setFormData}
        handleInputChange={handleInputChange}
        handleCheckboxChange={handleCheckboxChange}
        handleSubmit={handleSubmit}
        editingItem={editingItem}
        submitting={submitting}
        categories={categories}
        roles={roles}
        rarities={rarities}
        fishingRodParts={fishingRodParts}
        handleImageUpload={handleImageUpload}
        handleImageRemove={handleImageRemove}
        caseContents={caseContents}
        availableItems={availableItems}
        loadingCaseContents={loadingCaseContents}
        addCaseItem={addCaseItem}
        removeCaseItem={removeCaseItem}
        updateCaseItemDropRate={updateCaseItemDropRate}
        updateCaseItemSelection={updateCaseItemSelection}
        saveCaseContents={saveCaseContents}
      />

      {/* Item Owners Modal */}
      <ItemOwnersModal
        isOpen={isOwnersModalOpen}
        onClose={() => setIsOwnersModalOpen(false)}
        owners={itemOwners}
        loading={loadingOwners}
        itemName={selectedItemName}
      />
      
    </div>
  );
}

export default ShopAdminPage; 