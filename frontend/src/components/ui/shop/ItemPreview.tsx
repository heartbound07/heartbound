import React from 'react';
import { motion } from 'framer-motion';
import { FaCoins } from 'react-icons/fa';
import { GiFishingPole } from 'react-icons/gi';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { SafeText, sanitizeText } from '@/components/SafeHtmlRenderer';
import { ShopItem } from '@/types/inventory';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/valorant/popover';
import { useMemo } from 'react';

interface ItemPreviewProps {
  selectedItems: {
    nameplate?: ShopItem | null;
    badge?: ShopItem | null;
    [key: string]: ShopItem | null | undefined;
  };
  user: any;
  onEquip?: (itemId: string, instanceId?: string) => void;
  onUnequip?: (category: string) => void;
  onUnequipBadge?: (badgeId: string) => void;
  onOpenCase?: (caseId: string, caseName: string) => void;
  onEquipMultipleItems?: (itemIds: string[]) => void;
  onUnequipMultipleItems?: (itemIds: string[]) => void;
  actionInProgress?: string | null;
}

export const ItemPreview: React.FC<ItemPreviewProps> = ({
  selectedItems,
  user,
  onEquip,
  onUnequip,
  onUnequipBadge,
  onOpenCase,
  onEquipMultipleItems,
  onUnequipMultipleItems,
  actionInProgress
}) => {
  // Get the primary selected item (for action buttons)
  const primaryItem = Object.values(selectedItems).find(item => item !== null) || null;
  
  // Get all selected items (for batch operations)
  const allSelectedItems = Object.values(selectedItems).filter(item => item !== null) as ShopItem[];
  
  const aggregatedStats = useMemo(() => {
    if (!primaryItem) return null;

    if (primaryItem.category === 'FISHING_ROD' || primaryItem.category === 'FISHING_ROD_PART') {
      let totalBonusLootChance = 0;
      let totalRarityChanceIncrease = 0;
      let totalMultiplierIncrease = 0;
      let totalNegationChance = 0;

      if (primaryItem.category === 'FISHING_ROD') {
        if (primaryItem.equippedParts) {
          for (const part of Object.values(primaryItem.equippedParts)) {
            if (part) {
              totalBonusLootChance += part.bonusLootChance || 0;
              totalRarityChanceIncrease += part.rarityChanceIncrease || 0;
              totalMultiplierIncrease += part.multiplierIncrease || 0;
              totalNegationChance += part.negationChance || 0;
            }
          }
        }
      } else { // FISHING_ROD_PART
        totalBonusLootChance = primaryItem.bonusLootChance || 0;
        totalRarityChanceIncrease = primaryItem.rarityChanceIncrease || 0;
        totalMultiplierIncrease = primaryItem.multiplierIncrease || 0;
        totalNegationChance = primaryItem.negationChance || 0;
      }

      return {
        bonusLootChance: totalBonusLootChance,
        rarityChanceIncrease: totalRarityChanceIncrease,
        multiplierIncrease: totalMultiplierIncrease,
        negationChance: totalNegationChance,
      };
    }

    return null;
  }, [primaryItem]);

  // Determine if we have multiple items selected
  const hasMultipleItems = allSelectedItems.length > 1;
  
  // Get unequipped items from selection (for batch equip)
  const unequippedSelectedItems = allSelectedItems.filter(item => !item.equipped);
  
  // Get equipped items from selection (for batch unequip)
  const equippedSelectedItems = allSelectedItems.filter(item => item.equipped);

  // Determine what action is available
  const getActionMode = () => {
    if (hasMultipleItems) {
      // Multiple items selected
      if (unequippedSelectedItems.length > 0) {
        return 'batch-equip'; // At least one unequipped item
      } else {
        return 'batch-unequip'; // All selected items are equipped
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
  
  // Determine what badge to show in the preview
  const getPreviewBadgeUrl = () => {
    // If a badge is selected...
    if (selectedItems.badge) {
      // ...and it's equipped, we're previewing its removal, so show no badge.
      if (selectedItems.badge.equipped) {
        return null;
      }
      // ...and it's not equipped, we're previewing adding it.
      return selectedItems.badge.thumbnailUrl || selectedItems.badge.imageUrl;
    }
    // If no badge is selected, just show the user's currently equipped badge.
    return user?.badgeUrl || null;
  };

  // Determine what nameplate colors to show in the preview
  const getPreviewNameplate = () => {
    // Start with the user's currently equipped colors, or defaults.
    let color = user?.nameplateColor || '#ffffff';
    let endColor = user?.gradientEndColor;

    // If a nameplate is selected...
    if (selectedItems.nameplate) {
      // ...and it's equipped, we're previewing its removal. Show default white.
      if (selectedItems.nameplate.equipped) {
        color = '#ffffff';
        endColor = undefined;
      } else {
        // ...and it's not equipped, we're previewing adding it.
        color = selectedItems.nameplate.imageUrl;
        endColor = selectedItems.nameplate.gradientEndColor;
      }
    }
    
    return { color, endColor };
  };

  const previewBadgeUrl = getPreviewBadgeUrl();
  const { color: previewNameplateColor, endColor: previewNameplateEndColor } = getPreviewNameplate();
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
        onEquip(item.id, item.instanceId);
      }
    } else {
      if (item.equipped) {
        onUnequip(item.category);
      } else {
        onEquip(item.id, item.instanceId);
      }
    }
  };

  // Handle batch equip action
  const handleBatchAction = () => {
    if (actionMode === 'batch-equip' && onEquipMultipleItems && unequippedSelectedItems.length > 0) {
      const itemIds = unequippedSelectedItems.map(item => item.id);
      onEquipMultipleItems(itemIds);
    } else if (actionMode === 'batch-unequip' && onUnequipMultipleItems && equippedSelectedItems.length > 0) {
      const itemIds = equippedSelectedItems.map(item => item.id);
      onUnequipMultipleItems(itemIds);
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
      case 'batch-unequip':
        const unequipCount = equippedSelectedItems.length;
        return `Unequip ${unequipCount} Items`;
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
      default:
        return 'Select Items';
    }
  };

  const isActionDisabled = () => {
    if (actionMode === 'case-open' && primaryItem) {
      return !primaryItem.quantity || primaryItem.quantity < 1 || actionInProgress !== null;
    }
    if (actionMode === 'none') {
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
        </div>
      </div>

      {/* Main Preview Area */}
      <div className="item-preview-main">
        <div 
          className="item-preview-visual"
          style={{ 
            borderColor: 'transparent'
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
              <div 
                className="h-full w-full flex flex-col items-center justify-center relative overflow-hidden p-4"
                style={{ background: `linear-gradient(to bottom right, #1f2937, ${rarityColor})` }}
              >
                <GiFishingPole className="absolute w-32 h-32 text-white/10 transform -rotate-12 -right-6 -bottom-6" />
                <GiFishingPole className="relative z-10 w-20 h-20 text-white/80 drop-shadow-lg" />
                <div className="relative z-10 mt-3 text-center">
                  {primaryItem.level && (
                    <p className="text-lg font-bold text-white drop-shadow-md">
                      LVL {primaryItem.level}
                    </p>
                  )}
                  <SafeText
                    text={primaryItem.name}
                    tag="p"
                    className="text-md font-semibold text-white drop-shadow-sm mt-1"
                  />
                </div>
              </div>
            </div>
          ) : primaryItem?.category !== 'CASE' ? (
            <>
              {/* Combined Profile Preview */}
              <div className="item-preview-combined">
                {previewBadgeUrl ? (
                  // If we have a badge to show, render BadgePreview
                  <BadgePreview
                    username={user?.username || "Username"}
                    avatar={user?.avatar || "/images/default-avatar.png"}
                    badgeUrl={previewBadgeUrl}
                    message="This is what your profile looks like"
                    className="w-full"
                    size="lg"
                    nameplateColor={previewNameplateColor}
                    nameplateEndColor={previewNameplateEndColor}
                  />
                ) : (
                  // Otherwise, render NameplatePreview
                  <NameplatePreview
                    username={user?.username || "Username"}
                    avatar={user?.avatar || "/images/default-avatar.png"}
                    color={previewNameplateColor}
                    endColor={previewNameplateEndColor}
                    fallbackColor={rarityColor}
                    message="This is what your profile looks like"
                    className="w-full"
                    size="lg"
                  />
                )}
              </div>
            </>
          ) : null}

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
              
            </div>
          )}
        </div>
      </div>

      {/* Details Section */}
      <div className="item-preview-details">
        {/* Show selected items list */}
        {actionMode !== 'unequip' && (
          <div className="item-preview-selected-items">
            <div className="space-y-2">
              {Object.entries(selectedItems).map(([category, item]) => {
                if (!item || item.category === 'CASE') return null;

                // Don't show the main rod entry if it has parts, as they'll be listed below.
                if (item.category === 'FISHING_ROD' && item.equippedParts && Object.keys(item.equippedParts).length > 0) {
                  return null;
                }
                
                // Use direct sanitization instead of hook
                const sanitizedName = sanitizeText(item.name || '');
                const truncatedName = sanitizedName.length > 50 ? sanitizedName.substring(0, 50) : sanitizedName;
                const categoryLabel = category === 'nameplate' ? 'Nameplate' : 
                                   category === 'badge' ? 'Badge' : 
                                   category === 'fishing_rod' ? 'Fishing Rod' : 
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
        )}

        {/* Action button for primary item */}
        {primaryItem && (
          <div className="item-preview-actions mt-4">
            <button
              onClick={handleBatchAction}
              disabled={isActionDisabled()}
              className={`item-preview-action-btn ${
                actionMode === 'batch-equip' ? 'batch-equip-action-btn' :
                actionMode === 'batch-unequip' ? 'unequip-action-btn' :
                actionMode === 'case-open' ? 'case-action-btn' :
                actionMode === 'equip' ? 'equip-action-btn' :
                actionMode === 'unequip' ? 'unequip-action-btn' :
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
                  ) : actionMode === 'batch-unequip' ? (
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
        )}

        {aggregatedStats && (
          <div className="item-preview-stats">
              <div className="space-y-2 text-sm">
                  <div className="flex justify-between items-center">
                      <div className="flex items-center space-x-1.5">
                          <span className="font-medium text-slate-300">Fortune</span>
                          <Popover>
                              <PopoverTrigger asChild>
                                  <button className="flex items-center justify-center">
                                  <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 text-slate-400 hover:text-white transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                      <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                  </svg>
                                  </button>
                              </PopoverTrigger>
                              <PopoverContent><p>Increases the chance of finding bonus loot with each catch.</p></PopoverContent>
                          </Popover>
                      </div>
                      <span className="font-semibold text-yellow-400">{aggregatedStats.bonusLootChance > 0 ? `+${aggregatedStats.bonusLootChance.toFixed(0)}%` : '0%'}</span>
                  </div>
                  <div className="flex justify-between items-center">
                      <div className="flex items-center space-x-1.5">
                          <span className="font-medium text-slate-300">Rarity</span>
                          <Popover>
                              <PopoverTrigger asChild>
                              <button className="flex items-center justify-center">
                                  <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 text-slate-400 hover:text-white transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                      <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                  </svg>
                                  </button>
                              </PopoverTrigger>
                              <PopoverContent><p>Increases the chance of catching higher-tier fish and items.</p></PopoverContent>
                          </Popover>
                      </div>
                      <span className="font-semibold text-purple-400">{aggregatedStats.rarityChanceIncrease > 0 ? `+${aggregatedStats.rarityChanceIncrease.toFixed(0)}%` : '0%'}</span>
                  </div>
                  <div className="flex justify-between items-center">
                      <div className="flex items-center space-x-1.5">
                          <span className="font-medium text-slate-300">Reward Boost</span>
                          <Popover>
                              <PopoverTrigger asChild>
                              <button className="flex items-center justify-center">
                                  <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 text-slate-400 hover:text-white transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                      <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                  </svg>
                                  </button>
                              </PopoverTrigger>
                              <PopoverContent><p>Provides a flat boost to the credits earned from fishing.</p></PopoverContent>
                          </Popover>
                      </div>
                      <span className="font-semibold text-cyan-400">{`+${aggregatedStats.multiplierIncrease.toFixed(1)}x`}</span>
                  </div>
                  <div className="flex justify-between items-center">
                      <div className="flex items-center space-x-1.5">
                          <span className="font-medium text-slate-300">Stability</span>
                          <Popover>
                              <PopoverTrigger asChild>
                              <button className="flex items-center justify-center">
                                  <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 text-slate-400 hover:text-white transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                      <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                  </svg>
                                  </button>
                              </PopoverTrigger>
                              <PopoverContent><p>Increases resistance to negative events, like crabs snipping you</p></PopoverContent>
                          </Popover>
                      </div>
                      <span className="font-semibold text-red-500">{aggregatedStats.negationChance > 0 ? `+${aggregatedStats.negationChance.toFixed(0)}%` : '0%'}</span>
                  </div>
              </div>
          </div>
      )}
      </div>
    </motion.div>
  );
};

export default ItemPreview; 