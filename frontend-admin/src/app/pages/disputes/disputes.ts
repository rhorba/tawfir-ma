import { Component, signal } from '@angular/core';

interface DisputeRow {
  id: string;
  groupName: string;
  status: 'OPEN' | 'ACCEPTED' | 'REJECTED';
}

@Component({
  selector: 'app-disputes',
  imports: [],
  templateUrl: './disputes.html',
  styleUrl: './disputes.scss',
})
export class Disputes {
  // Wired to GET /api/v1/admin/disputes in Epic 6 (story 6.1)
  disputes = signal<DisputeRow[]>([]);
}
