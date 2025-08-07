import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { HiOutlineX } from 'react-icons/hi';
import { ShopItem } from '@/types/inventory';
import { GiFishingPole, GiSewingString, GiFishingHook, GiGearStick } from 'react-icons/gi';
import { PiFilmReel, PiHandPalm } from 'react-icons/pi';
import { ConfirmationModal } from '@/components/ui/ConfirmationModal';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/valorant/popover';

interface FishingRodPartsModalProps {
  isOpen: boolean;
  onClose: () => void;
  rod: ShopItem | null;
  parts: ShopItem[];
  onEquipPart: (rodId: string, partInstanceId: string) => void;
  onRepairPart: (part: ShopItem) => void;
  onUnequipPart: (rod: ShopItem, part: ShopItem) => void;
  onUnequipPartKeep: (rod: ShopItem, part: ShopItem) => void;
  actionInProgress?: string | null;
}



const partIcons: Record<string, React.ElementType> = {
  ROD_SHAFT: GiFishingPole,
  REEL: PiFilmReel,
  FISHING_LINE: GiSewingString,
  HOOK: GiFishingHook,
  GRIP: PiHandPalm,
};

const partSlots: Array<{ type: string; name: string }> = [
  { type: 'ROD_SHAFT', name: 'Rod Shaft' },
  { type: 'REEL', name: 'Reel' },
  { type: 'FISHING_LINE', name: 'Fishing Line' },
  { type: 'HOOK', name: 'Hook' },
  { type: 'GRIP', name: 'Grip' },
];

const partTypeDescriptions: Record<string, string> = {
  ROD_SHAFT: 'Increases the durability of your rod.',
  REEL: 'Bonus loot chance (credits, parts, etc.)',
  FISHING_LINE: 'Percentage increase to credit or XP rewards',
  HOOK: 'Chance of catching higher-tier fish/items',
  GRIP: 'Resistance to crab snips.',
};

