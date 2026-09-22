export interface CameraDetection {
  cameraName: string;
  label: string;
}

export interface DetectionMap {
  [cameraName: string]: string;
}

export function buildDetectionMap(list: any[] | null | undefined): DetectionMap {
  const map: DetectionMap = {};
  for (const item of (list ?? []) as CameraDetection[]) {
    if (item && item.cameraName) {
      map[item.cameraName] = item.label ?? '';
    }
  }
  return map;
}