import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import { FaCoins, FaInfoCircle } from 'react-icons/fa';
import { GiFishingPole } from 'react-icons/gi';
import { motion, AnimatePresence } from 'framer-motion';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import { Role } from '@/contexts/auth/types';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import '@/assets/shoppage.css';
import React, { forwardRef } from 'react';
import { getRarityColor, getRarityLabel, getRarityBadgeStyle } from '@/utils/rarityHelpers';
import NameplatePreview from '@/components/NameplatePreview';
import BadgePreview from '@/components/BadgePreview';
import { CasePreviewModal } from '@/components/ui/shop/CasePreviewModal';
import { SafeText } from '@/components/SafeHtmlRenderer';
import { useSanitizedContent } from '@/hooks/useSanitizedContent';
import { useDailyResetTimer } from '@/hooks/useDailyResetTimer';
import { HiOutlineClock } from 'react-icons/hi';

interface ShopItem {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  imageUrl: string;
  thumbnailUrl?: string;
  requiredRole: Role | null;
  owned: boolean;
  rarity: string;
  isCase?: boolean;
  caseContentsCount?: number;
  isFeatured?: boolean;
  isDaily?: boolean;
  fishingRodMultiplier?: number;
  gradientEndColor?: string;
}

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface PurchaseResponse {
  userProfile: any; // You can define a proper type for user profile
  purchasedItem: ShopItem;
}

interface ShopLayoutResponse {
  featuredItems: ShopItem[];
  dailyItems: ShopItem[];
}

