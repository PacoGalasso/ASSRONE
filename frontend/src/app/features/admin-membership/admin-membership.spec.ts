import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminMembership } from './admin-membership';

describe('AdminMembership', () => {
  let component: AdminMembership;
  let fixture: ComponentFixture<AdminMembership>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminMembership],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminMembership);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
