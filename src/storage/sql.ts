// Expo SQLite and Bun SQLite have different native APIs; keep storage logic shared and testable.
export type SqlValue = string | number | null;
export interface Sql {
  exec(sql: string): void;
  run(sql: string, ...values: SqlValue[]): void;
  all<T>(sql: string, ...values: SqlValue[]): T[];
  transaction<T>(work: () => T): T;
}
