import React, { useState, useEffect, useCallback } from 'react';
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/valorant/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Button } from "@/components/ui/valorant/button"
import { GamepadIcon, Trophy, Plus, ShoppingBag } from "lucide-react"
import httpClient from '@/lib/api/httpClient';
import PostGroupModal from "@/features/GroupCreate";
import Listing from "@/features/Listing";
import { useAuth } from '@/contexts/auth/useAuth';
import { usePartyUpdates } from '@/contexts/PartyUpdates';
import valorantBanner from '@/assets/images/valorant.jpg';
import valorantLogo from '@/assets/images/valorant-logo.png';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { SkeletonPartyListing } from '@/components/ui/SkeletonUI';
import { motion, AnimatePresence } from 'framer-motion';
import { LFGPartyResponseDTO } from '@/contexts/valorant/partyService';

export default function Home() {
  const [parties, setParties] = useState<any[]>([]);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [groupErrorMessage, setGroupErrorMessage] = useState<string | null>(null);
  const { user } = useAuth();
  const { update, clearUpdate, userActiveParty, setUserActiveParty } = usePartyUpdates();
  const [isLoading, setIsLoading] = useState(true);
  const [seenParties, setSeenParties] = useState<Set<string>>(new Set());

  // Add animation variants
  const containerVariants = {
    hidden: { opacity: 0 },
    visible: { 
      opacity: 1,
      transition: { 
        staggerChildren: 0.1
      }
    }
  };

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

  // Load seen parties from localStorage on component mount
  useEffect(() => {
    const storedSeenParties = localStorage.getItem('seenParties');
    if (storedSeenParties) {
      try {
        // Add type assertion to ensure TypeScript knows we're working with strings
        const parsedParties = JSON.parse(storedSeenParties) as string[];
        setSeenParties(new Set<string>(parsedParties));
      } catch (e) {
        console.error('Error parsing seen parties from localStorage:', e);
      }
    }
  }, []);
  
  // Update this function to check if a party is truly "new" to the user
  const isPartyNewToUser = useCallback((party: LFGPartyResponseDTO | any) => {
    // Party is NOT new if:
    // 1. Missing required data
    // 2. Current user is the creator
    if (!party.id || !party.createdAt || party.userId === user?.id) return false;
    
    // Check if party was created recently (within 5 minutes)
    const creationTime = new Date(party.createdAt).getTime();
    const currentTime = new Date().getTime();
    const fiveMinutesInMs = 5 * 60 * 1000;
    const isRecent = (currentTime - creationTime) < fiveMinutesInMs;
    
    // Check if user has already seen this party
    const hasNotSeen = !seenParties.has(party.id);
    
    return isRecent && hasNotSeen;
  }, [seenParties, user?.id]);
  
  // Replace the existing effect that marks parties as seen with this one
  useEffect(() => {
    if (!isLoading && parties.length > 0) {
      // Load seen parties from localStorage with proper type safety
      const loadSeenParties = () => {
        try {
          const storedSeenParties = localStorage.getItem('seenParties');
          if (storedSeenParties) {
            // Add explicit type assertion
            const parsedParties = JSON.parse(storedSeenParties) as string[];
            return new Set<string>(parsedParties);
          }
        } catch (e) {
          console.error('Error parsing seen parties:', e);
        }
        return new Set<string>();
      };
      
      // Start with seen parties from localStorage
      const newSeenParties = loadSeenParties();
      
      // Schedule marking parties as seen after a delay
      const markPartiesAsSeenTimeout = setTimeout(() => {
        // Only mark parties as seen after the user has had time to view them
        parties.forEach(party => newSeenParties.add(party.id));
        
        // Update state and localStorage
        setSeenParties(newSeenParties);
        localStorage.setItem('seenParties', JSON.stringify([...newSeenParties]));
      }, 7000); // Delay of 3 seconds to allow the user to see the "NEW" badges
      
      return () => clearTimeout(markPartiesAsSeenTimeout);
    }
  }, [parties, isLoading]);

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
                    value="shop"
                    className="bg-transparent text-white data-[state=active]:bg-[#FF4655] data-[state=active]:text-white rounded-md px-6 py-2 transition-all hover:bg-transparent hover:text-white"
                  >
                    <ShoppingBag className="w-4 h-4 mr-2" />
                    Shop
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
                      // Skeleton loaders for parties (keep existing code)
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {[...Array(4)].map((_, index) => (
                          <SkeletonPartyListing key={index} />
                        ))}
                      </div>
                    ) : parties.length === 0 ? (
                      // Empty state (keep existing code)
                      <div className="text-center p-8 bg-[#1F2731]/60 rounded-lg border border-white/5">
                        <p className="text-[#8B97A4]">No parties available right now. Create one to get started!</p>
                      </div>
                    ) : (
                      // Replace the existing div with motion.div for the grid
                      <motion.div 
                        className="grid grid-cols-1 md:grid-cols-2 gap-6"
                        variants={containerVariants}
                        initial="hidden"
                        animate="visible"
                      >
                        <AnimatePresence>
                          {parties.map((party) => (
                            <Listing 
                              key={party.id} 
                              party={party} 
                              isNew={
                                // Party is new if:
                                // 1. It's the user's active party (but they're not the creator) OR
                                // 2. It's a recent party the user hasn't seen yet AND they're not the creator
                                (party.id === userActiveParty && party.userId !== user?.id) || 
                                isPartyNewToUser(party)
                              } 
                            />
                          ))}
                        </AnimatePresence>
                      </motion.div>
                    )}
                  </div>
                </TabsContent>

                <TabsContent value="shop" className="mt-6">
                  <div className="text-center py-12 bg-[#1F2731]/30 rounded-lg border border-white/5">
                    <ShoppingBag className="w-12 h-12 mx-auto mb-4 text-white/30" />
                    <h3 className="text-xl font-semibold text-white/80 mb-2">Shop Coming Soon</h3>
                    <p className="text-white/50 max-w-md mx-auto">
                      The shop is currently in development. Check back soon to purchase exclusive items!
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

