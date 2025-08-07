import React from 'react';
import { HiOutlineSearch } from 'react-icons/hi';

interface ShopItem {
  id: string;
  category: string;
  fishingRodPartType?: string;
}

interface ShopFiltersProps {
    onSearch: (query: string) => void;
    onFilterChange: (filterType: string, value: string) => void;
    onVisibilityChange: (filterType: string, checked: boolean) => void;
    categories: string[];
    rarities: string[];
    items: ShopItem[];
  }

const ShopFilters: React.FC<ShopFiltersProps> = ({ 
    onSearch, 
    onFilterChange, 
    onVisibilityChange,
    categories,
    rarities,
    items
}) => {
  // Create expanded categories list that includes fishing rod part types
  const getExpandedCategories = () => {
    const expandedCategories: string[] = [];
    
    categories.forEach(category => {
      if (category === 'FISHING_ROD_PART') {
        // Add specific fishing rod part types found in items
        const fishingRodParts = [...new Set(
          items
            .filter(item => item.category === 'FISHING_ROD_PART' && item.fishingRodPartType)
            .map(item => item.fishingRodPartType!)
        )].sort();
        
        expandedCategories.push(...fishingRodParts);
      } else {
        expandedCategories.push(category);
      }
    });
    
    return expandedCategories;
  };

  const expandedCategories = getExpandedCategories();

  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-4 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Search Input */}
            <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <HiOutlineSearch className="text-slate-400" size={16} />
                </div>
                <input
                    type="text"
                    placeholder="Search by name..."
                    onChange={(e) => onSearch(e.target.value)}
                    className="w-full bg-slate-800 border border-slate-700 rounded-md pl-9 pr-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
                />
            </div>

            {/* Category Filter */}
            <select
                onChange={(e) => onFilterChange('category', e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
            >
                <option value="">All Categories</option>
                {expandedCategories.map(cat => <option key={cat} value={cat}>{cat}</option>)}
            </select>

            {/* Rarity Filter */}
            <select
                onChange={(e) => onFilterChange('rarity', e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
            >
                <option value="">All Rarities</option>
                {rarities.map(rarity => <option key={rarity} value={rarity}>{rarity}</option>)}
            </select>

            {/* Status Filter */}
            <select
                onChange={(e) => onFilterChange('status', e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
            >
                <option value="">All Statuses</option>
                <option value="active">Active</option>
                <option value="inactive">Inactive</option>
                <option value="expired">Expired</option>
            </select>

            {/* Visibility Filters */}
            <div className="col-span-1 md:col-span-2 lg:col-span-4 flex items-center space-x-4 pt-2">
                <label className="flex items-center space-x-2 text-sm font-medium text-slate-300">
                    <input
                        type="checkbox"
                        onChange={(e) => onVisibilityChange('isFeatured', e.target.checked)}
                        className="w-4 h-4 rounded text-primary focus:ring-primary bg-slate-800 border-slate-600"
                    />
                    <span>Featured</span>
                </label>
                <label className="flex items-center space-x-2 text-sm font-medium text-slate-300">
                    <input
                        type="checkbox"
                        onChange={(e) => onVisibilityChange('isDaily', e.target.checked)}
                        className="w-4 h-4 rounded text-primary focus:ring-primary bg-slate-800 border-slate-600"
                    />
                    <span>Daily</span>
                </label>
            </div>
        </div>
    </div>
  );
};

export default ShopFilters; 