/**
 * Individual chat message bubble with glass styling.
 * Shows captured image for user messages when available.
 */
import type { Message } from "../../types";

interface ChatMessageProps {
  message: Message;
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === "user";

  return (
    <div
      className={`flex ${isUser ? "justify-end" : "justify-start"} mb-5`}
    >
      <div
        className={`flex flex-col max-w-[85%] ${
          isUser ? "items-end" : "items-start"
        }`}
      >
        {/* Show captured image for user messages */}
        {isUser && message.imageBase64 && (
          <div className="mb-3 rounded-2xl overflow-hidden shadow-lg border border-white/20">
            <img
              src={`data:image/jpeg;base64,${message.imageBase64}`}
              alt="Captured frame"
              className="max-w-[180px] max-h-[120px] object-cover"
            />
          </div>
        )}

        {/* Message bubble with glass effect */}
        <div
          className={`px-5 py-4 rounded-[20px] ${
            isUser
              ? "glass-bubble-user rounded-br-[6px]"
              : "glass-bubble-ai rounded-bl-[6px]"
          }`}
        >
          <p className="whitespace-pre-wrap text-[14px] leading-relaxed text-white drop-shadow-sm">
            {message.content}
          </p>
        </div>

        {/* Timestamp */}
        <div
          className={`mt-2 text-[10px] text-white/50 ${
            isUser ? "text-right mr-3" : "text-left ml-3"
          }`}
        >
          {message.timestamp.toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </div>
      </div>
    </div>
  );
}
