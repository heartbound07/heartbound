import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { HiOutlineX } from 'react-icons/hi';
import { ShopItem } from '@/types/inventory';
import { GiFishingPole } from 'react-icons/gi';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';

interface FishingRodPartsModalProps {
  isOpen: boolean;
  onClose: () => void;
  rod: ShopItem | null;
  parts: ShopItem[];
  onEquipPart: (rodId: string, partId: string) => void;
}

export const FishingRodPartsModal: React.FC<FishingRodPartsModalProps> = ({
  isOpen,
  onClose,
  rod,
  parts,
  onEquipPart,
}) => {
  if (!isOpen || !rod) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black bg-opacity-70 z-50 flex justify-center items-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 m-4 max-h-[90vh] w-full max-w-2xl"
        >
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-xl font-semibold text-white flex items-center">
              <GiFishingPole className="mr-2 text-primary" size={22} />
              Customize: {rod.name}
            </h2>
            <button onClick={onClose} className="text-slate-400 hover:text-white">
              <HiOutlineX size={24} />
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Left side: Rod Stats */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-4">
              <h3 className="text-lg font-medium text-slate-200 mb-4">Rod Stats</h3>
              <div className="space-y-3">
                {/* Durability */}
                <div>
                  <div className="flex justify-between text-sm text-slate-300 mb-1">
                    <span>Durability</span>
                    <span>{rod.durability} / {rod.maxDurability}</span>
                  </div>
                  <div className="w-full bg-slate-700 rounded-full h-2.5">
                    <div
                      className="bg-green-500 h-2.5 rounded-full"
                      style={{ width: `${(rod.durability || 0) / (rod.maxDurability || 1) * 100}%` }}
                    ></div>
                  </div>
                </div>
                {/* Experience */}
                <div>
                  <div className="flex justify-between text-sm text-slate-300 mb-1">
                    <span>Experience</span>
                    <span>{rod.experience} XP</span>
                  </div>
                  <div className="w-full bg-slate-700 rounded-full h-2.5">
                    <div
                      className="bg-sky-500 h-2.5 rounded-full"
                      style={{ width: `50%` }} // Placeholder
                    ></div>
                  </div>
                </div>
              </div>
            </div>

            {/* Right side: Parts Inventory */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-4">
                <h3 className="text-lg font-medium text-slate-200 mb-4">Available Parts</h3>
                <div className="space-y-2 max-h-64 overflow-y-auto pr-2">
                    {parts.length > 0 ? (
                        parts.map(part => (
                            <div key={part.id} className="bg-slate-800 p-2 rounded-md flex items-center justify-between">
                                <div>
                                    <p className="font-medium text-white">{part.name}</p>
                                    <p className="text-xs text-slate-400">{part.description}</p>
                                </div>
                                <button
                                    onClick={() => onEquipPart(rod.instanceId!, part.id)}
                                    className="px-3 py-1 bg-primary/80 hover:bg-primary text-white text-xs font-semibold rounded-md transition-colors"
                                >
                                    Equip
                                </button>
                            </div>
                        ))
                    ) : (
                        <p className="text-slate-400 text-sm text-center py-8">No fishing rod parts in your inventory.</p>
                    )}
                </div>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}; 