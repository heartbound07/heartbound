import { forwardRef } from 'react';
import { motion } from 'framer-motion';
import { FaInfoCircle } from 'react-icons/fa';
import { GiFishingPole } from 'react-icons/gi';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { SafeText } from '@/components/SafeHtmlRenderer';
import { useSanitizedContent } from '@/hooks/useSanitizedContent';
import React from 'react';
import { ShopItem } from '../../../types/inventory';

// Inventory Item Card Component (based on ShopItemCard design)
export const InventoryItemCard = forwardRef(({ 
  item, 
  handleEquip, 
  handleUnequip,
  handleUnequipBadge,
  handleOpenCase,
  actionInProgress, 
  user,
  isSelected = false,
  onSelect,
  onViewCaseContents
}: { 
  item: ShopItem; 
  handleEquip: (id: string) => void;
  handleUnequip: (category: string) => void;
  handleUnequipBadge: (badgeId: string) => void;
  handleOpenCase: (caseId: string, caseName: string) => void;
  actionInProgress: string | null;
  user: any;
  isSelected?: boolean;
  onSelect: (item: ShopItem) => void;
  onViewCaseContents?: (caseId: string, caseName: string) => void;
}, ref) => {
  // Get rarity color for border
  const rarityColor = getRarityColor(item.rarity);
  
  // Sanitize content for safe display
  const nameContent = useSanitizedContent(item.name, { maxLength: 100, stripHtml: true });
  const descriptionContent = useSanitizedContent(item.description, { maxLength: 500, stripHtml: true });
  
  const handleAction = () => {
    if (item.category === 'CASE') {
      handleOpenCase(item.id, nameContent.sanitized);
    } else if (item.category === 'BADGE') {
      if (item.equipped) {
        handleUnequipBadge(item.id);
      } else {
        handleEquip(item.id);
      }
    } else {
      if (item.equipped) {
        handleUnequip(item.category);
      } else {
        handleEquip(item.id);
      }
    }
  };

  const getActionButtonText = () => {
    if (item.category === 'CASE') {
      return (!item.quantity || item.quantity < 1) ? 'No Cases' : 'Open Case';
    }
    return item.equipped ? 'Unequip' : 'Equip';
  };

  const isActionDisabled = () => {
    if (item.category === 'CASE') {
      return !item.quantity || item.quantity < 1 || actionInProgress !== null;
    }
    return actionInProgress !== null;
  };
  
  return (
    <motion.div
      ref={ref as React.RefObject<HTMLDivElement>}
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -5 }}
      className={`shop-item-card inventory-item-card ${isSelected ? 'inventory-item-selected' : ''} ${item.equipped ? 'inventory-item-equipped' : ''}`}
      style={{ borderColor: isSelected ? 'var(--color-primary, #0088cc)' : (item.equipped ? 'var(--color-primary, #0088cc)' : 'transparent') }}
      onClick={() => onSelect(item)}
    >
      {/* Show quantity for non-case items */}
      {(item.quantity && item.quantity > 1 && item.category !== 'CASE') && (
        <div className="absolute top-2 left-2 z-10 bg-indigo-600 text-white text-xs font-semibold px-2 py-1 rounded-full shadow-lg">
          x{item.quantity}
        </div>
      )}
      {/* Item image or Discord preview for USER_COLOR or BADGE preview */}
      {item.category === 'USER_COLOR' ? (
        <div className="shop-item-image inventory-item-image">
          <NameplatePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/images/default-avatar.png"}
            color={item.imageUrl}
            endColor={item.gradientEndColor}
            fallbackColor={rarityColor}
            message="Your nameplate color"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          
          {/* Equipped badge */}
          {item.equipped && (
            <div className="item-badge badge-equipped">
              Equipped
      </div>
          )}
        </div>
      ) : item.category === 'BADGE' ? (
        <div className="shop-item-image inventory-item-image">
          <BadgePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/images/default-avatar.png"}
            badgeUrl={item.thumbnailUrl || item.imageUrl}
            message="Your badge"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          
          {/* Equipped badge */}
          {item.equipped && (
            <div className="item-badge badge-equipped">
              Equipped
            </div>
          )}
        </div>
      ) : item.category === 'CASE' ? (
        <div className="shop-item-image inventory-item-image case-preview-container">
          {/* Show styled quantity ONLY for case items */}
          {(item.quantity && item.quantity > 1) && (
            <div className="absolute top-2 left-2 z-10 text-white text-lg font-bold px-2 py-1" style={{ textShadow: '1px 1px 3px rgba(0,0,0,0.7)' }}>
              x{item.quantity}
            </div>
          )}
          {/* Case visual representation */}
          <div className="h-full w-full bg-gradient-to-br from-slate-700 to-slate-800 flex flex-col items-center justify-center relative overflow-hidden">
            {/* Case icon/visual */}
            <div className="relative z-10">
              {item.imageUrl ? (
                <img 
                  src={item.imageUrl} 
                  alt={nameContent.sanitized}
                  className="h-16 w-16 object-cover rounded-lg border-2"
                  style={{ borderColor: rarityColor }}
                />
              ) : (
                <div 
                  className="h-16 w-16 rounded-lg border-2 flex items-center justify-center"
                  style={{ 
                    borderColor: rarityColor,
                    backgroundColor: `${rarityColor}20`
                  }}
                >
                  <svg 
                    className="w-8 h-8"
                    style={{ color: rarityColor }}
                    fill="none" 
                    viewBox="0 0 24 24" 
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                  </svg>
                </div>
              )}
            </div>
            
            {/* Case contents count */}
            {item.category === 'CASE' && item.caseContentsCount && item.caseContentsCount > 0 && (
              <div className="mt-2 text-xs text-slate-300 font-medium">
                Contains {item.caseContentsCount} items
              </div>
            )}

            {/* Mystical background effect */}
            <div 
              className="absolute inset-0 opacity-20"
              style={{
                background: `radial-gradient(circle at center, ${rarityColor}40 0%, transparent 70%)`
              }}
          />
        </div>
        
          {/* Info icon in top right corner for cases */}
          {item.category === 'CASE' && item.caseContentsCount && item.caseContentsCount > 0 && onViewCaseContents && (
            <div className="absolute top-2 right-2">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onViewCaseContents(item.id, nameContent.sanitized);
                }}
                className="case-info-icon"
                title="View case contents and drop rates"
                aria-label="View case contents and drop rates"
              >
                <FaInfoCircle size={16} />
              </button>
            </div>
          )}
        </div>
      ) : item.category === 'FISHING_ROD' ? (
        <div className="shop-item-image inventory-item-image fishing-rod-preview-container">
          <div
            className="h-full w-full flex flex-col items-center justify-center relative overflow-hidden p-4"
            style={{ background: `linear-gradient(to bottom right, #1f2937, ${rarityColor})` }}
          >
            <GiFishingPole
              className="absolute w-24 h-24 text-white/10 transform -rotate-12 -right-4 -bottom-4"
            />
            <GiFishingPole
              className="relative z-10 w-16 h-16 text-white/80 drop-shadow-lg"
            />
             <div className="relative z-10 mt-2 text-center">
              <p className="text-xl font-bold text-white drop-shadow-md">
                {item.fishingRodMultiplier}x
              </p>
              <p className="text-xs font-semibold text-cyan-200 drop-shadow-sm">
                Credit Bonus
              </p>
            </div>
          </div>
          {item.equipped && (
            <div className="item-badge badge-equipped">
              Equipped
            </div>
          )}
        </div>
      ) : (
        <div className="shop-item-image inventory-item-image">
          {item.imageUrl ? (
            <img 
              src={item.imageUrl} 
              alt={nameContent.sanitized}
              className="h-full w-full object-cover" 
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center bg-slate-800/50">
              <span className="text-slate-400">No Image</span>
            </div>
          )}
          
          {/* Equipped badge */}
          {item.equipped && (
            <div className="item-badge badge-equipped">
              Equipped
            </div>
          )}
        </div>
      )}
      
      <div className="shop-item-content">
        <div className="flex justify-between items-center mb-2">
          <div className="flex items-center">
            <SafeText 
              text={nameContent.sanitized}
              tag="h3"
              className="font-medium text-white text-lg mr-2"
              maxLength={100}
              showTooltip={true}
            />
            <div 
              className="px-2 py-0.5 rounded text-xs font-semibold"
              style={getRarityBadgeStyle(item.rarity)}
            >
              {getRarityLabel(item.rarity)}
            </div>
          </div>
          {item.category === 'FISHING_ROD' && (
            <div className="text-lg font-bold text-cyan-400">
              {item.fishingRodMultiplier}x
            </div>
          )}
        </div>
        
        {descriptionContent.sanitized && (
          <SafeText 
            text={descriptionContent.sanitized}
            tag="p"
            className="text-slate-300 text-sm mb-3 line-clamp-2"
            maxLength={200}
            showTooltip={true}
          />
        )}
        
        {/* Action button */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            handleAction();
          }}
          disabled={isActionDisabled()}
          className={`purchase-button ${
            item.category === 'CASE' ? 'purchase-button-active' :
            item.equipped ? 'purchase-button-owned' : 'purchase-button-active'
          } ${isActionDisabled() ? 'purchase-button-processing' : ''}`}
        >
          {actionInProgress !== null && (actionInProgress === item.id || actionInProgress === item.category) ? (
            <>
              <svg className="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              Processing...
            </>
          ) : (
            <>
              {item.category === 'CASE' ? (
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
              ) : item.equipped ? (
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              ) : (
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              )}
              {getActionButtonText()}
            </>
          )}
        </button>
        </div>
    </motion.div>
  );
});

// Add a display name for better debugging
InventoryItemCard.displayName = 'InventoryItemCard';

// Skeleton loader for inventory items
export const InventoryItemSkeleton = () => {
  return (
    <div className="shop-item-card">
      <div className="shop-item-image skeleton"></div>
      <div className="p-4 space-y-2">
        <div className="h-6 w-2/3 skeleton rounded"></div>
        <div className="h-4 w-full skeleton rounded"></div>
        <div className="h-8 w-full skeleton rounded mt-4"></div>
      </div>
    </div>
  );
}; 