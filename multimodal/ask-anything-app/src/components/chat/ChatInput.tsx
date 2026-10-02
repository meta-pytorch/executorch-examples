/**
 * Chat input with Enter key handling, send button, and voice recording button.
 * Glass-styled controls using CSS glassmorphism.
 */
import { useState, useRef, type KeyboardEvent } from "react";

interface ChatInputProps {
  onSend: (message: string) => void;
  onVoiceResult?: (transcription: string) => void;
  disabled?: boolean;
}

export function ChatInput({ onSend, onVoiceResult, disabled }: ChatInputProps) {
  const [input, setInput] = useState("");
  const [isRecording, setIsRecording] = useState(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey && input.trim()) {
      e.preventDefault();
      onSend(input.trim());
      setInput("");
    }
  };

  const handleSend = () => {
    if (input.trim()) {
      onSend(input.trim());
      setInput("");
    }
  };

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;
      chunksRef.current = [];

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          chunksRef.current.push(e.data);
        }
      };

      mediaRecorder.onstop = async () => {
        const audioBlob = new Blob(chunksRef.current, { type: "audio/wav" });
        stream.getTracks().forEach((track) => track.stop());

        // Call the voice result callback if provided
        if (onVoiceResult) {
          try {
            // Import api here to avoid circular deps
            const { api } = await import("../../services/api");
            const result = await api.transcribe(audioBlob);
            if (result.transcription) {
              onVoiceResult(result.transcription);
              setInput(result.transcription);
            }
          } catch (error) {
            console.error("Transcription failed:", error);
          }
        }
      };

      mediaRecorder.start();
      setIsRecording(true);
    } catch (error) {
      console.error("Failed to start recording:", error);
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop();
      setIsRecording(false);
    }
  };

  const toggleRecording = () => {
    if (isRecording) {
      stopRecording();
    } else {
      startRecording();
    }
  };

  return (
    <div className="pt-4">
      <div className="flex items-center gap-3">
        {/* Voice recording button */}
        <button
          onClick={toggleRecording}
          disabled={disabled}
          className={`px-4 py-2 rounded-full transition-all duration-200 text-sm font-medium flex-shrink-0 ${
            isRecording
              ? "bg-red-500/80 text-white animate-pulse shadow-lg shadow-red-500/30"
              : "glass-button text-white/80 hover:text-white"
          } disabled:opacity-40 disabled:cursor-not-allowed`}
          aria-label={isRecording ? "Stop recording" : "Start voice recording"}
          title={isRecording ? "Stop recording" : "Voice input"}
        >
          {isRecording ? "Stop" : "Voice"}
        </button>

        {/* Input field with Send button inside */}
        <div className="flex-1 glass-input rounded-full p-1.5 pl-4 flex items-center gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={disabled || isRecording}
            placeholder={
              isRecording ? "Recording..." : "Ask anything about what you see..."
            }
            className="flex-1 py-2 bg-transparent border-0 text-white placeholder:text-white/40 focus:outline-none text-[14px]"
          />

          {/* Send button inside input */}
          <button
            onClick={handleSend}
            disabled={disabled || !input.trim() || isRecording}
            className="px-5 py-2 bg-blue-500/80 text-white rounded-full disabled:opacity-40 disabled:cursor-not-allowed hover:bg-blue-500 transition-all duration-200 shadow-lg shadow-blue-500/20 text-sm font-medium flex-shrink-0"
            aria-label="Send message"
          >
            Send
          </button>
        </div>
      </div>

      {/* Recording indicator */}
      {isRecording && (
        <div className="mt-3 text-center text-sm text-red-400 font-medium drop-shadow-sm">
          Recording... Click "Stop" to finish
        </div>
      )}
    </div>
  );
}
