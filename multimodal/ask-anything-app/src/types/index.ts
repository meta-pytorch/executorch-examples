/**
 * Type definitions for Ask Anything app.
 */

/** Chat message in the conversation */
export interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: Date;
  imageBase64?: string; // Captured frame for context
}

/** Status of a loaded model */
export interface ModelStatus {
  loaded: boolean;
  model_type: string;
  max_seq_len?: number;
  vision_token_id?: number;
  eos_token_id?: number;
}

/** Status of all models */
export interface AppStatus {
  gemma3: ModelStatus;
  whisper: ModelStatus;
}

/** Request for vision-language inference */
export interface VisionRequest {
  prompt: string;
  image_base64?: string;
  max_new_tokens?: number;
  temperature?: number;
}

/** Response from vision-language inference */
export interface VisionResponse {
  response: string;
  tokens_generated: number;
}

/** Response from speech transcription */
export interface TranscriptionResponse {
  transcription: string;
}

/** Camera stream state */
export interface CameraState {
  isActive: boolean;
  stream: MediaStream | null;
  error: string | null;
}
