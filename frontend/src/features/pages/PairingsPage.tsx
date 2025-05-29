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
import { Loader2, Heart, Users, Clock, Trophy, MessageCircle, Settings } from 'lucide-react';
import { motion } from 'framer-motion';
import type { JoinQueueRequestDTO, PairingDTO } from '@/config/pairingService';
import { useQueueUpdates } from '@/contexts/QueueUpdates';
import { performMatchmaking, deleteAllPairings } from '@/config/pairingService';
import { usePairingUpdates } from '@/contexts/PairingUpdates';
import { MatchFoundModal } from '@/components/modals/MatchFoundModal';
import { UserProfileModal } from '@/components/modals/UserProfileModal';
import { getUserProfiles, type UserProfileDTO } from '@/config/userService';
import { DashboardNavigation } from '@/components/Sidebar';

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

// Extract form component for better organization
const QueueJoinForm = ({ onJoinQueue, loading }: { 
  onJoinQueue: (data: JoinQueueRequestDTO) => Promise<void>; 
  loading: boolean;
}) => {
  const [age, setAge] = useState<string>('');
  const [region, setRegion] = useState<string>('');
  const [rank, setRank] = useState<string>('');
  
  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    
    // Input validation
    const ageNum = parseInt(age);
    if (!ageNum || ageNum < 13 || ageNum > 100) {
      throw new Error('Please enter a valid age between 13 and 100');
    }
    
    if (!region || !rank) {
      throw new Error('Please select both region and rank');
    }
    
    await onJoinQueue({
      userId: '', // Will be set by parent
      age: ageNum,
      region: region as any,
      rank: rank as any
    });
  }, [age, region, rank, onJoinQueue]);

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

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-900">
      <DashboardNavigation />
      
      <div className="pl-64 transition-all duration-300">
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
                  <div className="flex flex-wrap gap-3 items-center">
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
                    {adminMessage && (
                      <p className={`text-sm ${adminMessage.includes('failed') ? 'text-red-300' : 'text-green-300'}`}>
                        {adminMessage}
                      </p>
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
                      <div className="flex items-center gap-4 p-4 bg-primary/10 rounded-lg border border-primary/20">
                        <Avatar className="h-16 w-16">
                          <AvatarImage src={pairedUser.avatar} alt={pairedUser.username} />
                          <AvatarFallback>{pairedUser.username.charAt(0)}</AvatarFallback>
                        </Avatar>
                        <div className="flex-1">
                          <h3 className="text-lg font-semibold text-white">
                            {pairedUser.displayName || pairedUser.username}
                          </h3>
                          <p className="text-slate-400">@{pairedUser.username}</p>
                          <div className="flex items-center gap-2 mt-2">
                            <Badge variant="secondary">
                              <Trophy className="h-3 w-3 mr-1" />
                              {currentPairing.compatibilityScore}% Match
                            </Badge>
                            <Badge variant="outline">
                              <Clock className="h-3 w-3 mr-1" />
                              {currentPairing.activeDays} days
                            </Badge>
                          </div>
                        </div>
                      </div>
                      
                      <div className="grid grid-cols-2 gap-4 text-center">
                        <div className="p-3 bg-slate-700/50 rounded-lg">
                          <div className="text-2xl font-bold text-primary">{currentPairing.messageCount}</div>
                          <div className="text-sm text-slate-400">Messages</div>
                        </div>
                        <div className="p-3 bg-slate-700/50 rounded-lg">
                          <div className="text-2xl font-bold text-primary">{currentPairing.wordCount}</div>
                          <div className="text-sm text-slate-400">Words</div>
                        </div>
                      </div>
                      
                      <p className="text-sm text-slate-400">
                        Matched on {formatDate(currentPairing.matchedAt)}
                      </p>
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

              {/* Join Queue Section */}
              {!currentPairing && !queueStatus.inQueue && (
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
      </div>
    </div>
  );
}