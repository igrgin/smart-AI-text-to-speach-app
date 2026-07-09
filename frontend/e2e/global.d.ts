declare global {
  interface Window {
    __mockRecording: {
      audioPayload: string;
      recordingMimeType: string;
    };
  }
}

export {};
