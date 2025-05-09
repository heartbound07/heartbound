import { CloudBackground } from '@/components/backgrounds/CloudBackground';
import { DiscordLoginButton } from '@/components/ui/DiscordLoginButton';
import { DiscordIcon } from '@/components/ui/DiscordIcon';
import { motion } from 'framer-motion';
import { useEffect, useState, useRef } from 'react';

// Import icons
import { 
  Trophy, 
  Check, 
  Users, 
  Clock, 
  Target, 
  Headphones, 
  Info, 
  ArrowDown, 
  ArrowRight, 
  UserCircle
} from 'lucide-react';

// Custom Navigation component for landing page with smooth scroll
function LandingNavigation({ className = '', scrollToSection }: { className?: string, scrollToSection: (ref: React.RefObject<HTMLDivElement>) => void }) {
  // Refs are passed from parent component
  return (
    <nav className={`relative z-10 p-4 ${className}`}>
      <ul className="flex justify-center gap-8 text-white font-medium">
        <li>
          <button
            onClick={() => scrollToSection(document.getElementById('competitive-section') as unknown as React.RefObject<HTMLDivElement>)}
            className="group relative inline-block text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-2 py-1 rounded"
          >
            competitive
          </button>
        </li>
        <li>
          <button
            onClick={() => scrollToSection(document.getElementById('duo-section') as unknown as React.RefObject<HTMLDivElement>)}
            className="group relative inline-block text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-2 py-1 rounded"
          >
            find a duo
          </button>
        </li>
        <li>
          <button
            onClick={() => scrollToSection(document.getElementById('discord-section') as unknown as React.RefObject<HTMLDivElement>)}
            className="group relative inline-block text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-2 py-1 rounded"
          >
            discord
          </button>
        </li>
        <li>
          <button
            onClick={() => scrollToSection(document.getElementById('how-it-works-section') as unknown as React.RefObject<HTMLDivElement>)}
            className="group relative inline-block text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-2 py-1 rounded"
          >
            how it works
          </button>
        </li>
      </ul>
    </nav>
  );
}

