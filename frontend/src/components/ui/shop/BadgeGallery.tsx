import React from 'react';
import BadgeItem from './BadgeItem';    
import { motion } from 'framer-motion';
import { ShopItem } from '../../../features/shop/InventoryPage';

interface BadgeGalleryProps {
  badges: ShopItem[];
  onEquip: (id: string) => void;
  onUnequip: (id: string) => void;
  actionInProgress: string | null;
}

const BadgeGallery: React.FC<BadgeGalleryProps> = ({ 
  badges, 
  onEquip, 
  onUnequip, 
  actionInProgress 
}) => {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="badge-gallery flex justify-center items-center min-h-[50vh]"
    > 
      <div className="badge-gallery-grid">
        {badges.map(badge => (
          <BadgeItem
            key={badge.id}
            badge={badge}
            onEquip={onEquip}
            onUnequip={onUnequip}
            isProcessing={actionInProgress === badge.id}
          />
        ))}
      </div>
    </motion.div>
  );
};

export default BadgeGallery;
