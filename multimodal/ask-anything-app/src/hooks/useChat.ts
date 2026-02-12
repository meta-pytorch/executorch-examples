/**
 * Custom hook for chat operations.
 */
import { useCallback } from "react";
import { useAppStore } from "../contexts/AppContext";
import { api } from "../services/api";
import { useCamera } from "./useCamera";

export function useChat() {
  const { messages, isThinking, addMessage, setIsThinking } = useAppStore();
  const { captureFrame } = useCamera();

  const sendMessage = useCallback(
    async (content: string, imageBase64?: string) => {
      // Capture frame if not provided
      const image = imageBase64 || captureFrame();

      // Add user message
      addMessage({ role: "user", content, imageBase64: image || undefined });

      // Set thinking state
      setIsThinking(true);

      try {
        // Call vision API
        const response = await api.visionInfer({
          prompt: content,
          image_base64: image || undefined,
          max_new_tokens: 256,
          temperature: 0.7,
        });

        // Add assistant response
        addMessage({ role: "assistant", content: response.response });
      } catch (error) {
        console.error("Vision inference failed:", error);
        addMessage({
          role: "assistant",
          content:
            "Sorry, I encountered an error processing your request. Please make sure the backend server is running on http://localhost:8000",
        });
      } finally {
        setIsThinking(false);
      }
    },
    [addMessage, setIsThinking, captureFrame]
  );

  return {
    messages,
    isThinking,
    sendMessage,
  };
}
