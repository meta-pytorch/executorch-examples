/**
 * Thinking indicator with animated dots and glass effect.
 */
export function ThinkingIndicator() {
  return (
    <div className="flex justify-start mb-5">
      <div className="glass-bubble-ai rounded-[20px] rounded-bl-[6px] px-5 py-4">
        <div className="flex space-x-2 items-center">
          <div
            className="w-2 h-2 bg-white/70 rounded-full animate-bounce"
            style={{ animationDelay: "0ms", animationDuration: "0.6s" }}
          />
          <div
            className="w-2 h-2 bg-white/70 rounded-full animate-bounce"
            style={{ animationDelay: "150ms", animationDuration: "0.6s" }}
          />
          <div
            className="w-2 h-2 bg-white/70 rounded-full animate-bounce"
            style={{ animationDelay: "300ms", animationDuration: "0.6s" }}
          />
        </div>
        <p className="text-[12px] text-white/60 mt-3 font-medium">
          Gemma is thinking...
        </p>
      </div>
    </div>
  );
}
