import React from 'react';
import { motion } from 'framer-motion';
import { GiFishingPole, GiSewingString, GiFishingHook, GiGearStick } from 'react-icons/gi';
import { PiFilmReel, PiHandPalm } from 'react-icons/pi';
import NameplatePreview from '@/components/NameplatePreview';
import { getRarityColor, getRarityLabel } from '@/utils/rarityHelpers';
import { CaseItemDTO, AnimationState } from '@/components/ui/shop/CaseTypes';

interface CaseItemThumbnailProps {
  item: CaseItemDTO;
  user?: any;
  animationState: AnimationState;
}

export const CaseItemThumbnail = React.memo(({ item, user, animationState }: CaseItemThumbnailProps) => {
  const containedItem = item.containedItem;
  const rarityColor = getRarityColor(containedItem.rarity);
  const isRevealing = animationState === 'revealing';

  const partIcons: Record<string, React.ElementType> = {
    ROD_SHAFT: GiFishingPole,
    REEL: PiFilmReel,
    FISHING_LINE: GiSewingString,
    HOOK: GiFishingHook,
    GRIP: PiHandPalm,
  };

  return (
    <motion.div
      className="flex-shrink-0 w-24 h-24 relative"
      style={{
        filter: isRevealing ? 'brightness(0.8)' : 'none',
        minWidth: '96px',
      }}
      transition={{ duration: 0.3 }}
    >
      <div 
        className="w-full h-full rounded-lg border-2 overflow-hidden relative bg-slate-800"
        style={{ borderColor: rarityColor }}
      >
        {containedItem.category === 'USER_COLOR' ? (
          <NameplatePreview
            username={user?.username || "User"}
            avatar={user?.avatar || "/images/default-avatar.png"}
            color={containedItem.imageUrl}
            endColor={containedItem.gradientEndColor}
            fallbackColor={rarityColor}
            message=""
            className="h-full w-full"
            size="sm"
          />
        ) : containedItem.category === 'BADGE' ? (
          <img 
            src={containedItem.thumbnailUrl || containedItem.imageUrl} 
            alt={containedItem.name}
            className="h-full w-full object-cover rounded-full"
            style={{ padding: '8px' }}
          />
        ) : containedItem.category === 'FISHING_ROD_PART' ? (() => {
          const PartIcon = containedItem.fishingRodPartType ? partIcons[containedItem.fishingRodPartType] || GiGearStick : GiGearStick;
          return (
            <div 
              className="h-full w-full flex flex-col items-center justify-center relative overflow-hidden p-2"
              style={{ background: `linear-gradient(to bottom right, #1f2937, ${rarityColor})` }}
            >
              <PartIcon className="absolute w-12 h-12 text-white/10 transform -rotate-12 -right-2 -bottom-2" />
              <PartIcon className="relative z-10 w-12 h-12 text-white/80" />
              <div className="relative z-10 mt-1 text-center">
              </div>
            </div>
          );
        })() : containedItem.category === 'FISHING_ROD' ? (
          <div 
            className="h-full w-full flex flex-col items-center justify-center relative overflow-hidden p-2"
            style={{ background: `linear-gradient(to bottom right, #1f2937, ${rarityColor})` }}
          >
            <GiFishingPole className="absolute w-12 h-12 text-white/10 transform -rotate-12 -right-2 -bottom-2" />
            <GiFishingPole className="relative z-10 w-12 h-12 text-white/80" />
            <div className="relative z-10 mt-1 text-center">
            </div>
          </div>
        ) : containedItem.imageUrl ? (
          <img 
            src={containedItem.thumbnailUrl || containedItem.imageUrl} 
            alt={containedItem.name}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="h-full w-full bg-slate-700 flex items-center justify-center">
            <span className="text-xs text-slate-400">No Image</span>
          </div>
        )}
        
      </div>
      
      <div 
        className="absolute -bottom-1 left-1/2 transform -translate-x-1/2 px-1 py-0.5 rounded text-xs font-bold"
        style={{
          backgroundColor: rarityColor,
          color: 'white',
          fontSize: '10px'
        }}
      >
        {getRarityLabel(containedItem.rarity).charAt(0)}
      </div>
    </motion.div>
  );
});

CaseItemThumbnail.displayName = 'CaseItemThumbnail'; 