import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/valorant/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Button } from "@/components/ui/valorant/button"
import { GamepadIcon, Trophy, Plus } from "lucide-react"
import httpClient from '@/lib/api/httpClient';

export default function Home() {
  const [parties, setParties] = useState<any[]>([]);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newPartyData, setNewPartyData] = useState({
    title: '',
    description: '',
    rank: '',
    region: '',
    voiceChat: false,
    expiresIn: 30,
    maxPlayers: 5
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();

  // Fetch parties from API on component mount using httpClient (which attaches the auth token)
  useEffect(() => {
    async function fetchParties() {
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
    }
    fetchParties();
  }, []);

  // OnChange handler for the create party form inputs
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    let newValue: string | boolean = value;
    if (e.target instanceof HTMLInputElement && e.target.type === 'checkbox') {
      newValue = e.target.checked;
    }
    setNewPartyData(prev => ({
      ...prev,
      [name]: newValue,
    }));
  };

  // Submit the new party (group) creation form and redirect to the party details page on success
  const handleCreateParty = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    const payload = {
      game: "Valorant",
      title: newPartyData.title,
      description: newPartyData.description,
      requirements: {
         rank: newPartyData.rank,
         region: newPartyData.region,
         voiceChat: newPartyData.voiceChat
      },
      expiresIn: parseInt(newPartyData.expiresIn.toString(), 10),
      maxPlayers: parseInt(newPartyData.maxPlayers.toString(), 10)
    };

    try {
      const res = await httpClient.post('/api/lfg/parties', payload);
      // After the party is successfully created, redirect to the party details page
      navigate(`/dashboard/valorant/${res.data.id}`);
      setShowCreateForm(false);
      setNewPartyData({
        title: '',
        description: '',
        rank: '',
        region: '',
        voiceChat: false,
        expiresIn: 30,
        maxPlayers: 5
      });
    } catch (err) {
      console.error("Error creating party:", err);
      setError('Error creating group.');
    } finally {
      setLoading(false);
    }
  };

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
            <div className="flex items-center gap-4">
              <Select>
                <SelectTrigger className="bg-[#1F2731] border-[#2C3A47] w-40 text-[#8B97A4]">
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
                <SelectTrigger className="bg-[#1F2731] border-[#2C3A47] w-40 text-[#8B97A4]">
                  <SelectValue placeholder="All Ranks" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Ranks</SelectItem>
                  <SelectItem value="iron">Iron</SelectItem>
                  <SelectItem value="bronze">Bronze</SelectItem>
                  <SelectItem value="silver">Silver</SelectItem>
                  <SelectItem value="gold">Gold</SelectItem>
                  <SelectItem value="platinum">Platinum</SelectItem>
                  <SelectItem value="diamond">Diamond</SelectItem>
                  <SelectItem value="ascendant">Ascendant</SelectItem>
                  <SelectItem value="immortal">Immortal</SelectItem>
                  <SelectItem value="radiant">Radiant</SelectItem>
                </SelectContent>
              </Select>

              <Button onClick={() => setShowCreateForm(!showCreateForm)}>
                <Plus className="w-4 h-4 mr-2" />
                Create Group
              </Button>
            </div>

            {/* Create Group Form */}
            {showCreateForm && (
              <form onSubmit={handleCreateParty} className="mt-4 space-y-4 bg-white/10 p-4 rounded-lg">
                {error && <div className="text-red-400">{error}</div>}
                <div>
                  <label className="block text-sm mb-1">Group Title</label>
                  <input
                    type="text"
                    name="title"
                    value={newPartyData.title}
                    onChange={handleInputChange}
                    className="w-full p-2 rounded bg-gray-700"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm mb-1">Description</label>
                  <textarea
                    name="description"
                    value={newPartyData.description}
                    onChange={handleInputChange}
                    className="w-full p-2 rounded bg-gray-700"
                    required
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm mb-1">Rank</label>
                    <input
                      type="text"
                      name="rank"
                      value={newPartyData.rank}
                      onChange={handleInputChange}
                      className="w-full p-2 rounded bg-gray-700"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm mb-1">Region</label>
                    <input
                      type="text"
                      name="region"
                      value={newPartyData.region}
                      onChange={handleInputChange}
                      className="w-full p-2 rounded bg-gray-700"
                      required
                    />
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  <div className="flex items-center">
                    <input
                      type="checkbox"
                      name="voiceChat"
                      checked={newPartyData.voiceChat}
                      onChange={handleInputChange}
                      className="mr-2"
                    />
                    <label className="text-sm">Voice Chat</label>
                  </div>
                  <div>
                    <label className="block text-sm mb-1">Expires In (mins)</label>
                    <input
                      type="number"
                      name="expiresIn"
                      value={newPartyData.expiresIn}
                      onChange={handleInputChange}
                      className="w-full p-2 rounded bg-gray-700"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm mb-1">Max Players</label>
                    <input
                      type="number"
                      name="maxPlayers"
                      value={newPartyData.maxPlayers}
                      onChange={handleInputChange}
                      className="w-full p-2 rounded bg-gray-700"
                      required
                    />
                  </div>
                </div>
                <Button type="submit" disabled={loading}>
                  {loading ? "Creating..." : "Create Group"}
                </Button>
              </form>
            )}

            {/* Group Listings */}
            <div className="mt-8">
              {parties.length === 0 ? (
                <div className="text-center text-gray-400">No available groups at the moment.</div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                  {parties.map((party) => (
                    <div key={party.id} className="bg-white/10 backdrop-blur-md rounded-lg p-6">
                      <div className="flex justify-between items-start mb-4">
                        <h2 className="text-xl font-bold text-white">{party.title}</h2>
                        <span className={`px-3 py-1 rounded-full text-sm ${party.status === 'open' ? 'bg-green-500/20 text-green-300' : 'bg-red-500/20 text-red-300'}`}>
                          {party.status}
                        </span>
                      </div>
                      <p className="mb-4">{party.description}</p>
                      {/* Additional party details could be displayed here */}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}

