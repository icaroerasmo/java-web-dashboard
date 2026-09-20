import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { resolveEnumOptions } from '../services/field-registry';

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
  @Input() moduleName = '';
  @Input() parentPath = '';

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

  getFieldPath(key: string): string {
    return this.parentPath ? `${this.parentPath}.${key}` : key;
  }

  getEnumOptions(key: string): string[] | null {
    const path = this.getFieldPath(key);
    return resolveEnumOptions(this.moduleName, path);
  }

  isArrayEnumField(key: string, subKey: string): string[] | null {
    const path = `${this.getFieldPath(key)}[].${subKey}`;
    return resolveEnumOptions(this.moduleName, path);
  }

  getArrayItemPath(arrayKey: string): string {
    return `${this.getFieldPath(arrayKey)}[]`;
  }

  addArrayItem(key: string): void {
    if (!Array.isArray(this.config[key])) return;
    const arr: any[] = this.config[key];
    if (arr.length > 0 && this.isObject(arr[0])) {
      const template: any = {};
      for (const k of Object.keys(arr[0])) {
        template[k] = '';
      }
      arr.push(template);
    } else {
      arr.push('');
    }
  }

  removeArrayItem(key: string, index: number): void {
    if (!Array.isArray(this.config[key])) return;
    this.config[key].splice(index, 1);
  }

  trackByIndex(index: number): number {
    return index;
  }
}