export const FishingRodPartsModal: React.FC<FishingRodPartsModalProps> = ({
  isOpen,
  onClose,
  rod,
  parts,
  onEquipPart,
  onRepairPart,
  onUnequipPart,
  onUnequipPartKeep,
  actionInProgress,
}) => {
  const [isConfirmModalOpen, setConfirmModalOpen] = useState(false);
  const [partToEquip, setPartToEquip] = useState<ShopItem | null>(null);
  const [isUnequipConfirmModalOpen, setUnequipConfirmModalOpen] = useState(false);
  const [partToUnequip, setPartToUnequip] = useState<ShopItem | null>(null);
  const [unequipAction, setUnequipAction] = useState<'remove' | 'keep'>('keep');

  if (!isOpen || !rod) return null;

  const handleEquipClick = (part: ShopItem) => {
    setPartToEquip(part);
    setConfirmModalOpen(true);
  };

  const handleConfirmEquip = () => {
    if (partToEquip && rod.instanceId) {
      onEquipPart(rod.instanceId, partToEquip.instanceId!);
      // Clear state after action
      setConfirmModalOpen(false);
      setPartToEquip(null);
    }
  };

  const handleUnequipClick = (part: ShopItem, action: 'remove' | 'keep') => {
    setPartToUnequip(part);
    setUnequipAction(action);
    setUnequipConfirmModalOpen(true);
  };

  const handleConfirmUnequip = () => {
    if (partToUnequip && rod) {
      if (unequipAction === 'remove') {
        onUnequipPart(rod, partToUnequip);
      } else {
        onUnequipPartKeep(rod, partToUnequip);
      }
      // Clear state after action
      setUnequipConfirmModalOpen(false);
      setPartToUnequip(null);
      setUnequipAction('keep');
    }
  };

  const getRarityClass = (rarity: string) => {
    switch (rarity) {
      case 'UNCOMMON': return 'border-green-500';
      case 'RARE': return 'border-blue-500';
      case 'EPIC': return 'border-purple-500';
      case 'LEGENDARY': return 'border-amber-400';
      default: return 'border-slate-600';
    }
  };
  
  const getDurabilityColor = (durability?: number, maxDurability?: number) => {
    if (durability === undefined || maxDurability === undefined || maxDurability === 0) {
      return 'bg-slate-500';
    }
    const percentage = (durability / maxDurability) * 100;
    if (percentage > 50) return 'bg-green-500';
    if (percentage > 20) return 'bg-yellow-500';
    return 'bg-red-500';
  };

  const equippedPartsMap = rod.equippedParts || {};

  // Check if any actions are in progress to prevent race conditions
  const isActionInProgress = Boolean(actionInProgress);

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black bg-opacity-70 z-50 flex justify-center items-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-4 md:p-6 m-2 md:m-4 max-h-[95vh] md:max-h-[90vh] w-full max-w-4xl overflow-hidden flex flex-col"
        >
          <div className="flex justify-between items-center mb-4">
            <button onClick={onClose} className="text-slate-400 hover:text-white">
              <HiOutlineX size={24} />
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-6 flex-1 min-h-0 overflow-hidden">
            {/* Left side: Rod Slots */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-4 flex flex-col min-h-0">
              <div className="space-y-3 flex-1 overflow-y-auto">
                {partSlots.map(({ type, name }) => {
                  const equippedPart = equippedPartsMap[type];
                  const Icon = partIcons[type] || GiGearStick;
                  
                  // Determine if part should be removed (broken and at max repairs)
                  const shouldRemovePart = equippedPart?.durability === 0 && 
                    equippedPart?.maxRepairs != null && 
                    (equippedPart?.repairCount || 0) >= equippedPart.maxRepairs;
                  return (
                    <div key={type} className={`bg-slate-800 p-3 rounded-md border-l-4 flex items-end justify-between ${getRarityClass(equippedPart?.rarity || 'COMMON')}`}>
                      <div className="flex items-center flex-grow">
                        <Icon className="mr-3 text-slate-400" size={20} />
                        <div className="flex-grow mr-3">
                          {equippedPart ? (
                            <>
                              <div className="flex justify-between items-center">
                                <p className="font-semibold text-white">{equippedPart.name}</p>
                                {equippedPart.maxDurability && equippedPart.fishingRodPartType !== 'ROD_SHAFT' && (
                                  <span className="text-xs text-slate-400">{equippedPart.durability} / {equippedPart.maxDurability}</span>
                                )}
                              </div>
                              {equippedPart.maxDurability && equippedPart.fishingRodPartType !== 'ROD_SHAFT' && (
                                <div className="w-full bg-slate-700 rounded-full h-1.5 mt-1">
                                  <div
                                    className={`${getDurabilityColor(equippedPart.durability, equippedPart.maxDurability)} h-1.5 rounded-full`}
                                    style={{ width: `${(equippedPart.durability || 0) / (equippedPart.maxDurability || 1) * 100}%` }}
                                  ></div>
                                </div>
                              )}
                              {equippedPart.repairCount != null && equippedPart.repairCount > 0 && equippedPart.fishingRodPartType !== 'ROD_SHAFT' && (
                                <p className="text-xs text-slate-400 mt-1">
                                  Repairs: {equippedPart.repairCount} / {equippedPart.maxRepairs ?? '∞'}
                                </p>
                              )}
                            </>
                          ) : (
                            <>
                              <p className="font-semibold text-white">{name}</p>
                              <p className="text-sm text-slate-500 italic">Empty Slot</p>
                            </>
                          )}
                        </div>
                      </div>
                      {equippedPart && rod && (
                        <div className="flex items-center space-x-2 flex-shrink-0">
                          {equippedPart.durability === 0 && (
                            equippedPart.maxRepairs != null && (equippedPart.repairCount || 0) < equippedPart.maxRepairs && (
                              <button
                                onClick={() => onRepairPart(equippedPart)}
                                disabled={isActionInProgress}
                                className="px-3 py-1 bg-green-600 hover:bg-green-700 disabled:bg-gray-500 disabled:cursor-not-allowed text-white text-xs font-semibold rounded-md transition-colors"
                              >
                                Repair
                              </button>
                            )
                          )}
                          {shouldRemovePart ? (
                            <button
                              onClick={() => handleUnequipClick(equippedPart, 'remove')}
                              disabled={isActionInProgress}
                              className="px-3 py-1 bg-red-600 hover:bg-red-700 disabled:bg-gray-500 disabled:cursor-not-allowed text-white text-xs font-semibold rounded-md transition-colors"
                            >
                              Remove
                            </button>
                          ) : (
                            <button
                              onClick={() => handleUnequipClick(equippedPart, 'keep')}
                              disabled={isActionInProgress}
                              className="px-3 py-1 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-500 disabled:cursor-not-allowed text-white text-xs font-semibold rounded-md transition-colors"
                            >
                              Unequip
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Right side: Parts Inventory */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-4 flex flex-col min-h-0">
              <div className="space-y-2 flex-1 max-h-60 md:max-h-80 overflow-y-auto pr-2">
                {partSlots.map(({ type, name }) => (
                  <div key={`inventory-${type}`}>
                    <div className="flex items-center space-x-2">
                      <h4 className="text-md font-semibold text-slate-400 my-2">{name}</h4>
                      <Popover>
                        <PopoverTrigger asChild>
                          <button className="flex items-center justify-center text-slate-400 hover:text-white transition-colors">
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                              <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                          </button>
                        </PopoverTrigger>
                        <PopoverContent>
                          <p>{partTypeDescriptions[type]}</p>
                        </PopoverContent>
                      </Popover>
                    </div>
                    {parts
                      .filter(p => p.fishingRodPartType === type)
                      .map(part => {
                        return (
                        <div key={part.instanceId} className={`bg-slate-800 p-2 rounded-md flex items-center justify-between mb-2 border-l-4 ${getRarityClass(part.rarity)}`}>
                          <div className="flex-grow">
                            <div className="flex justify-between items-center">
                              <p className="font-medium text-white">{part.name}</p>
                              {part.maxDurability && part.fishingRodPartType !== 'ROD_SHAFT' && (
                                <span className="text-xs text-slate-400">{part.durability} / {part.maxDurability}</span>
                              )}
                            </div>
                            {part.maxDurability && part.fishingRodPartType !== 'ROD_SHAFT' && (
                              <div className="w-full bg-slate-700 rounded-full h-1.5 mt-1">
                                <div
                                  className={`${getDurabilityColor(part.durability, part.maxDurability)} h-1.5 rounded-full`}
                                  style={{ width: `${(part.durability || 0) / (part.maxDurability || 1) * 100}%` }}
                                ></div>
                              </div>
                            )}
                            
                            {/* Stats for fishing rod parts */}
                            <div className="mt-2 space-y-1">
                              {/* Fortune stat */}
                              {((part as any).bonusLootChance || 0) > 0 && (
                                <div className="flex items-center space-x-2">
                                  <span className="text-xs text-slate-400">Fortune</span>
                                  <span className="text-xs font-semibold text-yellow-400">+{((part as any).bonusLootChance || 0).toFixed(0)}%</span>
                                </div>
                              )}
                              
                              {/* Rarity stat */}
                              {((part as any).rarityChanceIncrease || 0) > 0 && (
                                <div className="flex items-center space-x-2">
                                  <span className="text-xs text-slate-400">Rarity</span>
                                  <span className="text-xs font-semibold text-purple-400">+{((part as any).rarityChanceIncrease || 0).toFixed(0)}%</span>
                                </div>
                              )}
                              
                              {/* Reward Boost stat */}
                              {((part as any).multiplierIncrease || 0) > 0 && (
                                <div className="flex items-center space-x-2">
                                  <span className="text-xs text-slate-400">Reward Boost</span>
                                  <span className="text-xs font-semibold text-cyan-400">+{((part as any).multiplierIncrease || 0).toFixed(1)}x</span>
                                </div>
                              )}
                              
                              {/* Stability stat */}
                              {((part as any).negationChance || 0) > 0 && (
                                <div className="flex items-center space-x-2">
                                  <span className="text-xs text-slate-400">Stability</span>
                                  <span className="text-xs font-semibold text-red-500">+{((part as any).negationChance || 0).toFixed(0)}%</span>
                                </div>
                              )}
                            </div>
                          </div>
                          {!equippedPartsMap[type] && (
                            <button
                              onClick={() => handleEquipClick(part)}
                              disabled={isActionInProgress}
                              className="ml-4 px-3 py-1 bg-primary/80 hover:bg-primary disabled:bg-gray-500 disabled:cursor-not-allowed text-white text-xs font-semibold rounded-md transition-colors"
                            >
                              Equip
                            </button>
                          )}
                        </div>
                      )})}
                    {parts.filter(p => p.fishingRodPartType === type).length === 0 && (
                      <p className="text-slate-500 text-sm italic">No available parts of this type.</p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </motion.div>
      </div>
      <ConfirmationModal
        isOpen={isConfirmModalOpen}
        onClose={() => setConfirmModalOpen(false)}
        onConfirm={handleConfirmEquip}
        message={
          'Are you sure you want to equip this fishing rod part?'
        }
      />
      <ConfirmationModal
        isOpen={isUnequipConfirmModalOpen}
        onClose={() => setUnequipConfirmModalOpen(false)}
        onConfirm={handleConfirmUnequip}
        message={
          partToUnequip && partToUnequip.durability === 0 && 
          partToUnequip.maxRepairs != null && 
          (partToUnequip.repairCount || 0) >= partToUnequip.maxRepairs ? (
            <div className="text-center">
              <p>Are you sure you want to remove this part?</p>
              <p className="mt-2 text-red-400 text-sm font-semibold">
                This part has reached the max repair limit and is broken!
              </p>
              <p className="mt-1 text-orange-400 text-sm font-semibold">
                ⚠️ This action will permanently destroy the part.
              </p>
            </div>
          ) : (
            <div className="text-center">
              <p>Are you sure you want to unequip this part?</p>
              <p className="mt-2 text-slate-400 text-sm">
                You will need to pay credits again to re-equip it.
              </p>
            </div>
          )
        }
      />
    </AnimatePresence>
  );
}; 