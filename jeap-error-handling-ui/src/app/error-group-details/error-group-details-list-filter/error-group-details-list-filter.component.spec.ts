import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorGroupDetailsListFilterComponent } from './error-group-details-list-filter.component';

describe('ErrorGroupDetailsListFilterComponent', () => {
  let component: ErrorGroupDetailsListFilterComponent;
  let fixture: ComponentFixture<ErrorGroupDetailsListFilterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorGroupDetailsListFilterComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ErrorGroupDetailsListFilterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
