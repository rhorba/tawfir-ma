import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { GroupsList } from './groups-list';

describe('GroupsList', () => {
  let component: GroupsList;
  let fixture: ComponentFixture<GroupsList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GroupsList],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(GroupsList);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the empty state with no groups', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No groups to show.');
  });

  it('renders a table row per group', () => {
    component.groups.set([{ id: 'g-1', name: 'Daret Bureau', status: 'ACTIVE', memberCount: 10 }]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Daret Bureau');
    expect(compiled.textContent).toContain('ACTIVE');
  });
});
