/**
 * Glass layout with full-screen camera background and floating glass panels.
 * Uses CSS glassmorphism for compatibility with video backgrounds.
 */
import { CameraStream } from "../camera/CameraStream";
import { ChatInterface } from "../chat/ChatInterface";
import { useAppStore } from "../../contexts/AppContext";

export function GlassLayout() {
  const { camera, frozenFrame } = useAppStore();

  return (
    <div className="relative w-screen h-screen overflow-hidden bg-black">
      {/* Full-screen camera background */}
      <div className="absolute inset-0 z-0">
        <CameraStream fullscreen />
      </div>

      {/* Glass header bar */}
      <div className="fixed top-6 left-6 right-6 z-20 glass-panel rounded-2xl px-6 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className={`w-3 h-3 rounded-full shadow-lg ${
                frozenFrame
                  ? "bg-yellow-400 shadow-yellow-400/50"
                  : camera.isActive
                    ? "bg-green-400 animate-pulse shadow-green-400/50"
                    : "bg-red-400 shadow-red-400/50"
              }`}
            />
            <span className="text-white text-sm font-medium drop-shadow-sm">
              {frozenFrame
                ? "Processing..."
                : camera.isActive
                  ? "Live Camera"
                  : "Camera Inactive"}
            </span>
          </div>
          {camera.isActive && !frozenFrame && (
            <span className="text-white/80 text-xs bg-white/10 px-3 py-1 rounded-full">
              Frame captured on send
            </span>
          )}
          {frozenFrame && (
            <span className="text-yellow-400/80 text-xs bg-yellow-400/10 px-3 py-1 rounded-full">
              Analyzing frame...
            </span>
          )}
        </div>
      </div>

      {/* Glass chat panel - right side */}
      <div className="fixed top-24 right-6 bottom-6 w-[420px] z-10 glass-panel rounded-3xl p-6 flex flex-col overflow-hidden">
        <ChatInterface />
      </div>

      {/* Camera error overlay */}
      {camera.error && (
        <div className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-30 glass-panel rounded-3xl p-10">
          <div className="text-center max-w-sm">
            <h3 className="text-white text-lg font-semibold mb-3">
              Camera Unavailable
            </h3>
            <p className="text-white/70 text-sm leading-relaxed">
              {camera.error}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
