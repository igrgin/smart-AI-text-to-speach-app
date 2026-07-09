declare global {
  interface Window {
    __mockAudioPayload: string;
    __mockRecordingMimeType: string;
  }
}

export {};
