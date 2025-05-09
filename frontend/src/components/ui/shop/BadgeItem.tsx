import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createPortal } from 'react-dom';
import { ShopItem } from '../../../features/shop/InventoryPage';
import { getRarityColor, getRarityLabel } from '@/utils/rarityHelpers';
import { HiOutlineCheck } from 'react-icons/hi';

interface BadgeItemProps {
  badge: ShopItem;
  onEquip: (id: string) => void;
  onUnequip: (id: string) => void;
  isProcessing: boolean;
  showDetails: boolean;
  onToggleDetails: () => void;
}

const BadgeItem: React.FC<BadgeItemProps> = ({
  badge,
  onEquip,
  onUnequip,
  isProcessing,
  showDetails,
  onToggleDetails
}) => {
  const iconContainerRef = useRef<HTMLDivElement>(null);
  const [popupPosition, setPopupPosition] = useState({ top: 0, left: 0 });
  const rarityColor = getRarityColor(badge.rarity);
  const timerRef = useRef<number | null>(null);
  
  // Prefer thumbnailUrl for badges if available, fallback to imageUrl
  const badgeImageUrl = badge.thumbnailUrl || badge.imageUrl;
  
  // Update popup position when details are shown
  useEffect(() => {
    if (showDetails && iconContainerRef.current) {
      const rect = iconContainerRef.current.getBoundingClientRect();
      
      // Get viewport dimensions for mobile-friendly positioning
      const viewportWidth = window.innerWidth;
      
      // Adjust left position to ensure popup stays within viewport
      let leftPos = rect.left + rect.width / 2 + window.scrollX;
      
      // For very small screens, ensure the popup doesn't go off-screen
      const isMobile = viewportWidth <= 640;
      const mobileOffset = isMobile ? -10 : 0; // Slight adjustment for mobile
      
      setPopupPosition({
        // On mobile, position slightly higher to ensure visibility
        top: rect.top + window.scrollY - (isMobile ? 5 : 10),
        left: leftPos + mobileOffset
      });
      
      // Set a timer to hide the details after 3 seconds
      timerRef.current = window.setTimeout(() => {
        onToggleDetails(); // This will hide details by calling the parent function
      }, 3000);
    }
    
    // Clean up the timer when showDetails changes or component unmounts
    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [showDetails, onToggleDetails]);
  
  // Handle mouse enter to cancel auto-hide
  const handleMouseEnter = () => {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };
  
  // Handle mouse leave to restart auto-hide
  const handleMouseLeave = () => {
    if (showDetails) {
      timerRef.current = window.setTimeout(() => {
        onToggleDetails();
      }, 1000);
    }
  };
  
  // Create animation variants for more sophisticated effects
  const popupVariants = {
    hidden: { 
      opacity: 0, 
      y: -20,
      scale: 0.95
    },
    visible: { 
      opacity: 1, 
      y: 0,
      scale: 1,
      transition: {
        type: "spring",
        stiffness: window.innerWidth <= 640 ? 400 : 500,
        damping: window.innerWidth <= 640 ? 30 : 25,
        duration: window.innerWidth <= 640 ? 0.25 : 0.3
      }
    },
    exit: { 
      opacity: 0, 
      y: -10,
      scale: 0.9,
      transition: {
        duration: window.innerWidth <= 640 ? 0.15 : 0.2,
        ease: "easeOut"
      }
    }
  };
  
  // Add this function to handle touch interactions better
  const handleTouchInteraction = (e: React.TouchEvent) => {
    e.stopPropagation();
    
    // Cancel any existing auto-hide timer on touch
    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    
    // Set a slightly longer timer for touch devices
    if (showDetails) {
      timerRef.current = window.setTimeout(() => {
        onToggleDetails();
      }, 4000); // Longer timeout for mobile users to read the content
    }
  };
  
  return (
    <motion.div 
      className="badge-item"
      whileHover={{ y: -5, transition: { duration: 0.2 } }}
      onClick={onToggleDetails}
    >
      <div className="badge-item-wrapper">
        {/* Badge icon with rarity border */}
        <div 
          ref={iconContainerRef}
          className="badge-icon-container"
          style={{ 
            borderColor: badge.equipped ? 'var(--color-primary, #0088cc)' : rarityColor 
          }}
        >
          {/* Equipped indicator - now will be above the icon */}
          {badge.equipped && (
            <div className="badge-equipped-indicator">
              <HiOutlineCheck size={16} />
            </div>
          )}
          
          {/* Added inner container for image */}
          <div className="badge-icon-inner">
            <img 
              src={badgeImageUrl} 
              alt={badge.name}
              className="badge-icon"
            />
          </div>
          
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
      </div>
      
      {/* Portal with AnimatePresence for exit animations */}
      {document.body && createPortal(
        <AnimatePresence>
          {showDetails && (
            <motion.div 
              className={`badge-details-portal ${window.innerWidth <= 640 ? 'badge-details-mobile' : ''}`}
              style={{
                position: 'absolute',
                top: `${popupPosition.top}px`,
                left: `${popupPosition.left}px`,
                transform: 'translate(-50%, -100%)',
                zIndex: 9999
              }}
              variants={popupVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              onClick={(e) => {
                e.stopPropagation();
              }}
              onMouseEnter={handleMouseEnter}
              onMouseLeave={handleMouseLeave}
              onTouchStart={handleTouchInteraction}
              onTouchEnd={handleTouchInteraction}
            >
              <motion.div 
                className="badge-details-content"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.1 }}
              >
                <p className="badge-description">
                  {badge.description ? 
                    badge.description : 
                    <span className="text-slate-400 italic">This badge has no description</span>
                  }
                </p>
              </motion.div>
              <motion.div 
                className="badge-details-arrow"
                initial={{ opacity: 0, y: -5 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.15 }}
              />
            </motion.div>
          )}
        </AnimatePresence>,
        document.body
      )}
    </motion.div>
  );
};

export default BadgeItem;
