import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService, NotificationSummary } from '../services/notification.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css'
})
export class NotificationsComponent implements OnChanges {
  @Input() open = false;
  @Output() close = new EventEmitter<void>();

  tab: 'notifications' | 'logs' = 'notifications';
  all: NotificationSummary[] = [];
  kinds: string[] = [];
  selectedKind = 'all';
  selectedDate = '';
  selectedHour = '';

  constructor(private notificationService: NotificationService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.loadHistory();
    }
  }

  private loadHistory(): void {
    this.notificationService.getNotifications(300).subscribe((list) => {
      this.all = list;
      this.recomputeKinds();
    });
  }

  private recomputeKinds(): void {
    this.kinds = Array.from(new Set(this.logs().map((l) => l.kind ?? 'sem categoria'))).sort();
  }

  logs(): NotificationSummary[] {
    return this.all.filter((n) => n.mediaType === 'TEXT');
  }

  media(): NotificationSummary[] {
    return this.all.filter((n) => n.mediaType !== 'TEXT');
  }

  selectTab(tab: 'notifications' | 'logs'): void {
    this.tab = tab;
  }

  filteredLogs(): NotificationSummary[] {
    let list = this.logs();
    if (this.selectedKind !== 'all') {
      list = list.filter((l) => (l.kind ?? 'sem categoria') === this.selectedKind);
    }
    if (this.selectedDate) {
      list = list.filter((l) => this.dateOf(l) === this.selectedDate);
    }
    if (this.selectedHour !== '') {
      list = list.filter((l) => this.hourOf(l) === this.selectedHour);
    }
    return list;
  }

  dateOf(n: NotificationSummary): string {
    const d = new Date(n.timestamp);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  hourOf(n: NotificationSummary): string {
    return String(new Date(n.timestamp).getHours()).padStart(2, '0');
  }

  mediaUrl(n: NotificationSummary): string | null {
    return n.fileId ? this.notificationService.mediaUrl(n.fileId) : null;
  }

  senderLabel(sender: string): string {
    return sender === 'object-detection' ? 'Detecção' : sender === 'recorder' ? 'Recorder' : sender === 'live-transmission' ? 'Live' : sender;
  }

  timeOf(n: NotificationSummary): string {
    const d = new Date(n.timestamp);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  onClose(): void {
    this.close.emit();
  }
}
