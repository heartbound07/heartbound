import { Tabs, TabsList, TabsTrigger } from "@/components/ui/valorant/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Button } from "@/components/ui/valorant/button"
import { GamepadIcon, Share2, Trophy, Users, Plus } from "lucide-react"

export default function Home() {
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
            <div className="flex items-center">
              <button className="text-[#8B97A4] hover:text-white hover:bg-[#1F2731] p-2 rounded-full transition-colors">
                <Share2 className="w-5 h-5" />
              </button>
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
                  className="data-[state=active]:bg-[#FF4655] data-[state=active]:text-white rounded-md px-6 py-2 transition-all"
                >
                  <GamepadIcon className="w-4 h-4 mr-2" />
                  Play
                </TabsTrigger>
                <TabsTrigger
                  value="leaderboard"
                  className="data-[state=active]:bg-[#FF4655] data-[state=active]:text-white rounded-md px-6 py-2 transition-all"
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

              <Button
                className="bg-[#FF4655] hover:bg-[#FF4655]/90 text-white rounded-full w-10 h-10 flex items-center justify-center"
                title="Create a group"
              >
                <Plus className="w-6 h-6" />
              </Button>
            </div>

            {/* Group Listings / Empty State */}
            <div className="bg-[#1F2731] rounded-lg border border-[#2C3A47] p-8 text-center">
              <div className="flex flex-col items-center justify-center">
                <div className="w-16 h-16 bg-[#2C3A47] rounded-full flex items-center justify-center mb-4">
                  <Users className="w-8 h-8 text-[#8B97A4]" />
                </div>
                <h3 className="text-xl font-semibold text-white mb-2">No parties found</h3>
                <p className="text-[#8B97A4]">Create a new party or adjust your filters to find more players</p>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}

