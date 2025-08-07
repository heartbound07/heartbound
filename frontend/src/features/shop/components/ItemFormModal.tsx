import React from 'react';
import { 
    HiOutlineCheck, 
    HiOutlineCollection, 
    HiOutlineColorSwatch,
    HiOutlineTag,
    HiOutlineCash,
    HiOutlineExclamation,
    HiOutlineCalendar,
    HiOutlineStar,
    HiOutlineX,
    HiOutlinePlus
  } from 'react-icons/hi';
import { ImageUpload } from '@/components/ui/shop/ImageUpload';
import { BadgeUpload } from '@/components/ui/shop/BadgeUpload';
import NameplatePreview from '@/components/NameplatePreview';

// Keep the interface definitions in sync with ShopAdminPage.tsx
interface ShopItem {
    id: string;
    name: string;
    description: string;
    price: number;
    category: string;
    imageUrl: string;
    thumbnailUrl?: string;
    requiredRole: string | null;
    expiresAt: string | null;
    active: boolean;
    expired: boolean;
    isDeleting?: boolean;
    discordRoleId?: string;
    rarity: string;
    isFeatured: boolean;
    isDaily: boolean;
    fishingRodMultiplier?: number;
    gradientEndColor?: string;
    maxCopies?: number;
    copiesSold?: number;
    maxDurability?: number;
    durabilityIncrease?: number;
    fishingRodPartType?: string;
  }
  
  interface ShopFormData {
    name: string;
    description: string;
    price: number;
    category: string;
    imageUrl: string;
    thumbnailUrl?: string;
    requiredRole: string | null;
    expiresAt: string | null;
    active: boolean;
    discordRoleId?: string;
    rarity: string;
    isFeatured: boolean;
    isDaily: boolean;
    fishingRodMultiplier?: number;
    fishingRodPartType?: string;
    colorType: 'solid' | 'gradient';
    gradientEndColor?: string;
    maxCopies?: number;
    maxDurability?: number;
    durabilityIncrease?: number;
    bonusLootChance?: number;
    rarityChanceIncrease?: number;
    multiplierIncrease?: number;
    negationChance?: number;
    maxRepairs?: number;
  }
  
  interface CaseItemData {
    id?: string;
    containedItem: ShopItem;
    dropRate: number;
  }
  
  interface CaseContents {
    items: CaseItemData[];
    totalDropRate: number;
  }
  
  interface ItemFormModalProps {
    isOpen: boolean;
    onClose: () => void;
    formData: ShopFormData;
    setFormData: React.Dispatch<React.SetStateAction<ShopFormData>>;
    handleInputChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => void;
    handleCheckboxChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
    handleSubmit: (e: React.FormEvent) => Promise<void>;
    editingItem: ShopItem | null;
    submitting: boolean;
    categories: string[];
    roles: string[];
    rarities: string[];
    fishingRodParts: string[];
    handleImageUpload: (url: string) => void;
    handleImageRemove: () => void;
    // Case related props
    caseContents: CaseContents;
    availableItems: ShopItem[];
    loadingCaseContents: boolean;
    addCaseItem: () => void;
    removeCaseItem: (index: number) => void;
    updateCaseItemDropRate: (index: number, dropRate: number) => void;
    updateCaseItemSelection: (index: number, selectedItemId: string) => void;
    saveCaseContents: () => Promise<void>;
  }

// Function to format category display
const formatCategoryDisplay = (item: ShopItem): string => {
  if (item.category === 'FISHING_ROD_PART' && item.fishingRodPartType) {
    return item.fishingRodPartType;
  }
  return item.category;
};

