import { formatCategoryDisplay } from '@/utils/formatters';

interface InventoryControlsProps {
  selectedCategory: string | null;
  sortOrder: 'default' | 'rarity-asc' | 'rarity-desc';
  onSortChange: (value: 'default' | 'rarity-asc' | 'rarity-desc') => void;
  itemCount: number;
  loading: boolean;
}

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