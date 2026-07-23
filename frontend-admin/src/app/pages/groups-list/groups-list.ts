import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';

interface GroupRow {
  id: string;
  name: string;
  status: string;
  memberCount: number;
}

@Component({
  selector: 'app-groups-list',
  imports: [RouterLink, MatTableModule],
  templateUrl: './groups-list.html',
  styleUrl: './groups-list.scss',
})
export class GroupsList {
  // Wired to GET /api/v1/admin/groups in Epic 6 (story 6.1)
  displayedColumns = ['name', 'status', 'memberCount'];
  groups = signal<GroupRow[]>([]);
}
