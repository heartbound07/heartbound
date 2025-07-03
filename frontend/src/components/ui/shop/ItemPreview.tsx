import React from 'react';
import { motion } from 'framer-motion';
import { FaCoins } from 'react-icons/fa';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { SafeText, sanitizeText } from '@/components/SafeHtmlRenderer';
import { ShopItem } from '@/features/shop/InventoryPage';

interface ItemPreviewProps {
  selectedItems: {
    nameplate?: ShopItem | null;
    badge?: ShopItem | null;
    [key: string]: ShopItem | null | undefined;
  };
  user: any;
  onEquip?: (itemId: string) => void;
  onUnequip?: (category: string) => void;
  onUnequipBadge?: (badgeId: string) => void;
  onOpenCase?: (caseId: string, caseName: string) => void;
  onViewCaseContents?: (caseId: string, caseName: string) => void;
  actionInProgress?: string | null;
}

export const ItemPreview: React.FC<ItemPreviewProps> = ({
  selectedItems,
  user,
  onEquip,
  onUnequip,
  onUnequipBadge,
  onOpenCase,
  onViewCaseContents,
  actionInProgress
}) => {
  // Get the primary selected item (for action buttons)
  const primaryItem = Object.values(selectedItems).find(item => item !== null) || null;
  
  // Get selected nameplate or fall back to equipped/default
  const getNameplateColor = () => {
    if (selectedItems.nameplate) {
      return selectedItems.nameplate.imageUrl;
    }
    // Fall back to user's equipped nameplate color or default
    return user?.nameplateColor || '#0088cc';
  };

  // Get selected badge or fall back to equipped badge
  const getBadgeUrl = () => {
    if (selectedItems.badge) {
      return selectedItems.badge.thumbnailUrl || selectedItems.badge.imageUrl;
    }
    // Could fall back to user's equipped badge if we have that data
    return null;
  };

  // Get the rarity color for the primary selected item
  const rarityColor = primaryItem ? getRarityColor(primaryItem.rarity) : '#0088cc';

  const handleAction = (item: ShopItem) => {
    if (!onEquip || !onUnequip || !onUnequipBadge || !onOpenCase) return;

    // Use direct sanitization instead of hook
    const sanitizedName = sanitizeText(item.name || '');
    const truncatedName = sanitizedName.length > 100 ? sanitizedName.substring(0, 100) : sanitizedName;

    if (item.category === 'CASE') {
      onOpenCase(item.id, truncatedName);
    } else if (item.category === 'BADGE') {
      if (item.equipped) {
        onUnequipBadge(item.id);
      } else {
        onEquip(item.id);
      }
    } else {
      if (item.equipped) {
        onUnequip(item.category);
      } else {
        onEquip(item.id);
      }
    }
  };

  const getActionButtonText = (item: ShopItem) => {
    if (item.category === 'CASE') {
      return (!item.quantity || item.quantity < 1) ? 'No Cases' : 'Open Case';
    }
    return item.equipped ? 'Unequip' : 'Equip';
  };

  const isActionDisabled = (item: ShopItem) => {
    if (item.category === 'CASE') {
      return !item.quantity || item.quantity < 1 || actionInProgress !== null;
    }
    return actionInProgress !== null;
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="item-preview-container"
    >
      {/* Header */}
      <div className="item-preview-header">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <h2 className="item-preview-title">Profile Preview</h2>
            {primaryItem && (
              <div 
                className="px-3 py-1 rounded-lg text-sm font-semibold"
                style={getRarityBadgeStyle(primaryItem.rarity)}
              >
                {getRarityLabel(primaryItem.rarity)}
              </div>
            )}
          </div>
          
          {primaryItem?.equipped && (
            <div className="item-preview-equipped-badge">
              <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              Equipped
            </div>
          )}
        </div>
      </div>

      {/* Main Preview Area */}
      <div className="item-preview-main">
        <div 
          className="item-preview-visual"
          style={{ borderColor: primaryItem?.equipped ? 'var(--color-primary, #0088cc)' : rarityColor }}
        >
          {/* Rarity glow effect */}
          {primaryItem && (
            <motion.div
              className="item-preview-glow"
              initial={{ opacity: 0 }}
              animate={{ 
                opacity: [0.3, 0.6, 0.3],
                boxShadow: [
                  `0 0 20px 5px ${rarityColor}40`,
                  `0 0 30px 8px ${rarityColor}60`,
                  `0 0 20px 5px ${rarityColor}40`
                ]
              }}
              transition={{
                duration: 3,
                repeat: Infinity,
                repeatType: "reverse",
                ease: "easeInOut"
              }}
            />
          )}

          {/* Combined Profile Preview */}
          <div className="item-preview-combined">
            {selectedItems.badge ? (
              // Show badge preview with nameplate
              <BadgePreview
                username={user?.username || "Username"}
                avatar={user?.avatar || "/default-avatar.png"}
                badgeUrl={getBadgeUrl() || ''}
                message="This is what your profile looks like"
                className="w-full"
                size="lg"
              />
            ) : selectedItems.nameplate ? (
              // Show nameplate preview only
              <NameplatePreview
                username={user?.username || "Username"}
                avatar={user?.avatar || "/default-avatar.png"}
                color={getNameplateColor()}
                fallbackColor={rarityColor}
                message="This is what your profile looks like"
                className="w-full"
                size="lg"
              />
            ) : (
              // Default profile preview
              <div className="item-preview-default-profile">
                <NameplatePreview
                  username={user?.username || "Username"}
                  avatar={user?.avatar || "/default-avatar.png"}
                  color={getNameplateColor()}
                  fallbackColor="#0088cc"
                  message="This is what your profile looks like"
                  className="w-full"
                  size="lg"
                />
              </div>
            )}
          </div>

          {/* Show case preview if a case is selected */}
          {primaryItem?.category === 'CASE' && (
            <div className="item-preview-case-overlay">
              <div className="case-preview-visual">
                {primaryItem.imageUrl ? (
                  <img 
                    src={primaryItem.imageUrl} 
                    alt={primaryItem.name}
                    className="case-preview-image"
                  />
                ) : (
                  <div 
                    className="case-preview-icon"
                    style={{ 
                      borderColor: rarityColor,
                      backgroundColor: `${rarityColor}20`
                    }}
                  >
                    <svg 
                      className="w-16 h-16"
                      style={{ color: rarityColor }}
                      fill="none" 
                      viewBox="0 0 24 24" 
                      stroke="currentColor"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                    </svg>
                  </div>
                )}
                
                {/* Case mystical effect */}
                <div 
                  className="case-preview-mystical"
                  style={{
                    background: `radial-gradient(circle at center, ${rarityColor}30 0%, transparent 70%)`
                  }}
                />
              </div>
              
              {/* Case stats */}
              <div className="case-preview-stats">
                {primaryItem.caseContentsCount && (
                  <div className="case-stat">
                    <span className="case-stat-label">Contains:</span>
                    <span className="case-stat-value">{primaryItem.caseContentsCount} items</span>
                  </div>
                )}
                {primaryItem.quantity && (
                  <div className="case-stat">
                    <span className="case-stat-label">Owned:</span>
                    <span className="case-stat-value">x{primaryItem.quantity}</span>
                  </div>
                )}
              </div>
              
              {/* View contents button */}
              {primaryItem.caseContentsCount && primaryItem.caseContentsCount > 0 && onViewCaseContents && (
                <button
                  onClick={() => onViewCaseContents(primaryItem.id, primaryItem.name)}
                  className="case-view-contents-btn"
                >
                  <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                  </svg>
                  View Contents & Drop Rates
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Details Section */}
      <div className="item-preview-details">
        {/* Show selected items list */}
        <div className="item-preview-selected-items">
          <h4 className="text-sm font-medium text-slate-300 mb-3">Selected Items</h4>
          <div className="space-y-2">
            {Object.entries(selectedItems).map(([category, item]) => {
              if (!item) return null;
              
              // Use direct sanitization instead of hook
              const sanitizedName = sanitizeText(item.name || '');
              const truncatedName = sanitizedName.length > 50 ? sanitizedName.substring(0, 50) : sanitizedName;
              const categoryLabel = category === 'nameplate' ? 'Nameplate' : 
                                 category === 'badge' ? 'Badge' : 
                                 category.charAt(0).toUpperCase() + category.slice(1);
              
              return (
                <div key={category} className="selected-item-row">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-2">
                      <span className="text-xs text-slate-400">{categoryLabel}:</span>
                      <SafeText 
                        text={truncatedName}
                        tag="span"
                        className="text-sm text-white font-medium"
                        maxLength={50}
                        showTooltip={true}
                      />
                      <div 
                        className="px-1.5 py-0.5 rounded text-xs font-semibold"
                        style={getRarityBadgeStyle(item.rarity)}
                      >
                        {getRarityLabel(item.rarity)}
                      </div>
                    </div>
                    {item.equipped && (
                      <span className="text-xs text-primary font-medium">Equipped</span>
                    )}
                  </div>
                </div>
              );
            })}
            
            {Object.values(selectedItems).every(item => !item) && (
              <div className="text-sm text-slate-400 italic">
                Click on items to preview them here
              </div>
            )}
          </div>
        </div>

        {/* Primary item description */}
        {primaryItem && (
          <div className="item-preview-description mt-4">
            <h4 className="text-sm font-medium text-slate-300 mb-2">Description</h4>
            <SafeText 
              text={(() => {
                // Use direct sanitization instead of hook
                const sanitizedDesc = sanitizeText(primaryItem.description || '');
                return sanitizedDesc.length > 500 ? sanitizedDesc.substring(0, 500) : sanitizedDesc;
              })()}
              tag="p"
              className="text-slate-400 text-sm leading-relaxed"
              maxLength={500}
              showTooltip={true}
            />
          </div>
        )}

        {/* Action button for primary item */}
        {primaryItem && (
          <div className="item-preview-actions mt-4">
            <button
              onClick={() => handleAction(primaryItem)}
              disabled={isActionDisabled(primaryItem)}
              className={`item-preview-action-btn ${
                primaryItem.category === 'CASE' ? 'case-action-btn' : 
                primaryItem.equipped ? 'unequip-action-btn' : 'equip-action-btn'
              } ${isActionDisabled(primaryItem) ? 'action-btn-disabled' : ''}`}
            >
              {actionInProgress !== null ? (
                <>
                  <svg className="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Processing...
                </>
              ) : (
                <>
                  {primaryItem.category === 'CASE' ? (
                    <FaCoins className="mr-2" size={16} />
                  ) : primaryItem.equipped ? (
                    <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  ) : (
                    <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                  )}
                  {getActionButtonText(primaryItem)}
                </>
              )}
            </button>
          </div>
        )}
      </div>
    </motion.div>
  );
};

export default ItemPreview; 