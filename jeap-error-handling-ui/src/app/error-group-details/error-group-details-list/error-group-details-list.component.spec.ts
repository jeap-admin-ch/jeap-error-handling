import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorGroupDetailsListComponent } from './error-group-details-list.component';

describe('ErrorGroupDetailsListComponent', () => {
  let component: ErrorGroupDetailsListComponent;
  let fixture: ComponentFixture<ErrorGroupDetailsListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorGroupDetailsListComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ErrorGroupDetailsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
