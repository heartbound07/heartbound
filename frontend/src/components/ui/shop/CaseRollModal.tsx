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
} from './CaseTypes';

const ITEM_WIDTH = 112;

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
  const x = useMotionValue(0);
  const animationContainerRef = useRef<HTMLDivElement>(null);

  const animationItems = useMemo(() => {
    if (!caseContents?.items) return [];
    return Array(8).fill(caseContents.items).flat();
  }, [caseContents]);

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
    (rollValue: number, currentCaseContents: CaseContents) => {
        if (!animationItems.length || !currentCaseContents?.items) {
            return 0;
        }

    let cumulativeWeight = 0;
    let wonItemFromRoll = null;
    
        for (const caseItem of currentCaseContents.items) {
      const itemWeight = Math.floor(caseItem.dropRate * 10000);
      cumulativeWeight += itemWeight;
      if (rollValue < cumulativeWeight) {
        wonItemFromRoll = caseItem.containedItem;
        break;
      } 
    }
    
    if (!wonItemFromRoll) {
            wonItemFromRoll = currentCaseContents.items[currentCaseContents.items.length - 1].containedItem;
    }
    
    const uniqueItemsCount = animationItems.length / 8;
    const winningIndex = animationItems.findIndex(item => item.containedItem.id === wonItemFromRoll.id);
    
    let targetIndex;
    if (winningIndex !== -1) {
            const targetRepetition = 6;
      const indexInRepetition = winningIndex % uniqueItemsCount;
      targetIndex = targetRepetition * uniqueItemsCount + indexInRepetition;
      if (targetIndex >= animationItems.length) {
        targetIndex = 5 * uniqueItemsCount + indexInRepetition;
      }
    } else {
      targetIndex = Math.floor(animationItems.length * 0.75);
    }
    
        const container = animationContainerRef.current;
        if (!container) {
          // Fallback to the previous behavior if ref is not ready, though it shouldn't happen.
          return -(targetIndex * ITEM_WIDTH - (document.body.clientWidth / 2) + (ITEM_WIDTH / 2));
        }

        const containerWidth = container.offsetWidth;
        return -(targetIndex * ITEM_WIDTH - (containerWidth / 2) + (ITEM_WIDTH / 2));
    },
    [animationItems]
  );

  const handleOpenCase = async () => {
    if (animationState !== 'idle' || !caseContents?.items) return;

    setAnimationState('loading');
    setError(null);
    x.set(0);

    try {
      await new Promise(resolve => setTimeout(resolve, 500));
      
      setAnimationState('rolling');
      
      const apiPromise = httpClient.post(`/shop/cases/${caseId}/open`);

      // Start a long, continuous roll that will be interrupted later.
      animate(x, -animationItems.length * ITEM_WIDTH, {
        duration: 10, // A long background animation
        ease: 'linear',
      });

      const apiResponse = await apiPromise;
      const resultData: RollResult = apiResponse.data;
      setRollResult(resultData);

      setAnimationState('decelerating');

      const finalX = generateAnimationSequenceFromRoll(
        resultData.rollValue,
        caseContents
      );
      
      // This new animation will smoothly take over from the current one.
      await animate(x, finalX, {
        duration: 7, // Suspenseful deceleration
        ease: [0.22, 1, 0.36, 1], // Custom ease-out curve
      });

      setAnimationState('revealing');
      await new Promise(resolve => setTimeout(resolve, 1200));
      setAnimationState('reward');
    } catch (error: any) {
      setError(error.response?.data?.message || 'Failed to open case');
      setAnimationState('idle');
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
                  : 'absolute left-1/2 transform -translate-x-1/2'
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
                <h2 className="text-xl font-bold text-white whitespace-nowrap">
                  {animationState === 'idle' && `Open ${caseName}`}
                  {animationState === 'loading' && 'Preparing Case...'}
                  {(animationState === 'rolling' ||
                    animationState === 'decelerating' ||
                    animationState === 'revealing') &&
                    ' '}
                </h2>
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