import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/contexts/auth';
import { motion } from 'framer-motion';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import { HiOutlineClock } from 'react-icons/hi';
import { useDailyResetTimer } from '@/hooks/useDailyResetTimer';
import { CasePreviewModal } from '@/components/ui/shop/CasePreviewModal';
import ShopHeader from './components/ShopHeader';
import ShopSection from './components/ShopSection';
import { ShopItem, ToastNotification, PurchaseResponse, ShopLayoutResponse } from '@/types/shop';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import '@/assets/shoppage.css';

export function ShopPage() {
  const { user, profile, updateUserProfile } = useAuth();
  const [loading, setLoading] = useState(true);
  const [featuredItems, setFeaturedItems] = useState<ShopItem[]>([]);
  const [dailyItems, setDailyItems] = useState<ShopItem[]>([]);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [purchaseInProgress, setPurchaseInProgress] = useState(false);
  const [recentPurchases, setRecentPurchases] = useState<Record<string, number>>({});
  const timer = useDailyResetTimer();

  const [casePreviewModal, setCasePreviewModal] = useState<{
    isOpen: boolean;
    caseId: string;
    caseName: string;
  }>({
    isOpen: false,
    caseId: '',
    caseName: ''
  });

  const MIN_LOADING_TIME = 800;

  const showToast = useCallback((message: string, type: 'success' | 'error' | 'info') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);
  }, []);

  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };

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

  const fetchShopLayout = useCallback(async () => {
    const startTime = Date.now();
    setLoading(true);

    try {
      const response = await httpClient.get('/shop/layout');
      const data: ShopLayoutResponse = response.data;

      setFeaturedItems(data.featuredItems);
      setDailyItems(data.dailyItems);

    } catch (error) {
      console.error('Error fetching shop layout:', error);
      const id = Math.random().toString(36).substring(2, 9);
      setToasts(prev => [...prev, { id, message: 'Failed to load shop items', type: 'error' }]);
    } finally {
      const elapsedTime = Date.now() - startTime;
      if (elapsedTime < MIN_LOADING_TIME) {
        setTimeout(() => {
          setLoading(false);
        }, MIN_LOADING_TIME - elapsedTime);
      } else {
        setLoading(false);
      }
    }
  }, []); // Remove showToast from dependencies to prevent infinite loop

  useEffect(() => {
    fetchShopLayout();
  }, [fetchShopLayout]);

  useEffect(() => {
    const now = new Date();
    // Set the target to 2 seconds past the next midnight UTC to ensure the backend has time to update.
    const midnightUTC = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1, 0, 0, 2, 0));
    const msUntilMidnight = midnightUTC.getTime() - now.getTime();

    const timerId = setTimeout(() => {
      const id = Math.random().toString(36).substring(2, 9);
      setToasts(prev => [...prev, { id, message: 'Daily items have been refreshed!', type: 'info' }]);
      fetchShopLayout();
    }, msUntilMidnight);

    return () => clearTimeout(timerId);
  }, []); // Remove dependencies to avoid recreation

  const handlePurchase = async (itemId: string, quantity?: number) => {
    if (purchaseInProgress) return;

    setPurchaseInProgress(true);
    try {
      const response = await httpClient.post(`/shop/purchase/${itemId}`, { quantity });
      const { userProfile, purchasedItem }: PurchaseResponse = response.data;

      showToast(
        quantity && quantity > 1
          ? `${quantity} items purchased successfully!`
          : 'Item purchased successfully!',
        'success'
      );

      setRecentPurchases(prev => ({ ...prev, [itemId]: Date.now() }));

      setFeaturedItems(prevItems =>
        prevItems.map(item => (item.id === purchasedItem.id ? purchasedItem : item))
      );
      setDailyItems(prevItems => 
        prevItems.map(item => item.id === purchasedItem.id ? { ...item, owned: true } : item)
      );

      if (userProfile) {
        const updatedProfile = userProfile;
        
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

          <ShopHeader credits={user?.credits || 0} />

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.2 }}
            className="shop-layout-container"
          >
            <div className="shop-main-layout">
              <div className="featured-section">
                <ShopSection
                  title="Featured Items"
                  items={featuredItems}
                  loading={loading}
                  purchaseInProgress={purchaseInProgress}
                  user={user}
                  recentPurchases={recentPurchases}
                  handlePurchase={handlePurchase}
                  onViewCaseContents={openCasePreview}
                  emptyMessage="No featured items available."
                  gridClass="featured-grid"
                  skeletonCount={3}
                />
              </div>

              <div className="daily-section">
                <div className="flex items-center justify-between mb-6 w-full">
                  <h2 className="text-2xl font-bold text-white">Daily Items</h2>
                  <div className="flex items-center text-sm text-slate-400 bg-slate-800/50 px-3 py-1.5 rounded-full">
                    <HiOutlineClock className="mr-2" />
                    <span>{timer.formatted}</span>
                  </div>
                </div>
                <ShopSection
                  title=""
                  items={dailyItems}
                  loading={loading}
                  purchaseInProgress={purchaseInProgress}
                  user={user}
                  recentPurchases={recentPurchases}
                  handlePurchase={handlePurchase}
                  onViewCaseContents={openCasePreview}
                  emptyMessage="You have purchased all your daily items!"
                  gridClass="daily-grid"
                  skeletonCount={6}
                />
              </div>
            </div>
          </motion.div>
        </div>
      </div>

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