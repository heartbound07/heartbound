import React from 'react';
import { motion } from 'framer-motion';
import { FaCoins } from 'react-icons/fa';
import { GiFishingPole } from 'react-icons/gi';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { SafeText, sanitizeText } from '@/components/SafeHtmlRenderer';
import { ShopItem } from '@/types/inventory';

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
  onEquipMultipleItems?: (itemIds: string[]) => void;
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
  onEquipMultipleItems,
  actionInProgress
}) => {
  // Get the primary selected item (for action buttons)
  const primaryItem = Object.values(selectedItems).find(item => item !== null) || null;
  
  // Get all selected items (for batch operations)
  const allSelectedItems = Object.values(selectedItems).filter(item => item !== null) as ShopItem[];
  
  // Determine if we have multiple items selected
  const hasMultipleItems = allSelectedItems.length > 1;
  
  // Get unequipped items from selection (for batch equip)
  const unequippedSelectedItems = allSelectedItems.filter(item => !item.equipped);
  
  // Determine what action is available
  const getActionMode = () => {
    if (hasMultipleItems) {
      // Multiple items selected
      if (unequippedSelectedItems.length > 0) {
        return 'batch-equip'; // At least one unequipped item
      } else {
        return 'mixed-equipped'; // All selected items are equipped
      }
    } else if (primaryItem) {
      // Single item selected
      if (primaryItem.category === 'CASE') {
        return 'case-open';
      } else if (primaryItem.equipped) {
        return 'unequip';
      } else {
        return 'equip';
      }
    }
    return 'none';
  };
  
  const actionMode = getActionMode();
  
  // Get selected nameplate or fall back to equipped/default
  const getNameplateColor = () => {
    if (selectedItems.nameplate) {
      return selectedItems.nameplate.imageUrl;
    }
    // Debug logging for development
    if (import.meta.env.DEV && user?.nameplateColor) {
      console.log('[ItemPreview] Using equipped nameplate color:', user.nameplateColor);
    }
    // Fall back to user's equipped nameplate color or default (white instead of blue)
    return user?.nameplateColor || '#ffffff';
  };

  // Get selected badge or fall back to equipped badge
  const getBadgeUrl = () => {
    if (selectedItems.badge) {
      return selectedItems.badge.thumbnailUrl || selectedItems.badge.imageUrl;
    }
    // Fall back to user's equipped badge if available
    return user?.badgeUrl || null;
  };

  // Check if user has equipped badge for default state
  const hasEquippedBadge = () => {
    return user?.badgeUrl && user.badgeUrl.length > 0;
  };

  // Get the rarity color for the primary selected item (white when no items selected)
  const rarityColor = primaryItem ? getRarityColor(primaryItem.rarity) : '#ffffff';

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

  // Handle batch equip action
  const handleBatchAction = () => {
    if (actionMode === 'batch-equip' && onEquipMultipleItems && unequippedSelectedItems.length > 0) {
      const itemIds = unequippedSelectedItems.map(item => item.id);
      onEquipMultipleItems(itemIds);
    } else if (actionMode === 'case-open' && primaryItem) {
      handleAction(primaryItem);
    } else if (actionMode === 'equip' && primaryItem) {
      handleAction(primaryItem);
    } else if (actionMode === 'unequip' && primaryItem) {
      handleAction(primaryItem);
    }
  };

  const getActionButtonText = () => {
    switch (actionMode) {
      case 'batch-equip':
        const count = unequippedSelectedItems.length;
        return count === 1 ? 'Equip' : `Equip ${count} Items`;
      case 'case-open':
        return (!primaryItem?.quantity || primaryItem.quantity < 1) ? 'No Cases' : 'Open Case';
      case 'equip':
        // Show more descriptive text for badge replacement
        if (primaryItem?.category === 'BADGE' && user?.badgeUrl) {
          return 'Replace Equipped Badge';
        }
        return 'Equip';
      case 'unequip':
        return 'Unequip';
      case 'mixed-equipped':
        return 'All Equipped';
      default:
        return 'Select Items';
    }
  };

  const isActionDisabled = () => {
    if (actionMode === 'case-open' && primaryItem) {
      return !primaryItem.quantity || primaryItem.quantity < 1 || actionInProgress !== null;
    }
    if (actionMode === 'mixed-equipped' || actionMode === 'none') {
      return true;
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
            {/* Removed rarity badge - no longer showing rarity information */}
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
          style={{ 
            borderColor: primaryItem?.equipped 
              ? 'var(--color-primary, #0088cc)' 
              : 'transparent' // Always transparent - no rarity outline
          }}
        >
          {/* Rarity glow effect - only show when an item is selected */}
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

          {/* ADDED: Special preview for FISHING_ROD */}
          {primaryItem?.category === 'FISHING_ROD' ? (
            <div className="item-preview-visual fishing-rod-preview-container">
              <div className="h-full w-full bg-gradient-to-br from-blue-800 to-cyan-700 flex flex-col items-center justify-center relative overflow-hidden p-4">
                <GiFishingPole className="absolute w-32 h-32 text-white/10 transform -rotate-12 -right-6 -bottom-6" />
                <GiFishingPole className="relative z-10 w-20 h-20 text-white/80 drop-shadow-lg" />
                <div className="relative z-10 mt-3 text-center">
                  <p className="text-3xl font-bold text-white drop-shadow-md">
                    {primaryItem.fishingRodMultiplier}x
                  </p>
                  <p className="text-md font-semibold text-cyan-200 drop-shadow-sm">
                    Credit Bonus
                  </p>
                </div>
              </div>
            </div>
          ) : (
            <>
              {/* Combined Profile Preview */}
              <div className="item-preview-combined">
                {selectedItems.badge ? (
                  // Show badge preview with nameplate color
                  <BadgePreview
                    username={user?.username || "Username"}
                    avatar={user?.avatar || "/images/default-avatar.png"}
                    badgeUrl={getBadgeUrl() || ''}
                    message="This is what your profile looks like"
                    className="w-full"
                    size="lg"
                    nameplateColor={getNameplateColor()} // Pass the nameplate color
                  />
                ) : selectedItems.nameplate ? (
                  // Show nameplate preview only
                  <NameplatePreview
                    username={user?.username || "Username"}
                    avatar={user?.avatar || "/images/default-avatar.png"}
                    color={selectedItems.nameplate.imageUrl}
                    endColor={selectedItems.nameplate.gradientEndColor}
                    fallbackColor={rarityColor}
                    message="This is what your profile looks like"
                    className="w-full"
                    size="lg"
                  />
                ) : (
                  // Default profile preview - show equipped badge if available, otherwise nameplate
                  <div className="item-preview-default-profile">
                    {hasEquippedBadge() ? (
                      <BadgePreview
                        username={user?.username || "Username"}
                        avatar={user?.avatar || "/images/default-avatar.png"}
                        badgeUrl={getBadgeUrl() || ''}
                        message="This is what your profile looks like"
                        className="w-full"
                        size="lg"
                        nameplateColor={getNameplateColor()} // Show both equipped badge and nameplate color
                      />
                    ) : (
                      <NameplatePreview
                        username={user?.username || "Username"}
                        avatar={user?.avatar || "/images/default-avatar.png"}
                        color={getNameplateColor()}
                        fallbackColor="#ffffff"
                        message="This is what your profile looks like"
                        className="w-full"
                        size="lg"
                      />
                    )}
                  </div>
                )}
              </div>
            </>
          )}

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
        {/* Item Name */}
        {primaryItem && (
          <div className="item-preview-name-section">
            <SafeText
              text={primaryItem.name}
              tag="h3"
              className="item-preview-name"
              maxLength={100}
              showTooltip={true}
            />
            <div
              className="px-2 py-0.5 rounded text-xs font-semibold"
              style={getRarityBadgeStyle(primaryItem.rarity)}
            >
              {getRarityLabel(primaryItem.rarity)}
            </div>
          </div>
        )}
        
        {/* ADDED: Fishing Rod Multiplier Details */}
        {primaryItem?.category === 'FISHING_ROD' && (
          <div className="text-center my-3 p-3 bg-slate-800/50 rounded-lg border border-slate-700">
            <p className="text-lg font-bold text-cyan-400">
              {primaryItem.fishingRodMultiplier}x Credit Multiplier
            </p>
            <p className="text-xs text-slate-400 mt-1">
              With this rod equipped, a <strong>20</strong> credit fish will now award <strong>{20 * (primaryItem.fishingRodMultiplier || 1)}</strong> credits!
            </p>
          </div>
        )}

        {/* Show selected items list */}
        <div className="item-preview-selected-items">
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
          </div>
        </div>

        {/* Action button for primary item */}
        {primaryItem && (
          <div className="item-preview-actions mt-4">
            <button
              onClick={handleBatchAction}
              disabled={isActionDisabled()}
              className={`item-preview-action-btn ${
                actionMode === 'batch-equip' ? 'batch-equip-action-btn' :
                actionMode === 'case-open' ? 'case-action-btn' :
                actionMode === 'equip' ? 'equip-action-btn' :
                actionMode === 'unequip' ? 'unequip-action-btn' :
                actionMode === 'mixed-equipped' ? 'mixed-equipped-action-btn' :
                'action-btn-disabled'
              } ${isActionDisabled() ? 'action-btn-disabled' : ''}`}
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
                  {actionMode === 'batch-equip' ? (
                    <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                  ) : actionMode === 'case-open' ? (
                    <FaCoins className="mr-2" size={16} />
                  ) : actionMode === 'equip' ? (
                    <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                  ) : actionMode === 'unequip' ? (
                    <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  ) : actionMode === 'mixed-equipped' ? (
                    <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
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
        )}
      </div>
    </motion.div>
  );
};

export default ItemPreview; 