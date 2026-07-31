import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { Dashboard } from './dashboard';
import { AdminApiService } from '../../core/admin-api.service';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;
  let adminApi: { getMetrics: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    adminApi = { getMetrics: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [{ provide: AdminApiService, useValue: adminApi }],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    adminApi.getMetrics.mockResolvedValue({ activeGroups: 0, defaultRatePercent: 0, openDisputes: 0, atRiskGroups: 0 });
    expect(component).toBeTruthy();
  });

  it('loads and displays metrics on init', async () => {
    adminApi.getMetrics.mockResolvedValue({
      activeGroups: 5,
      defaultRatePercent: 12.5,
      openDisputes: 3,
      atRiskGroups: 2,
    });

    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.loading()).toBe(false);
    expect(component.metrics()).toEqual({ activeGroups: 5, defaultRatePercent: 12.5, openDisputes: 3, atRiskGroups: 2 });
    expect(component.error()).toBeNull();
  });

  it('surfaces an error when the metrics call fails', async () => {
    adminApi.getMetrics.mockRejectedValue(new Error('boom'));

    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.loading()).toBe(false);
    expect(component.metrics()).toBeNull();
    expect(component.error()).toBe('Could not load platform metrics.');
  });
});
