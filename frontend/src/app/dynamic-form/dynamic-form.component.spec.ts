import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { DynamicFormComponent } from './dynamic-form.component';

describe('DynamicFormComponent', () => {
  let component: DynamicFormComponent;
  let fixture: ComponentFixture<DynamicFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DynamicFormComponent, FormsModule]
    }).compileComponents();
  });

  function createComponent(config: any, moduleName = ''): void {
    fixture = TestBed.createComponent(DynamicFormComponent);
    component = fixture.componentInstance;
    component.config = config;
    component.moduleName = moduleName;
    fixture.detectChanges();
  }

  function fieldLabels(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.field-label'))
      .map((el: any) => el.textContent?.trim() ?? '');
  }

  it('renders number and string fields', () => {
    createComponent({ 'max-retries': 3, 'binary-path': 'ffmpeg' });

    expect(fieldLabels()).toEqual(['max-retries', 'binary-path']);
    expect(fixture.nativeElement.querySelector('input[type="number"]')).toBeTruthy();
  });

  it('keeps a number field visible when its value is erased (becomes null)', () => {
    createComponent({ 'max-retries': 3, 'binary-path': 'ffmpeg' });

    // Angular's NumberValueAccessor writes null to the model when a number input is cleared
    component.config['max-retries'] = null;
    fixture.detectChanges();

    expect(fieldLabels()).toContain('max-retries');
    expect(fixture.nativeElement.querySelector('input[type="number"]')).toBeTruthy();
  });

  it('keeps a number field visible when the user clears the input', () => {
    createComponent({ 'max-retries': 3 });

    const input = fixture.nativeElement.querySelector('input[type="number"]') as HTMLInputElement;
    input.value = '';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(component.config['max-retries']).toBeNull();
    expect(fieldLabels()).toContain('max-retries');
    expect(fixture.nativeElement.querySelector('input[type="number"]')).toBeTruthy();
  });

  it('keeps a string field visible when erased', () => {
    createComponent({ 'binary-path': 'ffmpeg' });

    component.config['binary-path'] = '';
    fixture.detectChanges();

    expect(fieldLabels()).toContain('binary-path');
  });

  it('renders a field whose value is null from the start', () => {
    createComponent({ 'retry-wait': null, 'binary-path': 'ffmpeg' });

    expect(fieldLabels()).toContain('retry-wait');
  });

  it('keeps a nested number field visible when erased', () => {
    createComponent({
      rtsp: { 'max-retries': 3, 'binary-path': 'ffmpeg' }
    });

    component.config.rtsp['max-retries'] = null;
    fixture.detectChanges();

    expect(fieldLabels()).toContain('max-retries');
    expect(fixture.nativeElement.querySelector('input[type="number"]')).toBeTruthy();
  });

  it('renders the camera enabled flag as a toggle switch', async () => {
    createComponent(
      { rtsp: { cameras: [{ enabled: true, url: 'rtsp://cam' }] } },
      'java-rtsp-recorder'
    );
    await fixture.whenStable();
    fixture.detectChanges();

    const toggle = fixture.nativeElement.querySelector('.toggle-switch');
    expect(toggle).toBeTruthy();
    expect(toggle.querySelector('.toggle-slider')).toBeTruthy();
    expect(toggle.querySelector('input[type="checkbox"]').checked).toBeTrue();
  });

  it('keeps the plain checkbox for booleans that are not camera toggles', () => {
    createComponent(
      { rtsp: { cameras: [{ enabled: true, url: 'rtsp://cam' }], 'keep-awake': true } },
      'java-rtsp-recorder'
    );

    expect(fixture.nativeElement.querySelectorAll('.toggle-switch').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('input[type="checkbox"]').length).toBe(2);
  });

  it('writes the toggle state back to the config as a boolean', async () => {
    createComponent(
      { rtsp: { cameras: [{ enabled: true, url: 'rtsp://cam' }] } },
      'java-rtsp-recorder'
    );
    await fixture.whenStable();
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('.toggle-switch input[type="checkbox"]') as HTMLInputElement;
    expect(input.checked).toBeTrue();

    input.checked = false;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(component.config.rtsp.cameras[0].enabled).toBeFalse();
  });

  it('treats the camera enabled flag as a toggle for every camera module', () => {
    const cases: Array<[string, string]> = [
      ['java-rtsp-recorder', 'rtsp.cameras[]'],
      ['java-object-detection', 'object-detection.streams.cameras[]'],
      ['java-live-transmission', 'live.cameras[]'],
    ];

    for (const [moduleName, parentPath] of cases) {
      createComponent({ enabled: true }, moduleName);
      component.parentPath = parentPath;
      expect(component.isToggleField('enabled')).withContext(moduleName).toBeTrue();
    }
  });

  it('defaults the enabled flag to true when cloning an array item', () => {
    createComponent(
      { cameras: [{ enabled: false, url: 'rtsp://cam' }] },
      'java-rtsp-recorder'
    );

    component.addArrayItem('cameras');

    expect(component.config.cameras.length).toBe(2);
    expect(component.config.cameras[1].enabled).toBeTrue();
    expect(component.config.cameras[1].url).toBe('');
  });
});