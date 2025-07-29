import { formatDisplayText } from '@/utils/formatters';

interface InventoryControlsProps {
  selectedCategory: string | null;
  sortOrder: 'default' | 'rarity-asc' | 'rarity-desc';
  onSortChange: (value: 'default' | 'rarity-asc' | 'rarity-desc') => void;
  itemCount: number;
  loading: boolean;
}

// Add category mapping for special cases
const categoryDisplayMapping: Record<string, string> = {
    'USER_COLOR': 'Nameplate',
    'LISTING': 'Listing Color',
    'ACCENT': 'Profile Accent',
    'BADGE': 'Badge',
    'CASE': 'Case',
    'FISHING_ROD': 'Fishing Rod',
    'FISHING_ROD_PART': 'Fishing Rod Part'
};

// Format category for display with custom mappings
const formatCategoryDisplay = (category: string): string => {
    return categoryDisplayMapping[category] || formatDisplayText(category);
};

export const InventoryControls: React.FC<InventoryControlsProps> = ({
  selectedCategory,
  sortOrder,
  onSortChange,
  itemCount,
  loading,
}) => {
  return (
    <div className="inventory-controls">
      <h2 className="text-xl font-bold text-white">
        {selectedCategory ? `${formatCategoryDisplay(selectedCategory)} Items` : 'All Items'}
      </h2>

      {!loading && itemCount > 0 && (
        <div className="sort-control-container">
          <span className="text-sm text-slate-300 mr-2">Sort by:</span>
          <select
            value={sortOrder}
            onChange={(e) => onSortChange(e.target.value as 'default' | 'rarity-asc' | 'rarity-desc')}
            className="inventory-sort-dropdown"
          >
            <option value="default">Default (Equipped first)</option>
            <option value="rarity-desc">Legendary to Common</option>
            <option value="rarity-asc">Common to Legendary</option>
          </select>
        </div>
      )}
    </div>
  );
}; 