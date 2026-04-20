/**
 * Camera stream component with real-time video and frame capture.
 * Supports fullscreen mode for glass layout.
 */
import { useEffect, useRef, useCallback, useState } from "react";
import { useAppStore } from "../../contexts/AppContext";

interface CameraStreamProps {
  fullscreen?: boolean;
}

export function CameraStream({ fullscreen = false }: CameraStreamProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { camera, setCameraStream, setCameraError, frozenFrame } = useAppStore();
  const [isInitializing, setIsInitializing] = useState(true);

  const startCamera = useCallback(async () => {
    try {
      setIsInitializing(true);
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: 1280, height: 720, facingMode: "user" },
      });
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      setCameraStream(stream);
      setIsInitializing(false);
    } catch (error) {
      setCameraError("Failed to access camera. Please allow camera access.");
      setIsInitializing(false);
    }
  }, [setCameraStream, setCameraError]);

  const stopCamera = useCallback(() => {
    if (camera.stream) {
      camera.stream.getTracks().forEach((track) => track.stop());
    }
    setCameraStream(null);
  }, [camera.stream, setCameraStream]);

  useEffect(() => {
    startCamera();
    return () => stopCamera();
  }, []);

  // Fullscreen mode - minimal UI, glass layout handles overlays
  if (fullscreen) {
    return (
      <div className="relative w-full h-full bg-black">
        {/* Video */}
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className={`w-full h-full object-cover ${frozenFrame ? "hidden" : ""}`}
        />

        {/* Frozen frame overlay while processing */}
        {frozenFrame && (
          <img
            src={`data:image/jpeg;base64,${frozenFrame}`}
            alt="Processing frame"
            className="w-full h-full object-cover"
          />
        )}

        {/* Hidden canvas for frame capture */}
        <canvas ref={canvasRef} className="hidden" />

        {/* Loading state */}
        {isInitializing && !camera.error && (
          <div className="absolute inset-0 flex items-center justify-center bg-black">
            <div className="text-center">
              <div className="w-16 h-16 border-4 border-white/30 border-t-white rounded-full animate-spin mb-6 mx-auto" />
              <p className="text-white text-lg font-medium">Starting camera...</p>
              <p className="text-white/60 text-sm mt-2">
                Please allow camera access when prompted
              </p>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Standard mode with full UI (legacy)
  return (
    <div className="relative h-full bg-gray-900 flex flex-col">
      {/* Header overlay */}
      <div className="absolute top-0 left-0 right-0 z-10 p-5 bg-gradient-to-b from-black/60 to-transparent">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className={`w-5 h-3 rounded-full shadow-lg ${
                camera.isActive
                  ? "bg-green-500 animate-pulse shadow-green-500/50"
                  : "bg-red-500 shadow-red-500/50"
              }`}
            />
            <span className="text-white text-sm font-medium">
              {camera.isActive ? "Live Camera" : "Camera Inactive"}
            </span>
          </div>
          {camera.isActive && (
            <span className="text-white/70 text-xs bg-white/10 px-3 py-1 rounded-full">
              Frame captured on send
            </span>
          )}
        </div>
      </div>

      {/* Video */}
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className="w-full h-full object-cover"
      />

      {/* Hidden canvas for frame capture */}
      <canvas ref={canvasRef} className="hidden" />

      {/* Error state */}
      {camera.error && (
        <div className="absolute inset-0 flex items-center justify-center bg-gray-900">
          <div className="text-center p-8 max-w-sm">
            <div className="w-20 h-20 rounded-full bg-red-500/20 flex items-center justify-center mx-auto mb-6">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={1.5}
                stroke="currentColor"
                className="w-10 h-10 text-red-500"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M15.75 10.5l4.72-4.72a.75.75 0 011.28.53v11.38a.75.75 0 01-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 002.25-2.25v-9a2.25 2.25 0 00-2.25-2.25h-9A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z"
                />
              </svg>
            </div>
            <h3 className="text-white text-xl font-semibold mb-2">
              Camera Unavailable
            </h3>
            <p className="text-gray-400 text-sm mb-6 leading-relaxed">
              {camera.error}
            </p>
            <button
              onClick={startCamera}
              className="px-6 py-3 bg-[#0084ff] text-white rounded-full hover:bg-[#0073e6] transition-all duration-200 font-medium shadow-lg hover:shadow-xl"
            >
              Try Again
            </button>
          </div>
        </div>
      )}

      {/* Loading state */}
      {isInitializing && !camera.error && (
        <div className="absolute inset-0 flex items-center justify-center bg-gray-900">
          <div className="text-center">
            <div className="w-16 h-16 border-4 border-[#0084ff] border-t-transparent rounded-full animate-spin mb-6 mx-auto" />
            <p className="text-white text-lg font-medium">Starting camera...</p>
            <p className="text-gray-400 text-sm mt-2">
              Please allow camera access when prompted
            </p>
          </div>
        </div>
      )}

      {/* Bottom gradient for visual depth */}
      <div className="absolute bottom-0 left-0 right-0 h-24 bg-gradient-to-t from-black/30 to-transparent pointer-events-none" />
    </div>
  );
}

/**
 * Hook to capture the current camera frame.
 */
export function useCameraCapture() {
  const captureFrame = useCallback((): string | null => {
    // Find video element in the DOM
    const video = document.querySelector("video");
    const canvas = document.createElement("canvas");

    if (!video || video.videoWidth === 0) return null;

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    const ctx = canvas.getContext("2d");
    if (!ctx) return null;

    ctx.drawImage(video, 0, 0);
    return canvas.toDataURL("image/jpeg", 0.8).split(",")[1]; // Base64 only
  }, []);

  return { captureFrame };
}
