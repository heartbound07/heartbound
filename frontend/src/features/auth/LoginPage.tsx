"use client"

import { CloudBackground } from "@/components/backgrounds/CloudBackground"
import { Navigation } from "@/components/ui/Navigation"
import { DiscordLoginButton } from "@/components/ui/DiscordLoginButton"
import { DiscordIcon } from "@/components/ui/DiscordIcon"
import { PairingCard } from "@/features/pages/PairingCard"
import type { PairingDTO } from "@/config/pairingService"
import type { UserProfileDTO } from "@/config/userService"
import { motion } from "framer-motion"
import { useEffect, useState, useRef } from "react"

// Import icons
import {
  Trophy,
  Users,
  Clock,
  Target,
  Headphones,
  Info,
  ArrowDown,
  ArrowRight,
  UserCircle,
  Shield,
  Zap,
  GamepadIcon,
} from "lucide-react"

export function LoginPage() {
  const [titleComplete, setTitleComplete] = useState(false)

  // References for sections
  const competitiveRef = useRef<HTMLElement>(null)
  const duoRef = useRef<HTMLElement>(null)
  const discordRef = useRef<HTMLElement>(null)
  const howItWorksRef = useRef<HTMLElement>(null)

  // Animation variants for letter-by-letter animation
  const letterVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: (i: number) => ({
      opacity: 1,
      y: 0,
      transition: {
        delay: i * 0.05 + 0.25,
        duration: 0.5,
        ease: [0.22, 1, 0.36, 1],
      },
    }),
  }

  // Animation variants for the subtitle
  const subtitleVariants = {
    hidden: { opacity: 0, y: 10 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.25,
        ease: "easeOut",
      },
    },
  }

  // Handle scroll to section from Navigation
  const handleSectionClick = (section: string) => {
    let targetRef

    switch (section) {
      case "competitive":
        targetRef = competitiveRef
        break
      case "find-a-duo":
        targetRef = duoRef
        break
      case "discord":
        targetRef = discordRef
        break
      case "how-it-works":
        targetRef = howItWorksRef
        break
      default:
        return
    }

    if (targetRef?.current) {
      window.scrollTo({
        top: targetRef.current.offsetTop - 80,
        behavior: "smooth",
      })
    }
  }

  // Trigger subtitle animation after title animation completes
  useEffect(() => {
    const timer = setTimeout(() => setTitleComplete(true), 1550)
    return () => clearTimeout(timer)
  }, [])

  // Mock data for PairingCard preview
  const mockUser1Profile: UserProfileDTO = {
    id: "demo-user-1",
    username: "ValorantPro",
    displayName: "Alex Chen",
    avatar: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop&crop=face",
    banned: false
  }

  const mockUser2Profile: UserProfileDTO = {
    id: "demo-user-2", 
    username: "RankClimber",
    displayName: "Sophie Martinez",
    avatar: "https://images.unsplash.com/photo-1494790108755-2616b332c24b?w=150&h=150&fit=crop&crop=face",
    banned: false
  }

  const mockPairing: PairingDTO = {
    id: 1,
    user1Id: "demo-user-1",
    user2Id: "demo-user-2",
    discordChannelId: 1234567890,
    discordChannelName: "alex-sophie-duo",
    active: true,
    blacklisted: false,
    matchedAt: "2024-01-15T10:30:00Z",
    messageCount: 847,
    user1MessageCount: 423,
    user2MessageCount: 424,
    voiceTimeMinutes: 2340, // 39 hours
    wordCount: 15623,
    emojiCount: 234,
    activeDays: 28,
    compatibilityScore: 95.5,
    mutualBreakup: false,
    breakupReason: undefined,
    breakupInitiatorId: undefined,
    breakupTimestamp: undefined
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] relative overflow-hidden">
      {/* Fixed Navigation */}
      <div className="sticky top-0 z-50 backdrop-blur-xl bg-black/20 border-b border-white/10">
        <Navigation className="font-grandstander" onSectionClick={handleSectionClick} isLandingPage={true} />
      </div>

      <CloudBackground />

      {/* Hero Section */}
      <section className="relative z-10 flex flex-col items-center justify-center min-h-[calc(100vh-80px)] px-4 text-center">
        {/* Cosmic background orb */}
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="w-[1000px] h-[1000px] bg-gradient-to-r from-[#6B5BE6]/30 via-[#8878f0]/25 to-[#B794F6]/20 rounded-full blur-3xl opacity-70"></div>
          <div className="absolute w-[600px] h-[600px] bg-gradient-to-br from-[#A78BFA]/20 via-[#DDD6FE]/15 to-[#6B5BE6]/25 rounded-full blur-2xl opacity-50"></div>
        </div>

        <h1 className="font-grandstander text-5xl sm:text-6xl md:text-7xl lg:text-8xl font-bold text-white mb-6 tracking-tight">
          {Array.from("heartbound").map((letter, i) => (
            <motion.span
              key={i}
              custom={i}
              variants={letterVariants}
              initial="hidden"
              animate="visible"
              aria-hidden="true"
              className="inline-block"
            >
              {letter}
            </motion.span>
          ))}
          <span className="sr-only">heartbound</span>
        </h1>

        <motion.p
          className="text-2xl sm:text-3xl md:text-4xl text-white/90 mb-12 font-medium"
          variants={subtitleVariants}
          initial="hidden"
          animate={titleComplete ? "visible" : "hidden"}
        >
          find your perfect duo!
        </motion.p>

        {/* CTA Buttons */}
        <motion.div
          className="bg-white/10 backdrop-blur-md rounded-2xl p-8 shadow-2xl w-full max-w-sm mx-auto mb-16"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{
            opacity: 1,
            scale: 1,
            transition: {
              delay: 1.95,
              duration: 0.25,
              ease: [0.22, 1, 0.36, 1],
            },
          }}
        >
          <div className="flex justify-center">
            <DiscordLoginButton />
          </div>
        </motion.div>

        {/* Game logos */}
        <motion.div
          className="flex items-center gap-6 mb-16"
          initial={{ opacity: 0, y: 20 }}
          animate={{
            opacity: 1,
            y: 0,
            transition: {
              delay: 2.2,
              duration: 0.5,
            },
          }}
        >
          {[
            { name: "VALORANT", logo: "https://gankster.gg/wp-content/uploads/2024/05/valorant.svg" },
            { name: "LEAGUE", logo: "https://gankster.gg/wp-content/uploads/2024/05/leagueoflegends.svg" },
            { name: "DOTA 2", logo: "https://gankster.gg/wp-content/uploads/2024/05/dota.svg" },
          ].map((game, index) => (
            <div key={index} className="flex items-center justify-center min-w-[80px] h-16">
              <img
                src={game.logo || "/placeholder.svg"}
                alt={game.name}
                className="h-10 w-auto filter brightness-0 invert opacity-70 hover:opacity-100 transition-opacity"
              />
            </div>
          ))}
        </motion.div>

        <motion.button
          onClick={() => handleSectionClick("competitive")}
          className="flex items-center justify-center text-white/60 hover:text-white focus:outline-none transition-all group"
          initial={{ opacity: 0, y: 20 }}
          animate={{
            opacity: 1,
            y: 0,
            transition: {
              delay: 2.5,
              duration: 0.5,
            },
          }}
          whileHover={{ y: 5 }}
        >
          <span className="mr-2 text-sm">Explore Features</span>
          <ArrowDown size={20} className="group-hover:translate-y-1 transition-transform" />
        </motion.button>
      </section>

      {/* Features Grid Section */}
      <section ref={competitiveRef} className="relative z-10 py-20 px-4">
        <motion.div
          className="max-w-7xl mx-auto"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          viewport={{ once: true, amount: 0.2 }}
        >
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              {
                icon: Shield,
                title: "SFW 15+ Server",
                description: "Our server is safe for everyone! Always moderating chat to prevent trolling or inappropriate people.",
                gradient: "from-[#6B5BE6]/20 to-[#8878f0]/20",
              },
              {
                icon: Users,
                title: "Made for everyone",
                description: "No matter who you are, everyone is welcome here!",
                gradient: "from-[#8878f0]/20 to-[#B794F6]/20",
              },
              {
                icon: Zap,
                title: "Flawless Discord integration",
                description: "Our website is integrated with Discord. You can buy roles, colors, and more with our credit system!",
                gradient: "from-[#6B5BE6]/20 to-[#A78BFA]/20",
              },
            ].map((feature, index) => (
              <motion.div
                key={index}
                className={`bg-gradient-to-br ${feature.gradient} backdrop-blur-xl border border-white/10 rounded-2xl p-6 hover:border-white/20 transition-all duration-300 group`}
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
                viewport={{ once: true, amount: 0.2 }}
                whileHover={{ y: -5 }}
              >
                <div className="w-12 h-12 bg-white/10 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                  <feature.icon size={24} className="text-white" />
                </div>
                <h3 className="text-xl font-bold text-white mb-3">{feature.title}</h3>
                <p className="text-white/70 text-sm leading-relaxed">{feature.description}</p>
              </motion.div>
            ))}
          </div>
        </motion.div>
      </section>

      {/* Find a Duo Section */}
      <section ref={duoRef} className="relative z-10 py-20 px-4">
        <motion.div
          className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-2 gap-12 items-center"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          viewport={{ once: true, amount: 0.2 }}
        >
          <div className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-3xl p-6 shadow-2xl">
            <div className="bg-gradient-to-br from-[#6B5BE6]/20 via-[#8878f0]/10 to-[#B794F6]/20 rounded-2xl p-4 relative overflow-hidden">
              <div className="absolute inset-0 bg-gradient-to-br from-purple-500/5 to-transparent"></div>
              <div className="relative z-10">
                <p className="text-white/70 text-sm mb-4 text-center font-medium">Live Preview</p>
                <div className="login-page-pairing-theme">
                  <PairingCard
                    pairing={mockPairing}
                    index={0}
                    user1Profile={mockUser1Profile}
                    user2Profile={mockUser2Profile}
                    isActive={true}
                    onUserClick={() => {}}
                    formatDate={() => "28 days ago"}
                    hasAdminActions={false}
                    currentStreak={7}
                    currentLevel={12}
                  />
                </div>
              </div>
            </div>
          </div>

          <style
            dangerouslySetInnerHTML={{
              __html: `
                .login-page-pairing-theme .pairing-card {
                  background: linear-gradient(135deg, rgba(107, 91, 230, 0.15) 0%, rgba(136, 120, 240, 0.1) 50%, rgba(183, 148, 246, 0.15) 100%) !important;
                  border: 1px solid rgba(255, 255, 255, 0.15) !important;
                  backdrop-filter: blur(16px) !important;
                  color: white !important;
                }
                
                .login-page-pairing-theme .pairing-card__header {
                  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
                }
                
                .login-page-pairing-theme .pairing-card__status-text {
                  color: rgba(255, 255, 255, 0.8) !important;
                }
                
                .login-page-pairing-theme .pairing-card__status-dot--active {
                  background: linear-gradient(45deg, #10B981, #34D399) !important;
                  box-shadow: 0 0 8px rgba(16, 185, 129, 0.4) !important;
                }
                
                .login-page-pairing-theme .pairing-card__username {
                  color: rgba(255, 255, 255, 0.9) !important;
                }
                
                .login-page-pairing-theme .pairing-card__heart {
                  color: #F472B6 !important;
                  filter: drop-shadow(0 0 6px rgba(244, 114, 182, 0.3)) !important;
                }
                
                .login-page-pairing-theme .pairing-card__metric-value {
                  color: rgba(255, 255, 255, 0.9) !important;
                }
                
                .login-page-pairing-theme .pairing-card__metric-icon--level {
                  color: #FBBF24 !important;
                }
                
                .login-page-pairing-theme .pairing-card__metric-icon--messages {
                  color: #8B5CF6 !important;
                }
                
                .login-page-pairing-theme .pairing-card__metric-icon--voice {
                  color: #06B6D4 !important;
                }
                
                .login-page-pairing-theme .pairing-card__metric-icon--streak {
                  color: #F97316 !important;
                }
                
                .login-page-pairing-theme .pairing-card__duration {
                  color: rgba(255, 255, 255, 0.7) !important;
                }
                
                .login-page-pairing-theme .pairing-card__chevron {
                  color: rgba(255, 255, 255, 0.4) !important;
                }
                
                .login-page-pairing-theme .pairing-card:hover {
                  background: linear-gradient(135deg, rgba(107, 91, 230, 0.2) 0%, rgba(136, 120, 240, 0.15) 50%, rgba(183, 148, 246, 0.2) 100%) !important;
                  border-color: rgba(255, 255, 255, 0.2) !important;
                  transform: translateY(-2px) !important;
                  box-shadow: 0 8px 32px rgba(107, 91, 230, 0.2) !important;
                }
                
                .login-page-pairing-theme .pairing-card__avatar-fallback {
                  background: linear-gradient(135deg, #6B5BE6, #8878f0) !important;
                  color: white !important;
                }
                
                .login-page-pairing-theme .pairing-card__user {
                  background: rgba(255, 255, 255, 0.05) !important;
                  border: 1px solid rgba(255, 255, 255, 0.1) !important;
                  border-radius: 12px !important;
                  padding: 8px 12px !important;
                  transition: all 0.2s ease !important;
                }
                
                .login-page-pairing-theme .pairing-card__user:hover {
                  background: rgba(255, 255, 255, 0.1) !important;
                  border-color: rgba(255, 255, 255, 0.2) !important;
                  transform: translateY(-1px) !important;
                  box-shadow: 0 4px 16px rgba(107, 91, 230, 0.2) !important;
                }
                
                .login-page-pairing-theme .pairing-card__metric {
                  background: rgba(255, 255, 255, 0.05) !important;
                  border: 1px solid rgba(255, 255, 255, 0.08) !important;
                  border-radius: 8px !important;
                  padding: 6px 10px !important;
                  transition: all 0.2s ease !important;
                }
                
                .login-page-pairing-theme .pairing-card__metric:hover {
                  background: rgba(255, 255, 255, 0.08) !important;
                  border-color: rgba(255, 255, 255, 0.15) !important;
                  transform: translateY(-1px) !important;
                }
                
                .login-page-pairing-theme .pairing-card__footer {
                  border-top: 1px solid rgba(255, 255, 255, 0.1) !important;
                  background: rgba(255, 255, 255, 0.02) !important;
                }
              `,
            }}
          />

          <div>
            <div className="inline-flex items-center bg-white/10 backdrop-blur-md border border-white/20 rounded-full px-4 py-2 mb-6">
              <Users size={16} className="mr-2 text-white" />
              <span className="text-sm font-medium text-white/90">Smart Matching</span>
            </div>
            <h2 className="text-4xl md:text-5xl font-bold text-white mb-6 font-grandstander leading-tight">
              Find Your Perfect Gaming Partner
            </h2>
            <p className="text-white/70 text-lg mb-8 leading-relaxed">
              Our intelligent matching system connects you with players who share your playstyle, schedule, and
              competitive goals.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {[
                { title: "Skill-Based Matching", icon: Target, description: "Find players at your skill level" },
                { title: "Schedule Sync", icon: Clock, description: "Match your gaming hours" },
                { title: "Communication Style", icon: Headphones, description: "Voice, text, or both" },
                { title: "Goal Alignment", icon: Trophy, description: "Casual fun or ranked grind" },
              ].map((feature, index) => (
                <motion.div
                  key={index}
                  className="bg-white/5 backdrop-blur-md border border-white/10 rounded-xl p-4 hover:bg-white/10 transition-all duration-200"
                  whileHover={{ scale: 1.02 }}
                >
                  <div className="flex items-center mb-2">
                    <div className="w-8 h-8 bg-white/10 rounded-lg flex items-center justify-center mr-3">
                      <feature.icon size={16} className="text-white" />
                    </div>
                    <h3 className="text-white font-semibold text-sm">{feature.title}</h3>
                  </div>
                  <p className="text-white/60 text-xs leading-relaxed">{feature.description}</p>
                </motion.div>
              ))}
            </div>
          </div>
        </motion.div>
      </section>

      {/* Discord Section */}
      <section ref={discordRef} className="relative z-10 py-20 px-4">
        <motion.div
          className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-2 gap-12 items-center"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          viewport={{ once: true, amount: 0.2 }}
        >
          <div>
            <div className="inline-flex items-center bg-[#5865F2]/20 backdrop-blur-md border border-[#5865F2]/30 rounded-full px-4 py-2 mb-6">
              <DiscordIcon className="h-4 w-4 mr-2" />
              <span className="text-sm font-medium text-white/90">Discord Integration</span>
            </div>
            <h2 className="text-4xl md:text-5xl font-bold text-white mb-6 font-grandstander leading-tight">
              Seamless Discord Experience
            </h2>
            <p className="text-white/70 text-lg mb-8 leading-relaxed">
              Built-in Discord integration means no extra apps or complicated setups. Just pure gaming connection.
            </p>

            <div className="bg-[#2C2F33]/40 backdrop-blur-xl border border-white/10 rounded-2xl p-6 mb-8">
              <div className="space-y-3">
                {[
                  { name: "general-chat", icon: "#", desc: "Connect with the community", online: "1,247" },
                  { name: "lfg-valorant", icon: "🎯", desc: "Find Valorant teammates", online: "892" },
                  { name: "lfg-league", icon: "⚔️", desc: "League of Legends LFG", online: "634" },
                ].map((channel, index) => (
                  <div
                    key={index}
                    className="flex items-center justify-between p-3 bg-[#36393F]/60 backdrop-blur-sm rounded-xl border border-white/5"
                  >
                    <div className="flex items-center">
                      <div className="w-10 h-10 bg-[#5865F2]/20 rounded-xl flex items-center justify-center text-white font-bold mr-3 text-sm">
                        {channel.icon}
                      </div>
                      <div>
                        <p className="text-white font-medium text-sm">{channel.name}</p>
                        <p className="text-white/50 text-xs">{channel.desc}</p>
                      </div>
                    </div>
                    <div className="text-green-400 text-xs font-medium">{channel.online} online</div>
                  </div>
                ))}
              </div>
            </div>

            <DiscordLoginButton />
          </div>

          <div className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-3xl p-8 shadow-2xl">
            <div className="aspect-video bg-[#36393F]/60 backdrop-blur-sm rounded-2xl flex items-center justify-center relative overflow-hidden">
              <div className="absolute inset-0 bg-gradient-to-br from-[#5865F2]/10 to-transparent"></div>
              <div className="text-center p-8 relative z-10">
                <DiscordIcon className="h-16 w-16 mx-auto mb-4 text-[#5865F2]" />
                <p className="text-white/60 text-sm">Active community of gamers</p>
              </div>
            </div>
          </div>
        </motion.div>
      </section>

      {/* How It Works Section */}
      <section ref={howItWorksRef} className="relative z-10 py-20 px-4">
        <motion.div
          className="max-w-6xl mx-auto"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          viewport={{ once: true, amount: 0.2 }}
        >
          <div className="text-center mb-16">
            <div className="inline-flex items-center bg-white/10 backdrop-blur-md border border-white/20 rounded-full px-4 py-2 mb-6">
              <Info size={16} className="mr-2 text-white" />
              <span className="text-sm font-medium text-white/90">Getting Started</span>
            </div>
            <h2 className="text-4xl md:text-5xl font-bold text-white mb-6 font-grandstander">
              Simple Steps
            </h2>
            <p className="text-white/70 text-lg max-w-2xl mx-auto leading-relaxed">
              Get matched with your perfect gaming partner in three easy steps
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-16 relative">
            {[
              {
                title: "Connect Discord",
                description: "Link your Discord account with one click to get started instantly",
                icon: DiscordIcon,
                delay: 0,
                step: "01",
              },
              {
                title: "Set Preferences",
                description: "Tell us your Gender, Age, Rank, Region, and what kind of teammate you're looking for",
                icon: UserCircle,
                delay: 0.2,
                step: "02",
              },
              {
                title: "Start Playing",
                description: "Get matched with compatible players and start your gaming journey together",
                icon: GamepadIcon,
                delay: 0.4,
                step: "03",
              },
            ].map((step, index) => (
              <motion.div
                key={index}
                className="relative"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: step.delay }}
                viewport={{ once: true, amount: 0.2 }}
              >
                <div className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-8 shadow-2xl hover:bg-white/10 transition-all duration-300 group relative overflow-hidden">
                  {/* Step number */}
                  <div className="absolute top-4 right-4 text-6xl font-bold text-white/5 group-hover:text-white/10 transition-colors">
                    {step.step}
                  </div>

                  <div className="w-16 h-16 bg-gradient-to-br from-[#6B5BE6]/20 to-[#8878f0]/20 backdrop-blur-md border border-white/20 rounded-2xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                    {step.icon === DiscordIcon ? (
                      <DiscordIcon className="h-8 w-8 text-white" />
                    ) : (
                      <step.icon size={32} className="text-white" />
                    )}
                  </div>
                  <h3 className="text-xl font-bold text-white mb-4">{step.title}</h3>
                  <p className="text-white/70 leading-relaxed">{step.description}</p>
                </div>

                {index < 2 && (
                  <div className="hidden md:block absolute right-[-20px] top-1/2 transform -translate-y-1/2 text-white/20 z-10">
                    <ArrowRight size={24} />
                  </div>
                )}
              </motion.div>
            ))}
          </div>

          <motion.div
            className="bg-gradient-to-br from-[#6B5BE6]/20 via-[#8878f0]/10 to-[#B794F6]/20 backdrop-blur-xl border border-white/20 rounded-3xl p-12 text-center max-w-3xl mx-auto shadow-2xl"
            initial={{ opacity: 0, scale: 0.95 }}
            whileInView={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.6 }}
            viewport={{ once: true, amount: 0.2 }}
          >
            <h3 className="text-3xl md:text-4xl font-bold text-white mb-4 font-grandstander">Ready to Level Up?</h3>
            <p className="text-white/70 text-lg mb-8 leading-relaxed">
              Join thousands of gamers who've already found their perfect teammates
            </p>
            <div className="bg-gradient-to-r from-[#6B5BE6] to-[#8878f0] rounded-xl p-[1px] inline-block">
              <div className="bg-gradient-to-r from-[#6B5BE6] to-[#8878f0] rounded-xl">
                <DiscordLoginButton />
              </div>
            </div>
          </motion.div>
        </motion.div>
      </section>

      {/* Footer Section */}
      <footer className="relative z-10 bg-black/20 backdrop-blur-xl border-t border-white/10 mt-20">
        <div className="max-w-6xl mx-auto px-4 py-12">
          <div className="flex flex-col md:flex-row justify-between items-center">
            <div className="mb-8 md:mb-0 text-center md:text-left">
              <h2 className="text-3xl font-bold text-white font-grandstander mb-2">heartbound</h2>
              <p className="text-white/50 text-sm">Team up with players you vibe with</p>
              <p className="text-white/40 text-xs mt-2">
                © {new Date().getFullYear()} Heartbound. All rights reserved.
              </p>
            </div>

            <div className="flex flex-wrap justify-center gap-8">
              {[
                { label: "Terms of Service", href: "#" },
                { label: "Privacy Policy", href: "#" },
                { label: "Contact Us", href: "#" },
                { label: "Support", href: "#" },
              ].map((link, index) => (
                <a key={index} href={link.href} className="text-white/60 hover:text-white transition-colors text-sm">
                  {link.label}
                </a>
              ))}
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
