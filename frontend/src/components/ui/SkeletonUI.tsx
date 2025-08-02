import React from "react";
import { cn } from "@/utils/cn";

// Add shimmer animation styles to match PairingsPage aesthetic
const shimmerStyles = `
  @keyframes shimmer {
    0% {
      background-position: -200% 0;
    }
    100% {
      background-position: 200% 0;
    }
  }
  
  .animate-shimmer {
    animation: shimmer 1.5s infinite;
  }
`;

// Add styles only once
let stylesInjected = false;

const injectStyles = () => {
  if (!stylesInjected && typeof document !== 'undefined') {
    const style = document.createElement('style');
    style.textContent = shimmerStyles;
    document.head.appendChild(style);
    stylesInjected = true;
  }
};

interface SkeletonProps {
  className?: string;
  /**
   * Visual variant of the skeleton
   * @default "default"
   */
  variant?: "default" | "circular" | "rounded" | "rectangular";
  /**
   * Whether to animate the skeleton
   * @default true
   */
  animate?: boolean;
  /**
   * Width of the skeleton (CSS value)
   */
  width?: string;
  /**
   * Height of the skeleton (CSS value)
   */
  height?: string;
  /**
   * Borderradius of the skeleton (CSS value)
   * @default "0.25rem" for "rounded" variant
   */
  borderRadius?: string;
  /**
   * Theme of the skeleton
   * @default "neutral"
   */
  theme?: "neutral" | "valorant" | "dashboard";
}

/**
 * Base Skeleton component for loading states
 */
export function Skeleton({
  className,
  variant = "default",
  animate = true,
  width,
  height,
  borderRadius,
  theme = "neutral",
}: SkeletonProps) {
  // Updated theme-specific colors to match PairingsPage styling
  const themeClasses = {
    neutral: "from-gray-200 via-gray-300 to-gray-200 dark:from-gray-700 dark:via-gray-600 dark:to-gray-700",
    valorant: "from-[rgba(31,39,49,0.4)] via-[rgba(31,39,49,0.6)] to-[rgba(31,39,49,0.4)]",
    dashboard: "from-[rgba(31,39,49,0.4)] via-[rgba(31,39,49,0.6)] to-[rgba(31,39,49,0.4)]",
  };

  // Variant-specific styling
  const variantClasses = {
    default: "",
    circular: "rounded-full",
    rounded: borderRadius ? "" : "rounded",
    rectangular: "rounded-none",
  };

  const animationClass = animate
    ? "bg-gradient-to-r animate-shimmer bg-[length:200%_100%]"
    : "bg-[rgba(31,39,49,0.5)]";

  const style: React.CSSProperties = {
    width,
    height,
    ...(borderRadius && { borderRadius }),
  };

  // Inject styles once
  React.useEffect(() => {
    injectStyles();
  }, []);

  return (
    <div
      className={cn(
        animationClass,
        themeClasses[theme],
        variantClasses[variant],
        className
      )}
      style={style}
      aria-hidden="true"
    />
  );
}

/**
 * Game Card Skeleton for loading states in game grid displays
 */
export function SkeletonGameCard({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-xl backdrop-blur-sm",
        "border border-[rgba(255,255,255,0.05)] shadow-lg",
        "bg-[rgba(31,39,49,0.3)]", // Updated to match PairingsPage
        "w-64",
        "transition-all duration-300 ease-in-out",
        className
      )}
    >
      <div className="aspect-[3/4] relative overflow-hidden rounded-lg">
        <Skeleton 
          className="w-full h-full"
          theme={theme}
          animate={true}
        />
      </div>
      
      {/* Static logo placeholder */}
      <div className="absolute bottom-3 left-3">
        <Skeleton
          variant="circular"
          width="32px"
          height="32px"
          theme={theme}
        />
      </div>
    </div>
  );
}

/**
 * Party Listing Skeleton for loading states in valorant party listings
 */
