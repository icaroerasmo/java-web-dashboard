import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-dynamic-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dynamic-form.component.html',
  styleUrl: './dynamic-form.component.css'
})
export class DynamicFormComponent {
  @Input({ required: true }) config: any = {};
  @Input() depth = 0;

  isObject(value: any): boolean {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
  }

  isArray(value: any): boolean {
    return Array.isArray(value);
  }

  isBoolean(value: any): boolean {
    return typeof value === 'boolean';
  }

  isNumber(value: any): boolean {
    return typeof value === 'number';
  }

  isString(value: any): boolean {
    return typeof value === 'string';
  }

  getKeys(obj: any): string[] {
    if (!obj || typeof obj !== 'object') return [];
    return Object.keys(obj);
  }
}
