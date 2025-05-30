import { useState, useEffect, useCallback, useMemo } from 'react';
import { useAuth } from '@/contexts/auth/useAuth';
import { usePairings } from '@/hooks/usePairings';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/valorant/avatar';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/valorant/badge';
import { Input } from '@/components/ui/profile/input';
import { Label } from '@/components/ui/valorant/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/valorant/select';
import { Loader2, Heart, Users, Trophy, MessageCircle, Settings, Info, User, MapPin, Calendar } from 'lucide-react';
import { motion } from 'framer-motion';
import type { JoinQueueRequestDTO, PairingDTO } from '@/config/pairingService';
import { useQueueUpdates } from '@/contexts/QueueUpdates';
import { performMatchmaking, deleteAllPairings, enableQueue, disableQueue } from '@/config/pairingService';
import { usePairingUpdates } from '@/contexts/PairingUpdates';
import { MatchFoundModal } from '@/components/modals/MatchFoundModal';
import { UserProfileModal } from '@/components/modals/UserProfileModal';
import { getUserProfiles, type UserProfileDTO } from '@/config/userService';
import { DashboardNavigation } from '@/components/Sidebar';
import '@/assets/PairingsPage.css';
import { useQueueConfig } from '@/contexts/QueueConfigUpdates';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/valorant/tooltip';

const REGIONS = [
  { value: 'NA_EAST', label: 'NA East' },
  { value: 'NA_WEST', label: 'NA West' },
  { value: 'NA_CENTRAL', label: 'NA Central' },
  { value: 'EU', label: 'Europe' },
  { value: 'AP', label: 'Asia Pacific' },
  { value: 'KR', label: 'Korea' },
  { value: 'LATAM', label: 'Latin America' },
  { value: 'BR', label: 'Brazil' }
];

const RANKS = [
  { value: 'IRON', label: 'Iron' },
  { value: 'BRONZE', label: 'Bronze' },
  { value: 'SILVER', label: 'Silver' },
  { value: 'GOLD', label: 'Gold' },
  { value: 'PLATINUM', label: 'Platinum' },
  { value: 'DIAMOND', label: 'Diamond' },
  { value: 'ASCENDANT', label: 'Ascendant' },
  { value: 'IMMORTAL', label: 'Immortal' },
  { value: 'RADIANT', label: 'Radiant' }
];

const GENDERS = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'NON_BINARY', label: 'Non-Binary' },
  { value: 'PREFER_NOT_TO_SAY', label: 'Prefer not to say' }
];

