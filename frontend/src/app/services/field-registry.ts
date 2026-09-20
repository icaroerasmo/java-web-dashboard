export interface EnumField {
  path: string;
  options: string[];
}

export const ENUM_REGISTRY: Record<string, EnumField[]> = {
  'java-rtsp-recorder': [
    { path: 'rtsp.hardware-acceleration', options: ['nvidia', 'vaapi', 'none'] },
    { path: 'rclone.transfer-method', options: ['copy', 'move', 'sync'] },
    { path: 'general.locale', options: ['pt-BR', 'en-US'] },
    { path: 'rtsp.cameras[].protocol', options: ['tcp', 'udp'] },
  ],
  'java-object-detection': [
    { path: 'object-detection.acceleration.backend', options: ['AUTO', 'OPENCV', 'CUDA', 'OPENCL', 'VULKAN'] },
    { path: 'general.locale', options: ['pt-BR', 'en-US'] },
    { path: 'object-detection.streams.cameras[].protocol', options: ['TCP', 'UDP'] },
  ],
  'java-telegram-notifier': [
    { path: 'general.locale', options: ['pt-BR', 'en-US'] },
  ],
  'java-live-transmission': [
    { path: 'live.output.video-codec', options: ['h264_nvenc', 'libx264', 'h264_vaapi'] },
    { path: 'live.output.video-preset', options: ['p1', 'p2', 'p3', 'p4', 'p5', 'p6', 'p7'] },
    { path: 'live.output.audio-codec', options: ['aac', 'copy'] },
  ],
};

export function resolveEnumOptions(
  moduleName: string,
  currentPath: string,
): string[] | null {
  const fields = ENUM_REGISTRY[moduleName];
  if (!fields) return null;
  for (const field of fields) {
    if (field.path === currentPath) {
      return field.options;
    }
  }
  return null;
}
