import { FaCoins } from 'react-icons/fa';
import { motion } from 'framer-motion';

interface ShopHeaderProps {
  credits: number;
}

const ShopHeader: React.FC<ShopHeaderProps> = ({ credits }) => {
  return (
    <motion.div className="section-header mb-6 text-center">
      <motion.h1
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ delay: 0.3, type: "spring" }}
        className="shop-title"
      >
        Shop
      </motion.h1>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5 }}
        className="credit-balance-container"
      >
        <div className="credit-balance">
          <FaCoins className="credit-balance-icon" size={20} />
          <span className="credit-balance-amount">
            {credits || 0}
          </span>
        </div>
      </motion.div>
    </motion.div>
  );
};

export default ShopHeader; 