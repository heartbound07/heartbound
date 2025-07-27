import React from 'react';
import { motion } from 'framer-motion';
import { GiFishingPole } from 'react-icons/gi';
import NameplatePreview from '@/components/NameplatePreview';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import { formatDisplayText } from '@/utils/formatters';
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
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {caseContents.items
              .sort((a, b) => b.dropRate - a.dropRate)
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
                            message="Preview of your nameplate color"
                            size="md"
                          />
                        </div>
                        {item.description && (
                          <p className="text-xs text-slate-400 mt-2 line-clamp-2">{item.description}</p>
                        )}
                      </div>
                    ) : item.category === 'FISHING_ROD' ? (
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
                        <div 
                          className="h-24 w-full flex flex-col items-center justify-center relative overflow-hidden rounded-lg p-2"
                          style={{ background: `linear-gradient(to bottom right, #1f2937, ${rarityColor})` }}
                        >
                          <GiFishingPole className="absolute w-20 h-20 text-white/10 transform -rotate-12 -right-4 -bottom-4" />
                          <GiFishingPole className="relative z-10 w-12 h-12 text-white/80" />
                          <div className="relative z-10 mt-1 text-center">
                            <p className="text-xl font-bold text-white">{item.name}</p>
                          </div>
                        </div>
                        {item.description && (
                          <p className="text-xs text-slate-400 mt-2 line-clamp-2">{item.description}</p>
                        )}
                      </div>
                    ) : (
                      <div className="flex items-start space-x-3">
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
                            <span>{item.price ?? 0} credits</span>
                          </div>
                          {item.description && (
                            <p className="text-xs text-slate-400 mt-2 line-clamp-2">{item.description}</p>
                          )}
                        </div>
                      </div>
                    )}
                  </motion.div>
                );
              })}
          </div>
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