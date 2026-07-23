import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { GroupDetail } from './group-detail';

describe('GroupDetail', () => {
  let component: GroupDetail;
  let fixture: ComponentFixture<GroupDetail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GroupDetail],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ groupId: 'g-1' }) },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(GroupDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('reads the groupId from the route', () => {
    expect(component.groupId).toBe('g-1');
  });
});
