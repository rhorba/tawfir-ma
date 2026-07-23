import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-group-detail',
  imports: [],
  templateUrl: './group-detail.html',
  styleUrl: './group-detail.scss',
})
export class GroupDetail {
  // Wired to GET /api/v1/admin/groups + ledger drill-down in Epic 6 (story 6.1)
  private readonly route = inject(ActivatedRoute);
  groupId = this.route.snapshot.paramMap.get('groupId');
}
