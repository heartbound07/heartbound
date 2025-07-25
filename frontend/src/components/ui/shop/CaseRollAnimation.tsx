import React, { useRef, useState, useEffect, useCallback } from 'react';
import { motion, MotionValue } from 'framer-motion';
import { CaseItemThumbnail } from './CaseItemThumbnail';
import { CaseItemDTO, AnimationState } from './CaseTypes';

const ITEM_WIDTH = 112; // w-24 (96px) + mx-2 (16px) = 112px
const OVERSCAN = 5;

interface VirtualItem {
  index: number;
  item: CaseItemDTO;
}

interface CaseRollAnimationProps {
  animationItems: CaseItemDTO[];
  animationState: AnimationState;
  x: MotionValue<number>;
  user?: any;
}

export const CaseRollAnimation = React.memo(({ animationItems, animationState, x, user }: CaseRollAnimationProps) => {
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const [virtualItems, setVirtualItems] = useState<VirtualItem[]>([]);
  
  const totalAnimationWidth = animationItems.length * ITEM_WIDTH;

  const updateVirtualItems = useCallback(() => {
    if (!animationItems.length || !scrollContainerRef.current) return;
    
    const containerWidth = scrollContainerRef.current.offsetWidth;
    const scrollLeft = -x.get();
    
    let startIndex = Math.floor(scrollLeft / ITEM_WIDTH) - OVERSCAN;
    startIndex = Math.max(0, startIndex);

    let endIndex = Math.ceil((scrollLeft + containerWidth) / ITEM_WIDTH) + OVERSCAN;
    endIndex = Math.min(animationItems.length - 1, endIndex);

    const newVirtualItems: VirtualItem[] = [];
    for (let i = startIndex; i <= endIndex; i++) {
        newVirtualItems.push({
            index: i,
            item: animationItems[i],
        });
    }
    setVirtualItems(newVirtualItems);
  }, [animationItems, x]);

  useEffect(() => {
    const unsubscribe = x.onChange(updateVirtualItems);
    updateVirtualItems(); // Initial call
    return unsubscribe;
  }, [x, updateVirtualItems]);
  
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-6"
    >
      <div className="relative bg-gradient-to-r from-slate-800/50 via-slate-700/50 to-slate-800/50 rounded-lg border border-slate-600/50 overflow-hidden">
        <div className="absolute top-0 bottom-0 left-1/2 transform -translate-x-1/2 w-1 bg-gradient-to-b from-primary/60 via-primary to-primary/60 z-10">
          <div className="absolute -top-2 left-1/2 transform -translate-x-1/2">
            <div className="w-0 h-0 border-l-4 border-r-4 border-b-4 border-transparent border-b-primary"></div>
          </div>
        </div>
        
        <div ref={scrollContainerRef} className="relative h-32 overflow-hidden">
          <motion.div
            className="h-full"
            style={{ 
              x,
              width: totalAnimationWidth,
              position: 'relative',
            }}
          >
            {virtualItems.map(({ item, index }) => (
              <div
                key={`${item.containedItem.id}-${index}`}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: `${index * ITEM_WIDTH}px`,
                  width: `${ITEM_WIDTH}px`,
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <CaseItemThumbnail
                  item={item}
                  animationState={animationState}
                  user={user}
                />
              </div>
            ))}
          </motion.div>
          
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
        
        <div className="absolute inset-y-0 left-0 w-16 bg-gradient-to-r from-slate-700/80 to-transparent pointer-events-none"></div>
        <div className="absolute inset-y-0 right-0 w-16 bg-gradient-to-l from-slate-700/80 to-transparent pointer-events-none"></div>
      </div>
    </motion.div>
  );
});

CaseRollAnimation.displayName = 'CaseRollAnimation'; 