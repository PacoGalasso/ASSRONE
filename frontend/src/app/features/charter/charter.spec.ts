import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Charter } from './charter';

describe('Charter', () => {
  let component: Charter;
  let fixture: ComponentFixture<Charter>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Charter],
    }).compileComponents();

    fixture = TestBed.createComponent(Charter);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
