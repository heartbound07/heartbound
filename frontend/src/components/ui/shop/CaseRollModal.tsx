import React, { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence, useAnimation } from 'framer-motion';
import { FaTimes, FaDice, FaGift, FaForward, FaVolumeUp } from 'react-icons/fa';
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
  const scrollAnimation = useAnimation();
  
  // Audio hooks for future implementation
  const audioHooks: AudioHooks = {
    onInitiate: () => {
      // Hook for case opening initiation sound
      console.log('🎵 Audio Hook: Initiate');
    },
    onRollingTick: () => {
      // Hook for rolling tick sound
      console.log('🎵 Audio Hook: Rolling Tick');
    },
    onDecelerate: () => {
      // Hook for deceleration sound
      console.log('🎵 Audio Hook: Decelerate');
    },
    onReveal: () => {
      // Hook for item reveal sound
      console.log('🎵 Audio Hook: Reveal');
    },
    onRarityReveal: (rarity: string) => {
      // Hook for rarity-specific reveal sound
      console.log(`🎵 Audio Hook: Rarity Reveal - ${rarity}`);
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
      console.error('Error fetching case contents:', error);
      setError('Failed to load case contents');
    }
  };

  const generateAnimationSequence = useCallback((winningItem: RollResult['wonItem']) => {
    if (!animationItems.length) return 0;
    
    // Calculate the position where the winning item should stop (center of view)
    const containerWidth = scrollContainerRef.current?.offsetWidth || 800;
    const itemWidth = 120; // Width of each item including margin (96px + 16px margin)
    const centerPosition = containerWidth / 2 - (itemWidth / 2);
    
    // Find winning item in the animation items array (look for it in the later iterations for dramatic effect)
    const uniqueItemsCount = animationItems.length / 8;
    const winningIndex = animationItems.findIndex(item => item.containedItem.id === winningItem.id);
    
    // If found, use an instance from the later part of the array for better visual effect
    let targetIndex;
    if (winningIndex !== -1) {
      // Find the item in the 5th or 6th repetition for dramatic timing
      const repetitionOffset = Math.floor(uniqueItemsCount * 4.5); // Start from 5th repetition
      const indexInRepetition = winningIndex % uniqueItemsCount;
      targetIndex = repetitionOffset + indexInRepetition;
    } else {
      // Fallback to a position in the later part
      targetIndex = Math.floor(animationItems.length * 0.6);
    }
    
    // Calculate final scroll position to center the winning item
    const finalScrollX = -(targetIndex * itemWidth - centerPosition);
    
    return finalScrollX;
  }, [animationItems]);

  const handleOpenCase = async () => {
    if (animationState !== 'idle') return;
    
    setAnimationState('loading');
    setError(null);
    audioHooks.onInitiate?.();

    try {
      // Reset animation to starting position
      scrollAnimation.set({ x: 0 });
      
      // Start loading animation
      await new Promise(resolve => setTimeout(resolve, 800));
      
      // Start rolling animation immediately
      setAnimationState('rolling');
      setCanSkip(true);
      
      // Small delay to ensure DOM is ready, then start animation
      await new Promise(resolve => setTimeout(resolve, 100));
      startRollingAnimation();
      
      const startTime = Date.now();
      const minRollingTime = 8000; // Minimum 8 seconds of rolling for suspense
      
      // Start API call in parallel with animation
      const apiPromise = httpClient.post(`/shop/cases/${caseId}/open`);
      
      // Get the API response
      const apiResponse = await apiPromise;
      setRollResult(apiResponse.data);
      
      // Calculate how long we've been rolling
      const elapsedTime = Date.now() - startTime;
      const remainingTime = Math.max(0, minRollingTime - elapsedTime);
      
      // Wait for minimum rolling time if needed
      if (remainingTime > 0) {
        await new Promise(resolve => setTimeout(resolve, remainingTime));
      }
      
      // Now start deceleration with the correct item
      await handleDeceleration(apiResponse.data.wonItem);
      
    } catch (error: any) {
      console.error('Error opening case:', error);
      setError(error.response?.data?.message || 'Failed to open case');
      setAnimationState('idle');
    }
  };

  const startRollingAnimation = () => {
    console.log('🎰 Starting rolling animation...', {
      animationItemsCount: animationItems.length,
      animationState
    });
    
    // Calculate the total width needed for smooth infinite scroll
    const itemWidth = 120; // Width including margin (24 + 16 margin)
    const totalItems = animationItems.length;
    const oneLoopWidth = totalItems > 0 ? (totalItems / 8) * itemWidth : 800; // One set of unique items
    
    console.log('🎰 Animation parameters:', {
      itemWidth,
      totalItems,
      oneLoopWidth,
      scrollDistance: -oneLoopWidth * 6
    });
    
    // Start continuous scrolling animation with infinite repeat
    scrollAnimation.start({
      x: -oneLoopWidth * 6, // Scroll through more loops for extended effect
      transition: {
        duration: 8, // 8 seconds per loop for smoother, longer rolling
        ease: "linear",
        repeat: Infinity
      }
    });
    
    console.log('🎰 Rolling animation started!');
  };

  const handleDeceleration = async (winningItem: RollResult['wonItem']) => {
    setAnimationState('decelerating');
    setCanSkip(false);
    audioHooks.onDecelerate?.();
    
    // Debug logging
    console.log('🎲 Winning item from API:', {
      id: winningItem.id,
      name: winningItem.name,
      rarity: winningItem.rarity,
      category: winningItem.category
    });
    
    console.log('🎲 Available animation items:', animationItems.map(item => ({
      id: item.containedItem.id,
      name: item.containedItem.name,
      rarity: item.containedItem.rarity,
      category: item.containedItem.category
    })));
    
    // Stop the infinite scroll and calculate final position
    scrollAnimation.stop();
    const finalPosition = generateAnimationSequence(winningItem);
    
    console.log('🎲 Final scroll position:', finalPosition);
    
    // Decelerate to the winning item position with perfect timing
    await scrollAnimation.start({
      x: finalPosition,
      transition: {
        duration: 3.0, // 3 seconds for dramatic deceleration
        ease: [0.25, 0.46, 0.45, 0.94] // Smooth deceleration curve
      }
    });
    
    // Reveal the winning item
    setAnimationState('revealing');
    audioHooks.onReveal?.();
    audioHooks.onRarityReveal?.(winningItem.rarity);
    
    // Wait for dramatic pause before showing final result
    await new Promise(resolve => setTimeout(resolve, 1200)); // 1.2 seconds for perfect timing
    
    setAnimationState('reward');
  };

  const handleSkipAnimation = async () => {
    if (!canSkip || !rollResult) return;
    
    scrollAnimation.stop();
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
    scrollAnimation.stop();
    scrollAnimation.set({ x: 0 }); // Reset position
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
          <div className="flex items-center justify-between p-6 border-b border-slate-700/50">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-primary/20 rounded-lg">
                {animationState === 'reward' ? (
                  <FaGift className="text-primary" size={20} />
                ) : (
                  <FaDice className="text-primary" size={20} />
                )}
              </div>
              <div>
                <h2 className="text-xl font-bold text-white">
                  {animationState === 'idle' && 'Open Case'}
                  {animationState === 'loading' && 'Preparing Case...'}
                  {(animationState === 'rolling' || animationState === 'decelerating') && 'Opening Case...'}
                  {animationState === 'revealing' && 'Revealing Item...'}
                  {animationState === 'reward' && 'Congratulations!'}
                </h2>
                <p className="text-slate-400 text-sm">{caseName}</p>
              </div>
            </div>
            
            <div className="flex items-center space-x-2">
              {/* Skip Animation Button */}
              {canSkip && (
                <motion.button
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  onClick={handleSkipAnimation}
                  className="p-2 bg-yellow-600/20 hover:bg-yellow-600/30 rounded-lg transition-colors text-yellow-400 hover:text-yellow-300 flex items-center space-x-1"
                  title="Skip Animation"
                >
                  <FaForward size={14} />
                  <span className="text-xs">Skip</span>
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
                className="text-center space-y-6"
              >
                <div className="p-6 bg-slate-800/50 border border-slate-700 rounded-lg">
                  <div className="text-lg font-medium text-white mb-2">
                    Ready to open this case?
                  </div>
                  <p className="text-slate-300 text-sm">
                    You'll receive one random item from this case. The item you get is completely random based on drop rates.
                  </p>
                  
                  {caseContents && (
                    <div className="mt-4 text-slate-400 text-sm">
                      Contains {caseContents.items.length} possible items
                    </div>
                  )}
                </div>

                {error && (
                  <div className="p-4 bg-red-500/10 border border-red-500/30 rounded-lg">
                    <p className="text-red-400 text-center">{error}</p>
                  </div>
                )}

                <div className="flex space-x-3 justify-center">
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
                    <FaDice size={16} />
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
                    <FaDice className="text-primary" size={32} />
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
                      animate={scrollAnimation}
                      className="flex items-center h-full py-4"
                      style={{ 
                        x: 0,
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
                
                {/* Status Text */}
                <div className="text-center">
                  <h3 className="text-xl font-bold text-white mb-2">
                    {animationState === 'rolling' && 'Rolling for your item...'}
                    {animationState === 'decelerating' && 'Almost there...'}
                    {animationState === 'revealing' && 'You won...'}
                  </h3>
                  <p className="text-slate-300">
                    {animationState === 'rolling' && 'Watch the items scroll by!'}
                    {animationState === 'decelerating' && 'Slowing down to reveal your prize'}
                    {animationState === 'revealing' && 'Drumroll please...'}
                  </p>
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
                  <motion.h3 
                    initial={{ scale: 0.8 }}
                    animate={{ scale: 1 }}
                    transition={{ type: "spring", damping: 15 }}
                    className="text-2xl font-bold text-white mb-6"
                  >
                    🎉 You won! 🎉
                  </motion.h3>
                  
                  <motion.div
                    initial={{ scale: 0.8, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    transition={{ delay: 0.2 }}
                    className="bg-slate-800/50 border rounded-lg p-8 max-w-md mx-auto relative overflow-hidden"
                    style={{ 
                      borderColor: getRarityColor(rollResult.wonItem.rarity),
                      borderWidth: '3px'
                    }}
                  >
                    {/* Animated background glow */}
                    <motion.div
                      animate={{
                        opacity: [0.3, 0.6, 0.3],
                      }}
                      transition={{
                        duration: 2,
                        repeat: Infinity,
                        ease: "easeInOut"
                      }}
                      className="absolute inset-0 rounded-lg"
                      style={{
                        background: `radial-gradient(circle at center, ${getRarityColor(rollResult.wonItem.rarity)}20 0%, transparent 70%)`
                      }}
                    />
                    
                    {/* Item Preview */}
                    <div className="relative z-10">
                      <div className="flex justify-center mb-6">
                        <motion.div 
                          initial={{ scale: 0.5, rotateY: -180 }}
                          animate={{ scale: 1, rotateY: 0 }}
                          transition={{ delay: 0.4, type: "spring", damping: 15 }}
                          className="w-32 h-32 rounded-lg overflow-hidden border-3" 
                          style={{ borderColor: getRarityColor(rollResult.wonItem.rarity) }}
                        >
                          {rollResult.wonItem.category === 'USER_COLOR' ? (
                            <NameplatePreview
                              username={user?.username || "Username"}
                              avatar={user?.avatar || "/default-avatar.png"}
                              color={rollResult.wonItem.imageUrl}
                              fallbackColor={getRarityColor(rollResult.wonItem.rarity)}
                              message="Your new nameplate color"
                              className="h-full w-full"
                              size="md"
                            />
                          ) : rollResult.wonItem.category === 'BADGE' ? (
                            <BadgePreview
                              username={user?.username || "Username"}
                              avatar={user?.avatar || "/default-avatar.png"}
                              badgeUrl={rollResult.wonItem.thumbnailUrl || rollResult.wonItem.imageUrl}
                              message="Your new badge"
                              className="h-full w-full"
                              size="md"
                            />
                          ) : rollResult.wonItem.imageUrl ? (
                            <img 
                              src={rollResult.wonItem.imageUrl} 
                              alt={rollResult.wonItem.name}
                              className="h-full w-full object-cover"
                            />
                          ) : (
                            <div className="h-full w-full bg-slate-700 flex items-center justify-center">
                              <span className="text-xs text-slate-400">No Image</span>
                            </div>
                          )}
                        </motion.div>
                      </div>

                      {/* Item Details */}
                      <div className="text-center">
                        <div className="flex items-center justify-center space-x-2 mb-3">
                          <motion.h4 
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            transition={{ delay: 0.6 }}
                            className="text-xl font-bold text-white"
                          >
                            {rollResult.wonItem.name}
                          </motion.h4>
                          <motion.span 
                            initial={{ scale: 0 }}
                            animate={{ scale: 1 }}
                            transition={{ delay: 0.8, type: "spring" }}
                            className="px-3 py-1 rounded-full text-sm font-semibold"
                            style={getRarityBadgeStyle(rollResult.wonItem.rarity)}
                          >
                            {getRarityLabel(rollResult.wonItem.rarity)}
                          </motion.span>
                        </div>
                        
                        <p className="text-slate-300 text-sm mb-3">
                          {formatDisplayText(rollResult.wonItem.category)}
                        </p>
                        
                        {rollResult.wonItem.description && (
                          <p className="text-slate-400 text-xs mb-4">
                            {rollResult.wonItem.description}
                          </p>
                        )}
                      </div>
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