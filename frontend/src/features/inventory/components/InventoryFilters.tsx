import { motion } from 'framer-motion';
import { formatCategoryDisplay } from '@/utils/formatters';

interface InventoryFiltersProps {
  categories: string[];
  selectedCategory: string | null;
  onSelectCategory: (category: string | null) => void;
}

export const InventoryFilters: React.FC<InventoryFiltersProps> = ({
  categories,
  selectedCategory,
  onSelectCategory,
}) => {
  return (
    <motion.div
      className="inventory-categories"
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay: 0.2 }}
    >
      <div className="inventory-categories-scroll-container">
        <motion.button
          key="all"
          onClick={() => onSelectCategory(null)}
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
            onClick={() => onSelectCategory(category)}
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
  );
}; 