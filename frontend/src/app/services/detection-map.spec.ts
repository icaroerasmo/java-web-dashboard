import { buildDetectionMap } from './detection-map';

describe('buildDetectionMap', () => {
  it('mapeia cameraName para label', () => {
    const map = buildDetectionMap([
      { cameraName: 'garagem1', label: 'Pessoa detectada' },
      { cameraName: 'garagem2', label: 'Movimento detectado' }
    ]);

    expect(map).toEqual({
      garagem1: 'Pessoa detectada',
      garagem2: 'Movimento detectado'
    });
  });

  it('ignora entradas sem cameraName', () => {
    const map = buildDetectionMap([
      { cameraName: '', label: 'Pessoa detectada' },
      { cameraName: null, label: 'Movimento detectado' },
      { cameraName: 'garagem1', label: 'Carro detectado' }
    ]);

    expect(map).toEqual({ garagem1: 'Carro detectado' });
  });

  it('aceita lista nula ou vazia', () => {
    expect(buildDetectionMap(null)).toEqual({});
    expect(buildDetectionMap([])).toEqual({});
  });

  it('usar label vazio quando ausente mas camera presente', () => {
    const map = buildDetectionMap([{ cameraName: 'area_de_servico' }]);
    expect(map['area_de_servico']).toBe('');
  });
});