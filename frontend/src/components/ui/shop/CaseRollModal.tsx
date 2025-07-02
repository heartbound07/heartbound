import React, { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence, useMotionValue, useTransform, useSpring, animate } from 'framer-motion';
import { FaTimes, FaDice, FaGift, FaForward } from 'react-icons/fa';
import httpClient from '@/lib/api/httpClient';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { formatDisplayText } from '@/utils/formatters';

interface RollResult {
  caseId: string;
  caseName: string;
  wonItem: {
    id: string;
    name: string;
    description: string;
    price: number;
    category: string;
    imageUrl: string;
    thumbnailUrl?: string;
    rarity: string;
    owned: boolean;
  };
  rollValue: number;
  rolledAt: string;
  alreadyOwned: boolean;
}

interface CaseItemDTO {
  id: string;
  caseId: string;
  containedItem: {
    id: string;
    name: string;
    description: string;
    price: number;
    category: string;
    imageUrl: string;
    thumbnailUrl?: string;
    rarity: string;
    owned: boolean;
  };
  dropRate: number;
}

interface CaseContents {
  caseId: string;
  caseName: string;
  items: CaseItemDTO[];
  totalDropRate: number;
  itemCount: number;
}

interface CaseRollModalProps {
  isOpen: boolean;
  onClose: () => void;
  caseId: string;
  caseName: string;
  onRollComplete: (result: RollResult) => void;
  user?: any;
}

type AnimationState = 'idle' | 'loading' | 'rolling' | 'decelerating' | 'revealing' | 'reward';

// Audio hook interface for future implementation
interface AudioHooks {
  onInitiate?: () => void;
  onRollingTick?: () => void;
  onDecelerate?: () => void;
  onReveal?: () => void;
  onRarityReveal?: (rarity: string) => void;
}

