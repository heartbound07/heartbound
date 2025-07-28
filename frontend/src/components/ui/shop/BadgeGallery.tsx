import React, { useState } from 'react';
import BadgeItem from './BadgeItem';    
import { motion } from 'framer-motion';
import { ShopItem } from '@/types/inventory';

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
  // Add state to track which badge is showing details (if any)
  const [activeDetailsId, setActiveDetailsId] = useState<string | null>(null);
  
  // Function to toggle details for a badge
  const toggleDetails = (badgeId: string) => {
    setActiveDetailsId(currentId => currentId === badgeId ? null : badgeId);
  };
  
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
            showDetails={activeDetailsId === badge.id}
            onToggleDetails={() => toggleDetails(badge.id)}
          />
        ))}
      </div>
    </motion.div>
  );
};

export default BadgeGallery;
