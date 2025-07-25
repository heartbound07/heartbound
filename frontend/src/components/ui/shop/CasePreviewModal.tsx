import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { FaTimes, FaDice } from 'react-icons/fa';
import { GiFishingPole } from 'react-icons/gi';
import httpClient from '@/lib/api/httpClient';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle, RARITY_ORDER } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import { formatDisplayText } from '@/utils/formatters';

interface CaseItem {
  id: string;
  containedItem: {
    id: string;
    name: string;
    description: string;
    price: number;
    category: string;
    imageUrl: string;
    thumbnailUrl?: string;
    rarity: string;
    fishingRodMultiplier?: number;
    gradientEndColor?: string;
  };
  dropRate: number;
}

interface CaseContents {
  caseId: string;
  caseName: string;
  items: CaseItem[];
  totalDropRate: number;
  itemCount: number;
}

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

  useEffect(() => {
    if (isOpen && caseId) {
      fetchCaseContents();
    }
  }, [isOpen, caseId]);

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
          {/* Header */}
          <div className="flex items-center justify-between p-6 border-b border-slate-700/50">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-primary/20 rounded-lg">
                <FaDice className="text-primary" size={20} />
              </div>
              <div>
                <h2 className="text-xl font-bold text-white">{caseName}</h2>
                <p className="text-slate-400 text-sm">Case Contents & Drop Rates</p>
              </div>
            </div>
            
            <button
              onClick={onClose}
              className="p-2 hover:bg-slate-700/50 rounded-lg transition-colors text-slate-400 hover:text-white"
            >
              <FaTimes size={20} />
            </button>
          </div>

          {/* Content */}
          <div className="p-6 overflow-y-auto max-h-[calc(90vh-140px)]">
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


                {/* Items Grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {caseContents.items
                    .sort((a, b) => {
                      const aIndex = RARITY_ORDER.indexOf(a.containedItem.rarity);
                      const bIndex = RARITY_ORDER.indexOf(b.containedItem.rarity);
                      if (aIndex !== bIndex) {
                        return aIndex - bIndex;
                      }
                      return b.dropRate - a.dropRate;
                    })
                    .map((caseItem) => {
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
                          {item.category === 'USER_COLOR' ? (
                            // USER_COLOR - Full NameplatePreview layout (matches CaseRollModal.tsx exactly)
                            <div className="space-y-3">
                              {/* Header with name and rarity */}
                              <div className="flex items-center justify-center space-x-2 mb-2">
                                <h3 className="font-medium text-white text-sm">{item.name}</h3>
                                <span 
                                  className="px-2 py-0.5 rounded text-xs font-semibold"
                                  style={getRarityBadgeStyle(item.rarity)}
                                >
                                  {getRarityLabel(item.rarity)}
                                </span>
                              </div>
                              
                              {/* Full NameplatePreview */}
                              <div className="flex justify-center py-2">
                                <NameplatePreview
                                  username={user?.username || "Preview"}
                                  avatar={user?.avatar || "/images/default-avatar.png"}
                                  color={item.imageUrl}
                                  endColor={item.gradientEndColor}
                                  fallbackColor={rarityColor}
                                  message="Preview of your nameplate color"
                                  size="md"
                                />
                              </div>

                              {item.description && (
                                <p className="text-xs text-slate-400 mt-2 line-clamp-2">{item.description}</p>
                              )}
                            </div>
                          ) : item.category === 'FISHING_ROD' ? (
                            // FISHING_ROD - Full preview layout
                            <div className="space-y-3">
                              {/* Header with name and rarity */}
                              <div className="flex items-center justify-center space-x-2 mb-2">
                                <h3 className="font-medium text-white text-sm">{item.name}</h3>
                                <span 
                                  className="px-2 py-0.5 rounded text-xs font-semibold"
                                  style={getRarityBadgeStyle(item.rarity)}
                                >
                                  {getRarityLabel(item.rarity)}
                                </span>
                              </div>
                              
                              {/* Fishing Rod Visual Preview */}
                              <div 
                                className="h-24 w-full flex flex-col items-center justify-center relative overflow-hidden rounded-lg p-2"
                                style={{ background: `linear-gradient(to bottom right, #1f2937, ${rarityColor})` }}
                              >
                                <GiFishingPole className="absolute w-20 h-20 text-white/10 transform -rotate-12 -right-4 -bottom-4" />
                                <GiFishingPole className="relative z-10 w-12 h-12 text-white/80 drop-shadow-lg" />
                                <div className="relative z-10 mt-1 text-center">
                                  <p className="text-xl font-bold text-white drop-shadow-md">{item.fishingRodMultiplier}x</p>
                                  <p className="text-xs font-semibold text-cyan-200 drop-shadow-sm">Credit Bonus</p>
                                </div>
                              </div>

                              {item.description && (
                                <p className="text-xs text-slate-400 mt-2 line-clamp-2">{item.description}</p>
                              )}
                            </div>
                          ) : (
                            // Other items - Horizontal layout (matches CaseRollModal.tsx exactly)
                            <div className="flex items-start space-x-3">
                              {/* Item Preview */}
                              <div className="flex-shrink-0 w-16 h-16 rounded-lg overflow-hidden border-2" style={{ borderColor: rarityColor }}>
                                {item.category === 'BADGE' ? (
                                  <img 
                                    src={item.thumbnailUrl || item.imageUrl} 
                                    alt={item.name}
                                    className="h-full w-full object-cover rounded-full"
                                    style={{ padding: '8px' }}
                                  />
                                ) : item.imageUrl ? (
                                  <img 
                                    src={item.imageUrl} 
                                    alt={item.name}
                                    className="h-full w-full object-cover"
                                  />
                                ) : (
                                  <div className="h-full w-full bg-slate-700 flex items-center justify-center">
                                    <span className="text-xs text-slate-400">No Image</span>
                                  </div>
                                )}
                              </div>

                              {/* Item Details */}
                              <div className="flex-1 min-w-0">
                                <div className="flex items-start justify-between mb-1">
                                  <h3 className="font-medium text-white text-sm truncate pr-2">{item.name}</h3>
                                  <div className="flex-shrink-0">
                                    <span 
                                      className="px-2 py-0.5 rounded text-xs font-semibold"
                                      style={getRarityBadgeStyle(item.rarity)}
                                    >
                                      {getRarityLabel(item.rarity)}
                                    </span>
                                  </div>
                                </div>
                                
                                <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
                                  <span>{formatDisplayText(item.category)}</span>
                                  <span>{item.price} credits</span>
                                </div>

                                {item.description && (
                                  <p className="text-xs text-slate-400 mt-2 line-clamp-2">{item.description}</p>
                                )}
                              </div>
                            </div>
                          )}
                        </motion.div>
                      );
                    })
                  }
                </div>

                {/* Footer Note */}
                <div className="mt-6 p-3 bg-slate-800/30 border border-slate-700/30 rounded-lg">
                  <p className="text-xs text-slate-400 text-center">
                    💡 <strong>Note:</strong> Cases are purchased and stored in your inventory. 
                    Future updates will allow you to open cases and receive random items based on these drop rates.
                  </p>
                </div>
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