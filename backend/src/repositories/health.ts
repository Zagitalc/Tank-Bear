// A narrow port keeps transport and health policy independent of D1.
export interface HealthRepository {
  isAvailable(): Promise<boolean>;
}

export function createHealthRepository(db: D1Database): HealthRepository {
  return {
    async isAvailable() {
      try {
        const row = await db.prepare("SELECT 1 AS ok").first<{ ok: number }>();
        return row?.ok === 1;
      } catch {
        // Raw provider errors can contain SQL, URLs, or credentials.
        return false;
      }
    },
  };
}
