import { AnimatePresence, motion } from 'framer-motion';
import { HiOutlineUsers, HiX } from 'react-icons/hi';

interface Owner {
  id: string;
  displayName: string;
  avatar: string;
}

interface ItemOwnersModalProps {
  isOpen: boolean;
  onClose: () => void;
  owners: Owner[];
  loading: boolean;
  itemName: string;
}

const ItemOwnersModal: React.FC<ItemOwnersModalProps> = ({
  isOpen,
  onClose,
  owners,
  loading,
  itemName,
}) => {
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{ y: -50, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 50, opacity: 0 }}
            className="bg-slate-900 border border-slate-700/50 rounded-lg shadow-xl w-full max-w-md relative"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-slate-800">
              <div className="flex items-center">
                <HiOutlineUsers className="text-primary mr-3" size={20} />
                <h2 className="text-lg font-semibold text-white truncate">
                  Owners of "{itemName}"
                </h2>
              </div>
              <button
                onClick={onClose}
                className="text-slate-400 hover:text-white transition-colors"
                aria-label="Close modal"
              >
                <HiX size={20} />
              </button>
            </div>

            {/* Body */}
            <div className="p-6 max-h-[60vh] overflow-y-auto">
              {loading ? (
                <div className="flex flex-col items-center justify-center h-48">
                  <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin mb-4"></div>
                  <p className="text-slate-300">Loading owners...</p>
                </div>
              ) : owners.length === 0 ? (
                <div className="text-center text-slate-400 py-10">
                  <p>No users currently own this item.</p>
                </div>
              ) : (
                <ul className="space-y-3">
                  {owners.map((owner) => (
                    <li
                      key={owner.id}
                      className="flex items-center p-2 bg-slate-800/50 rounded-lg"
                    >
                      <img
                        src={owner.avatar}
                        alt={owner.displayName}
                        className="w-10 h-10 rounded-full object-cover mr-4"
                      />
                      <span className="text-white font-medium">
                        {owner.displayName}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Footer */}
            <div className="p-4 border-t border-slate-800 flex justify-end">
              <button
                onClick={onClose}
                className="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white rounded-md transition-colors"
              >
                Close
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};

export default ItemOwnersModal; 