import { cn } from '@/utils/cn';

interface GameCardProps {
  title: string;
  image: string;
  logo: string;
  alt: string;
  className?: string;
}

export function GameCard({ title, image, logo, alt, className }: GameCardProps) {
  return (
    <div
      className={cn(
        "group relative overflow-hidden rounded-xl bg-black/5 backdrop-blur-sm transition-all duration-300 hover:scale-[1.03] hover:bg-black/10",
        "border border-white/15 shadow-lg hover:shadow-xl hover:border-white/20",
        "animate-gameCard",
        className
      )}
    >
      <div className="aspect-[3/4] relative overflow-hidden rounded-lg">
        <img 
          src={image} 
          alt={alt} 
          className="object-cover w-full h-full transform transition-transform duration-500 group-hover:scale-105" 
        />
      </div>
      
      {/* Static logo at bottom-left - hidden on hover */}
      <div className="absolute bottom-3 left-3 group-hover:opacity-0 transition-opacity duration-300">
        <img
          src={logo}
          alt={`${title} logo`}
          width="32"
          height="32"
          className="rounded-md bg-black/60 p-1 backdrop-blur-md shadow-md"
        />
      </div>

      {/* Hover overlay with optimized performance */}
      <div 
        className="absolute inset-0 flex flex-col items-center justify-center bg-gradient-to-t from-black/70 via-black/50 to-black/30 backdrop-blur-sm opacity-0 transition-all duration-400 group-hover:opacity-100" 
        style={{ willChange: "opacity, backdrop-filter", transform: "translateZ(0)" }}
      >
        <div className="transform translate-y-4 transition-transform duration-300 group-hover:translate-y-0">
          <p className="text-white text-xl font-bold drop-shadow-md px-3 py-1 rounded-md">
            {title}
          </p>
        </div>
      </div>
    </div>
  );
} 