const ItemFormModal: React.FC<ItemFormModalProps> = ({
    isOpen,
    onClose,
    formData,
    setFormData,
    handleInputChange,
    handleCheckboxChange,
    handleSubmit,
    editingItem,
    submitting,
    categories,
    roles,
    rarities,
    fishingRodParts,
    handleImageUpload,
    handleImageRemove,
    caseContents,
    availableItems,
    loadingCaseContents,
    addCaseItem,
    removeCaseItem,
    updateCaseItemDropRate,
    updateCaseItemSelection,
    saveCaseContents
}) => {
    if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-70 z-40 flex justify-center items-center">
        <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 m-4 max-h-[90vh] overflow-y-auto transition-all duration-300 hover:shadow-xl w-full max-w-4xl">
            <div className="flex justify-between items-center mb-6">
                <h2 className="text-xl font-semibold text-white flex items-center">
                    <HiOutlineTag className="text-primary mr-2" size={20} />
                    {editingItem ? 'Edit Item' : 'Create New Item'}
                </h2>
                <button onClick={onClose} className="text-slate-400 hover:text-white">
                    <HiOutlineX size={24} />
                </button>
            </div>
            
            <form onSubmit={handleSubmit}>
            <div className="space-y-6">
            {/* Basic Information */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
              <h3 className="text-md font-medium text-slate-200 mb-4 flex items-center">
                <HiOutlineCollection className="mr-2 text-primary" size={18} />
                Basic Information
              </h3>
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    Name
                  </label>
                  <input
                    type="text"
                    name="name"
                    value={formData.name}
                    onChange={handleInputChange}
                    required
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  />
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    Price (Credits)
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                      <HiOutlineCash className="text-slate-400" size={16} />
                    </div>
                    <input
                      type="number"
                      name="price"
                      value={formData.price}
                      onChange={handleInputChange}
                      required
                      min="0"
                      step="0.01"
                      className="w-full bg-slate-800 border border-slate-700 rounded-md pl-9 pr-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                    />
                  </div>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    Max Copies (Optional)
                  </label>
                  <input
                    type="number"
                    name="maxCopies"
                    value={formData.maxCopies || ''}
                    onChange={handleInputChange}
                    min="0"
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  />
                  <p className="text-xs text-slate-400 mt-1">
                    Set a maximum number of copies for this item. Leave empty for unlimited.
                  </p>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    Category
                  </label>
                  <select
                    name="category"
                    value={formData.category}
                    onChange={handleInputChange}
                    required
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  >
                    <option value="">Select Category</option>
                    {categories.map(category => (
                      <option key={category} value={category}>{category}</option>
                    ))}
                  </select>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    Required Role (Optional)
                  </label>
                  <select
                    name="requiredRole"
                    value={formData.requiredRole || ''}
                    onChange={handleInputChange}
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  >
                    <option value="">None (Available to Everyone)</option>
                    {roles.map(role => (
                      <option key={role} value={role}>{role}</option>
                    ))}
                  </select>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    Rarity
                  </label>
                  <select
                    id="rarity"
                    name="rarity"
                    value={formData.rarity}
                    onChange={handleInputChange}
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  >
                    {rarities.map(rarity => (
                      <option key={rarity} value={rarity}>
                        {rarity.charAt(0) + rarity.slice(1).toLowerCase()}
                      </option>
                    ))}
                  </select>
                  <p className="text-xs text-slate-400 mt-1">
                    The rarity level affects the item's border color and badge in the shop.
                  </p>
                </div>
                
                {formData.category === 'FISHING_ROD_PART' && (
                  <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">
                      Part Type
                    </label>
                    <select
                      name="fishingRodPartType"
                      value={formData.fishingRodPartType || ''}
                      onChange={handleInputChange}
                      required
                      className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                    >
                      <option value="">Select Part Type</option>
                      {fishingRodParts.map(part => (
                        <option key={part} value={part}>{part}</option>
                      ))}
                    </select>
                  </div>
                )}

                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">
                    <div className="flex items-center">
                      <HiOutlineCalendar className="mr-1.5 text-slate-400" size={16} />
                      Expires At (Optional)
                    </div>
                  </label>
                  <input
                    type="datetime-local"
                    name="expiresAt"
                    value={formData.expiresAt || ''}
                    onChange={handleInputChange}
                    className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                  />
                  <p className="text-xs text-slate-400 mt-1">
                    Leave empty for items that never expire
                  </p>
                </div>
              </div>
              
              <div className="mt-4">
                <label className="flex items-center space-x-2 text-sm font-medium text-slate-300">
                  <input
                    type="checkbox"
                    name="active"
                    checked={formData.active}
                    onChange={handleCheckboxChange}
                    className="w-4 h-4 rounded text-primary focus:ring-primary bg-slate-800 border-slate-600"
                  />
                  <span>Active</span>
                </label>
                <p className="text-xs text-slate-400 mt-1 ml-6">
                  {editingItem?.expired ? 
                    "This item has expired. You can reactivate it by setting a new expiration date or removing the expiration." : 
                    "Inactive items won't be visible in the shop."
                  }
                </p>
              </div>
            </div>
            
            {/* Visibility Section */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
              <h3 className="text-md font-medium text-slate-200 mb-4 flex items-center">
                <HiOutlineStar className="mr-2 text-primary" size={18} />
                Visibility Settings
              </h3>
              
              <div className="space-y-3">
                <div>
                  <label className="flex items-center space-x-2 text-sm font-medium text-slate-300">
                    <input
                      type="checkbox"
                      name="isFeatured"
                      checked={formData.isFeatured}
                      onChange={handleCheckboxChange}
                      className="w-4 h-4 rounded text-primary focus:ring-primary bg-slate-800 border-slate-600"
                    />
                    <span>Featured Item</span>
                  </label>
                  <p className="text-xs text-slate-400 mt-1 ml-6">
                    Featured items appear in the left section of the shop page (max 3 items shown vertically).
                  </p>
                </div>
                
                <div>
                  <label className="flex items-center space-x-2 text-sm font-medium text-slate-300">
                    <input
                      type="checkbox"
                      name="isDaily"
                      checked={formData.isDaily}
                      onChange={handleCheckboxChange}
                      className="w-4 h-4 rounded text-primary focus:ring-primary bg-slate-800 border-slate-600"
                    />
                    <span>Eligible for Daily Items Pool</span>
                  </label>
                  <p className="text-xs text-slate-400 mt-1 ml-6">
                    If checked, this item can be randomly selected for a user's personalized daily items.
                  </p>
                </div>
              </div>
            </div>
            
            {/* Category-Specific Properties */}
            {formData.category === 'FISHING_ROD' && (
              <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
                <h3 className="text-md font-medium text-slate-200 mb-4 flex items-center">
                  <HiOutlineColorSwatch className="mr-2 text-primary" size={18} />
                  Fishing Rod Properties
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                  <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">
                      Max Durability
                    </label>
                    <input
                      type="number"
                      name="maxDurability"
                      value={formData.maxDurability || ''}
                      onChange={handleInputChange}
                      min="1"
                      className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
                      placeholder="e.g., 100"
                    />
                    <p className="text-xs text-slate-400 mt-1">
                      The starting and maximum durability for this fishing rod.
                    </p>
                  </div>
                </div>
              </div>
            )}

            {formData.category === 'FISHING_ROD_PART' && (
              <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
                <h3 className="text-md font-medium text-slate-200 mb-4 flex items-center">
                  <HiOutlineColorSwatch className="mr-2 text-primary" size={18} />
                  Part Properties
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                    {formData.fishingRodPartType === 'ROD_SHAFT' && (
                      <div className="col-span-full">
                        <div className="p-3 bg-blue-500/10 border border-blue-500/30 rounded-md">
                          <p className="text-blue-200 text-sm">
                            <strong>ROD_SHAFT parts have infinite durability.</strong> They don't need Max Durability or Max Repairs settings since they never break and cannot be repaired.
                          </p>
                        </div>
                      </div>
                    )}
                    {formData.fishingRodPartType !== 'ROD_SHAFT' && (
                      <div>
                          <label className="block text-sm font-medium text-slate-300 mb-1">
                              Max Durability
                          </label>
                          <input
                              type="number"
                              name="maxDurability"
                              value={formData.maxDurability || ''}
                              onChange={handleInputChange}
                              min="1"
                              className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
                              placeholder="e.g., 100"
                          />
                           <p className="text-xs text-slate-400 mt-1">
                             The starting and maximum durability for this part.
                           </p>
                      </div>
                    )}
                  <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">
                      Durability Increase
                    </label>
                    <input
                      type="number"
                      name="durabilityIncrease"
                      value={formData.durabilityIncrease || ''}
                      onChange={handleInputChange}
                      min="1"
                      className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
                      placeholder="e.g., 50"
                    />
                    <p className="text-xs text-slate-400 mt-1">
                      Increases the rod's max durability by this amount.
                    </p>
                  </div>
                  {formData.fishingRodPartType !== 'ROD_SHAFT' && (
                    <div>
                      <label className="block text-sm font-medium text-slate-300 mb-1">
                        Max Repairs
                      </label>
                      <input
                        type="number"
                        name="maxRepairs"
                        value={formData.maxRepairs || ''}
                        onChange={handleInputChange}
                        min="0"
                        className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary"
                        placeholder="e.g., 5"
                      />
                      <p className="text-xs text-slate-400 mt-1">
                        Number of times this part can be repaired. Leave empty for infinite.
                      </p>
                    </div>
                  )}
                  {formData.fishingRodPartType === 'REEL' && (
                    <div>
                      <label className="block text-sm font-medium text-slate-300 mb-1">Bonus Loot Chance (%)</label>
                      <input type="number" name="bonusLootChance" value={formData.bonusLootChance || ''} onChange={handleInputChange} min="0" max="100" step="any" className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary" placeholder="e.g., 5"/>
                      <p className="text-xs text-slate-400 mt-1">Percentage chance to grant bonus loot on a successful catch.</p>
                    </div>
                  )}
                  {formData.fishingRodPartType === 'HOOK' && (
                    <div>
                      <label className="block text-sm font-medium text-slate-300 mb-1">Rarity Chance Increase (%)</label>
                      <input type="number" name="rarityChanceIncrease" value={formData.rarityChanceIncrease || ''} onChange={handleInputChange} min="0" max="100" step="any" className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary" placeholder="e.g., 2"/>
                      <p className="text-xs text-slate-400 mt-1">Increases the chance to catch higher-rarity fish.</p>
                    </div>
                  )}
                  {formData.fishingRodPartType === 'FISHING_LINE' && (
                    <div>
                      <label className="block text-sm font-medium text-slate-300 mb-1">Added Multiplier</label>
                      <input type="number" name="multiplierIncrease" value={formData.multiplierIncrease || ''} onChange={handleInputChange} min="0" step="0.1" className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary" placeholder="e.g., 0.5"/>
                      <p className="text-xs text-slate-400 mt-1">Flat amount to add to the fishing rod's multiplier.</p>
                    </div>
                  )}
                  {formData.fishingRodPartType === 'GRIP' && (
                    <div>
                      <label className="block text-sm font-medium text-slate-300 mb-1">Negation Chance (%)</label>
                      <input type="number" name="negationChance" value={formData.negationChance || ''} onChange={handleInputChange} min="0" max="100" step="any" className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary" placeholder="e.g., 10"/>
                      <p className="text-xs text-slate-400 mt-1">Chance to negate a negative event (e.g., crab snip).</p>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Image & Appearance */}
            {formData.category !== 'FISHING_ROD_PART' && (
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
              <h3 className="text-md font-medium text-slate-200 mb-4 flex items-center">
                <HiOutlineColorSwatch className="mr-2 text-primary" size={18} />
                Item Appearance
              </h3>
              
              {formData.category === 'USER_COLOR' ? (
                <div className="space-y-5">
                   <div>
                    <label className="block text-sm font-medium text-slate-300 mb-2">
                      Color Type
                    </label>
                    <div className="flex items-center space-x-4">
                      <label className="flex items-center space-x-2 cursor-pointer">
                        <input
                          type="radio"
                          name="colorType"
                          value="solid"
                          checked={formData.colorType === 'solid'}
                          onChange={() => setFormData({ ...formData, colorType: 'solid' })}
                          className="text-primary focus:ring-primary bg-slate-700 border-slate-600"
                        />
                        <span className="text-slate-300">Solid</span>
                      </label>
                      <label className="flex items-center space-x-2 cursor-pointer">
                        <input
                          type="radio"
                          name="colorType"
                          value="gradient"
                          checked={formData.colorType === 'gradient'}
                          onChange={() => setFormData({ ...formData, colorType: 'gradient' })}
                          className="text-primary focus:ring-primary bg-slate-700 border-slate-600"
                        />
                        <span className="text-slate-300">Gradient</span>
                      </label>
                    </div>
                  </div>
                  
                  {formData.colorType === 'solid' ? (
                    <div>
                      <label htmlFor="colorPicker" className="block text-sm font-medium text-slate-300 mb-1">
                        Nameplate Color
                      </label>
                      <div className="flex items-center space-x-3">
                        <input
                          id="colorPicker"
                          type="color"
                          value={formData.imageUrl && formData.imageUrl.startsWith('#') ? formData.imageUrl : '#ffffff'}
                          onChange={(e) => setFormData({...formData, imageUrl: e.target.value})}
                          className="h-10 w-14 p-1 bg-slate-800 border border-slate-700 rounded cursor-pointer"
                        />
                        <input
                          type="text"
                          value={formData.imageUrl || ''}
                          onChange={(e) => setFormData({...formData, imageUrl: e.target.value})}
                          className="flex-1 px-3 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary"
                          placeholder="#RRGGBB Hex Color"
                        />
                        <div 
                          className="h-10 w-10 rounded border border-slate-600"
                          style={{ backgroundColor: formData.imageUrl || '#ffffff' }}
                        ></div>
                      </div>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                      <div>
                        <label className="block text-sm font-medium text-slate-300 mb-1">
                          Start Color
                        </label>
                        <div className="flex items-center space-x-3">
                          <input
                            type="color"
                            value={formData.imageUrl && formData.imageUrl.startsWith('#') ? formData.imageUrl : '#ffffff'}
                            onChange={(e) => setFormData({...formData, imageUrl: e.target.value})}
                            className="h-10 w-14 p-1 bg-slate-800 border border-slate-700 rounded cursor-pointer"
                          />
                          <input
                            type="text"
                            value={formData.imageUrl || ''}
                            onChange={(e) => setFormData({...formData, imageUrl: e.target.value})}
                            className="flex-1 px-3 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary"
                            placeholder="#RRGGBB Hex Color"
                          />
                        </div>
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-slate-300 mb-1">
                          End Color
                        </label>
                        <div className="flex items-center space-x-3">
                          <input
                            type="color"
                            value={formData.gradientEndColor && formData.gradientEndColor.startsWith('#') ? formData.gradientEndColor : '#ffffff'}
                            onChange={(e) => setFormData({...formData, gradientEndColor: e.target.value})}
                            className="h-10 w-14 p-1 bg-slate-800 border border-slate-700 rounded cursor-pointer"
                          />
                          <input
                            type="text"
                            value={formData.gradientEndColor || ''}
                            onChange={(e) => setFormData({...formData, gradientEndColor: e.target.value})}
                            className="flex-1 px-3 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary"
                            placeholder="#RRGGBB Hex Color"
                          />
                        </div>
                      </div>
                    </div>
                  )}

                  <p className="mt-1 text-xs text-slate-400">
                    Choose a color for this nameplate. The gradient will only appear on the website, not in Discord.
                  </p>
                  
                  {/* Preview section */}
                  <div className="mt-4 p-5 bg-slate-900 rounded-md border border-slate-700">
                    <h4 className="text-sm font-medium text-slate-300 mb-3 flex items-center">
                      <HiOutlineExclamation className="mr-1.5 text-yellow-400" size={16} />
                      Preview
                    </h4>
                    <NameplatePreview
                      username="Username"
                      color={formData.imageUrl}
                      endColor={formData.colorType === 'gradient' ? formData.gradientEndColor : undefined}
                      message="This is how the color will appear"
                      size="md"
                      className="bg-slate-800/80 rounded-md"
                    />
                  </div>
                </div>
              ) : formData.category === 'FISHING_ROD' ? (
                <div className="space-y-5">
                  <div>
                    <label htmlFor="fishingRodMultiplier" className="block text-sm font-medium text-slate-300 mb-1">
                      Fishing Rod Multiplier
                    </label>
                    <input
                      id="fishingRodMultiplier"
                      type="number"
                      name="fishingRodMultiplier"
                      value={formData.fishingRodMultiplier || 1.0}
                      onChange={handleInputChange}
                      min="0.1"
                      max="10.0"
                      step="0.1"
                      className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                    />
                    <p className="text-xs text-slate-400 mt-1">
                      Multiplies the credits earned when fishing (0.1x to 10.0x).
                    </p>
                  </div>
                  <div className="mt-4 p-5 bg-slate-900 rounded-md border border-slate-700">
                    <h4 className="text-sm font-medium text-slate-300 mb-3 flex items-center">
                      <HiOutlineExclamation className="mr-1.5 text-yellow-400" size={16} />
                      Multiplier Preview
                    </h4>
                    <p className="text-sm text-slate-300">
                      A <strong>{formData.fishingRodMultiplier}x</strong> rod means a <strong>20</strong> credit fish becomes <strong>{20 * (formData.fishingRodMultiplier || 1)}</strong> credits.
                    </p>
                  </div>
                </div>
              ) : (
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-2">
                    Item Image
                  </label>
                  <ImageUpload 
                    currentImageUrl={formData.imageUrl}
                    onUpload={handleImageUpload}
                    onRemove={handleImageRemove}
                    showRemoveButton={!!formData.imageUrl}
                    className="mb-2"
                  />
                  <p className="text-xs text-slate-400 mt-2">
                    Click to upload an image for this shop item. Supported formats: JPG, PNG, GIF, WebP (max 5MB)
                  </p>
                </div>
              )}
            </div>
            )}
            
            {/* In the form, add this conditional thumbnail upload field */}
            {formData.category === 'BADGE' && (
              <div className="mb-6">
                <label className="mb-2 block text-sm font-medium text-white">
                  Badge Image (Circle Format)
                </label>
                <p className="mb-2 text-xs text-white/60">
                  This circular image will be shown on user profiles when the badge is equipped.
                </p>
                <BadgeUpload
                  currentImageUrl={formData.thumbnailUrl || ''}
                  onUpload={(url) => setFormData({ ...formData, thumbnailUrl: url })}
                  onRemove={() => setFormData({ ...formData, thumbnailUrl: '' })}
                  showRemoveButton={!!formData.thumbnailUrl}
                />
              </div>
            )}
            
            {/* Discord Role ID input - show for USER_COLOR and BADGE categories */}
            {(formData.category === 'USER_COLOR' || formData.category === 'BADGE') && (
              <div>
                <label htmlFor="discordRoleId" className="block text-sm font-medium text-slate-300 mb-1">
                  Discord Role ID
                </label>
                <input
                  id="discordRoleId"
                  type="text"
                  value={formData.discordRoleId || ''}
                  onChange={(e) => setFormData({...formData, discordRoleId: e.target.value})}
                  className="block w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder={`Discord role ID for ${formData.category} items`}
                />
                <p className="mt-1 text-xs text-slate-400">
                  {formData.category === 'USER_COLOR' 
                    ? "Enter the Discord role ID to be granted when this color is equipped. Leave empty for no role."
                    : "Enter the Discord role ID to be granted when this badge is equipped. Leave empty for no role."}
                </p>
              </div>
            )}
            
            {/* Case Contents Management - Only show for CASE category */}
            {formData.category === 'CASE' && (
              <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
                <h3 className="text-md font-medium text-slate-200 mb-4 flex items-center">
                  <HiOutlineCollection className="mr-2 text-primary" size={18} />
                  Case Contents
                  {editingItem && (
                    <span className="ml-2 text-xs bg-blue-500/20 text-blue-300 px-2 py-1 rounded">
                      Editing Existing Case
                    </span>
                  )}
                </h3>
                
                {!editingItem && (
                  <div className="mb-4 p-3 bg-yellow-500/10 border border-yellow-500/30 rounded-md">
                    <p className="text-yellow-200 text-sm">
                      <strong>Note:</strong> Save the case item first, then edit it to configure contents.
                      Case contents can only be managed for existing cases.
                    </p>
                  </div>
                )}
                
                {editingItem && (
                  <>
                    {loadingCaseContents ? (
                      <div className="flex items-center justify-center py-8">
                        <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin mr-2"></div>
                        <span className="text-slate-300">Loading case contents...</span>
                      </div>
                    ) : (
                      <>
                        {/* Drop Rate Summary */}
                        <div className="mb-4 p-3 rounded-md border" style={{
                          backgroundColor: caseContents.totalDropRate > 0 ? 'rgb(34 197 94 / 0.1)' : 'rgb(239 68 68 / 0.1)',
                          borderColor: caseContents.totalDropRate > 0 ? 'rgb(34 197 94 / 0.3)' : 'rgb(239 68 68 / 0.3)'
                        }}>
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-medium" style={{
                              color: caseContents.totalDropRate > 0 ? 'rgb(34 197 94)' : 'rgb(239 68 68)'
                            }}>
                              Total Weight: {caseContents.totalDropRate}
                            </span>
                            {caseContents.totalDropRate > 0 ? (
                              <span className="text-xs bg-green-500/20 text-green-300 px-2 py-1 rounded">Valid</span>
                            ) : (
                              <span className="text-xs bg-red-500/20 text-red-300 px-2 py-1 rounded">
                                Must be greater than 0
                              </span>
                            )}
                          </div>
                        </div>
                        
                        {/* Case Items List */}
                        <div className="space-y-3 mb-4">
                          {caseContents.items.map((caseItem, index) => (
                            <div key={index} className="bg-slate-900/50 border border-slate-600 rounded-md p-3">
                              <div className="flex items-center space-x-3">
                                {/* Item Selection */}
                                <div className="flex-1">
                                  <label className="block text-xs text-slate-400 mb-1">Item</label>
                                  <select
                                    value={caseItem.containedItem.id}
                                    onChange={(e) => updateCaseItemSelection(index, e.target.value)}
                                    className="w-full bg-slate-800 border border-slate-700 rounded px-2 py-1 text-sm text-white focus:outline-none focus:ring-1 focus:ring-primary"
                                  >
                                    {availableItems.map(item => (
                                      <option key={item.id} value={item.id}>
                                        {item.name} ({formatCategoryDisplay(item)})
                                      </option>
                                    ))}
                                  </select>
                                </div>
                                
                                {/* Drop Rate Input */}
                                <div className="w-24">
                                  <label className="block text-xs text-slate-400 mb-1">Weight</label>
                                  <input
                                    type="number"
                                    min="0.01"
                                    step="0.01"
                                    value={caseItem.dropRate}
                                    onChange={(e) => updateCaseItemDropRate(index, parseFloat(e.target.value) || 0.01)}
                                    className="w-full bg-slate-800 border border-slate-700 rounded px-2 py-1 text-sm text-white focus:outline-none focus:ring-1 focus:ring-primary"
                                  />
                                </div>
                                
                                {/* Remove Button */}
                                <button
                                  type="button"
                                  onClick={() => removeCaseItem(index)}
                                  className="p-2 text-red-400 hover:text-red-300 hover:bg-red-500/10 rounded transition-colors"
                                  title="Remove item from case"
                                >
                                  <HiOutlineX size={16} />
                                </button>
                              </div>
                              
                              {/* Item Preview */}
                              <div className="mt-2 flex items-center space-x-2 text-xs text-slate-400">
                                <span>Price: {caseItem.containedItem.price} credits</span>
                                <span>•</span>
                                <span>Rarity: {caseItem.containedItem.rarity}</span>
                              </div>
                            </div>
                          ))}
                          
                          {caseContents.items.length === 0 && (
                            <div className="text-center py-8 text-slate-400">
                              <p>No items in this case yet.</p>
                              <p className="text-sm">Click "Add Item" to start building your case.</p>
                            </div>
                          )}
                        </div>
                        
                        {/* Action Buttons */}
                        <div className="flex items-center justify-between">
                          <button
                            type="button"
                            onClick={addCaseItem}
                            disabled={caseContents.items.length >= availableItems.length}
                            className="px-3 py-2 bg-primary/20 border border-primary/30 text-primary rounded hover:bg-primary/30 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center text-sm"
                          >
                            <HiOutlinePlus className="mr-1" size={14} />
                            Add Item
                          </button>
                          
                          <button
                            type="button"
                            onClick={saveCaseContents}
                            disabled={caseContents.items.length === 0}
                            className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center text-sm"
                          >
                            <HiOutlineCheck className="mr-1" size={14} />
                            Save Case Contents
                          </button>
                        </div>
                      </>
                    )}
                  </>
                )}
              </div>
            )}
            
            {/* Description */}
            <div className="bg-slate-800/50 border border-slate-700 rounded-lg p-5">
              <h3 className="text-md font-medium text-slate-200 mb-4">
                Description
              </h3>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleInputChange}
                rows={3}
                className="w-full bg-slate-800 border border-slate-700 rounded-md px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary"
                placeholder="Enter item description..."
              ></textarea>
            </div>
          </div>
          
          <div className="flex justify-end mt-6 space-x-3">
            {editingItem && (
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 border border-slate-600 rounded-md bg-slate-800 text-white hover:bg-slate-700 transition-colors flex items-center"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={submitting}
              className={`px-5 py-2 bg-primary hover:bg-primary/90 text-white rounded-md transition-all duration-300 flex items-center ${
                submitting ? 'opacity-70 cursor-not-allowed' : 'transform hover:-translate-y-0.5 hover:shadow-lg hover:shadow-primary/20'
              }`}
            >
              {submitting ? (
                <>
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"></div>
                  {editingItem ? 'Updating...' : 'Creating...'}
                </>
              ) : (
                <>
                  <HiOutlineCheck className="mr-2" size={18} />
                  {editingItem ? 'Update Item' : 'Create Item'}
                </>
              )}
            </button>
          </div>
        </form>
        </div>
    </div>
  );
};

export default ItemFormModal; 