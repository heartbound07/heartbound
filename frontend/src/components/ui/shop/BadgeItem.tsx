import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ShopItem } from '../../../features/shop/InventoryPage';
import { getRarityColor, getRarityLabel } from '@/utils/rarityHelpers';
import { HiOutlineCheck } from 'react-icons/hi';

interface BadgeItemProps {
  badge: ShopItem;
  onEquip: (id: string) => void;
  onUnequip: (id: string) => void;
  isProcessing: boolean;
}

const BadgeItem: React.FC<BadgeItemProps> = ({
  badge,
  onEquip,
  onUnequip,
  isProcessing
}) => {
  const [showDetails, setShowDetails] = useState(false);
  const rarityColor = getRarityColor(badge.rarity);
  
  // Prefer thumbnailUrl for badges if available, fallback to imageUrl
  const badgeImageUrl = badge.thumbnailUrl || badge.imageUrl;
  
  return (
    <motion.div 
      className="badge-item"
      whileHover={{ y: -5, transition: { duration: 0.2 } }}
      onClick={() => setShowDetails(!showDetails)}
    >
      <div className="badge-item-wrapper">
        {/* Badge icon with rarity border */}
        <div 
          className="badge-icon-container"
          style={{ 
            borderColor: badge.equipped ? 'var(--color-primary, #0088cc)' : rarityColor 
          }}
        >
          {/* Equipped indicator */}
          {badge.equipped && (
            <div className="badge-equipped-indicator">
              <HiOutlineCheck size={16} />
            </div>
          )}
          
          <img 
            src={badgeImageUrl} 
            alt={badge.name}
            className="badge-icon"
          />
          
          {/* Rarity glow effect */}
          <motion.div
            className={`badge-rarity-glow ${badge.equipped ? 'badge-equipped-glow' : ''}`}
            animate={{ 
              opacity: [0.4, 0.8, 0.4],
              boxShadow: [
                `0 0 5px 1px ${rarityColor}80`,
                `0 0 10px 2px ${rarityColor}90`,
                `0 0 5px 1px ${rarityColor}80`
              ]
            }}
            transition={{
              duration: 3,
              repeat: Infinity,
              repeatType: "reverse",
              ease: "easeInOut"
            }}
          />
        </div>
        
        {/* Badge name */}
        <h3 className="badge-name">{badge.name}</h3>
        
        {/* Badge rarity label */}
        <div 
          className="badge-rarity-label"
          style={{
            backgroundColor: `${rarityColor}20`, 
            color: rarityColor,
            border: `1px solid ${rarityColor}`
          }}
        >
          {getRarityLabel(badge.rarity)}
        </div>
        
        {/* Moved equip/unequip button outside of details - always visible */}
        <div className="badge-action-container badge-action-persistent">
          {badge.equipped ? (
            <button
              onClick={(e) => {
                e.stopPropagation(); // Prevent the badge item click handler
                onUnequip(badge.id);
              }}
              disabled={isProcessing}
              className={`badge-action-button badge-unequip-button ${
                isProcessing ? 'badge-action-processing' : ''
              }`}
            >
              {isProcessing ? 'Processing...' : 'Unequip'}
            </button>
          ) : (
            <button
              onClick={(e) => {
                e.stopPropagation(); // Prevent the badge item click handler
                onEquip(badge.id);
              }}
              disabled={isProcessing}
              className={`badge-action-button badge-equip-button ${
                isProcessing ? 'badge-action-processing' : ''
              }`}
            >
              {isProcessing ? 'Processing...' : 'Equip'}
            </button>
          )}
        </div>
        
        {/* Badge details on click - now only shows description */}
        {showDetails && badge.description && (
          <motion.div 
            className="badge-details badge-details-description-only"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            onClick={(e) => {
              e.stopPropagation(); // Prevent triggering parent onClick
            }}
          >
            <p className="badge-description">{badge.description}</p>
          </motion.div>
        )}
      </div>
    </motion.div>
  );
};

export default BadgeItem;
