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
        "leaderboard-container",
        // Updated to match PairingsPage styling
        "bg-[rgba(31,39,49,0.3)] border border-[rgba(255,255,255,0.05)]",
        "rounded-2xl overflow-hidden backdrop-blur-sm",
        "shadow-[0_8px_32px_rgba(0,0,0,0.15)]",
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
