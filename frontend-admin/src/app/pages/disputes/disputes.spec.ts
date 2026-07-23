import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Disputes } from './disputes';

describe('Disputes', () => {
  let component: Disputes;
  let fixture: ComponentFixture<Disputes>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Disputes],
    }).compileComponents();

    fixture = TestBed.createComponent(Disputes);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the empty state with no disputes', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No open disputes.');
  });

  it('renders a list item per dispute', () => {
    component.disputes.set([{ id: 'd-1', groupName: 'Daret Bureau', status: 'OPEN' }]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Daret Bureau');
    expect(compiled.textContent).toContain('OPEN');
  });
});
