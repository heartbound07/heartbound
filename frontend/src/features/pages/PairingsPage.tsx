import { useState } from 'react';
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
import type { JoinQueueRequestDTO } from '@/config/pairingService';
import { useQueueUpdates } from '@/contexts/QueueUpdates';
import { performMatchmaking, deleteAllPairings } from '@/config/pairingService';
import { usePairingUpdates } from '@/contexts/PairingUpdates';
import { MatchFoundModal } from '@/components/modals/MatchFoundModal';

const REGIONS = [
  { value: 'NA_EAST', label: 'NA East' },
  { value: 'NA_WEST', label: 'NA West' },
  { value: 'EU', label: 'Europe' },
  { value: 'ASIA', label: 'Asia' },
  { value: 'OCE', label: 'Oceania' }
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
    leaveQueue 
  } = usePairings();
  const { isConnected } = useQueueUpdates();
  const { pairingUpdate, clearUpdate } = usePairingUpdates();
  
  const [adminActionLoading, setAdminActionLoading] = useState(false);
  const [adminMessage, setAdminMessage] = useState<string | null>(null);

  const [queueForm, setQueueForm] = useState<Omit<JoinQueueRequestDTO, 'userId'>>({
    age: 18,
    region: 'NA_EAST',
    rank: 'SILVER'
  });

  const handleJoinQueue = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!user?.id) {
      console.error('User ID is required to join queue');
      return;
    }
    
    try {
      const queueRequest: JoinQueueRequestDTO = {
        ...queueForm,
        userId: user.id
      };
      await joinQueue(queueRequest);
    } catch (error) {
      // Error is handled by the hook
    }
  };

  const handleAdminMatchmaking = async () => {
    try {
      setAdminActionLoading(true);
      setAdminMessage(null);
      
      const newPairings = await performMatchmaking();
      setAdminMessage(`Successfully created ${newPairings.length} new pairing(s)!`);
      
      // Clear message after 5 seconds
      setTimeout(() => setAdminMessage(null), 5000);
    } catch (error: any) {
      setAdminMessage(`Matchmaking failed: ${error.message}`);
      setTimeout(() => setAdminMessage(null), 5000);
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

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      <div className="container mx-auto p-6">
        {/* Admin Controls */}
        {hasRole('ADMIN') && (
          <motion.div 
            initial={{ opacity: 0, y: -20 }} 
            animate={{ opacity: 1, y: 0 }}
            className="mb-6"
          >
            <Card className="bg-red-900/20 border-red-500/30">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-red-300">
                  <Settings className="h-5 w-5" />
                  Admin Controls
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="flex items-center gap-4">
                  <Button
                    onClick={handleAdminMatchmaking}
                    disabled={adminActionLoading}
                    variant="destructive"
                    size="sm"
                  >
                    {adminActionLoading ? (
                      <Loader2 className="h-4 w-4 animate-spin mr-2" />
                    ) : (
                      <Heart className="h-4 w-4 mr-2" />
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
              <Card className="bg-slate-800/50 border-slate-700">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-white">
                    <Users className="h-5 w-5 text-primary" />
                    Enter the Challenge
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <form onSubmit={handleJoinQueue} className="space-y-4">
                    <div>
                      <Label htmlFor="age" className="text-white">Age</Label>
                      <Input
                        id="age"
                        type="number"
                        min="18"
                        max="100"
                        value={queueForm.age}
                        onChange={(e) => setQueueForm(prev => ({ ...prev, age: parseInt(e.target.value) }))}
                        className="bg-slate-700 border-slate-600 text-white"
                        required
                      />
                    </div>

                    <div>
                      <Label htmlFor="region" className="text-white">Region</Label>
                      <Select
                        value={queueForm.region}
                        onValueChange={(value) => setQueueForm(prev => ({ 
                          ...prev, 
                          region: value as 'NA_EAST' | 'NA_WEST' | 'EU' | 'ASIA' | 'OCE'
                        }))}
                      >
                        <SelectTrigger className="bg-slate-700 border-slate-600 text-white">
                          <SelectValue placeholder="Select region" />
                        </SelectTrigger>
                        <SelectContent>
                          {REGIONS.map((region) => (
                            <SelectItem key={region.value} value={region.value}>
                              {region.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div>
                      <Label htmlFor="rank" className="text-white">Rank</Label>
                      <Select
                        value={queueForm.rank}
                        onValueChange={(value) => setQueueForm(prev => ({ 
                          ...prev, 
                          rank: value as 'IRON' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND' | 'ASCENDANT' | 'IMMORTAL' | 'RADIANT'
                        }))}
                      >
                        <SelectTrigger className="bg-slate-700 border-slate-600 text-white">
                          <SelectValue placeholder="Select rank" />
                        </SelectTrigger>
                        <SelectContent>
                          {RANKS.map((rank) => (
                            <SelectItem key={rank.value} value={rank.value}>
                              {rank.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <Button 
                      type="submit" 
                      className="w-full" 
                      disabled={actionLoading}
                    >
                      {actionLoading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                      Join Matchmaking Queue
                    </Button>
                  </form>
                </CardContent>
              </Card>
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
                    {pairingHistory.slice(0, 5).map((pairing) => (
                      <div key={pairing.id} className="flex items-center justify-between p-3 bg-slate-700/50 rounded-lg">
                        <div>
                          <p className="text-white font-medium">
                            Pairing #{pairing.id}
                          </p>
                          <p className="text-sm text-slate-400">
                            {formatDate(pairing.matchedAt)} - {pairing.activeDays} days
                          </p>
                        </div>
                        <div className="flex items-center gap-2">
                          <Badge variant={pairing.active ? "default" : "secondary"}>
                            {pairing.active ? "Active" : "Ended"}
                          </Badge>
                          <Badge variant="outline">
                            {pairing.compatibilityScore}% Match
                          </Badge>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}
          </div>
        </motion.div>
      </div>

      {pairingUpdate?.eventType === 'MATCH_FOUND' && (
        <MatchFoundModal 
          pairing={pairingUpdate.pairing}
          onClose={() => clearUpdate()}
        />
      )}
    </div>
  );
} 