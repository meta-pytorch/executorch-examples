/**
 * API client for communicating with the FastAPI backend.
 */
import axios from "axios";
import type {
  VisionRequest,
  VisionResponse,
  TranscriptionResponse,
  AppStatus,
} from "../types";

const API_BASE = "http://localhost:8000/api";

const client = axios.create({
  baseURL: API_BASE,
  timeout: 120000, // 2 minutes for slow inference
});

export const api = {
  /**
   * Check if the server is healthy.
   */
  async getHealth(): Promise<{ status: string }> {
    const { data } = await client.get("/health");
    return data;
  },

  /**
   * Get status of all loaded models.
   */
  async getStatus(): Promise<AppStatus> {
    const { data } = await client.get("/status");
    return data;
  },

  /**
   * Run vision-language inference.
   */
  async visionInfer(request: VisionRequest): Promise<VisionResponse> {
    const { data } = await client.post("/vision/infer", request);
    return data;
  },

  /**
   * Transcribe audio to text.
   */
  async transcribe(audioBlob: Blob): Promise<TranscriptionResponse> {
    const formData = new FormData();
    formData.append("audio", audioBlob, "audio.wav");
    const { data } = await client.post("/speech/transcribe", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return data;
  },
};
