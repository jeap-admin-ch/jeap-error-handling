import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorGroupFilterComponent } from './error-group-filter.component';

describe('ErrorGroupFilterComponent', () => {
  let component: ErrorGroupFilterComponent;
  let fixture: ComponentFixture<ErrorGroupFilterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorGroupFilterComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ErrorGroupFilterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
