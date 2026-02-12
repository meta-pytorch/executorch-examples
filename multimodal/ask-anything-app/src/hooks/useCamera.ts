/**
 * Custom hook for camera operations.
 */
import { useCallback } from "react";
import { useAppStore } from "../contexts/AppContext";

export function useCamera() {
  const { camera, setCameraStream, setCameraError } = useAppStore();

  const startCamera = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: 1280, height: 720, facingMode: "user" },
      });
      setCameraStream(stream);
      return stream;
    } catch (error) {
      setCameraError("Failed to access camera");
      return null;
    }
  }, [setCameraStream, setCameraError]);

  const stopCamera = useCallback(() => {
    if (camera.stream) {
      camera.stream.getTracks().forEach((track) => track.stop());
    }
    setCameraStream(null);
  }, [camera.stream, setCameraStream]);

  const captureFrame = useCallback((): string | null => {
    // Find video element in the DOM
    const video = document.querySelector("video");
    if (!video || video.videoWidth === 0) return null;

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    const ctx = canvas.getContext("2d");
    if (!ctx) return null;

    ctx.drawImage(video, 0, 0);
    return canvas.toDataURL("image/jpeg", 0.8).split(",")[1]; // Base64 only
  }, []);

  return {
    isActive: camera.isActive,
    error: camera.error,
    startCamera,
    stopCamera,
    captureFrame,
  };
}
