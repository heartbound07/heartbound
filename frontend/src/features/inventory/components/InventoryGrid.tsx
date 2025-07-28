import { motion, AnimatePresence } from 'framer-motion';
import { InventoryItemCard, InventoryItemSkeleton } from './InventoryItemCard';
import { ShopItem } from '@/types/inventory';
import { formatDisplayText } from '@/utils/formatters';

interface InventoryGridProps {
  loading: boolean;
  items: ShopItem[];
  selectedCategory: string | null;
  handleEquip: (id: string) => void;
  handleUnequip: (category: string) => void;
  handleUnequipBadge: (badgeId: string) => void;
  handleOpenCase: (caseId: string, caseName: string) => void;
  actionInProgress: string | null;
  user: any;
  isItemSelected: (item: ShopItem) => boolean;
  onSelectItem: (item: ShopItem) => void;
  onViewCaseContents: (caseId: string, caseName: string) => void;
}

// Add category mapping for special cases
const categoryDisplayMapping: Record<string, string> = {
    'USER_COLOR': 'Nameplate',
    'LISTING': 'Listing Color',
    'ACCENT': 'Profile Accent',
    'BADGE': 'Badge',
    'CASE': 'Case',
    'FISHING_ROD': 'Fishing Rod'
};

// Format category for display with custom mappings
const formatCategoryDisplay = (category: string): string => {
    return categoryDisplayMapping[category] || formatDisplayText(category);
};

export const InventoryGrid: React.FC<InventoryGridProps> = ({
  loading,
  items,
  selectedCategory,
  ...props
}) => {
  return (
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
            {selectedCategory
              ? `No ${formatCategoryDisplay(selectedCategory)} items`
              : "Your inventory is empty"}
          </h3>
          <p className="empty-inventory-message">
            {selectedCategory
              ? `You don't have any items in the ${formatCategoryDisplay(selectedCategory)} category yet.`
              : "Start shopping to collect amazing items!"}
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
                handleEquip={props.handleEquip}
                handleUnequip={props.handleUnequip}
                handleUnequipBadge={props.handleUnequipBadge}
                handleOpenCase={props.handleOpenCase}
                actionInProgress={props.actionInProgress}
                user={props.user}
                isSelected={props.isItemSelected(item)}
                onSelect={props.onSelectItem}
                onViewCaseContents={props.onViewCaseContents}
              />
            ))}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}; 