import { motion, AnimatePresence } from 'framer-motion';
import ShopItemCard from './ShopItemCard';
import ShopItemSkeleton from './ShopItemSkeleton';
import { ShopItem, UserProfile } from '@/types/shop';

interface ShopSectionProps {
  title: string;
  items: ShopItem[];
  loading: boolean;
  purchaseInProgress: boolean;
  user: UserProfile | null;
  recentPurchases: Record<string, number>;
  handlePurchase: (itemId: string, quantity?: number) => void;
  onViewCaseContents: (caseId: string, caseName: string) => void;
  emptyMessage: string;
  gridClass: string;
  skeletonCount: number;
}

const ShopSection: React.FC<ShopSectionProps> = ({
  title,
  items,
  loading,
  purchaseInProgress,
  user,
  recentPurchases,
  handlePurchase,
  onViewCaseContents,
  emptyMessage,
  gridClass,
  skeletonCount
}) => {
  if (loading) {
    return (
      <div className={gridClass}>
        {Array.from({ length: skeletonCount }).map((_, index) => (
          <ShopItemSkeleton key={index} />
        ))}
      </div>
    );
  }

  return (
    <div>
      <h2 className="text-2xl font-bold text-white mb-6">{title}</h2>
      {items.length === 0 ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="text-center py-12 rounded-lg border border-slate-700/50 bg-slate-800/20"
        >
          <div className="text-slate-400 mb-2">
            {emptyMessage}
          </div>
        </motion.div>
      ) : (
        <div className={gridClass}>
          <AnimatePresence mode="popLayout">
            {items.map((item) => (
              <ShopItemCard
                key={item.id}
                item={item}
                handlePurchase={handlePurchase}
                purchaseInProgress={purchaseInProgress}
                user={user}
                isRecentlyPurchased={!!recentPurchases[item.id] && (Date.now() - recentPurchases[item.id] < 5000)}
                onViewCaseContents={onViewCaseContents}
              />
            ))}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
};

export default ShopSection; 