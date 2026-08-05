import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Membership } from './membership';

describe('Membership', () => {
  let component: Membership;
  let fixture: ComponentFixture<Membership>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Membership],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Membership);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
