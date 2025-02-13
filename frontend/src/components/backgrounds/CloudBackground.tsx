export function CloudBackground() {
  return (
    <div className="absolute inset-0 overflow-hidden">
      <div className="absolute w-[500px] h-[500px] bg-white/10 rounded-full blur-3xl -top-48 -left-24" />
      <div className="absolute w-[400px] h-[400px] bg-white/10 rounded-full blur-3xl top-1/2 right-0" />
      <div className="absolute w-[600px] h-[600px] bg-white/10 rounded-full blur-3xl -bottom-48 -left-24" />
    </div>
  );
} 