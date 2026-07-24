import { ComponentFixture, TestBed } from '@angular/core/testing';

import MembersPortal from './members-portal';

describe('MembersPortal', () => {
  let component: MembersPortal;
  let fixture: ComponentFixture<MembersPortal>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MembersPortal],
    }).compileComponents();

    fixture = TestBed.createComponent(MembersPortal);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
