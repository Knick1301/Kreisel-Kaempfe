import type { Group, Vector3 } from 'three'

/** Ein abnehmbares Teil eines Rotors (für den Burst). */
export interface RotorPart {
  group: Group
  /** Ruheposition der Gruppe im Parent */
  rest: Vector3
  /** Drehpunkt (Mitte des Teils) im Parent */
  pivot: Vector3
}
