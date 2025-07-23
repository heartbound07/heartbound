import { getRarityColor } from '@/utils/rarityHelpers';
import { 
    HiOutlineCheck, 
    HiOutlineCollection, 
    HiOutlineColorSwatch,
    HiOutlineCash,
    HiOutlineExclamation,
    HiOutlineClock,
    HiOutlineTrash,
    HiOutlinePencil,
    HiOutlineStar,
  } from 'react-icons/hi';
import InlinePriceEditor from './InlinePriceEditor';
import httpClient from '@/lib/api/httpClient';

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
  }

  interface ShopItemsTableProps {
    items: ShopItem[];
    handleEdit: (item: ShopItem) => void;
    handleDelete: (itemId: string) => void;
    fetchShopItems: () => void;
  }

const ShopItemsTable: React.FC<ShopItemsTableProps> = ({ items, handleEdit, handleDelete, fetchShopItems }) => {

    const handleStatusToggle = async (itemId: string, currentStatus: boolean) => {
        try {
            await httpClient.patch(`/shop/admin/items/${itemId}/status`, { active: !currentStatus });
            fetchShopItems();
        } catch (error) {
            console.error("Failed to update status", error);
            // Here you might want to show a toast notification
        }
    };

  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 overflow-hidden transition-all duration-300 hover:shadow-xl">
    <div className="p-5 border-b border-slate-700">
      <h2 className="text-xl font-semibold text-white flex items-center">
        <HiOutlineCollection className="text-primary mr-2" size={20} />
        Shop Items
      </h2>
    </div>
    
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-700">
        <thead>
          <tr className="bg-slate-800/70">
            <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Item</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Category</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Price</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Multiplier</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Rarity</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Visibility</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Status</th>
            <th className="px-6 py-3 text-left text-xs font-medium text-slate-300 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-700/70 bg-slate-800/20">
          {items.length === 0 ? (
            <tr>
              <td colSpan={8} className="px-6 py-8 text-center text-slate-400">
                <div className="flex flex-col items-center">
                  <HiOutlineExclamation className="text-slate-500 mb-2" size={24} />
                  <p>No items found. Create your first shop item above.</p>
                </div>
              </td>
            </tr>
          ) : (
            items.map(item => (
              <tr key={item.id} className="hover:bg-slate-800/40 transition-colors">
                <td className="px-6 py-4">
                  <div className="flex items-center">
                    {item.category === 'USER_COLOR' ? (
                      <div 
                        className="h-10 w-10 rounded-full mr-3 flex items-center justify-center"
                        style={{ 
                          backgroundColor: item.imageUrl || '#ffffff',
                          border: `2px solid ${getRarityColor(item.rarity)}`,
                          background: item.gradientEndColor ? `linear-gradient(to right, ${item.imageUrl}, ${item.gradientEndColor})` : item.imageUrl
                        }}
                      >
                        <HiOutlineColorSwatch className="text-white text-opacity-80" size={16} />
                      </div>
                    ) : (
                      item.imageUrl ? (
                        <div className="h-10 w-10 rounded-full overflow-hidden mr-3" style={{ 
                          border: `2px solid ${getRarityColor(item.rarity)}`
                        }}>
                          <img 
                            src={item.imageUrl} 
                            alt={item.name} 
                            className="h-full w-full object-cover"
                          />
                        </div>
                      ) : (
                        <div className="h-10 w-10 rounded-full mr-3 bg-slate-700 flex items-center justify-center"
                          style={{ border: `2px solid ${getRarityColor(item.rarity)}` }}
                        >
                          <HiOutlineCollection className="text-slate-400" size={16} />
                        </div>
                      )
                    )}
                    <div className="text-sm font-medium text-white">{item.name}</div>
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-300">{item.category}</td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="flex items-center text-sm text-yellow-400">
                    <HiOutlineCash className="mr-1" size={14} />
                    <InlinePriceEditor itemId={item.id} initialPrice={item.price} onSave={fetchShopItems} />
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-300">
                    {item.category === 'FISHING_ROD' ? `${item.fishingRodMultiplier}x` : 'N/A'}
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span 
                    className="px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full"
                    style={{
                      backgroundColor: getRarityColor(item.rarity) + '20',
                      color: getRarityColor(item.rarity),
                      border: `1px solid ${getRarityColor(item.rarity)}`
                    }}
                  >
                    <HiOutlineStar className="mr-1" size={14} />
                    {item.rarity.charAt(0) + item.rarity.slice(1).toLowerCase()}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="flex flex-col space-y-1">
                    {item.isFeatured && (
                      <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-blue-900/30 text-blue-400 border border-blue-700">
                        <HiOutlineStar className="mr-1" size={12} />
                        Featured
                      </span>
                    )}
                    {item.isDaily && (
                      <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-purple-900/30 text-purple-400 border border-purple-700">
                        <HiOutlineClock className="mr-1" size={12} />
                        Daily
                      </span>
                    )}
                    {!item.isFeatured && !item.isDaily && (
                      <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-slate-700/50 text-slate-400 border border-slate-600">
                        Regular
                      </span>
                    )}
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                        <label htmlFor={`status-toggle-${item.id}`} className="flex items-center cursor-pointer">
                            <div className="relative">
                            <input type="checkbox" id={`status-toggle-${item.id}`} className="sr-only" 
                                checked={item.active}
                                onChange={() => handleStatusToggle(item.id, item.active)}
                                disabled={item.expired}
                            />
                            <div className={`block w-10 h-6 rounded-full ${item.active ? 'bg-primary' : 'bg-slate-600'}`}></div>
                            <div className={`dot absolute left-1 top-1 bg-white w-4 h-4 rounded-full transition-transform ${item.active ? 'translate-x-4' : ''}`}></div>
                            </div>
                            <div className="ml-3 text-sm">
                                {item.expired ? (
                                    <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-red-900/30 text-red-400 border border-red-700">
                                    <HiOutlineClock className="mr-1" size={14} />
                                    Expired
                                    </span>
                                ) : item.active ? (
                                    <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-green-900/30 text-green-400 border border-green-700">
                                    <HiOutlineCheck className="mr-1" size={14} />
                                    Active
                                    </span>
                                ) : (
                                    <span className="px-2 py-1 inline-flex items-center text-xs leading-5 font-semibold rounded-full bg-slate-700/50 text-slate-400 border border-slate-600">
                                    Inactive
                                    </span>
                                )}
                            </div>
                        </label>
                  </div>
                  {item.expiresAt && !item.expired && (
                    <div className="text-xs text-slate-400 mt-1 flex items-center">
                      <HiOutlineClock className="mr-1" size={12} />
                      Expires: {new Date(item.expiresAt).toLocaleString()}
                    </div>
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                  <div className="flex items-center space-x-3">
                    <button
                      onClick={() => handleEdit(item)}
                      className="text-primary hover:text-primary/80 flex items-center"
                    >
                      <HiOutlinePencil className="mr-1" size={16} />
                      Edit
                    </button>
                    <button
                      onClick={() => handleDelete(item.id)}
                      disabled={item.isDeleting}
                      className={`text-red-500 hover:text-red-400 flex items-center ${item.isDeleting ? 'opacity-50 cursor-not-allowed' : ''}`}
                    >
                      {item.isDeleting ? (
                        <>
                          <div className="w-3 h-3 border-2 border-red-500 border-t-transparent rounded-full animate-spin mr-1"></div>
                          Deleting...
                        </>
                      ) : (
                        <>
                          <HiOutlineTrash className="mr-1" size={16} />
                          Delete
                        </>
                      )}
                    </button>
                  </div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  </div>
  );
};

export default ShopItemsTable; 