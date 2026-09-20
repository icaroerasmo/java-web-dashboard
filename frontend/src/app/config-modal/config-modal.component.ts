import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
export class ConfigModalComponent implements OnChanges {
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

  subTabs: Record<string, 'config' | 'env'> = {};
  envVars: Record<string, any[]> = {};
  envLoading: Record<string, boolean> = {};
  envSaving = false;
  envMessage = '';

  constructor(
    private modulesService: ModulesService,
    private configService: ConfigService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.loadModules();
    }
  }

  get liveJavaModules(): ModuleInfo[] {
    return this.modules.filter(m => m.type === 'java' && m.status === 'UP');
  }

  get currentSubTab(): 'config' | 'env' {
    return this.selectedTab ? (this.subTabs[this.selectedTab] ?? 'config') : 'config';
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
    if (!this.subTabs[name]) {
      this.subTabs[name] = 'config';
    }
    if (!this.configs[name]) {
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

  switchSubTab(tab: 'config' | 'env'): void {
    if (!this.selectedTab) return;
    this.subTabs[this.selectedTab] = tab;
    if (tab === 'env' && !this.envVars[this.selectedTab] && !this.envLoading[this.selectedTab]) {
      this.loadEnv();
    }
  }

  loadEnv(): void {
    if (!this.selectedTab) return;
    this.envLoading[this.selectedTab] = true;
    this.configService.getEnv(this.selectedTab).subscribe({
      next: (vars) => {
        this.envVars[this.selectedTab!] = vars;
        this.envLoading[this.selectedTab!] = false;
      },
      error: () => {
        this.envVars[this.selectedTab!] = [];
        this.envLoading[this.selectedTab!] = false;
      }
    });
  }

  addEnvVar(): void {
    if (!this.selectedTab) return;
    if (!this.envVars[this.selectedTab]) {
      this.envVars[this.selectedTab] = [];
    }
    this.envVars[this.selectedTab].push({ key: '', value: '', ref: null, defaultValue: null });
  }

  removeEnvVar(index: number): void {
    if (!this.selectedTab || !this.envVars[this.selectedTab]) return;
    this.envVars[this.selectedTab].splice(index, 1);
  }

  saveEnv(): void {
    if (!this.selectedTab || !this.envVars[this.selectedTab]) return;
    this.envSaving = true;
    this.envMessage = '';
    this.configService.saveEnv(this.selectedTab, this.envVars[this.selectedTab]).subscribe({
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
    if (!this.selectedTab || !this.configs[this.selectedTab]) return;
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
    this.restartMessage = 'Restarting...';
    this.configService.restartModule(this.selectedTab).subscribe({
      next: () => {
        setTimeout(() => {
          this.restarting = false;
          this.restartMessage = '';
        }, 15000);
      },
      error: () => {
        this.restarting = false;
        this.restartMessage = '';
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
