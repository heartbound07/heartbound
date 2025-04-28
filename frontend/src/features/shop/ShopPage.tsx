import { useAuth } from '@/contexts/auth';
import { FaCoins } from 'react-icons/fa';
import { motion } from 'framer-motion';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';

export function ShopPage() {
  const { user } = useAuth();
  
  // Categories for shop items (placeholder for now)
  const categories = [
    { id: 'banners', name: 'Banners', description: 'Customize your profile with unique banners' },
    { id: 'effects', name: 'Profile Effects', description: 'Add special effects to your profile' },
    { id: 'badges', name: 'Badges', description: 'Show off your achievements with exclusive badges' },
  ];
  
  // Placeholder items for demonstration
  const placeholderItems = [
    { id: 1, name: 'Premium Banner', price: 500, category: 'banners', imageUrl: '/placeholder-banner.png' },
    { id: 2, name: 'Gold Trim Effect', price: 750, category: 'effects', imageUrl: '/placeholder-effect.png' },
    { id: 3, name: 'Season 1 Badge', price: 1000, category: 'badges', imageUrl: '/placeholder-badge.png' },
  ];
  
  return (
    <div className="container mx-auto p-6">
      <div className="flex flex-col space-y-8">
        {/* Header with credits display */}
        <div className="flex justify-between items-center">
          <h1 className="text-3xl font-bold text-white">Shop</h1>
          <div className="flex items-center bg-slate-800/50 rounded-lg px-4 py-2 border border-slate-700">
            <FaCoins className="text-yellow-400 mr-2" size={20} />
            <span className="text-yellow-400 font-semibold text-lg">
              {user?.credits || 0}
            </span>
          </div>
        </div>
        
        {/* Featured items banner */}
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative w-full h-60 bg-gradient-to-r from-primary/20 to-primary/10 rounded-xl overflow-hidden"
        >
          <div className="absolute inset-0 flex flex-col justify-center p-8">
            <h2 className="text-2xl font-bold text-white mb-2">Featured Items</h2>
            <p className="text-white/80 max-w-md">
              Enhance your profile with exclusive items. New items added regularly!
            </p>
            <button className="mt-4 px-6 py-2 bg-primary text-white rounded-lg w-fit hover:bg-primary/90 transition-colors">
              View Featured
            </button>
          </div>
          {/* We'd add an image or visual element here in a future update */}
        </motion.div>
        
        {/* Categories */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {categories.map((category) => (
            <motion.div
              key={category.id}
              whileHover={{ scale: 1.02 }}
              className="bg-slate-800/50 rounded-lg p-6 cursor-pointer border border-slate-700 hover:border-primary/50 transition-colors"
            >
              <h3 className="text-xl font-semibold text-white mb-2">{category.name}</h3>
              <p className="text-slate-300 text-sm">{category.description}</p>
            </motion.div>
          ))}
        </div>
        
        {/* Popular items */}
        <div>
          <h2 className="text-2xl font-bold text-white mb-4">Popular Items</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {placeholderItems.map((item) => (
              <motion.div
                key={item.id}
                whileHover={{ y: -5 }}
                className="bg-slate-800/30 border border-slate-700 rounded-lg overflow-hidden"
              >
                {/* Placeholder for item image */}
                <div className="h-40 bg-slate-700/50 flex items-center justify-center">
                  <span className="text-slate-400">Item Preview</span>
                </div>
                
                <div className="p-4">
                  <div className="flex justify-between items-center mb-2">
                    <h3 className="font-medium text-white">{item.name}</h3>
                    <div className="flex items-center">
                      <FaCoins className="text-yellow-400 mr-1" size={14} />
                      <span className="text-yellow-400 font-medium">{item.price}</span>
                    </div>
                  </div>
                  <button className="w-full mt-2 px-4 py-2 bg-primary/80 hover:bg-primary text-white rounded transition-colors">
                    Purchase
                  </button>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default ShopPage;
