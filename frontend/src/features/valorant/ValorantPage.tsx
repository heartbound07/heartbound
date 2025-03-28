import React, { useState, useEffect, useCallback } from 'react';
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/valorant/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Button } from "@/components/ui/valorant/button"
import { GamepadIcon, Trophy, Plus } from "lucide-react"
import httpClient from '@/lib/api/httpClient';
import PostGroupModal from "@/features/GroupCreate";
import Listing from "@/features/Listing";
import { useAuth } from '@/contexts/auth/useAuth';
import { usePartyUpdates } from '@/contexts/PartyUpdates';
import valorantBanner from '@/assets/images/valorant.jpg';
import valorantLogo from '@/assets/images/valorant-logo.png';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { SkeletonPartyListing } from '@/components/ui/SkeletonUI';

export default function Home() {
  const [parties, setParties] = useState<any[]>([]);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [groupErrorMessage, setGroupErrorMessage] = useState<string | null>(null);
  const { user } = useAuth();
  const { update, clearUpdate, userActiveParty, setUserActiveParty } = usePartyUpdates();
  const [isLoading, setIsLoading] = useState(true);

  // Define a reusable function to fetch parties.
  const fetchParties = useCallback(async () => {
    try {
      setIsLoading(true);
      const response = await httpClient.get('/api/lfg/parties');
      const data = response.data;
      const partiesArray = Array.isArray(data)
        ? data
        : data.content || [];
      
      // Sort parties by createdAt in descending order (newest first)
      const sortedParties = [...partiesArray].sort((a, b) => {
        const dateA = new Date(a.createdAt).getTime();
        const dateB = new Date(b.createdAt).getTime();
        return dateB - dateA; // Descending order (newest first)
      });
      
      setParties(sortedParties);
    } catch (err) {
      console.error("Error fetching parties:", err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // On mount, fetch the initial list of parties.
  useEffect(() => {
    fetchParties();
  }, [fetchParties]);

  // Add effect to refresh parties when there's a relevant party update
  useEffect(() => {
    if (update) {
      const relevantEvents = [
        'PARTY_CREATED', 
        'PARTY_UPDATED', 
        'PARTY_DELETED', 
        'PARTY_JOIN_REQUEST',
        'PARTY_JOIN_REQUEST_ACCEPTED',
        'PARTY_JOIN_REQUEST_REJECTED',
        'PARTY_JOINED',
        'PARTY_LEFT',
        'PARTY_USER_KICKED'
      ];
      
      if (relevantEvents.includes(update.eventType)) {
        fetchParties();
        clearUpdate(); // Clear update after handling
      }
    }
  }, [update, fetchParties, clearUpdate]);

  // Auto-dismiss error message after 3 seconds.
  useEffect(() => {
    if (groupErrorMessage) {
      const timer = setTimeout(() => setGroupErrorMessage(null), 3000);
      return () => clearTimeout(timer);
    }
  }, [groupErrorMessage]);

  // Check if user already has a party
  const userHasParty = userActiveParty !== null;

  // Create Group Button Handler
  const handleCreateGroupClick = () => {
    // Check if user is already in a party
    const userHasParty = userActiveParty !== null;
    
    // If user has a party but we're not sure if it's valid, let's verify
    if (userHasParty) {
      try {
        // Optional: Verify party existence before blocking creation
        httpClient.get(`/api/lfg/parties/${userActiveParty}`)
          .then(() => {
            // Party exists, show error
            setGroupErrorMessage("Error: You can only be in one party at a time");
          })
          .catch(err => {
            if (err.response?.status === 404) {
              // Party doesn't exist, clear state and allow creation
              setUserActiveParty(null);
              setShowCreateForm(true);
            } else {
              // Other error, show message
              setGroupErrorMessage("Error: You can only be in one party at a time");
            }
          });
      } catch (err) {
        // If verification fails, assume user can create a party
        setGroupErrorMessage("Error: You can only be in one party at a time");
      }
    } else {
      // User doesn't have a party, allow creation
      setShowCreateForm(true);
    }
  };

  return (
    <div className="min-h-screen bg-[#0F1923] text-white font-sans">
      {/* Banner and background elements */}
      <div className="fixed inset-0 bg-[#0F1923] z-0">
        <div className="absolute inset-0 bg-gradient-to-br from-[#FF4655]/10 to-transparent opacity-50"></div>
        <div className="absolute top-0 left-0 w-full h-64 bg-gradient-to-b from-[#1F2731] to-transparent opacity-30"></div>
      </div>

      {/* Content */}
      <div className="relative z-10">
        {/* Hero Banner Section */}
        <div className="relative w-full h-64 md:h-80 overflow-hidden">
          <img 
            src={valorantBanner} 
            alt="Valorant Banner" 
            className="w-full h-full object-cover object-center"
          />
          <div className="absolute inset-0 bg-gradient-to-b from-[#0F1923]/70 to-[#0F1923]"></div>
          
          {/* Header Content */}
          <div className="absolute inset-x-0 top-0 p-6">
            <div className="max-w-6xl mx-auto flex items-center gap-4">
              <img 
                src={valorantLogo} 
                alt="Valorant Logo" 
                className="w-12 h-12 object-contain"
              />
              <h1 className="text-3xl font-bold tracking-tight text-white drop-shadow-md">
                VALORANT
              </h1>
            </div>
          </div>
          
          {/* Feature Text */}
          <div className="absolute bottom-0 left-0 right-0 p-6">
            <div className="max-w-6xl mx-auto">
            </div>
          </div>
        </div>

        <main className="max-w-6xl mx-auto p-6">
          <div className="space-y-8">
            {/* Tabs */}
            <div className="bg-[#1F2731]/60 backdrop-blur-sm rounded-xl p-4 shadow-lg border border-white/5">
              <Tabs defaultValue="play" className="w-full">
                <TabsList className="bg-[#1F2731] p-1 rounded-lg">
                  <TabsTrigger
                    value="play"
                    className="bg-transparent text-white data-[state=active]:bg-[#FF4655] data-[state=active]:text-white rounded-md px-6 py-2 transition-all hover:bg-transparent hover:text-white"
                  >
                    <GamepadIcon className="w-4 h-4 mr-2" />
                    Play
                  </TabsTrigger>
                  <TabsTrigger
                    value="leaderboard"
                    className="bg-transparent text-white data-[state=active]:bg-[#FF4655] data-[state=active]:text-white rounded-md px-6 py-2 transition-all hover:bg-transparent hover:text-white"
                  >
                    <Trophy className="w-4 h-4 mr-2" />
                    Leaderboard
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="play" className="mt-6">
                  {/* Filters and Create Group Button */}
                  <div className="flex flex-wrap items-center gap-4 mb-6">
                    <div className="flex items-center gap-3">
                      <Select>
                        <SelectTrigger className="bg-[#1F2731] border-[#2C3A47] w-full md:w-40 text-[#8B97A4] transition-colors hover:bg-[#FF4655]/10 hover:text-white">
                          <SelectValue placeholder="All Servers" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="all">All Servers</SelectItem>
                          <SelectItem value="na">NA</SelectItem>
                          <SelectItem value="eu">EU</SelectItem>
                          <SelectItem value="asia">Asia</SelectItem>
                        </SelectContent>
                      </Select>

                      <Select>
                        <SelectTrigger className="bg-[#1F2731] border-[#2C3A47] w-full md:w-40 text-[#8B97A4] transition-colors hover:bg-[#FF4655]/10 hover:text-white">
                          <SelectValue placeholder="All Ranks" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="all">All Ranks</SelectItem>
                          <SelectItem value="IRON">Iron</SelectItem>
                          <SelectItem value="BRONZE">Bronze</SelectItem>
                          <SelectItem value="SILVER">Silver</SelectItem>
                          <SelectItem value="GOLD">Gold</SelectItem>
                          <SelectItem value="PLATINUM">Platinum</SelectItem>
                          <SelectItem value="DIAMOND">Diamond</SelectItem>
                          <SelectItem value="ASCENDANT">Ascendant</SelectItem>
                          <SelectItem value="IMMORTAL">Immortal</SelectItem>
                          <SelectItem value="RADIANT">Radiant</SelectItem>
                        </SelectContent>
                      </Select>
                      
                      <Button
                        onClick={handleCreateGroupClick}
                        className="text-sm font-medium transition-all bg-[#FF4655] hover:bg-[#FF4655]/90 text-white rounded-full w-10 h-10 flex items-center justify-center shadow-md hover:shadow-lg hover:scale-105"
                      >
                        <Plus className="w-4 h-4" />
                      </Button>
                    </div>
                  </div>

                  {/* Error Message Bubble */}
                  {groupErrorMessage && (
                    <div className="bg-[#FF4655]/10 text-[#FF4655] border border-[#FF4655]/30 rounded-lg px-4 py-3 mt-4 text-center animate-fadeIn">
                      {groupErrorMessage}
                    </div>
                  )}

                  {/* Group Listings */}
                  <div className="mt-8">
                    {isLoading ? (
                      // Show skeleton loading UI for parties
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {Array(6).fill(0).map((_, index) => (
                          <SkeletonPartyListing key={`skeleton-listing-${index}`} theme="valorant" />
                        ))}
                      </div>
                    ) : parties.length === 0 ? (
                      <div className="text-center py-12 bg-[#1F2731]/30 rounded-lg border border-white/5">
                        <GamepadIcon className="w-12 h-12 mx-auto mb-4 text-white/30" />
                        <h3 className="text-xl font-semibold text-white/80 mb-2">No Parties Available</h3>
                        <p className="text-white/50 max-w-md mx-auto">
                          There are no available groups at the moment. Be the first to create one!
                        </p>
                      </div>
                    ) : (
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {parties.map((party) => (
                          <Listing key={party.id} party={party} />
                        ))}
                      </div>
                    )}
                  </div>
                </TabsContent>

                <TabsContent value="leaderboard" className="mt-6">
                  <div className="text-center py-12 bg-[#1F2731]/30 rounded-lg border border-white/5">
                    <Trophy className="w-12 h-12 mx-auto mb-4 text-white/30" />
                    <h3 className="text-xl font-semibold text-white/80 mb-2">Leaderboard Coming Soon</h3>
                    <p className="text-white/50 max-w-md mx-auto">
                      The leaderboard feature is currently in development. Check back soon!
                    </p>
                  </div>
                </TabsContent>
              </Tabs>
            </div>
          </div>
        </main>
      </div>

      {/* Group Create Modal */}
      {showCreateForm && (
        <PostGroupModal
          onClose={() => setShowCreateForm(false)}
          onPartyCreated={(newParty) => {
            setParties((prevParties) => [{ ...newParty, isNew: true }, ...prevParties]);
            setShowCreateForm(false);
          }}
        />
      )}
    </div>
  )
}

