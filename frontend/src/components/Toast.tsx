import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, CheckCircle, AlertCircle, Info } from 'lucide-react';
import '@/assets/Toast.css';

interface ToastProps {
  message: string;
  type: 'success' | 'error' | 'info';
  onClose: () => void;
  duration?: number;
}

export function Toast({ message, type, onClose, duration = 4000 }: ToastProps) {
  const [isVisible, setIsVisible] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsVisible(false);
    }, duration);

    return () => clearTimeout(timer);
  }, [duration]);

  useEffect(() => {
    const handleEsc = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsVisible(false);
      }
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, []);

  const handleAnimationComplete = () => {
    if (!isVisible) {
      onClose();
    }
  };

  const getIcon = () => {
    switch (type) {
      case 'success':
        return <CheckCircle className="toast-icon toast-success-icon" />;
      case 'error':
        return <AlertCircle className="toast-icon toast-error-icon" />;
      case 'info':
        return <Info className="toast-icon toast-info-icon" />;
      default:
        return null;
    }
  };

  return (
    <AnimatePresence>
      {isVisible && (
        <motion.div
          className={`valorant-toast valorant-toast-${type}`}
          initial={{ x: 50, opacity: 0 }}
          animate={{ 
            x: 0, 
            opacity: 1,
            transition: { 
              type: "spring",
              stiffness: 400,
              damping: 25
            }
          }}
          exit={{ 
            x: 50, 
            opacity: 0,
            transition: { 
              duration: 0.2,
              ease: "easeOut" 
            }
          }}
          onAnimationComplete={handleAnimationComplete}
        >
          <div className="toast-content">
            {getIcon()}
            <span className="toast-message">{message}</span>
            <button 
              className="toast-close-button" 
              onClick={() => setIsVisible(false)}
              aria-label="Close notification"
            >
              <X size={16} />
            </button>
          </div>
          <motion.div 
            className="toast-progress"
            initial={{ width: "100%" }}
            animate={{ 
              width: "0%",
              transition: { duration: duration / 1000, ease: "linear" }
            }}
          />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
