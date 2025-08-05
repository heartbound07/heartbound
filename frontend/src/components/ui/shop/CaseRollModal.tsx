import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { motion, AnimatePresence, useMotionValue, animate } from 'framer-motion';
import { FaTimes, FaGift } from 'react-icons/fa';
import httpClient from '@/lib/api/httpClient';
import { getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import { formatDisplayText } from '@/utils/formatters';
import { CaseIdleScreen } from './CaseIdleScreen';
import { CaseRollAnimation } from './CaseRollAnimation';
import { CaseRewardScreen } from './CaseRewardScreen';
import {
  AnimationState,
  RollResult,
  CaseContents,
  CaseItemDTO,
} from './CaseTypes';

const ITEM_WIDTH = 112; // Fallback width

interface CaseRollModalProps {
  isOpen: boolean;
  onClose: () => void;
  caseId: string;
  caseName: string;
  onRollComplete: (result: RollResult) => void;
  user?: any;
}

export function CaseRollModal({ 
  isOpen, 
  onClose, 
  caseId, 
  caseName, 
  onRollComplete,
  user,
}: CaseRollModalProps) {
  const [animationState, setAnimationState] =
    useState<AnimationState>('idle');
  const [rollResult, setRollResult] = useState<RollResult | null>(null);
  const [caseContents, setCaseContents] = useState<CaseContents | null>(
    null
  );
  const [error, setError] = useState<string | null>(null);
  const [measuredItemWidth, setMeasuredItemWidth] = useState<number>(ITEM_WIDTH);
  const [showFullRarityItems, setShowFullRarityItems] = useState(false);
  const x = useMotionValue(0);
  const animationContainerRef = useRef<HTMLDivElement>(null);

  // Stable helper functions
  const buildCommonOnlyReel = useCallback((caseItems: CaseItemDTO[]): CaseItemDTO[] => {
    const commonItems = caseItems.filter(item => item.containedItem.rarity === 'COMMON');
    
    // If no common items exist, fall back to original behavior
    if (commonItems.length === 0) {
      return Array(8).fill(caseItems).flat();
    }

    // Create a reel with ~80 items from commons pool
    const reelSize = 80;
    const commonReel: CaseItemDTO[] = [];
    
    for (let i = 0; i < reelSize; i++) {
      const randomCommon = commonItems[Math.floor(Math.random() * commonItems.length)];
      commonReel.push(randomCommon);
    }
    
    return commonReel;
  }, []);

  const buildFinalRevealReel = useCallback((
    caseItems: CaseItemDTO[], 
    wonItem: RollResult['wonItem']
  ): CaseItemDTO[] => {
    // Get COMMON items for the majority of the reel
    const commonItems = caseItems.filter(item => item.containedItem.rarity === 'COMMON');
    
    // Fallback if no common items exist
    const itemsToUse = commonItems.length > 0 ? commonItems : caseItems;
    
    // Create initial COMMON items before the won item
    const preWonItems: CaseItemDTO[] = [];
    for (let i = 0; i < 90; i++) {
      const randomItem = itemsToUse[Math.floor(Math.random() * itemsToUse.length)];
      preWonItems.push(randomItem);
    }
    
    // Find or create the won item as CaseItemDTO with its TRUE rarity
    let wonItemAsCase = caseItems.find(item => item.containedItem.id === wonItem.id);
    
    if (!wonItemAsCase) {
      // If for some reason the won item isn't in the case contents, create it
      wonItemAsCase = {
        id: `won-item-${wonItem.id}`,
        caseId: caseId,
        containedItem: wonItem,
        dropRate: 0 // This doesn't matter for display
      };
    }
    
    // Create additional COMMON items after the won item to fill the right side
    const postWonItems: CaseItemDTO[] = [];
    for (let i = 0; i < 20; i++) {
      const randomItem = itemsToUse[Math.floor(Math.random() * itemsToUse.length)];
      postWonItems.push(randomItem);
    }
    
    // Structure: [90 COMMON items] + [1 WON item] + [20 COMMON items] = 111 total
    return [...preWonItems, wonItemAsCase, ...postWonItems];
  }, [caseId]);

  // Dynamic animation items based on state and available data
  const animationItems = useMemo(() => {
    if (!caseContents?.items) return [];
    
    // Phase 1: Rolling - show only COMMON items for suspense
    if (animationState === 'rolling') {
      return buildCommonOnlyReel(caseContents.items);
    }
    
    // Phase 2: Final reveal - only when explicitly enabled and we have roll result
    if (showFullRarityItems && rollResult) {
      return buildFinalRevealReel(caseContents.items, rollResult.wonItem);
    }
    
    // Continue showing COMMON items during early deceleration for smooth transition
    if (animationState === 'decelerating' && !showFullRarityItems) {
      return buildCommonOnlyReel(caseContents.items);
    }
    
    // Fallback to original behavior for other states
    return Array(8).fill(caseContents.items).flat();
  }, [caseContents, animationState, rollResult, showFullRarityItems, buildCommonOnlyReel, buildFinalRevealReel]);

  // Dynamically measure item width for better cross-device compatibility
  useEffect(() => {
    const measureItemWidth = () => {
      const container = animationContainerRef.current;
      if (!container) return;
      
      // Look for the first item element to measure its actual width
      const firstItem = container.querySelector('[data-case-item]');
      if (firstItem) {
        const measuredWidth = firstItem.getBoundingClientRect().width;
        if (measuredWidth > 0) {
          setMeasuredItemWidth(measuredWidth);
        }
      }
    };

    // Measure after animation starts and DOM is ready
    if (animationState === 'rolling' || animationState === 'decelerating') {
      const timer = setTimeout(measureItemWidth, 100);
      return () => clearTimeout(timer);
    }
  }, [animationState]);

  const fetchCaseContents = useCallback(async () => {
    try {
      const response = await httpClient.get(
        `/shop/cases/${caseId}/contents`
      );
      setCaseContents(response.data);
    } catch (error: any) {
      setError('Failed to load case contents');
    }
  }, [caseId]);

  useEffect(() => {
    if (isOpen && caseId) {
      fetchCaseContents();
    }
  }, [isOpen, caseId, fetchCaseContents]);

  const generateAnimationSequenceFromRoll = useCallback(
    (currentCaseContents: CaseContents) => {
        if (!currentCaseContents?.items) {
            return 0;
        }

        // For the new phased animation system, the won item is at position 90
        // (after 90 COMMON items), so we can calculate its position directly
        const targetIndex = 90; // Won item is always at position 90
        
        const container = animationContainerRef.current;
        if (!container) {
            // Fallback to document body width if ref is not ready
            return -(targetIndex * measuredItemWidth - (document.body.clientWidth / 2) + (measuredItemWidth / 2));
        }

        const containerWidth = container.offsetWidth;
        return -(targetIndex * measuredItemWidth - (containerWidth / 2) + (measuredItemWidth / 2));
    },
    [measuredItemWidth]
  );

  const handleOpenCase = async () => {
    if (animationState !== 'idle' || !caseContents?.items) return;

    setAnimationState('loading');
    setError(null);
    setShowFullRarityItems(false);
    x.set(0);

    try {
      await new Promise(resolve => setTimeout(resolve, 500));
      
      setAnimationState('rolling');
      
      const apiPromise = httpClient.post(`/shop/cases/${caseId}/open`);

      // Start a long, continuous roll with the COMMON-only items
      const commonOnlyReel = buildCommonOnlyReel(caseContents.items);
      animate(x, -commonOnlyReel.length * measuredItemWidth, {
        duration: 10, // A long background animation
        ease: 'linear',
      });

      const apiResponse = await apiPromise;
      const resultData: RollResult = apiResponse.data;
      setRollResult(resultData);

      setAnimationState('decelerating');

      // Continue with COMMON items for first part of deceleration (2 seconds)
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      // Now switch to full rarity items for the final reveal
      setShowFullRarityItems(true);
      
      // Small delay to ensure the animation items update is processed
      await new Promise(resolve => setTimeout(resolve, 150));

      const finalX = generateAnimationSequenceFromRoll(
        caseContents
      );
      
      // This new animation will smoothly take over from the current one.
      await animate(x, finalX, {
        duration: 5, // Remaining deceleration time
        ease: [0.22, 1, 0.36, 1], // Custom ease-out curve
      });

      setAnimationState('revealing');
      await new Promise(resolve => setTimeout(resolve, 1200));
      setAnimationState('reward');
    } catch (error: any) {
      setError(error.response?.data?.message || 'Failed to open case');
      setAnimationState('idle');
      setShowFullRarityItems(false);
    }
  };

  const handleClaimAndClose = () => {
    if (rollResult) {
      onRollComplete(rollResult);
    }
    handleClose();
  };

  const handleClose = () => {
    x.set(0);
    setAnimationState('idle');
    setRollResult(null);
    setError(null);
    setCaseContents(null);
    setShowFullRarityItems(false);
    setMeasuredItemWidth(ITEM_WIDTH); // Reset to fallback width
    onClose();
  };

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget && animationState === 'idle') {
      handleClose();
    }
  };

  const renderContent = () => {
    switch (animationState) {
      case 'idle':
        return (
          <CaseIdleScreen
            caseContents={caseContents}
            error={error}
            user={user}
            onOpenCase={handleOpenCase}
            onFetchCaseContents={fetchCaseContents}
            onClose={handleClose}
          />
        );
      case 'loading':
        return (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center space-y-6 py-8"
          >
            <motion.div
              animate={{ rotate: [0, 360], scale: [1, 1.2, 1] }}
              transition={{ duration: 0.8, repeat: Infinity, ease: 'easeInOut' }}
              className="flex justify-center"
            >
              <div className="p-4 bg-primary/20 rounded-full">
                <FaGift className="text-primary" size={32} />
              </div>
            </motion.div>
            <div>
              <h3 className="text-xl font-bold text-white mb-2">Preparing your case...</h3>
              <p className="text-slate-300">Get ready for the reveal!</p>
            </div>
          </motion.div>
        );
      case 'rolling':
      case 'decelerating':
      case 'revealing':
        return (
          <CaseRollAnimation
            ref={animationContainerRef}
            animationItems={animationItems}
            animationState={animationState}
            x={x}
            user={user}
          />
        );
      case 'reward':
        return rollResult ? (
          <CaseRewardScreen
            rollResult={rollResult}
            user={user}
            onClaimAndClose={handleClaimAndClose}
          />
        ) : null;
      default:
        return null;
    }
  };
  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4"
        onClick={handleBackdropClick}
      >
        <motion.div
          initial={{ scale: 0.9, opacity: 0, y: 20 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          exit={{ scale: 0.9, opacity: 0, y: 20 }}
          transition={{ type: 'spring', damping: 25, stiffness: 300 }}
          className="relative bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 rounded-xl shadow-2xl border border-slate-700/50 max-w-4xl w-full max-h-[90vh] overflow-hidden"
          onClick={e => e.stopPropagation()}
        >
          <div
            className={`relative flex p-6 border-b border-slate-700/50 ${
              animationState === 'reward' ? 'items-start' : 'items-center'
            }`}
          >
            <div className="flex items-center">
              {animationState !== 'reward' && animationState !== 'idle' && (
                <div className="p-2 bg-primary/20 rounded-lg">
                  <FaGift className="text-primary" size={20} />
                </div>
              )}
            </div>
            <div
              className={`${
                animationState === 'reward'
                  ? 'flex-1 flex justify-center py-2'
                  : 'absolute left-1/2 transform -translate-x-1/2 w-full px-100'
              }`}
            >
              {animationState === 'reward' && rollResult ? (
                <div className="text-center">
                  <div className="flex items-center justify-center space-x-2 mb-2">
                    <h2 className="text-xl font-bold text-white">
                      {rollResult.wonItem.name}
                    </h2>
                    <span 
                      className="px-2 py-1 rounded-full text-xs font-semibold"
                      style={getRarityBadgeStyle(rollResult.wonItem.rarity)}
                    >
                      {getRarityLabel(rollResult.wonItem.rarity)}
                    </span>
                  </div>
                  <p className="text-slate-300 text-sm">
                    {formatDisplayText(rollResult.wonItem.category)}
                  </p>
                </div>
              ) : (
                <div className="text-center">
                  <h2 className="text-2xl font-bold text-white tracking-tight sm:text-3xl font-grandstander">
                    {animationState === 'idle' && `Open ${caseName}`}
                    {animationState === 'loading' && 'Preparing Case...'}
                    {(animationState === 'rolling' ||
                      animationState === 'decelerating' ||
                      animationState === 'revealing') &&
                      ' '}
                  </h2>
                  {animationState === 'idle' && (
                    <p className="mt-2 text-base text-slate-400">Contains one of the following items:</p>
                  )}
                </div>
              )}
            </div>
            <div className="flex items-center space-x-2 ml-auto">
              {animationState === 'idle' && (
                <button
                  onClick={handleClose}
                  className="p-2 hover:bg-slate-700/50 rounded-lg transition-colors text-slate-400 hover:text-white"
                >
                  <FaTimes size={20} />
                </button>
              )}
            </div>
          </div>
          <div className="p-6">{renderContent()}</div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
} 