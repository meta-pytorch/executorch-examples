/**
 * Global state management using Zustand.
 */
import { create } from "zustand";
import type { Message, AppStatus, CameraState } from "../types";

interface AppState {
  // Messages
  messages: Message[];
  addMessage: (message: Omit<Message, "id" | "timestamp">) => void;
  clearMessages: () => void;

  // Loading states
  isThinking: boolean;
  setIsThinking: (thinking: boolean) => void;

  // Frozen frame (shown while thinking)
  frozenFrame: string | null;
  setFrozenFrame: (frame: string | null) => void;

  // Camera
  camera: CameraState;
  setCameraStream: (stream: MediaStream | null) => void;
  setCameraError: (error: string | null) => void;

  // Model status
  status: AppStatus | null;
  setStatus: (status: AppStatus) => void;

  // Settings
  isSettingsOpen: boolean;
  setSettingsOpen: (open: boolean) => void;
}

export const useAppStore = create<AppState>((set) => ({
  // Messages
  messages: [],
  addMessage: (message) =>
    set((state) => ({
      messages: [
        ...state.messages,
        {
          ...message,
          id: crypto.randomUUID(),
          timestamp: new Date(),
        },
      ],
    })),
  clearMessages: () => set({ messages: [] }),

  // Loading
  isThinking: false,
  setIsThinking: (thinking) => set({ isThinking: thinking }),

  // Frozen frame
  frozenFrame: null,
  setFrozenFrame: (frame) => set({ frozenFrame: frame }),

  // Camera
  camera: { isActive: false, stream: null, error: null },
  setCameraStream: (stream) =>
    set({
      camera: { isActive: !!stream, stream, error: null },
    }),
  setCameraError: (error) =>
    set({
      camera: { isActive: false, stream: null, error },
    }),

  // Status
  status: null,
  setStatus: (status) => set({ status }),

  // Settings
  isSettingsOpen: false,
  setSettingsOpen: (open) => set({ isSettingsOpen: open }),
}));
