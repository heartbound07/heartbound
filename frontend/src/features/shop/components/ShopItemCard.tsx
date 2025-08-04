import React, { useState, forwardRef } from 'react';
import { motion } from 'framer-motion';
import { FaCoins, FaInfoCircle } from 'react-icons/fa';
import { GiFishingPole, GiSewingString, GiFishingHook, GiGearStick } from 'react-icons/gi';
import { PiFilmReel, PiHandPalm } from 'react-icons/pi';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { SafeText } from '@/components/SafeHtmlRenderer';
import { useSanitizedContent } from '@/hooks/useSanitizedContent';
import { ShopItem } from '@/types/shop';
import { UserProfile } from '@/types/shop';

interface ShopItemCardProps {
  item: ShopItem;
  handlePurchase: (id: string, quantity?: number) => void;
  purchaseInProgress: boolean;
  user: UserProfile | null;
  isRecentlyPurchased?: boolean;
  onViewCaseContents?: (caseId: string, caseName: string) => void;
}

const ShopItemCard = forwardRef<HTMLDivElement, ShopItemCardProps>(({ 
  item, 
  handlePurchase, 
  purchaseInProgress, 
  user,
  isRecentlyPurchased = false,
  onViewCaseContents
}, ref) => {
  const rarityColor = getRarityColor(item.rarity);
  
  const nameContent = useSanitizedContent(item.name, { maxLength: 100, stripHtml: true });
  const descriptionContent = useSanitizedContent(item.description, { maxLength: 500, stripHtml: true });
  
  const [showInsufficientCredits, setShowInsufficientCredits] = useState(false);
  const [quantity, setQuantity] = useState(1);
  
  const totalPrice = item.category === 'CASE' ? item.price * quantity : item.price;
  
  const handlePurchaseAttempt = () => {
    if ((user?.credits ?? 0) < totalPrice) {
      setShowInsufficientCredits(true);
      setTimeout(() => {
        setShowInsufficientCredits(false);
      }, 3000);
    } else {
      handlePurchase(item.id, item.category === 'CASE' ? quantity : undefined);
    }
  };

  const partIcons: Record<string, React.ElementType> = {
    ROD_SHAFT: GiFishingPole,
    REEL: PiFilmReel,
    FISHING_LINE: GiSewingString,
    HOOK: GiFishingHook,
    GRIP: PiHandPalm,
  };
  
  return (
    <motion.div
      ref={ref}
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -5 }}
      className={`shop-item-card ${isRecentlyPurchased ? 'shop-item-recent-purchase' : ''}`}
      style={{ borderColor: 'transparent' }}
    >
      {item.category === 'USER_COLOR' ? (
        <div className="shop-item-image discord-preview">
          <NameplatePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/images/default-avatar.png"}
            color={item.imageUrl}
            endColor={item.gradientEndColor}
            fallbackColor={rarityColor}
            message="This is the color for the Nameplate!"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : item.category === 'BADGE' ? (
        <div className="shop-item-image discord-preview">
          <BadgePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/images/default-avatar.png"}
            badgeUrl={item.thumbnailUrl || item.imageUrl}
            message="This is how the badge will appear!"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : item.category === 'CASE' ? (
        <div className="shop-item-image case-preview-container">
          <div className="h-full w-full bg-gradient-to-br from-slate-700 to-slate-800 flex flex-col items-center justify-center relative overflow-hidden">
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
            {item.category === 'CASE' && item.caseContentsCount && item.caseContentsCount > 0 && (
              <div className="mt-2 text-xs text-slate-300 font-medium">
                Contains {item.caseContentsCount} items
              </div>
            )}
            <div 
              className="absolute inset-0 opacity-20"
              style={{
                background: `radial-gradient(circle at center, ${rarityColor}40 0%, transparent 70%)`
              }}
            />
          </div>
          <div className="absolute top-2 left-2">
            <div 
              className="px-2 py-1 rounded text-xs font-semibold border"
              style={{
                backgroundColor: `${rarityColor}20`,
                color: rarityColor,
                borderColor: rarityColor
              }}
            >
              Case
            </div>
          </div>
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
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : item.category === 'FISHING_ROD_PART' ? (() => {
          const PartIcon = item.fishingRodPartType ? partIcons[item.fishingRodPartType] || GiGearStick : GiGearStick;
          return (
            <div className="shop-item-image fishing-rod-preview-container">
              <div 
                className="h-full w-full flex flex-col items-center justify-center relative overflow-hidden p-4"
                style={{ background: `linear-gradient(to bottom right, #1f2937, ${rarityColor})` }}
              >
                <PartIcon
                  className="absolute w-24 h-24 text-white/10 transform -rotate-12 -right-4 -bottom-4"
                />
                <PartIcon
                  className="relative z-10 w-16 h-16 text-white/80 drop-shadow-lg"
                />
              </div>
              {isRecentlyPurchased && (
                <motion.div
                  initial={{ opacity: 0.8 }}
                  animate={{ opacity: 0 }}
                  transition={{ duration: 2 }}
                  className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
                />
              )}
            </div>
          );
        })()
       : item.category === 'FISHING_ROD' ? (
        <div className="shop-item-image fishing-rod-preview-container">
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
              <p className="text-2xl font-bold text-white drop-shadow-md">
                {item.fishingRodMultiplier}x
              </p>
              <p className="text-sm font-semibold text-cyan-200 drop-shadow-sm">
                Credit Bonus
              </p>
            </div>
          </div>
           {isRecentlyPurchased && (
            <motion.div
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : (
        <div className="shop-item-image">
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
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
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
          <div className="flex items-center space-x-3">
            {item.category === 'CASE' && (
              <div className="flex items-center space-x-1">
                <button
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  disabled={quantity <= 1}
                  className="w-5 h-5 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 disabled:cursor-not-allowed rounded flex items-center justify-center text-white text-xs transition-colors"
                >
                  −
                </button>
                <span className="text-white font-medium min-w-[1.5rem] text-center text-sm">{quantity}</span>
                <button
                  onClick={() => setQuantity(Math.min(10, quantity + 1))}
                  disabled={quantity >= 10}
                  className="w-5 h-5 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 disabled:cursor-not-allowed rounded flex items-center justify-center text-white text-xs transition-colors"
                >
                  +
                </button>
              </div>
            )}
            <div className="flex items-center">
              <FaCoins className="text-yellow-400 mr-1" size={14} />
              <span className="text-yellow-400 font-medium">
                {item.category === 'CASE' && quantity > 1 
                  ? totalPrice 
                  : item.price
                }
              </span>
            </div>
          </div>
        </div>
        
        {/* Stat bars for FISHING_ROD_PART items */}
        {item.category === 'FISHING_ROD_PART' && (
          <div className="mt-2 mb-3 space-y-2">
            {/* Fortune stat */}
            {((item as any).bonusLootChance || 0) > 0 && (
              <div>
                <div className="flex justify-between text-xs text-slate-400 mb-1">
                  <span>Fortune</span>
                  <span className="font-semibold text-yellow-400">+{((item as any).bonusLootChance || 0).toFixed(0)}%</span>
                </div>
                <div className="w-full bg-slate-700 rounded-full h-2">
                  <div 
                    className="bg-yellow-500 h-2 rounded-full"
                    style={{ width: `${Math.min(((item as any).bonusLootChance || 0) / 25 * 100, 100)}%` }}
                  ></div>
                </div>
              </div>
            )}
            
            {/* Rarity stat */}
            {((item as any).rarityChanceIncrease || 0) > 0 && (
              <div>
                <div className="flex justify-between text-xs text-slate-400 mb-1">
                  <span>Rarity</span>
                  <span className="font-semibold text-purple-400">+{((item as any).rarityChanceIncrease || 0).toFixed(0)}%</span>
                </div>
                <div className="w-full bg-slate-700 rounded-full h-2">
                  <div 
                    className="bg-purple-500 h-2 rounded-full"
                    style={{ width: `${Math.min(((item as any).rarityChanceIncrease || 0) / 25 * 100, 100)}%` }}
                  ></div>
                </div>
              </div>
            )}
            
            {/* Reward Boost stat */}
            {((item as any).multiplierIncrease || 0) > 0 && (
              <div>
                <div className="flex justify-between text-xs text-slate-400 mb-1">
                  <span>Reward Boost</span>
                  <span className="font-semibold text-cyan-400">+{((item as any).multiplierIncrease || 0).toFixed(1)}x</span>
                </div>
                <div className="w-full bg-slate-700 rounded-full h-2">
                  <div 
                    className="bg-cyan-500 h-2 rounded-full"
                    style={{ width: `${Math.min(((item as any).multiplierIncrease || 0) / 0.5 * 100, 100)}%` }}
                  ></div>
                </div>
              </div>
            )}
            
            {/* Stability stat */}
            {((item as any).negationChance || 0) > 0 && (
              <div>
                <div className="flex justify-between text-xs text-slate-400 mb-1">
                  <span>Stability</span>
                  <span className="font-semibold text-red-500">+{((item as any).negationChance || 0).toFixed(0)}%</span>
                </div>
                <div className="w-full bg-slate-700 rounded-full h-2">
                  <div 
                    className="bg-red-500 h-2 rounded-full"
                    style={{ width: `${Math.min(((item as any).negationChance || 0) / 25 * 100, 100)}%` }}
                  ></div>
                </div>
              </div>
            )}
          </div>
        )}
        
        {descriptionContent.sanitized && (
          <SafeText 
            text={descriptionContent.sanitized}
            tag="p"
            className="text-slate-300 text-sm mb-3 line-clamp-2"
            maxLength={200}
            showTooltip={true}
          />
        )}
        
        {(item.owned && item.category !== 'CASE') ? (
          <button 
            disabled
            className="purchase-button purchase-button-owned"
          >
            <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
            Owned
          </button>
        ) : item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) ? (
          <button 
            disabled
            className="purchase-button purchase-button-required"
          >
            <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
            {item.requiredRole} Required
          </button>
        ) : (
          <button 
            onClick={handlePurchaseAttempt}
            disabled={purchaseInProgress || showInsufficientCredits}
            className={`purchase-button ${
              showInsufficientCredits 
                ? 'purchase-button-insufficient' 
                : 'purchase-button-active'
            } ${purchaseInProgress ? 'purchase-button-processing' : ''}`}
          >
            {purchaseInProgress ? (
              <>
                <svg className="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Processing...
              </>
            ) : showInsufficientCredits ? (
              <>
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Not Enough Credits
              </>
            ) : (
              <>
                <FaCoins className="mr-2" size={14} />
                {item.category === 'CASE' && quantity > 1 ? `Purchase ${quantity}` : 'Purchase'}
              </>
            )}
          </button>
        )}
      </div>
    </motion.div>
  );
});

ShopItemCard.displayName = 'ShopItemCard';

export default React.memo(ShopItemCard); 