import { Component, inject, OnInit, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { AdminApiService, PlatformMetrics } from '../../core/admin-api.service';

@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly adminApi = inject(AdminApiService);

  metrics = signal<PlatformMetrics | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    void this.loadMetrics();
  }

  async loadMetrics(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.metrics.set(await this.adminApi.getMetrics());
    } catch {
      this.error.set('Could not load platform metrics.');
    } finally {
      this.loading.set(false);
    }
  }
}
