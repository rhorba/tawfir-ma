import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { ACCESS_TOKEN_KEY } from './auth.service';

export interface PlatformMetrics {
  activeGroups: number;
  defaultRatePercent: number;
  openDisputes: number;
  atRiskGroups: number;
}

export class AdminApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);

  async getMetrics(): Promise<PlatformMetrics> {
    return this.get<PlatformMetrics>('/api/v1/admin/metrics');
  }

  private async get<T>(path: string): Promise<T> {
    try {
      const token = localStorage.getItem(ACCESS_TOKEN_KEY);
      const headers = token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : undefined;
      return await firstValueFrom(this.http.get<T>(`${environment.apiBaseUrl}${path}`, { headers }));
    } catch (err) {
      if (err instanceof HttpErrorResponse) {
        const message = (err.error as { message?: string } | null)?.message ?? 'Request failed';
        throw new AdminApiError(err.status, message);
      }
      throw err;
    }
  }
}
