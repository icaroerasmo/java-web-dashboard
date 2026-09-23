import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';
import { ConfigService } from '../services/config.service';
import { ModulesService, ModuleInfo } from '../services/modules.service';
import { DynamicFormComponent } from '../dynamic-form/dynamic-form.component';

@Component({
  selector: 'app-config-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, DynamicFormComponent],
  templateUrl: './config-modal.component.html',
  styleUrl: './config-modal.component.css'
})
export class ConfigModalComponent implements OnChanges, OnDestroy {
  @Input() open = false;
  @Output() close = new EventEmitter<void>();

  modules: ModuleInfo[] = [];
  selectedTab: string | null = null;
  configs: Record<string, any> = {};
  loading: Record<string, boolean> = {};
  saving = false;
  savedMessage = '';
  restarting = false;
  restartMessage = '';
  restartError = false;

  envVars: { key: string; value: string; secret: boolean }[] = [];
  envLoading = false;
  envSaving = false;
  envMessage = '';
  revealedSecrets = new Set<number>();

  queues: any[] = [];
  queuesLoading = false;
  selectedQueue: string | null = null;
  messages: any[] = [];
  messagesLoading = false;
  viewCount = 10;
  messagePayload = '';
  mqSending = false;
  mqMessage = '';
  mqInfoMessage = '';

  go2rtcStreams: { name: string; url: string }[] = [];
  go2rtcOriginalNames: string[] = [];
  go2rtcLoading = false;
  go2rtcSaving = false;

  private pollTimer: any = null;

  constructor(
    private modulesService: ModulesService,
    private configService: ConfigService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open']) {
      if (this.open) {
        this.loadModules();
        this.startPolling();
      } else {
        this.stopPolling();
      }
    }
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  get liveJavaModules(): ModuleInfo[] {
    return this.modules.filter(m => m.type === 'java' && m.status === 'UP');
  }

  get isEnvironmentTab(): boolean {
    return this.selectedTab === '__environment__';
  }

  get isRabbitMqTab(): boolean {
    return this.selectedTab === 'rabbitmq';
  }

  get isGo2RtcTab(): boolean {
    return this.selectedTab === 'go2rtc';
  }

