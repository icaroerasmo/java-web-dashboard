import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './menu.component.html',
  styleUrl: './menu.component.css'
})
export class MenuComponent {
  @Input() open = false;
  @Output() openConfigs = new EventEmitter<void>();
  @Output() openPresentation = new EventEmitter<void>();
  @Output() openNotifications = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  onOpenConfigs(): void {
    this.openConfigs.emit();
    this.close.emit();
  }

  onOpenPresentation(): void {
    this.openPresentation.emit();
    this.close.emit();
  }

  onOpenNotifications(): void {
    this.openNotifications.emit();
    this.close.emit();
  }

  onOverlayClick(): void {
    this.close.emit();
  }

  onClose(): void {
    this.close.emit();
  }
}
