import { cn } from '@/utils/cn';
import { TbClick } from "react-icons/tb";
import { BiLockAlt } from "react-icons/bi";
import { useState } from 'react';

interface GameCardProps {
  title: string;
  image: string;
  logo: string;
  alt: string;
  className?: string;
  isClickable?: boolean;
  isAvailable?: boolean;
}

export function GameCard({ 
  title, 
  image, 
  logo, 
  alt, 
  className, 
  isClickable = false,
  isAvailable = true
}: GameCardProps) {
  const [isHovering, setIsHovering] = useState(false);
  
  return (
    <div
      className={cn(
        "group relative overflow-hidden rounded-xl bg-black/5 backdrop-blur-sm transition-all duration-300",
        "border border-white/15 shadow-md",
        "animate-gameCard",
        isClickable && isAvailable ? "cursor-pointer hover:shadow-lg hover:border-white/30" : "cursor-default",
        !isAvailable && "opacity-75 grayscale-[30%]",
        className
      )}
      tabIndex={isClickable && isAvailable ? 0 : -1}
      role={isClickable && isAvailable ? "button" : "presentation"}
      onMouseEnter={() => setIsHovering(true)}
      onMouseLeave={() => setIsHovering(false)}
      onFocus={() => setIsHovering(true)}
      onBlur={() => setIsHovering(false)}
    >
      <div className="aspect-[3/4] relative overflow-hidden rounded-lg">
        {/* Background image with subtle zoom */}
        <img 
          src={image} 
          alt={alt} 
          className={cn(
            "object-cover w-full h-full transform transition-transform",
            isHovering && isAvailable ? "duration-700 scale-105" : "duration-500"
          )}
        />
        
        {/* Permanent title banner with enhanced visibility */}
        <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent pt-8 pb-3 px-3">
          <div className="flex items-center">
            <img
              src={logo}
              alt={`${title} logo`}
              width="24"
              height="24"
              className="rounded-md mr-2 transition-opacity duration-300"
            />
            <p className="text-white text-lg font-medium">{title}</p>
          </div>
        </div>
      </div>
      
      {/* Subtle overlay effect on hover */}
      <div 
        className={cn(
          "absolute inset-0 bg-gradient-to-t from-black/40 to-transparent transition-opacity duration-300",
          isHovering && isAvailable ? "opacity-100" : "opacity-0"
        )}
      />
      
      {/* Border highlight animation on hover for clickable cards */}
      {isClickable && isAvailable && (
        <div 
          className={cn(
            "absolute inset-0 rounded-xl transition-opacity duration-300 pointer-events-none",
            "border-2 border-white/50",
            isHovering ? "opacity-100" : "opacity-0"
          )}
        />
      )}
      
      {/* Unavailable game overlay */}
      {!isAvailable && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
          <BiLockAlt className="text-white/90 text-4xl mb-2" />
          <p className="text-white/90 text-sm font-medium px-3 py-1.5 bg-black/40 rounded-full">
            Coming Soon
          </p>
        </div>
      )}
      
      {/* Mobile touch hint that fades out */}
      {isClickable && isAvailable && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none md:hidden opacity-0 animate-tapHint">
          <div className="h-16 w-16 rounded-full bg-white/30 flex items-center justify-center backdrop-blur-md">
            <TbClick className="text-white text-2xl" />
          </div>
        </div>
      )}
    </div>
  );
} 