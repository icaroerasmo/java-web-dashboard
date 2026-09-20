import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './menu.component.html',
  styleUrl: './menu.component.css'
})
export class MenuComponent {
  @Output() openConfigs = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  onOpenConfigs(): void {
    this.openConfigs.emit();
    this.close.emit();
  }

  onClose(): void {
    this.close.emit();
  }
}
