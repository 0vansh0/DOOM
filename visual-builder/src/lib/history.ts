export type History<T> = { past: T[]; future: T[] };
export function pushHistory<T>(history: History<T>, current: T): History<T> { return { past:[...history.past.slice(-49),current], future:[] }; }
export function undoHistory<T>(history: History<T>, current: T): { history:History<T>; current:T } | null {
  if (!history.past.length) return null;
  return { history:{past:history.past.slice(0,-1),future:[...history.future,current]},current:history.past.at(-1)! };
}
export function redoHistory<T>(history: History<T>, current: T): { history:History<T>; current:T } | null {
  if (!history.future.length) return null;
  return { history:{past:[...history.past,current],future:history.future.slice(0,-1)},current:history.future.at(-1)! };
}
