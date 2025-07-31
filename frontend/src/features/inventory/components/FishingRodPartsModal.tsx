import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { HiOutlineX } from 'react-icons/hi';
import { ShopItem } from '@/types/inventory';
import { GiFishingPole, GiSewingString, GiFishingHook, GiGearStick } from 'react-icons/gi';
import { PiFilmReel, PiHandPalm } from 'react-icons/pi';
import { ConfirmationModal } from '@/components/ui/ConfirmationModal';
import { FaCoins } from 'react-icons/fa';
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
}

const RARITY_COSTS: Record<string, number> = {
  COMMON: 60,
  UNCOMMON: 280,
  RARE: 1450,
  EPIC: 6200,
  LEGENDARY: 30000,
};

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
}) => {
  const [isConfirmModalOpen, setConfirmModalOpen] = useState(false);
  const [partToEquip, setPartToEquip] = useState<ShopItem | null>(null);

  if (!isOpen || !rod) return null;

  const handleEquipClick = (part: ShopItem) => {
    setPartToEquip(part);
    setConfirmModalOpen(true);
  };

  const handleConfirmEquip = () => {
    if (partToEquip && rod.instanceId) {
      onEquipPart(rod.instanceId, partToEquip.instanceId!);
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

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black bg-opacity-70 z-50 flex justify-center items-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 m-4 max-h-[90vh] w-full max-w-4xl"
        >
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-xl font-semibold text-white flex items-center">
              {rod.name}
              {rod.level && (
                <span className="ml-3 text-sm font-bold text-white bg-amber-500 px-2.5 py-1 rounded-full shadow-md">
                  LVL {rod.level}
                </span>
              )}
            </h2>
            <button onClick={onClose} className="text-slate-400 hover:text-white">
              <HiOutlineX size={24} />
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Left side: Rod Slots */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-4">
              <div className="space-y-3">
                {partSlots.map(({ type, name }) => {
                  const equippedPart = equippedPartsMap[type];
                  const Icon = partIcons[type] || GiGearStick;
                  return (
                    <div key={type} className={`bg-slate-800 p-3 rounded-md border-l-4 flex items-center justify-between ${getRarityClass(equippedPart?.rarity || 'COMMON')}`}>
                      <div className="flex items-center flex-grow">
                        <Icon className="mr-3 text-slate-400" size={20} />
                        <div className="flex-grow">
                          {equippedPart ? (
                            <>
                              <div className="flex justify-between items-center">
                                <p className="font-semibold text-white">{equippedPart.name}</p>
                                {equippedPart.maxDurability && (
                                  <span className="text-xs text-slate-400">{equippedPart.durability} / {equippedPart.maxDurability}</span>
                                )}
                              </div>
                              {equippedPart.maxDurability && (
                                <div className="w-full bg-slate-700 rounded-full h-1.5 mt-1">
                                  <div
                                    className={`${getDurabilityColor(equippedPart.durability, equippedPart.maxDurability)} h-1.5 rounded-full`}
                                    style={{ width: `${(equippedPart.durability || 0) / (equippedPart.maxDurability || 1) * 100}%` }}
                                  ></div>
                                </div>
                              )}
                              {equippedPart.repairCount != null && equippedPart.repairCount > 0 && (
                                <p className="text-xs text-slate-400 mt-1">
                                  Repairs: {equippedPart.repairCount} / {equippedPart.maxRepairs}
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
                        <>
                          {equippedPart.durability === 0 && (
                            (equippedPart.maxRepairs != null && (equippedPart.repairCount || 0) >= equippedPart.maxRepairs) ? (
                              <button
                                onClick={() => onUnequipPart(rod, equippedPart)}
                                className="ml-4 px-3 py-1 bg-red-600 hover:bg-red-700 text-white text-xs font-semibold rounded-md transition-colors"
                              >
                                Max Repairs
                              </button>
                            ) : (
                              <button
                                onClick={() => onRepairPart(equippedPart)}
                                className="ml-4 px-3 py-1 bg-green-600 hover:bg-green-700 text-white text-xs font-semibold rounded-md transition-colors"
                              >
                                Repair
                              </button>
                            )
                          )}
                        </>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Right side: Parts Inventory */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-4">
              <div className="space-y-2 max-h-80 overflow-y-auto pr-2">
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
                        const cost = RARITY_COSTS[part.rarity] || 0;
                        return (
                        <div key={part.instanceId} className={`bg-slate-800 p-2 rounded-md flex items-center justify-between mb-2 border-l-4 ${getRarityClass(part.rarity)}`}>
                          <div>
                            <p className="font-medium text-white">{part.name}</p>
                            <p className="text-xs text-slate-400">{part.description}</p>
                          </div>
                          <button
                            onClick={() => handleEquipClick(part)}
                            className="px-3 py-1 bg-primary/80 hover:bg-primary text-white text-xs font-semibold rounded-md transition-colors"
                            disabled={!!equippedPartsMap[type]}
                          >
                            Equip ( <FaCoins className="inline-block -mt-px mr-1" />{cost} )
                          </button>
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
          partToEquip ? (
            <div className="text-center">
              <p>Are you sure you want to equip this? This part cannot be unequipped.</p>
              <div className="flex items-center justify-center mt-4 text-lg">
                  <FaCoins className="mr-2 text-yellow-400" />
                  <span className="font-semibold text-white">{new Intl.NumberFormat().format(RARITY_COSTS[partToEquip.rarity] || 0)}</span>
                  <span className="ml-1.5 text-slate-300 text-base">credits</span>
              </div>
            </div>
          ) : (
            'Are you sure you want to equip this? You won\'t be able to unequip this part.'
          )
        }
      />
    </AnimatePresence>
  );
}; 