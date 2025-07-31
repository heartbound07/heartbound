import React from 'react';
import { motion } from 'framer-motion';
import { GiFishingPole, GiSewingString, GiFishingHook, GiGearStick } from 'react-icons/gi';
import { PiFilmReel, PiHandPalm } from 'react-icons/pi';
import NameplatePreview from '@/components/NameplatePreview';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle, RARITY_ORDER } from '@/utils/rarityHelpers';
import { CaseContents } from './CaseTypes';

interface CaseIdleScreenProps {
  caseContents: CaseContents | null;
  error: string | null;
  user?: any;
  onOpenCase: () => void;
  onFetchCaseContents: () => void;
  onClose: () => void;
}

export const CaseIdleScreen = React.memo(({
  caseContents,
  error,
  user,
  onOpenCase,
  onFetchCaseContents,
  onClose,
}: CaseIdleScreenProps) => {

  const partIcons: Record<string, React.ElementType> = {
    ROD_SHAFT: GiFishingPole,
    REEL: PiFilmReel,
    FISHING_LINE: GiSewingString,
    HOOK: GiFishingHook,
    GRIP: PiHandPalm,
  };

  const sortCaseItems = (items: CaseContents['items']) => {
    return items.sort((a, b) => {
      const aIndex = RARITY_ORDER.indexOf(a.containedItem.rarity);
      const bIndex = RARITY_ORDER.indexOf(b.containedItem.rarity);
      if (aIndex !== bIndex) {
        return aIndex - bIndex;
      }
      return b.dropRate - a.dropRate;
    });
  };

  const fishingRelatedItems = caseContents
    ? sortCaseItems(
        caseContents.items.filter(
          (caseItem) =>
            caseItem.containedItem.category === 'FISHING_ROD' ||
            caseItem.containedItem.category === 'FISHING_ROD_PART'
        )
      )
    : [];

  const otherItems = caseContents
    ? sortCaseItems(
        caseContents.items.filter(
          (caseItem) =>
            caseItem.containedItem.category !== 'FISHING_ROD' &&
            caseItem.containedItem.category !== 'FISHING_ROD_PART'
        )
      )
    : [];

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-6 max-h-[calc(90vh-220px)] overflow-y-auto"
    >
      {!caseContents ? (
        <div className="flex flex-col items-center justify-center py-12">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin mb-4"></div>
          <p className="text-slate-300">Loading case contents...</p>
        </div>
      ) : error ? (
        <div className="flex flex-col items-center justify-center py-12">
          <div className="p-4 bg-red-500/10 border border-red-500/30 rounded-lg mb-4">
            <p className="text-red-400 text-center">{error}</p>
          </div>
          <button
            onClick={onFetchCaseContents}
            className="px-4 py-2 bg-primary hover:bg-primary/90 text-white rounded-lg transition-colors"
          >
            Try Again
          </button>
        </div>
      ) : (
        <>
          {/* Items Grid */}
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {otherItems.map((caseItem) => {
              const item = caseItem.containedItem;
              const rarityColor = getRarityColor(item.rarity);

              if (item.category === 'USER_COLOR') {
                return (
                  <motion.div
                    key={caseItem.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="bg-slate-800/30 border border-slate-700/50 rounded-lg p-4 hover:bg-slate-800/50 transition-colors md:col-span-2 lg:col-span-2"
                    style={{
                      borderLeftColor: rarityColor,
                      borderLeftWidth: '4px'
                    }}
                  >
                    <div className="space-y-3">
                      <div className="flex items-center justify-center space-x-2 mb-2">
                        <h3 className="font-medium text-white text-sm">{item.name}</h3>
                        <span
                          className="px-2 py-0.5 rounded text-xs font-semibold"
                          style={getRarityBadgeStyle(item.rarity)}
                        >
                          {getRarityLabel(item.rarity)}
                        </span>
                      </div>
                      <div className="flex justify-center py-2">
                        <NameplatePreview
                          username={user?.username || "Preview"}
                          avatar={user?.avatar || "/images/default-avatar.png"}
                          color={item.imageUrl}
                          endColor={item.gradientEndColor}
                          fallbackColor={rarityColor}
                          message=""
                          size="md"
                        />
                      </div>
                      {item.description && (
                        <p className="text-xs text-slate-400 mt-2 line-clamp-2">{item.description}</p>
                      )}
                    </div>
                  </motion.div>
                );
              }

              const nameParts = item.name.split('|').map(s => s.trim());
              const nameLine1 = nameParts[0];
              const nameLine2 = nameParts.length > 1 ? nameParts[1] : null;

              return (
                <motion.div
                  key={caseItem.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="group flex flex-col overflow-hidden rounded-lg bg-slate-800/40 border border-slate-700/50 transition-all duration-300 hover:bg-slate-800/60 hover:shadow-xl hover:shadow-black/20"
                >
                  <div className="aspect-square w-full bg-slate-900/20 flex items-center justify-center p-4">
                    {item.category === 'BADGE' ? (
                      <img
                        src={item.thumbnailUrl || item.imageUrl}
                        alt={item.name}
                        className="h-full w-full object-contain rounded-full p-2"
                      />
                    ) : item.imageUrl ? (
                      <img
                        src={item.imageUrl}
                        alt={item.name}
                        className="max-h-full max-w-full object-contain transition-transform duration-300 group-hover:scale-105"
                      />
                    ) : (
                      <div className="flex items-center justify-center text-xs text-slate-400">
                        No Image
                      </div>
                    )}
                  </div>
                  <div className="p-2 text-left">
                    <p className="truncate text-sm font-semibold text-white">{nameLine1}</p>
                    {nameLine2 && <p className="truncate text-xs text-slate-400">{nameLine2}</p>}
                  </div>
                  <div className="h-1 w-full" style={{ backgroundColor: rarityColor }}></div>
                </motion.div>
              );
            })}
          </div>

          {fishingRelatedItems.length > 0 && (
            <>
              <div className="mt-8 mb-4">
                <div className="relative">
                  <div className="absolute inset-0 flex items-center" aria-hidden="true">
                    <div className="w-full border-t border-slate-700" />
                  </div>
                  <div className="relative flex justify-center">
                    <span className="bg-slate-800 px-3 text-lg font-medium text-white rounded-md">
                      Fishing Gear
                    </span>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {fishingRelatedItems.map((caseItem) => {
                  const item = caseItem.containedItem;
                  const rarityColor = getRarityColor(item.rarity);

                  return (
                    <motion.div
                      key={caseItem.id}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      className="bg-slate-800/30 border border-slate-700/50 rounded-lg p-3 hover:bg-slate-800/50 transition-colors flex flex-col"
                      style={{
                        borderTopColor: rarityColor,
                        borderTopWidth: '4px'
                      }}
                    >
                      <div className="h-20 w-full flex flex-col items-center justify-center relative overflow-hidden rounded-lg mb-2">
                        {item.category === 'FISHING_ROD_PART' ?
                          (() => {
                            const PartIcon = item.fishingRodPartType ? partIcons[item.fishingRodPartType] || GiGearStick : GiGearStick;
                            return <PartIcon className="w-10 h-10 text-white/80 drop-shadow-lg" />;
                          })() :
                          (<>
                            <GiFishingPole className="w-10 h-10 text-white/80 drop-shadow-lg" />
                            {item.fishingRodMultiplier && (
                              <div className="mt-1 text-center">
                                <p className="text-sm font-bold text-white drop-shadow-md">{item.fishingRodMultiplier}x</p>
                              </div>
                            )}
                          </>)
                        }
                      </div>
                      <div className="flex-grow flex flex-col justify-between">
                        <div>
                          <h3 className="font-medium text-white text-xs truncate" title={item.name}>{item.name}</h3>
                          {item.description && <p className="text-xs text-slate-400 mt-1 line-clamp-2">{item.description}</p>}
                        </div>
                        <div className="flex items-center justify-between mt-2">
                          <span
                            style={getRarityBadgeStyle(item.rarity)}
                            className="px-1.5 py-0.5 rounded text-xs font-semibold"
                          >
                            {getRarityLabel(item.rarity)}
                          </span>
                        </div>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            </>
          )}
          
          <div className="flex space-x-3 justify-center pt-4 border-t border-slate-700/50">
            <button
              onClick={onClose}
              className="px-6 py-3 bg-slate-700 hover:bg-slate-600 text-white rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={onOpenCase}
              disabled={!caseContents}
              className="px-6 py-3 bg-primary hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-lg transition-colors flex items-center space-x-2"
            >
              <span>Open Case</span>
            </button>
          </div>
        </>
      )}
    </motion.div>
  );
});
CaseIdleScreen.displayName = 'CaseIdleScreen'; 