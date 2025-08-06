import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';

interface ConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  message: string | React.ReactNode;
  actionInProgress?: boolean;
}

export const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  message,
  actionInProgress = false,
}) => {
  if (!isOpen) return null;

  const handleConfirm = () => {
    if (!actionInProgress) {
      onConfirm();
    }
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black bg-opacity-70 z-50 flex justify-center items-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 m-4 max-w-md w-full"
        >
          
          <div className="text-slate-300 my-6">
            {message}
          </div>

          <div className="flex justify-center space-x-4">
            <button
              onClick={onClose}
              disabled={actionInProgress}
              className="px-4 py-2 rounded-md bg-slate-700 text-white font-semibold hover:bg-slate-600 disabled:bg-gray-600 disabled:cursor-not-allowed transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={handleConfirm}
              disabled={actionInProgress}
              className="px-4 py-2 rounded-md bg-red-600 text-white font-semibold hover:bg-red-700 disabled:bg-gray-600 disabled:cursor-not-allowed transition-colors flex items-center"
            >
              {actionInProgress ? (
                <>
                  <svg className="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Processing...
                </>
              ) : (
                'Confirm'
              )}
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}; 