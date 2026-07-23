import { Component, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

interface PlatformMetrics {
  activeGroups: number;
  defaultRatePercent: number;
  openDisputes: number;
  atRiskGroups: number;
}

@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  // Wired to GET /api/v1/admin/metrics in Epic 6 (story 6.1)
  metrics = signal<PlatformMetrics>({
    activeGroups: 0,
    defaultRatePercent: 0,
    openDisputes: 0,
    atRiskGroups: 0,
  });
}
