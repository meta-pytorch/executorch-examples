/**
 * Main chat interface with messages and input.
 * Designed for glass layout with transparent backgrounds.
 */
import { useRef, useEffect } from "react";
import { useAppStore } from "../../contexts/AppContext";
import { useCameraCapture } from "../camera/CameraStream";
import { api } from "../../services/api";
import { ChatMessage } from "./ChatMessage";
import { ChatInput } from "./ChatInput";
import { ThinkingIndicator } from "./ThinkingIndicator";

export function ChatInterface() {
  const { messages, isThinking, addMessage, setIsThinking, setFrozenFrame } = useAppStore();
  const { captureFrame } = useCameraCapture();
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isThinking]);

  const handleSend = async (content: string) => {
    // Capture current frame when user sends message
    const imageBase64 = captureFrame();

    // Freeze the camera on this frame while processing
    if (imageBase64) {
      setFrozenFrame(imageBase64);
    }

    // Add user message with captured image
    addMessage({
      role: "user",
      content,
      imageBase64: imageBase64 || undefined,
    });

    // Set thinking state
    setIsThinking(true);

    try {
      // Call vision API
      const response = await api.visionInfer({
        prompt: content,
        image_base64: imageBase64 || undefined,
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
          "Sorry, I encountered an error processing your request. Please make sure the backend server is running.",
      });
    } finally {
      setIsThinking(false);
      // Unfreeze camera when done
      setFrozenFrame(null);
    }
  };

  const handleVoiceResult = (transcription: string) => {
    console.log("Voice transcription:", transcription);
  };

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Header */}
      <div className="pb-4 border-b border-white/10 flex-shrink-0">
        <div>
          <h2 className="text-base font-semibold text-white drop-shadow-sm">Ask Anything</h2>
          <p className="text-xs text-white/60 mt-1">Powered by Gemma 3 Vision</p>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto py-4 min-h-0">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-center px-4">
            <h3 className="text-lg font-semibold text-white mb-3 drop-shadow-sm">
              Welcome!
            </h3>
            <p className="text-white/70 text-sm max-w-xs leading-relaxed">
              Ask any question about what you see in the camera. I'll analyze
              the current frame and provide an answer.
            </p>
            <div className="mt-5 flex flex-wrap justify-center gap-2">
              <span className="px-4 py-2 bg-white/10 backdrop-blur-sm border border-white/20 rounded-full text-xs text-white/80">
                "What do you see?"
              </span>
              <span className="px-4 py-2 bg-white/10 backdrop-blur-sm border border-white/20 rounded-full text-xs text-white/80">
                "How many people?"
              </span>
              <span className="px-4 py-2 bg-white/10 backdrop-blur-sm border border-white/20 rounded-full text-xs text-white/80">
                "Describe this"
              </span>
            </div>
          </div>
        )}

        {messages.map((message) => (
          <ChatMessage key={message.id} message={message} />
        ))}

        {isThinking && <ThinkingIndicator />}

        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="flex-shrink-0">
        <ChatInput
          onSend={handleSend}
          onVoiceResult={handleVoiceResult}
          disabled={isThinking}
        />
      </div>
    </div>
  );
}