export function LoginPage() {
  const [titleComplete, setTitleComplete] = useState(false);
  
  // Animation variants for letter-by-letter animation
  const letterVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: (i: number) => ({
      opacity: 1,
      y: 0,
      transition: {
        delay: i * 0.05 + 0.25,
        duration: 0.5,
        ease: [0.22, 1, 0.36, 1]
      }
    })
  };
  
  // Animation variants for the subtitle
  const subtitleVariants = {
    hidden: { opacity: 0, y: 10 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.25,
        ease: "easeOut"
      }
    }
  };
  
  // Scroll handler function for navigation
  const scrollToSection = (ref: React.RefObject<HTMLDivElement>) => {
    ref.current?.scrollIntoView({ behavior: 'smooth' });
  };
  
  // Trigger subtitle animation after title animation completes
  useEffect(() => {
    const timer = setTimeout(() => setTitleComplete(true), 1550);
    return () => clearTimeout(timer);
  }, []);
  
  // References for sections
  const competitiveRef = useRef<HTMLDivElement>(null);
  const duoRef = useRef<HTMLDivElement>(null);
  const discordRef = useRef<HTMLDivElement>(null);
  const howItWorksRef = useRef<HTMLDivElement>(null);
  
  return (
    <div className="min-h-screen bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] relative overflow-hidden">
      {/* Fixed Navigation with custom scroll function */}
      <div className="sticky top-0 z-50 backdrop-blur-md bg-white/5">
        <LandingNavigation className="font-grandstander" scrollToSection={scrollToSection} />
      </div>
      
      <CloudBackground />
      
      {/* Hero Section - Maintained from original LoginPage */}
      <section className="relative z-10 flex flex-col items-center justify-center min-h-[calc(100vh-80px)] px-4 text-center">
        <h1 className="font-grandstander text-4xl sm:text-5xl md:text-6xl lg:text-7xl font-bold text-white mb-4 sm:mb-6 md:mb-8 tracking-wide inline-block">
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
          {/* Hidden but accessible text for screen readers */}
          <span className="sr-only">heartbound</span>
        </h1>
        
        <motion.p 
          className="text-xl sm:text-2xl text-white/90 mb-8 sm:mb-10 md:mb-12"
          variants={subtitleVariants}
          initial="hidden"
          animate={titleComplete ? "visible" : "hidden"}
        >
          find your perfect duo!
        </motion.p>
        
        <motion.div 
          className="bg-white/10 backdrop-blur-md rounded-2xl p-6 sm:p-8 shadow-lg w-full max-w-[340px] mx-auto mb-8"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ 
            opacity: 1, 
            scale: 1,
            transition: { 
              delay: 1.95,
              duration: 0.25,
              ease: [0.22, 1, 0.36, 1]
            }
          }}
        >
          <DiscordLoginButton />
        </motion.div>
        
        <motion.button
          onClick={() => scrollToSection(competitiveRef)}
          className="flex items-center justify-center mt-10 text-white/80 hover:text-white focus:outline-none transition-all"
          initial={{ opacity: 0, y: 20 }}
          animate={{ 
            opacity: 1, 
            y: 0,
            transition: { 
              delay: 2.25,
              duration: 0.5
            }
          }}
          whileHover={{ y: 5 }}
        >
          <span className="mr-2 text-sm">Explore More</span>
          <ArrowDown size={20} />
        </motion.button>
      </section>

      {/* Competitive Section */}
      <section 
        ref={competitiveRef}
        id="competitive-section"
        className="relative z-10 py-20 px-4 min-h-screen flex items-center"
      >
        <motion.div 
          className="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-2 gap-8 items-center"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          viewport={{ once: true, amount: 0.2 }}
        >
          <div>
            <div className="inline-flex items-center bg-white/10 rounded-full px-4 py-1 mb-4">
              <Trophy size={16} className="mr-2 text-yellow-300" />
              <span className="text-sm font-medium text-white/90">Competitive</span>
            </div>
            <h2 className="text-3xl md:text-4xl font-bold text-white mb-4 font-grandstander">Elevate Your Game</h2>
            <p className="text-white/80 text-lg mb-6">
              Track your competitive performance, analyze match history, and get insights to improve your gameplay.
            </p>
            
            <ul className="space-y-4 mb-8">
              {[
                "Real-time match tracking",
                "Performance analytics",
                "Skill progression metrics",
                "Personalized improvement tips"
              ].map((feature, index) => (
                <li key={index} className="flex items-start">
                  <div className="bg-green-500/20 p-1 rounded-full mr-3 mt-1">
                    <Check size={14} className="text-green-400" />
                  </div>
                  <span className="text-white/80">{feature}</span>
                </li>
              ))}
            </ul>
          </div>
          
          <div className="order-1 md:order-2 bg-white/5 backdrop-blur-sm rounded-2xl p-4 shadow-lg">
            <div className="aspect-video bg-gradient-to-br from-[#FF4655]/20 to-[#0F1923]/50 rounded-xl flex items-center justify-center">
              {/* Placeholder for competitive gameplay image */}
              <div className="text-center p-8">
                <Trophy size={64} className="mx-auto mb-4 text-yellow-300 opacity-75" />
                <p className="text-white/60 text-sm">Competitive gameplay visualization</p>
              </div>
            </div>
          </div>
        </motion.div>
      </section>

      {/* Find a Duo Section */}
      <section 
        ref={duoRef}
        id="duo-section"
        className="relative z-10 py-20 px-4 min-h-screen flex items-center bg-gradient-to-br from-[#7E6AE6] to-[#9889f5]"
      >
        <motion.div 
          className="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-2 gap-8 items-center"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          viewport={{ once: true, amount: 0.2 }}
        >
          <div className="bg-white/5 backdrop-blur-sm rounded-2xl p-4 shadow-lg">
            <div className="aspect-video bg-gradient-to-br from-[#4752C4]/30 to-[#6B5BE6]/30 rounded-xl flex items-center justify-center">
              {/* Placeholder for duo finder image */}
              <div className="text-center p-8">
                <Users size={64} className="mx-auto mb-4 text-white opacity-75" />
                <p className="text-white/60 text-sm">Duo matching visualization</p>
              </div>
            </div>
          </div>
          
          <div>
            <div className="inline-flex items-center bg-white/10 rounded-full px-4 py-1 mb-4">
              <Users size={16} className="mr-2 text-white" />
              <span className="text-sm font-medium text-white/90">Find a Duo</span>
            </div>
            <h2 className="text-3xl md:text-4xl font-bold text-white mb-4 font-grandstander">Find Your Perfect Match</h2>
            <p className="text-white/80 text-lg mb-6">
              Our advanced matching system pairs you with players who complement your playstyle, availability, and communication preferences.
            </p>
            
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
              {[
                { title: "Skill-Based", icon: Target, description: "Find players at your level" },
                { title: "Schedule Matching", icon: Clock, description: "Play when it works for you" },
                { title: "Communication", icon: Headphones, description: "Voice or text, your choice" },
                { title: "Similar Goals", icon: Trophy, description: "Casual or competitive" }
              ].map((feature, index) => (
                <div key={index} className="bg-white/10 backdrop-blur-md rounded-lg p-4">
                  <div className="flex items-center mb-2">
                    <feature.icon size={18} className="text-white mr-2" />
                    <h3 className="text-white font-medium">{feature.title}</h3>
                  </div>
                  <p className="text-white/70 text-sm">{feature.description}</p>
                </div>
              ))}
            </div>
          </div>
        </motion.div>
      </section>

      {/* Discord Section */}
      <section 
        ref={discordRef}
        id="discord-section"
        className="relative z-10 py-20 px-4 min-h-screen flex items-center"
      >
        <motion.div 
          className="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-2 gap-8 items-center"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          viewport={{ once: true, amount: 0.2 }}
        >
          <div>
            <div className="inline-flex items-center bg-white/10 rounded-full px-4 py-1 mb-4">
              <DiscordIcon className="h-4 w-4 mr-2" />
              <span className="text-sm font-medium text-white/90">Discord Community</span>
            </div>
            <h2 className="text-3xl md:text-4xl font-bold text-white mb-4 font-grandstander">Join Our Community</h2>
            <p className="text-white/80 text-lg mb-6">
              Connect with thousands of players in our Discord server. Chat, coordinate games, participate in events, and make new friends.
            </p>
            
            <div className="bg-[#2C2F33]/60 backdrop-blur-sm rounded-xl p-5 mb-8 border border-white/10">
              <div className="flex flex-col space-y-4">
                <div className="flex items-center p-3 bg-[#36393F]/60 rounded-lg">
                  <div className="w-10 h-10 bg-[#5865F2] rounded-full flex items-center justify-center text-white font-bold mr-3">#</div>
                  <div>
                    <p className="text-white font-medium">general-chat</p>
                    <p className="text-white/60 text-xs">Connect with the community</p>
                  </div>
                </div>
                
                <div className="flex items-center p-3 bg-[#36393F]/60 rounded-lg">
                  <div className="w-10 h-10 bg-[#5865F2] rounded-full flex items-center justify-center text-white font-bold mr-3">🎮</div>
                  <div>
                    <p className="text-white font-medium">lfg-valorant</p>
                    <p className="text-white/60 text-xs">Find players for your next match</p>
                  </div>
                </div>
                
                <div className="flex items-center p-3 bg-[#36393F]/60 rounded-lg">
                  <div className="w-10 h-10 bg-[#5865F2] rounded-full flex items-center justify-center text-white font-bold mr-3">📢</div>
                  <div>
                    <p className="text-white font-medium">announcements</p>
                    <p className="text-white/60 text-xs">Stay updated with the latest news</p>
                  </div>
                </div>
              </div>
            </div>
            
            <div className="flex justify-center md:justify-start">
              <DiscordLoginButton />
            </div>
          </div>
          
          <div className="bg-white/5 backdrop-blur-sm rounded-2xl p-4 shadow-lg">
            <div className="aspect-video bg-[#36393F]/70 rounded-xl flex items-center justify-center">
              {/* Placeholder for Discord community image */}
              <div className="text-center p-8">
                <DiscordIcon className="h-16 w-16 mx-auto mb-4 text-[#5865F2]" />
                <p className="text-white/60 text-sm">Active Discord community</p>
              </div>
            </div>
          </div>
        </motion.div>
      </section>

      {/* How It Works Section */}
      <section 
        ref={howItWorksRef}
        id="how-it-works-section"
        className="relative z-10 py-20 px-4 min-h-screen flex items-center"
      >
        <motion.div 
          className="max-w-6xl mx-auto"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          viewport={{ once: true, amount: 0.2 }}
        >
          <div className="text-center mb-12">
            <div className="inline-flex items-center bg-white/10 rounded-full px-4 py-1 mb-4 mx-auto">
              <Info size={16} className="mr-2 text-white" />
              <span className="text-sm font-medium text-white/90">How It Works</span>
            </div>
            <h2 className="text-3xl md:text-4xl font-bold text-white mb-4 font-grandstander">Simple Steps to Get Started</h2>
            <p className="text-white/80 text-lg max-w-2xl mx-auto">
              Heartbound makes it easy to find your perfect gaming partner in just a few simple steps.
            </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-16 relative">
            {[
              {
                title: "Sign In With Discord",
                description: "Connect your Discord account in one click to get started",
                icon: DiscordIcon,
                delay: 0
              },
              {
                title: "Create Your Profile",
                description: "Set up your gaming preferences, schedule, and communication style",
                icon: UserCircle,
                delay: 0.2
              },
              {
                title: "Find Your Duo",
                description: "Browse matches or let our system find the perfect gaming partner for you",
                icon: Users,
                delay: 0.4
              }
            ].map((step, index) => (
              <motion.div
                key={index}
                className="bg-white/10 backdrop-blur-sm rounded-xl p-6 shadow-lg flex flex-col items-center text-center relative"
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: step.delay }}
                viewport={{ once: true, amount: 0.2 }}
              >
                <div className="w-16 h-16 bg-white/20 rounded-full flex items-center justify-center mb-4">
                  {step.icon === DiscordIcon ? (
                    <DiscordIcon className="h-8 w-8 text-white" />
                  ) : (
                    <step.icon size={32} className="text-white" />
                  )}
                </div>
                <h3 className="text-xl font-bold text-white mb-2">{step.title}</h3>
                <p className="text-white/70">{step.description}</p>
                
                {index < 2 && (
                  <div className="hidden md:block absolute right-[-30px] top-1/2 transform -translate-y-1/2 text-white/30 z-10">
                    <ArrowRight size={24} />
                  </div>
                )}
              </motion.div>
            ))}
          </div>
          
          <motion.div 
            className="bg-white/10 backdrop-blur-md rounded-2xl p-8 text-center max-w-2xl mx-auto"
            initial={{ opacity: 0, scale: 0.95 }}
            whileInView={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.5 }}
            viewport={{ once: true, amount: 0.2 }}
          >
            <h3 className="text-2xl font-bold text-white mb-4 font-grandstander">Ready to Find Your Duo?</h3>
            <p className="text-white/80 mb-6">
              Join thousands of gamers already finding their perfect teammates on Heartbound
            </p>
            <DiscordLoginButton />
          </motion.div>
        </motion.div>
      </section>

      {/* Footer Section */}
      <footer className="relative z-10 bg-white/5 backdrop-blur-md border-t border-white/10">
        <div className="max-w-6xl mx-auto px-4 py-8">
          <div className="flex flex-col md:flex-row justify-between items-center">
            <div className="mb-4 md:mb-0">
              <h2 className="text-2xl font-bold text-white font-grandstander">heartbound</h2>
              <p className="text-white/60 text-sm">© {new Date().getFullYear()} Heartbound. All rights reserved.</p>
            </div>
            
            <div className="flex space-x-6">
              {[
                { label: "Terms", href: "#" },
                { label: "Privacy", href: "#" },
                { label: "Contact", href: "#" },
                { label: "Support", href: "#" }
              ].map((link, index) => (
                <a 
                  key={index}
                  href={link.href}
                  className="text-white/70 hover:text-white transition-colors"
                >
                  {link.label}
                </a>
              ))}
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
} 