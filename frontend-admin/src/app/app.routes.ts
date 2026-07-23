import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Dashboard } from './pages/dashboard/dashboard';
import { GroupsList } from './pages/groups-list/groups-list';
import { GroupDetail } from './pages/group-detail/group-detail';
import { Disputes } from './pages/disputes/disputes';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'dashboard', component: Dashboard },
  { path: 'groups', component: GroupsList },
  { path: 'groups/:groupId', component: GroupDetail },
  { path: 'disputes', component: Disputes },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
