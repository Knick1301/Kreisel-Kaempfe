/** Blickrichtung der Kamera um die Hochachse, geteilt zwischen 3D-Szene und Steuerung. */
export interface CameraView {
  /** atan2(Kamera.x − Ziel.x, Kamera.z − Ziel.z): 0 = Startansicht (Kamera auf +z, Blick nach −z) */
  yaw: number
}

/**
 * Bildschirm-Eingabe (x nach rechts, z zum Betrachter, wie WASD in der Startansicht) in Weltrichtung drehen.
 * W bleibt so immer „ins Bild hinein“, egal wie die Kamera gerade steht.
 */
export function rotateInput(x: number, z: number, yaw: number): { x: number; z: number } {
  const c = Math.cos(yaw)
  const s = Math.sin(yaw)
  return { x: x * c + z * s, z: -x * s + z * c }
}
