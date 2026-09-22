import { computeGrid } from './grid-layout';

describe('computeGrid', () => {
  it('monta grade 1x1 para uma camera', () => {
    expect(computeGrid(1)).toEqual({ columns: 1, rows: 1, cells: 1 });
  });

  it('monta grade 2x1 para duas cameras', () => {
    expect(computeGrid(2)).toEqual({ columns: 2, rows: 1, cells: 2 });
  });

  it('monta grade 2x2 para quatro cameras', () => {
    expect(computeGrid(4)).toEqual({ columns: 2, rows: 2, cells: 4 });
  });

  it('monta grade 3x2 para seis cameras', () => {
    expect(computeGrid(6)).toEqual({ columns: 3, rows: 2, cells: 6 });
  });

  it('monta grade 4x2 para oito cameras', () => {
    expect(computeGrid(8)).toEqual({ columns: 4, rows: 2, cells: 8 });
  });

  it('monta grade 3x2 para cinco cameras (uma cela vazia)', () => {
    expect(computeGrid(5)).toEqual({ columns: 3, rows: 2, cells: 6 });
  });

  it('monta grade 3x3 para nove cameras', () => {
    expect(computeGrid(9)).toEqual({ columns: 3, rows: 3, cells: 9 });
  });

  it('sempre cobre todas as celulas para contagens crescentes', () => {
    for (let count = 1; count <= 64; count++) {
      const grid = computeGrid(count);
      expect(grid.cells).toBeGreaterThanOrEqual(count);
    }
  });

  it('rejeita contagem invalida', () => {
    expect(() => computeGrid(0)).toThrow();
    expect(() => computeGrid(-2)).toThrow();
  });
});