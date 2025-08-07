import React, { useState, useMemo } from 'react';
import { 
  HiOutlineSearch, 
  HiOutlineX,
  HiOutlineCheck,
  HiOutlineCollection
} from 'react-icons/hi';
import { getRarityColor } from '@/utils/rarityHelpers';

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

interface CaseItemSelectorProps {
  isOpen: boolean;
  onClose: () => void;
  availableItems: ShopItem[];
  onSelectItem: (item: ShopItem) => void;
  excludeItemIds: string[];
  title?: string;
}

// Function to format category display
const formatCategoryDisplay = (item: ShopItem): string => {
  if (item.category === 'FISHING_ROD_PART' && item.fishingRodPartType) {
    return item.fishingRodPartType;
  }
  return item.category;
};

const CaseItemSelector: React.FC<CaseItemSelectorProps> = ({
  isOpen,
  onClose,
  availableItems,
  onSelectItem,
  excludeItemIds,
  title = "Select Item for Case"
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedRarity, setSelectedRarity] = useState('');

  // Get unique categories and rarities from available items
  const { categories, rarities } = useMemo(() => {
    const categorySet = new Set<string>();
    const raritySet = new Set<string>();
    
    availableItems.forEach(item => {
      if (item.category === 'FISHING_ROD_PART' && item.fishingRodPartType) {
        categorySet.add(item.fishingRodPartType);
      } else {
        categorySet.add(item.category);
      }
      raritySet.add(item.rarity);
    });
    
    return {
      categories: Array.from(categorySet).sort(),
      rarities: Array.from(raritySet).sort()
    };
  }, [availableItems]);

  // Filter available items based on search and filters
  const filteredItems = useMemo(() => {
    return availableItems.filter(item => {
      // Exclude items already in case
      if (excludeItemIds.includes(item.id)) return false;
      
      // Search filter
      if (searchQuery && !item.name.toLowerCase().includes(searchQuery.toLowerCase())) {
        return false;
      }
      
      // Category filter
      if (selectedCategory) {
        const itemCategory = formatCategoryDisplay(item);
        if (itemCategory !== selectedCategory) return false;
      }
      
      // Rarity filter
      if (selectedRarity && item.rarity !== selectedRarity) {
        return false;
      }
      
      return true;
    });
  }, [availableItems, excludeItemIds, searchQuery, selectedCategory, selectedRarity]);

  const handleItemSelect = (item: ShopItem) => {
    onSelectItem(item);
    // Reset filters and close
    setSearchQuery('');
    setSelectedCategory('');
    setSelectedRarity('');
    onClose();
  };

  const resetFilters = () => {
    setSearchQuery('');
    setSelectedCategory('');
    setSelectedRarity('');
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-70 z-50 flex justify-center items-center">
      <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 m-4 max-h-[80vh] overflow-hidden transition-all duration-300 w-full max-w-4xl">
        {/* Header */}
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-xl font-semibold text-white flex items-center">
            <HiOutlineCollection className="text-primary mr-2" size={20} />
            {title}
          </h2>
          <button onClick={onClose} className="text-slate-400 hover:text-white">
            <HiOutlineX size={24} />
          </button>
        </div>

        {/* Filters */}
        <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-4 mb-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Search */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <HiOutlineSearch className="text-slate-400" size={16} />
              </div>
              <input
                type="text"
                placeholder="Search items..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-md pl-9 pr-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            {/* Category Filter */}
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">All Categories</option>
              {categories.map(category => (
                <option key={category} value={category}>{category}</option>
              ))}
            </select>

            {/* Rarity Filter */}
            <select
              value={selectedRarity}
              onChange={(e) => setSelectedRarity(e.target.value)}
              className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">All Rarities</option>
              {rarities.map(rarity => (
                <option key={rarity} value={rarity}>
                  {rarity.charAt(0) + rarity.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </div>

          {/* Filter Actions */}
          <div className="flex justify-between items-center mt-4">
            <span className="text-sm text-slate-400">
              {filteredItems.length} item{filteredItems.length !== 1 ? 's' : ''} available
            </span>
            <button
              onClick={resetFilters}
              className="text-sm text-slate-400 hover:text-white transition-colors"
            >
              Reset Filters
            </button>
          </div>
        </div>

        {/* Items List */}
        <div className="overflow-y-auto max-h-96 border border-slate-700 rounded-lg">
          {filteredItems.length === 0 ? (
            <div className="p-8 text-center text-slate-400">
              <HiOutlineCollection className="mx-auto mb-2" size={32} />
              <p>No items match your criteria</p>
              <p className="text-sm mt-1">Try adjusting your filters</p>
            </div>
          ) : (
            <div className="space-y-1 p-2">
              {filteredItems.map(item => (
                <div
                  key={item.id}
                  onClick={() => handleItemSelect(item)}
                  className="flex items-center p-3 rounded-md bg-slate-800/50 hover:bg-slate-700/50 cursor-pointer transition-colors border border-transparent hover:border-slate-600"
                >
                  {/* Item Image */}
                  <div className="flex-shrink-0 mr-3">
                    {item.imageUrl ? (
                      <div 
                        className="h-10 w-10 rounded-full overflow-hidden"
                        style={{ border: `2px solid ${getRarityColor(item.rarity)}` }}
                      >
                        <img 
                          src={item.imageUrl} 
                          alt={item.name} 
                          className="h-full w-full object-cover"
                        />
                      </div>
                    ) : (
                      <div 
                        className="h-10 w-10 rounded-full bg-slate-700 flex items-center justify-center"
                        style={{ border: `2px solid ${getRarityColor(item.rarity)}` }}
                      >
                        <HiOutlineCollection className="text-slate-400" size={16} />
                      </div>
                    )}
                  </div>

                  {/* Item Info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <h3 className="text-sm font-medium text-white truncate">
                        {item.name}
                      </h3>
                      <div className="flex items-center space-x-2 ml-3">
                        <span 
                          className="px-2 py-1 text-xs font-semibold rounded-full"
                          style={{
                            backgroundColor: getRarityColor(item.rarity) + '20',
                            color: getRarityColor(item.rarity),
                            border: `1px solid ${getRarityColor(item.rarity)}`
                          }}
                        >
                          {item.rarity.charAt(0) + item.rarity.slice(1).toLowerCase()}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center justify-between mt-1">
                      <span className="text-xs text-slate-400">
                        {formatCategoryDisplay(item)}
                      </span>
                      <span className="text-xs text-yellow-400 font-medium">
                        {item.price} credits
                      </span>
                    </div>
                    {item.description && (
                      <p className="text-xs text-slate-400 mt-1 truncate">
                        {item.description}
                      </p>
                    )}
                  </div>

                  {/* Select Button */}
                  <div className="flex-shrink-0 ml-3">
                    <button className="p-2 text-primary hover:text-primary/80 transition-colors">
                      <HiOutlineCheck size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-end mt-4">
          <button
            onClick={onClose}
            className="px-4 py-2 border border-slate-600 rounded-md bg-slate-800 text-white hover:bg-slate-700 transition-colors"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
};

export default CaseItemSelector; 