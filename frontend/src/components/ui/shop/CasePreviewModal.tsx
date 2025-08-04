import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { FaTimes } from 'react-icons/fa';
import { GiFishingPole, GiSewingString, GiFishingHook, GiGearStick } from 'react-icons/gi';
import { PiFilmReel, PiHandPalm } from 'react-icons/pi';
import httpClient from '@/lib/api/httpClient';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle, RARITY_ORDER } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import { CaseItemDTO, CaseContents } from './CaseTypes';

interface CasePreviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  caseId: string;
  caseName: string;
  user?: any;
}

export function CasePreviewModal({ isOpen, onClose, caseId, caseName, user }: CasePreviewModalProps) {
  const [loading, setLoading] = useState(false);
  const [caseContents, setCaseContents] = useState<CaseContents | null>(null);
  const [error, setError] = useState<string | null>(null);

  const partIcons: Record<string, React.ElementType> = {
    ROD_SHAFT: GiFishingPole,
    REEL: PiFilmReel,
    FISHING_LINE: GiSewingString,
    HOOK: GiFishingHook,
    GRIP: PiHandPalm,
  };

  useEffect(() => {
    if (isOpen && caseId) {
      fetchCaseContents();
    }
  }, [isOpen, caseId]);

  const sortCaseItems = (items: CaseItemDTO[]) => {
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

  const nameplateItems = caseContents
    ? sortCaseItems(
        caseContents.items.filter(
          (caseItem) => caseItem.containedItem.category === 'USER_COLOR'
        )
      )
    : [];

  const badgeItems = caseContents
    ? sortCaseItems(
        caseContents.items.filter(
          (caseItem) => caseItem.containedItem.category === 'BADGE'
        )
      )
    : [];

  const otherItems = caseContents
    ? sortCaseItems(
        caseContents.items.filter(
          (caseItem) =>
            caseItem.containedItem.category !== 'FISHING_ROD' &&
            caseItem.containedItem.category !== 'FISHING_ROD_PART' &&
            caseItem.containedItem.category !== 'USER_COLOR' &&
            caseItem.containedItem.category !== 'BADGE'
        )
      )
    : [];

  const fetchCaseContents = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await httpClient.get(`/shop/cases/${caseId}/contents`);
      setCaseContents(response.data);
    } catch (error) {
      console.error('Error fetching case contents:', error);
      setError('Failed to load case contents');
    } finally {
      setLoading(false);
    }
  };

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
        onClick={handleBackdropClick}
      >
        <motion.div
          initial={{ scale: 0.9, opacity: 0, y: 20 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          exit={{ scale: 0.9, opacity: 0, y: 20 }}
          transition={{ type: "spring", damping: 25, stiffness: 300 }}
          className="relative bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-2xl border border-slate-700/50 max-w-4xl w-full max-h-[90vh] overflow-hidden"
          onClick={(e) => e.stopPropagation()}
        >
          <button
            onClick={onClose}
            className="absolute top-4 right-4 p-2 hover:bg-slate-700/50 rounded-lg transition-colors text-slate-400 hover:text-white z-10"
          >
            <FaTimes size={20} />
          </button>

          {/* Content */}
          <div className="p-6 pt-12 overflow-y-auto max-h-[90vh]">
            {loading ? (
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
                  onClick={fetchCaseContents}
                  className="px-4 py-2 bg-primary hover:bg-primary/90 text-white rounded-lg transition-colors"
                >
                  Try Again
                </button>
              </div>
            ) : caseContents ? (
              <>
                <div className="mb-6 text-center">
                  <h2 className="text-2xl font-bold text-white tracking-tight sm:text-3xl font-grandstander">{caseName}</h2>
                  <p className="mt-2 text-base text-slate-400">Contains one of the following items:</p>
                </div>

                 {/* Other Items Grid */}
                {otherItems.length > 0 && (
                  <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
                     {otherItems.map((caseItem) => {
                        const item = caseItem.containedItem;
                        const rarityColor = getRarityColor(item.rarity);

                        const nameParts = item.name.split('|').map((s: string) => s.trim());
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
                              {item.imageUrl ? (
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
                )}

                 {/* Nameplates Section */}
                 {nameplateItems.length > 0 && (
                   <>
                    <div className="mt-8 mb-4">
                        <div className="relative">
                            <div className="absolute inset-0 flex items-center" aria-hidden="true">
                                <div className="w-full border-t border-slate-700" />
                            </div>
                            <div className="relative flex justify-center">
                                <span className="bg-slate-800 px-3 text-lg font-medium text-white rounded-md">
                                    Nameplates
                                </span>
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {nameplateItems.map((caseItem) => {
                        const item = caseItem.containedItem;
                        const rarityColor = getRarityColor(item.rarity);

                        return (
                            <motion.div
                                key={caseItem.id}
                                initial={{ opacity: 0, y: 10 }}
                                animate={{ opacity: 1, y: 0 }}
                                className="bg-slate-800/30 border border-slate-700/50 rounded-lg p-4 hover:bg-slate-800/50 transition-colors"
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
                      })}
                    </div>
                  </>
                )}

                 {/* Badges Section */}
                 {badgeItems.length > 0 && (
                   <>
                    <div className="mt-8 mb-4">
                        <div className="relative">
                            <div className="absolute inset-0 flex items-center" aria-hidden="true">
                                <div className="w-full border-t border-slate-700" />
                            </div>
                            <div className="relative flex justify-center">
                                <span className="bg-slate-800 px-3 text-lg font-medium text-white rounded-md">
                                    Badges
                                </span>
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
                      {badgeItems.map((caseItem) => {
                        const item = caseItem.containedItem;
                        const rarityColor = getRarityColor(item.rarity);

                        const nameParts = item.name.split('|').map((s: string) => s.trim());
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
                              <img 
                                src={item.thumbnailUrl || item.imageUrl} 
                                alt={item.name}
                                className="h-full w-full object-contain rounded-full p-2"
                              />
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
                  </>
                )}
 
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
              </>
            ) : (
              <div className="flex flex-col items-center justify-center py-12">
                <p className="text-slate-400">No case contents available</p>
              </div>
            )}
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
} 