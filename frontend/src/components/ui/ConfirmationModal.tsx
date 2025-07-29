import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { HiOutlineExclamation, HiOutlineX } from 'react-icons/hi';

interface ConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string | React.ReactNode;
}

export const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
}) => {
  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black bg-opacity-70 z-50 flex justify-center items-center">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 m-4 max-w-md w-full"
        >
          <div className="flex justify-between items-start mb-4">
            <div className="flex items-center">
              <div className="mr-3 flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-red-500/20">
                <HiOutlineExclamation className="h-6 w-6 text-red-400" aria-hidden="true" />
              </div>
              <h2 className="text-xl font-semibold text-white">{title}</h2>
            </div>
            <button onClick={onClose} className="text-slate-400 hover:text-white">
              <HiOutlineX size={24} />
            </button>
          </div>
          
          <div className="text-slate-300 mb-6">
            {message}
          </div>

          <div className="flex justify-end space-x-4">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-md bg-slate-700 text-white font-semibold hover:bg-slate-600 transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={() => {
                onConfirm();
                onClose();
              }}
              className="px-4 py-2 rounded-md bg-red-600 text-white font-semibold hover:bg-red-700 transition-colors"
            >
              Confirm
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}; 