  private startPolling(): void {
    this.stopPolling();
    this.pollTimer = setInterval(() => this.loadModules(), 5000);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  loadModules(): void {
    this.modulesService.getModules().subscribe({
      next: (res) => {
        this.modules = res.modules;
        if (this.liveJavaModules.length > 0 && !this.selectedTab) {
          this.selectTab(this.liveJavaModules[0].name);
        }
      }
    });
  }

  selectTab(name: string): void {
    this.selectedTab = name;
    if (name === '__environment__') {
      if (this.envVars.length === 0 && !this.envLoading) {
        this.loadGlobalEnv();
      }
    } else if (name === 'go2rtc') {
      if (this.go2rtcStreams.length === 0 && !this.go2rtcLoading) {
        this.loadGo2RtcStreams();
      }
    } else if (name === 'rabbitmq') {
      if (this.queues.length === 0 && !this.queuesLoading) {
        this.loadQueues();
      }
    } else if (!this.configs[name]) {
      this.loading[name] = true;
      this.configService.getConfig(name).subscribe({
        next: (config) => {
          this.configs[name] = config;
          this.loading[name] = false;
        },
        error: () => {
          this.loading[name] = false;
        }
      });
    }
  }

  loadGlobalEnv(): void {
    this.envLoading = true;
    this.configService.getGlobalEnv().subscribe({
      next: (vars) => {
        this.envVars = vars;
        this.envLoading = false;
      },
      error: () => {
        this.envVars = [];
        this.envLoading = false;
      }
    });
  }

  addEnvVar(): void {
    this.envVars.push({ key: '', value: '', secret: false });
  }

  removeEnvVar(index: number): void {
    this.envVars.splice(index, 1);
    this.revealedSecrets.delete(index);
  }

  toggleSecretReveal(index: number): void {
    if (this.revealedSecrets.has(index)) {
      this.revealedSecrets.delete(index);
    } else {
      this.revealedSecrets.add(index);
    }
  }

  isSecretRevealed(index: number): boolean {
    return this.revealedSecrets.has(index);
  }

  saveGlobalEnv(): void {
    this.envSaving = true;
    this.envMessage = '';
    this.configService.saveGlobalEnv(this.envVars).subscribe({
      next: () => {
        this.envSaving = false;
        this.envMessage = 'Applied';
        setTimeout(() => this.envMessage = '', 2000);
      },
      error: () => {
        this.envSaving = false;
        this.envMessage = 'Error applying';
        setTimeout(() => this.envMessage = '', 3000);
      }
    });
  }

  save(): void {
    if (!this.selectedTab) return;
    if (this.isGo2RtcTab) {
      this.saveGo2Rtc();
      return;
    }
    if (!this.configs[this.selectedTab]) return;
    this.saving = true;
    this.configService.saveConfig(this.selectedTab, this.configs[this.selectedTab]).subscribe({
      next: () => {
        this.saving = false;
        this.savedMessage = 'Saved';
        setTimeout(() => this.savedMessage = '', 2000);
      },
      error: () => {
        this.saving = false;
      }
    });
  }

  restart(): void {
    if (!this.selectedTab) return;
    if (!confirm(`Restart ${this.selectedTab}? The module will be unavailable for ~15 seconds.`)) {
      return;
    }
    this.restarting = true;
    this.restartError = false;
    this.restartMessage = `Application ${this.selectedTab} is restarting`;
    const request = this.isGo2RtcTab
      ? this.configService.restartGo2Rtc()
      : this.isRabbitMqTab
        ? this.configService.restartRabbitMq()
        : this.configService.restartModule(this.selectedTab);
    request.subscribe({
      next: () => {
        setTimeout(() => {
          this.restarting = false;
          this.restartMessage = '';
        }, 15000);
      },
      error: () => {
        this.restarting = false;
        this.restartError = true;
        this.restartMessage = 'Restart failed';
        setTimeout(() => {
          this.restartError = false;
          this.restartMessage = '';
        }, 5000);
      }
    });
  }

  loadQueues(): void {
    this.queuesLoading = true;
    this.configService.getRabbitMqQueues().subscribe({
      next: (queues) => {
        this.queues = queues;
        this.queuesLoading = false;
        if (this.selectedQueue && !queues.some((q: any) => q.name === this.selectedQueue)) {
          this.selectedQueue = null;
          this.messages = [];
        }
      },
      error: () => {
        this.queues = [];
        this.queuesLoading = false;
      }
    });
  }

  selectQueue(name: string): void {
    this.selectedQueue = name;
    this.messages = [];
    this.mqInfoMessage = '';
  }

  messageId(msg: any): string {
    return msg?.properties?.message_id || 'no id';
  }

  viewMessages(): void {
    if (!this.selectedQueue) return;
    this.messagesLoading = true;
    this.mqInfoMessage = '';
    this.configService.getRabbitMqMessages(this.selectedQueue, this.viewCount).subscribe({
      next: (messages) => {
        this.messages = messages;
        this.messagesLoading = false;
        if (messages.length === 0) {
          const queue = this.queues.find((q: any) => q.name === this.selectedQueue);
          const unack = queue?.messages_unacknowledged ?? 0;
          const ready = queue?.messages_ready ?? 0;
          if (unack > 0) {
            this.mqInfoMessage = `${unack} message(s) are being processed by consumers (unacknowledged). They will appear here once acknowledged or requeued.`;
          } else if (ready === 0) {
            this.mqInfoMessage = 'No messages in queue.';
          }
        }
      },
      error: () => {
        this.messages = [];
        this.messagesLoading = false;
      }
    });
  }

  sendMessage(): void {
    if (!this.selectedQueue) return;
    this.mqSending = true;
    this.mqMessage = '';
    this.configService.sendRabbitMqMessage(this.selectedQueue, this.messagePayload).subscribe({
      next: () => {
        this.mqSending = false;
        this.messagePayload = '';
        this.mqMessage = 'Sent';
        this.loadQueues();
        setTimeout(() => this.mqMessage = '', 2000);
      },
      error: () => {
        this.mqSending = false;
        this.mqMessage = 'Error sending';
        setTimeout(() => this.mqMessage = '', 3000);
      }
    });
  }

  removeOneMessage(): void {
    if (!this.selectedQueue) return;
    this.configService.removeRabbitMqMessages(this.selectedQueue, 1).subscribe({
      next: () => {
        this.messages = [];
        this.mqInfoMessage = '';
        this.loadQueues();
      }
    });
  }

  removeAllMessages(): void {
    if (!this.selectedQueue) return;
    if (!confirm(`Remove ALL messages from queue "${this.selectedQueue}"?`)) {
      return;
    }
    this.configService.removeRabbitMqMessages(this.selectedQueue).subscribe({
      next: () => {
        this.messages = [];
        this.mqInfoMessage = '';
        this.loadQueues();
      }
    });
  }

  loadGo2RtcStreams(): void {
    this.go2rtcLoading = true;
    this.configService.getGo2RtcStreams().subscribe({
      next: (streams) => {
        this.go2rtcStreams = streams.map((s: any) => ({
          name: s.name,
          url: s.source || s.url || ''
        }));
        this.go2rtcOriginalNames = streams.map((s: any) => s.name);
        this.go2rtcLoading = false;
      },
      error: () => {
        this.go2rtcStreams = [];
        this.go2rtcOriginalNames = [];
        this.go2rtcLoading = false;
      }
    });
  }

  addGo2RtcStream(): void {
    this.go2rtcStreams.push({ name: '', url: '' });
  }

  removeGo2RtcStream(index: number): void {
    this.go2rtcStreams.splice(index, 1);
  }

  saveGo2Rtc(): void {
    const current = this.go2rtcStreams
      .map(s => ({ name: s.name.trim(), url: s.url.trim() }))
      .filter(s => s.name && s.url);
    const currentNames = current.map(s => s.name);
    const removed = this.go2rtcOriginalNames.filter(n => !currentNames.includes(n));
    const ops: Observable<any>[] = [];
    for (const name of removed) {
      ops.push(this.configService.removeGo2RtcStream(name));
    }
    for (const s of current) {
      ops.push(this.configService.saveGo2RtcStream(s.name, s.url));
    }
    if (ops.length === 0) {
      this.savedMessage = 'Saved';
      setTimeout(() => this.savedMessage = '', 2000);
      return;
    }
    this.go2rtcSaving = true;
    forkJoin(ops).subscribe({
      next: () => {
        this.go2rtcSaving = false;
        this.savedMessage = 'Saved';
        setTimeout(() => this.savedMessage = '', 2000);
        this.loadGo2RtcStreams();
      },
      error: () => {
        this.go2rtcSaving = false;
      }
    });
  }

  onClose(): void {
    this.close.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.onClose();
    }
  }
}