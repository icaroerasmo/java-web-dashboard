export interface CameraGrid {
  columns: number;
  rows: number;
  cells: number;
}

export function computeGrid(count: number, targetAspect = 16 / 9): CameraGrid {
  if (!Number.isInteger(count) || count <= 0) {
    throw new Error('count must be a positive integer');
  }

  let best: CameraGrid | undefined;
  let bestScore = Infinity;
  let bestRows = Infinity;

  for (let rows = 1; rows <= count; rows++) {
    const columns = Math.ceil(count / rows);
    const empty = columns * rows - count;
    const aspect = columns / rows;
    const score = empty + Math.abs(aspect - targetAspect);

    if (score < bestScore - 1e-12 || (Math.abs(score - bestScore) <= 1e-12 && rows < bestRows)) {
      best = { columns, rows, cells: columns * rows };
      bestScore = score;
      bestRows = rows;
    }
  }

  return best ?? { columns: 1, rows: 1, cells: 1 };
}