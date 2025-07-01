import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { FaTimes, FaDice, FaGift } from 'react-icons/fa';
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
  user 
}: CaseRollModalProps) {
  const [stage, setStage] = useState<'confirm' | 'rolling' | 'result'>('confirm');
  const [rollResult, setRollResult] = useState<RollResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleOpenCase = async () => {
    setStage('rolling');
    setError(null);

    try {
      const response = await httpClient.post(`/shop/cases/${caseId}/open`);
      setRollResult(response.data);
      
      // Add a small delay for dramatic effect
      setTimeout(() => {
        setStage('result');
      }, 2000);
      
    } catch (error: any) {
      console.error('Error opening case:', error);
      setError(error.response?.data?.message || 'Failed to open case');
      setStage('confirm');
    }
  };

  const handleClaimAndClose = () => {
    if (rollResult) {
      onRollComplete(rollResult);
    }
    handleClose();
  };

  const handleClose = () => {
    setStage('confirm');
    setRollResult(null);
    setError(null);
    onClose();
  };

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget && stage !== 'rolling') {
      handleClose();
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
          className="relative bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-2xl border border-slate-700/50 max-w-2xl w-full max-h-[90vh] overflow-hidden"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-6 border-b border-slate-700/50">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-primary/20 rounded-lg">
                {stage === 'result' ? (
                  <FaGift className="text-primary" size={20} />
                ) : (
                  <FaDice className="text-primary" size={20} />
                )}
              </div>
              <div>
                <h2 className="text-xl font-bold text-white">
                  {stage === 'confirm' && 'Open Case'}
                  {stage === 'rolling' && 'Opening Case...'}
                  {stage === 'result' && 'Congratulations!'}
                </h2>
                <p className="text-slate-400 text-sm">{caseName}</p>
              </div>
            </div>
            
            {stage !== 'rolling' && (
              <button
                onClick={handleClose}
                className="p-2 hover:bg-slate-700/50 rounded-lg transition-colors text-slate-400 hover:text-white"
              >
                <FaTimes size={20} />
              </button>
            )}
          </div>

          {/* Content */}
          <div className="p-6">
            {stage === 'confirm' && (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="text-center space-y-6"
              >
                <div className="p-4 bg-slate-800/50 border border-slate-700 rounded-lg">
                  <div className="text-lg font-medium text-white mb-2">
                    Are you sure you want to open this case?
                  </div>
                  <p className="text-slate-300 text-sm">
                    This action cannot be undone. The case will be consumed and you'll receive a random item based on the drop rates.
                  </p>
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
                    className="px-6 py-3 bg-primary hover:bg-primary/90 text-white rounded-lg transition-colors flex items-center space-x-2"
                  >
                    <FaDice size={16} />
                    <span>Open Case</span>
                  </button>
                </div>
              </motion.div>
            )}

            {stage === 'rolling' && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="text-center space-y-6 py-8"
              >
                <motion.div
                  animate={{ 
                    rotate: [0, 360],
                    scale: [1, 1.1, 1]
                  }}
                  transition={{ 
                    duration: 1.5, 
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
                  <h3 className="text-xl font-bold text-white mb-2">Opening your case...</h3>
                  <p className="text-slate-300">Determining your reward...</p>
                </div>

                <div className="flex justify-center">
                  <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin"></div>
                </div>
              </motion.div>
            )}

            {stage === 'result' && rollResult && (
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                className="space-y-6"
              >
                {/* Won Item Display */}
                <div className="text-center">
                  <h3 className="text-lg font-bold text-white mb-4">You won:</h3>
                  
                  <motion.div
                    initial={{ scale: 0.8, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    transition={{ delay: 0.2 }}
                    className="bg-slate-800/50 border rounded-lg p-6 max-w-md mx-auto"
                    style={{ 
                      borderColor: getRarityColor(rollResult.wonItem.rarity),
                      borderWidth: '2px'
                    }}
                  >
                    {/* Item Preview */}
                    <div className="flex justify-center mb-4">
                      <div className="w-24 h-24 rounded-lg overflow-hidden border-2" 
                           style={{ borderColor: getRarityColor(rollResult.wonItem.rarity) }}>
                        {rollResult.wonItem.category === 'USER_COLOR' ? (
                          <NameplatePreview
                            username={user?.username || "Username"}
                            avatar={user?.avatar || "/default-avatar.png"}
                            color={rollResult.wonItem.imageUrl}
                            fallbackColor={getRarityColor(rollResult.wonItem.rarity)}
                            message="Your new nameplate color"
                            className="h-full w-full"
                            size="sm"
                          />
                        ) : rollResult.wonItem.category === 'BADGE' ? (
                          <BadgePreview
                            username={user?.username || "Username"}
                            avatar={user?.avatar || "/default-avatar.png"}
                            badgeUrl={rollResult.wonItem.thumbnailUrl || rollResult.wonItem.imageUrl}
                            message="Your new badge"
                            className="h-full w-full"
                            size="sm"
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
                      </div>
                    </div>

                    {/* Item Details */}
                    <div className="text-center">
                      <div className="flex items-center justify-center space-x-2 mb-2">
                        <h4 className="text-lg font-bold text-white">{rollResult.wonItem.name}</h4>
                        <span 
                          className="px-2 py-0.5 rounded text-xs font-semibold"
                          style={getRarityBadgeStyle(rollResult.wonItem.rarity)}
                        >
                          {getRarityLabel(rollResult.wonItem.rarity)}
                        </span>
                      </div>
                      
                      <p className="text-slate-300 text-sm mb-2">
                        {formatDisplayText(rollResult.wonItem.category)}
                      </p>
                      
                      {rollResult.wonItem.description && (
                        <p className="text-slate-400 text-xs">
                          {rollResult.wonItem.description}
                        </p>
                      )}
                    </div>
                  </motion.div>
                </div>

                {/* Already Owned Notice */}
                {rollResult.alreadyOwned && (
                  <div className="p-3 bg-blue-500/10 border border-blue-500/30 rounded-lg">
                    <p className="text-blue-400 text-sm text-center">
                      💡 <strong>Note:</strong> You already owned this item, so no duplicate was added to your inventory.
                    </p>
                  </div>
                )}

                {/* Action Button */}
                <div className="flex justify-center">
                  <button
                    onClick={handleClaimAndClose}
                    className="px-8 py-3 bg-green-600 hover:bg-green-500 text-white rounded-lg transition-colors flex items-center space-x-2"
                  >
                    <FaGift size={16} />
                    <span>Claim Reward</span>
                  </button>
                </div>
              </motion.div>
            )}
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
} 