export function SkeletonPartyListing({ 
  className,
  theme = "valorant"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div
      className={cn(
        "rounded-xl border p-4 shadow-md backdrop-blur-sm",
        "border-[rgba(255,255,255,0.05)] bg-[rgba(31,39,49,0.3)]", // Updated to match PairingsPage
        "hover:bg-[rgba(31,39,49,0.4)] transition-all duration-300",
        className
      )}
    >
      {/* Header with title and avatar */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex-1">
          <Skeleton 
            width="70%" 
            height="24px" 
            className="mb-2"
            theme={theme}
          />
          <Skeleton 
            width="40%" 
            height="16px"
            theme={theme}
          />
        </div>
        <Skeleton 
          variant="circular" 
          width="40px" 
          height="40px"
          theme={theme}
        />
      </div>
      
      {/* Tags row */}
      <div className="flex flex-wrap gap-2 mb-3">
        {[1, 2, 3].map((i) => (
          <Skeleton 
            key={i} 
            width="60px" 
            height="20px" 
            borderRadius="9999px"
            theme={theme}
          />
        ))}
      </div>
      
      {/* Description */}
      <Skeleton 
        width="100%" 
        height="48px" 
        className="mb-3"
        theme={theme}
      />
      
      {/* Button row */}
      <div className="flex justify-end mt-2">
        <Skeleton 
          width="100px" 
          height="36px" 
          borderRadius="6px"
          theme={theme}
        />
      </div>
    </div>
  );
}

/**
 * Party Details Skeleton for loading states in party detail views
 */
export function SkeletonPartyDetails({ 
  className,
  theme = "valorant"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div className={cn("space-y-8", className)}>
      {/* Party Header */}
      <div className="bg-[rgba(31,39,49,0.3)] backdrop-blur-sm rounded-xl border border-[rgba(255,255,255,0.05)] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="p-6 border-b border-[rgba(255,255,255,0.05)]">
          <div className="flex justify-between items-start gap-4">
            <div className="flex-1">
              <Skeleton 
                width="70%" 
                height="32px" 
                className="mb-3"
                theme={theme}
              />
              
              <div className="flex flex-wrap gap-2 mb-3">
                {[1, 2, 3, 4].map((i) => (
                  <Skeleton 
                    key={i} 
                    width="40px" 
                    height="40px" 
                    variant="circular"
                    theme={theme}
                  />
                ))}
              </div>
              
              <div className="flex gap-2 mt-3">
                {[1, 2].map((i) => (
                  <Skeleton 
                    key={i}
                    width="80px" 
                    height="24px" 
                    borderRadius="9999px"
                    theme={theme}
                  />
                ))}
              </div>
            </div>
            
            <div className="flex gap-2">
              {[1, 2].map((i) => (
                <Skeleton 
                  key={i}
                  width="40px" 
                  height="40px" 
                  variant="circular"
                  theme={theme}
                />
              ))}
            </div>
          </div>
          
          <Skeleton 
            width="100%" 
            height="48px" 
            className="mt-4"
            theme={theme}
          />
        </div>
        
        {/* Game Settings */}
        <div className="p-6 bg-[rgba(31,39,49,0.4)]">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {[1, 2, 3].map((i) => (
              <Skeleton 
                key={i}
                width="100%" 
                height="64px" 
                borderRadius="0.5rem"
                theme={theme}
              />
            ))}
          </div>
        </div>
      </div>
      
      {/* Player Slots */}
      <div className="bg-[rgba(31,39,49,0.3)] backdrop-blur-sm rounded-xl border border-[rgba(255,255,255,0.05)] shadow-2xl p-6">
        <Skeleton 
          width="30%" 
          height="24px" 
          className="mb-4"
          theme={theme}
        />
        
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-3">
          {[1, 2, 3, 4, 5].map((i) => (
            <Skeleton 
              key={i}
              width="100%" 
              height="80px" 
              borderRadius="0.5rem"
              theme={theme}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * Profile Skeleton for loading states in profile views
 */
export function SkeletonProfile({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div className={cn("p-6 max-w-md mx-auto", className)}>
      <div className="flex flex-col items-center">
        <Skeleton 
          variant="circular" 
          width="96px" 
          height="96px" 
          className="mb-4"
          theme={theme}
        />
        
        <Skeleton 
          width="70%" 
          height="28px" 
          className="mb-2"
          theme={theme}
        />
        
        <Skeleton 
          width="50%" 
          height="20px" 
          className="mb-6"
          theme={theme}
        />
        
        <div className="w-full space-y-3">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton 
              key={i}
              width="100%" 
              height="24px"
              theme={theme}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * Authentication Skeleton for loading states during authentication flows
 */
export function SkeletonAuthentication({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div className={cn(
      "min-h-screen bg-gradient-to-br flex items-center justify-center p-6",
      theme === "valorant" ? "from-[#0F1923] to-[#1F2731]" : "from-[#111827] to-[#1f2937]",
      className
    )}>
      <div className={cn(
        "p-8 rounded-xl backdrop-blur-sm border shadow-lg flex flex-col items-center",
        "border-[rgba(255,255,255,0.05)] bg-[rgba(31,39,49,0.3)]", // Updated to match PairingsPage
        className
      )}>
        <Skeleton 
          variant="circular" 
          width="64px" 
          height="64px" 
          className="mb-6"
          theme={theme}
        />
        
        <Skeleton 
          width="200px" 
          height="28px" 
          className="mb-4"
          theme={theme}
        />
        
        <Skeleton 
          width="300px" 
          height="20px" 
          className="mb-2"
          theme={theme}
        />
      </div>
    </div>
  );
}

/**
 * Individual Leaderboard Row Skeleton for granular control
 */
export function SkeletonLeaderboardRow({
  className,
  theme = "dashboard",
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
  rank?: number;
}) {
  return (
    <div
      className={cn(
        "grid gap-4 items-center border-b border-[rgba(255,255,255,0.05)] transition-all duration-150",
        // Responsive grid matching leaderboard.css breakpoints
        "grid-cols-[80px_1fr_100px]", // Default desktop
        "md:grid-cols-[80px_1fr_100px]", // Medium screens
        "sm:grid-cols-[60px_1fr_90px]", // Tablet/small desktop
        "max-[480px]:grid-cols-[50px_1fr_70px]", // Mobile
        "py-3.5 px-6", // Default padding
        "sm:py-3 sm:px-4", // Smaller padding on mobile
        className
      )}
    >
      {/* Rank skeleton */}
      <div className="flex items-center justify-center">
        <Skeleton
          width="40px"
          height="24px"
          borderRadius="6px"
          theme={theme}
        />
      </div>
      
      {/* User info skeleton */}
      <div className="flex items-center gap-4 sm:gap-3">
        {/* Avatar - responsive sizing matching leaderboard.css */}
        <Skeleton
          variant="circular"
          className="w-10 h-10 sm:w-9 sm:h-9 max-[480px]:w-8 max-[480px]:h-8"
          theme={theme}
        />
        
        {/* User info */}
        <div className="flex-1 space-y-1">
          <Skeleton
            width="65%"
            height="20px"
            borderRadius="4px"
            theme={theme}
            className="max-[480px]:h-4"
          />
          <Skeleton
            width="45%"
            height="16px"
            borderRadius="4px"
            theme={theme}
            className="max-[480px]:h-3 max-[480px]:w-[35%]"
          />
        </div>
      </div>
      
      {/* Credits/Level skeleton */}
      <div className="flex items-center justify-end">
        <Skeleton
          width="70px"
          height="24px"
          borderRadius="6px"
          theme={theme}
          className="max-[480px]:w-14 max-[480px]:h-5"
        />
      </div>
    </div>
  );
}

/**
 * Leaderboard Skeleton for loading states in leaderboard views
 */
export function SkeletonLeaderboard({ 
  className,
  theme = "dashboard",
  itemCount = 9,
  showHeader = true
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
  itemCount?: number;
  showHeader?: boolean;
}) {
  // Inject styles once
  React.useEffect(() => {
    injectStyles();
  }, []);

  return (
    <div
      className={cn(
        // Updated to exactly match PairingsPage valorant-card styling
        "bg-[rgba(31,39,49,0.3)] border border-[rgba(255,255,255,0.05)]",
        "rounded-lg overflow-hidden backdrop-blur-sm", // Changed from rounded-2xl to rounded-lg to match valorant-card
        "shadow-[0_8px_32px_rgba(0,0,0,0.15)]",
        "transition-all duration-300 ease", // Added transition to match valorant-card
        className
      )}
    >
      {/* Header skeleton */}
      {showHeader && (
        <div className="p-6 border-b border-[rgba(255,255,255,0.05)] bg-[rgba(255,255,255,0.04)]">
          <Skeleton
            width="30%"
            height="28px"
            borderRadius="4px"
            theme={theme}
          />
        </div>
      )}
      
      {/* Table headers skeleton */}
      <div className={cn(
        "grid gap-4 text-sm font-semibold text-white/70 uppercase tracking-wider border-b border-[rgba(255,255,255,0.05)]",
        // Responsive grid matching leaderboard.css breakpoints
        "grid-cols-[80px_1fr_100px]", // Default desktop
        "md:grid-cols-[80px_1fr_100px]", // Medium screens
        "sm:grid-cols-[60px_1fr_90px]", // Tablet/small desktop
        "max-[480px]:grid-cols-[50px_1fr_70px]", // Mobile
        "py-4 px-6", // Default padding
        "sm:py-3 sm:px-4", // Smaller padding on mobile
      )}>
        <div className="text-center">
          <Skeleton
            width="30px"
            height="16px"
            borderRadius="4px"
            theme={theme}
            className="max-[480px]:w-6 max-[480px]:h-3"
          />
        </div>
        <div>
          <Skeleton
            width="40px"
            height="16px"
            borderRadius="4px"
            theme={theme}
            className="max-[480px]:w-8 max-[480px]:h-3"
          />
        </div>
        <div className="text-right">
          <Skeleton
            width="50px"
            height="16px"
            borderRadius="4px"
            theme={theme}
            className="max-[480px]:w-10 max-[480px]:h-3"
          />
        </div>
      </div>
      
      {/* Skeleton rows */}
      <div>
        {Array.from({ length: itemCount }).map((_, index) => (
          <SkeletonLeaderboardRow
            key={index}
            theme={theme}
            rank={index + 1}
          />
        ))}
      </div>
    </div>
  );
}

/**
 * Dashboard Stats Skeleton for loading states in the main stats grid
 * Matches the three-section layout of DashboardPage: Message Stats, Messages, Voice Activity
 */
export function SkeletonDashboardStats({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  // Inject styles once
  React.useEffect(() => {
    injectStyles();
  }, []);

  return (
    <div className={cn("grid grid-cols-1 lg:grid-cols-3 gap-6", className)}>
      {/* Message Stats Section */}
      <div className="p-4 rounded-lg bg-[rgba(31,39,49,0.3)] border border-[rgba(255,255,255,0.05)] backdrop-blur-sm">
        {/* Section header */}
        <div className="flex items-center gap-2 mb-4">
          <Skeleton
            variant="circular"
            width="20px"
            height="20px"
            theme={theme}
          />
          <Skeleton
            width="100px"
            height="20px"
            borderRadius="4px"
            theme={theme}
          />
        </div>
        
        {/* Rank card */}
        <div className="p-4 rounded-lg bg-[rgba(30,41,59,0.5)] border border-[rgba(148,163,184,0.2)] backdrop-blur-sm">
          <Skeleton
            width="80px"
            height="16px"
            borderRadius="4px"
            theme={theme}
            className="mb-2"
          />
          <Skeleton
            width="60px"
            height="24px"
            borderRadius="4px"
            theme={theme}
          />
        </div>
      </div>
      
      {/* Messages Section */}
      <div className="p-4 rounded-lg bg-[rgba(31,39,49,0.3)] border border-[rgba(255,255,255,0.05)] backdrop-blur-sm">
        {/* Section header */}
        <div className="flex items-center gap-2 mb-4">
          <Skeleton
            variant="circular"
            width="20px"
            height="20px"
            theme={theme}
          />
          <Skeleton
            width="80px"
            height="20px"
            borderRadius="4px"
            theme={theme}
          />
        </div>
        
        {/* Time period cards */}
        <div className="space-y-3">
          {['1d', '7d', '14d'].map((period) => (
            <div key={period} className="p-4 rounded-lg bg-[rgba(30,41,59,0.5)] border border-[rgba(148,163,184,0.2)] backdrop-blur-sm flex items-center justify-between">
              <Skeleton
                width="24px"
                height="24px"
                borderRadius="4px"
                theme={theme}
              />
              <div className="flex flex-col items-end gap-1">
                <Skeleton
                  width="60px"
                  height="16px"
                  borderRadius="4px"
                  theme={theme}
                />
                <Skeleton
                  width="40px"
                  height="12px"
                  borderRadius="4px"
                  theme={theme}
                />
              </div>
            </div>
          ))}
        </div>
      </div>
      
      {/* Voice Activity Section */}
      <div className="p-4 rounded-lg bg-[rgba(31,39,49,0.3)] border border-[rgba(255,255,255,0.05)] backdrop-blur-sm">
        {/* Section header */}
        <div className="flex items-center gap-2 mb-4">
          <Skeleton
            variant="circular"
            width="20px"
            height="20px"
            theme={theme}
          />
          <Skeleton
            width="100px"
            height="20px"
            borderRadius="4px"
            theme={theme}
          />
        </div>
        
        {/* Time period cards */}
        <div className="space-y-3">
          {['1d', '7d', '14d'].map((period) => (
            <div key={period} className="p-4 rounded-lg bg-[rgba(30,41,59,0.5)] border border-[rgba(148,163,184,0.2)] backdrop-blur-sm flex items-center justify-between">
              <Skeleton
                width="24px"
                height="24px"
                borderRadius="4px"
                theme={theme}
              />
              <div className="flex flex-col items-end gap-1">
                <Skeleton
                  width="50px"
                  height="16px"
                  borderRadius="4px"
                  theme={theme}
                />
                <Skeleton
                  width="30px"
                  height="12px"
                  borderRadius="4px"
                  theme={theme}
                />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * Dashboard Activity Skeleton for loading states in the activity overview section
 */
export function SkeletonDashboardActivity({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div className={cn("p-4 rounded-lg bg-[rgba(31,39,49,0.3)] border border-[rgba(255,255,255,0.05)] backdrop-blur-sm", className)}>
      {/* Section header */}
      <div className="flex items-center gap-2 mb-4">
        <Skeleton
          variant="circular"
          width="20px"
          height="20px"
          theme={theme}
        />
        <Skeleton
          width="130px"
          height="20px"
          borderRadius="4px"
          theme={theme}
        />
      </div>
      
      {/* Activity summary */}
      <div className="space-y-3">
        <div className="flex items-center gap-3 p-3 rounded-lg bg-[rgba(30,41,59,0.5)] border border-[rgba(148,163,184,0.2)] backdrop-blur-sm">
          <Skeleton
            variant="circular"
            width="16px"
            height="16px"
            theme={theme}
          />
          <Skeleton
            width="100px"
            height="16px"
            borderRadius="4px"
            theme={theme}
            className="flex-1"
          />
          <Skeleton
            width="80px"
            height="16px"
            borderRadius="4px"
            theme={theme}
          />
        </div>
      </div>
    </div>
  );
}

/**
 * Dashboard Chart Skeleton for loading states in the charts section
 */
export function SkeletonDashboardChart({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div className={cn("p-4 rounded-lg bg-[rgba(31,39,49,0.3)] border border-[rgba(255,255,255,0.05)] backdrop-blur-sm", className)}>
      {/* Charts section header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Skeleton
            variant="circular"
            width="20px"
            height="20px"
            theme={theme}
          />
          <Skeleton
            width="60px"
            height="20px"
            borderRadius="4px"
            theme={theme}
          />
        </div>
        
        {/* Chart legend */}
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <Skeleton
              variant="circular"
              width="12px"
              height="12px"
              theme={theme}
            />
            <Skeleton
              width="50px"
              height="14px"
              borderRadius="4px"
              theme={theme}
            />
          </div>
          <div className="flex items-center gap-2">
            <Skeleton
              variant="circular"
              width="12px"
              height="12px"
              theme={theme}
            />
            <Skeleton
              width="40px"
              height="14px"
              borderRadius="4px"
              theme={theme}
            />
          </div>
        </div>
      </div>
      
      {/* Chart container */}
      <div className="mt-4 rounded-lg overflow-hidden bg-[rgba(30,41,59,0.5)] border border-[rgba(148,163,184,0.2)] backdrop-blur-sm" style={{ height: '250px' }}>
        <Skeleton
          width="100%"
          height="100%"
          theme={theme}
          animate={true}
        />
      </div>
    </div>
  );
}

/**
 * Active Pairings Leaderboard Skeleton
 * Matches the exact structure of PairingLeaderboard.tsx with top 3 podium and remaining rankings
 */
export function SkeletonActivePairingsLeaderboard({ 
  className,
  theme = "valorant",
  showTopThree = true,
  remainingPairsCount = 5
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
  showTopThree?: boolean;
  remainingPairsCount?: number;
}) {
  // Inject styles once
  React.useEffect(() => {
    injectStyles();
  }, []);

  return (
    <div
      className={cn(
        // Match PairingLeaderboard main card styling but remove theme border
        "bg-theme-container rounded-lg overflow-hidden",
        "border border-[rgba(255,255,255,0.05)]", // Use subtle border instead of border-theme
        "shadow-[0_8px_32px_rgba(0,0,0,0.15)]",
        className
      )}
    >
      {/* Card Header - matches PairingLeaderboard header */}
      <div className="p-6 border-b border-[rgba(255,255,255,0.1)]">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-white">
            <Skeleton
              variant="circular"
              width="20px"
              height="20px"
              theme={theme}
            />
            <Skeleton
              width="200px"
              height="24px"
              borderRadius="4px"
              theme={theme}
            />
          </div>
          <Skeleton
            width="60px"
            height="20px"
            borderRadius="12px"
            theme={theme}
          />
        </div>
      </div>

      {/* Card Content */}
      <div className="p-6 space-y-6">
        {/* Top Three Podium Section */}
        {showTopThree && (
          <div className="mb-8">
            {/* Top Pairs Title */}
            <div className="text-center mb-6">
              <Skeleton
                width="150px"
                height="28px"
                borderRadius="4px"
                theme={theme}
                className="mx-auto"
              />
            </div>
            
            {/* Top 3 Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {[1, 2, 3].map((rank) => (
                <div
                  key={rank}
                  className={cn(
                    "relative h-full",
                    // Match the responsive ordering from PairingLeaderboard
                    rank === 1 ? 'md:order-2 md:scale-110' : 
                    rank === 2 ? 'md:order-1' : 'md:order-3'
                  )}
                >
                  {/* Podium Card */}
                  <div className={cn(
                    "h-full rounded-lg p-6 relative",
                    // Use consistent skeleton theming for all podium cards
                    "bg-[rgba(31,39,49,0.4)] border border-[rgba(255,255,255,0.08)]",
                    "backdrop-blur-sm"
                  )}>
                    {/* Rank Badge */}
                    <div className="absolute -top-3 left-1/2 transform -translate-x-1/2 z-10">
                      <Skeleton
                        variant="circular"
                        width="32px"
                        height="32px"
                        theme={theme}
                        className="bg-[rgba(31,39,49,0.6)] border border-[rgba(255,255,255,0.1)]"
                      />
                    </div>

                    {/* Card Header */}
                    <div className="pb-4 pt-6">
                      <div className="text-center">
                        <div className="flex items-center justify-center gap-2 mb-4">
                          <Skeleton
                            variant="circular"
                            width="20px"
                            height="20px"
                            theme={theme}
                          />
                          <Skeleton
                            width="60px"
                            height="20px"
                            borderRadius="4px"
                            theme={theme}
                          />
                        </div>
                      </div>
                    </div>

                    {/* User Profiles */}
                    <div className="flex items-center justify-center gap-4 mb-4">
                      <div className="flex flex-col items-center">
                        <Skeleton
                          variant="circular"
                          width="48px"
                          height="48px"
                          theme={theme}
                          className="mb-2"
                        />
                        <Skeleton
                          width="60px"
                          height="16px"
                          borderRadius="4px"
                          theme={theme}
                        />
                      </div>

                      <Skeleton
                        variant="circular"
                        width="24px"
                        height="24px"
                        theme={theme}
                      />

                      <div className="flex flex-col items-center">
                        <Skeleton
                          variant="circular"
                          width="48px"
                          height="48px"
                          theme={theme}
                          className="mb-2"
                        />
                        <Skeleton
                          width="60px"
                          height="16px"
                          borderRadius="4px"
                          theme={theme}
                        />
                      </div>
                    </div>

                    {/* Stats Grid */}
                    <div className="grid grid-cols-2 gap-3 text-sm">
                      {[1, 2, 3, 4].map((stat) => (
                        <div key={stat} className="text-center p-2 rounded-lg bg-black/20">
                          <Skeleton
                            variant="circular"
                            width="16px"
                            height="16px"
                            theme={theme}
                            className="mx-auto mb-1"
                          />
                          <Skeleton
                            width="40px"
                            height="16px"
                            borderRadius="4px"
                            theme={theme}
                            className="mx-auto mb-1"
                          />
                          <Skeleton
                            width="50px"
                            height="12px"
                            borderRadius="4px"
                            theme={theme}
                            className="mx-auto"
                          />
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Full Rankings Section */}
        {remainingPairsCount > 0 && (
          <div>
            {/* Full Rankings Title */}
            <div className="flex items-center gap-2 mb-4">
              <Skeleton
                variant="circular"
                width="20px"
                height="20px"
                theme={theme}
              />
              <Skeleton
                width="120px"
                height="20px"
                borderRadius="4px"
                theme={theme}
              />
            </div>
            
            {/* Rankings List */}
            <div className="space-y-3">
              {Array.from({ length: remainingPairsCount }).map((_, index) => {
                const rank = index + 4;
                return (
                  <div
                    key={index}
                    className="flex items-center gap-4 p-4 rounded-lg bg-theme-container border border-[rgba(255,255,255,0.05)] transition-colors"
                  >
                    {/* Rank */}
                    <div className="flex-shrink-0">
                      <Skeleton
                        variant="circular"
                        width="32px"
                        height="32px"
                        theme={theme}
                      />
                    </div>

                    {/* User Avatars */}
                    <div className="flex items-center gap-2">
                      <Skeleton
                        variant="circular"
                        width="32px"
                        height="32px"
                        theme={theme}
                      />
                      <Skeleton
                        variant="circular"
                        width="16px"
                        height="16px"
                        theme={theme}
                      />
                      <Skeleton
                        variant="circular"
                        width="32px"
                        height="32px"
                        theme={theme}
                      />
                    </div>

                    {/* Names */}
                    <div className="flex-1 min-w-0">
                      <Skeleton
                        width="70%"
                        height="16px"
                        borderRadius="4px"
                        theme={theme}
                        className="mb-1"
                      />
                      <Skeleton
                        width="50%"
                        height="12px"
                        borderRadius="4px"
                        theme={theme}
                      />
                    </div>

                    {/* Stats */}
                    <div className="flex items-center gap-4 text-sm">
                      <Skeleton
                        width="60px"
                        height="20px"
                        borderRadius="10px"
                        theme={theme}
                      />
                      <Skeleton
                        width="50px"
                        height="16px"
                        borderRadius="4px"
                        theme={theme}
                      />
                      <Skeleton
                        width="55px"
                        height="16px"
                        borderRadius="4px"
                        theme={theme}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Complete Dashboard Page Skeleton combining all sections
 * Matches the exact structure of DashboardPage.tsx
 */
export function SkeletonDashboardPage({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  // Inject styles once
  React.useEffect(() => {
    injectStyles();
  }, []);

  return (
    <div className={cn("bg-theme-gradient min-h-screen", className)}>
      <div className="container mx-auto px-4 py-8 max-w-6xl">
        {/* Refresh Button - Top Right Corner */}
        <div className="absolute top-4 right-4 z-10">
          <Skeleton
            width="40px"
            height="32px"
            borderRadius="6px"
            theme={theme}
          />
        </div>

        {/* Hero Section */}
        <div className="section-header mb-12 text-center">
          <Skeleton
            width="200px"
            height="48px"
            borderRadius="8px"
            theme={theme}
            className="mx-auto mb-6"
          />
          <Skeleton
            width="300px"
            height="24px"
            borderRadius="6px"
            theme={theme}
            className="mx-auto"
          />
        </div>

        {/* Dashboard Content Wrapper */}
        <div className="discord-dashboard">
          {/* Main Stats Grid */}
          <div className="mb-6">
            <SkeletonDashboardStats theme={theme} />
          </div>

          {/* Bottom Section */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
            {/* Activity Overview */}
            <SkeletonDashboardActivity theme={theme} />
            
            {/* Charts Section */}
            <SkeletonDashboardChart theme={theme} />
          </div>

          {/* Footer */}
          <div className="text-center text-sm py-4">
            <Skeleton
              width="200px"
              height="16px"
              borderRadius="4px"
              theme={theme}
              className="mx-auto"
            />
          </div>
        </div>
      </div>
    </div>
  );
}
