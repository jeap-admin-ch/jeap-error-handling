import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorGroupDetailsHeaderComponent } from './error-group-details-header.component';

describe('ErrorGroupDetailsHeaderComponent', () => {
  let component: ErrorGroupDetailsHeaderComponent;
  let fixture: ComponentFixture<ErrorGroupDetailsHeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorGroupDetailsHeaderComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ErrorGroupDetailsHeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
