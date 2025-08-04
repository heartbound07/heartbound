import { forwardRef } from 'react';
import { motion } from 'framer-motion';
import { GiFishingPole, GiSewingString, GiFishingHook, GiGearStick } from 'react-icons/gi';
import { PiFilmReel, PiHandPalm } from 'react-icons/pi';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { SafeText } from '@/components/SafeHtmlRenderer';
import { useSanitizedContent } from '@/hooks/useSanitizedContent';
import React from 'react';
import { ShopItem } from '@/types/inventory';
import { HiOutlinePlus } from 'react-icons/hi';
import { UserProfileDTO } from '@/config/userService';

interface InventoryItemCardProps {
    item: ShopItem;
    user: UserProfileDTO | null;
    isSelected?: boolean;
    onSelect: (item: ShopItem) => void;
    onOpenPartsModal: (item: ShopItem) => void;
    onRepair: (item: ShopItem) => void;
}

// Inventory Item Card Component (based on ShopItemCard design)
export const InventoryItemCard = forwardRef<HTMLDivElement, InventoryItemCardProps>(({ 
  item, 
  user,
  isSelected = false,
  onSelect,
  onOpenPartsModal,
  onRepair
}, ref) => {
  // Get rarity color for border
  const rarityColor = getRarityColor(item.rarity);
  
  // Sanitize content for safe display
  const nameContent = useSanitizedContent(item.name, { maxLength: 100, stripHtml: true });

  const partIcons: Record<string, React.ElementType> = {
    ROD_SHAFT: GiFishingPole,
    REEL: PiFilmReel,
    FISHING_LINE: GiSewingString,
    HOOK: GiFishingHook,
    GRIP: PiHandPalm,
  };
  
  return (
    <motion.div
      ref={ref as React.RefObject<HTMLDivElement>}
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -5 }}
      className={`shop-item-card inventory-item-card flex flex-col ${isSelected ? 'inventory-item-selected' : ''} ${item.equipped ? 'inventory-item-equipped' : ''}`}
      style={{ borderColor: isSelected ? 'var(--color-primary, #0088cc)' : 'transparent' }}
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
            message=""
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
             <div className="relative z-10 mt-2 flex items-center justify-center">
              {item.level && (
                <div className="text-sm font-bold text-white drop-shadow-md">
                  LVL {item.level}
                </div>
              )}
            </div>
          </div>
          {item.equipped && (
            <div className="item-badge badge-equipped">
              Equipped
            </div>
          )}
          <button 
            onClick={(e) => {
              e.stopPropagation();
              onOpenPartsModal(item);
            }}
            className="absolute top-2 left-2 z-20 w-8 h-8 bg-slate-800/50 hover:bg-slate-700/80 rounded-full flex items-center justify-center text-white transition-all duration-200"
            title="Customize Rod"
          >
            <HiOutlinePlus size={20} />
          </button>
        </div>
      ) : item.category === 'FISHING_ROD_PART' ? (() => {
          const PartIcon = item.fishingRodPartType ? partIcons[item.fishingRodPartType] || GiGearStick : GiGearStick;
          return (
            <div className="shop-item-image inventory-item-image fishing-rod-preview-container">
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
            </div>
          );
        })()
       : (
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
      
      <div className="shop-item-content flex flex-col flex-grow">
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
        </div>
        
        {item.category === 'FISHING_ROD' && (
          <div className="mt-2 space-y-2">
            {/* Show durability section - now always available after migration */}
            <div>
              <div className="flex justify-between text-xs text-slate-400 mb-1">
                <span>Durability</span>
                <span>{item.durability || 0} / {item.maxDurability || 0}</span>
              </div>
              <div className="w-full bg-slate-700 rounded-full h-2">
                <div 
                  className="bg-green-500 h-2 rounded-full"
                  style={{ width: `${(item.durability && item.maxDurability) ? (item.durability / item.maxDurability) * 100 : 0}%`}}
                ></div>
              </div>
            </div>
            
            {/* Show experience section if available */}
            {(item.experience !== null && item.experience !== undefined) && (
              <div>
                <div className="flex justify-between text-xs text-slate-400 mb-1">
                  <span>Experience</span>
                  <span>{item.experience} XP</span>
                </div>
                <div className="w-full bg-slate-700 rounded-full h-2">
                  <div 
                    className="bg-sky-500 h-2 rounded-full"
                    style={{ width: `${(item.experience && item.xpForNextLevel) ? (item.experience / item.xpForNextLevel) * 100 : 0}%`}}
                  ></div>
                </div>
              </div>
            )}
            
            {/* Show repair button only if durability is explicitly 0 */}
            {item.durability === 0 && (
                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        onRepair(item);
                    }}
                    className="w-full mt-2 px-4 py-2 bg-red-600 text-white font-semibold rounded-md hover:bg-red-700 transition-colors"
                >
                    Repair Rod
                </button>
            )}
          </div>
        )}

        {item.description && item.category !== 'FISHING_ROD' && (
          <SafeText 
            text={item.description}
            tag="p"
            className="text-slate-300 text-sm mb-3 line-clamp-2"
            maxLength={200}
            showTooltip={true}
          />
        )}
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