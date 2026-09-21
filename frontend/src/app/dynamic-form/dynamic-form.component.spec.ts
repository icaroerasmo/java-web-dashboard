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

  function createComponent(config: any): void {
    fixture = TestBed.createComponent(DynamicFormComponent);
    component = fixture.componentInstance;
    component.config = config;
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
});