// Shop Item Card Component
const ShopItemCard = forwardRef(({ 
  item, 
  handlePurchase, 
  purchaseInProgress, 
  user,
  isRecentlyPurchased = false,
  onViewCaseContents
}: { 
  item: ShopItem; 
  handlePurchase: (id: string, quantity?: number) => void; 
  purchaseInProgress: boolean;
  user: any;
  isRecentlyPurchased?: boolean;
  onViewCaseContents?: (caseId: string, caseName: string) => void;
}, ref) => {
  // Get rarity color for border
  const rarityColor = getRarityColor(item.rarity);
  
  // Sanitize content for safe display
  const nameContent = useSanitizedContent(item.name, { maxLength: 100, stripHtml: true });
  const descriptionContent = useSanitizedContent(item.description, { maxLength: 500, stripHtml: true });
  
  // Add state to track insufficient credits message
  const [showInsufficientCredits, setShowInsufficientCredits] = useState(false);
  
  // Add quantity state for cases
  const [quantity, setQuantity] = useState(1);
  
  // Calculate total price for cases
  const totalPrice = item.category === 'CASE' ? item.price * quantity : item.price;
  
  // Function to handle purchase attempt
  const handlePurchaseAttempt = () => {
    if ((user?.credits ?? 0) < totalPrice) {
      // Show insufficient credits message
      setShowInsufficientCredits(true);
      
      // Reset after 3 seconds
      setTimeout(() => {
        setShowInsufficientCredits(false);
      }, 3000);
    } else {
      // Proceed with purchase
      handlePurchase(item.id, item.category === 'CASE' ? quantity : undefined);
    }
  };
  
  return (
    <motion.div
      ref={ref as React.RefObject<HTMLDivElement>}
      layout
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -5 }}
      className={`shop-item-card ${isRecentlyPurchased ? 'shop-item-recent-purchase' : ''}`}
      style={{ borderColor: 'transparent' }}
    >
      {/* Item image or Discord preview for USER_COLOR or BADGE preview */}
      {item.category === 'USER_COLOR' ? (
        <div className="shop-item-image discord-preview">
          <NameplatePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/images/default-avatar.png"}
            color={item.imageUrl}
            endColor={item.gradientEndColor}
            fallbackColor={rarityColor}
            message="This is the color for the Nameplate!"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          
          {/* Status badges - Removed "Owned" badge, keeping only role requirement badge */}
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          
          {/* Recent purchase effect */}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : item.category === 'BADGE' ? (
        <div className="shop-item-image discord-preview">
          <BadgePreview
            username={user?.username || "Username"}
            avatar={user?.avatar || "/images/default-avatar.png"}
            badgeUrl={item.thumbnailUrl || item.imageUrl}
            message="This is how the badge will appear!"
            className="h-full w-full rounded-t-lg"
            size="md"
          />
          
          {/* Status badges - Removed "Owned" badge, keeping only role requirement badge */}
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          
          {/* Recent purchase effect */}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : item.category === 'CASE' ? (
        <div className="shop-item-image case-preview-container">
          {/* Case visual representation */}
          <div className="h-full w-full bg-gradient-to-br from-slate-700 to-slate-800 flex flex-col items-center justify-center relative overflow-hidden">
            {/* Case icon/visual */}
            <div className="relative z-10">
              {item.imageUrl ? (
                <img 
                  src={item.imageUrl} 
                  alt={nameContent.sanitized}
                  className="h-16 w-16 object-cover rounded-lg border-2"
                  style={{ borderColor: rarityColor }}
                />
              ) : (
                <div 
                  className="h-16 w-16 rounded-lg border-2 flex items-center justify-center"
                  style={{ 
                    borderColor: rarityColor,
                    backgroundColor: `${rarityColor}20`
                  }}
                >
                  <svg 
                    className="w-8 h-8"
                    style={{ color: rarityColor }}
                    fill="none" 
                    viewBox="0 0 24 24" 
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                  </svg>
                </div>
              )}
            </div>
            
            {/* Case contents count */}
            {item.category === 'CASE' && item.caseContentsCount && item.caseContentsCount > 0 && (
              <div className="mt-2 text-xs text-slate-300 font-medium">
                Contains {item.caseContentsCount} items
              </div>
            )}
            
            {/* Mystical background effect */}
            <div 
              className="absolute inset-0 opacity-20"
              style={{
                background: `radial-gradient(circle at center, ${rarityColor}40 0%, transparent 70%)`
              }}
            />
          </div>
          
          {/* Case-specific badges */}
          <div className="absolute top-2 left-2">
            <div 
              className="px-2 py-1 rounded text-xs font-semibold border"
              style={{
                backgroundColor: `${rarityColor}20`,
                color: rarityColor,
                borderColor: rarityColor
              }}
            >
              Case
            </div>
          </div>
          
          {/* Info icon in top right corner for cases */}
          {item.category === 'CASE' && item.caseContentsCount && item.caseContentsCount > 0 && onViewCaseContents && (
            <div className="absolute top-2 right-2">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onViewCaseContents(item.id, nameContent.sanitized);
                }}
                className="case-info-icon"
                title="View case contents and drop rates"
                aria-label="View case contents and drop rates"
              >
                <FaInfoCircle size={16} />
              </button>
            </div>
          )}
          
          {/* Role requirement badge */}
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          
          {/* Recent purchase effect */}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : item.category === 'FISHING_ROD' ? (
        <div className="shop-item-image fishing-rod-preview-container">
          <div 
            className="h-full w-full flex flex-col items-center justify-center relative overflow-hidden p-4"
            style={{ background: `linear-gradient(to bottom right, #1f2937, ${rarityColor})` }}
            >
            <GiFishingPole
              className="absolute w-24 h-24 text-white/10 transform -rotate-12 -right-4 -bottom-4"
            />
            <GiFishingPole
              className="relative z-10 w-16 h-16 text-white/80 drop-shadow-lg"
            />
            <div className="relative z-10 mt-2 text-center">
              <p className="text-2xl font-bold text-white drop-shadow-md">
                {item.fishingRodMultiplier}x
              </p>
              <p className="text-sm font-semibold text-cyan-200 drop-shadow-sm">
                Credit Bonus
              </p>
            </div>
          </div>
           {isRecentlyPurchased && (
            <motion.div
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      ) : (
        <div className="shop-item-image">
          {item.imageUrl ? (
            <img 
              src={item.imageUrl} 
              alt={nameContent.sanitized}
              className="h-full w-full object-cover" 
            />
          ) : (
            <div className="h-full w-full flex items-center justify-center bg-slate-800/50">
              <span className="text-slate-400">No Image</span>
            </div>
          )}
          
          {/* Status badges - Removed "Owned" badge, keeping only role requirement badge */}
          {!item.owned && item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) && (
            <div className="item-badge badge-required">
              {item.requiredRole} Required
            </div>
          )}
          
          {/* Recent purchase effect */}
          {isRecentlyPurchased && (
            <motion.div 
              initial={{ opacity: 0.8 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 2 }}
              className="absolute inset-0 bg-green-500/20 rounded-t-lg z-10"
            />
          )}
        </div>
      )}
      
      <div className="shop-item-content">
        <div className="flex justify-between items-center mb-2">
          <div className="flex items-center">
            <SafeText 
              text={nameContent.sanitized}
              tag="h3"
              className="font-medium text-white text-lg mr-2"
              maxLength={100}
              showTooltip={true}
            />
            <div 
              className="px-2 py-0.5 rounded text-xs font-semibold"
              style={getRarityBadgeStyle(item.rarity)}
            >
              {getRarityLabel(item.rarity)}
            </div>
          </div>
          <div className="flex items-center space-x-3">
            {/* Quantity controls for cases */}
            {item.category === 'CASE' && (
              <div className="flex items-center space-x-1">
                <button
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  disabled={quantity <= 1}
                  className="w-5 h-5 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 disabled:cursor-not-allowed rounded flex items-center justify-center text-white text-xs transition-colors"
                >
                  −
                </button>
                <span className="text-white font-medium min-w-[1.5rem] text-center text-sm">{quantity}</span>
                <button
                  onClick={() => setQuantity(Math.min(10, quantity + 1))}
                  disabled={quantity >= 10}
                  className="w-5 h-5 bg-slate-700 hover:bg-slate-600 disabled:opacity-50 disabled:cursor-not-allowed rounded flex items-center justify-center text-white text-xs transition-colors"
                >
                  +
                </button>
              </div>
            )}
            
            {/* Credits display */}
            <div className="flex items-center">
              <FaCoins className="text-yellow-400 mr-1" size={14} />
              <span className="text-yellow-400 font-medium">
                {item.category === 'CASE' && quantity > 1 
                  ? totalPrice 
                  : item.price
                }
              </span>
            </div>
          </div>
        </div>
        
        {descriptionContent.sanitized && (
          <SafeText 
            text={descriptionContent.sanitized}
            tag="p"
            className="text-slate-300 text-sm mb-3 line-clamp-2"
            maxLength={200}
            showTooltip={true}
          />
        )}
        

        

        
        {/* Enhanced purchase button - Remove owned check for cases */}
        {(item.owned && item.category !== 'CASE') ? (
          <button 
            disabled
            className="purchase-button purchase-button-owned"
          >
            <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
            Owned
          </button>
        ) : item.requiredRole && user?.roles && !user.roles.includes(item.requiredRole) ? (
          <button 
            disabled
            className="purchase-button purchase-button-required"
          >
            <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
            {item.requiredRole} Required
          </button>
        ) : (
          <button 
            onClick={handlePurchaseAttempt}
            disabled={purchaseInProgress || showInsufficientCredits}
            className={`purchase-button ${
              showInsufficientCredits 
                ? 'purchase-button-insufficient' 
                : 'purchase-button-active'
            } ${purchaseInProgress ? 'purchase-button-processing' : ''}`}
          >
            {purchaseInProgress ? (
              <>
                <svg className="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Processing...
              </>
            ) : showInsufficientCredits ? (
              <>
                <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Not Enough Credits
              </>
            ) : (
              <>
                <FaCoins className="mr-2" size={14} />
                {item.category === 'CASE' && quantity > 1 ? `Purchase ${quantity}` : 'Purchase'}
              </>
            )}
          </button>
        )}
      </div>
    </motion.div>
  );
});