export function CaseRollModal({ 
  isOpen, 
  onClose, 
  caseId, 
  caseName, 
  onRollComplete,
  user 
}: CaseRollModalProps) {
  const [animationState, setAnimationState] = useState<AnimationState>('idle');
  const [rollResult, setRollResult] = useState<RollResult | null>(null);
  const [caseContents, setCaseContents] = useState<CaseContents | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [canSkip, setCanSkip] = useState(false);
  const [animationItems, setAnimationItems] = useState<CaseItemDTO[]>([]);
  
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  
  // Use proper motion values following Framer Motion best practices
  const scrollProgress = useMotionValue(0);
  const smoothScrollProgress = useSpring(scrollProgress, {
    damping: 25,
    stiffness: 200,
    mass: 0.5
  });
  
  // Transform the smooth progress to actual pixel movement
  // Calculate total width dynamically: itemCount × 8 repetitions × 112px spacing
  // Use fallback of 3360 (30 items × 112px) if animationItems not loaded yet
  const totalAnimationWidth = animationItems.length > 0 ? animationItems.length * 112 : 3360;
  const x = useTransform(smoothScrollProgress, [0, 1], [0, -totalAnimationWidth]);
  
  // Audio hooks for future implementation
  const audioHooks: AudioHooks = {
    onInitiate: () => {
      // TODO: Implement audio feedback
    },
    onRollingTick: () => {
      // TODO: Implement rolling sound
    },
    onDecelerate: () => {
      // TODO: Implement deceleration sound
    },
    onReveal: () => {
      // TODO: Implement reveal sound
    }
  };

  // Fetch case contents when modal opens
  useEffect(() => {
    if (isOpen && caseId) {
      fetchCaseContents();
    }
  }, [isOpen, caseId]);

  const fetchCaseContents = async () => {
    try {
      const response = await httpClient.get(`/shop/cases/${caseId}/contents`);
      setCaseContents(response.data);
      
      // Create extended animation items list for seamless scrolling
      if (response.data?.items) {
        const items = response.data.items;
        // Repeat items multiple times for smooth infinite scroll effect
        const extendedItems = Array(8).fill(items).flat();
        setAnimationItems(extendedItems);
      }
    } catch (error: any) {
      setError('Failed to load case contents');
    }
  };

  const generateAnimationSequence = useCallback((winningItem: RollResult['wonItem']) => {
    if (!animationItems.length) return 0.85;
    
    // Calculate the final scroll progress to center the winning item
    // Item width: w-24 (96px) + mx-2 (8px each side = 16px) = 112px total spacing
    const itemWidth = 112;
    const containerWidth = scrollContainerRef.current?.offsetWidth || 800;
    const centerPosition = containerWidth / 2 - (itemWidth / 2);
    
    // Find winning item in the animation items array
    const uniqueItemsCount = animationItems.length / 8;
    const winningIndex = animationItems.findIndex(item => item.containedItem.id === winningItem.id);
    
    let targetIndex;
    if (winningIndex !== -1) {
      // Ensure we target an item in the later repetitions to continue leftward motion
      const repetitionOffset = Math.floor(uniqueItemsCount * 5.2); // Increased from 4.5 to 5.2
      const indexInRepetition = winningIndex % uniqueItemsCount;
      targetIndex = repetitionOffset + indexInRepetition;
    } else {
      // Fallback to a position further along to maintain leftward direction
      targetIndex = Math.floor(animationItems.length * 0.75); // Increased from 0.6 to 0.75
    }
    
    // Convert to scroll progress (0 to 1)
    const totalWidth = animationItems.length * itemWidth;
    const targetPosition = targetIndex * itemWidth - centerPosition;
    const calculatedProgress = targetPosition / totalWidth;
    
    // Only apply minimum constraint if calculated position would be too close to rolling end
    const rollingEndPosition = 0.7;
    const minSafeDistance = 0.05; // 5% buffer from rolling end
    const minProgress = rollingEndPosition + minSafeDistance; // 0.75
    const maxProgress = 0.95; // Don't go too far to avoid running out of items
    
    // Only enforce minimum if our calculation is too close to rolling end
    const finalProgress = calculatedProgress < minProgress 
      ? minProgress 
      : Math.min(calculatedProgress, maxProgress);
    
    return finalProgress;
  }, [animationItems]);

  const generateAnimationSequenceFromRoll = useCallback((rollValue: number, caseContents: CaseContents) => {
    if (!animationItems.length || !caseContents?.items) return 0.85;
    
    // Calculate the final scroll progress based on the rollValue and drop rates
    // Item width: w-24 (96px) + mx-2 (8px each side = 16px) = 112px total spacing
    const itemWidth = 112;
    const containerWidth = scrollContainerRef.current?.offsetWidth || 800;
    const centerPosition = containerWidth / 2 - (itemWidth / 2);
    
    // Define rolling constraints
    const rollingEndPosition = 0.7;
    
    // Find which item corresponds to the rollValue based on cumulative drop rates
    let cumulative = 0;
    let wonItemFromRoll = null;
    
    for (const caseItem of caseContents.items) {
      cumulative += caseItem.dropRate;
      if (rollValue < cumulative) {
        wonItemFromRoll = caseItem.containedItem;
        break;
      } 
    }
    
    if (!wonItemFromRoll) {
      console.error('Could not determine winning item from roll value');
      return 0.85;
    }
    
    // Find winning item in the animation items array
    const uniqueItemsCount = animationItems.length / 8;
    const winningIndex = animationItems.findIndex(item => item.containedItem.id === wonItemFromRoll.id);
    
    let targetIndex;
    if (winningIndex !== -1) {
      // Calculate which repetition to target - use a consistent repetition (6th repetition)
      const targetRepetition = 6; // This puts us well past the rolling end at ~75% through animation
      const indexInRepetition = winningIndex % uniqueItemsCount;
      targetIndex = targetRepetition * uniqueItemsCount + indexInRepetition;
      
      // Ensure we don't go beyond available items (we have 8 repetitions)
      if (targetIndex >= animationItems.length) {
        // Fall back to 5th repetition if 6th would exceed bounds
        targetIndex = 5 * uniqueItemsCount + indexInRepetition;
      }
    } else {
      // Fallback to a position further along to maintain leftward direction
      targetIndex = Math.floor(animationItems.length * 0.75);
    }
    
    // Convert to scroll progress (0 to 1)
    const totalWidth = animationItems.length * itemWidth;
    const targetPosition = targetIndex * itemWidth - centerPosition;
    const calculatedProgress = targetPosition / totalWidth;
    
    // Calculate minimum safe position based on actual container size and rolling end
    // Convert rolling end position to actual pixel position for this container
    const rollingEndPixels = rollingEndPosition * totalWidth;
    const minSafePixels = rollingEndPixels + (containerWidth * 0.15); // 15% of container width buffer
    const minProgressForContainer = minSafePixels / totalWidth;
    
    const maxProgress = 0.95; // Don't go too far to avoid running out of items
    
    // Only enforce minimum if our calculation is PAST the rolling end but too close to it
    // If calculated position is BEFORE rolling end, use it as-is (no constraint needed)
    let finalProgress;
    if (calculatedProgress <= rollingEndPosition) {
      // Item lands before/at rolling end - use exact calculation
      finalProgress = Math.min(calculatedProgress, maxProgress);
    } else if (calculatedProgress < minProgressForContainer) {
      // Item lands after rolling end but too close - apply constraint
      finalProgress = minProgressForContainer;
    } else {
      // Item lands after rolling end with sufficient buffer - use calculation
      finalProgress = Math.min(calculatedProgress, maxProgress);
    }
    

    

    
    return finalProgress;
  }, [animationItems]);

  const handleOpenCase = async () => {
    if (animationState !== 'idle') return;
    
    setAnimationState('loading');
    setError(null);
    audioHooks.onInitiate?.();

    try {
      // Reset scroll progress
      scrollProgress.set(0);
      
      // Start loading animation
      await new Promise(resolve => setTimeout(resolve, 800));
      
      // Start rolling animation
      setAnimationState('rolling');
      setCanSkip(true);
      
      // Track timing for adaptive rolling duration
      const startTime = Date.now();
      
      // Start API call in parallel to get the target position
      const apiPromise = httpClient.post(`/shop/cases/${caseId}/open`);
      
      // Start a longer rolling animation that we can interrupt
      const rollingAnimation = animate(scrollProgress, 0.9, {
        duration: 8, // Longer duration so we can stop it early
        ease: "linear"
      });
      
      // Wait for API response
      const apiResponse = await apiPromise;
      
      // Calculate where we should stop rolling based on the target
      const targetProgress = caseContents 
        ? generateAnimationSequenceFromRoll(apiResponse.data.rollValue, caseContents)
        : generateAnimationSequence(apiResponse.data.wonItem);
      
      // Determine optimal rolling end point
      // If target is early (< 0.7), stop just before it; if target is late, stop at reasonable point  
      const safeBuffer = 0.05;
      const rollingEndPoint = targetProgress < 0.7 
        ? Math.max(targetProgress - safeBuffer, 0.3) // Don't go below 30%
        : Math.min(0.8, targetProgress - safeBuffer); // Standard case
      

      
      // Stop rolling animation early and animate to the calculated end point
      rollingAnimation.stop();
      const adjustedRollingAnimation = animate(scrollProgress, rollingEndPoint, {
        duration: Math.max(1, 6 - (Date.now() - startTime) / 1000), // Adjust remaining time
        ease: "linear"
      });
      
      // Wait for adjusted rolling to complete
      await adjustedRollingAnimation;
      setRollResult(apiResponse.data);
      
      // Start deceleration using the rollValue from the API response  
      await handleDeceleration(apiResponse.data, targetProgress);
      
    } catch (error: any) {
      setError(error.response?.data?.message || 'Failed to open case');
      setAnimationState('idle');
    }
  };

  const handleDeceleration = async (rollResult: RollResult, targetProgress?: number) => {
    setAnimationState('decelerating');
    setCanSkip(false);
    audioHooks.onDecelerate?.();
    
    // Use pre-calculated target progress or calculate it
    const finalProgress = targetProgress ?? (caseContents 
      ? generateAnimationSequenceFromRoll(rollResult.rollValue, caseContents)
      : generateAnimationSequence(rollResult.wonItem)); // Fallback to old method if no case contents
    
    // Use Framer Motion's animate function for smooth deceleration
    const decelerationAnimation = animate(scrollProgress, finalProgress, {
      duration: 3,
      ease: [0.25, 0.46, 0.45, 0.94] // Smooth deceleration curve
    });
    
    // Wait for deceleration to complete
    await decelerationAnimation;
    
    // Animation complete, reveal the item
    setAnimationState('revealing');
    audioHooks.onReveal?.();
    audioHooks.onRarityReveal?.(rollResult.wonItem.rarity);
    
    // Wait for dramatic pause before showing final result
    await new Promise(resolve => setTimeout(resolve, 1200));
    
    setAnimationState('reward');
  };

  const handleSkipAnimation = async () => {
    if (!canSkip || !rollResult) return;
    
    setAnimationState('reward');
    audioHooks.onRarityReveal?.(rollResult.wonItem.rarity);
  };

  const handleClaimAndClose = () => {
    if (rollResult) {
      onRollComplete(rollResult);
    }
    handleClose();
  };

  const handleClose = () => {
    scrollProgress.set(0);
    setAnimationState('idle');
    setRollResult(null);
    setError(null);
    setCanSkip(false);
    onClose();
  };

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget && animationState === 'idle') {
      handleClose();
    }
  };

  const renderItemThumbnail = (item: CaseItemDTO, index: number) => {
    const containedItem = item.containedItem;
    const rarityColor = getRarityColor(containedItem.rarity);
    
    return (
      <motion.div
        key={`${containedItem.id}-${index}`}
        className="flex-shrink-0 w-24 h-24 mx-2 relative"
        style={{
          filter: animationState === 'revealing' ? 'blur(1px) brightness(0.7)' : 'none',
          minWidth: '96px', // Ensure consistent width
        }}
        transition={{ duration: 0.3 }}
      >
        <div 
          className="w-full h-full rounded-lg border-2 overflow-hidden relative bg-slate-800"
          style={{ borderColor: rarityColor }}
        >
          {/* Item preview based on category */}
          {containedItem.category === 'USER_COLOR' ? (
            <NameplatePreview
              username={user?.username || "User"}
              avatar={user?.avatar || "/default-avatar.png"}
              color={containedItem.imageUrl}
              fallbackColor={rarityColor}
              message=""
              className="h-full w-full"
              size="sm"
            />
          ) : containedItem.category === 'BADGE' ? (
            <BadgePreview
              username={user?.username || "User"}
              avatar={user?.avatar || "/default-avatar.png"}
              badgeUrl={containedItem.thumbnailUrl || containedItem.imageUrl}
              message=""
              className="h-full w-full"
              size="sm"
            />
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
          
          {/* Rarity glow effect */}
          <div 
            className="absolute inset-0 rounded-lg opacity-30"
            style={{
              boxShadow: `inset 0 0 10px ${rarityColor}`,
            }}
          />
        </div>
        
        {/* Rarity indicator */}
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
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4"
        onClick={handleBackdropClick}
      >
        <motion.div
          initial={{ scale: 0.9, opacity: 0, y: 20 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          exit={{ scale: 0.9, opacity: 0, y: 20 }}
          transition={{ type: "spring", damping: 25, stiffness: 300 }}
          className="relative bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 rounded-xl shadow-2xl border border-slate-700/50 max-w-4xl w-full max-h-[90vh] overflow-hidden"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="relative flex items-center p-6 border-b border-slate-700/50">
            {/* Left section - Icon (when needed) */}
            <div className="flex items-center">
              {animationState !== 'reward' && animationState !== 'idle' && (
                <div className="p-2 bg-primary/20 rounded-lg">
                  <FaGift className="text-primary" size={20} />
                </div>
              )}
            </div>
            
            {/* Center section - Title */}
            <div className="absolute left-1/2 transform -translate-x-1/2">
              <h2 className="text-xl font-bold text-white whitespace-nowrap">
                {animationState === 'idle' && `Open ${caseName}`}
                {animationState === 'loading' && 'Preparing Case...'}
                {(animationState === 'rolling' || animationState === 'decelerating' || animationState === 'revealing') && ' '}
                {/* REMOVED: 'Congratulations!' text for reward state */}
                {animationState === 'reward' && ' '}
              </h2>
            </div>
            
            {/* Right section - Action buttons */}
            <div className="flex items-center space-x-2 ml-auto">
              {/* Skip Animation Button - Icon Only in Top Right */}
              {canSkip && (
                <motion.button
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  onClick={handleSkipAnimation}
                  className="p-2 bg-yellow-600/20 hover:bg-yellow-600/30 rounded-lg transition-colors text-yellow-400 hover:text-yellow-300"
                  title="Skip Animation"
                >
                  <FaForward size={16} />
                </motion.button>
              )}
              
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

          {/* Content */}
          <div className="p-6">
            {animationState === 'idle' && (
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
                      onClick={fetchCaseContents}
                      className="px-4 py-2 bg-primary hover:bg-primary/90 text-white rounded-lg transition-colors"
                    >
                      Try Again
                    </button>
                  </div>
                ) : (
                  <>
                    {/* Items Grid */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {caseContents.items
                        .sort((a, b) => b.dropRate - a.dropRate) // Sort by drop rate descending
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
                              <div className="flex items-start space-x-3">
                                {/* Item Preview */}
                                <div className="flex-shrink-0 w-16 h-16 rounded-lg overflow-hidden border-2" style={{ borderColor: rarityColor }}>
                                  {item.category === 'USER_COLOR' ? (
                                    <NameplatePreview
                                      username="Preview"
                                      avatar="/default-avatar.png"
                                      color={item.imageUrl}
                                      fallbackColor={rarityColor}
                                      message=""
                                      className="h-full w-full"
                                      size="sm"
                                    />
                                  ) : item.category === 'BADGE' ? (
                                    <BadgePreview
                                      username="Preview"
                                      avatar="/default-avatar.png"
                                      badgeUrl={item.thumbnailUrl || item.imageUrl}
                                      message=""
                                      className="h-full w-full"
                                      size="sm"
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

                                  {/* Drop Rate */}
                                  <div className="flex items-center justify-between">
                                    <span className="text-xs text-slate-300">Drop Rate</span>
                                    <div className="flex items-center space-x-2">
                                      <div className="w-16 bg-slate-700 rounded-full h-1.5 overflow-hidden">
                                        <div 
                                          className="h-full bg-primary transition-all duration-300"
                                          style={{ width: `${caseItem.dropRate}%` }}
                                        />
                                      </div>
                                      <span className="text-sm font-medium text-primary">
                                        {caseItem.dropRate}%
                                      </span>
                                    </div>
                                  </div>

                                  {item.description && (
                                    <p className="text-xs text-slate-400 mt-2 line-clamp-2">{item.description}</p>
                                  )}
                                </div>
                              </div>
                            </motion.div>
                          );
                        })
                      }
                    </div>
                  </>
                )}

                {/* Action Buttons */}
                <div className="flex space-x-3 justify-center pt-4 border-t border-slate-700/50">
                  <button
                    onClick={handleClose}
                    className="px-6 py-3 bg-slate-700 hover:bg-slate-600 text-white rounded-lg transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleOpenCase}
                    disabled={!caseContents}
                    className="px-6 py-3 bg-primary hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-lg transition-colors flex items-center space-x-2"
                  >
                    <span>Open Case</span>
                  </button>
                </div>
              </motion.div>
            )}

            {animationState === 'loading' && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="text-center space-y-6 py-8"
              >
                <motion.div
                  animate={{ 
                    rotate: [0, 360],
                    scale: [1, 1.2, 1]
                  }}
                  transition={{ 
                    duration: 0.8, 
                    repeat: Infinity,
                    ease: "easeInOut"
                  }}
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
            )}

            {(animationState === 'rolling' || animationState === 'decelerating' || animationState === 'revealing') && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="space-y-6"
              >
                {/* Case Opening Animation Area */}
                <div className="relative bg-gradient-to-r from-slate-800/50 via-slate-700/50 to-slate-800/50 rounded-lg border border-slate-600/50 overflow-hidden">
                  {/* Stop Zone Indicator */}
                  <div className="absolute top-0 bottom-0 left-1/2 transform -translate-x-1/2 w-1 bg-gradient-to-b from-primary/60 via-primary to-primary/60 z-10">
                    <div className="absolute -top-2 left-1/2 transform -translate-x-1/2">
                      <div className="w-0 h-0 border-l-4 border-r-4 border-b-4 border-transparent border-b-primary"></div>
                    </div>
                  </div>
                  
                  {/* Scrolling Items Container */}
                  <div 
                    ref={scrollContainerRef}
                    className="relative h-32 overflow-hidden"
                  >
                    <motion.div
                      className="flex items-center h-full py-4"
                      style={{ 
                        x,
                        width: 'max-content',
                        flexWrap: 'nowrap'
                      }}
                    >
                      {animationItems.map((item, index) => renderItemThumbnail(item, index))}
                    </motion.div>
                    
                    {/* Spotlight Effect for Revealing State */}
                    {animationState === 'revealing' && (
                      <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="absolute inset-0 bg-gradient-radial from-transparent via-black/20 to-black/60 pointer-events-none"
                        style={{
                          background: 'radial-gradient(circle at center, transparent 15%, rgba(0,0,0,0.3) 40%, rgba(0,0,0,0.7) 70%)'
                        }}
                      />
                    )}
                  </div>
                  
                  {/* Fade edges for infinite scroll effect */}
                  <div className="absolute inset-y-0 left-0 w-16 bg-gradient-to-r from-slate-700/80 to-transparent pointer-events-none"></div>
                  <div className="absolute inset-y-0 right-0 w-16 bg-gradient-to-l from-slate-700/80 to-transparent pointer-events-none"></div>
                </div>
              </motion.div>
            )}

            {animationState === 'reward' && rollResult && (
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                className="space-y-6"
              >
                {/* Reward Display */}
                <div className="text-center">
                  {/* Restructured Header: Item name, rarity badge, and type - MOVED TO TOP */}
                  <motion.div 
                    initial={{ y: 20, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    transition={{ delay: 0.2, duration: 0.6, ease: "easeOut" }}
                    className="text-center mb-6"
                  >
                    <div className="flex items-center justify-center space-x-2 mb-2">
                      <h4 className="text-2xl font-bold text-white">
                        {rollResult.wonItem.name}
                      </h4>
                      <span 
                        className="px-3 py-1 rounded-full text-sm font-semibold"
                        style={getRarityBadgeStyle(rollResult.wonItem.rarity)}
                      >
                        {getRarityLabel(rollResult.wonItem.rarity)}
                      </span>
                    </div>
                    
                    <p className="text-slate-300 text-base">
                      {formatDisplayText(rollResult.wonItem.category)}
                    </p>
                  </motion.div>
                  
                  {/* REMOVED: Container and background glow - now just the preview component */}
                  <motion.div 
                    initial={{ y: 20, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    transition={{ delay: 0.4, duration: 0.6, ease: "easeOut" }}
                    className="flex justify-center"
                  >
                    <div className="max-w-md mx-auto">
                      {rollResult.wonItem.category === 'USER_COLOR' ? (
                        <NameplatePreview
                          username={user?.username || "Username"}
                          avatar={user?.avatar || "/default-avatar.png"}
                          color={rollResult.wonItem.imageUrl}
                          fallbackColor={getRarityColor(rollResult.wonItem.rarity)}
                          message="Your new nameplate color"
                          className=""
                          size="md"
                        />
                      ) : rollResult.wonItem.category === 'BADGE' ? (
                        <BadgePreview
                          username={user?.username || "Username"}
                          avatar={user?.avatar || "/default-avatar.png"}
                          badgeUrl={rollResult.wonItem.thumbnailUrl || rollResult.wonItem.imageUrl}
                          message="Your new badge"
                          className=""
                          size="md"
                        />
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

                      {/* Item Description - kept for cases where description exists */}
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

                {/* Already Owned Notice */}
                {rollResult.alreadyOwned && (
                  <motion.div 
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 1 }}
                    className="p-4 bg-blue-500/10 border border-blue-500/30 rounded-lg"
                  >
                    <p className="text-blue-400 text-sm text-center">
                      💡 <strong>Note:</strong> You already owned this item, so no duplicate was added to your inventory.
                    </p>
                  </motion.div>
                )}

                {/* Action Buttons */}
                <motion.div 
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 1.2 }}
                  className="flex justify-center space-x-3"
                >
                  <button
                    onClick={handleClose}
                    className="px-6 py-3 bg-slate-700 hover:bg-slate-600 text-white rounded-lg transition-colors"
                  >
                    Close
                  </button>
                  <button
                    onClick={handleClaimAndClose}
                    className="px-8 py-3 bg-green-600 hover:bg-green-500 text-white rounded-lg transition-colors flex items-center space-x-2"
                  >
                    <FaGift size={16} />
                    <span>Claim & Continue</span>
                  </button>
                </motion.div>
              </motion.div>
            )}
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
} 