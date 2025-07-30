import React from 'react';
import { motion } from 'framer-motion';
import { FaGift, FaCoins } from 'react-icons/fa';
import { Star } from 'lucide-react';
import { GiFishingPole, GiSewingString, GiFishingHook, GiGearStick } from 'react-icons/gi';
import { PiFilmReel, PiHandPalm } from 'react-icons/pi';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { getRarityColor } from '@/utils/rarityHelpers';
import { RollResult } from './CaseTypes';

interface CaseRewardScreenProps {
  rollResult: RollResult;
  user?: any;
  onClaimAndClose: () => void;
}

export const CaseRewardScreen = React.memo(({ rollResult, user, onClaimAndClose }: CaseRewardScreenProps) => {

  const partIcons: Record<string, React.ElementType> = {
    ROD_SHAFT: GiFishingPole,
    REEL: PiFilmReel,
    FISHING_LINE: GiSewingString,
    HOOK: GiFishingHook,
    GRIP: PiHandPalm,
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-6"
    >
      <div className="text-center">
        <motion.div 
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.2, duration: 0.6, ease: "easeOut" }}
          className="flex justify-center"
        >
          <div className="max-w-sm mx-auto">
            {rollResult.wonItem.category === 'USER_COLOR' ? (
              <NameplatePreview
                username={user?.username || "Username"}
                avatar={user?.avatar || "/images/default-avatar.png"}
                color={rollResult.wonItem.imageUrl}
                endColor={rollResult.wonItem.gradientEndColor}
                fallbackColor={getRarityColor(rollResult.wonItem.rarity)}
                message="Your new nameplate color"
                className=""
                size="md"
              />
            ) : rollResult.wonItem.category === 'BADGE' ? (
              <BadgePreview
                username={user?.username || "Username"}
                avatar={user?.avatar || "/images/default-avatar.png"}
                badgeUrl={rollResult.wonItem.thumbnailUrl || rollResult.wonItem.imageUrl}
                message="Your new badge"
                className=""
                size="md"
              />
            ) : rollResult.wonItem.category === 'FISHING_ROD_PART' ? (() => {
              const PartIcon = rollResult.wonItem.fishingRodPartType ? partIcons[rollResult.wonItem.fishingRodPartType] || GiGearStick : GiGearStick;
              return (
                <div 
                  className="h-32 w-full flex flex-col items-center justify-center relative overflow-hidden rounded-lg p-4"
                >
                  <PartIcon className="relative z-10 w-16 h-16 text-white/80" />
                  <div className="relative z-10 mt-2 text-center">
                    <p className="text-sm font-semibold text-white-200">{rollResult.wonItem.name}</p>
                  </div>
                </div>
              )
            })() : rollResult.wonItem.category === 'FISHING_ROD' ? (
              <div 
                className="h-32 w-full flex flex-col items-center justify-center relative overflow-hidden rounded-lg p-4"
              >
                <GiFishingPole className="absolute w-24 h-24 text-white/10 transform -rotate-12 -right-4 -bottom-4" />
                <GiFishingPole className="relative z-10 w-16 h-16 text-white/80" />
                <div className="relative z-10 mt-2 text-center">
                  <p className="text-sm font-semibold text-white-200">{rollResult.wonItem.name}</p>
                </div>
              </div>
            ) : rollResult.wonItem.imageUrl ? (
              <div className="w-32 h-32 rounded-lg overflow-hidden border-3 mx-auto" 
                   style={{ borderColor: getRarityColor(rollResult.wonItem.rarity) }}>
                <img 
                  src={rollResult.wonItem.imageUrl} 
                  alt={rollResult.wonItem.name}
                  className="h-full w-full object-cover"
                />
              </div>
            ) : (
              <div className="w-32 h-32 rounded-lg overflow-hidden border-3 mx-auto bg-slate-700 flex items-center justify-center" 
                   style={{ borderColor: getRarityColor(rollResult.wonItem.rarity) }}>
                <span className="text-xs text-slate-400">No Image</span>
              </div>
            )}

            {rollResult.wonItem.description && (
              <div className="text-center mt-4">
                <p className="text-slate-400 text-xs">
                  {rollResult.wonItem.description}
                </p>
              </div>
            )}
          </div>
        </motion.div>
      </div>

      {rollResult.alreadyOwned && rollResult.compensationAwarded && (
        <div className="space-y-3">
          <motion.div 
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.4 }}
            className="flex justify-center"
          >
            <span className="text-slate-300 text-sm font-medium">Duplicate!</span>
          </motion.div>

          <motion.div 
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.8 }}
            className="flex justify-center items-center space-x-4"
          >
            <div className="flex items-center space-x-2 px-3 py-2 bg-yellow-500/10 border border-yellow-500/20 rounded-lg">
              <FaCoins size={14} style={{ color: '#fbbf24' }} />
              <div>
                <p className="font-bold text-base" style={{ color: '#fbbf24' }}>+{rollResult.compensatedCredits}</p>
                <p className="text-slate-400 text-xs">Credits</p>
              </div>
            </div>
            
            <div className="flex items-center space-x-2 px-3 py-2 bg-blue-500/10 border border-blue-500/20 rounded-lg">
              <Star size={14} style={{ color: '#60a5fa' }} />
              <div>
                <p className="font-bold text-base" style={{ color: '#60a5fa' }}>+{rollResult.compensatedXp}</p>
                <p className="text-slate-400 text-xs">Experience</p>
              </div>
            </div>
          </motion.div>
        </div>
      )}

      {rollResult.alreadyOwned && !rollResult.compensationAwarded && (
        <motion.div 
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.6 }}
          className="p-4 bg-slate-800/30 border border-slate-700/50 rounded-lg"
        >
          <p className="text-blue-400 text-sm text-center">
            💡 <strong>Note:</strong> You already owned this item, so no duplicate was added to your inventory.
          </p>
        </motion.div>
      )}

      <motion.div 
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.8 }}
        className="flex justify-center"
      >
        <button
          onClick={onClaimAndClose}
          className="px-8 py-3 bg-green-600 hover:bg-green-500 text-white rounded-lg transition-colors flex items-center space-x-2"
        >
          <FaGift size={16} />
          <span>Claim & Continue</span>
        </button>
      </motion.div>
    </motion.div>
  );
});

CaseRewardScreen.displayName = 'CaseRewardScreen'; 