// Add a display name for better debugging
ShopItemCard.displayName = 'ShopItemCard';

// Skeleton loader for shop items
const ShopItemSkeleton = () => {
  return (
    <div className="shop-item-card">
      <div className="shop-item-image skeleton"></div>
      <div className="p-4 space-y-2">
        <div className="h-6 w-2/3 skeleton rounded"></div>
        <div className="h-4 w-full skeleton rounded"></div>
        <div className="h-8 w-full skeleton rounded mt-4"></div>
      </div>
    </div>
  );
};

export function ShopPage() {
  const { user, profile, updateUserProfile } = useAuth();
  const [loading, setLoading] = useState(true);
  const [featuredItems, setFeaturedItems] = useState<ShopItem[]>([]);
  const [dailyItems, setDailyItems] = useState<ShopItem[]>([]);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [purchaseInProgress, setPurchaseInProgress] = useState(false);
  const [recentPurchases, setRecentPurchases] = useState<Record<string, number>>({});
  const timer = useDailyResetTimer();
  
  // Case preview modal state
  const [casePreviewModal, setCasePreviewModal] = useState<{
    isOpen: boolean;
    caseId: string;
    caseName: string;
  }>({
    isOpen: false,
    caseId: '',
    caseName: ''
  });
  
  // Minimum loading time in milliseconds
  const MIN_LOADING_TIME = 800;
  
  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  // Case preview modal functions
  const openCasePreview = (caseId: string, caseName: string) => {
    setCasePreviewModal({
      isOpen: true,
      caseId,
      caseName
    });
  };
  
  const closeCasePreview = () => {
    setCasePreviewModal({
      isOpen: false,
      caseId: '',
      caseName: ''
    });
  };
  
  useEffect(() => {
    const fetchShopLayout = async () => {
      // Record the start time
      const startTime = Date.now();
      setLoading(true);
      
      try {
        const response = await httpClient.get('/shop/layout');
        const data: ShopLayoutResponse = response.data;
        
        setFeaturedItems(data.featuredItems);
        setDailyItems(data.dailyItems);
        
      } catch (error) {
        console.error('Error fetching shop layout:', error);
        showToast('Failed to load shop items', 'error');
      } finally {
        // Calculate elapsed time
        const elapsedTime = Date.now() - startTime;
        
        // If elapsed time is less than minimum loading time, wait before hiding loader
        if (elapsedTime < MIN_LOADING_TIME) {
          setTimeout(() => {
            setLoading(false);
          }, MIN_LOADING_TIME - elapsedTime);
        } else {
          setLoading(false);
        }
      }
    };
    
    fetchShopLayout();
  }, []);
  
  const handlePurchase = async (itemId: string, quantity?: number) => {
    if (purchaseInProgress) return;
    
    setPurchaseInProgress(true);
    try {
      // Store the purchase response which contains updated user profile and purchased item state
      const response = await httpClient.post(`/shop/purchase/${itemId}`, { quantity });
      const { userProfile, purchasedItem }: PurchaseResponse = response.data;
      
      showToast(
        quantity && quantity > 1 
          ? `${quantity} items purchased successfully!` 
          : 'Item purchased successfully!', 
        'success'
      );
      
      // Mark recent purchase for animation
      setRecentPurchases(prev => ({...prev, [itemId]: Date.now()}));

      // Update item states locally based on the authoritative response from the server.
      // Featured items are updated to show they are "owned".
      setFeaturedItems(prevItems =>
        prevItems.map(item => (item.id === purchasedItem.id ? purchasedItem : item))
      );
      
      // Daily items are removed from the list upon purchase to prevent re-purchase.
      setDailyItems(prevItems => prevItems.filter(item => item.id !== purchasedItem.id));
      
      // Update user profile to refresh credits while preserving other profile data
      if (userProfile) {
        const updatedProfile = userProfile;
        
        // Prepare the avatar value for the profile update.
        // The backend validation for avatars rejects local paths like '/default-avatar.png'.
        // If the current avatar is the default path, send an empty string instead,
        // which is a valid value indicating no custom avatar is set.
        let avatarForUpdate = updatedProfile.avatar || user?.avatar || '';
        if (avatarForUpdate === '/images/default-avatar.png') {
          avatarForUpdate = '';
        }
        
        await updateUserProfile({
          displayName: updatedProfile.displayName || profile?.displayName || user?.username || '',
          pronouns: updatedProfile.pronouns || profile?.pronouns || '',
          about: updatedProfile.about || profile?.about || '',
          bannerColor: updatedProfile.bannerColor || profile?.bannerColor || '',
          bannerUrl: updatedProfile.bannerUrl || profile?.bannerUrl || '',
          avatar: avatarForUpdate
        });
      }
    } catch (error: any) {
      console.error('Purchase error:', error);
      if (error.response?.status === 403) {
        showToast('You do not have permission to purchase this item', 'error');
      } else if (error.response?.status === 409) {
        showToast('You already own this item', 'error');
      } else if (error.response?.status === 400 && error.response.data.message.includes("Insufficient credits")) {
        showToast('Insufficient credits to purchase this item', 'error');
      } else {
        showToast('Failed to purchase item: ' + (error.response?.data?.message || 'Unknown error'), 'error');
      }
    } finally {
      setPurchaseInProgress(false);
    }
  };
  
  return (
    <div className="bg-theme-gradient min-h-screen">
      <div className="container mx-auto px-4 py-8 shop-container">
      <div className="flex flex-col space-y-8">
        {/* Toast notifications */}
        <div className="fixed top-4 right-4 z-50 flex flex-col space-y-2">
          {toasts.map(toast => (
            <Toast
              key={toast.id}
              message={toast.message}
              type={toast.type}
              onClose={() => removeToast(toast.id)}
            />
          ))}
        </div>
      
        {/* Centered Shop title with custom font to match Leaderboard */}
        <motion.div className="section-header mb-6 text-center">
          <motion.h1 
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ delay: 0.3, type: "spring" }}
            className="shop-title"
          >
            Shop
          </motion.h1>
          
          {/* Centered credit balance */}
          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.5 }}
            className="credit-balance-container"
          >
            <div className="credit-balance">
              <FaCoins className="credit-balance-icon" size={20} />
              <span className="credit-balance-amount">
                {user?.credits || 0}
              </span>
            </div>
          </motion.div>
        </motion.div>
        
        {/* Shop Layout - Featured Left, Daily Right */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2 }}
          className="shop-layout-container"
        >
          {loading ? (
            // Loading state for both sections
            <div className="shop-loading-layout">
              <div className="featured-section">
                <h2 className="text-2xl font-bold text-white mb-6">Featured Items</h2>
                <div className="featured-grid">
                  {Array.from({ length: 3 }).map((_, index) => (
                    <ShopItemSkeleton key={index} />
                  ))}
                </div>
              </div>
              <div className="daily-section">
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-2xl font-bold text-white">Daily Items</h2>
                  <div className="flex items-center text-sm text-slate-400 bg-slate-800/50 px-3 py-1.5 rounded-full">
                    <HiOutlineClock className="mr-2" />
                    <span>{timer.formatted}</span>
                  </div>
                </div>
                <div className="daily-grid">
                  {Array.from({ length: 6 }).map((_, index) => (
                    <ShopItemSkeleton key={index} />
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <div className="shop-main-layout">
              {/* Featured Items Section - Left Side */}
              <div className="featured-section">
                <h2 className="text-2xl font-bold text-white mb-6">Featured Items</h2>
                {featuredItems.length === 0 ? (
                  <motion.div 
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="text-center py-12 rounded-lg border border-slate-700/50 bg-slate-800/20"
                  >
                    <div className="text-slate-400 mb-2">
                      No featured items available.
                    </div>
                  </motion.div>
                ) : (
                  <div className="featured-grid">
                    <AnimatePresence mode="popLayout">
                      {featuredItems.slice(0, 3).map((item) => (
                        <ShopItemCard
                          key={item.id}
                          item={item}
                          handlePurchase={handlePurchase}
                          purchaseInProgress={purchaseInProgress}
                          user={user}
                          isRecentlyPurchased={!!recentPurchases[item.id] && (Date.now() - recentPurchases[item.id] < 5000)}
                          onViewCaseContents={openCasePreview}
                        />
                      ))}
                    </AnimatePresence>
                  </div>
                )}
              </div>
              
              {/* Daily Items Section - Right Side */}
              <div className="daily-section">
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-2xl font-bold text-white">Daily Items</h2>
                  <div className="flex items-center text-sm text-slate-400 bg-slate-800/50 px-3 py-1.5 rounded-full">
                    <HiOutlineClock className="mr-2" />
                    <span>{timer.formatted}</span>
                  </div>
                </div>
                {dailyItems.length === 0 ? (
                  <motion.div 
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="flex-grow flex items-center justify-center w-full"
                  >
                    <p 
                        className="text-slate-300 text-2xl" 
                        style={{ fontFamily: 'Grandstander, sans-serif' }}
                    >
                      You have purchased all your daily items!
                    </p>
                  </motion.div>
                ) : (
                  <div className="daily-grid">
                    <AnimatePresence mode="popLayout">
                      {dailyItems.map((item) => (
                        <ShopItemCard
                          key={item.id}
                          item={item}
                          handlePurchase={handlePurchase}
                          purchaseInProgress={purchaseInProgress}
                          user={user}
                          isRecentlyPurchased={!!recentPurchases[item.id] && (Date.now() - recentPurchases[item.id] < 5000)}
                          onViewCaseContents={openCasePreview}
                        />
                      ))}
                    </AnimatePresence>
                  </div>
                )}
              </div>
            </div>
          )}
        </motion.div>
      </div>
    </div>
    
    {/* Case Preview Modal */}
    <CasePreviewModal
      isOpen={casePreviewModal.isOpen}
      onClose={closeCasePreview}
      caseId={casePreviewModal.caseId}
      caseName={casePreviewModal.caseName}
      user={user}
    />
    </div>
  );
}

export default ShopPage;