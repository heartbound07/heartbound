import React, { useState, useEffect, useCallback } from 'react';
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/valorant/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Button } from "@/components/ui/valorant/button"
import { GamepadIcon, Trophy, Plus } from "lucide-react"
import httpClient from '@/lib/api/httpClient';
import PostGroupModal from "@/features/GroupCreate";
import Listing from "@/features/Listing";
import { useAuth } from '@/contexts/auth/useAuth';
import { usePartyUpdates } from '@/contexts/PartyUpdates';

export default function Home() {
  const [parties, setParties] = useState<any[]>([]);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [groupErrorMessage, setGroupErrorMessage] = useState<string | null>(null);
  const { user } = useAuth();
  const { update } = usePartyUpdates();

  // Define a reusable function to fetch parties.
  const fetchParties = useCallback(async () => {
    try {
      const response = await httpClient.get('/api/lfg/parties');
      const data = response.data;
      const partiesArray = Array.isArray(data)
        ? data
        : data.content || [];
      setParties(partiesArray);
    } catch (err) {
      console.error("Error fetching parties:", err);
    }
  }, []);

  // On mount, fetch the initial list of parties.
  useEffect(() => {
    fetchParties();
  }, [fetchParties]);

  // Whenever a new party update is received via WebSocket, refetch the parties.
  useEffect(() => {
    if (update) {
      fetchParties();
    }
  }, [update, fetchParties]);

  // Auto-dismiss error message after 3 seconds if set
  useEffect(() => {
    if (groupErrorMessage) {
      const timer = setTimeout(() => setGroupErrorMessage(null), 3000);
      return () => clearTimeout(timer);
    }
  }, [groupErrorMessage]);

  // Check if the current user already has a party
  const userHasParty = user ? parties.some(party => party.userId === user.id) : false;

  return (
    <div className="min-h-screen bg-[#0F1923] text-white font-sans">
      {/* Background elements */}
      <div className="fixed inset-0 bg-[#0F1923] z-0">
        <div className="absolute inset-0 bg-gradient-to-br from-[#FF4655]/10 to-transparent opacity-50"></div>
        <div className="absolute top-0 left-0 w-full h-64 bg-gradient-to-b from-[#1F2731] to-transparent opacity-30"></div>
      </div>

      {/* Content */}
      <div className="relative z-10">
        {/* Header */}
        <header className="border-b border-[#1F2731] p-4">
          <div className="max-w-6xl mx-auto flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 bg-[#FF4655] flex items-center justify-center rounded">
                <span className="text-white font-bold text-xl">V</span>
              </div>
              <h1 className="text-2xl font-bold tracking-tight">VALORANT</h1>
            </div>
          </div>
        </header>

        <main className="max-w-6xl mx-auto p-6">
          <div className="flex-1 space-y-8">
            {/* Tabs */}
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
            </Tabs>

            {/* Filters and Create Group Button */}
            <div className="flex items-center gap-4 mt-4">
              <Select>
                <SelectTrigger className="bg-[#1F2731] border-[#2C3A47] w-40 text-[#8B97A4] transition-colors hover:bg-[#FF4655]/10 hover:text-white">
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
                <SelectTrigger className="bg-[#1F2731] border-[#2C3A47] w-40 text-[#8B97A4] transition-colors hover:bg-[#FF4655]/10 hover:text-white">
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
                onClick={() => {
                  if (userHasParty) {
                    setGroupErrorMessage("Error: You can only create one party");
                  } else {
                    setShowCreateForm(true);
                  }
                }}
                className="text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 px-4 py-2 bg-[#FF4655] hover:bg-[#FF4655]/90 text-white rounded-full w-10 h-10 flex items-center justify-center"
              >
                <Plus className="w-4 h-4" />
              </Button>
            </div>

            {/* Error Message Bubble */}
            {groupErrorMessage && (
              <div className="bg-red-200 text-red-900 border border-red-300 rounded-lg px-4 py-2 mt-4 text-center">
                {groupErrorMessage}
              </div>
            )}

            {/* Group Listings */}
            <div className="mt-8">
              {parties.length === 0 ? (
                <div className="text-center text-gray-400">
                  No available groups at the moment.
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-6">
                  {parties.map((party) => (
                    <Listing key={party.id} party={party} />
                  ))}
                </div>
              )}
            </div>
          </div>
        </main>
      </div>

      {/* Group Create Modal */}
      {showCreateForm && (
        <PostGroupModal
          onClose={() => setShowCreateForm(false)}
          onPartyCreated={(newParty) => {
            // Optionally update the UI immediately while the WebSocket update refreshes the list.
            setParties((prevParties) => [{ ...newParty, isNew: true }, ...prevParties]);
            setShowCreateForm(false);
          }}
        />
      )}
    </div>
  )
}