// Extract form component for better organization
const QueueJoinForm = ({ onJoinQueue, loading }: { 
  onJoinQueue: (data: JoinQueueRequestDTO) => Promise<void>; 
  loading: boolean;
}) => {
  const [age, setAge] = useState<string>('');
  const [region, setRegion] = useState<string>('');
  const [rank, setRank] = useState<string>('');
  const [gender, setGender] = useState<string>('');
  
  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    
    // Input validation
    const ageNum = parseInt(age);
    if (!ageNum || ageNum < 13 || ageNum > 100) {
      throw new Error('Please enter a valid age between 13 and 100');
    }
    
    if (!region) {
      throw new Error('Please select your region');
    }
    
    if (!rank) {
      throw new Error('Please select your rank');
    }

    if (!gender) {
      throw new Error('Please select your gender');
    }
    
    await onJoinQueue({
      userId: '', // This gets filled by the parent component
      age: ageNum,
      region: region as any,
      rank: rank as any,
      gender: gender as any
    });
    
    // Clear form on success
    setAge('');
    setRegion('');
    setRank('');
    setGender('');
  }, [age, region, rank, gender, onJoinQueue]);

  return (
    <Card className="bg-slate-800/50 border-slate-700">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-white">
          <Heart className="h-5 w-5 text-primary" />
          Join Matchmaking Queue
        </CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <Label htmlFor="age" className="text-white">Age</Label>
            <Input
              id="age"
              type="number"
              placeholder="Enter your age"
              value={age}
              onChange={(e) => setAge(e.target.value)}
              min="13"
              max="100"
              required
              className="bg-slate-700 border-slate-600 text-white"
            />
          </div>
          
          <div>
            <Label htmlFor="region" className="text-white">Region</Label>
            <Select value={region} onValueChange={setRegion} required>
              <SelectTrigger id="region" className="bg-slate-700 border-slate-600 text-white">
                <SelectValue placeholder="Select your region" />
              </SelectTrigger>
              <SelectContent>
                {REGIONS.map((reg) => (
                  <SelectItem key={reg.value} value={reg.value}>
                    {reg.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          
          <div>
            <Label htmlFor="rank" className="text-white">Rank</Label>
            <Select value={rank} onValueChange={setRank} required>
              <SelectTrigger id="rank" className="bg-slate-700 border-slate-600 text-white">
                <SelectValue placeholder="Select your rank" />
              </SelectTrigger>
              <SelectContent>
                {RANKS.map((r) => (
                  <SelectItem key={r.value} value={r.value}>
                    {r.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="gender" className="text-slate-200 font-medium">
              Gender
            </Label>
            <Select value={gender} onValueChange={setGender}>
              <SelectTrigger className="valorant-select-trigger">
                <SelectValue placeholder="Select your gender" />
              </SelectTrigger>
              <SelectContent className="valorant-select-content">
                {GENDERS.map((g) => (
                  <SelectItem key={g.value} value={g.value} className="valorant-select-item">
                    {g.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          
          <Button 
            type="submit" 
            className="w-full" 
            disabled={loading}
            aria-label="Join matchmaking queue"
          >
            {loading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Joining Queue...
              </>
            ) : (
              <>
                <Heart className="mr-2 h-4 w-4" />
                Join Queue
              </>
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
};
export function PairingsPage() {
  const { user, hasRole } = useAuth();
  const { 
    currentPairing, 
    pairingHistory, 
    queueStatus, 
    pairedUser, 
    loading, 
    error, 
    actionLoading, 
    joinQueue, 
    leaveQueue,
    refreshData 
  } = usePairings();
  const { isConnected } = useQueueUpdates();
  const { pairingUpdate, clearUpdate } = usePairingUpdates();
  
  const [adminActionLoading, setAdminActionLoading] = useState(false);
  const [adminMessage, setAdminMessage] = useState<string | null>(null);
  const [showMatchModal, setShowMatchModal] = useState(false);
  const [matchedPairing, setMatchedPairing] = useState<PairingDTO | null>(null);
  const [userProfiles, setUserProfiles] = useState<Record<string, UserProfileDTO>>({});
  const [selectedUserProfile, setSelectedUserProfile] = useState<UserProfileDTO | null>(null);
  const [showUserProfileModal, setShowUserProfileModal] = useState(false);
  const [userProfileModalPosition, setUserProfileModalPosition] = useState<{ x: number; y: number } | null>(null);

  // Add sidebar collapse state tracking
  const [isCollapsed, setIsCollapsed] = useState(() => {
    const savedState = localStorage.getItem('sidebar-collapsed');
    return savedState ? JSON.parse(savedState) : false;
  });

  // Listen for sidebar state changes
  useEffect(() => {
    const handleSidebarStateChange = (event: CustomEvent) => {
      setIsCollapsed(event.detail.collapsed);
    };

    window.addEventListener('sidebarStateChange', handleSidebarStateChange as EventListener);
    return () => {
      window.removeEventListener('sidebarStateChange', handleSidebarStateChange as EventListener);
    };
  }, []);

  // Memoize expensive calculations
  const formatDate = useMemo(() => (dateString: string) => {
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(dateString));
  }, []);

  // Add queue config state and context
  const { queueConfig, isQueueEnabled } = useQueueConfig();
  const [queueConfigLoading, setQueueConfigLoading] = useState(false);
  const [queueConfigMessage, setQueueConfigMessage] = useState<string | null>(null);

  // Enhanced queue join with better error handling
  const handleJoinQueue = useCallback(async (queueData: Omit<JoinQueueRequestDTO, 'userId'>) => {
    if (!user?.id) {
      // Error handling is managed by usePairings hook
      throw new Error('User authentication required');
    }

    try {
      await joinQueue({
        ...queueData,
        userId: user.id
      });
      
      // Clear form state on success (if needed)
    } catch (err: any) {
      const errorMessage = err?.message || 'Failed to join matchmaking queue';
      // Error state is managed by usePairings hook, just rethrow
      console.error('Queue join error:', err);
      throw new Error(errorMessage);
    }
  }, [user?.id, joinQueue]);

  const handleAdminMatchmaking = async () => {
    try {
      setAdminActionLoading(true);
      setAdminMessage(null);
      
      const newPairings = await performMatchmaking();
      
      setAdminMessage(`Successfully created ${newPairings.length} new pairings! Notifications will be sent shortly...`);
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err?.message || 'Matchmaking failed';
      setAdminMessage(`Error: ${errorMessage}`);
      console.error('Admin matchmaking error:', err);
    } finally {
      setAdminActionLoading(false);
    }
  };

  const handleDeleteAllPairings = async () => {
    if (!confirm('Are you sure you want to delete ALL active pairings? This action cannot be undone.')) {
      return;
    }

    try {
      setAdminActionLoading(true);
      setAdminMessage(null);
      
      const result = await deleteAllPairings();
      setAdminMessage(`Successfully deleted ${result.deletedCount} active pairing(s)!`);
      
      // Refresh pairings data
      window.location.reload(); // Quick refresh - you could also update state
      
      setTimeout(() => setAdminMessage(null), 5000);
    } catch (error: any) {
      setAdminMessage(`Failed to delete pairings: ${error.message}`);
      setTimeout(() => setAdminMessage(null), 5000);
    } finally {
      setAdminActionLoading(false);
    }
  };

  // Handle pairing updates to show modal
  useEffect(() => {
    if (pairingUpdate && pairingUpdate.eventType === 'MATCH_FOUND' && pairingUpdate.pairing) {
      console.log('[PairingsPage] Match found, showing modal:', pairingUpdate);
      setMatchedPairing(pairingUpdate.pairing);
      setShowMatchModal(true);
      // Refresh data to update UI
      refreshData();
      // Clear the update after handling it
      clearUpdate();
    }
  }, [pairingUpdate, refreshData, clearUpdate]);

  // Fetch user profiles for pairing history
  useEffect(() => {
    const fetchUserProfiles = async () => {
      const userIds = new Set<string>();
      
      // Collect all user IDs from pairing history
      pairingHistory.forEach(pairing => {
        userIds.add(pairing.user1Id);
        userIds.add(pairing.user2Id);
      });
      
      if (userIds.size > 0) {
        try {
          const profiles = await getUserProfiles(Array.from(userIds));
          setUserProfiles(profiles);
        } catch (error) {
          console.error('Failed to fetch user profiles:', error);
        }
      }
    };

    if (pairingHistory.length > 0) {
      fetchUserProfiles();
    }
  }, [pairingHistory]);

  // Handle user profile click
  const handleUserClick = useCallback((userId: string, event: React.MouseEvent) => {
    const profile = userProfiles[userId];
    if (profile) {
      setSelectedUserProfile(profile);
      setUserProfileModalPosition({ x: event.clientX, y: event.clientY });
      setShowUserProfileModal(true);
    }
  }, [userProfiles]);

  const handleCloseUserProfileModal = useCallback(() => {
    setShowUserProfileModal(false);
    setSelectedUserProfile(null);
    setUserProfileModalPosition(null);
  }, []);

  const handleCloseMatchModal = () => {
    console.log('[PairingsPage] Closing match modal');
    setShowMatchModal(false);
    setMatchedPairing(null);
  };

  // Add queue control handlers
  const handleEnableQueue = async () => {
    try {
      setQueueConfigLoading(true);
      setQueueConfigMessage(null);
      
      const result = await enableQueue();
      setQueueConfigMessage(`Queue enabled successfully: ${result.message}`);
      
      setTimeout(() => setQueueConfigMessage(null), 5000);
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || 'Failed to enable queue';
      setQueueConfigMessage(`Error: ${errorMessage}`);
      setTimeout(() => setQueueConfigMessage(null), 5000);
    } finally {
      setQueueConfigLoading(false);
    }
  };

  const handleDisableQueue = async () => {
    if (!confirm('Are you sure you want to disable the matchmaking queue? Users will not be able to join until re-enabled.')) {
      return;
    }

    try {
      setQueueConfigLoading(true);
      setQueueConfigMessage(null);
      
      const result = await disableQueue();
      setQueueConfigMessage(`Queue disabled successfully: ${result.message}`);
      
      setTimeout(() => setQueueConfigMessage(null), 5000);
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || 'Failed to disable queue';
      setQueueConfigMessage(`Error: ${errorMessage}`);
      setTimeout(() => setQueueConfigMessage(null), 5000);
    } finally {
      setQueueConfigLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="pairings-container">
      <DashboardNavigation />
      
      <main className={`pairings-content ${isCollapsed ? 'sidebar-collapsed' : ''}`}>
        <div className="p-6">
          {/* Admin Controls */}
          {hasRole('ADMIN') && (
            <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} className="mb-6">
              <Card className="bg-slate-800/50 border-slate-700">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-white">
                    <Settings className="h-5 w-5 text-primary" />
                    Admin Controls
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {/* Queue Status Display */}
                    <div className="p-3 rounded-lg border border-slate-600 bg-slate-700/30">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className={`w-3 h-3 rounded-full ${isQueueEnabled ? 'bg-green-500' : 'bg-red-500'}`} />
                          <span className="text-white font-medium">
                            Queue Status: {isQueueEnabled ? 'Enabled' : 'Disabled'}
                          </span>
                        </div>
                        {queueConfig && (
                          <Badge variant="outline" className="text-xs">
                            Updated by {queueConfig.updatedBy}
                          </Badge>
                        )}
                      </div>
                      {queueConfig && (
                        <p className="text-sm text-slate-400 mt-1">{queueConfig.message}</p>
                      )}
                    </div>

                    {/* Queue Control Buttons */}
                    <div className="flex flex-wrap gap-3 items-center">
                      <Button
                        onClick={handleEnableQueue}
                        disabled={queueConfigLoading || isQueueEnabled}
                        variant={isQueueEnabled ? "outline" : "default"}
                        className="flex items-center gap-2"
                      >
                        {queueConfigLoading ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <Users className="h-4 w-4" />
                        )}
                        Enable Queue
                      </Button>
                      
                      <Button
                        onClick={handleDisableQueue}
                        disabled={queueConfigLoading || !isQueueEnabled}
                        variant={!isQueueEnabled ? "outline" : "destructive"}
                        className="flex items-center gap-2"
                      >
                        {queueConfigLoading ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <Users className="h-4 w-4" />
                        )}
                        Disable Queue
                      </Button>

                      {/* Existing admin buttons */}
                      <Button
                        onClick={handleAdminMatchmaking}
                        disabled={adminActionLoading}
                        className="flex items-center gap-2"
                      >
                        {adminActionLoading ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <Heart className="h-4 w-4" />
                        )}
                        Run Matchmaking
                      </Button>
                      
                      <Button
                        onClick={handleDeleteAllPairings}
                        disabled={adminActionLoading}
                        variant="destructive"
                        size="sm"
                      >
                        {adminActionLoading ? (
                          <Loader2 className="h-4 w-4 animate-spin mr-2" />
                        ) : (
                          <Users className="h-4 w-4 mr-2" />
                        )}
                        Delete All Pairings
                      </Button>
                    </div>

                    {/* Admin Messages */}
                    {(adminMessage || queueConfigMessage) && (
                      <div className={`p-3 rounded-lg text-sm ${
                        (adminMessage || queueConfigMessage)?.includes('Error') 
                          ? 'bg-red-500/10 border border-red-500/20 text-red-400' 
                          : 'bg-green-500/10 border border-green-500/20 text-green-400'
                      }`}>
                        {queueConfigMessage || adminMessage}
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
            </motion.div>
          )}

          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
            <div className="mb-8">
              <h1 className="text-4xl font-bold text-white mb-2">Don't Catch Feelings Challenge</h1>
              <p className="text-slate-400">Find your perfect match and see if you can avoid catching feelings!</p>
            </div>

            {error && (
              <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4 mb-6">
                <p className="text-red-400">{error}</p>
              </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Current Status Section */}
              <Card className="bg-slate-800/50 border-slate-700">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-white">
                    <Heart className="h-5 w-5 text-primary" />
                    Current Status
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {currentPairing && pairedUser ? (
                    <div className="space-y-4">
                      {/* 🔍 DEBUG: Add logging to see what data we have */}
                      {(() => {
                        console.log('=== PAIRING DEBUG DATA ===');
                        console.log('currentPairing:', currentPairing);
                        console.log('user?.id:', user?.id);
                        console.log('currentPairing.user1Id:', currentPairing.user1Id);
                        console.log('currentPairing.user2Id:', currentPairing.user2Id);
                        console.log('Available pairing properties:', Object.keys(currentPairing));
                        
                        // Check for user preference data
                        console.log('User1 data check:');
                        console.log('- user1Age:', currentPairing?.user1Age);
                        console.log('- user1Gender:', currentPairing?.user1Gender);
                        console.log('- user1Region:', currentPairing?.user1Region);
                        console.log('- user1Rank:', currentPairing?.user1Rank);
                        
                        console.log('User2 data check:');
                        console.log('- user2Age:', currentPairing?.user2Age);
                        console.log('- user2Gender:', currentPairing?.user2Gender);
                        console.log('- user2Region:', currentPairing?.user2Region);
                        console.log('- user2Rank:', currentPairing?.user2Rank);
                        
                        console.log('========================');
                        return null;
                      })()}
                      
                      <TooltipProvider>
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-3">
                            <Avatar className="h-12 w-12">
                              <AvatarImage src={pairedUser?.avatar} alt={pairedUser?.displayName} />
                              <AvatarFallback className="bg-primary/20 text-primary">
                                {pairedUser?.displayName?.charAt(0) || '?'}
                              </AvatarFallback>
                            </Avatar>
                            
                            <div className="flex-1">
                              <p className="font-medium text-white">{pairedUser?.displayName || 'Unknown User'}</p>
                              
                              {/* Add debugging for the other user's data */}
                              {(() => {
                                const isUser1 = currentPairing?.user1Id === user?.id;
                                const otherUserAge = isUser1 ? currentPairing?.user2Age : currentPairing?.user1Age;
                                const otherUserGender = isUser1 ? currentPairing?.user2Gender : currentPairing?.user1Gender;
                                const otherUserRegion = isUser1 ? currentPairing?.user2Region : currentPairing?.user1Region;
                                const otherUserRank = isUser1 ? currentPairing?.user2Rank : currentPairing?.user1Rank;
                                
                                console.log('=== OTHER USER DATA ===');
                                console.log('Am I user1?', isUser1);
                                console.log('Other user age:', otherUserAge);
                                console.log('Other user gender:', otherUserGender);
                                console.log('Other user region:', otherUserRegion);
                                console.log('Other user rank:', otherUserRank);
                                console.log('======================');
                                
                                return null;
                              })()}
                              
                              {/* User Preferences Row */}
                              <TooltipProvider>
                                <div className="flex items-center gap-2 mt-2">
                                  <Tooltip>
                                    <TooltipTrigger>
                                      <div className="flex items-center gap-1 p-1 bg-slate-700/50 rounded cursor-pointer">
                                        <User className="h-3 w-3 text-blue-400" />
                                        <span className="text-xs text-slate-300">{currentPairing?.user1Id === user?.id ? currentPairing?.user2Age : currentPairing?.user1Age}</span>
                                      </div>
                                    </TooltipTrigger>
                                    <TooltipContent className="bg-slate-900 border-slate-700">
                                      <p className="text-sm text-slate-200">
                                        Age: <span className="font-semibold text-blue-400">{currentPairing?.user1Id === user?.id ? currentPairing?.user2Age : currentPairing?.user1Age}</span>
                                      </p>
                                    </TooltipContent>
                                  </Tooltip>
                                  
                                  <Tooltip>
                                    <TooltipTrigger>
                                      <div className="flex items-center gap-1 p-1 bg-slate-700/50 rounded cursor-pointer">
                                        <div className="h-3 w-3 bg-pink-500 rounded-full" />
                                        <span className="text-xs text-slate-300">
                                          {GENDERS.find(g => g.value === (currentPairing?.user1Id === user?.id ? currentPairing?.user2Gender : currentPairing?.user1Gender))?.label || 'N/A'}
                                        </span>
                                      </div>
                                    </TooltipTrigger>
                                    <TooltipContent className="bg-slate-900 border-slate-700">
                                      <p className="text-sm text-slate-200">
                                        Gender: <span className="font-semibold text-pink-400">
                                          {GENDERS.find(g => g.value === (currentPairing?.user1Id === user?.id ? currentPairing?.user2Gender : currentPairing?.user1Gender))?.label || 'Not specified'}
                                        </span>
                                      </p>
                                    </TooltipContent>
                                  </Tooltip>
                                  
                                  <Tooltip>
                                    <TooltipTrigger>
                                      <div className="flex items-center gap-1 p-1 bg-slate-700/50 rounded cursor-pointer">
                                        <MapPin className="h-3 w-3 text-green-400" />
                                        <span className="text-xs text-slate-300">
                                          {REGIONS.find(r => r.value === (currentPairing?.user1Id === user?.id ? currentPairing?.user2Region : currentPairing?.user1Region))?.label || 'N/A'}
                                        </span>
                                      </div>
                                    </TooltipTrigger>
                                    <TooltipContent className="bg-slate-900 border-slate-700">
                                      <p className="text-sm text-slate-200">
                                        Region: <span className="font-semibold text-green-400">
                                          {REGIONS.find(r => r.value === (currentPairing?.user1Id === user?.id ? currentPairing?.user2Region : currentPairing?.user1Region))?.label || 'Not specified'}
                                        </span>
                                      </p>
                                    </TooltipContent>
                                  </Tooltip>
                                  
                                  <Tooltip>
                                    <TooltipTrigger>
                                      <div className="flex items-center gap-1 p-1 bg-slate-700/50 rounded cursor-pointer">
                                        <Trophy className="h-3 w-3 text-yellow-400" />
                                        <span className="text-xs text-slate-300">
                                          {RANKS.find(r => r.value === (currentPairing?.user1Id === user?.id ? currentPairing?.user2Rank : currentPairing?.user1Rank))?.label || 'N/A'}
                                        </span>
                                      </div>
                                    </TooltipTrigger>
                                    <TooltipContent className="bg-slate-900 border-slate-700">
                                      <p className="text-sm text-slate-200">
                                        VALORANT Rank: <span className="font-semibold text-yellow-400">
                                          {RANKS.find(r => r.value === (currentPairing?.user1Id === user?.id ? currentPairing?.user2Rank : currentPairing?.user1Rank))?.label || 'Not specified'}
                                        </span>
                                      </p>
                                    </TooltipContent>
                                  </Tooltip>
                                </div>
                              </TooltipProvider>
                            </div>
                          </div>
                          
                          <Tooltip>
                            <TooltipTrigger>
                              <div className="flex items-center gap-1 text-slate-400 cursor-pointer">
                                <Info className="h-4 w-4" />
                                <span className="text-sm">Match Info</span>
                              </div>
                            </TooltipTrigger>
                            <TooltipContent className="bg-slate-900 border-slate-700 max-w-xs">
                              <div className="space-y-2">
                                <p className="text-sm font-medium text-white">Match Details</p>
                                <div className="grid grid-cols-2 gap-2 text-xs">
                                  <div className="flex items-center gap-1">
                                    <Calendar className="h-3 w-3 text-blue-400" />
                                    <span className="text-slate-300">Matched:</span>
                                  </div>
                                  <span className="text-slate-200">{formatDate(currentPairing.matchedAt)}</span>
                                  
                                  <div className="flex items-center gap-1">
                                    <MessageCircle className="h-3 w-3 text-green-400" />
                                    <span className="text-slate-300">Channel:</span>
                                  </div>
                                  <span className="text-slate-200">#{currentPairing.discordChannelId}</span>
                                </div>
                              </div>
                            </TooltipContent>
                          </Tooltip>
                        </div>
                      </TooltipProvider>
                    </div>
                  ) : queueStatus.inQueue ? (
                    <div className="text-center py-6">
                      <Users className="h-12 w-12 text-primary mx-auto mb-4" />
                      <h3 className="text-lg font-semibold text-white mb-2">In Matchmaking Queue</h3>
                      
                      <div className="flex items-center justify-center gap-2 mb-2">
                        <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
                        <span className="text-xs text-slate-400">
                          {isConnected ? 'Live updates' : 'Reconnecting...'}
                        </span>
                      </div>
                      
                      <div className="space-y-2 mb-4">
                        {queueStatus.queuePosition && queueStatus.totalQueueSize && (
                          <p className="text-sm text-slate-300">
                            Position: <span className="font-semibold text-primary">{queueStatus.queuePosition}</span> of {queueStatus.totalQueueSize}
                          </p>
                        )}
                        {queueStatus.estimatedWaitTime && (
                          <p className="text-sm text-slate-300">
                            Estimated wait: <span className="font-semibold text-primary">{queueStatus.estimatedWaitTime}</span> minutes
                          </p>
                        )}
                        {queueStatus.queuedAt && (
                          <p className="text-xs text-slate-500">
                            Queued since {formatDate(queueStatus.queuedAt)}
                          </p>
                        )}
                      </div>
                      
                      <Button 
                        variant="outline" 
                        onClick={leaveQueue}
                        disabled={actionLoading}
                      >
                        {actionLoading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                        Leave Queue
                      </Button>
                    </div>
                  ) : (
                    <div className="text-center py-6">
                      <Heart className="h-12 w-12 text-slate-500 mx-auto mb-4" />
                      <h3 className="text-lg font-semibold text-white mb-2">No Active Pairing</h3>
                      <p className="text-slate-400">Join the matchmaking queue to find your match!</p>
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Join Queue Section - Hide if queue disabled */}
              {!currentPairing && !queueStatus.inQueue && isQueueEnabled && (
                <QueueJoinForm onJoinQueue={handleJoinQueue} loading={actionLoading} />
              )}

              {/* Pairing History */}
              {pairingHistory.length > 0 && (
                <Card className="bg-slate-800/50 border-slate-700 lg:col-span-2">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-white">
                      <MessageCircle className="h-5 w-5 text-primary" />
                      Pairing History
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-3">
                      {pairingHistory.slice(0, 5).map((pairing) => {
                        const user1Profile = userProfiles[pairing.user1Id];
                        const user2Profile = userProfiles[pairing.user2Id];
                        
                        return (
                          <div key={pairing.id} className="flex items-center justify-between p-3 bg-slate-700/50 rounded-lg">
                            <div className="flex items-center gap-3">
                              {/* User 1 */}
                              <div 
                                className="flex items-center gap-2 cursor-pointer hover:bg-slate-600/30 p-1 rounded transition-colors"
                                onClick={(e) => handleUserClick(pairing.user1Id, e)}
                              >
                                <Avatar className="h-8 w-8">
                                  <AvatarImage src={user1Profile?.avatar} />
                                  <AvatarFallback>
                                    {user1Profile?.displayName?.[0] || user1Profile?.username?.[0] || '?'}
                                  </AvatarFallback>
                                </Avatar>
                                <span className="text-white font-medium text-sm">
                                  {user1Profile?.displayName || user1Profile?.username || 'Unknown User'}
                                </span>
                              </div>
                              
                              {/* Plus separator */}
                              <span className="text-slate-400 font-bold">+</span>
                              
                              {/* User 2 */}
                              <div 
                                className="flex items-center gap-2 cursor-pointer hover:bg-slate-600/30 p-1 rounded transition-colors"
                                onClick={(e) => handleUserClick(pairing.user2Id, e)}
                              >
                                <Avatar className="h-8 w-8">
                                  <AvatarImage src={user2Profile?.avatar} />
                                  <AvatarFallback>
                                    {user2Profile?.displayName?.[0] || user2Profile?.username?.[0] || '?'}
                                  </AvatarFallback>
                                </Avatar>
                                <span className="text-white font-medium text-sm">
                                  {user2Profile?.displayName || user2Profile?.username || 'Unknown User'}
                                </span>
                              </div>
                            </div>
                            
                            <div className="flex flex-col items-end gap-1">
                              <p className="text-sm text-slate-400">
                                {formatDate(pairing.matchedAt)} - {pairing.activeDays} days
                              </p>
                              <div className="flex items-center gap-2">
                                <Badge variant={pairing.active ? "default" : "secondary"}>
                                  {pairing.active ? "Active" : "Ended"}
                                </Badge>
                                <Badge variant="outline">
                                  {pairing.compatibilityScore}% Match
                                </Badge>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </CardContent>
                </Card>
              )}
            </div>
          </motion.div>
          
          {/* User Profile Modal */}
          {showUserProfileModal && selectedUserProfile && (
            <UserProfileModal 
              isOpen={showUserProfileModal}
              onClose={handleCloseUserProfileModal}
              userProfile={selectedUserProfile}
              position={userProfileModalPosition}
            />
          )}
          
          {/* Match Found Modal */}
          {showMatchModal && matchedPairing && (
            <MatchFoundModal 
              pairing={matchedPairing} 
              onClose={handleCloseMatchModal} 
            />
          )}
        </div>
      </main>
    </div>
  );
}