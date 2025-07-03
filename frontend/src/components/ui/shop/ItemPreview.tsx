import React from 'react';
import { motion } from 'framer-motion';
import { FaCoins } from 'react-icons/fa';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { SafeText } from '@/components/SafeHtmlRenderer';
import { useSanitizedContent } from '@/hooks/useSanitizedContent';
import { ShopItem } from '@/features/shop/InventoryPage';

interface ItemPreviewProps {
  item: ShopItem | null;
  user: any;
  onEquip?: (itemId: string) => void;
  onUnequip?: (category: string) => void;
  onUnequipBadge?: (badgeId: string) => void;
  onOpenCase?: (caseId: string, caseName: string) => void;
  onViewCaseContents?: (caseId: string, caseName: string) => void;
  actionInProgress?: string | null;
}

export const ItemPreview: React.FC<ItemPreviewProps> = ({
  item,
  user,
  onEquip,
  onUnequip,
  onUnequipBadge,
  onOpenCase,
  onViewCaseContents,
  actionInProgress
}) => {
  // Sanitize content for safe display
  const nameContent = item ? useSanitizedContent(item.name, { maxLength: 100, stripHtml: true }) : null;
  const descriptionContent = item ? useSanitizedContent(item.description, { maxLength: 500, stripHtml: true }) : null;

  if (!item) {
    return (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="item-preview-container item-preview-empty"
      >
        <div className="item-preview-empty-content">
          <div className="item-preview-empty-icon">
            <svg className="w-16 h-16" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
            </svg>
          </div>
          <h3 className="item-preview-empty-title">Select an Item</h3>
          <p className="item-preview-empty-subtitle">
            Click on any item in your inventory to see a detailed preview here.
          </p>
        </div>
      </motion.div>
    );
  }

  const rarityColor = getRarityColor(item.rarity);

  const handleAction = () => {
    if (!onEquip || !onUnequip || !onUnequipBadge || !onOpenCase) return;

    if (item.category === 'CASE') {
      onOpenCase(item.id, nameContent?.sanitized || item.name);
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
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="item-preview-container"
    >
      {/* Header */}
      <div className="item-preview-header">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <SafeText 
              text={nameContent?.sanitized || item.name}
              tag="h2"
              className="item-preview-title"
              maxLength={100}
              showTooltip={true}
            />
            <div 
              className="px-3 py-1 rounded-lg text-sm font-semibold"
              style={getRarityBadgeStyle(item.rarity)}
            >
              {getRarityLabel(item.rarity)}
            </div>
          </div>
          
          {item.equipped && (
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
          style={{ borderColor: item.equipped ? 'var(--color-primary, #0088cc)' : rarityColor }}
        >
          {/* Rarity glow effect */}
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

          {/* Category-specific preview */}
          {item.category === 'USER_COLOR' ? (
            <div className="item-preview-nameplate">
              <NameplatePreview
                username={user?.username || "Username"}
                avatar={user?.avatar || "/default-avatar.png"}
                color={item.imageUrl}
                fallbackColor={rarityColor}
                message="This is how your nameplate will look!"
                className="w-full"
                size="lg"
              />
            </div>
          ) : item.category === 'BADGE' ? (
            <div className="item-preview-badge">
              <BadgePreview
                username={user?.username || "Username"}
                avatar={user?.avatar || "/default-avatar.png"}
                badgeUrl={item.thumbnailUrl || item.imageUrl}
                message="This is how your badge will appear!"
                className="w-full"
                size="lg"
              />
            </div>
          ) : item.category === 'CASE' ? (
            <div className="item-preview-case">
              <div className="case-preview-visual">
                {item.imageUrl ? (
                  <img 
                    src={item.imageUrl} 
                    alt={nameContent?.sanitized || item.name}
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
                {item.caseContentsCount && (
                  <div className="case-stat">
                    <span className="case-stat-label">Contains:</span>
                    <span className="case-stat-value">{item.caseContentsCount} items</span>
                  </div>
                )}
                {item.quantity && (
                  <div className="case-stat">
                    <span className="case-stat-label">Owned:</span>
                    <span className="case-stat-value">x{item.quantity}</span>
                  </div>
                )}
              </div>
              
              {/* View contents button */}
              {item.caseContentsCount && item.caseContentsCount > 0 && onViewCaseContents && (
                <button
                  onClick={() => onViewCaseContents(item.id, nameContent?.sanitized || item.name)}
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
          ) : (
            <div className="item-preview-standard">
              {item.imageUrl ? (
                <img 
                  src={item.imageUrl} 
                  alt={nameContent?.sanitized || item.name}
                  className="standard-preview-image"
                />
              ) : (
                <div className="standard-preview-placeholder">
                  <span>No Image</span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Details Section */}
      <div className="item-preview-details">
        {descriptionContent?.sanitized && (
          <div className="item-preview-description">
            <h4 className="text-sm font-medium text-slate-300 mb-2">Description</h4>
            <SafeText 
              text={descriptionContent.sanitized}
              tag="p"
              className="text-slate-400 text-sm leading-relaxed"
              maxLength={500}
              showTooltip={true}
            />
          </div>
        )}

        {/* Action button */}
        <div className="item-preview-actions">
          <button
            onClick={handleAction}
            disabled={isActionDisabled()}
            className={`item-preview-action-btn ${
              item.category === 'CASE' ? 'case-action-btn' : 
              item.equipped ? 'unequip-action-btn' : 'equip-action-btn'
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
                {item.category === 'CASE' ? (
                  <FaCoins className="mr-2" size={16} />
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
      </div>
    </motion.div>
  );
};

export default ItemPreview; 